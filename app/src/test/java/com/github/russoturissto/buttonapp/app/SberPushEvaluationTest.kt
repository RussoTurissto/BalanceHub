package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SberPushEvaluationTest {
    private val bank = SberPushParser.SBER_PACKAGE

    private fun payload(
        titles: List<String> = emptyList(),
        bodies: List<String> = emptyList()
    ) = SberNotificationText.Payload(titles, bodies, 0)

    @Test fun readsNewPhraseInBodyWithSeparateTitle() {
        val message = payload(listOf("Баланс распушился 😺"),
            listOf("+10 ₽ — Теперь у тебя: 30,89 ₽ Плат. счёт ••9667"))
        val result = SberPushEvaluation.analyze(bank, message).result
        assertEquals(3_089L, result?.kopecks)
        assertEquals("9667", result?.lastFour)
    }

    @Test fun acceptsBalanceDeclaredInTitleWithAmountInText() {
        val message = payload(listOf("Теперь у тебя:"), listOf("30,89 ₽"))
        assertEquals(3_089L, SberPushEvaluation.analyze(bank, message).result?.kopecks)
    }

    @Test fun takesAccountNumberFromMoreCompleteField() {
        val message = payload(
            listOf("Сбербанк"),
            listOf("Теперь у тебя: 30,89 ₽", "Теперь у тебя: 30,89 ₽ счёт ••9667")
        )
        assertEquals("9667", SberPushEvaluation.analyze(bank, message).result?.lastFour)
    }

    @Test fun rejectsConflictingAmountsInDifferentFields() {
        val message = payload(bodies = listOf("Остаток: 50 ₽", "Остаток: 150 ₽"))
        val result = SberPushEvaluation.analyze(bank, message)
        assertEquals(SberPushParser.Reason.AMBIGUOUS_BALANCE, result.reason)
        assertNull(result.result)
    }

    @Test fun neverAssumesTransferAmountIsBalance() {
        val message = payload(listOf("Сбербанк"), listOf("+10 ₽ Перевод зачислен"))
        assertNull(SberPushEvaluation.analyze(bank, message).result)
    }

    @Test fun allowsTitleOnlyWhenItReallyContainsExplicitBalance() {
        val message = payload(listOf("На лапках: 10,89 ₽"))
        assertEquals(1_089L, SberPushEvaluation.analyze(bank, message).result?.kopecks)
    }

    @Test fun doesNotUseUnrelatedPackage() {
        val message = payload(bodies = listOf("Теперь у тебя: 30,89 ₽"))
        assertNull(SberPushEvaluation.analyze("another.app", message).result)
    }
    @Test fun acceptsBalanceSplitByNewlineAndInvisibleSpace() {
        val message = payload(bodies = listOf(
            "+ 10 ₽ — Теперь\u200B у тебя:\n40,89 ₽ Плат. счёт ••\n9667"
        ))
        val result = SberPushEvaluation.analyze(bank, message).result
        assertEquals(4_089L, result?.kopecks)
        assertEquals("9667", result?.lastFour)
    }

    @Test fun combinesSplitFieldsWithoutGuessingTransactionAmount() {
        val message = payload(
            titles = listOf("Сбербанк"),
            bodies = listOf("+ 10 ₽ — Теперь у тебя:", "40,89 ₽ ••9667")
        )
        val result = SberPushEvaluation.analyze(bank, message).result
        assertEquals(4_089L, result?.kopecks)
        assertEquals("9667", result?.lastFour)
    }

    @Test fun bonusOnlyAndMultipleAmountsNeverBecomeBalance() {
        val message = payload(bodies = listOf("Поступило + 10 ₽", "Баланс бонусов: 500 ₽"))
        assertNull(SberPushEvaluation.analyze(bank, message).result)
    }

    @Test fun titleCueAndUnsignedAmountCanBeJoinedSafely() {
        val message = payload(
            titles = listOf("Теперь у вас:"),
            bodies = listOf("1 250,70 ₽ Плат. счёт ••4444")
        )
        val result = SberPushEvaluation.analyze(bank, message)
        assertEquals(125_070L, result.result?.kopecks)
        assertEquals("4444", result.result?.lastFour)
    }

    @Test fun ambiguousTitleDoesNotTurnIncomingTransferIntoBalance() {
        val message = payload(
            titles = listOf("Баланс распушился 😺"),
            bodies = listOf("+10 ₽ — перевод успешно зачислен")
        )
        assertNull(SberPushEvaluation.analyze(bank, message).result)
    }

    @Test fun independentAccountsInSeparateFieldsAreKeptSeparate() {
        val message = payload(bodies = listOf(
            "Остаток карты **1001: 100 ₽",
            "Остаток карты **2002: 200 ₽"
        ))
        val result = SberPushEvaluation.analyze(bank, message)
        assertEquals(SberPushParser.Reason.ACCEPTED, result.reason)
        assertNull(result.result)
        assertEquals(2, result.balances.size)
    }

    @Test fun aHistoricalOnlyMarkerCannotBecomeCurrentBalance() {
        val message = SberNotificationText.Payload(
            emptyList(), emptyList(),
            SberNotificationText.Field.HISTORIC.mask,
            hasHistoricMessages = true
        )
        assertNull(SberPushEvaluation.analyze(bank, message).result)
    }

    @Test fun explicitBalanceWinsWhenAmountOfOperationDiffers() {
        val message = payload(bodies = listOf(
            "Оплата 500 ₽. Доступно: 2 000 ₽ карта ••3456",
            "Оплата 500 ₽"
        ))
        assertEquals(200_000L, SberPushEvaluation.analyze(bank, message).result?.kopecks)
    }

}
