package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** All Android navigation styles need a clear bottom edge for touch targets. */
class BottomNavigationSafeAreaTest {
    @Test fun threeButtonNavigationControlsTheClearance() {
        assertEquals(82, BottomNavigationSafeArea.bottomPaddingPx(
            navigationBar = 80, systemGesture = 0, mandatoryGesture = 0,
            tappableElement = 76, gapPx = 2))
    }

    @Test fun systemGesturesCanExceedTheNavigationBar() {
        assertEquals(46, BottomNavigationSafeArea.bottomPaddingPx(
            navigationBar = 22, systemGesture = 44, mandatoryGesture = 24,
            tappableElement = 0, gapPx = 2))
    }

    @Test fun mandatoryGesturesAndTappableElementsAreAlsoProtected() {
        assertEquals(34, BottomNavigationSafeArea.bottomPaddingPx(
            navigationBar = 12, systemGesture = 18, mandatoryGesture = 32,
            tappableElement = 24, gapPx = 2))
        assertEquals(42, BottomNavigationSafeArea.bottomPaddingPx(
            navigationBar = 10, systemGesture = 20, mandatoryGesture = 0,
            tappableElement = 40, gapPx = 2))
    }

    @Test fun zeroInsetsAndMalformedValuesKeepExactlyTheRequestedGap() {
        assertEquals(2, BottomNavigationSafeArea.bottomPaddingPx(
            navigationBar = 0, systemGesture = 0, mandatoryGesture = 0,
            tappableElement = 0, gapPx = 2))
        assertEquals(0, BottomNavigationSafeArea.bottomPaddingPx(
            navigationBar = -1, systemGesture = -2, mandatoryGesture = -3,
            tappableElement = -4, gapPx = -1))
    }
}
