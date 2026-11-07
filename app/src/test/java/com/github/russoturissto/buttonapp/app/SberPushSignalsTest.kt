package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SberPushSignalsTest {
    @Test fun signalsDetectRealBankPushWithoutKeepingItsText() {
        val signals = SberPushSignals.inspect(
            "+ 10 ₽ — Теперь у тебя: 40,89 ₽ Плат. счёт ••\n9667")
        assertTrue(signals.money)
        assertTrue(signals.balanceCue)
        assertTrue(signals.maskedAccount)
    }

    @Test fun normalizesInvisibleSpacesAndLineBreaks() {
        val raw = "Теперь\u200B у\u202Fтебя:\n40,89 ₽"
        assertEquals("Теперь у тебя: 40,89 ₽", SberPushSignals.normalize(raw))
        assertTrue(SberPushSignals.inspect(raw).balanceCue)
    }

    @Test fun amountOnlyDoesNotSignalBankBalance() {
        val signals = SberPushSignals.inspect("Перевод + 10 ₽ по карте")
        assertTrue(signals.money)
        assertFalse(signals.balanceCue)
    }

    @Test fun titleWithNoAmountDoesNotClaimRubles() {
        val signals = SberPushSignals.inspect("Баланс распушился 😺")
        assertTrue(signals.balanceCue)
        assertFalse(signals.money)
    }
}
