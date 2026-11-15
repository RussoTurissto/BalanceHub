package com.github.russoturissto.buttonapp.app

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorOsCompatibilityEmulatorTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() =
        app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test fun optInRecreatesQuietChannelsAndRestoreKeepsSelectedVibration() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // The suite runs on fresh emulator installs, not the user's phone.
        if (BalanceNotificationSounds.colorOsCompatibility(app)) return
        val categories = listOf(BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT, BalanceNotificationSounds.UPDATE)
        val originalIds = categories.associateWith { BalanceNotificationSounds.ensureChannel(app, it) }
        val originalKeys = categories.associateWith { BalanceNotificationSounds.selected(app, it) }
        val originalModes = categories.associateWith {
            BalanceNotificationSounds.vibrationMode(app, it)
        }
        val issue = BalanceNotificationSounds.changeColorOsCompatibility(app, true)
        assertNull("Unable to enable ColorOS compatibility: " + issue, issue)
        try {
            assertTrue(BalanceNotificationSounds.colorOsCompatibility(app))
            for (category in categories) {
                val id = BalanceNotificationSounds.channelId(app, category)
                assertNotEquals("Channel must be replaced once", originalIds[category], id)
                val channel = manager.getNotificationChannel(id)
                assertNotNull(channel)
                assertFalse("Native vibration must be off in compatibility mode",
                    channel!!.shouldVibrate())
                assertEquals(originalKeys[category], BalanceNotificationSounds.selected(app, category))
                assertEquals(originalModes[category],
                    BalanceNotificationSounds.vibrationMode(app, category))
            }
        } finally {
            val undo = BalanceNotificationSounds.changeColorOsCompatibility(app, false)
            assertNull("Compatibility must be reversible: " + undo, undo)
        }
        assertFalse(BalanceNotificationSounds.colorOsCompatibility(app))
        for (category in categories) {
            val channel = manager.getNotificationChannel(BalanceNotificationSounds.channelId(
                app, category))
            assertNotNull(channel)
            assertEquals(NotificationVibrationMode.enabled(originalModes[category]!!),
                channel!!.shouldVibrate())
        }
    }

    @Test fun disabledByDefaultWithoutUserOptIn() {
        // This test only checks the flag, not user data. It must never force-enable.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        assertFalse(BalanceNotificationSounds.colorOsCompatibility(app))
    }
}
