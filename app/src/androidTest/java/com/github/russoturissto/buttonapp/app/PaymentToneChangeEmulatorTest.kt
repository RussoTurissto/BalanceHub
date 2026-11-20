package com.github.russoturissto.buttonapp.app

import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
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

/** Payment sound trials must not alter active channels before confirmation. */
@RunWith(AndroidJUnit4::class)
class PaymentToneChangeEmulatorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test fun all85PaymentCombinationsStageWithoutDestroyingWorkingChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val sounds = listOf("system") +
            BalanceNotificationSounds.tones.map { it.key } + listOf("silent")
        val modes = NotificationVibrationMode.options
        assertEquals(17, sounds.size)
        assertEquals(5, modes.size)
        val activeId = BalanceNotificationSounds.ensureChannel(
            context, BalanceNotificationSounds.PAYMENT)
        val prior = manager.getNotificationChannel(activeId)
        assertNotNull(prior)
        val prefs = context.getSharedPreferences(
            "balancehub_notification_sounds", Context.MODE_PRIVATE)
        val oldMode = BalanceNotificationSounds.vibrationMode(
            context, BalanceNotificationSounds.PAYMENT)
        val oldCompatibility = BalanceNotificationSounds.colorOsCompatibility(context)
        // Some clean emulators have no actual system notification ringtone.
        // The app must reject that trial without touching the working channel.
        val systemToneAvailable = RingtoneManager.getActualDefaultRingtoneUri(
            context, RingtoneManager.TYPE_NOTIFICATION) != null
        var count = 0
        try {
            // A test-only, ephemeral opt-in. Do not migrate or replace other channels.
            assertTrue(prefs.edit().putBoolean("coloros_direct_vibration", true).commit())
            for (sound in sounds) for (mode in modes) {
                assertNull(BalanceNotificationSounds.applyVibrationModeChoice(
                    context, BalanceNotificationSounds.PAYMENT, mode))
                assertEquals("Vibration mode replaced working payment channel",
                    activeId, BalanceNotificationSounds.channelId(
                        context, BalanceNotificationSounds.PAYMENT))
                val issue = PaymentToneChange.begin(context, sound, 100)
                if (sound == "system" && !systemToneAvailable) {
                    assertEquals("Не удалось открыть новую мелодию платежей", issue)
                    assertNull(PaymentToneChange.pending(context))
                    assertEquals(activeId, BalanceNotificationSounds.channelId(
                        context, BalanceNotificationSounds.PAYMENT))
                    assertNotNull(manager.getNotificationChannel(activeId))
                    count++
                    continue
                }
                assertNull("Cannot stage $sound/$mode: $issue", issue)
                val trial = PaymentToneChange.pending(context)
                assertNotNull(trial)
                val candidate = manager.getNotificationChannel(trial!!.id)
                assertNotNull(candidate)
                assertFalse("ColorOS trial must use direct motor", candidate!!.shouldVibrate())
                // The confirmed automatic ColorOS route plays WAV separately.
                // The candidate notification channel must stay silent to avoid double audio.
                assertNull("Automatic ColorOS trial channel must be silent",
                    candidate.sound)
                assertFalse(trial.tested)
                assertEquals("Active channel replaced before confirmation", activeId,
                    BalanceNotificationSounds.channelId(
                        context, BalanceNotificationSounds.PAYMENT))
                assertNotNull("Previous working Android channel deleted",
                    manager.getNotificationChannel(activeId))
                PaymentToneChange.cancel(context)
                assertNull(PaymentToneChange.pending(context))
                assertNotNull(manager.getNotificationChannel(activeId))
                count++
            }
            assertEquals(85, count)
        } finally {
            PaymentToneChange.cancel(context)
            BalanceNotificationSounds.applyVibrationModeChoice(
                context, BalanceNotificationSounds.PAYMENT, oldMode)
            prefs.edit().putBoolean("coloros_direct_vibration", oldCompatibility).commit()
        }
    }

    @Test fun neverAcceptsATrialWithoutRealNotificationTest() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val before = BalanceNotificationSounds.channelId(
            context, BalanceNotificationSounds.PAYMENT)
        try {
            assertNull(PaymentToneChange.begin(context, "crystal", 100))
            assertNotNull(PaymentToneChange.accept(context))
            assertEquals(before, BalanceNotificationSounds.channelId(
                context, BalanceNotificationSounds.PAYMENT))
        } finally {
            PaymentToneChange.cancel(context)
        }
    }
}
