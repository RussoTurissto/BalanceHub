package com.github.russoturissto.buttonapp.app

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * System-channel tests, run on real Android OS emulators.
 * These tests validate actual NotificationManager state, not UI preferences.
 */
@RunWith(AndroidJUnit4::class)
class NotificationChannelEmulatorTest {
    private val app: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun manager(): NotificationManager =
        app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test fun allThreeChannelsHaveEffectiveSoundAndVibrationOnFreshInstall() {
        val context = app
        BalanceNotificationSounds.ensureDefaultChannels(context)
        val channels = listOf(
            BalanceNotificationSounds.UPDATE, BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT)
        if (Build.VERSION.SDK_INT >= 26) {
            for (category in channels) {
                val channel = manager().getNotificationChannel(
                    BalanceNotificationSounds.channelId(context, category))
                assertNotNull("Channel missing " + category, channel)
                assertTrue("Importance silent " + category,
                    channel!!.importance >= NotificationManager.IMPORTANCE_DEFAULT)
                assertNotNull("Sound missing " + category, channel.sound)
                assertTrue("No vibration " + category, channel.shouldVibrate())
            }
        }
    }

    @Test fun changingMelodyAndVibrationPersistsAfterSettingsRecreated() {
        val context = app
        val category = BalanceNotificationSounds.EVENT
        BalanceNotificationSounds.ensureDefaultChannels(context)
        if (Build.VERSION.SDK_INT < 26) return
        val first = BalanceNotificationSounds.applyUserChoice(context, category, "crystal", true)
        assertNull("Initial tone failed: " + first, first)
        val firstChannelId = BalanceNotificationSounds.channelId(context, category)
        var channel = manager().getNotificationChannel(firstChannelId)
        assertNotNull(channel)
        assertTrue(channel!!.shouldVibrate())
        assertNotNull(channel.sound)
        assertNull("System URI unreadable",
            BalanceNotificationSounds.audioProbe(context, channel.sound!!))
        // Same code path as returning from Android Settings and the app's next launch.
        BalanceNotificationSounds.ensureDefaultChannels(context)
        channel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(context, category))
        assertTrue("Vibration lost during reinitialization", channel!!.shouldVibrate())
        assertNotNull("Sound lost during reinitialization", channel.sound)
        val second = BalanceNotificationSounds.applyVibrationChoice(context, category, false)
        assertNull("Disable vibration failed: " + second, second)
        channel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(context, category))
        assertFalse(channel!!.shouldVibrate())
        assertNotNull("Tone lost when disabling vibration", channel.sound)
        val third = BalanceNotificationSounds.applyVibrationChoice(context, category, true)
        assertNull("Reenable vibration failed: " + third, third)
        BalanceNotificationSounds.ensureDefaultChannels(context)
        channel = manager().getNotificationChannel(
            BalanceNotificationSounds.channelId(context, category))
        assertTrue("Vibration not restored", channel!!.shouldVibrate())
        assertNotNull("Tone not restored", channel.sound)
    }

    @Test fun customToneIsReadableBySeparateShellProcess() {
        if (Build.VERSION.SDK_INT < 26) return
        val context = app
        val uri = Uri.parse("content://" + context.packageName +
            ".sounds/crystal/v3/100")
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("content read --uri " + uri.toString())
        result.use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).use { stream ->
                val bytes = ByteArray(12)
                var count = 0
                while (count < 12) {
                    val n = stream.read(bytes, count, 12 - count)
                    if (n <= 0) break
                    count += n
                }
                assertEquals("Android shell cannot open BalanceHub provider",12,count)
                assertEquals("RIFF",String(bytes,0,4))
                assertEquals("WAVE",String(bytes,8,4))
            }
        }
    }
}
