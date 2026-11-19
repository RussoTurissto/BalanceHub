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
import androidx.core.graphics.withSave
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/** Local date: December 20 through January 10 inclusive, every year. */
internal object WinterThemePolicy {
    const val AUTO = "auto"
    const val FORCE_ON = "force_on"
    const val FORCE_OFF = "force_off"
    const val FROST_INTENSITY = 0.95f

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
 * Feathered ice like the approved reference, at 95% reference intensity.
 * The details stay in a narrow band near the edges so text remains legible.
 * One wrapper is reused by cards, modals, the navigation bar, and the screen.
 */
internal class WinterFrostDrawable(
    private val original: Drawable,
    private val density: Float,
    private val dark: Boolean,
    private val surface: WinterSurface
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val d get() = density
    private var opacity = 255
    private fun ice(alpha: Int): Int {
        val actual = (alpha * WinterThemePolicy.FROST_INTENSITY * opacity / 255f)
            .toInt().coerceIn(0, 255)
        return if (dark) Color.argb(actual, 214, 247, 252)
            else Color.argb(actual, 52, 141, 163)
    }

    override fun onBoundsChange(bounds: Rect) { original.bounds = bounds }

    private fun line(canvas: Canvas, x: Float, y: Float,
        xx: Float, yy: Float, thickness: Float, alpha: Int
    ) {
        stroke.shader = null
        stroke.color = ice(alpha)
        stroke.strokeWidth = thickness * d
        canvas.drawLine(x, y, xx, yy, stroke)
    }

    /** Many fine nested branches instead of the old repeated geometric stamps. */
    private fun feather(canvas: Canvas, x: Float, y: Float,
        dx: Float, dy: Float, length: Float, strength: Int
    ) {
        val perpX = -dy
        val perpY = dx
        val endX = x + dx * length
        val endY = y + dy * length
        line(canvas, x, y, endX, endY, .8f, strength)
        for (i in 1..10) {
            val t = i / 11f
            val bx = x + dx * length * t
            val by = y + dy * length * t
            val sideLength = length * (.40f - .22f * t)
            for (side in listOf(-1f, 1f)) {
                val ex = bx + dx * sideLength * .30f + perpX * sideLength * side
                val ey = by + dy * sideLength * .30f + perpY * sideLength * side
                line(canvas, bx, by, ex, ey, .51f, strength * 8 / 10)
                if (i % 2 == 0) {
                    val sx = bx + (ex - bx) * .7f
                    val sy = by + (ey - by) * .7f
                    line(canvas, sx, sy, ex + dx * sideLength * .16f,
                        ey + dy * sideLength * .16f, .38f,
                        strength * 6 / 10)
                }
            }
        }
    }

    private fun sparkle(canvas: Canvas, x: Float, y: Float,
        radius: Float, alpha: Int
    ) {
        for (arm in 0 until 6) {
            val a = arm * Math.PI / 3.0
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            line(canvas, x, y, x + dx * radius, y + dy * radius, .48f, alpha)
            val bx = x + dx * radius * .69f
            val by = y + dy * radius * .69f
            line(canvas, bx, by, bx - dx * radius * .14f + dy * radius * .15f,
                by - dy * radius * .14f - dx * radius * .15f, .35f, alpha)
            line(canvas, bx, by, bx - dx * radius * .14f - dy * radius * .15f,
                by - dy * radius * .14f + dx * radius * .15f, .35f, alpha)
        }
    }

    private fun corner(canvas: Canvas, x: Float, y: Float,
        signX: Float, signY: Float, wide: Boolean
    ) {
        val size = if (wide) 19f * d else 13f * d
        feather(canvas, x, y, signX * .93f, signY * .37f, size, 216)
        feather(canvas, x, y, signX * .30f, signY * .95f, size * 1.10f, 212)
        feather(canvas, x, y, signX * .72f, signY * .70f, size * .88f, 180)
        if (wide) {
            feather(canvas, x + signX * 11f * d, y + signY * 2.2f * d,
                signX * .85f, signY * .52f, 13f * d, 158)
        }
        sparkle(canvas, x + signX * 7f * d, y + signY * 7f * d,
            2.4f * d, 135)
    }

    private fun frame(canvas: Canvas, w: Float, h: Float, radius: Float) {
        for ((inset, thickness, alpha) in listOf(
            Triple(3.5f, 5.2f, 45),
            Triple(2.4f, 2.8f, 82),
            Triple(1.2f, 1.0f, 217)
        )) {
            stroke.shader = null
            stroke.color = ice(alpha)
            stroke.strokeWidth = thickness * d
            canvas.drawRoundRect(RectF(inset * d, inset * d, w - inset * d,
                h - inset * d),
                (radius - inset * d).coerceAtLeast(0f),
                (radius - inset * d).coerceAtLeast(0f), stroke)
        }
    }

    override fun draw(canvas: Canvas) {
        original.bounds = bounds
        original.draw(canvas)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w < 20f * d || h < 20f * d) return
        canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
            canvas.withSave {
                val screen = surface == WinterSurface.SCREEN
                val radius = when (surface) {
                    WinterSurface.DIALOG -> 22f * d
                    WinterSurface.NAVIGATION -> 20f * d
                    WinterSurface.PIN_KEY -> 12f * d
                    else -> if (screen) 23f * d else 20f * d
                }
                if (screen) {
                    canvas.clipRect(0f, 0f, w, h)
                } else {
                    val shape = Path().apply {
                        addRoundRect(RectF(d, d, w - d, h - d),
                            radius, radius, Path.Direction.CW)
                    }
                    canvas.clipPath(shape)
                }
                // A translucent luminous lip, not a thick solid neon frame.
                frame(canvas, w, h, radius)
                if (surface == WinterSurface.PIN_KEY) return@withSave

                val lip = if (screen) 12f * d else 15f * d
                fill.shader = LinearGradient(0f, 0f, 0f, lip,
                    ice(if (screen) 65 else 105), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, lip, fill)
                fill.shader = LinearGradient(0f, h, 0f, h - lip,
                    ice(if (screen) 65 else 95), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP)
                canvas.drawRect(0f, h - lip, w, h, fill)
                fill.shader = null

                val wide = surface == WinterSurface.DIALOG ||
                    surface == WinterSurface.CARD || screen
                val start = if (screen) 5f * d else 8.5f * d
                corner(canvas, start, start, 1f, 1f, wide)
                corner(canvas, w - start, start, -1f, 1f, wide)
                if (h > 54f * d) {
                    corner(canvas, start, h - start, 1f, -1f, wide)
                    corner(canvas, w - start, h - start, -1f, -1f, wide)
                }
                if (h > 110f * d) {
                    // Sparse feathered ice at the edges, not evenly spaced
                    // identical snowflake badges on the content side.
                    val levels = if (screen) 4 else 2
                    for (i in 1..levels) {
                        val y = h * i / (levels + 1f)
                        val stagger = if (i % 2 == 0) 5f * d else -5f * d
                        feather(canvas, 3f * d, y + stagger,
                            .45f, -.9f, 7.2f * d, 108)
                        feather(canvas, w - 3f * d, y - stagger,
                            -.45f, -.9f, 7.2f * d, 108)
                    }
                }
                if (!screen && w > 190f * d) {
                    for (i in 0..7) {
                        val x = w * (.16f + i * .09f)
                        fill.color = ice(if (i % 3 == 0) 140 else 65)
                        canvas.drawCircle(x, 2.2f * d,
                            (if (i % 3 == 0) .8f else .48f) * d, fill)
                    }
                }
            }
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
    @Deprecated("Deprecated in Android API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Foreground ornament layer in the original 84dp wave banner. The title stays
 * exactly where it was: all decorations are in the first ~35dp or the right
 * 38% of the banner. No animations, bitmaps, permission or networking.
 */
internal class WinterHeaderDrawable(
    private val original: Drawable,
    private val density: Float
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val d get() = density
    private var opacity = 255
    override fun onBoundsChange(bounds: Rect) { original.bounds = bounds }

    private fun color(a: Int, r: Int, g: Int, b: Int) =
        Color.argb((a * opacity / 255f).toInt().coerceIn(0, 255), r, g, b)

    private fun snowflake(canvas: Canvas, x: Float, y: Float, radius: Float) {
        stroke.color = color(235, 239, 253, 250)
        stroke.strokeWidth = .95f * d
        for (i in 0 until 6) {
            val a = i * Math.PI / 3.0
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            canvas.drawLine(x, y, x + dx * radius, y + dy * radius, stroke)
            val bx = x + dx * radius * .7f
            val by = y + dy * radius * .7f
            val t = radius * .22f
            canvas.drawLine(bx, by, bx + (dy - dx) * t,
                by - (dx + dy) * t, stroke)
            canvas.drawLine(bx, by, bx - (dy + dx) * t,
                by + (dx - dy) * t, stroke)
        }
    }

    private fun fir(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float) {
        val vx = x1 - x0
        val vy = y1 - y0
        val dist = kotlin.math.sqrt(vx * vx + vy * vy)
        if (dist < 1f) return
        val nx = vx / dist
        val ny = vy / dist
        val px = -ny
        val py = nx
        stroke.color = color(245, 14, 84, 72)
        stroke.strokeWidth = 2.6f * d
        canvas.drawLine(x0, y0, x1, y1, stroke)
        for (j in 1..15) {
            val t = j / 16f
            val bx = x0 + vx * t
            val by = y0 + vy * t
            val len = (5f + 9f * kotlin.math.sin(Math.PI * t).toFloat()) * d
            for (side in arrayOf(-1f, 1f)) {
                val tipX = bx + nx * (5f * d) + px * (side * len)
                val tipY = by + ny * (5f * d) + py * (side * len)
                stroke.strokeWidth = (1.5f + (j % 3) * .19f) * d
                stroke.color = if (j % 3 == 0)
                    color(250, 55, 151, 117) else color(250, 28, 116, 93)
                canvas.drawLine(bx, by, tipX, tipY, stroke)
                // Snow catches on outer tips, not on the wordmark.
                if (j % 2 == 0) {
                    stroke.color = color(245, 234, 252, 248)
                    stroke.strokeWidth = 1.05f * d
                    canvas.drawLine(tipX - nx * 3f * d, tipY - ny * 3f * d,
                        tipX, tipY, stroke)
                }
            }
        }
    }

    private fun ornament(canvas: Canvas, x: Float, top: Float,
        cy: Float, radius: Float, red: Boolean) {
        stroke.shader = null
        stroke.color = color(230, 255, 236, 185)
        stroke.strokeWidth = 1.0f * d
        canvas.drawLine(x, top, x, cy - radius - 2.4f * d, stroke)
        // A broad halo, a bright metallic ball, and a specular highlight.
        fill.color = if (red) color(65, 255, 111, 119)
            else color(60, 255, 224, 150)
        canvas.drawCircle(x, cy, radius * 1.7f, fill)
        fill.color = if (red) color(255, 220, 64, 66)
            else color(255, 232, 184, 66)
        canvas.drawCircle(x, cy, radius, fill)
        stroke.color = color(245, 255, 240, 190)
        stroke.strokeWidth = .9f * d
        canvas.drawCircle(x, cy, radius * .86f, stroke)
        fill.color = color(255, 255, 252, 231)
        canvas.drawCircle(x - radius * .3f, cy - radius * .32f,
            radius * .26f, fill)
        fill.color = color(255, 243, 218, 158)
        canvas.drawRect(x - 2.3f * d, cy - radius - 2.0f * d,
            x + 2.3f * d, cy - radius + .3f * d, fill)
    }

    override fun draw(canvas: Canvas) {
        original.bounds = bounds
        original.draw(canvas)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w < 180f * d || h < 60f * d) return
        canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
            canvas.withSave {
                canvas.clipRect(0f, 0f, w, h)
                // The festive string stays above the BalanceHub wordmark.
                val cable = Path().apply {
                    moveTo(0f, h * .15f)
                    cubicTo(w * .25f, h * .23f, w * .38f, h * .29f,
                        w * .55f, h * .17f)
                    cubicTo(w * .74f, h * .12f, w * .90f, h * .24f,
                        w, h * .16f)
                }
                stroke.color = color(242, 223, 245, 226)
                stroke.strokeWidth = 1.05f * d
                canvas.drawPath(cable, stroke)
                for (i in 0..15) {
                    val x = w * (i + .3f) / 16f
                    val t = x / w
                    val y = h * (.16f + .06f * kotlin.math.sin(t * Math.PI).toFloat())
                    stroke.color = color(225, 210, 234, 213)
                    stroke.strokeWidth = .85f * d
                    canvas.drawLine(x, y, x, y + 5.5f * d, stroke)
                    fill.color = color(72, 255, 219, 144)
                    canvas.drawCircle(x, y + 8.4f * d, 7.3f * d, fill)
                    fill.color = if (i % 4 == 0) color(255, 255, 245, 204)
                        else color(255, 255, 202, 101)
                    canvas.drawCircle(x, y + 8.4f * d, 2.9f * d, fill)
                    fill.color = color(255, 255, 255, 242)
                    canvas.drawCircle(x - .5f * d, y + 7.2f * d, 1.05f * d, fill)
                }

                // Snow-loaded spruce cluster in the top-right corner.
                fir(canvas, w - 5f * d, 3f * d,
                    w - 104f * d, 20f * d)
                fir(canvas, w - 2f * d, 6f * d,
                    w - 50f * d, 42f * d)
                fir(canvas, w - 16f * d, 0f,
                    w - 72f * d, 29f * d)

                // Two actual baubles, not just their suspension threads.
                ornament(canvas, w * .88f, h * .21f, h * .63f, 6.8f * d, true)
                ornament(canvas, w * .73f, h * .20f, h * .44f, 4.9f * d, false)
                snowflake(canvas, w * .56f, h * .62f, 6.0f * d)
                snowflake(canvas, w * .69f, h * .75f, 4.4f * d)
                snowflake(canvas, w * .95f, h * .76f, 5.5f * d)

                // Frosted accent follows the *existing* bezier wave exactly.
                val lip = Path().apply {
                    moveTo(0f, h * .90f)
                    cubicTo(w * .20f, h * .74f, w * .35f, h * 1.02f,
                        w * .53f, h * .88f)
                    cubicTo(w * .75f, h * .68f, w * .88f, h * .80f,
                        w, h * .93f)
                }
                stroke.color = color(175, 209, 244, 243)
                stroke.strokeWidth = 1.7f * d
                canvas.drawPath(lip, stroke)
            }
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
