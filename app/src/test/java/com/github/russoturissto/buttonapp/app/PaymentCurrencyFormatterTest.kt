package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentCurrencyFormatterTest {
    @Test fun rubUsesRussianLabel() {
        assertEquals("990 Руб.", PaymentCurrencyFormatter.amountWithCurrency("990", "RUB"))
        assertEquals("13000 Руб.", PaymentCurrencyFormatter.amountWithCurrency("13000", "rub"))
    }

    @Test fun otherCurrencyCodesStayTheSame() {
        assertEquals("12 EUR", PaymentCurrencyFormatter.amountWithCurrency("12", "EUR"))
    }
}
