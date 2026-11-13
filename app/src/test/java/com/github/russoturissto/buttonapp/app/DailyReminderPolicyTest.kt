package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailyReminderPolicyTest {
    private fun at(y: Int, m: Int, d: Int, h: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(y, m - 1, d, h, minute)
        }.timeInMillis

    @Test fun paymentFiresOnFollowingLocalDaysOnlyUntilDue() {
        val due = at(2026, 11, 19, 10)
        val first = DailyReminderPolicy.firstPayment(due, 3)
        assertEquals(at(2026, 11, 16, 10), first)
        assertEquals(at(2026, 11, 17, 10),
            DailyReminderPolicy.following(first, due, at(2026, 11, 16, 10)))
        assertEquals(at(2026, 11, 18, 10),
            DailyReminderPolicy.following(first, due, at(2026, 11, 17, 12)))
        assertNull(DailyReminderPolicy.following(first, due, at(2026, 11, 19, 9)))
        assertFalse(DailyReminderPolicy.eligible(first, due, due + 2_000L))
    }

    @Test fun dueTimeAlertHasGraceButNoRepeatedDays() {
        val due = at(2026, 11, 19, 10)
        assertTrue(DailyReminderPolicy.eligible(due, due, due + 1_000L))
        assertFalse(DailyReminderPolicy.eligible(due, due, due + 60_000L))
    }

    @Test fun eventLeadMoreThanOneDayCreatesFollowingDays() {
        val due = at(2026, 11, 20, 14)
        val first = DailyReminderPolicy.firstEvent(due, 3 * 24 * 60)
        assertEquals(at(2026, 11, 17, 14), first)
        assertEquals(at(2026, 11, 19, 14),
            DailyReminderPolicy.following(first, due, at(2026, 11, 18, 18)))
    }

    @Test fun daylightSavingRetainsLocalHour() {
        val zone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
            val due = at(2027, 3, 30, 10)
            val first = DailyReminderPolicy.firstPayment(due, 3)
            assertEquals(at(2027, 3, 27, 10), first)
            assertEquals(at(2027, 3, 28, 10),
                DailyReminderPolicy.following(first, due, first))
        } finally { TimeZone.setDefault(zone) }
    }

    @Test fun recurringDueDoesNotAdvanceAtEarlyReminder() {
        val due = at(2026, 11, 19, 10)
        assertEquals(at(2026, 11, 26, 10),
            PaymentRecurrencePolicy.nextDue(due, "weekly", due + 1000L))
        assertNull(PaymentRecurrencePolicy.nextDue(due, "once", due + 1000L))
    }
}
