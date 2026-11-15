package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReminderNoticeIdsTest {
    @Test fun repeatedOccurrenceHasNewNotificationId() {
        val current = 1_784_000_000_000L
        assertNotEquals(ReminderNoticeIds.payment("rent", current),
            ReminderNoticeIds.payment("rent", current + 604_800_000L))
        assertNotEquals(ReminderNoticeIds.event("birthday", current),
            ReminderNoticeIds.event("birthday", current + 31_536_000_000L))
    }

    @Test fun duplicateDeliveryReusesSameNotificationId() {
        val whenDue = 1_784_000_000_000L
        assertEquals(ReminderNoticeIds.payment("rent", whenDue),
            ReminderNoticeIds.payment("rent", whenDue))
        assertEquals(ReminderNoticeIds.diagnostic("event", whenDue),
            ReminderNoticeIds.diagnostic("event", whenDue))
    }

    @Test fun separateCategoriesDoNotShareIds() {
        assertNotEquals(ReminderNoticeIds.payment("same", 123L),
            ReminderNoticeIds.event("same", 123L))
    }

    @Test fun buttonTestsAreFresh() {
        assertNotEquals(ReminderNoticeIds.test(), ReminderNoticeIds.test())
    }
}
