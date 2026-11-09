package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SberBalanceCardPolicyTest {
    private data class Balance(val suffix: String?, val kopecks: Long, val time: Long)

    @Test fun cardAndAccountSuffixesYieldOneLatestBalanceWithoutSumming() {
        val previous = Balance("7673", 9089L, 1_000L)
        val recent = Balance("9667", 1189L, 2_000L)
        val chosen = SberBalanceCardPolicy.latest(listOf(previous, recent)) { it.time }
        assertEquals("9667", chosen?.suffix)
        assertEquals(1189L, chosen?.kopecks)
    }

    @Test fun delayedOlderPushCannotDisplaceRecentOne() {
        val recent = Balance("9667", 1189L, 2_000L)
        val delayed = Balance("7673", 9089L, 1_000L)
        assertEquals(recent,
            SberBalanceCardPolicy.latest(listOf(recent, delayed)) { it.time })
    }

    @Test fun PushWithoutIdentifierMayProvideTheLatestAmount() {
        val unknown = Balance(null, 4089L, 3_000L)
        assertEquals(unknown,
            SberBalanceCardPolicy.latest(listOf(unknown)) { it.time })
    }

    @Test fun noPushYetHasNoBalance() {
        assertNull(SberBalanceCardPolicy.latest(emptyList<Balance>()) { it.time })
    }
}
