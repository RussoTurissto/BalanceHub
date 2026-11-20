package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WinterThemePolicyTest {
    @Test fun activatesDecember20UntilJanuary10() {
        assertFalse(WinterThemePolicy.enabled(12, 19))
        assertTrue(WinterThemePolicy.enabled(12, 20))
        assertTrue(WinterThemePolicy.enabled(12, 31))
        assertTrue(WinterThemePolicy.enabled(1, 1))
        assertTrue(WinterThemePolicy.enabled(1, 10))
        assertFalse(WinterThemePolicy.enabled(1, 11))
        assertFalse(WinterThemePolicy.enabled(2, 1))
        assertFalse(WinterThemePolicy.enabled(11, 30))
    }

    @Test fun engineerOverrideWinsOnAnyDate() {
        assertTrue(WinterThemePolicy.enabled(7, 1, WinterThemePolicy.FORCE_ON))
        assertTrue(WinterThemePolicy.enabled(1, 11, WinterThemePolicy.FORCE_ON))
        assertFalse(WinterThemePolicy.enabled(12, 25, WinterThemePolicy.FORCE_OFF))
        assertFalse(WinterThemePolicy.enabled(1, 5, WinterThemePolicy.FORCE_OFF))
        assertTrue(WinterThemePolicy.enabled(12, 20, WinterThemePolicy.AUTO))
    }

    @Test fun frostIsFivePercentSofterThanApprovedReference() {
        assertTrue(WinterThemePolicy.FROST_INTENSITY in 0.949f..0.951f)
    }
}
