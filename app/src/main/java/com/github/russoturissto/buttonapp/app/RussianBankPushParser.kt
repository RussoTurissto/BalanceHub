package com.github.russoturissto.buttonapp.app

import java.math.BigDecimal

/**
 * Conservative offline parser for non-Sber bank notifications.
 * Never subtracts purchases from a balance or interprets credit limits, bonuses
 * or transaction amounts as the available balance.
 */
internal object RussianBankPushParser {
    data class Result(val kopecks: Long, val lastFour: String?)
    enum class Reason { ACCEPTED, NOT_SUPPORTED, NO_BALANCE, AMBIGUOUS, INVALID }
    data class Assessment(val result: Result?, val reason: Reason)

    private val balanceCue = Regex(
        """(?iu)(?<!\p{L})(?:остат(?:ок|ка|ке)|баланс|доступно|осталось|balans|balance|available balance)(?!\p{L})"""
    )
    private val money = Regex(
        """(?iu)(?<![\d.,])([+-]?(?:\d{1,3}(?:[ \u00a0\u202f]\d{3})+|\d{1,12})(?:[.,]\d{1,2})?)\s*(?:₽|руб(?:\.|ля|лей)?|RUB|р\.)(?![a-zа-я0-9])"""
    )
    private val invalidContext = Regex(
        """(?iu)(?:кредит\p{L}*|лимит\p{L}*|овердрафт\p{L}*|бонус\p{L}*|балл\p{L}*|к[эе]шб[эе]к\p{L}*|спасибо|рассрочк\p{L}*|покупк\p{L}*|списани\p{L}*|перевод\p{L}*|пополнени\p{L}*)"""
    )
    private val masked = Regex("""[•●*xX]{1,8}\s*(\d{4})(?!\d)""")

    fun analyze(packageName: String, bodies: List<String>, titles: List<String> =
        emptyList()): Assessment {
        val bank = RussianBankCatalog.byPackage(packageName)
        if (bank == null || bank.id == "sber") return Assessment(null, Reason.NOT_SUPPORTED)
        val parts = bodies.ifEmpty { titles }
            .filter { it.isNotBlank() && it.length <= 8192 }
            .map(SberPushSignals::normalize)
        val candidates = mutableSetOf<Long>()
        val accounts = mutableSetOf<String>()
        var invalid = false
        for (part in parts) {
            for (cue in balanceCue.findAll(part)) {
                // Never interpret a bonus balance or available credit as cash.
                val before = part.substring(maxOf(0, cue.range.first - 25), cue.range.first)
                if (Regex("""(?iu)(?:бонус|балл|к[эе]шб[эе]к|кредит|лимит)""").containsMatchIn(before))
                    continue
                val tail = part.substring(cue.range.last + 1,
                    minOf(part.length, cue.range.last + 1 + 58))
                val nextSentence = Regex("""[.!?;]\s+(?=\p{L})""").find(tail)
                val clause = if (nextSentence == null) tail else tail.substring(0,
                    nextSentence.range.first)
                val m = money.find(clause) ?: continue
                val between = clause.substring(0, m.range.first)
                if (m.range.first > 40 || invalidContext.containsMatchIn(between))
                    continue
                val amount = try {
                    BigDecimal(m.groupValues[1].replace(" ", "")
                        .replace("\u00a0", "").replace("\u202f", "")
                        .replace(',', '.')).movePointRight(2).longValueExact()
                } catch (_: Exception) {
                    invalid = true
                    continue
                }
                if (amount < 0L || amount > 999_999_999_999L) {
                    invalid = true
                    continue
                }
                candidates.add(amount)
                masked.findAll(part).forEach { accounts.add(it.groupValues[1]) }
            }
        }
        if (invalid) return Assessment(null, Reason.INVALID)
        if (candidates.isEmpty()) return Assessment(null, Reason.NO_BALANCE)
        if (candidates.size > 1 || accounts.size > 1) return Assessment(null, Reason.AMBIGUOUS)
        return Assessment(Result(candidates.first(), accounts.firstOrNull()), Reason.ACCEPTED)
    }
}
