package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentListInteractionTest {
    @Test fun tappingHeaderExpandsAndCollapses() {
        assertFalse(PaymentListInteraction.toggleSection(true))
        assertTrue(PaymentListInteraction.toggleSection(false))
    }

    @Test fun tappingSameRowCollapsesIt() {
        assertEquals("payment-1", PaymentListInteraction.toggleRow(null, "payment-1"))
        assertNull(PaymentListInteraction.toggleRow("payment-1", "payment-1"))
    }

    @Test fun tappingDifferentRowShowsItsDetails() {
        assertEquals("payment-2", PaymentListInteraction.toggleRow("payment-1", "payment-2"))
    }
}
