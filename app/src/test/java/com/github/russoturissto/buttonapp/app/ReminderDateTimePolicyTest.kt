package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ReminderDateTimePolicyTest {
    private fun at(y: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(y, month - 1, day, hour, minute, 43)
            set(Calendar.MILLISECOND, 987)
        }

    @Test fun openingUsesTheExactDayHourAndMinuteFromThePhone() {
        val now = at(2026, 11, 15, 1, 42)
        val date = ReminderDateTimePolicy.atOpening(now.timeInMillis)
        assertEquals(now.get(Calendar.YEAR), date.get(Calendar.YEAR))
        assertEquals(now.get(Calendar.MONTH), date.get(Calendar.MONTH))
        assertEquals(now.get(Calendar.DAY_OF_MONTH), date.get(Calendar.DAY_OF_MONTH))
        assertEquals(1, date.get(Calendar.HOUR_OF_DAY))
        assertEquals(42, date.get(Calendar.MINUTE))
        assertEquals(0, date.get(Calendar.SECOND))
        assertEquals(0, date.get(Calendar.MILLISECOND))
    }

    @Test fun everyNewCardGetsItsOwnOpeningInstant() {
        val first = ReminderDateTimePolicy.atOpening(at(2026, 11, 15, 1, 42).timeInMillis)
        val second = ReminderDateTimePolicy.atOpening(at(2026, 11, 15, 1, 52).timeInMillis)
        assertEquals(42, first.get(Calendar.MINUTE))
        assertEquals(52, second.get(Calendar.MINUTE))
    }

    @Test fun pickingAnotherDayKeepsTheChosenTime() {
        val date = at(2027, 1, 31, 22, 37)
        ReminderDateTimePolicy.selectDay(date, 2027, Calendar.FEBRUARY, 5)
        assertEquals(2027, date.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, date.get(Calendar.MONTH))
        assertEquals(5, date.get(Calendar.DAY_OF_MONTH))
        assertEquals(22, date.get(Calendar.HOUR_OF_DAY))
        assertEquals(37, date.get(Calendar.MINUTE))
    }
}
