package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationVibrationModeTest {
    @Test fun fiveHumanReadableOptionsHaveUniqueKeys() {
        assertEquals(5, NotificationVibrationMode.options.size)
        assertEquals(5, NotificationVibrationMode.options.distinct().size)
        assertEquals("По умолчанию",
            NotificationVibrationMode.title(NotificationVibrationMode.DEFAULT))
        assertEquals("Двойной",
            NotificationVibrationMode.title(NotificationVibrationMode.DOUBLE))
    }

    @Test fun offDisablesOnlyTheHapticAlert() {
        assertFalse(NotificationVibrationMode.enabled(NotificationVibrationMode.OFF))
        assertTrue(NotificationVibrationMode.enabled(NotificationVibrationMode.DEFAULT))
        assertNull(NotificationVibrationMode.pattern(NotificationVibrationMode.OFF))
        assertNull(NotificationVibrationMode.pattern(NotificationVibrationMode.DEFAULT))
    }

    @Test fun customPatternsAreDistinctAndStartWithoutDelay() {
        val short = NotificationVibrationMode.pattern(NotificationVibrationMode.SHORT)!!
        val long = NotificationVibrationMode.pattern(NotificationVibrationMode.LONG)!!
        val double = NotificationVibrationMode.pattern(NotificationVibrationMode.DOUBLE)!!
        assertArrayEquals(longArrayOf(0L, 110L), short)
        assertArrayEquals(longArrayOf(0L, 450L), long)
        assertArrayEquals(longArrayOf(0L, 130L, 110L, 130L), double)
        assertTrue(short.sum() < long.sum())
        assertTrue(double.size > short.size)
    }
}
