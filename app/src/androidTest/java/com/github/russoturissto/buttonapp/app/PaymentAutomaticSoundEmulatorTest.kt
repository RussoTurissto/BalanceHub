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

/** Protect a confirmed ColorOS payment channel while testing app-owned audio. */
@RunWith(AndroidJUnit4::class)
class PaymentAutomaticSoundEmulatorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test fun automaticTrialUsesSilentChannelAndNeverReplacesOldBeforeAccept() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val prefs = context.getSharedPreferences("balancehub_notification_sounds",
            Context.MODE_PRIVATE)
        val oldCompatibility = BalanceNotificationSounds.colorOsCompatibility(context)
        val currentId = BalanceNotificationSounds.ensureChannel(
            context, BalanceNotificationSounds.PAYMENT)
        val oldChannel = manager.getNotificationChannel(currentId)
        assertNotNull(oldChannel)
        try {
            assertTrue(prefs.edit().putBoolean("coloros_direct_vibration", true).commit())
            val issue = PaymentToneChange.begin(context, "drop", 100, automatic = true)
            assertNull(issue)
            val trial = PaymentToneChange.pending(context)
            assertNotNull(trial)
            assertTrue(trial!!.automatic)
            assertEquals("drop", trial.key)
            assertFalse(trial.tested)
            assertNotNull(manager.getNotificationChannel(trial.id))
            assertNull(manager.getNotificationChannel(trial.id)!!.sound)
            assertFalse(manager.getNotificationChannel(trial.id)!!.shouldVibrate())
            assertEquals(currentId, BalanceNotificationSounds.channelId(
                context, BalanceNotificationSounds.PAYMENT))
            assertNotNull(manager.getNotificationChannel(currentId))
            PaymentToneChange.cancel(context)
            assertNull(PaymentToneChange.pending(context))
            assertNull(manager.getNotificationChannel(trial.id))
            assertNotNull(manager.getNotificationChannel(currentId))
        } finally {
            PaymentToneChange.cancel(context)
            prefs.edit().putBoolean("coloros_direct_vibration", oldCompatibility).commit()
        }
    }

    @Test fun acceptedAutomaticChannelIsReusedForNewToneWithoutReplacingChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val prefs = context.getSharedPreferences("balancehub_notification_sounds",
            Context.MODE_PRIVATE)
        val names = listOf("coloros_direct_vibration", PaymentAutomaticSound.ENABLED,
            "channel_active_id_" + BalanceNotificationSounds.PAYMENT,
            "channel_revision_" + BalanceNotificationSounds.PAYMENT,
            "payment_previous_working_channel_id", "payment_previous_working_sound_key",
            "payment_previous_working_sound_gain", "payment_previous_working_automatic",
            BalanceNotificationSounds.PAYMENT,
            "volume_" + BalanceNotificationSounds.PAYMENT)
        val snapshot = prefs.all.filterKeys { it in names }
        val originalId = BalanceNotificationSounds.ensureChannel(
            context, BalanceNotificationSounds.PAYMENT)
        val originalKey = BalanceNotificationSounds.selected(
            context, BalanceNotificationSounds.PAYMENT)
        var newId: String? = null
        try {
            assertTrue(prefs.edit().putBoolean("coloros_direct_vibration", true)
                .putBoolean(PaymentAutomaticSound.ENABLED, false).commit())
            assertNull(PaymentToneChange.begin(context, "crystal", 100, automatic = true))
            val trial = PaymentToneChange.pending(context)!!
            newId = trial.id
            assertFalse(trial.tested)
            assertNotNull("Must test before accepting", PaymentToneChange.accept(context))
            assertTrue(PaymentToneChange.markTested(context, trial.id))
            assertNull(PaymentToneChange.accept(context))
            assertTrue(PaymentAutomaticSound.enabled(context))
            assertEquals(trial.id, BalanceNotificationSounds.channelId(
                context, BalanceNotificationSounds.PAYMENT))
            assertNotNull(manager.getNotificationChannel(originalId))
            assertNull(PaymentToneChange.begin(context, "drop", 100))
            val next = PaymentToneChange.pending(context)!!
            assertTrue(next.automatic)
            assertEquals(trial.id, next.id)
            assertNull(manager.getNotificationChannel(next.id)!!.sound)
            assertTrue(PaymentToneChange.markTested(context, next.id))
            assertNull(PaymentToneChange.accept(context))
            assertEquals(trial.id, BalanceNotificationSounds.channelId(
                context, BalanceNotificationSounds.PAYMENT))
            assertEquals("drop", BalanceNotificationSounds.selected(
                context, BalanceNotificationSounds.PAYMENT))
            assertTrue(PaymentToneChange.canRestorePrevious(context))
            assertEquals(originalId, PaymentToneChange.previousWorkingId(context))
            assertNull(PaymentToneChange.restorePrevious(context))
            assertFalse(PaymentAutomaticSound.enabled(context))
            assertEquals(originalId, BalanceNotificationSounds.channelId(
                context, BalanceNotificationSounds.PAYMENT))
            assertEquals(originalKey, BalanceNotificationSounds.selected(
                context, BalanceNotificationSounds.PAYMENT))
        } finally {
            PaymentToneChange.cancel(context)
            val editor = prefs.edit()
            for (name in names) when (val saved = snapshot[name]) {
                is String -> editor.putString(name, saved)
                is Int -> editor.putInt(name, saved)
                is Boolean -> editor.putBoolean(name, saved)
                else -> editor.remove(name)
            }
            assertTrue(editor.commit())
            if (newId != null && newId != originalId)
                manager.deleteNotificationChannel(newId)
        }
    }
}
