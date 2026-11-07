package com.github.russoturissto.buttonapp.app

import android.content.res.Resources

/** Diagnostic clues only: never tries to undo Android's notification redaction. */
internal object SberNotificationPrivacy {
    enum class Evidence { NONE, SYSTEM_REDACTION, POSSIBLE_REDACTION, NO_FINANCIAL_DATA }

    fun localizedSystemMessage(): String? = try {
        val res = Resources.getSystem()
        val id = res.getIdentifier("redacted_notification_message", "string", "android")
        if (id != 0) res.getString(id) else null
    } catch (_: Resources.NotFoundException) { null }
      catch (_: SecurityException) { null }

    fun detect(
        titles: List<String>, bodies: List<String>, rublesMask: Int,
        cuesMask: Int, accountMask: Int, systemText: String?
    ): Evidence {
        val known = listOfNotNull(systemText?.takeIf { it.isNotBlank() },
            "Sensitive notification content hidden").map(SberPushSignals::normalize)
        if (bodies.any { body -> known.any {
                it.equals(SberPushSignals.normalize(body), ignoreCase = true)
            } }) return Evidence.SYSTEM_REDACTION

        if (rublesMask != 0 || cuesMask != 0 || accountMask != 0) return Evidence.NONE
        if (bodies.isEmpty()) return Evidence.NO_FINANCIAL_DATA
        val titlesAreApp = titles.isNotEmpty() && titles.all {
            val t = SberPushSignals.normalize(it)
            t.equals("Сбербанк", true) || t.equals("Сбербанк Онлайн", true)
        }
        // A weak indicator only. Generic bank alerts can look identical.
        return if (titlesAreApp && bodies.map(SberPushSignals::normalize).distinct().size == 1)
            Evidence.POSSIBLE_REDACTION else Evidence.NO_FINANCIAL_DATA
    }
}
