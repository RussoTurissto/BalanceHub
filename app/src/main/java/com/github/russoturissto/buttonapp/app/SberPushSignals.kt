package com.github.russoturissto.buttonapp.app

import java.text.Normalizer

/**
 * Pure text normalization and coarse anonymous field diagnostics.
 * Text and amounts are never kept in diagnostics.
 */
internal object SberPushSignals {
    private val hiddenSpace = Regex("""[\u200B-\u200D\u2060\uFEFF]""")
    private val whitespace = Regex("""[\s\u00A0\u202F]+""")
    private val money = Regex(
        """(?iu)(?<![\d.,])[+-]?\d[\d \u00a0\u202f,.]{0,24}\s*(?:₽|руб(?:\.|ля|лей)?|RUB|р\.)(?![\p{L}\d])"""
    )
    private val cue = Regex(
        """(?iu)(?:\b(?:остаток|остатка|остатке|баланс|доступно|осталось)\b|\bна\s+лапках\b|\b(?:теперь|сейчас)\s+у\s+тебя\b|\bу\s+тебя\s+осталось\b|\b(?:на\s+сч[её]те|на\s+карте)\b)"""
    )
    private val masked = Regex("""[•●*xX]{1,8}\s*\d{4}(?!\d)""")

    fun normalize(text: String): String = whitespace.replace(
        hiddenSpace.replace(Normalizer.normalize(text, Normalizer.Form.NFKC), " "),
        " "
    ).trim()

    data class Signals(val money: Boolean, val balanceCue: Boolean, val maskedAccount: Boolean)

    fun inspect(text: String): Signals {
        val value = normalize(text)
        return Signals(money.containsMatchIn(value), cue.containsMatchIn(value),
            masked.containsMatchIn(value))
    }
}
