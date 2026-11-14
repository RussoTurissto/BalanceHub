package com.github.russoturissto.buttonapp.app

/**
 * User-facing vibration choices. Android may adapt patterns to device hardware;
 * notification channels remain authoritative for real system notifications.
 */
internal object NotificationVibrationMode {
    const val OFF = "off"
    const val DEFAULT = "default"
    const val SHORT = "short"
    const val LONG = "long"
    const val DOUBLE = "double"

    val options = listOf(OFF, DEFAULT, SHORT, LONG, DOUBLE)

    fun valid(value: String): Boolean = value in options

    fun title(value: String): String = when (value) {
        OFF -> "Выключено"
        SHORT -> "Короткий"
        LONG -> "Длинный"
        DOUBLE -> "Двойной"
        else -> "По умолчанию"
    }

    /** Show a configured pattern separately from what Android currently reports. */
    fun effectiveLabel(requestedMode: String, channelVibrates: Boolean): String {
        val chosen = title(requestedMode)
        return when {
            requestedMode == OFF && !channelVibrates -> chosen
            requestedMode == OFF -> "$chosen (в Android включена)"
            !channelVibrates -> "$chosen (в Android отключена)"
            else -> chosen
        }
    }

    fun enabled(value: String): Boolean = value != OFF

    /** null means the platform's default notification vibration. */
    fun pattern(value: String): LongArray? = when (value) {
        SHORT -> longArrayOf(0L, 110L)
        LONG -> longArrayOf(0L, 450L)
        DOUBLE -> longArrayOf(0L, 130L, 110L, 130L)
        else -> null
    }
}
