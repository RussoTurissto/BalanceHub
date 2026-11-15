package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The original verified payment preference is immutable; other categories are isolated. */
class AutomaticNotificationCategoryTest {
    @Test fun eachCategoryKeepsAnIndependentAutomaticAudioPreference() {
        val payment = PaymentAutomaticSound.enabledKey(BalanceNotificationSounds.PAYMENT)
        val event = PaymentAutomaticSound.enabledKey(BalanceNotificationSounds.EVENT)
        val update = PaymentAutomaticSound.enabledKey(BalanceNotificationSounds.UPDATE)
        assertEquals("payment_automatic_sound", payment)
        assertNotEquals(payment, event)
        assertNotEquals(payment, update)
        assertNotEquals(event, update)
    }

    @Test fun allUserSelectableSoundAndVibrationModesAreShared() {
        assertEquals(15, BalanceNotificationSounds.tones.distinctBy { it.key }.size)
        assertEquals(5, NotificationVibrationMode.options.distinct().size)
        for (category in listOf(BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT, BalanceNotificationSounds.UPDATE)) {
            assertTrue(StableNotificationChannels.belongsToCategory(
                StableNotificationChannels.id(category), category))
        }
    }
}
