package com.github.russoturissto.buttonapp.app

import java.util.Calendar

/** The date and time shown by a new editor come from one opening instant. */
internal object ReminderDateTimePolicy {
    fun atOpening(nowMillis: Long = System.currentTimeMillis()): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    /** Choosing a day never silently resets the hour or minute. */
    fun selectDay(calendar: Calendar, year: Int, month: Int, day: Int) {
        // Start from day 1 to avoid an overflow on January 31 -> February.
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(year, month, day)
    }
}
