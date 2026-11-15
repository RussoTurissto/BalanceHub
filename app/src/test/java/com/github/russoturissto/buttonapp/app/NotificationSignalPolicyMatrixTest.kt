package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every user-selectable sound and vibration pairing, in every category. */
class NotificationSignalPolicyMatrixTest {
    @Test fun all255CombinationsAgreeWithBothAndroidDeliveryModes() {
        val categories = listOf(BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT, BalanceNotificationSounds.UPDATE)
        val sounds = listOf("system") +
            BalanceNotificationSounds.tones.map { it.key } + listOf("silent")
        val modes = NotificationVibrationMode.options
        assertEquals(17, sounds.distinct().size)
        assertEquals(5, modes.distinct().size)
        var combinations = 0
        for (category in categories) {
            assertTrue(category.isNotBlank())
            for (sound in sounds) for (mode in modes) {
                combinations++
                val active = NotificationVibrationMode.enabled(mode)
                for (compatibility in listOf(false, true)) {
                    val native = NotificationSignalPolicy.nativeVibrationEnabled(
                        mode, compatibility)
                    val direct = NotificationSignalPolicy.directVibrationEnabled(
                        mode, compatibility)
                    assertEquals(active, native || direct)
                    assertFalse("Duplicate vibration for $category/$sound/$mode",
                        native && direct)
                    assertEquals(sound != "silent",
                        NotificationSignalPolicy.soundEnabled(sound, 100))
                    assertEquals(sound == "system",
                        NotificationSignalPolicy.soundEnabled(sound, 0))
                    assertEquals(mode == NotificationVibrationMode.OFF, !native && !direct)
                    if (mode == NotificationVibrationMode.LONG)
                        assertTrue(NotificationVibrationMode.pattern(mode)!!
                            .contentEquals(longArrayOf(0L, 450L)))
                }
            }
        }
        assertEquals(255, combinations)
    }

    @Test fun alternativeOnePlusMediaStoreUriIsRecognizedForEveryCategory() {
        val primary = "content://media/external_primary/audio/media/321"
        val picker = "content://media/external/audio/media/321"
        for (category in listOf(BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT, BalanceNotificationSounds.UPDATE)) {
            assertTrue(category, NotificationSignalPolicy.sameSoundUri(primary, picker))
            assertFalse(category, NotificationSignalPolicy.sameSoundUri(primary,
                "content://media/external/audio/media/322"))
        }
    }
}
