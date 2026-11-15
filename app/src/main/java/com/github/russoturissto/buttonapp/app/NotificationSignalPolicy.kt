package com.github.russoturissto.buttonapp.app

/**
 * Category-independent contract used by Payments, Events and Updates.
 * ColorOS plays audio through Android channels and vibration outside them.
 */
internal object NotificationSignalPolicy {
    fun soundEnabled(key: String, gain: Int): Boolean =
        key == "system" || (key != "silent" && gain.coerceIn(0, 100) > 0)

    fun nativeVibrationEnabled(mode: String, colorOsCompatibility: Boolean): Boolean =
        !colorOsCompatibility && NotificationVibrationMode.enabled(mode)

    fun directVibrationEnabled(mode: String, colorOsCompatibility: Boolean): Boolean =
        colorOsCompatibility && NotificationVibrationMode.enabled(mode)

    /** Android's ringtone picker may expose the same MediaStore row via two URIs. */
    fun sameSoundUri(expected: String?, actual: String?): Boolean =
        NotificationToneIdentity.same(expected, actual)
}
