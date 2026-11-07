package com.github.russoturissto.buttonapp.app

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentCalendarMathTest {
    private fun date(year: Int, month: Int, day: Int, hour: Int = 10): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis

    @Test fun november2026BeginsOnSundayAndHasThirtyDays() {
        assertEquals(6, PaymentCalendarMath.mondayOffset(2026, Calendar.NOVEMBER))
        assertEquals(30, PaymentCalendarMath.daysInMonth(2026, Calendar.NOVEMBER))
    }

    @Test fun leapFebruaryIsHandled() {
        assertEquals(29, PaymentCalendarMath.daysInMonth(2028, Calendar.FEBRUARY))
        assertEquals(28, PaymentCalendarMath.daysInMonth(2027, Calendar.FEBRUARY))
    }

    @Test fun oneTimeRemindersOnlyAppearInTheirOwnMonth() {
        val due = date(2026, Calendar.NOVEMBER, 13)
        assertEquals(setOf(13), PaymentCalendarMath.scheduledDays(
            due, "once", 2026, Calendar.NOVEMBER))
        assertTrue(PaymentCalendarMath.scheduledDays(
            due, "once", 2026, Calendar.DECEMBER).isEmpty())
    }

    @Test fun recurringMonthlyReminderAppearsAgainNextMonth() {
        val due = date(2026, Calendar.NOVEMBER, 13)
        assertEquals(setOf(13), PaymentCalendarMath.scheduledDays(
            due, "monthly", 2026, Calendar.DECEMBER))
    }

    @Test fun recurringWeeklyReminderShowsSeveralDaysInMonth() {
        val due = date(2026, Calendar.NOVEMBER, 13)
        val days = PaymentCalendarMath.scheduledDays(
            due, "weekly", 2026, Calendar.NOVEMBER)
        assertEquals(setOf(13, 20, 27), days)
        assertFalse(days.contains(19))
    }

    @Test fun countdownUsesCalendarDaysRatherThanElapsedHours() {
        assertEquals(0, PaymentCalendarMath.daysUntil(
            date(2026, Calendar.NOVEMBER, 7, 1),
            date(2026, Calendar.NOVEMBER, 7, 23)))
        assertEquals(1, PaymentCalendarMath.daysUntil(
            date(2026, Calendar.NOVEMBER, 7, 23),
            date(2026, Calendar.NOVEMBER, 8, 1)))
        assertEquals(6, PaymentCalendarMath.daysUntil(
            date(2026, Calendar.NOVEMBER, 7),
            date(2026, Calendar.NOVEMBER, 13)))
    }
}
