package com.github.russoturissto.buttonapp.app

import java.util.Calendar

/** Calendar-based recurrence across daylight saving changes and month/year lengths. */
internal object EventDateRules {
    private val cycles = setOf("daily", "weekly", "monthly", "yearly")
    fun validRecurrence(value: String) = value == "once" || value in cycles

    fun nextOccurrence(dueAt: Long, recurrence: String, now: Long): Long? {
        if (recurrence !in cycles || dueAt <= 0L) return null
        val next = Calendar.getInstance().apply { timeInMillis = dueAt }
        var attempts = 0
        do {
            when (recurrence) {
                "daily" -> next.add(Calendar.DAY_OF_YEAR, 1)
                "weekly" -> next.add(Calendar.DAY_OF_YEAR, 7)
                "monthly" -> next.add(Calendar.MONTH, 1)
                "yearly" -> next.add(Calendar.YEAR, 1)
            }
            attempts++
        } while (next.timeInMillis <= now && attempts < 10_000)
        return next.timeInMillis.takeIf { it > now }
    }

    fun alertAt(dueAt: Long, minutesBefore: Int, now: Long): Long? {
        if (dueAt <= now || minutesBefore !in 0..10_080) return null
        val chosen = dueAt - minutesBefore * 60_000L
        return if (chosen <= now) now + 2_000L else chosen
    }
}
