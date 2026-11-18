package com.github.russoturissto.buttonapp.app

/** Pure policy used by the app lock and by JVM regression tests. */
internal object AppLockPolicy {
    const val IMMEDIATE = "immediate"
    const val ONE_MINUTE = "one_minute"
    const val FIVE_MINUTES = "five_minutes"
    const val DEVICE_LOCK = "device_lock"

    fun lockoutMillis(failedAttempts: Int): Long = when {
        failedAttempts < 5 -> 0L
        failedAttempts < 10 -> 30_000L
        failedAttempts < 15 -> 120_000L
        else -> 300_000L
    }

    fun shouldRelock(mode: String, backgroundMillis: Long, deviceWasLocked: Boolean): Boolean =
        when (mode) {
            IMMEDIATE -> true
            ONE_MINUTE -> backgroundMillis >= 60_000L
            FIVE_MINUTES -> backgroundMillis >= 300_000L
            DEVICE_LOCK -> deviceWasLocked
            else -> true
        }

    fun validPin(pin: String): Boolean =
        pin.length == 4 && pin.all { it in '0'..'9' }
}
