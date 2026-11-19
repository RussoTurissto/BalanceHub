package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Bottom navigation clears the nav bar, not the much larger gesture region. */
class BottomNavigationSafeAreaTest {
    @Test fun threeButtonNavigationKeepsTwoPixelsAboveTheBar() {
        assertEquals(82, BottomNavigationSafeArea.bottomPaddingPx(
            navigationBar = 80, gapPx = 2))
    }

    @Test fun transparentGestureBarStillUsesItsNavigationInset() {
        // Gestures may occupy a taller recognition area, but transparency
        // does not erase the system-provided navigation bar inset.
        assertEquals(26, BottomNavigationSafeArea.bottomPaddingPx(
            navigationBar = 24, gapPx = 2))
    }

    @Test fun noVisibleNavigationBarLeavesOnlyTheRequestedGap() {
        assertEquals(2, BottomNavigationSafeArea.bottomPaddingPx(
            navigationBar = 0, gapPx = 2))
    }

    @Test fun malformedInsetsNeverMoveTheBarOutsideTheWindow() {
        assertEquals(2, BottomNavigationSafeArea.bottomPaddingPx(
            navigationBar = -42, gapPx = 2))
        assertEquals(0, BottomNavigationSafeArea.bottomPaddingPx(
            navigationBar = -1, gapPx = -1))
    }
}
