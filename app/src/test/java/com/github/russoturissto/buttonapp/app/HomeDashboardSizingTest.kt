package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDashboardSizingTest {
    @Test fun typicalPhoneKeepsAllBlocksCompact() {
        val size = HomeDashboardSizing.forHeight(790)
        assertEquals(4, size.topPaddingDp)
        assertEquals(36, size.dayCellHeightDp)
        assertEquals(38, size.monthControlHeightDp)
    }

    @Test fun shortPhoneHasSmallerCalendarButCanStillScroll() {
        val size = HomeDashboardSizing.forHeight(680)
        assertEquals(4, size.topPaddingDp)
        assertEquals(34, size.dayCellHeightDp)
    }

    @Test fun largePhoneRetainsComfortableCalendar() {
        val size = HomeDashboardSizing.forHeight(950)
        assertEquals(8, size.topPaddingDp)
        assertEquals(40, size.dayCellHeightDp)
    }

    @Test fun largeFontScalesTightenCalendarWithoutGoingBelowMinimum() {
        val size = HomeDashboardSizing.forHeight(830, 1.5f)
        assertEquals(36, size.dayCellHeightDp)
        val smaller = HomeDashboardSizing.forHeight(750, 1.6f)
        assertTrue(smaller.dayCellHeightDp >= 34)
    }

    @Test fun compactingSixWeekCalendarSavesRoomForBottomButton() {
        val normal = 6 * 40
        val compact = 6 * HomeDashboardSizing.forHeight(790).dayCellHeightDp
        assertEquals(24, normal - compact)
    }
}
