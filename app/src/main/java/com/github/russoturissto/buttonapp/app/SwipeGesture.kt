package com.github.russoturissto.buttonapp.app

import kotlin.math.abs

/**
 * Detect a deliberate horizontal tab swipe. +1 means next tab (left swipe),
 * -1 means previous tab (right swipe); 0 preserves taps and vertical scrolling.
 */
internal object SwipeGesture {
    fun direction(deltaX: Float, deltaY: Float, durationMs: Long, density: Float): Int {
        if (density <= 0f || durationMs !in 35L..950L) return 0
        val horizontal = abs(deltaX)
        val vertical = abs(deltaY)
        if (horizontal <= vertical * 1.35f) return 0

        // 44 dp for an ordinary swipe, or a shorter deliberate quick flick.
        val passesDistance = horizontal >= 44f * density
        val quickFlick = durationMs <= 320L &&
            horizontal >= 30f * density &&
            horizontal / durationMs >= 0.38f * density
        if (!passesDistance && !quickFlick) return 0
        return if (deltaX < 0f) 1 else -1
    }
}
