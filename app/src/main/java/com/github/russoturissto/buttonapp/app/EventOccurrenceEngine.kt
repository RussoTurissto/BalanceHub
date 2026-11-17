package com.github.russoturissto.buttonapp.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

internal data class EventOccurrenceEdit(
    val sourceId: String,
    val movedAt: Long? = null,
    val skipped: Boolean = false
)
internal data class EventHistoryEntry(
    val sourceId: String, val operation: String, val timestamp: Long,
    val oldDate: Long?, val newDate: Long?
)
internal data class EventPlan(
    val mode: String = "weekdays",
    val weekdays: Set<Int> = emptySet(), // Calendar.MONDAY .. Calendar.SUNDAY
    val intervalWeeks: Int = 1,
    val explicitDates: Set<Long> = emptySet(),
    val endsAt: Long? = null,
    val anchorAt: Long = 0L,
    val edits: Map<String, EventOccurrenceEdit> = emptyMap(),
    val history: List<EventHistoryEntry> = emptyList()
) {
    fun withEdit(edit: EventOccurrenceEdit, operation: String,
        originalAt: Long?, updatedAt: Long?): EventPlan = copy(
        edits = edits + (edit.sourceId to edit),
        history = history + EventHistoryEntry(edit.sourceId, operation,
            System.currentTimeMillis(), originalAt, updatedAt))
}

internal data class EventOccurrence(
    val sourceId: String,
    val originalAt: Long,
    val dueAt: Long,
    val skipped: Boolean,
    val moved: Boolean
)

internal object EventOccurrenceEngine {
    fun sourceId(timestamp: Long): String = Calendar.getInstance().apply {
        timeInMillis = timestamp
    }.let {
        String.format(Locale.ROOT, "%04d-%02d-%02d",
            it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1,
            it.get(Calendar.DAY_OF_MONTH))
    }

    private fun startOfWeek(timestamp: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = timestamp
        val weekday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
        add(Calendar.DAY_OF_YEAR, -weekday)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun between(event: PlannedEvent, from: Long, until: Long): List<EventOccurrence> {
        if (until <= from) return emptyList()
        val plan = event.plan
        if (event.recurrence != "custom" || plan == null) {
            val cursor = Calendar.getInstance().apply { timeInMillis = event.dueAt }
            val found = mutableListOf<EventOccurrence>()
            var steps = 0
            while (cursor.timeInMillis < from && steps++ < 12000) {
                val next = EventDateRules.nextOccurrence(cursor.timeInMillis,
                    event.recurrence, cursor.timeInMillis) ?: return found
                cursor.timeInMillis = next
            }
            steps = 0
            while (cursor.timeInMillis < until && steps++ < 500) {
                found.add(EventOccurrence(sourceId(cursor.timeInMillis),
                    cursor.timeInMillis, cursor.timeInMillis, false, false))
                val next = EventDateRules.nextOccurrence(cursor.timeInMillis,
                    event.recurrence, cursor.timeInMillis) ?: break
                cursor.timeInMillis = next
            }
            return found
        }
        val anchor = if (plan.anchorAt > 0) plan.anchorAt else event.dueAt
        val anchorClock = Calendar.getInstance().apply { timeInMillis = anchor }
        val hour = anchorClock.get(Calendar.HOUR_OF_DAY)
        val minute = anchorClock.get(Calendar.MINUTE)
        val generated = linkedMapOf<String, Long>()
        val end = plan.endsAt?.let { PaymentOccurrenceEngine.day(it) }
        if (plan.mode == "dates") {
            for (date in plan.explicitDates) {
                val source = Calendar.getInstance().apply {
                    timeInMillis = date
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if ((end == null || PaymentOccurrenceEngine.day(source) <= end) &&
                    source >= anchor) generated[sourceId(source)] = source
            }
        } else {
            val weekStart = startOfWeek(anchor)
            val minStart = startOfWeek(from)
            // The source of an occurrence cannot be later than its actual date
            // unless it was manually moved (handled below).
            while (weekStart.before(minStart)) weekStart.add(Calendar.WEEK_OF_YEAR,
                plan.intervalWeeks.coerceIn(1, 104))
            val weekInterval = plan.intervalWeeks.coerceIn(1, 104)
            var iterations = 0
            while (weekStart.timeInMillis < until && iterations++ < 520) {
                for (day in plan.weekdays) {
                    val offset = (day + 5) % 7
                    val source = (weekStart.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_YEAR, offset)
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                    }.timeInMillis
                    if (source >= anchor && (end == null ||
                            PaymentOccurrenceEngine.day(source) <= end)) {
                        generated[sourceId(source)] = source
                    }
                }
                weekStart.add(Calendar.WEEK_OF_YEAR, weekInterval)
            }
        }
        // Moved occurrences retain their source identity even across months.
        for ((id, edit) in plan.edits) {
            val target = edit.movedAt ?: continue
            if (target !in from until until || generated.containsKey(id)) continue
            val parts = id.split("-")
            if (parts.size != 3) continue
            try {
                val original = Calendar.getInstance().apply {
                    clear()
                    set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), hour, minute)
                }.timeInMillis
                generated[id] = original
            } catch (_: Exception) { continue }
        }
        return generated.mapNotNull { (id, originalAt) ->
            val edit = plan.edits[id]
            val actual = edit?.movedAt ?: originalAt
            if (actual !in from until until) null
            else EventOccurrence(id, originalAt, actual,
                edit?.skipped ?: false, edit?.movedAt != null)
        }.sortedWith(compareBy({ it.dueAt }, { it.sourceId }))
    }

    fun next(event: PlannedEvent, after: Long): EventOccurrence? {
        val until = Calendar.getInstance().apply {
            timeInMillis = after
            add(Calendar.YEAR, 30)
        }.timeInMillis
        return between(event, after + 1L, until).firstOrNull { !it.skipped }
    }
}

