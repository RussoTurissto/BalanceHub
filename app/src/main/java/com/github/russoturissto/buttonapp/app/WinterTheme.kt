package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.edit
import androidx.core.graphics.withTranslation
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/** Local date: December 20 through January 10 inclusive, every year. */
internal object WinterThemePolicy {
    const val AUTO = "auto"
    const val FORCE_ON = "force_on"
    const val FORCE_OFF = "force_off"
    const val FROST_INTENSITY = 0.89f

    fun enabled(month: Int, day: Int, mode: String = AUTO): Boolean = when (mode) {
        FORCE_ON -> true
        FORCE_OFF -> false
        else -> (month == 12 && day in 20..31) || (month == 1 && day in 1..10)
    }
}

internal object WinterThemeState {
    private const val PREFS = "balancehub_data"
    private const val KEY = "winter_theme_test_mode"

    fun mode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, WinterThemePolicy.AUTO) ?: WinterThemePolicy.AUTO

    fun setMode(context: Context, value: String) {
        require(value in listOf(WinterThemePolicy.AUTO,
            WinterThemePolicy.FORCE_ON, WinterThemePolicy.FORCE_OFF))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY, value)
        }
    }

    fun active(context: Context): Boolean {
        val today = Calendar.getInstance()
        return WinterThemePolicy.enabled(today.get(Calendar.MONTH) + 1,
            today.get(Calendar.DAY_OF_MONTH), mode(context))
    }
}

internal enum class WinterSurface { SCREEN, CARD, NAVIGATION, DIALOG, PIN_KEY }

/**
 * Procedural frost; never affects layout, clicks, text or calendar highlights.
 * All crystals sit in outer 12dp of the screen, and at component edges.
 */
internal class WinterFrostDrawable(
    private val original: Drawable,
    private val density: Float,
    private val dark: Boolean,
    private val surface: WinterSurface
) : Drawable() {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255

    private fun frostColor(alpha: Int): Int {
        val a = (alpha * WinterThemePolicy.FROST_INTENSITY * opacity / 255f)
            .toInt().coerceIn(0, 255)
        return if (dark) Color.argb(a, 215, 248, 249)
            else Color.argb(a, 70, 151, 161)
    }

    override fun onBoundsChange(bounds: Rect) {
        original.bounds = bounds
    }

    private fun crystal(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int) {
        pen.shader = null
        pen.color = frostColor(alpha)
        pen.strokeWidth = (0.68f * density).coerceAtLeast(.65f)
        pen.strokeCap = Paint.Cap.ROUND
        pen.style = Paint.Style.STROKE
        for (arm in 0..5) {
            val angle = arm * Math.PI / 3.0
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            canvas.drawLine(cx, cy, cx + dx * r, cy + dy * r, pen)
            val bx = cx + dx * r * .67f
            val by = cy + dy * r * .67f
            val side = r * .22f
            canvas.drawLine(bx, by, bx - dy * side - dx * side,
                by + dx * side - dy * side, pen)
            canvas.drawLine(bx, by, bx + dy * side - dx * side,
                by - dx * side - dy * side, pen)
        }
        pen.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        original.bounds = bounds
        original.draw(canvas)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w < 12f * density || h < 12f * density) return
        canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
        val d = density
        if (surface == WinterSurface.SCREEN) {
            // The content begins at 18dp, so frost never touches the content.
            pen.shader = LinearGradient(0f, 0f, 13f * d, 0f,
                frostColor(90), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            pen.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, 13f * d, h, pen)
            pen.shader = LinearGradient(w, 0f, w - 13f * d, 0f,
                frostColor(90), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(w - 13f * d, 0f, w, h, pen)
            pen.shader = null
            for (i in 0..7) {
                val y = h * (i + .5f) / 8f
                crystal(canvas, 6f * d, y, 3.3f * d, 85)
                crystal(canvas, w - 6f * d, y + 8f * d, 2.9f * d, 75)
            }
        } else {
            val radius = when (surface) {
                WinterSurface.DIALOG -> 22f * d
                WinterSurface.NAVIGATION -> 20f * d
                WinterSurface.PIN_KEY -> 13f * d
                else -> 20f * d
            }
            val inset = 1.5f * d
            val frame = RectF(inset, inset, w - inset, h - inset)
            pen.shader = null
            pen.style = Paint.Style.STROKE
            pen.strokeWidth = (1.15f * d).coerceAtLeast(1f)
            pen.color = frostColor(if (surface == WinterSurface.DIALOG) 160 else 115)
            canvas.drawRoundRect(frame, radius, radius, pen)
            pen.style = Paint.Style.FILL
            // A faint snow shelf only in the top 10dp, above actual card text.
            pen.shader = LinearGradient(0f, 0f, 0f, 10f * d,
                frostColor(115), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(inset, inset, w - inset, 10f * d),
                radius * .45f, radius * .45f, pen)
            pen.shader = null
            val corner = if (surface == WinterSurface.PIN_KEY) 4f * d else 9f * d
            val r = if (surface == WinterSurface.PIN_KEY) 1.8f * d else 3.6f * d
            crystal(canvas, corner, corner, r, 125)
            crystal(canvas, w - corner, corner, r, 125)
            if (h > 55f * d && surface != WinterSurface.PIN_KEY) {
                crystal(canvas, corner, h - corner, r * .8f, 100)
                crystal(canvas, w - corner, h - corner, r * .8f, 100)
            }
            if (h > 90f * d && surface != WinterSurface.PIN_KEY) {
                for (i in 1..3) {
                    val y = h * i / 4f
                    crystal(canvas, 3.5f * d, y, 2f * d, 75)
                    crystal(canvas, w - 3.5f * d, y, 2f * d, 75)
                }
            }
        }
        pen.shader = null
        pen.style = Paint.Style.FILL
        }
    }

    override fun setAlpha(alpha: Int) {
        opacity = alpha.coerceIn(0, 255)
        original.alpha = opacity
        invalidateSelf()
    }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        original.setColorFilter(colorFilter)
        invalidateSelf()
    }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Original turquoise wave plus static, offline New Year ornaments. */
