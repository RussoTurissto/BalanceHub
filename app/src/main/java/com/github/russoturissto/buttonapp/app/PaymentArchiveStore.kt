package com.github.russoturissto.buttonapp.app

import android.annotation.SuppressLint
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/**
 * Active and archived payment records are kept in one SharedPreferences transaction.
 * Archiving is idempotent and preserves original details for viewing and restoration.
 */
internal data class ArchivedPayment(
    val id: String,
    val title: String,
    val amount: String,
    val currency: String,
    val dueAt: Long,
    val leadDays: Int,
    val recurrence: String,
    val archivedAt: Long,
    val reason: String,
    val iconId: String? = null
)

// Synchronous commit is intentional: changing active and archived sets is one
// durable transaction, and the return value lets callers handle write failures.
@SuppressLint("UseKtx")
internal object PaymentArchiveStore {
    private const val PREFS = "balancehub_data"
    private const val ACTIVE = "payment_reminders"
    private const val ARCHIVE = "payment_archive"
    const val COMPLETED = "completed"
    const val DELETED = "deleted"

    /**
     * Keep a one-time payment active during the bounded grace for a late alarm.
     * Otherwise the archive could remove the item before the alarm receiver
     * validates it, silently dropping the final payment notification.
     */
    fun shouldArchive(recurrence: String, dueAt: Long, now: Long): Boolean =
        recurrence == "once" && dueAt > 0L && now >= dueAt &&
            now - dueAt >= PaymentDeliveryPolicy.LATE_GRACE_MS

    fun archiveDeadline(payment: PaymentReminder): Long? {
        if (payment.recurrence != "custom") return if (payment.recurrence == "once")
            payment.dueAt + PaymentDeliveryPolicy.LATE_GRACE_MS else null
        val last = PaymentOccurrenceEngine.lastScheduledAt(payment) ?: return null
        val start = PaymentOccurrenceEngine.day(last)
        val nextDay = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val lastDayCharges = PaymentOccurrenceEngine.occurrences(payment, start, nextDay)
        return if (lastDayCharges.isNotEmpty() && lastDayCharges.all { it.skipped })
            nextDay else last + PaymentDeliveryPolicy.LATE_GRACE_MS
    }

    fun shouldArchive(record: JSONObject, now: Long): Boolean {
        val payment = PaymentReminderCodec.decode(record) ?: return false
        return if (payment.recurrence == "custom")
            archiveDeadline(payment)?.let { now >= it } ?: false
        else shouldArchive(payment.recurrence, payment.dueAt, now)
    }

    private fun readArray(raw: String?): JSONArray? = try {
        JSONArray(raw ?: "[]")
    } catch (_: Exception) {
        null
    }

