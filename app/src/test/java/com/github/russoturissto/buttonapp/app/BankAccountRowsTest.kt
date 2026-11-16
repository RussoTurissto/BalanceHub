package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankAccountRowsTest {
    @Test fun multipleCardsOfSameBankRemainSeparateIncludingZero() {
        val old = BankAccountBalance(3_500_00L, "0479", 100L)
        val another = BankAccountBalance(9_000_00L, "8634", 200L)
        val zero = BankAccountBalance(0L, "0479", 300L)
        val result = BankAccountRows.newestPerAccount(listOf(old, another, zero))
        assertEquals(2, result.size)
        assertEquals(listOf("0479", "8634"), result.map { it.lastFour })
        assertEquals(0L, result[0].kopecks)
        assertEquals(9_000_00L, result[1].kopecks)
    }

    @Test fun unknownBalanceNeverReplacesAnIdentifiedCard() {
        val result = BankAccountRows.newestPerAccount(listOf(
            BankAccountBalance(100L, "1234", 100L),
            BankAccountBalance(200L, null, 200L),
            BankAccountBalance(300L, "1234", 300L)
        ))
        assertEquals(listOf("1234", null), result.map { it.lastFour })
        assertEquals(listOf(300L, 200L), result.map { it.kopecks })
        assertEquals("Карта / счёт без номера", BankAccountRows.label(null))
        assertEquals("Карта / счёт •••• 1234", BankAccountRows.label("1234"))
    }

    @Test fun versionChangeCollapsesOnlyOnce() {
        assertTrue(UpcomingListVersionPolicy.shouldCollapse(716L, -1L))
        assertTrue(UpcomingListVersionPolicy.shouldCollapse(716L, 715L))
        assertFalse(UpcomingListVersionPolicy.shouldCollapse(716L, 716L))
    }
}
