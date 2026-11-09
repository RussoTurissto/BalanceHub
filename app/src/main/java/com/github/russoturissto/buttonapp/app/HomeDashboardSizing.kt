package com.github.russoturissto.buttonapp.app

/**
 * Dashboard proportions chosen from available display height (in dp).
 *
 * The calendar needs six rows in some months. On a typical phone the
 * summary, calendar, collapsed payments and action button should fit between
 * the fixed header and navigation, without clipping the action button.
 * On very small displays the ScrollView remains available.
 */
internal data class HomeDashboardSize(
    val topPaddingDp: Int,
    val dayCellHeightDp: Int,
    val monthControlHeightDp: Int
)

internal object HomeDashboardSizing {
    /**
     * Compact based on the measured ScrollView height, after Android window
     * insets, persistent header and bottom navigation have been laid out.
     * Six-row months use the tighter layout sooner; scrolling remains enabled.
     */
    fun forViewport(availableHeightDp: Int, fontScale: Float = 1f,
        calendarWeeks: Int = 5): HomeDashboardSize {
        val adjusted = availableHeightDp -
            ((fontScale - 1f).coerceAtLeast(0f) * 90f).toInt()
        return when {
            adjusted <= 560 || (calendarWeeks >= 6 && adjusted <= 650) ->
                HomeDashboardSize(4, 34, 35)
            adjusted <= 700 -> HomeDashboardSize(4, 36, 37)
            else -> HomeDashboardSize(6, 38, 39)
        }
    }

    fun forHeight(screenHeightDp: Int, fontScale: Float = 1f): HomeDashboardSize {
        val adjusted = screenHeightDp -
            ((fontScale - 1f).coerceAtLeast(0f) * 130f).toInt()
        return when {
            adjusted in 1..700 -> HomeDashboardSize(
                topPaddingDp = 4, dayCellHeightDp = 34, monthControlHeightDp = 36
            )
            adjusted in 701..850 -> HomeDashboardSize(
                topPaddingDp = 4, dayCellHeightDp = 36, monthControlHeightDp = 38
            )
            else -> HomeDashboardSize(
                topPaddingDp = 8, dayCellHeightDp = 40, monthControlHeightDp = 40
            )
        }
    }
}
