package com.github.russoturissto.buttonapp.app

import java.math.BigDecimal

/**
 * Strict, offline extractor for explicitly stated balances in Sber Push notifications.
 * Never subtracts an operation amount to fabricate a bank balance.
 */
internal object SberPushParser {
    const val SBER_PACKAGE = "ru.sberbankmobile"
    enum class Reason {
        ACCEPTED, OTHER_APPLICATION, NO_BALANCE, AMBIGUOUS_BALANCE,
        AMBIGUOUS_ACCOUNT, INVALID_BALANCE
    }
    enum class Confidence { EXPLICIT, CONTEXTUAL }
    data class Result(
        val kopecks: Long,
        val lastFour: String?,
        val confidence: Confidence
    )
    data class Assessment(val result: Result?, val reason: Reason)

    private data class Anchor(val pattern: Regex, val confidence: Confidence)

    // Explicit phrases take priority over transaction verbs and amounts.
    // "На лапках" is an observed Sber-style equivalent of the remaining funds.
    private val anchors = listOf(
        Anchor(Regex("""(?iu)\b(?:остат(?:ок|ка|ке)|баланс|доступно|осталось)\b"""),
            Confidence.EXPLICIT),
        Anchor(Regex("""(?iu)\bна\s+лапках\b"""), Confidence.EXPLICIT),
        Anchor(Regex("""(?iu)\b(?:теперь|сейчас)\s+у\s+тебя\b"""), Confidence.EXPLICIT),
        Anchor(Regex("""(?iu)\bу\s+тебя\s+осталось\b"""), Confidence.EXPLICIT),
        Anchor(Regex("""(?iu)\b(?:свободные\s+средства|доступный\s+остаток)\b"""),
            Confidence.EXPLICIT),
        // These words are ambiguous in running text. Require a separate clause.
        Anchor(Regex("""(?imu)(?:^|[.!?;—–]\s*)\s*(?:на\s+(?:сч[её]те|счету|карт(?:е|очке))|в\s+(?:кошельке|копилке))\b"""),
            Confidence.CONTEXTUAL)
    )
    // Amounts must carry an explicit ruble currency unit.
    private val moneyPattern = Regex(
        """(?iu)(?<![\d.,])([+-]?(?:\d{1,3}(?:[ \u00a0\u202f]\d{3})+|\d{1,12})(?:[.,]\d{1,2})?)\s*(?:₽|руб(?:\.|ля|лей)?|RUB|р\.)(?![a-zа-я0-9])"""
    )
    private val maskedAccountPattern =
        Regex("""(?iu)[•●*xX]{1,8}\s*(\d{4})(?!\d)""")
    private val bonuses = Regex("""(?iu)\b(?:бонус(?:ы|ов|ами|ный)?|спасибо|балл(?:ы|ов)|кешб[эе]к)\b""")

    fun parse(sourcePackage: String, text: String): Result? =
        analyze(sourcePackage, text).result

    fun analyze(sourcePackage: String, text: String): Assessment {
        if (sourcePackage != SBER_PACKAGE || text.length > 8_192) {
            return Assessment(null, Reason.OTHER_APPLICATION)
        }
        // Never use bonus/loyalty "balances" as cash. Only skip a bonus-specific
        // clause, not a genuine cash balance elsewhere in the same notification.
        val found = mutableListOf<Pair<Long, Confidence>>()
        var invalidCandidate = false
        for (anchor in anchors) {
            for (match in anchor.pattern.findAll(text)) {
                val prefix = text.substring(maxOf(0, match.range.first - 18), match.range.first)
                if (bonuses.containsMatchIn(prefix)) continue
                val tailEnd = minOf(text.length, match.range.last + 1 + 72)
                val tail = text.substring(match.range.last + 1, tailEnd)
                    .substringBefore('\n').substringBefore('\r')
                // A new sentence marks a new operation: never borrow its money
                // amount as the balance from the previous sentence.
                val clauseBreak = Regex("""[.!?;]\s+(?=\p{L})""").find(tail)
                val clause = if (clauseBreak == null) tail
                    else tail.substring(0, clauseBreak.range.first)
                val amount = moneyPattern.find(clause) ?: continue
                if (bonuses.containsMatchIn(clause.substring(0, amount.range.first))) continue
                // The balance should be attached to the label. A distant amount
                // is much more likely to be a transaction or marketing number.
                if (amount.range.first > 38) continue
                val digits = amount.groupValues[1]
                    .replace(" ", "").replace("\u00a0", "")
                    .replace("\u202f", "").replace(',', '.')
                val kopecks = try {
                    BigDecimal(digits).movePointRight(2).longValueExact()
                } catch (_: ArithmeticException) {
                    invalidCandidate = true
                    continue
                } catch (_: NumberFormatException) {
                    invalidCandidate = true
                    continue
                }
                if (kopecks < 0L || kopecks > 999_999_999_999L) {
                    invalidCandidate = true
                    continue
                }
                found.add(kopecks to anchor.confidence)
            }
        }
        if (invalidCandidate) return Assessment(null, Reason.INVALID_BALANCE)
        if (found.isEmpty()) return Assessment(null, Reason.NO_BALANCE)
        if (found.map { it.first }.distinct().size != 1) {
            return Assessment(null, Reason.AMBIGUOUS_BALANCE)
        }
        val accounts = maskedAccountPattern.findAll(text)
            .map { it.groupValues[1] }.distinct().toList()
        if (accounts.size > 1) return Assessment(null, Reason.AMBIGUOUS_ACCOUNT)
        val confidence = if (found.any { it.second == Confidence.EXPLICIT }) {
            Confidence.EXPLICIT
        } else Confidence.CONTEXTUAL
        return Assessment(Result(found.first().first, accounts.firstOrNull(), confidence),
            Reason.ACCEPTED)
    }
}
