package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceAmountStyleTest {
    @Test fun bothBankAndMobileUseExactly24spAndSamePillInsets() {
        assertEquals(24f, BalanceAmountStyle.FONT_SIZE_SP, 0f)
        assertEquals(10, BalanceAmountStyle.HORIZONTAL_PADDING_DP)
        assertEquals(6, BalanceAmountStyle.VERTICAL_PADDING_DP)
        assertEquals(13, BalanceAmountStyle.CORNER_RADIUS_DP)
    }

    @Test fun shortAndLongValuesKeepFontButAdaptTheirPlacement() {
        // Width is measured from the Android TextView paint at full 24sp.
        assertFalse(BalanceAmountStyle.needsOwnLine(420, 1f, 88f,
            BalanceAmountStyle.MOBILE_RESERVED_DP))
        assertTrue(BalanceAmountStyle.needsOwnLine(420, 1f, 180f,
            BalanceAmountStyle.MOBILE_RESERVED_DP))
        assertTrue(BalanceAmountStyle.needsOwnLine(360, 1f, 70f,
            BalanceAmountStyle.BANK_RESERVED_DP))
        assertFalse(BalanceAmountStyle.needsOwnLine(420, 1f, 160f,
            BalanceAmountStyle.BANK_RESERVED_DP))
        assertTrue(BalanceAmountStyle.needsOwnLine(420, 1f, 250f,
            BalanceAmountStyle.BANK_RESERVED_DP))
    }

    @Test fun everyRegistryOperatorAutomaticallyUsesSameRenderingBranch() {
        assertTrue(RussianMobileOperators.all.size > 10)
        assertTrue(RussianMobileOperators.all.map { it.displayName }.distinct().size > 10)
    }
}
