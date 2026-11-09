package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ReminderTriggerPolicyTest {
    private fun day(y: Int, m: Int, d: Int, h: Int = 15): Long =
        Calendar.getInstance().apply {
            clear()
            set(y, m - 1, d, h, 0)
        }.timeInMillis

    @Test fun alreadyPassedLeadTimeStillSchedulesPromptly() {
        val now = day(2026, 11, 9)
        assertEquals(now + 1_000L,
            ReminderTriggerPolicy.payment(day(2026, 11, 11), 3, now))
    }

    @Test fun futureLeadTimeUsesLocalCalendar() {
        val now = day(2026, 11, 1)
        assertEquals(day(2026, 11, 8),
            ReminderTriggerPolicy.payment(day(2026, 11, 11), 3, now))
    }

    @Test fun neverRescheduleExpiredOrInvalidPayments() {
        val now = day(2026, 11, 9)
        assertNull(ReminderTriggerPolicy.payment(now, 0, now))
        assertNull(ReminderTriggerPolicy.payment(now + 60_000L, 31, now))
    }
}
