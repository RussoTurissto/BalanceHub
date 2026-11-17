package com.github.russoturissto.buttonapp.app

import java.math.BigDecimal

/**
 * Conservative offline extraction of ruble balances from all Sber notification tones.
 * A transfer, purchase, cashback or credit amount alone is never a balance.
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
    // A notification may explicitly report distinct balances for distinct accounts.
    // result remains compatible with single-balance callers; balances carries all safe matches.
    data class Assessment(
        val result: Result?,
        val reason: Reason,
        val balances: List<Result> = listOfNotNull(result)
    )

    private data class Anchor(val regex: Regex, val confidence: Confidence)
    private data class Candidate(
        val amount: Long,
        val account: String?,
        val confidence: Confidence,
        val accountAmbiguous: Boolean
    )

    private val anchors = listOf(
        Anchor(Regex("""(?iu)\b(?:остат(?:ок|ка|ке)|баланс|доступно|осталось)\b"""),
            Confidence.EXPLICIT),
        Anchor(Regex("""(?iu)\bна\s+лапках\b"""), Confidence.EXPLICIT),
        Anchor(Regex("""(?iu)\b(?:теперь|сейчас)\s+у\s+(?:тебя|вас)\b"""),
            Confidence.EXPLICIT),
        Anchor(Regex("""(?iu)\bу\s+(?:тебя|вас)\s+осталось\b"""),
            Confidence.EXPLICIT),
        Anchor(Regex("""(?iu)\b(?:свободные\s+средства|доступные\s+средства|доступный\s+остаток)\b"""),
            Confidence.EXPLICIT),
        // Contextual labels must start a clause, and may not borrow a purchase sum.
        Anchor(Regex("""(?imu)(?:^|[.!?;—–]\s*)\s*(?:на\s+(?:сч[её]те|счету|карт(?:е|очке))|в\s+(?:кошельке|копилке))\b"""),
            Confidence.CONTEXTUAL)
    )
    private val moneyPattern = Regex(
        """(?iu)(?<![\d.,])([+-]?(?:\d{1,3}(?:[ \u00a0\u202f]\d{3})+|\d{1,12})(?:[.,]\d{1,2})?)\s*(?:₽|руб(?:\.|ля|лей)?|RUB|р\.)(?![a-zа-я0-9])"""
    )
    private val maskedAccountPattern =
        Regex("""(?iu)[•●*xX]{1,8}\s*(\d{4})(?!\d)""")
    private val bonuses = Regex(
        """(?iu)\b(?:бонус(?:ы|ов|ами|ный|ного|ных)?|спасибо|балл(?:ы|ов)|кешб[эе]к|кэшб[эе]к)\b"""
    )
    private val nonCash = Regex(
        """(?iu)\b(?:кредит\p{L}*|задолженност\p{L}*|долг(?:а|ов)?|лимит\s+кредит)\b"""
    )
    private val operation = Regex(
        """(?iu)\b(?:покупк\p{L}*|оплат\p{L}*|списани\p{L}*|перевод\p{L}*|поступлени\p{L}*|пополнени\p{L}*|начислени\p{L}*)\b"""
    )
    private val clauseBreak = Regex("""[.!?;]\s+(?=\p{L})""")
    private val nextBalance = Regex(
        """(?iu)\b(?:остат(?:ок|ка|ке)|баланс|доступно|на\s+лапках|теперь\s+у\s+(?:тебя|вас))\b"""
    )

    fun parse(sourcePackage: String, text: String): Result? =
        analyze(sourcePackage, text).result

    fun analyze(sourcePackage: String, text: String): Assessment {
        if (sourcePackage != SBER_PACKAGE || text.length > 8_192) {
            return Assessment(null, Reason.OTHER_APPLICATION)
        }
        val value = SberPushSignals.normalize(text)
        val allAccounts = maskedAccountPattern.findAll(value)
            .map { it.groupValues[1] }.distinct().toList()
        val found = mutableListOf<Candidate>()
        var invalid = false

        for (anchor in anchors) {
            for (label in anchor.regex.findAll(value)) {
                val before = value.substring(maxOf(0, label.range.first - 60),
                    label.range.first).split(Regex("""[.!?;]\s+""")).last()
                // A balance labelled as loyalty points or a credit limit is not cash.
                if (bonuses.containsMatchIn(before) || nonCash.containsMatchIn(before)) continue
                val end = minOf(value.length, label.range.last + 1 + 96)
                val tail = value.substring(label.range.last + 1, end)
                val boundary = clauseBreak.find(tail)?.range?.first ?: tail.length
                val clause = tail.take(boundary)
                val money = moneyPattern.find(clause) ?: continue
                val between = clause.substring(0, money.range.first)
                if (bonuses.containsMatchIn(between) || nonCash.containsMatchIn(between) ||
                    bonuses.containsMatchIn(clause.substring(money.range.last + 1))) continue
                if (money.range.first > 48) continue
                // A playful "Баланс распушился — перевод +10 ₽" is not a balance.
                if (operation.containsMatchIn(between) &&
                    (anchor.confidence == Confidence.CONTEXTUAL ||
                        label.value.equals("баланс", ignoreCase = true))) continue

                val kopecks = try {
                    BigDecimal(money.groupValues[1].replace(" ", "")
                        .replace("\u00a0", "").replace("\u202f", "")
                        .replace(',', '.')).movePointRight(2).longValueExact()
                } catch (_: ArithmeticException) {
                    invalid = true
                    continue
                } catch (_: NumberFormatException) {
                    invalid = true
                    continue
                }
                if (kopecks < 0L || kopecks > 999_999_999_999L) {
                    invalid = true
                    continue
                }

                val direct = maskedAccountPattern.findAll(between)
                    .map { it.groupValues[1] }.toMutableList()
                // Sber often puts "Плат. счёт ••1234" AFTER the balance.
                // Never cross into the next explicitly labelled balance.
                val moneyEnd = label.range.last + 1 + money.range.last + 1
                val remaining = value.substring(moneyEnd, minOf(value.length, moneyEnd + 70))
                val next = nextBalance.find(remaining)?.range?.first ?: remaining.length
                val nearby = remaining.take(next)
                direct.addAll(maskedAccountPattern.findAll(nearby).map { it.groupValues[1] })
                val localAccounts = direct.distinct()
                val ambiguous = localAccounts.size > 1 ||
                    (localAccounts.isEmpty() && allAccounts.size > 1)
                val account = localAccounts.singleOrNull() ?: allAccounts.singleOrNull()
                found.add(Candidate(kopecks, account, anchor.confidence, ambiguous))
            }
        }

        if (invalid) return Assessment(null, Reason.INVALID_BALANCE)
        if (found.isEmpty()) return Assessment(null, Reason.NO_BALANCE)
        if (found.any { it.accountAmbiguous }) {
            return Assessment(null, Reason.AMBIGUOUS_ACCOUNT)
        }

        // Strongly linked candidates take priority over copies without an account
        // (for example EXTRA_TEXT and EXTRA_BIG_TEXT of the same operation).
        val linked = found.filter { it.account != null }
        val unknown = found.filter { it.account == null }
        if (linked.isNotEmpty() &&
            unknown.any { u -> linked.none { it.amount == u.amount } }) {
            return Assessment(null, Reason.AMBIGUOUS_ACCOUNT)
        }
        val effective = if (linked.isNotEmpty()) linked else unknown
        val grouped = effective.groupBy { it.account }
        if (grouped.any { (_, items) -> items.map { it.amount }.distinct().size > 1 }) {
            return Assessment(null, Reason.AMBIGUOUS_BALANCE)
        }
        val balances = grouped.map { (account, items) ->
            Result(items.first().amount, account,
                if (items.any { it.confidence == Confidence.EXPLICIT })
                    Confidence.EXPLICIT else Confidence.CONTEXTUAL)
        }
        if (balances.size > 1 && balances.any { it.lastFour == null }) {
            return Assessment(null, Reason.AMBIGUOUS_ACCOUNT)
        }
        return Assessment(balances.singleOrNull(), Reason.ACCEPTED, balances)
    }
}
