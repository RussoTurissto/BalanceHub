package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDeliveryPolicyTest {
    @Test fun delayedAlarmRemainsDeliverableAfterArchiving() {
        val due = 1_800_000_000_000L
        assertTrue(EventDeliveryPolicy.canNotify(due - 3_600_000L,
            due, due + 2 * 60_000L))
        assertTrue(EventDeliveryPolicy.archivedOccurrenceEligible(
            "completed", due, due + 60_000L, due + 2 * 60_000L))
    }

    @Test fun oldAndDeletedEventsAreNotNotified() {
        val due = 1_800_000_000_000L
        assertFalse(EventDeliveryPolicy.canNotify(due - 3_600_000L,
            due, due + EventDeliveryPolicy.MAX_DELAY_MS + 1L))
        assertFalse(EventDeliveryPolicy.archivedOccurrenceEligible(
            "deleted", due, due + 60_000L, due + 70_000L))
        assertFalse(EventDeliveryPolicy.archivedOccurrenceEligible(
            "completed", due, due - 1L, due + 70_000L))
    }

    @Test fun notYetReachedReminderLeadMustNotFire() {
        val due = 1_800_000_000_000L
        assertFalse(EventDeliveryPolicy.canNotify(due - 3_600_000L,
            due, due - 3_600_001L))
    }
}
