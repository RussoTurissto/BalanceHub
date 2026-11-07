package com.github.russoturissto.buttonapp.app

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Calendar-day operations for the dashboard. Works with the phone's local time
 * zone and does not mistake a DST transition for a shorter/longer calendar day.
 */
internal object PaymentCalendarMath {
    fun daysInMonth(year: Int, month: Int): Int =
        Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)

    /** Monday is 0, Sunday is 6 (unlike Android's Sunday-first Calendar). */
    fun mondayOffset(year: Int, month: Int): Int {
        val weekday = Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }.get(Calendar.DAY_OF_WEEK)
        return (weekday + 5) % 7
    }

    private fun localDateUtcMidnight(timestamp: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = timestamp }
        return GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    fun daysUntil(now: Long, dueAt: Long): Int =
        ((localDateUtcMidnight(dueAt) - localDateUtcMidnight(now)) / 86_400_000L)
            .toInt()

    /**
     * Days with payments for a month. Repeating reminders are projected to
     * subsequent months without modifying the saved next-payment date.
     */
    fun scheduledDays(
        dueAt: Long, recurrence: String, year: Int, month: Int
    ): Set<Int> {
        val first = Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }
        val end = (first.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val cursor = Calendar.getInstance().apply { timeInMillis = dueAt }
        if (cursor.timeInMillis >= end.timeInMillis) return emptySet()

        if (recurrence != "weekly" && recurrence != "monthly") {
            return if (cursor.timeInMillis >= first.timeInMillis)
                setOf(cursor.get(Calendar.DAY_OF_MONTH)) else emptySet()
        }

        fun advance() {
            if (recurrence == "weekly") cursor.add(Calendar.DAY_OF_YEAR, 7)
            else cursor.add(Calendar.MONTH, 1)
        }
        var iterations = 0
        while (cursor.timeInMillis < first.timeInMillis && iterations++ < 5_000) {
            advance()
        }
        val dates = linkedSetOf<Int>()
        while (cursor.timeInMillis < end.timeInMillis && iterations++ < 5_100) {
            if (cursor.timeInMillis >= first.timeInMillis)
                dates.add(cursor.get(Calendar.DAY_OF_MONTH))
            advance()
        }
        return dates
    }
}
