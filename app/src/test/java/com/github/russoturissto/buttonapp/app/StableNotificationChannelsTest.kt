package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StableNotificationChannelsTest {
    @Test fun threeChannelsStayStableForAllSettings() {
        assertEquals(3, StableNotificationChannels.all().size)
        assertEquals(StableNotificationChannels.EVENT,
            StableNotificationChannels.id(BalanceNotificationSounds.EVENT))
        assertEquals(StableNotificationChannels.PAYMENT,
            StableNotificationChannels.id(BalanceNotificationSounds.PAYMENT))
        assertEquals(StableNotificationChannels.UPDATE,
            StableNotificationChannels.id(BalanceNotificationSounds.UPDATE))
    }

    @Test fun legacyVariantsAreIdentifiedWithoutMatchingNewChannels() {
        for (category in listOf(BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT, BalanceNotificationSounds.UPDATE)) {
            assertTrue(StableNotificationChannels.isRetired(category))
            assertTrue(StableNotificationChannels.isRetired(
                category + "_tone_crystal_v5_100_vib_on"))
            assertFalse(StableNotificationChannels.isRetired(
                StableNotificationChannels.id(category)))
        }
        assertFalse(StableNotificationChannels.isRetired("third_party_channel"))
    }
}
