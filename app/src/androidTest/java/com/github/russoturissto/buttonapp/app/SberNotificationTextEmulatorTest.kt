package com.github.russoturissto.buttonapp.app

import android.app.Notification
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verify real Android extras, not just synthetic parser inputs. */
@RunWith(AndroidJUnit4::class)
class SberNotificationTextEmulatorTest {
    @Test fun infoFieldCanCarryBalanceButHistoricalMessagesCannotOverrideIt() {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "Сбербанк")
                putCharSequence(Notification.EXTRA_INFO_TEXT, "Теперь у вас: 123,45 ₽")
                putParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES,
                    arrayOf(Bundle().apply { putCharSequence("text", "Остаток: 999 ₽") }))
            }
        }
        val payload = SberNotificationText.extract(notification)
        assertTrue(payload.hasHistoricMessages)
        assertTrue(payload.fieldsMask and SberNotificationText.Field.INFO.mask != 0)
        assertTrue(payload.fieldsMask and SberNotificationText.Field.HISTORIC.mask != 0)
        val result = SberPushEvaluation.analyze(SberPushParser.SBER_PACKAGE, payload)
        assertEquals(12_345L, result.result?.kopecks)
    }
}
