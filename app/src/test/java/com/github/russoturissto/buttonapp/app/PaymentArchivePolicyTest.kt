package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentArchivePolicyTest {
    @Test fun oneTimeMovesOnlyAtActualPaymentDate() {
        val due = 1_800_000_000_000L
        assertFalse(PaymentArchiveStore.shouldArchive("once", due, due - 86400000L))
        assertFalse(PaymentArchiveStore.shouldArchive("once", due, due - 1L))
        assertTrue(PaymentArchiveStore.shouldArchive("once", due, due))
        assertTrue(PaymentArchiveStore.shouldArchive("once", due, due + 1L))
    }

    @Test fun repeatedRemindersNeverMoveAutomatically() {
        val due = 1_800_000_000_000L
        assertFalse(PaymentArchiveStore.shouldArchive("monthly", due, due + 9999999L))
        assertFalse(PaymentArchiveStore.shouldArchive("weekly", due, due + 9999999L))
    }
}
