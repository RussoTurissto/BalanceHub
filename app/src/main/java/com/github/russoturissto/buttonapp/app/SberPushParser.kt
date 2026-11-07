package com.github.russoturissto.buttonapp.app

import java.math.BigDecimal

/** Accept only explicit RUB balances, never infer a balance from an expense or transfer. */
internal object SberPushParser {
    // Android package of the supported SberBank Online application.
    const val SBER_PACKAGE = "ru.sberbankmobile"

    data class Result(val kopecks: Long, val lastFour: String?)

    private val balancePattern = Regex(
        """(?iu)\b(?:остаток|баланс|доступно)\b[^\r\n]{0,65}?\s+(-?(?:\d{1,3}(?:[ \u00a0\u202f]\d{3})+|\d{1,12})(?:[.,]\d{1,2})?)\s*(?:₽|руб(?:\.|ля|лей)?|RUB)(?=$|[\s,;.!?])"""
    )
    private val cardPattern = Regex(
        """(?iu)(?:карт(?:а|е|ы|ой)?|сч[её]т(?:а|е|у)?)\b[^\r\n]{0,32}?[•*xX]{1,8}\s*(\d{4})(?!\d)"""
    )
    private val maskedPattern = Regex("""[•*]{2,8}\s*(\d{4})(?!\d)""")

    fun parse(sourcePackage: String, text: String): Result? {
        if (sourcePackage != SBER_PACKAGE || text.length > 8_192) return null
        val match = balancePattern.find(text) ?: return null
        val normalized = match.groupValues[1].replace(" ", "")
            .replace("\u00a0", "").replace("\u202f", "").replace(',', '.')
        val value = try {
            BigDecimal(normalized).movePointRight(2).longValueExact()
        } catch (_: ArithmeticException) {
            return null
        } catch (_: NumberFormatException) {
            return null
        }
        if (value < 0L || value > 999_999_999_999L) return null
        val lastFour = cardPattern.find(text)?.groupValues?.get(1)
            ?: maskedPattern.find(text)?.groupValues?.get(1)
        return Result(value, lastFour)
    }
}
