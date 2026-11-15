package com.github.russoturissto.buttonapp.app

/** Render the localized currency name without changing stored ISO codes. */
internal object PaymentCurrencyFormatter {
    /** UI payment lists deliberately show the saved numeric amount only.
     * Currency remains in the record and in notifications, never in a list row.
     */
    fun amountForList(amount: String): String = amount

    fun currencyLabel(code: String): String =
        if (code.equals("RUB", ignoreCase = true)) "Руб." else code

    fun amountWithCurrency(amount: String, currency: String): String =
        "$amount ${currencyLabel(currency)}"
}
