package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentDeliveryPolicyTest {
    private val due = 1_800_000_000_000L

    @Test fun normalReminderAndShortDozeDelayAreDelivered() {
        assertTrue(PaymentDeliveryPolicy.eligible(due, due, due))
        assertTrue(PaymentDeliveryPolicy.eligible(due, due, due + 149_000L))
        assertTrue(PaymentDeliveryPolicy.eligible(due - 86_400_000L, due,
            due - 120_000L))
    }

    @Test fun lateWindowHasHardEnd() {
        assertTrue(PaymentDeliveryPolicy.eligible(due, due,
            due + PaymentDeliveryPolicy.LATE_GRACE_MS - 1L))
        assertFalse(PaymentDeliveryPolicy.eligible(due, due,
            due + PaymentDeliveryPolicy.LATE_GRACE_MS))
        assertFalse(PaymentDeliveryPolicy.eligible(due, due, due - 1L))
    }

    @Test fun recoveryOnlyWithinGraceAndNotBeforeDue() {
        assertTrue(PaymentDeliveryPolicy.mayRecover(due, due + 120_000L))
        assertFalse(PaymentDeliveryPolicy.mayRecover(due, due - 1L))
        assertFalse(PaymentDeliveryPolicy.mayRecover(due,
            due + PaymentDeliveryPolicy.LATE_GRACE_MS))
    }

    @Test fun prematureSignalIsRescheduledAtOriginalDueTime() {
        assertEquals(PaymentDeliveryPolicy.Status.EARLY,
            PaymentDeliveryPolicy.status(due, due, due - 46_000L))
        assertEquals(due, PaymentDeliveryPolicy.earlyRetryAt(due, due, due - 46_000L))
        assertFalse(PaymentDeliveryPolicy.eligible(due, due, due - 46_000L))
    }

    @Test fun earlyRetryNeverCreatesAnImmediateLoop() {
        assertEquals(due + 500L,
            PaymentDeliveryPolicy.earlyRetryAt(due, due, due - 500L))
        val first = due - 3 * 86_400_000L
        assertEquals(first,
            PaymentDeliveryPolicy.earlyRetryAt(first, due, first - 5_000L))
    }

    @Test fun readyExpiredAndInvalidSignalsAreNotRearmed() {
        assertNull(PaymentDeliveryPolicy.earlyRetryAt(due, due, due))
        assertNull(PaymentDeliveryPolicy.earlyRetryAt(due, due,
            due + PaymentDeliveryPolicy.LATE_GRACE_MS))
        assertNull(PaymentDeliveryPolicy.earlyRetryAt(0L, due, due - 10_000L))
        assertEquals(PaymentDeliveryPolicy.Status.EXPIRED,
            PaymentDeliveryPolicy.status(due, due,
                due + PaymentDeliveryPolicy.LATE_GRACE_MS))
        assertEquals(PaymentDeliveryPolicy.Status.INVALID,
            PaymentDeliveryPolicy.status(0L, due, due))
    }

    @Test fun invalidTimestampsNeverDeliver() {
        assertFalse(PaymentDeliveryPolicy.eligible(0L, due, due))
        assertFalse(PaymentDeliveryPolicy.eligible(due, 0L, due))
        assertFalse(PaymentDeliveryPolicy.mayRecover(0L, due))
    }
}
