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

/** Events/updates stage independent silent trials without ever touching payments. */
@RunWith(AndroidJUnit4::class)
class CategoryToneChangeEmulatorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test fun eventAndUpdateTrialsKeepEveryExistingActiveChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val prefs = context.getSharedPreferences(
            "balancehub_notification_sounds", Context.MODE_PRIVATE)
        val oldCompatibility = BalanceNotificationSounds.colorOsCompatibility(context)
        val paymentId = BalanceNotificationSounds.ensureChannel(
            context, BalanceNotificationSounds.PAYMENT)
        val paymentTone = BalanceNotificationSounds.selected(
            context, BalanceNotificationSounds.PAYMENT)
        val categories = listOf(
            BalanceNotificationSounds.EVENT to "crystal",
            BalanceNotificationSounds.UPDATE to "spark")
        try {
            assertTrue(prefs.edit().putBoolean("coloros_direct_vibration", true).commit())
            for ((category, key) in categories) {
                val activeId = BalanceNotificationSounds.ensureChannel(context, category)
                val activeTone = BalanceNotificationSounds.selected(context, category)
                val error = CategoryToneChange.begin(context, category, key, 100)
                assertNull("Could not stage $category: $error", error)
                val trial = CategoryToneChange.pending(context, category)
                assertNotNull(trial)
                assertTrue(trial!!.automatic)
                val candidate = manager.getNotificationChannel(trial.id)
                assertNotNull(candidate)
                assertNull("Automatic channels must leave WAV to the foreground player",
                    candidate!!.sound)
                assertFalse(candidate.shouldVibrate())
                assertFalse(trial.tested)
                assertNotNull("Test must precede confirmation",
                    CategoryToneChange.accept(context, category))
                assertEquals(activeId, BalanceNotificationSounds.channelId(context, category))
                assertEquals(activeTone, BalanceNotificationSounds.selected(context, category))
                assertEquals(paymentId, BalanceNotificationSounds.channelId(
                    context, BalanceNotificationSounds.PAYMENT))
                assertEquals(paymentTone, BalanceNotificationSounds.selected(
                    context, BalanceNotificationSounds.PAYMENT))
                // App startup cleanup must preserve unconfirmed trial channels.
                BalanceNotificationSounds.ensureDefaultChannels(context)
                assertNotNull(manager.getNotificationChannel(trial.id))
                CategoryToneChange.cancel(context, category)
                assertNull(CategoryToneChange.pending(context, category))
                assertNotNull(manager.getNotificationChannel(activeId))
            }
        } finally {
            for ((category, _) in categories) CategoryToneChange.cancel(context, category)
            prefs.edit().putBoolean("coloros_direct_vibration", oldCompatibility).commit()
        }
    }
}
