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
            set(y, month - 1, d, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test fun oneOffEventRemainsVisibleThroughoutItsDay() {
        val due = instant(2026, 11, 9, 9)
        assertFalse(EventArchivePolicy.shouldArchiveOnce(due, instant(2026, 11, 9, 23, 59)))
        assertTrue(EventArchivePolicy.shouldArchiveOnce(due, instant(2026, 11, 10)))
    }

    @Test fun midnightEventExpiresNextMidnight() {
        val due = instant(2026, 11, 9)
        assertFalse(EventArchivePolicy.shouldArchiveOnce(due, due))
        assertTrue(EventArchivePolicy.shouldArchiveOnce(due, instant(2026, 11, 10)))
    }

    @Test fun respectsLocalDstBoundary() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
            val due = instant(2026, 3, 29, 12)
            val tomorrow = instant(2026, 3, 30)
            assertEquals(tomorrow, EventArchivePolicy.endOfDayExclusive(due))
            assertFalse(EventArchivePolicy.shouldArchiveOnce(due, tomorrow - 1))
            assertTrue(EventArchivePolicy.shouldArchiveOnce(due, tomorrow))
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
