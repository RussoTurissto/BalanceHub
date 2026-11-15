package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Payment-only auto sound must never be assumed active without user opt-in. */
class PaymentAutomaticSoundPolicyTest {
    @Test fun soundAndVibrationMatrix() {
        val sounds = listOf("system", "silent") + BalanceNotificationSounds.tones.map { it.key }
        assertEquals(17, sounds.size)
        assertEquals(5, NotificationVibrationMode.options.size)
        var tested = 0
        for (sound in sounds) for (mode in NotificationVibrationMode.options) {
            assertEquals(sound != "silent",
                NotificationSignalPolicy.soundEnabled(sound, 100))
            assertEquals(mode != NotificationVibrationMode.OFF,
                NotificationSignalPolicy.directVibrationEnabled(mode, true))
            assertFalse(NotificationSignalPolicy.nativeVibrationEnabled(mode, true))
            tested++
        }
        assertEquals(85, tested)
    }

    @Test fun gainZeroDisablesBundledAudioButNotDefaultRingtone() {
        assertFalse(NotificationSignalPolicy.soundEnabled("drop", 0))
        assertTrue(NotificationSignalPolicy.soundEnabled("system", 0))
        assertFalse(NotificationSignalPolicy.soundEnabled("silent", 100))
    }
}
