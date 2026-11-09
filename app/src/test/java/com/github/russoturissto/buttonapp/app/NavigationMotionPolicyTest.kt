package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMotionPolicyTest {
    @Test fun navigationIncludesFourNamedTabsAndSettings() {
        assertEquals(5, NavigationMotionPolicy.TAB_COUNT)
        assertEquals(4, NavigationMotionPolicy.SETTINGS_INDEX)
        for (i in 0..4) assertTrue(NavigationMotionPolicy.valid(i))
        assertFalse(NavigationMotionPolicy.valid(-1))
        assertFalse(NavigationMotionPolicy.valid(5))
    }

    @Test fun tappingSelectedOrInvalidTabDoesNotSwitch() {
        assertFalse(NavigationMotionPolicy.isChange(3, 3))
        assertFalse(NavigationMotionPolicy.isChange(4, 5))
        assertFalse(NavigationMotionPolicy.isChange(-1, 4))
        assertTrue(NavigationMotionPolicy.isChange(1, 4))
    }

    @Test fun swipesAndTapsShareConsistentDirection() {
        assertEquals(1, NavigationMotionPolicy.direction(0, 4))
        assertEquals(-1, NavigationMotionPolicy.direction(4, 0))
        assertEquals(0, NavigationMotionPolicy.direction(2, 2))
        assertEquals(-1, NavigationMotionPolicy.direction(4, 3))
    }
}
