package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentOverviewTest {
    @Test fun totalsRublesForTwoPayments() {
        val sum = PaymentOverview.totalLabel(listOf("990" to "RUB", "13000" to "RUB"))
        assertTrue(sum.contains("13"))
        assertTrue(sum.contains("990 Руб."))
    }

    @Test fun currenciesAreNeverMixed() {
        val sum = PaymentOverview.totalLabel(listOf("990" to "RUB", "10" to "EUR"))
        assertTrue(sum.contains("990 Руб."))
        assertTrue(sum.contains("10 EUR"))
        assertTrue(sum.contains("\n"))
    }

    @Test fun emptyPaymentsShowZero() {
        assertEquals("0 Руб.", PaymentOverview.totalLabel(emptyList()))
    }
}
