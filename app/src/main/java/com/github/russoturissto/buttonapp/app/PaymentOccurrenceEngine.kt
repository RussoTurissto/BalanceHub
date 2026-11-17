package com.github.russoturissto.buttonapp.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/**
 * A payment is a stable card; an occurrence is one dated charge of that card.
 * Only edits/exceptions are persisted. Ordinary future occurrences are projected
 * from the rule, so an unlimited schedule never grows an unlimited JSON array.
 */
internal data class PaymentOccurrenceEdit(
    val sourceId: String,
    val amount: String? = null,
    val movedAt: Long? = null,
    val paymentHour: Int? = null,
    val paymentMinute: Int? = null,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val leadDays: Int? = null,
    val skipped: Boolean = false,
    val manualMove: Boolean = false
)

internal data class PaymentHistoryEntry(
    val sourceId: String,
    val action: String,
    val timestamp: Long,
    val previousAt: Long?,
    val currentAt: Long?,
    val details: String? = null
)

internal data class PaymentBulkEdit(
    val from: Long,
    val sourceDay: Int? = null,
    val amount: String? = null,
    val paymentHour: Int? = null,
    val paymentMinute: Int? = null,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val leadDays: Int? = null,
    val includeSkipped: Boolean = false
)

internal data class PaymentPlan(
    val mode: String = "dates",
    val selectedDates: Set<Long> = emptySet(),
    val monthlyDays: Set<Int> = emptySet(),
    val monthlyEnabled: Boolean = false,
    val startsAt: Long = 0L,
    val endsAt: Long? = null,
    val edits: Map<String, PaymentOccurrenceEdit> = emptyMap(),
    val history: List<PaymentHistoryEntry> = emptyList(),
    val bulkEdits: List<PaymentBulkEdit> = emptyList()
) {
    fun withEdit(edit: PaymentOccurrenceEdit, action: String, oldAt: Long?,
        newAt: Long?, now: Long = System.currentTimeMillis()): PaymentPlan =
        copy(edits = edits + (edit.sourceId to edit),
            history = history + PaymentHistoryEntry(edit.sourceId, action, now, oldAt, newAt,
                "До: " + (edits[edit.sourceId]?.toString() ?: "по расписанию") +
                "; после: " + edit.toString()))
}