internal object EventPlanCodec {
    fun decode(raw: JSONObject?): EventPlan? {
        if (raw == null) return null
        return try {
            val weekdays = raw.optJSONArray("weekdays") ?: JSONArray()
            val dates = raw.optJSONArray("explicitDates") ?: JSONArray()
            val editsArray = raw.optJSONArray("edits") ?: JSONArray()
            val changes = raw.optJSONArray("history") ?: JSONArray()
            val edits = linkedMapOf<String, EventOccurrenceEdit>()
            for (i in 0 until editsArray.length()) {
                val obj = editsArray.getJSONObject(i)
                val source = obj.getString("sourceId")
                edits[source] = EventOccurrenceEdit(source,
                    obj.optLong("movedAt").takeIf {
                        obj.has("movedAt") && !obj.isNull("movedAt")
                    }, obj.optBoolean("skipped"))
            }
            EventPlan(raw.optString("mode", "weekdays"),
                (0 until weekdays.length()).map { weekdays.optInt(it) }
                    .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }.toSet(),
                raw.optInt("intervalWeeks", 1).coerceIn(1, 104),
                (0 until dates.length()).map { dates.optLong(it) }.filter { it > 0 }.toSet(),
                raw.optLong("endsAt").takeIf {
                    raw.has("endsAt") && !raw.isNull("endsAt")
                }, raw.optLong("anchorAt"), edits,
                (0 until changes.length()).mapNotNull { i ->
                    val entry = changes.optJSONObject(i) ?: return@mapNotNull null
                    EventHistoryEntry(entry.optString("sourceId"), entry.optString("operation"),
                        entry.optLong("timestamp"),
                        entry.optLong("oldDate").takeIf {
                            entry.has("oldDate") && !entry.isNull("oldDate")
                        }, entry.optLong("newDate").takeIf {
                            entry.has("newDate") && !entry.isNull("newDate")
                        })
                })
        } catch (_: Exception) { null }
    }
    fun encode(plan: EventPlan): JSONObject = JSONObject().apply {
        put("mode", plan.mode)
        put("weekdays", JSONArray().apply { plan.weekdays.sorted().forEach { put(it) } })
        put("intervalWeeks", plan.intervalWeeks)
        put("explicitDates", JSONArray().apply {
            plan.explicitDates.sorted().forEach { put(it) }
        })
        put("endsAt", plan.endsAt ?: JSONObject.NULL)
        put("anchorAt", plan.anchorAt)
        put("edits", JSONArray().apply {
            plan.edits.values.forEach { e ->
                put(JSONObject().put("sourceId", e.sourceId)
                    .put("movedAt", e.movedAt ?: JSONObject.NULL)
                    .put("skipped", e.skipped))
            }
        })
        put("history", JSONArray().apply {
            plan.history.forEach { h ->
                put(JSONObject().put("sourceId", h.sourceId)
                    .put("operation", h.operation).put("timestamp", h.timestamp)
                    .put("oldDate", h.oldDate ?: JSONObject.NULL)
                    .put("newDate", h.newDate ?: JSONObject.NULL))
            }
        })
    }
}
