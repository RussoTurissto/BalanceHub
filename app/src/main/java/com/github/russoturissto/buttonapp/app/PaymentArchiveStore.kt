package com.github.russoturissto.buttonapp.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
    val reason: String
)

internal object PaymentArchiveStore {
    private const val PREFS = "balancehub_data"
    private const val ACTIVE = "payment_reminders"
    private const val ARCHIVE = "payment_archive"
    const val COMPLETED = "completed"
    const val DELETED = "deleted"

    /** An alert sent early is NOT the payment's actual due date. */
    fun shouldArchive(recurrence: String, dueAt: Long, now: Long): Boolean =
        recurrence == "once" && dueAt <= now

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
                    record.optString("archiveReason", DELETED)
                ))
            } catch (_: Exception) {
                // Keep other valid history entries visible if a legacy entry is malformed.
            }
        }
        return records.sortedByDescending { it.archivedAt }
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
                        !shouldArchive(record.optString("recurrence", "once"), due, now))
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
            if (shouldArchive(record.optString("recurrence", "once"),
                    record.optLong("dueAt", Long.MAX_VALUE), now)) {
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
        val remaining = JSONArray()
        for (i in 0 until archive.length()) {
            val entry = archive.optJSONObject(i) ?: return null
            if (entry.optString("id") == id) {
                if (selected != null) return null
                selected = JSONObject(entry.toString())
            } else remaining.put(entry)
        }
        val record = selected ?: return null
        val original = ArchivedPayment(
            id, record.getString("title"), record.getString("amount"),
            record.getString("currency"), nextDueAt, record.getInt("leadDays"),
            record.optString("recurrence", "once"),
            record.optLong("archivedAt", 0L),
            record.optString("archiveReason", DELETED)
        )
        record.remove("archivedAt")
        record.remove("archiveReason")
        record.put("dueAt", nextDueAt)
        active.put(record)
        val saved = prefs.edit().putString(ACTIVE, active.toString())
            .putString(ARCHIVE, remaining.toString()).commit()
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
