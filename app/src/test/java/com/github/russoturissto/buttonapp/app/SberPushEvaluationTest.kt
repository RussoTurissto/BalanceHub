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
}
