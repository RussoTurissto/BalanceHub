package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SberPushParserTest {
    private val bank = SberPushParser.SBER_PACKAGE

    @Test fun matchesObservedSberKotIncomingPush() {
        val push = "Деньги прыгнули к тебе от отправителя\n" +
            "+ 10 ₽ — На лапках: 10,89 ₽ Плат. счёт ••\n9667"
        val result = SberPushParser.parse(bank, push)
        assertEquals(1_089L, result?.kopecks)
        assertEquals("9667", result?.lastFour)
        assertEquals(SberPushParser.Confidence.EXPLICIT, result?.confidence)
    }

    @Test fun findsExplicitBalanceAndIgnoresPurchasePrice() {
        val result = SberPushParser.parse(bank,
            "Покупка 50 ₽. Карта *1234\nОстаток: 12 450,80 ₽")
        assertEquals(1_245_080L, result?.kopecks)
        assertEquals("1234", result?.lastFour)
    }

    @Test fun parsesNonBreakingSpacesAndAvailableFunds() {
        val result = SberPushParser.parse(bank, "Доступно: 1\u202f234,50 руб.")
        assertEquals(123_450L, result?.kopecks)
    }

    @Test fun findsCardNumberBetweenLabelAndAmount() {
        val result = SberPushParser.parse(bank,
            "Баланс карты ••••5678: 77,60 RUB")
        assertEquals(7_760L, result?.kopecks)
        assertEquals("5678", result?.lastFour)
    }

    @Test fun parsesContextualRubleBalanceAtStartOfClause() {
        val result = SberPushParser.parse(bank,
            "Зачисление 500 ₽. На счёте: 2 050,19 ₽")
        assertEquals(205_019L, result?.kopecks)
        assertEquals(SberPushParser.Confidence.CONTEXTUAL, result?.confidence)
    }

    @Test fun ignoresExpenseWithoutRemainingFunds() {
        assertNull(SberPushParser.parse(bank, "Списание 2 500,00 ₽ по карте *9000"))
        assertNull(SberPushParser.parse(bank, "Покупка на карте *9000: 2 500 ₽"))
    }

    @Test fun ignoresBonusesWithoutRubleBalance() {
        assertNull(SberPushParser.parse(bank, "Начислено 500 бонусов"))
        assertNull(SberPushParser.parse(bank, "Бонусный баланс: 500 ₽"))
    }

    @Test fun doesNotGuessMissingCurrencyOrSpoofSource() {
        assertNull(SberPushParser.parse(bank, "Баланс: 4000"))
        assertNull(SberPushParser.parse("com.example.fake", "На лапках: 500 ₽"))
    }

    @Test fun rejectsConflictingBalanceAmounts() {
        val result = SberPushParser.analyze(bank,
            "Баланс 100 ₽. Остаток 200 ₽. Списание 50 ₽")
        assertEquals(SberPushParser.Reason.AMBIGUOUS_BALANCE, result.reason)
        assertNull(result.result)
    }

    @Test fun rejectsMultipleMaskedAccounts() {
        val result = SberPushParser.analyze(bank,
            "Перевод с карты **1234 на карту **5678. Остаток: 200 ₽")
        assertEquals(SberPushParser.Reason.AMBIGUOUS_ACCOUNT, result.reason)
        assertNull(result.result)
    }

    @Test fun rejectsNegativeAndHugeBalances() {
        assertNull(SberPushParser.parse(bank, "Остаток: -50 ₽"))
        assertNull(SberPushParser.parse(bank, "Остаток: 9999999999999 ₽"))
    }

    @Test fun allowsZeroBalance() {
        assertEquals(0L, SberPushParser.parse(bank, "Остаток: 0 ₽")?.kopecks)
    }

    @Test fun ignoresUnrelatedTextAfterAnchor() {
        assertNull(SberPushParser.parse(bank,
            "Баланс бонусов начислен. Покупка 10 ₽"))
    }
    @Test fun recognizesNowYouHaveIncomingTransfer() {
        val push = "Баланс распушился 😻 Перевод от отправителя\n" +
            "+ 10 ₽ — Теперь у тебя: 30,89 ₽ Плат. счёт ••\n9667"
        val result = SberPushParser.parse(bank, push)
        assertEquals(3_089L, result?.kopecks)
        assertEquals("9667", result?.lastFour)
    }

    @Test fun doesNotMistakeReceiptForBalanceWithoutLabel() {
        assertNull(SberPushParser.parse(bank, "+ 10 ₽ — перевод на счёт ••9667"))
    }
}
