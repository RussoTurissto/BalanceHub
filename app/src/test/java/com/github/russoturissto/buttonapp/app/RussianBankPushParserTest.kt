package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Examples are anonymised test fixtures, never a promise about current bank Push formats. */
class RussianBankPushParserTest {
    @Test fun exactlyThreeSupportedBanks() {
        assertEquals(listOf("sber", "vtb", "tbank"),
            RussianBankCatalog.banks.map { it.id })
    }
    @Test fun purchaseAmountIsNotBalance() {
        val p = "com.idamob.tinkoff.android"
        val a = RussianBankPushParser.analyze(p,
            listOf("Покупка 350 ₽. Остаток 12 450,30 ₽, карта ••1111"))
        assertEquals(1245030L, a.result?.kopecks)
        assertEquals("1111", a.result?.lastFour)
    }
    @Test fun noLabelIsNotBalance() {
        val a = RussianBankPushParser.analyze("com.idamob.tinkoff.android",
            listOf("Покупка: 350 ₽. Спасибо за оплату!"))
        assertNull(a.result)
    }
    @Test fun vtbLatinBalance() {
        val a = RussianBankPushParser.analyze("ru.vtb24.mobilebanking.android",
            listOf("Balans: 1 234.55 RUB"))
        assertEquals(123455L, a.result?.kopecks)
    }
    @Test fun differentBalancesAreAmbiguous() {
        val a = RussianBankPushParser.analyze("com.idamob.tinkoff.android",
            listOf("Остаток 123 ₽. Доступно 456 ₽"))
        assertEquals(RussianBankPushParser.Reason.AMBIGUOUS, a.reason)
    }
    @Test fun creditLimitIsNotCash() {
        val a = RussianBankPushParser.analyze("ru.vtb24.mobilebanking.android",
            listOf("Доступно по кредиту 50 000 ₽"))
        assertNull(a.result)
    }
    @Test fun bonusBalanceIsNotCash() {
        val a = RussianBankPushParser.analyze("ru.vtb24.mobilebanking.android",
            listOf("Бонусный баланс 150 ₽"))
        assertNull(a.result)
    }
    @Test fun zeroRublesIsConfirmedBalance() {
        val a = RussianBankPushParser.analyze("com.idamob.tinkoff.android",
            listOf("На карте ••2222. Остаток 0 ₽"))
        assertEquals(0L, a.result?.kopecks)
    }
    @Test fun unsupportedRussianBankPackagesAreIgnored() {
        assertNull(RussianBankCatalog.byPackage("ru.alfabank.mobile.android"))
        assertNull(RussianBankCatalog.byPackage("ru.psbank.android"))
        assertNull(RussianBankCatalog.byPackage("ru.raiffeisennews"))
        assertEquals("vtb", RussianBankCatalog.byPackage(
            "ru.vtb24.mobilebanking.android")?.id)
        assertEquals("tbank", RussianBankCatalog.byPackage(
            "com.idamob.tinkoff.android")?.id)
    }
    @Test fun existingSberParserStillReadsConfirmedBalance() {
        val result = SberPushParser.analyze("ru.sberbankmobile",
            "Покупка 350 ₽. Остаток: 12 450,30 ₽")
        assertEquals(1_245_030L, result.result?.kopecks)
    }
    @Test fun sberNeverUsesGenericParser() {
        val a = RussianBankPushParser.analyze("ru.sberbankmobile",
            listOf("Остаток 900 ₽"))
        assertEquals(RussianBankPushParser.Reason.NOT_SUPPORTED, a.reason)
    }
    @Test fun unknownApplicationRejected() {
        val a = RussianBankPushParser.analyze("com.random.app",
            listOf("Остаток 900 ₽"))
        assertNull(a.result)
    }
}
