package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeGestureTest {
    @Test fun ordinaryLeftAndRightSwipesChangeTab() {
        assertEquals(1, SwipeGesture.direction(-46f, 4f, 420L, 1f))
        assertEquals(-1, SwipeGesture.direction(46f, -7f, 450L, 1f))
    }

    @Test fun quickShortFlickIsResponsive() {
        assertEquals(1, SwipeGesture.direction(-32f, 5f, 70L, 1f))
        assertEquals(-1, SwipeGesture.direction(32f, 4f, 70L, 1f))
    }

    @Test fun tapsAndVerticalScrollingNeverSwitchTabs() {
        assertEquals(0, SwipeGesture.direction(12f, 2f, 110L, 1f))
        assertEquals(0, SwipeGesture.direction(54f, 100f, 250L, 1f))
        assertEquals(0, SwipeGesture.direction(46f, 40f, 220L, 1f))
    }

    @Test fun thresholdsScaleWithScreenDensity() {
        assertEquals(0, SwipeGesture.direction(48f, 1f, 400L, 2f))
        assertEquals(1, SwipeGesture.direction(-92f, 2f, 400L, 2f))
    }

    @Test fun longDragsAndMultiSecondPressesDoNotTrigger() {
        assertEquals(0, SwipeGesture.direction(110f, 0f, 1100L, 1f))
        assertEquals(0, SwipeGesture.direction(110f, 0f, 15L, 1f))
    }
}
