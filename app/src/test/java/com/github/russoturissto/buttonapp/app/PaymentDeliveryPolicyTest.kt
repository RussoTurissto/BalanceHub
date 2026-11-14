package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertFalse
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

    @Test fun invalidTimestampsNeverDeliver() {
        assertFalse(PaymentDeliveryPolicy.eligible(0L, due, due))
        assertFalse(PaymentDeliveryPolicy.eligible(due, 0L, due))
        assertFalse(PaymentDeliveryPolicy.mayRecover(0L, due))
    }
}
