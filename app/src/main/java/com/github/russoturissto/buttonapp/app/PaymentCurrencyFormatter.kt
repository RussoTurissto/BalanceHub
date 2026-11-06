package com.github.russoturissto.buttonapp.app

/** Render the localized currency name without changing stored ISO codes. */
internal object PaymentCurrencyFormatter {
    fun currencyLabel(code: String): String =
        if (code.equals("RUB", ignoreCase = true)) "Руб." else code

    fun amountWithCurrency(amount: String, currency: String): String =
        "$amount ${currencyLabel(currency)}"
}
