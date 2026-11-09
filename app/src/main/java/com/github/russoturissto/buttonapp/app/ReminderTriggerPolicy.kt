package com.github.russoturissto.buttonapp.app

import java.util.Calendar

/** Select an actionable reminder even when a new entry's lead time has elapsed. */
internal object ReminderTriggerPolicy {
    fun payment(dueAt: Long, leadDays: Int, now: Long): Long? {
        if (dueAt <= now || leadDays !in 0..30) return null
        val chosen = Calendar.getInstance().apply {
            timeInMillis = dueAt
            add(Calendar.DAY_OF_YEAR, -leadDays)
        }.timeInMillis
        return if (chosen <= now) now + 1_000L else chosen
    }
}