internal data class PaymentOccurrence(
    val id: String,
    val cardId: String,
    val sourceDate: Long,
    val sourceDay: Int,
    val sourceIds: List<String>,
    val dueAt: Long,
    val originalAt: Long,
    val amount: String,
    val currency: String,
    val reminderHour: Int,
    val reminderMinute: Int,
    val leadDays: Int,
    val skipped: Boolean,
    val manuallyMoved: Boolean,
    val automaticallyClamped: Boolean
) {
    fun firstReminderAt(): Long = Calendar.getInstance().apply {
        timeInMillis = dueAt
        add(Calendar.DAY_OF_YEAR, -leadDays)
        set(Calendar.HOUR_OF_DAY, reminderHour)
        set(Calendar.MINUTE, reminderMinute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Include a due-day reminder even when the configured reminder hour is later. */
    fun reminderOnDay(day: Long): Long? {
        val date = Calendar.getInstance().apply {
            timeInMillis = day
            set(Calendar.HOUR_OF_DAY, reminderHour)
            set(Calendar.MINUTE, reminderMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (date < firstReminderAt()) return null
        if (date <= dueAt) return date
        return if (PaymentCalendarMath.daysUntil(day, dueAt) == 0) dueAt else null
    }
}

/** All timestamps representing calendar days use local midnight, not fixed 24h deltas. */
internal object PaymentOccurrenceEngine {
    fun day(timestamp: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun monthEnd(year: Int, month: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month + 1, 1)
    }.timeInMillis

    private fun localTimestamp(year: Int, month: Int, day: Int, hour: Int,
        minute: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis

    fun sourceId(year: Int, month: Int, day: Int): String =
        "%04d-%02d-%02d".format(java.util.Locale.ROOT, year, month + 1, day)

    fun dateForSource(id: String): Long? = try {
        val p = id.split("-")
        if (p.size != 3) null else {
            val y = p[0].toInt()
            val m = p[1].toInt() - 1
            val d = p[2].toInt()
            if (m !in 0..11 || d !in 1..31) null
            else localTimestamp(y, m, d.coerceAtMost(PaymentCalendarMath.daysInMonth(y, m)), 0, 0)
        }
    } catch (_: Exception) { null }

    private fun sourceFrom(ts: Long): String = Calendar.getInstance().apply {
        timeInMillis = ts
    }.let { sourceId(it.get(Calendar.YEAR), it.get(Calendar.MONTH),
        it.get(Calendar.DAY_OF_MONTH)) }

    fun occurrences(payment: PaymentReminder, from: Long, until: Long): List<PaymentOccurrence> {
        if (until <= from) return emptyList()
        val schedule = payment.plan
        val anchor = Calendar.getInstance().apply { timeInMillis = payment.dueAt }
        val hour = anchor.get(Calendar.HOUR_OF_DAY)
        val minute = anchor.get(Calendar.MINUTE)
        val byId = linkedMapOf<String, Pair<Long, Boolean>>()
        fun addSource(year: Int, month: Int, selectedDay: Int, clamp: Boolean) {
            val id = sourceId(year, month, selectedDay)
            val actualDay = selectedDay.coerceAtMost(
                PaymentCalendarMath.daysInMonth(year, month))
            val start = localTimestamp(year, month, actualDay, hour, minute)
            if (schedule?.endsAt != null && day(start) > day(schedule.endsAt)) return
            if (schedule?.startsAt != null && schedule.startsAt > 0 &&
                day(start) < day(schedule.startsAt)) return
            if (start in from until until || schedule?.edits?.get(id)?.movedAt
                    ?.let { it in from until until } == true) {
                byId[id] = start to (clamp && actualDay != selectedDay)
            }
        }
        if (payment.recurrence == "custom" && schedule != null) {
            if (schedule.mode == "monthly" && schedule.monthlyEnabled) {
                val startMonth = Calendar.getInstance().apply {
                    timeInMillis = from
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val endMonth = Calendar.getInstance().apply {
                    timeInMillis = until
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, 1)
                }
                // Moved sources can start outside the visible date window.
                var steps = 0
                while (startMonth.before(endMonth) && steps++ < 360) {
                    for (selected in schedule.monthlyDays.sorted())
                        addSource(startMonth.get(Calendar.YEAR),
                            startMonth.get(Calendar.MONTH), selected, true)
                    startMonth.add(Calendar.MONTH, 1)
                }
            } else if (schedule.mode == "dates") {
                for (selected in schedule.selectedDates) {
                    val cal = Calendar.getInstance().apply { timeInMillis = selected }
                    addSource(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH), false)
                }
            }
            // A moved charge is shown even when its original month is not visible.
            for ((id, edit) in schedule.edits) {
                val target = edit.movedAt ?: continue
                if (target !in from until until || byId.containsKey(id)) continue
                val sourceDay = dateForSource(id) ?: continue
                val cal = Calendar.getInstance().apply { timeInMillis = sourceDay }
                val originalNumber = id.substringAfterLast('-').toIntOrNull()
                    ?.takeIf { it in 1..31 } ?: continue
                addSource(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                    originalNumber,
                    schedule.mode == "monthly" && schedule.monthlyEnabled)
            }
        } else {
            val cursor = Calendar.getInstance().apply { timeInMillis = payment.dueAt }
            val recur = payment.recurrence
            var count = 0
            while (cursor.timeInMillis < from && count++ < 12_000) {
                when (recur) {
                    "weekly" -> cursor.add(Calendar.DAY_OF_YEAR, 7)
                    "monthly" -> cursor.add(Calendar.MONTH, 1)
                    else -> break
                }
            }
            count = 0
            while (cursor.timeInMillis < until && count++ < 520) {
                val source = sourceFrom(cursor.timeInMillis)
                byId[source] = cursor.timeInMillis to false
                when (recur) {
                    "weekly" -> cursor.add(Calendar.DAY_OF_YEAR, 7)
                    "monthly" -> cursor.add(Calendar.MONTH, 1)
                    else -> break
                }
            }
        }
        val expanded = byId.mapNotNull { (id, entry) ->
            val (originalAt, auto) = entry
            val edit = schedule?.edits?.get(id)
            val source = dateForSource(id) ?: return@mapNotNull null
            val sourceDay = id.substringAfterLast('-').toIntOrNull() ?: return@mapNotNull null
            val selectedRule = schedule?.bulkEdits?.lastOrNull { rule ->
                val actual = edit?.movedAt ?: originalAt
                val changedTime = Calendar.getInstance().apply {
                    timeInMillis = actual
                    rule.paymentHour?.let { set(Calendar.HOUR_OF_DAY, it) }
                    rule.paymentMinute?.let { set(Calendar.MINUTE, it) }
                }.timeInMillis
                actual >= rule.from && changedTime >= rule.from &&
                    (rule.sourceDay == null || rule.sourceDay == sourceDay) &&
                    (edit?.skipped != true || rule.includeSkipped)
            }
            val effectiveAt = edit?.movedAt ?: if (edit?.paymentHour != null ||
                edit?.paymentMinute != null || selectedRule?.paymentHour != null ||
                selectedRule?.paymentMinute != null) {
                Calendar.getInstance().apply {
                    timeInMillis = originalAt
                    (edit?.paymentHour ?: selectedRule?.paymentHour)
                        ?.let { set(Calendar.HOUR_OF_DAY, it) }
                    (edit?.paymentMinute ?: selectedRule?.paymentMinute)
                        ?.let { set(Calendar.MINUTE, it) }
                }.timeInMillis
            } else originalAt
            if (effectiveAt !in from until until) return@mapNotNull null
            PaymentOccurrence(id, payment.id, source, sourceDay, listOf(id),
                effectiveAt, originalAt, edit?.amount ?: selectedRule?.amount ?: payment.amount, payment.currency,
                edit?.reminderHour ?: selectedRule?.reminderHour ?: hour,
                edit?.reminderMinute ?: selectedRule?.reminderMinute ?: minute,
                edit?.leadDays ?: selectedRule?.leadDays ?: payment.leadDays,
                edit?.skipped ?: false,
                edit?.manualMove ?: false, auto)
        }
        // Only automatically clamped coincident charges with identical settings
        // collapse. Manual moves never collapse (even if all settings coincide).
        val merged = linkedMapOf<String, PaymentOccurrence>()
        for (occ in expanded.sortedWith(compareBy({ it.dueAt }, { it.id }))) {
            val key = if (occ.automaticallyClamped && !occ.manuallyMoved)
                listOf("auto", occ.dueAt, occ.amount, occ.currency,
                    occ.reminderHour, occ.reminderMinute, occ.leadDays,
                    occ.skipped).joinToString("|")
            else "single|" + occ.id
            val old = merged[key]
            merged[key] = if (old == null) occ else old.copy(
                sourceIds = old.sourceIds + occ.sourceIds,
                sourceDay = minOf(old.sourceDay, occ.sourceDay))
        }
        return merged.values.sortedWith(compareBy({ it.dueAt }, { it.id }))
    }

    fun month(payment: PaymentReminder, year: Int, month: Int): List<PaymentOccurrence> {
        val from = localTimestamp(year, month, 1, 0, 0)
        return occurrences(payment, from, monthEnd(year, month))
    }

    fun upcoming(payment: PaymentReminder, now: Long, count: Int = 4):
        List<PaymentOccurrence> {
        val baseEnd = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.YEAR, 30)
        }.timeInMillis
        val selected = payment.plan?.selectedDates?.maxOrNull() ?: 0L
        val end = maxOf(baseEnd, selected + 86_400_000L)
        return occurrences(payment, day(now), end).filter {
            !it.skipped && it.dueAt >= now
        }.take(count)
    }

    fun lastScheduledAt(payment: PaymentReminder): Long? {
        val plan = payment.plan ?: return if (payment.recurrence == "once")
            payment.dueAt else null
        val end = plan.endsAt ?: if (plan.mode == "dates")
            plan.selectedDates.maxOrNull() else null
        // An indefinitely repeating monthly rule has no finite end.
        if (end == null && plan.mode == "monthly" && plan.monthlyEnabled) return null
        val boundary = end ?: return null
        val from = Calendar.getInstance().apply {
            timeInMillis = boundary
            add(Calendar.YEAR, -3)
        }.timeInMillis
        val until = Calendar.getInstance().apply {
            timeInMillis = boundary
            add(Calendar.YEAR, 3)
        }.timeInMillis
        val last = occurrences(payment, from, until).maxOfOrNull { it.dueAt }
        val moved = plan.edits.values.mapNotNull { it.movedAt }.maxOrNull()
        // Skipping the last date does not advance the end of its calendar day.
        return listOfNotNull(last, moved).maxOrNull() ?: boundary
    }
}

internal object PaymentPlanCodec {
    fun decode(json: JSONObject?): PaymentPlan? {
        if (json == null) return null
        return try {
            val dates = json.optJSONArray("selectedDates") ?: JSONArray()
            val days = json.optJSONArray("monthlyDays") ?: JSONArray()
            val edits = json.optJSONArray("edits") ?: JSONArray()
            val history = json.optJSONArray("history") ?: JSONArray()
            val bulkArray = json.optJSONArray("bulkEdits") ?: JSONArray()
            val map = linkedMapOf<String, PaymentOccurrenceEdit>()
            for (i in 0 until edits.length()) {
                val e = edits.getJSONObject(i)
                val source = e.getString("sourceId")
                fun optionalLong(key: String) = if (e.isNull(key) || !e.has(key))
                    null else e.getLong(key)
                fun optionalInt(key: String) = if (e.isNull(key) || !e.has(key))
                    null else e.getInt(key)
                fun optionalString(key: String) = if (e.isNull(key) || !e.has(key))
                    null else e.getString(key)
                map[source] = PaymentOccurrenceEdit(source,
                    optionalString("amount"), optionalLong("movedAt"),
                    optionalInt("paymentHour"), optionalInt("paymentMinute"),
                    optionalInt("reminderHour"), optionalInt("reminderMinute"),
                    optionalInt("leadDays"), e.optBoolean("skipped"),
                    e.optBoolean("manualMove"))
            }
            val audit = (0 until history.length()).mapNotNull { i ->
                val h = history.optJSONObject(i) ?: return@mapNotNull null
                PaymentHistoryEntry(h.optString("sourceId"),
                    h.optString("action"), h.optLong("timestamp"),
                    h.optLong("previousAt").takeIf { h.has("previousAt") && !h.isNull("previousAt") },
                    h.optLong("currentAt").takeIf { h.has("currentAt") && !h.isNull("currentAt") },
                    h.optString("details").takeIf { h.has("details") && !h.isNull("details") })
            }
            val bulk = (0 until bulkArray.length()).mapNotNull { i ->
                val e = bulkArray.optJSONObject(i) ?: return@mapNotNull null
                fun optionalInt(k: String) = if (e.has(k) && !e.isNull(k))
                    e.optInt(k) else null
                fun optionalString(k: String) = if (e.has(k) && !e.isNull(k))
                    e.optString(k) else null
                PaymentBulkEdit(e.optLong("from"), optionalInt("sourceDay"),
                    optionalString("amount"), optionalInt("paymentHour"),
                    optionalInt("paymentMinute"), optionalInt("reminderHour"),
                    optionalInt("reminderMinute"), optionalInt("leadDays"),
                    e.optBoolean("includeSkipped"))
            }
            PaymentPlan(json.optString("mode", "dates"),
                (0 until dates.length()).map { dates.optLong(it) }.filter { it > 0 }.toSet(),
                (0 until days.length()).map { days.optInt(it) }.filter { it in 1..31 }.toSet(),
                json.optBoolean("monthlyEnabled", false), json.optLong("startsAt"),
                json.optLong("endsAt").takeIf { json.has("endsAt") && !json.isNull("endsAt") },
                map, audit, bulk)
        } catch (_: Exception) { null }
    }

    fun encode(plan: PaymentPlan): JSONObject = JSONObject().apply {
        put("mode", plan.mode)
        put("selectedDates", JSONArray().apply {
            plan.selectedDates.sorted().forEach { put(it) }
        })
        put("monthlyDays", JSONArray().apply {
            plan.monthlyDays.sorted().forEach { put(it) }
        })
        put("monthlyEnabled", plan.monthlyEnabled)
        put("startsAt", plan.startsAt)
        put("endsAt", plan.endsAt ?: JSONObject.NULL)
        put("edits", JSONArray().apply {
            plan.edits.values.sortedBy { it.sourceId }.forEach { e ->
                put(JSONObject().apply {
                    put("sourceId", e.sourceId)
                    put("amount", e.amount ?: JSONObject.NULL)
                    put("movedAt", e.movedAt ?: JSONObject.NULL)
                    put("paymentHour", e.paymentHour ?: JSONObject.NULL)
                    put("paymentMinute", e.paymentMinute ?: JSONObject.NULL)
                    put("reminderHour", e.reminderHour ?: JSONObject.NULL)
                    put("reminderMinute", e.reminderMinute ?: JSONObject.NULL)
                    put("leadDays", e.leadDays ?: JSONObject.NULL)
                    put("skipped", e.skipped)
                    put("manualMove", e.manualMove)
                })
            }
        })
        put("bulkEdits", JSONArray().apply {
            for (r in plan.bulkEdits) {
                put(JSONObject().apply {
                    put("from", r.from)
                    put("sourceDay", r.sourceDay ?: JSONObject.NULL)
                    put("amount", r.amount ?: JSONObject.NULL)
                    put("paymentHour", r.paymentHour ?: JSONObject.NULL)
                    put("paymentMinute", r.paymentMinute ?: JSONObject.NULL)
                    put("reminderHour", r.reminderHour ?: JSONObject.NULL)
                    put("reminderMinute", r.reminderMinute ?: JSONObject.NULL)
                    put("leadDays", r.leadDays ?: JSONObject.NULL)
                    put("includeSkipped", r.includeSkipped)
                })
            }
        })
        put("history", JSONArray().apply {
            for (h in plan.history) {
                put(JSONObject().apply {
                    put("sourceId", h.sourceId)
                    put("action", h.action)
                    put("timestamp", h.timestamp)
                    put("previousAt", h.previousAt ?: JSONObject.NULL)
                    put("currentAt", h.currentAt ?: JSONObject.NULL)
                    put("details", h.details ?: JSONObject.NULL)
                })
            }
        })
    }
}