    fun archived(context: Context): List<ArchivedPayment> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = readArray(prefs.getString(ARCHIVE, "[]")) ?: return emptyList()
        val records = mutableListOf<ArchivedPayment>()
        for (i in 0 until array.length()) {
            try {
                val record = array.getJSONObject(i)
                records.add(ArchivedPayment(
                    record.getString("id"), record.getString("title"),
                    record.getString("amount"), record.getString("currency"),
                    record.getLong("dueAt"), record.getInt("leadDays"),
                    record.optString("recurrence", "once"),
                    record.optLong("archivedAt", 0L),
                    record.optString("archiveReason", DELETED),
                    record.optString("icon").takeIf { IconRegistry.find(it) != null }
                ))
            } catch (_: Exception) {
                // Keep other valid history entries visible if a legacy entry is malformed.
            }
        }
        return records.sortedByDescending { it.archivedAt }
    }

    fun archivedPlan(context: Context, id: String): PaymentPlan? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = readArray(prefs.getString(ARCHIVE, "[]")) ?: return null
        for (i in 0 until entries.length()) {
            val item = entries.optJSONObject(i) ?: continue
            if (item.optString("id") == id)
                return PaymentPlanCodec.decode(item.optJSONObject("scheduleV2"))
        }
        return null
    }

    /** Read active entries without dropping malformed rows or rewriting stored JSON. */
    @Synchronized
    fun activeSnapshot(context: Context): JSONArray? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return readArray(prefs.getString(ACTIVE, "[]"))
    }

    /**
     * Merge only user edits against the latest on-disk list. The same monitor
     * protects archive, restoration and deletion transactions. Unknown rows
     * are preserved; archived IDs cannot be resurrected by a stale UI.
     */
    @Synchronized
    fun saveUiChanges(context: Context, baseline: Map<String, String>,
        desired: JSONArray): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = readArray(prefs.getString(ACTIVE, "[]")) ?: return false
        val history = readArray(prefs.getString(ARCHIVE, "[]")) ?: return false
        val merged = PaymentActiveMergePolicy.merge(current, history, baseline, desired)
            ?: return false
        return prefs.edit().putString(ACTIVE, merged.toString()).commit()
    }

    /** Reboot rescheduling also uses the archive transaction monitor. */
    @Synchronized
    fun advanceRepeatingForBoot(context: Context, now: Long): JSONArray? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = readArray(prefs.getString(ACTIVE, "[]")) ?: return null
        var advanced = false
        for (i in 0 until items.length()) {
            val payment = items.optJSONObject(i) ?: continue
            try {
                val recurrence = payment.optString("recurrence", "once")
                if (recurrence != "weekly" && recurrence != "monthly") continue
                val due = Calendar.getInstance().apply {
                    timeInMillis = payment.getLong("dueAt")
                }
                var iterations = 0
                while (due.timeInMillis <= now &&
                    now - due.timeInMillis >= PaymentDeliveryPolicy.LATE_GRACE_MS &&
                    iterations++ < 10_000) {
                    if (recurrence == "weekly") due.add(Calendar.DAY_OF_YEAR, 7)
                    else due.add(Calendar.MONTH, 1)
                    advanced = true
                }
                payment.put("dueAt", due.timeInMillis)
            } catch (_: Exception) {
                // Preserve malformed rows for recovery, continue valid reminders.
            }
        }
        if (advanced && !prefs.edit().putString(ACTIVE, items.toString()).commit())
            return null
        return items
    }

    /** Atomic archive transaction; expectedDue prevents stale alarms archiving edited payments. */
    @Synchronized
    fun archiveById(
        context: Context, id: String, reason: String, now: Long = System.currentTimeMillis(),
        expectedDue: Long? = null
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = readArray(prefs.getString(ACTIVE, "[]")) ?: return false
        val archived = readArray(prefs.getString(ARCHIVE, "[]")) ?: return false
        val remaining = JSONArray()
        var found: JSONObject? = null
        for (i in 0 until active.length()) {
            val record = active.optJSONObject(i) ?: return false
            if (record.optString("id") == id) {
                val due = record.optLong("dueAt", -1L)
                if (found != null || (expectedDue != null && due != expectedDue) ||
                    (reason == COMPLETED &&
                        !shouldArchive(record, now))
                ) return false
                found = JSONObject(record.toString())
            } else remaining.put(record)
        }
        val record = found ?: return false
        record.put("archivedAt", now)
        record.put("archiveReason", reason)
        // In case history was restored and later archived again, keep one entry per ID.
        val newArchive = JSONArray()
        for (i in 0 until archived.length()) {
            val old = archived.optJSONObject(i) ?: return false
            if (old.optString("id") != id) newArchive.put(old)
        }
        newArchive.put(record)
        return prefs.edit().putString(ACTIVE, remaining.toString())
            .putString(ARCHIVE, newArchive.toString()).commit()
    }

    @Synchronized
    fun archiveDue(context: Context, now: Long = System.currentTimeMillis()): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = readArray(prefs.getString(ACTIVE, "[]")) ?: return emptyList()
        val archive = readArray(prefs.getString(ARCHIVE, "[]")) ?: return emptyList()
        val remaining = JSONArray()
        val moved = mutableListOf<String>()
        for (i in 0 until active.length()) {
            val record = active.optJSONObject(i) ?: return emptyList()
            if (shouldArchive(record, now)) {
                val id = record.optString("id")
                if (id.isBlank()) return emptyList()
                val item = JSONObject(record.toString())
                item.put("archiveReason", COMPLETED)
                item.put("archivedAt", now)
                var existing = false
                for (j in 0 until archive.length()) {
                    if (archive.optJSONObject(j)?.optString("id") == id) existing = true
                }
                if (!existing) archive.put(item)
                moved.add(id)
            } else remaining.put(record)
        }
        if (moved.isEmpty()) return emptyList()
        val saved = prefs.edit().putString(ACTIVE, remaining.toString())
            .putString(ARCHIVE, archive.toString()).commit()
        return if (saved) moved else emptyList()
    }

    /** Restore only to a future due date; prevents immediate re-archiving. */
    @Synchronized
    fun restore(context: Context, id: String, nextDueAt: Long): ArchivedPayment? {
        if (nextDueAt <= System.currentTimeMillis()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = readArray(prefs.getString(ACTIVE, "[]")) ?: return null
        val archive = readArray(prefs.getString(ARCHIVE, "[]")) ?: return null
        for (i in 0 until active.length()) {
            if (active.optJSONObject(i)?.optString("id") == id) return null
        }
        var selected: JSONObject? = null
        for (i in 0 until archive.length()) {
            val entry = archive.optJSONObject(i) ?: return null
            if (entry.optString("id") == id) {
                if (selected != null) return null
                selected = JSONObject(entry.toString())
            }
        }
        val record = selected ?: return null
        val newId = UUID.randomUUID().toString()
        val original = ArchivedPayment(
            newId, record.getString("title"), record.getString("amount"),
            record.getString("currency"), nextDueAt, record.getInt("leadDays"),
            record.optString("recurrence", "once"),
            record.optLong("archivedAt", 0L),
            record.optString("archiveReason", DELETED)
        )
        record.remove("archivedAt")
        record.remove("archiveReason")
        record.put("id", newId)
        record.put("dueAt", nextDueAt)
        if (record.optString("recurrence") == "custom") {
            val plan = PaymentPlanCodec.decode(record.optJSONObject("scheduleV2"))
            if (plan != null) record.put("scheduleV2", PaymentPlanCodec.encode(
                plan.copy(startsAt = nextDueAt,
                    selectedDates = if (plan.mode == "dates")
                        setOf(PaymentOccurrenceEngine.day(nextDueAt))
                        else plan.selectedDates,
                    history = plan.history + PaymentHistoryEntry(
                        "", "restored_copy", System.currentTimeMillis(), null, nextDueAt))))
        }
        active.put(record)
        val saved = prefs.edit().putString(ACTIVE, active.toString())
            .commit()
        return if (saved) original else null
    }

    @Synchronized
    fun deletePermanently(context: Context, id: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val original = readArray(prefs.getString(ARCHIVE, "[]")) ?: return false
        val remaining = JSONArray()
        var deleted = false
        for (i in 0 until original.length()) {
            val entry = original.optJSONObject(i) ?: return false
            if (entry.optString("id") == id) deleted = true
            else remaining.put(entry)
        }
        if (!deleted) return false
        return prefs.edit().putString(ARCHIVE, remaining.toString()).commit()
    }
}
