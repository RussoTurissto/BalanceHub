package com.github.russoturissto.buttonapp.app

/**
 * One visual contract for confirmed ruble balances from all mobile operators
 * in the registry, and for Sberbank, VTB and T-Bank account rows.
 * Display large amounts at full size; move the pill below its label if needed.
 */
internal object BalanceAmountStyle {
    const val FONT_SIZE_SP = 24f
    const val HORIZONTAL_PADDING_DP = 10
    const val VERTICAL_PADDING_DP = 6
    const val CORNER_RADIUS_DP = 13

    // Included in the screen-width calculation: outer content gutters, card
    // padding, logo/name column (for mobile), and a readable text-column floor.
    const val MOBILE_RESERVED_DP = 252
    const val BANK_RESERVED_DP = 210
    private const val MIN_INLINE_SCREEN_DP = 395

    fun needsOwnLine(
        screenWidthDp: Int, density: Float, measuredTextWidthPx: Float,
        reservedDp: Int
    ): Boolean {
        if (screenWidthDp < MIN_INLINE_SCREEN_DP || density <= 0f) return true
        return measuredTextWidthPx + 2 * HORIZONTAL_PADDING_DP * density >
            (screenWidthDp - reservedDp) * density
    }
}
