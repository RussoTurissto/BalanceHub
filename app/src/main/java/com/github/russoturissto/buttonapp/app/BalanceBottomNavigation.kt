package com.github.russoturissto.buttonapp.app

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Navigation indexes and motion rules shared by taps, swipes and deep links. */
internal object NavigationMotionPolicy {
    const val TAB_COUNT = 5
    const val SETTINGS_INDEX = 4
    const val INDICATOR_DURATION_MS = 225L
    const val CONTENT_DURATION_MS = 175L
    fun valid(index: Int): Boolean = index in 0 until TAB_COUNT
    fun isChange(current: Int, destination: Int): Boolean =
        valid(current) && valid(destination) && current != destination
    fun direction(current: Int, destination: Int): Int =
        destination.compareTo(current).coerceIn(-1, 1)

    /** System animator scale and display VSync remain under Android's control. */
    fun canAnimate(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            try {
                Settings.Global.getFloat(context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            } catch (_: SecurityException) {
                true
            }
        }
}

/**
 * One continuous, five-section, inset-safe bottom bar.
 * Only the highlight moves; tab geometry stays fixed during transitions.
 * ValueAnimator takes its frames from the Android animation clock (VSync).
 */
// Constructed only in code; it is never inflated from XML.
@SuppressLint("ViewConstructor")
internal class BalanceBottomNavigation(
    context: Context,
    private val night: Boolean,
    initialTab: Int,
    onTabRequested: (Int) -> Unit
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density + .5f).toInt()
    private val labels = listOf("Главная", "Балансы", "Платежи", "События", "Настройки")
    private val symbols = listOf("home", "wallet", "credit-card", "calendar-days", "settings")
    private val captions = mutableListOf<TextView>()
    private val symbolsViews = mutableListOf<ImageView>()
    private val cells = mutableListOf<LinearLayout>()
    private val row = LinearLayout(context)
    private val indicator = View(context)
    private var indicatorReady = false
    private var slide: ValueAnimator? = null
    private var selected = initialTab.coerceIn(0, NavigationMotionPolicy.TAB_COUNT - 1)
    private val normalText = if (night) Color.rgb(227, 240, 239)
        else Color.rgb(42, 75, 77)
    private val normalIcon = if (night) Color.rgb(216, 234, 232)
        else Color.rgb(53, 119, 118)

    init {
        val edge = if (night) Color.rgb(91, 149, 149)
            else Color.rgb(118, 177, 175)
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (night) intArrayOf(Color.rgb(21, 43, 49), Color.rgb(16, 36, 44))
            else intArrayOf(Color.rgb(232, 248, 245), Color.rgb(244, 252, 250))
        ).apply {
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), edge)
        }
        setPadding(dp(3), dp(3), dp(3), dp(3))
        clipChildren = true
        clipToPadding = false

        // Fine dividers are visible only between unselected sections.
        val separators = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (night) Color.rgb(63, 104, 107)
                    else Color.rgb(182, 219, 214)
                strokeWidth = dp(1).toFloat()
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                for (position in 1 until NavigationMotionPolicy.TAB_COUNT) {
                    val x = width * position.toFloat() / NavigationMotionPolicy.TAB_COUNT
                    canvas.drawLine(x, height * .24f, x, height * .76f, paint)
                }
            }
        }
        addView(separators, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        indicator.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (night) intArrayOf(Color.rgb(43, 135, 142), Color.rgb(34, 162, 147))
            else intArrayOf(Color.rgb(43, 147, 150), Color.rgb(33, 158, 137))
        ).apply { cornerRadius = dp(17).toFloat() }
        addView(indicator, LayoutParams(0, LayoutParams.MATCH_PARENT))

        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        labels.forEachIndexed { index, label ->
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(64)
                setPadding(dp(2), dp(4), dp(2), dp(4))
                isClickable = true
                isFocusable = true
                setOnClickListener { onTabRequested(index) }
            }
            val pictogram = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                setImageDrawable(AppIconDrawable(symbols[index], normalIcon))
            }
            val size = if (index == NavigationMotionPolicy.SETTINGS_INDEX) dp(31)
                else dp(21)
            cell.addView(pictogram, LinearLayout.LayoutParams(size, size))
            val caption = TextView(context).apply {
                text = if (index == NavigationMotionPolicy.SETTINGS_INDEX) "" else label
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                includeFontPadding = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            if (index != NavigationMotionPolicy.SETTINGS_INDEX) {
                val textParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(5) }
                cell.addView(caption, textParams)
            }
            row.addView(cell, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            cells.add(cell)
            captions.add(caption)
            symbolsViews.add(pictogram)
        }
        addView(row, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Relayouts (insets, font scale, multi-window) reposition without motion.
        row.addOnLayoutChangeListener { _, _, _, right, bottom, oldLeft, oldTop,
            oldRight, oldBottom ->
            if (!indicatorReady || right != oldRight || bottom != oldBottom ||
                oldRight == oldLeft && oldBottom == oldTop) {
                moveIndicator(false)
            }
        }
        updateSelectionVisuals()
    }

    fun section(index: Int): View? = cells.getOrNull(index)

    fun select(index: Int, animate: Boolean = true) {
        if (!NavigationMotionPolicy.valid(index)) return
        if (selected == index && indicatorReady) return
        selected = index
        updateSelectionVisuals()
        moveIndicator(animate)
    }

    private fun updateSelectionVisuals() {
        for (i in cells.indices) {
            val active = i == selected
            val color = if (active) Color.WHITE else normalIcon
            symbolsViews[i].setImageDrawable(AppIconDrawable(symbols[i], color))
            captions[i].setTextColor(if (active) Color.WHITE else normalText)
            captions[i].setTypeface(null, if (active)
                android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            cells[i].contentDescription = labels[i] +
                if (active) ", выбранная вкладка" else ""
            cells[i].isSelected = active
        }
    }

    private fun moveIndicator(animate: Boolean) {
        val cell = cells.getOrNull(selected) ?: return
        if (row.width <= 0 || cell.width <= 0) return
        val params = indicator.layoutParams
        if (params.width != cell.width) {
            params.width = cell.width
            indicator.layoutParams = params
        }
        val goal = (row.left + cell.left - indicator.left).toFloat()
        // Capture the rendered position BEFORE cancelling an in-flight animation.
        val from = indicator.translationX
        slide?.cancel()
        slide = null
        val allowed = indicatorReady && animate &&
            NavigationMotionPolicy.canAnimate(context)
        indicatorReady = true
        if (!allowed || kotlin.math.abs(goal - from) < .5f) {
            indicator.translationX = goal
            return
        }
        slide = ValueAnimator.ofFloat(from, goal).apply {
            duration = NavigationMotionPolicy.INDICATOR_DURATION_MS
            interpolator = PathInterpolator(.2f, 0f, .0f, 1f)
            addUpdateListener { value ->
                indicator.translationX = value.animatedValue as Float
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        slide?.cancel()
        slide = null
        super.onDetachedFromWindow()
    }
}
