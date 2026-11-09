package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EventArchivePolicyTest {
    private fun instant(y: Int, month: Int, d: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(y, month - 1, d, hour, minute, 0)
        }.timeInMillis

    @Test fun oneOffMovesToArchiveOneMinuteAfterDueTimeNotAtMidnight() {
        val due = instant(2026, 11, 9, 16)
        assertFalse(EventArchivePolicy.shouldArchiveOnce(due, due))
        assertFalse(EventArchivePolicy.shouldArchiveOnce(due, due + 59_999L))
        assertTrue(EventArchivePolicy.shouldArchiveOnce(due, due + 60_000L))
        assertTrue(EventArchivePolicy.shouldArchiveOnce(due, instant(2026, 11, 9, 17)))
    }

    @Test fun midnightEventExpiresAtOneMinutePastMidnight() {
        val due = instant(2026, 11, 9, 0)
        assertEquals(due + 60_000L, EventArchivePolicy.archiveAt(due))
        assertFalse(EventArchivePolicy.shouldArchiveOnce(due, due + 59_999L))
        assertTrue(EventArchivePolicy.shouldArchiveOnce(due, due + 60_000L))
    }

    @Test fun calendarDstShiftDoesNotDelayOrAccelerateDueTimeArchival() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
            val due = instant(2026, 3, 29, 4, 0)
            assertEquals(due + 60_000L, EventArchivePolicy.archiveAt(due))
            assertFalse(EventArchivePolicy.shouldArchiveOnce(due, due + 59_999L))
            assertTrue(EventArchivePolicy.shouldArchiveOnce(due, due + 60_000L))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test fun invalidTimestampsCannotBeArchived() {
        assertFalse(EventArchivePolicy.shouldArchiveOnce(0, 60_000))
        assertFalse(EventArchivePolicy.shouldArchiveOnce(-1L, Long.MAX_VALUE))
    }
}
