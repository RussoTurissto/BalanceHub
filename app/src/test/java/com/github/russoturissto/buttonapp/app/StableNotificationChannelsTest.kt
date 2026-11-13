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

    @Test fun explicitReconfigurationKeepsExactlyThreeActiveCategoryIdentities() {
        val categories = listOf(
            BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT,
            BalanceNotificationSounds.UPDATE)
        for (category in categories) {
            val initial = StableNotificationChannels.id(category)
            val first = StableNotificationChannels.id(category, 1)
            val second = StableNotificationChannels.id(category, 2)
            assertFalse(initial == first)
            assertFalse(first == second)
            assertTrue(StableNotificationChannels.isManaged(initial))
            assertTrue(StableNotificationChannels.isManaged(first))
            assertTrue(StableNotificationChannels.isManaged(second))
            assertFalse(StableNotificationChannels.isRetired(first))
            assertFalse(StableNotificationChannels.isRetired(second))
        }
        assertEquals(3, categories.map { StableNotificationChannels.id(it, 3) }.toSet().size)
        assertFalse(StableNotificationChannels.isManaged("third_party_channel"))
        assertFalse(StableNotificationChannels.isManaged("balancehub.channel.event.configured.bad"))
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
