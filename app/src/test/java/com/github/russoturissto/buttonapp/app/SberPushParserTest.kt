package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SberPushParserTest {
    private val bank = SberPushParser.SBER_PACKAGE

    @Test fun parsesRussianGroupingAndMaskedCard() {
        val result = SberPushParser.parse(bank,
            "СберБанк Онлайн\nПокупка 50 ₽. Карта *1234\nОстаток: 12 450,80 ₽")
        assertEquals(1_245_080L, result?.kopecks)
        assertEquals("1234", result?.lastFour)
    }

    @Test fun parsesNonBreakingSpaceAndAvailableBalance() {
        val result = SberPushParser.parse(bank, "Доступно: 1\u202f234,50 руб.")
        assertEquals(123_450L, result?.kopecks)
    }

    @Test fun ignoresExpenseWithoutBalance() {
        assertNull(SberPushParser.parse(bank, "Списание 2 500,00 ₽ по карте *9000"))
    }

    @Test fun ignoresOtherAppsEvenWithSameWords() {
        assertNull(SberPushParser.parse("com.example.notabank", "Остаток: 500 ₽"))
    }

    @Test fun ignoresUnspecifiedCurrencyAndBonusPoints() {
        assertNull(SberPushParser.parse(bank, "Баланс: 4000"))
        assertNull(SberPushParser.parse(bank, "Начислено 500 бонусов"))
    }

    @Test fun rejectsNegativeAndUnrealisticAmounts() {
        assertNull(SberPushParser.parse(bank, "Остаток: -50 ₽"))
        assertNull(SberPushParser.parse(bank, "Остаток: 9999999999999 ₽"))
    }

    @Test fun doesNotTreatTransactionAmountAsBalance() {
        val value = SberPushParser.parse(bank,
            "Перевод 2 500 ₽.\nБаланс карты ••••5678: 77,60 RUB")
        assertEquals(7760L, value?.kopecks)
        assertEquals("5678", value?.lastFour)
    }
}
