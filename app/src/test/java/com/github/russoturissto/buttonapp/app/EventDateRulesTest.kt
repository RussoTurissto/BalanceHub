package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EventDateRulesTest {
    private fun whenAt(year: Int, month: Int, day: Int, hour: Int = 9): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0)
        }.timeInMillis

    @Test fun repeatingMonthlyUsesCalendarDays() {
        val start = whenAt(2026, Calendar.JANUARY, 30)
        val next = EventDateRules.nextOccurrence(
            start, "monthly", start + 1000L)!!
        val day = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(Calendar.FEBRUARY, day.get(Calendar.MONTH))
        assertTrue(day.get(Calendar.DAY_OF_MONTH) in 28..29)
    }

    @Test fun dailyAndYearlyAlwaysAdvanceIntoFuture() {
        val day = whenAt(2026, Calendar.NOVEMBER, 9)
        assertTrue(EventDateRules.nextOccurrence(day, "daily", day + 3*86_400_000L)!! >
            day + 3*86_400_000L)
        assertTrue(EventDateRules.nextOccurrence(day, "yearly", day + 370*86_400_000L)!! >
            day + 370*86_400_000L)
    }

    @Test fun oneOffNeverSpawnsAnotherReminder() {
        assertNull(EventDateRules.nextOccurrence(10_000L, "once", 11_000L))
    }

    @Test fun remindersPastLeadTimeSchedulePromptlyAndNotAfterEvent() {
        assertEquals(3_000L, EventDateRules.alertAt(5_000L, 30, 1_000L))
        assertNull(EventDateRules.alertAt(1_000L, 0, 1_000L))
    }
}
