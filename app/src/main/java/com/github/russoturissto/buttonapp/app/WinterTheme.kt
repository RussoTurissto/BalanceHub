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
 * Procedural ice overlay that follows the *actual* rounded card geometry.
 * Decorative drawings are confined to 0..14dp around every edge, outside
 * the 18dp content padding. Its opacity is 89% of the reference art.
 */
internal class WinterFrostDrawable(
    private val original: Drawable,
    private val density: Float,
    private val dark: Boolean,
    private val surface: WinterSurface
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var opacity = 255
    private val d get() = density

    private fun ice(alpha: Int): Int {
        val actual = (alpha * WinterThemePolicy.FROST_INTENSITY * opacity / 255f)
            .toInt().coerceIn(0, 255)
        return if (dark) Color.argb(actual, 204, 244, 251)
            else Color.argb(actual, 58, 132, 151)
    }

    override fun onBoundsChange(bounds: Rect) { original.bounds = bounds }

    private fun snowflake(canvas: Canvas, cx: Float, cy: Float, size: Float, alpha: Int) {
        stroke.shader = null
        stroke.color = ice(alpha)
        stroke.strokeWidth = (0.85f * d).coerceAtLeast(1.0f)
        for (arm in 0 until 6) {
            val a = Math.PI * arm / 3.0
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            val ex = cx + dx * size
            val ey = cy + dy * size
            canvas.drawLine(cx, cy, ex, ey, stroke)
            val bx = cx + dx * size * .69f
            val by = cy + dy * size * .69f
            val twig = size * .23f
            canvas.drawLine(bx, by, bx - dx * twig + dy * twig,
                by - dy * twig - dx * twig, stroke)
            canvas.drawLine(bx, by, bx - dx * twig - dy * twig,
                by - dy * twig + dx * twig, stroke)
        }
    }

    private fun frostSprig(
        canvas: Canvas, x: Float, y: Float, dx: Float, dy: Float,
        length: Float, alpha: Int
    ) {
        stroke.shader = null
        stroke.color = ice(alpha)
        stroke.strokeWidth = .85f * d
        canvas.drawLine(x, y, x + dx * length, y + dy * length, stroke)
        val px = -dy
        val py = dx
        for (i in 1..5) {
            val t = i / 6f
            val bx = x + dx * length * t
            val by = y + dy * length * t
            val twig = (4.5f + (1f - t) * 3.5f) * d
            stroke.strokeWidth = .65f * d
            canvas.drawLine(bx, by, bx + (dx * .6f + px) * twig,
                by + (dy * .6f + py) * twig, stroke)
            canvas.drawLine(bx, by, bx + (dx * .6f - px) * twig,
                by + (dy * .6f - py) * twig, stroke)
        }
    }

    override fun draw(canvas: Canvas) {
        original.bounds = bounds
        original.draw(canvas)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 20f * d || h <= 20f * d) return

        canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
            canvas.withSave {
                val edge = if (surface == WinterSurface.SCREEN) 0f else 1.0f * d
                val radius = when (surface) {
                    WinterSurface.DIALOG -> 22f * d
                    WinterSurface.NAVIGATION -> 20f * d
                    WinterSurface.PIN_KEY -> 12f * d
                    else -> 20f * d
                }
                val outer = RectF(edge, edge, w - edge, h - edge)
                if (surface == WinterSurface.SCREEN) {
                    canvas.clipRect(0f, 0f, w, h)
                } else {
                    val shape = Path().apply {
                        addRoundRect(outer, radius, radius, Path.Direction.CW)
                    }
                    canvas.clipPath(shape)
                }

                if (surface == WinterSurface.SCREEN) {
                    fill.shader = LinearGradient(0f, 0f, 16f * d, 0f,
                        ice(115), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, 16f * d, h, fill)
                    fill.shader = LinearGradient(w, 0f, w - 16f * d, 0f,
                        ice(115), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    canvas.drawRect(w - 16f * d, 0f, w, h, fill)
                    fill.shader = null
                    val steps = (h / (155f * d)).toInt().coerceIn(3, 12)
                    for (i in 0..steps) {
                        val y = h * (i + .45f) / (steps + 1)
                        snowflake(canvas, 7.5f * d, y, 5.5f * d, 185)
                        snowflake(canvas, w - 7.5f * d, y + 22f * d,
                            5.0f * d, 165)
                        frostSprig(canvas, 1.5f * d, y - 15f * d, .67f, .74f,
                            12f * d, 180)
                        frostSprig(canvas, w - 1.5f * d, y + 10f * d,
                            -.67f, .74f, 12f * d, 180)
                    }
                    return@withSave
                }

                // Glow, ice line and frosted lip follow the same rounded shape.
                stroke.shader = null
                stroke.color = ice(if (surface == WinterSurface.PIN_KEY) 70 else 80)
                stroke.strokeWidth = 4.3f * d
                canvas.drawRoundRect(RectF(edge + 2.1f * d, edge + 2.1f * d,
                    w - edge - 2.1f * d, h - edge - 2.1f * d),
                    (radius - 2.1f * d).coerceAtLeast(0f),
                    (radius - 2.1f * d).coerceAtLeast(0f), stroke)
                stroke.color = ice(if (surface == WinterSurface.PIN_KEY) 130 else 205)
                stroke.strokeWidth = 1.4f * d
                canvas.drawRoundRect(RectF(edge + 1f * d, edge + 1f * d,
                    w - edge - 1f * d, h - edge - 1f * d),
                    (radius - d).coerceAtLeast(0f),
                    (radius - d).coerceAtLeast(0f), stroke)

                val lip = if (surface == WinterSurface.PIN_KEY) 5f * d else 14f * d
                fill.shader = LinearGradient(0f, 0f, 0f, lip,
                    ice(if (surface == WinterSurface.PIN_KEY) 88 else 165),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, lip, fill)
                fill.shader = LinearGradient(0f, h, 0f, h - lip,
                    ice(if (surface == WinterSurface.PIN_KEY) 70 else 125),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, h - lip, w, h, fill)
                fill.shader = null

                if (surface == WinterSurface.PIN_KEY) {
                    snowflake(canvas, 5.3f * d, 5.3f * d, 2.7f * d, 165)
                    snowflake(canvas, w - 5.3f * d, h - 5.3f * d,
                        2.7f * d, 165)
                    return@withSave
                }

                // A finite corner pattern; 18dp content insets remain clear.
                val near = 8f * d
                frostSprig(canvas, near, 5f * d, .85f, .53f, 14f * d, 230)
                frostSprig(canvas, w - near, 5f * d, -.85f, .53f, 14f * d, 230)
                snowflake(canvas, near, 7.5f * d, 6f * d, 195)
                snowflake(canvas, w - near, 7.5f * d, 6f * d, 195)
                if (h > 54f * d) {
                    frostSprig(canvas, near, h - 5f * d, .85f, -.53f,
                        13f * d, 205)
                    frostSprig(canvas, w - near, h - 5f * d, -.85f, -.53f,
                        13f * d, 205)
                    snowflake(canvas, near, h - 7f * d, 5f * d, 178)
                    snowflake(canvas, w - near, h - 7f * d, 5f * d, 178)
                }
                if (h > 105f * d) {
                    for (i in 1..3) {
                        val y = h * i / 4f
                        frostSprig(canvas, 2.5f * d, y, .9f, -.42f,
                            9f * d, 145)
                        frostSprig(canvas, w - 2.5f * d, y, -.9f, -.42f,
                            9f * d, 145)
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
