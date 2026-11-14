package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Check OS-gate semantics independently from the emulator's actual motor. */
class ColorOsDirectVibrationPolicyTest {
    private fun allowed(
        compatibility: Boolean = true,
        wantsVibration: Boolean = true,
        notificationsEnabled: Boolean = true,
        channelCanAlert: Boolean = true,
        systemChannelVibrates: Boolean = false,
        silentMode: Boolean = false,
        normalNotificationPolicy: Boolean = true
    ) = ColorOsDirectVibration.canVibrate(
        compatibility, wantsVibration, notificationsEnabled,
        channelCanAlert, systemChannelVibrates, silentMode, normalNotificationPolicy
    )

    @Test fun allowedInOrdinaryAndVibrateModeWithQuietChannel() {
        assertTrue(allowed())
    }

    @Test fun optInIsRequiredAndOffPreferenceIsHonored() {
        assertFalse(allowed(compatibility = false))
        assertFalse(allowed(wantsVibration = false))
        assertFalse(allowed(notificationsEnabled = false))
    }

    @Test fun neverVibrateWithMutedNotificationsOrDnd() {
        assertFalse(allowed(channelCanAlert = false))
        assertFalse(allowed(silentMode = true))
        assertFalse(allowed(normalNotificationPolicy = false))
    }

    @Test fun systemChannelVibrationWinsToAvoidDuplicatePulse() {
        assertFalse(allowed(systemChannelVibrates = true))
    }
}