internal class WinterHeaderDrawable(
    private val original: Drawable,
    private val density: Float
) : Drawable() {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onBoundsChange(bounds: Rect) { original.bounds = bounds }

    override fun draw(canvas: Canvas) {
        original.bounds = bounds
        original.draw(canvas)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
        val d = density

        val wire = Path().apply {
            moveTo(0f, h * .17f)
            cubicTo(w * .26f, h * .33f, w * .63f, h * .10f, w, h * .24f)
        }
        pen.shader = null
        pen.color = Color.argb(185, 233, 248, 237)
        pen.style = Paint.Style.STROKE
        pen.strokeWidth = .85f * d
        canvas.drawPath(wire, pen)
        pen.style = Paint.Style.FILL
        for (i in 0..9) {
            val x = w * (i + .45f) / 10f
            val t = x / w
            val y = h * (.17f + .11f * sin(t.toDouble() * Math.PI).toFloat())
            pen.color = Color.argb(95, 255, 221, 131)
            canvas.drawCircle(x, y + 3f * d, 5.4f * d, pen)
            pen.color = if (i % 3 == 0) Color.rgb(255, 232, 176)
                else Color.rgb(255, 204, 100)
            canvas.drawCircle(x, y + 3f * d, 2.1f * d, pen)
        }

        fun sprig(x: Float, y: Float, direction: Float) {
            pen.color = Color.argb(190, 29, 102, 77)
            pen.strokeWidth = 1.6f * d
            pen.strokeCap = Paint.Cap.ROUND
            pen.style = Paint.Style.STROKE
            canvas.drawLine(x, y, x + direction * 45f * d, y + 9f * d, pen)
            for (j in 0..6) {
                val bx = x + direction * j * 6f * d
                val by = y + j * 1.2f * d
                pen.color = if (j % 2 == 0) Color.argb(210, 106, 190, 154)
                    else Color.argb(220, 49, 131, 107)
                canvas.drawLine(bx, by, bx + direction * 10f * d,
                    by - 9f * d, pen)
                canvas.drawLine(bx, by, bx + direction * 10f * d,
                    by + 9f * d, pen)
            }
            pen.style = Paint.Style.FILL
        }
        sprig(0f, h * .06f, 1f)
        sprig(w, h * .12f, -1f)

        val bx = w * .88f
        val by = h * .62f
        pen.style = Paint.Style.STROKE
        pen.strokeWidth = .9f * d
        pen.color = Color.argb(215, 255, 230, 173)
        canvas.drawLine(bx, h * .26f, bx, by - 6f * d, pen)
        pen.style = Paint.Style.FILL
        pen.color = Color.rgb(205, 62, 65)
        canvas.drawCircle(bx, by, 5.5f * d, pen)
        pen.color = Color.argb(210, 255, 229, 192)
        canvas.drawCircle(bx - 1.5f * d, by - 1.7f * d, 1.6f * d, pen)
        pen.color = Color.argb(165, 223, 251, 249)
        pen.strokeWidth = .85f * d
        pen.style = Paint.Style.STROKE
        for (pos in arrayOf(.58f to .55f, .75f to .72f, .95f to .48f)) {
            val cx = pos.first * w
            val cy = pos.second * h
            for (i in 0..5) {
                val a = i * Math.PI / 3.0
                canvas.drawLine(cx, cy, cx + cos(a).toFloat() * 4f * d,
                    cy + sin(a).toFloat() * 4f * d, pen)
            }
        }
        }
    }
    override fun setAlpha(alpha: Int) { original.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        original.setColorFilter(colorFilter)
        invalidateSelf()
    }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
