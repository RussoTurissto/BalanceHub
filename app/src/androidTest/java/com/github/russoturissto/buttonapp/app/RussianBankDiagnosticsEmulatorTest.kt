package com.github.russoturissto.buttonapp.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Per-bank engineering metadata must never capture financial Push content. */
@RunWith(AndroidJUnit4::class)
class RussianBankDiagnosticsEmulatorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun bankLogsAreIsolatedBoundedAndPrivacySafe() {
        val prefs = context.getSharedPreferences(
            "balancehub_ru_bank_diagnostics_v1", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        val vtb = RussianBankCatalog.byId("vtb")!!
        val tbank = RussianBankCatalog.byId("tbank")!!
        try {
            assertTrue(prefs.edit().clear().commit())
            val payload = SberNotificationText.Payload(
                titles = listOf("Private example title"),
                bodies = listOf("Secret transaction 9876 RUB"),
                fieldsMask = SberNotificationText.Field.TEXT.mask,
                rubleFieldsMask = SberNotificationText.Field.TEXT.mask,
                cueFieldsMask = SberNotificationText.Field.TEXT.mask,
                accountFieldsMask = 0,
                fieldLengths = listOf(0, 0, 26)
            )
            repeat(8) {
                RussianBankDiagnostics.record(context, vtb, "Баланс обновлён",
                    payload, SberNotificationPrivacy.Evidence.NONE,
                    SberPushNotificationListener.STATE_LOCKED)
            }
            RussianBankDiagnostics.record(context, tbank,
                "Push без подтверждённого остатка", payload)

            val vtbEvents = RussianBankDiagnostics.recent(context, vtb)
            val tbankEvents = RussianBankDiagnostics.recent(context, tbank)
            assertEquals(6, vtbEvents.size)
            assertEquals(1, tbankEvents.size)
            assertEquals("Баланс обновлён", vtbEvents.first().status)
            assertEquals(SberNotificationText.Field.TEXT.mask,
                vtbEvents.first().rubleFieldsMask)
            assertEquals(SberPushNotificationListener.STATE_LOCKED,
                vtbEvents.first().deliveryState)
            assertNotNull(RussianBankDiagnostics.lastSuccess(context, vtb))
            assertNull(RussianBankDiagnostics.lastSuccess(context, tbank))
            val stored = prefs.getString("events_vtb", "")!!
            assertFalse(stored.contains("Private example title"))
            assertFalse(stored.contains("Secret transaction"))
            assertFalse(stored.contains("9876 RUB"))
            val sourceKey = "private-card-tag-9876"
            val postedAt = System.currentTimeMillis() - 120_000L
            RussianBankDiagnostics.record(context, vtb, "Баланс обновлён",
                payload, postedAt = postedAt, notificationKey = sourceKey)
            val afterFirst = RussianBankDiagnostics.recent(context, vtb)
            assertEquals(6, afterFirst.size)
            assertEquals(postedAt, afterFirst.first().receivedAt)
            assertEquals(24, afterFirst.first().fingerprint.length)
            RussianBankDiagnostics.record(context, vtb, "Баланс обновлён",
                payload, postedAt = postedAt, notificationKey = sourceKey)
            assertEquals(afterFirst, RussianBankDiagnostics.recent(context, vtb))
            assertFalse(prefs.getString("events_vtb", "")!!.contains(sourceKey))

            RussianBankDiagnostics.clear(context, "vtb")
            assertTrue(RussianBankDiagnostics.recent(context, vtb).isEmpty())
            assertEquals(1, RussianBankDiagnostics.recent(context, tbank).size)
        } finally {
            val editor = prefs.edit().clear()
            for ((key, value) in original) when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
            editor.commit()
        }
    }
}
