package com.github.russoturissto.buttonapp.app

/**
 * Interprets Android notification fields without treating unrelated fragments as
 * one financial statement. Only unambiguous explicitly-labelled ruble balances pass.
 */
internal object SberPushEvaluation {
    private val operationCue = Regex(
        """(?iu)\b(?:покупк\p{L}*|перевод\p{L}*|списани\p{L}*|поступлени\p{L}*|оплат\p{L}*|пополнени\p{L}*)\b"""
    )

    // A continuation must begin with an unsigned balance amount and cannot
    // itself describe a transaction. A "+10 ₽ перевод" must never be promoted.
    private fun safeContinuation(text: String): Boolean {
        val normalized = SberPushSignals.normalize(text)
        return normalized.firstOrNull()?.isDigit() == true &&
            SberPushSignals.inspect(normalized).money &&
            !operationCue.containsMatchIn(normalized)
    }

    fun analyze(
        packageName: String,
        payload: SberNotificationText.Payload
    ): SberPushParser.Assessment {
        if (packageName != SberPushParser.SBER_PACKAGE) {
            return SberPushParser.Assessment(null, SberPushParser.Reason.OTHER_APPLICATION)
        }
        val candidates = linkedSetOf<String>()
        payload.bodies.filter { it.isNotBlank() }.forEach { candidates.add(it) }
        // Real balances can be entirely in a title, including a one-line BigTitle.
        payload.titles.filter { SberPushSignals.inspect(it).balanceCue }
            .forEach { candidates.add(it) }

        // A short title can be a balance label, while EXTRA_TEXT is its value.
        for (title in payload.titles) {
            val titleCue = SberPushSignals.inspect(title)
            if (!titleCue.balanceCue || titleCue.money ||
                title.contains("распуш", ignoreCase = true)) continue
            for (body in payload.bodies) {
                val bodyCue = SberPushSignals.inspect(body)
                if (!bodyCue.balanceCue && safeContinuation(body) &&
                    title.length + body.length + 1 <= 8_192) {
                    candidates.add("$title $body")
                }
            }
        }

        // Some builds split "Теперь у вас:" between two body fields. A prior
        // transaction amount in the first field is fine; only the incomplete
        // label (ending with ':') is joined to an unsigned amount continuation.
        for (i in 0 until payload.bodies.lastIndex) {
            val left = payload.bodies[i].trim()
            val right = payload.bodies[i + 1].trim()
            val leftSignals = SberPushSignals.inspect(left)
            val rightSignals = SberPushSignals.inspect(right)
            if (leftSignals.balanceCue && !rightSignals.balanceCue &&
                safeContinuation(right) && left.endsWith(":") &&
                right.length + left.length + 1 <= 8_192) {
                candidates.add("$left $right")
            }
        }

        if (candidates.isEmpty()) {
            return SberPushParser.Assessment(null, SberPushParser.Reason.NO_BALANCE)
        }
        val assessments = candidates.map { SberPushParser.analyze(packageName, it) }
        for (reason in listOf(
            SberPushParser.Reason.INVALID_BALANCE,
            SberPushParser.Reason.AMBIGUOUS_BALANCE,
            SberPushParser.Reason.AMBIGUOUS_ACCOUNT
        )) {
            if (assessments.any { it.reason == reason }) {
                return SberPushParser.Assessment(null, reason)
            }
        }
        val found = assessments.flatMap { it.balances }
        if (found.isEmpty()) {
            return SberPushParser.Assessment(null, SberPushParser.Reason.NO_BALANCE)
        }

        val linked = found.filter { it.lastFour != null }
        val unlinked = found.filter { it.lastFour == null }
        if (linked.isNotEmpty() &&
            unlinked.any { u -> linked.none { it.kopecks == u.kopecks } }) {
            return SberPushParser.Assessment(
                null, SberPushParser.Reason.AMBIGUOUS_ACCOUNT)
        }
        val effective = if (linked.isNotEmpty()) linked else unlinked
        val grouped = effective.groupBy { it.lastFour }
        if (grouped.any { (_, items) -> items.map { it.kopecks }.distinct().size > 1 }) {
            return SberPushParser.Assessment(
                null, SberPushParser.Reason.AMBIGUOUS_BALANCE)
        }
        val balances = grouped.map { (account, items) ->
            SberPushParser.Result(items.first().kopecks, account,
                if (items.any { it.confidence == SberPushParser.Confidence.EXPLICIT })
                    SberPushParser.Confidence.EXPLICIT
                else SberPushParser.Confidence.CONTEXTUAL)
        }
        return SberPushParser.Assessment(
            balances.singleOrNull(), SberPushParser.Reason.ACCEPTED, balances)
    }
}
