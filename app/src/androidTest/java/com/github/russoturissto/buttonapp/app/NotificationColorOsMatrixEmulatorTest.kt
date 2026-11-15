package com.github.russoturissto.buttonapp.app

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the actual channel-settings transaction for the full 17 x 5 x 3
 * matrix under the OnePlus-compatible delivery contract. No notifications are
 * posted and no physical motor/audio claims are made by these emulator checks.
 */
@RunWith(AndroidJUnit4::class)
class NotificationColorOsMatrixEmulatorTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() =
        app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test fun all255ConfigurationsAreAcceptedAndIsolatedByAndroid() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val categories = listOf(BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT, BalanceNotificationSounds.UPDATE)
        val sounds = listOf("system") +
            BalanceNotificationSounds.tones.map { it.key } + listOf("silent")
        val modes = NotificationVibrationMode.options
        data class Saved(val sound: String, val vibration: String, val gain: Int)
        val originals = categories.associateWith {
            Saved(BalanceNotificationSounds.selected(app, it),
                BalanceNotificationSounds.vibrationMode(app, it),
                BalanceNotificationSounds.volume(app, it))
        }
        val originallyCompatible = BalanceNotificationSounds.colorOsCompatibility(app)
        var checked = 0
        try {
            if (!originallyCompatible) {
                val issue = BalanceNotificationSounds.changeColorOsCompatibility(app, true)
                assertNull("Cannot enable ColorOS delivery: $issue", issue)
            }
            for (category in categories) {
                for (tone in sounds) for (mode in modes) {
                    val previousCategorySettings = categories.filter { it != category }
                        .associateWith {
                            BalanceNotificationSounds.selected(app, it) to
                                BalanceNotificationSounds.vibrationMode(app, it)
                        }
                    val error = BalanceNotificationSounds.applySettings(
                        app, category, tone, mode, 100)
                    assertNull("Rejected $category/$tone/$mode: $error", error)
                    val id = BalanceNotificationSounds.channelId(app, category)
                    val channel = manager.getNotificationChannel(id)
                    assertNotNull("Missing channel $category/$tone/$mode", channel)
                    assertEquals(tone, BalanceNotificationSounds.selected(app, category))
                    assertEquals(mode, BalanceNotificationSounds.vibrationMode(app, category))
                    assertEquals(100, BalanceNotificationSounds.volume(app, category))
                    assertFalse("ColorOS must not duplicate vibration $category/$tone/$mode",
                        channel!!.shouldVibrate())
                    assertEquals("Wrong sound enablement $category/$tone/$mode",
                        tone != "silent", channel.sound != null)
                    if (tone != "system" && tone != "silent") {
                        val indexed = BalanceSystemToneStore.cachedUri(app, tone, 100)
                        assertNotNull("No indexed WAV $tone", indexed)
                        assertTrue("Wrong URI $category/$tone/$mode",
                            NotificationSignalPolicy.sameSoundUri(
                                indexed.toString(), channel.sound?.toString()))
                        assertNull("Unreadable WAV $category/$tone/$mode",
                            BalanceNotificationSounds.audioProbe(app, channel.sound!!))
                    }
                    assertTrue("Configuration mismatch $category/$tone/$mode: " +
                        BalanceNotificationSounds.signalIssues(app, category),
                        BalanceNotificationSounds.signalIssues(app, category).isEmpty())
                    for ((other, selected) in previousCategorySettings) {
                        assertEquals("Changed another category sound: $other",
                            selected.first, BalanceNotificationSounds.selected(app, other))
                        assertEquals("Changed another category vibration: $other",
                            selected.second, BalanceNotificationSounds.vibrationMode(app, other))
                    }
                    checked++
                }
            }
            assertEquals(255, checked)
        } finally {
            // Restore the exact original app choices; never leave the test
            // notification configuration in any category.
            for (category in categories) {
                val saved = originals.getValue(category)
                val issue = BalanceNotificationSounds.applySettings(app,
                    category, saved.sound, saved.vibration, saved.gain)
                assertNull("Restore failed for $category: $issue", issue)
            }
            if (!originallyCompatible) {
                val issue = BalanceNotificationSounds.changeColorOsCompatibility(app, false)
                assertNull("Cannot restore native Android channels: $issue", issue)
            }
        }
    }
}
