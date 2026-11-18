package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationPolicyTest {
    @Test fun topLevelSectionsOpenFromRoot() {
        for (page in listOf("appearance", "security", "mobile", "banks", "notifications",
            "updates", "help", "about")) {
            assertTrue(SettingsNavigationPolicy.canOpen("root", page))
        }
    }
    @Test fun notificationSectionsCannotSkipParent() {
        for (page in listOf("payment", "event", "update_sound")) {
            assertTrue(SettingsNavigationPolicy.canOpen("notifications", page))
            assertFalse(SettingsNavigationPolicy.canOpen("root", page))
        }
        // Diagnostic tests must remain invisible outside the engineering menu.
        assertFalse(SettingsNavigationPolicy.canOpen("help", "vibration"))
        assertFalse(SettingsNavigationPolicy.canOpen("notifications", "vibration"))
    }
    @Test fun releaseHistoryIsNestedOnlyUnderUpdates() {
        assertTrue(SettingsNavigationPolicy.canOpen("updates", "history"))
        assertFalse(SettingsNavigationPolicy.canOpen("root", "history"))
        assertFalse(SettingsNavigationPolicy.canOpen("notifications", "history"))
        assertEquals(listOf("updates", "history"),
            SettingsNavigationPolicy.sanitize(listOf("updates", "history")))
        assertEquals(listOf("updates"),
            SettingsNavigationPolicy.sanitize(listOf("updates", "payment")))
    }

    @Test fun restorationStopsAtInvalidPage() {
        assertEquals(listOf("notifications", "payment"),
            SettingsNavigationPolicy.sanitize(listOf("notifications", "payment")))
        assertEquals(listOf("banks"),
            SettingsNavigationPolicy.sanitize(listOf("banks", "payment")))
        assertEquals(emptyList<String>(),
            SettingsNavigationPolicy.sanitize(listOf("unknown", "notifications")))
    }
}
