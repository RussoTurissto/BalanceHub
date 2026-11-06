package com.github.russoturissto.buttonapp.app

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Sum upcoming payments per currency; never add rubles to euros. */
internal object PaymentOverview {
    fun totalLabel(items: List<Pair<String, String>>): String {
        if (items.isEmpty()) return "0 Руб."
        val totals = linkedMapOf<String, BigDecimal>()
        items.forEach { (amount, currency) ->
            val value = amount.replace(',', '.').toBigDecimalOrNull() ?: return@forEach
            val code = currency.uppercase(Locale.ROOT)
            totals[code] = (totals[code] ?: BigDecimal.ZERO).add(value)
        }
        if (totals.isEmpty()) return "0 Руб."
        val format = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale("ru")))
        return totals.entries.sortedWith(compareBy({ it.key != "RUB" }, { it.key }))
            .joinToString("\n") { (code, amount) ->
                "\${format.format(amount)} \${PaymentCurrencyFormatter.currencyLabel(code)}"
            }
    }
}
