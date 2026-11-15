package com.github.russoturissto.buttonapp.app

/**
 * Pure, testable composition of Android notification fields. Accepts only one
 * unambiguous explicitly-labelled ruble balance across all readable fields.
 */
internal object SberPushEvaluation {
    fun analyze(
        packageName: String,
        payload: SberNotificationText.Payload
    ): SberPushParser.Assessment {
        val candidates = linkedSetOf<String>()
        for (body in payload.bodies) {
            candidates.add(body)
            for (title in payload.titles) {
                if (title.length + body.length < 8_190) {
                    candidates.add("$title $body")
                }
            }
        }
        if (payload.bodies.size > 1) {
            val joined = payload.bodies.joinToString(" ")
            if (joined.length <= 8_192) candidates.add(joined)
        }
        if (candidates.isEmpty()) candidates.addAll(payload.titles)
        if (candidates.isEmpty()) {
            return SberPushParser.Assessment(null, SberPushParser.Reason.NO_BALANCE)
        }
        val assessments = candidates.map { SberPushParser.analyze(packageName, it) }
        for (reason in listOf(
            SberPushParser.Reason.AMBIGUOUS_BALANCE,
            SberPushParser.Reason.AMBIGUOUS_ACCOUNT,
            SberPushParser.Reason.INVALID_BALANCE
        )) {
            if (assessments.any { it.reason == reason }) {
                return SberPushParser.Assessment(null, reason)
            }
        }
        val accepted = assessments.mapNotNull { it.result }
        if (accepted.isEmpty()) return assessments.first()
        if (accepted.map { it.kopecks }.distinct().size > 1) {
            return SberPushParser.Assessment(null, SberPushParser.Reason.AMBIGUOUS_BALANCE)
        }
        val accounts = accepted.mapNotNull { it.lastFour }.distinct()
        if (accounts.size > 1) {
            return SberPushParser.Assessment(null, SberPushParser.Reason.AMBIGUOUS_ACCOUNT)
        }
        val selected = accepted.firstOrNull { it.lastFour != null } ?: accepted.first()
        return SberPushParser.Assessment(
            selected, SberPushParser.Reason.ACCEPTED
        )
    }
}
