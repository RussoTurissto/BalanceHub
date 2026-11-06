package com.github.russoturissto.buttonapp.app

import java.util.Calendar
import java.util.GregorianCalendar

/** Parses only values explicitly present in T2's USSD message. */
internal data class T2UssdSnapshot(
    val balance: String? = null,
    val planMinutes: String? = null,
    val planGigabytes: String? = null,
    val savedMinutes: String? = null,
    val savedGigabytes: String? = null,
    val packagesExpireAt: Long? = null
) {
    val hasRecognizedData: Boolean
        get() = balance != null || planMinutes != null || planGigabytes != null ||
            savedMinutes != null || savedGigabytes != null || packagesExpireAt != null
}

internal object T2UssdParser {
    private val balanceRegex = Regex(
        """(?iu)(?:баланс|остаток\s+средств|на\s+счет[еу])\s*[:\-–]?\s*(-?\d[\d ]*(?:[.,]\d{1,2})?)\s*(?:₽|руб(?:лей|ля|\.|лям)?)?"""
    )
    private val planMinutesRegex = Regex("""(?iu)(\d[\d ]*)\s*мин\.?\s+на\s+остальн""")
    private val planGbRegex = Regex("""(?iu)(\d+(?:[.,]\d{1,2})?)\s*ГБ\s+из\s+пакетов\s+по\s+тарифу""")
    private val savedRegex = Regex(
        """(?iu)накопленные\s+остатки\s*:\s*(\d[\d ]*)\s*мин\.?\s*и\s*(\d[\d ]*(?:[.,]\d{1,2})?)\s*ГБ"""
    )
    private val expirationRegex = Regex(
        """(?iu)пакеты\s+действуют\s+до\s+(\d{1,2})\.(\d{1,2})\.(\d{2,4})\s+(\d{1,2}):(\d{2})"""
    )
    private fun amount(value: String): String = value.replace(" ", "").replace(',', '.')
    fun parse(response: String): T2UssdSnapshot {
        val text = response.replace('\u00a0', ' ').replace(Regex("""\s+"""), " ")
        val saved = savedRegex.find(text)
        val expires = expirationRegex.find(text)?.groupValues?.let { parts ->
            try {
                val yearPart = parts[3].toInt()
                val year = if (yearPart < 100) 2000 + yearPart else yearPart
                val cal = GregorianCalendar().apply {
                    isLenient = false
                    clear()
                    set(year, parts[2].toInt() - 1, parts[1].toInt(),
                        parts[4].toInt(), parts[5].toInt())
                }
                cal.timeInMillis
            } catch (_: IllegalArgumentException) { null }
        }
        return T2UssdSnapshot(
            balance = balanceRegex.find(text)?.groupValues?.get(1)?.let(::amount),
            planMinutes = planMinutesRegex.find(text)?.groupValues?.get(1)?.let(::amount),
            planGigabytes = planGbRegex.find(text)?.groupValues?.get(1)?.let(::amount),
            savedMinutes = saved?.groupValues?.get(1)?.let(::amount),
            savedGigabytes = saved?.groupValues?.get(2)?.let(::amount),
            packagesExpireAt = expires
        )
    }

    /** Only a 23:59 package expiry supports the agreed *estimated* next-day debit. */
    fun estimatedNextDebit(expiresAt: Long?): Long? {
        if (expiresAt == null) return null
        val date = Calendar.getInstance().apply { timeInMillis = expiresAt }
        if (date.get(Calendar.HOUR_OF_DAY) != 23 || date.get(Calendar.MINUTE) != 59) {
            return null
        }
        date.add(Calendar.DAY_OF_YEAR, 1)
        date.set(Calendar.HOUR_OF_DAY, 9)
        date.set(Calendar.MINUTE, 0)
        date.set(Calendar.SECOND, 0)
        date.set(Calendar.MILLISECOND, 0)
        return date.timeInMillis
    }
}
