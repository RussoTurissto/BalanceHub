package com.github.russoturissto.buttonapp.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The snowy lip is shared by the banner, cable and glints: no independent curves to drift. */
internal object WinterWaveGeometry {
    fun edge(w: Float, h: Float): Path = Path().apply {
        moveTo(0f, h * .78f)
        cubicTo(w * .16f, h * .59f, w * .24f, h * .94f, w * .48f, h * .83f)
        cubicTo(w * .71f, h * .79f, w * .81f, h * .59f, w, h * .76f)
    }
}

/**
 * Illustration of the approved winter reference: warm bulbs BELOW the snowy
 * wave, snow-laden fir branches, a gold bow and star, and turquoise GLASS balls.
 * All geometry is proportional to the header; no downloaded or external assets.
 */
internal class WinterReferenceHeaderDrawable(
    private val original: Drawable,
    private val density: Float
) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = density
    private var opacity = 255
    private fun color(a: Int, r: Int, g: Int, b: Int): Int =
        Color.argb((a * opacity / 255f).toInt().coerceIn(0, 255), r, g, b)
    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float,
        width: Float, ink: Int
    ) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.strokeCap = Paint.Cap.ROUND
        p.color = ink
        canvas.drawLine(x1, y1, x2, y2, p)
        p.style = Paint.Style.FILL
    }
    private fun orb(canvas: Canvas, x: Float, y: Float, radius: Float,
        r: Int, g: Int, b: Int
    ) {
        p.shader = null
        p.style = Paint.Style.FILL
        for ((scale, alpha) in listOf(3.8f to 10, 2.8f to 17, 2.0f to 35, 1.4f to 65)) {
            p.color = color(alpha, r, g, b)
            canvas.drawCircle(x, y, radius * scale, p)
        }
        p.shader = RadialGradient(x - radius * .23f, y - radius * .32f,
            radius * 1.4f,
            intArrayOf(color(255, 255, 252, 232), color(255, 255, 210, 111),
                color(235, 209, 120, 21)), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, p)
        p.shader = null
        p.color = color(245, 255, 255, 235)
        canvas.drawCircle(x - radius * .25f, y - radius * .3f,
            radius * .19f, p)
    }
    private fun flake(canvas: Canvas, x: Float, y: Float, size: Float,
        alpha: Int = 210
    ) {
        for (i in 0 until 6) {
            val theta = i * PI / 3
            val dx = cos(theta).toFloat()
            val dy = sin(theta).toFloat()
            line(canvas, x, y, x + dx * size, y + dy * size, .75f * d,
                color(alpha, 230, 251, 255))
            val bx = x + dx * size * .67f
            val by = y + dy * size * .67f
            val wing = size * .23f
            line(canvas, bx, by, bx - dx * wing + dy * wing,
                by - dy * wing - dx * wing, .60f * d, color(alpha, 220, 249, 255))
            line(canvas, bx, by, bx - dx * wing - dy * wing,
                by - dy * wing + dx * wing, .60f * d, color(alpha, 220, 249, 255))
        }
    }

    private fun garland(canvas: Canvas, w: Float, h: Float) {
        val path = WinterWaveGeometry.edge(w, h)
        val measure = PathMeasure(path, false)
        val point = FloatArray(2)
        // The cable hugs the snowy wave, with warm bulbs HANGING beneath it.
        p.color = color(190, 96, 100, 65)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.1f * d
        canvas.drawPath(path, p)
        p.style = Paint.Style.FILL
        // Thin snow deposits accumulate on the physical wire, especially at
        // bends. Irregular volume and intermittent gaps prevent a white stripe.
        val snowPoint = FloatArray(2)
        for (patch in 0..34) {
            val t = .015f + patch * .027f
            measure.getPosTan(measure.length * t, snowPoint, null)
            val drift = (.47f + .28f * sin(patch * 2.37f).toFloat()
                + .18f * cos(patch * 1.41f).toFloat()).coerceAtLeast(.15f)
            p.color = color((116 + patch % 4 * 19).coerceAtMost(220),
                233, 248, 252)
            canvas.drawOval(RectF(snowPoint[0] - 2.4f * d,
                snowPoint[1] - drift * 1.6f * d,
                snowPoint[0] + (1.1f + drift) * d,
                snowPoint[1] + .24f * d), p)
            p.color = color(230, 249, 255, 255)
            canvas.drawCircle(snowPoint[0] - .4f * d,
                snowPoint[1] - drift * .88f * d, .30f * d, p)
        }
        for (i in 0..14) {
            val t = .035f + .057f * i
            measure.getPosTan(measure.length * t, point, null)
            val x = point[0]
            val y = point[1] + 3.1f * d
            line(canvas, x, point[1], x, y + 1.6f * d, .75f * d,
                color(210, 136, 114, 62))
            if (i < 12) {
                orb(canvas, x, y + 3.0f * d,
                    (1.95f + if (i % 4 == 2) .65f else 0f) * d,
                    255, 198, 87)
            } else {
                // The last bulbs peek from under the spruce on the right.
                orb(canvas, x, y + 3f * d, 1.6f * d, 255, 180, 66)
            }
        }
    }

    /** Dense, anti-aliased individually shaded needles with irregular snow. */
    private fun firSprig(canvas: Canvas, x0: Float, y0: Float,
        x1: Float, y1: Float, length: Float
    ) {
        val vx = x1 - x0
        val vy = y1 - y0
        val distance = sqrt(vx * vx + vy * vy).coerceAtLeast(1f)
        val nx = vx / distance
        val ny = vy / distance
        val px = -ny
        val py = nx
        // A thick shaded core makes each frond an actual tree branch.
        line(canvas, x0, y0 + .5f * d, x1, y1 + .5f * d,
            3.0f * d, color(125, 2, 30, 32))
        line(canvas, x0, y0, x1, y1, 1.15f * d, color(236, 24, 77, 64))
        val needles = 47
        for (i in 1..needles) {
            val t = i.toFloat() / (needles + 1)
            val bx = x0 + vx * t
            val by = y0 + vy * t
            val bulge = .20f + .80f * sin(PI * t).toFloat()
            val stagger = .88f + .12f * sin(i * 1.87).toFloat()
            val base = length * bulge * stagger
            for (side in listOf(-1f, 1f)) {
                val needle = base * (if (i % 5 == 0) .76f else 1f)
                val tx = bx + px * needle * side + nx * needle * .30f
                val ty = by + py * needle * side + ny * needle * .30f
                line(canvas, bx + .35f * d, by + .5f * d, tx + .2f * d, ty + .5f * d,
                    1.13f * d, color(188, 4, 42, 41))
                line(canvas, bx, by, tx, ty, .67f * d,
                    if (side > 0f) color(250, 49, 146, 123)
                    else color(240, 17, 107, 92))
                // Snow rests on physically upward-facing needles and catches
                // at selected tips; don't coat the entire fir in white.
                if (ty < by && i % 3 != 1) {
                    val start = .42f + .12f * ((i % 4) / 4f)
                    line(canvas, bx + (tx - bx) * start,
                        by + (ty - by) * start - .30f * d, tx, ty - .30f * d,
                        (.55f + (i % 3) * .07f) * d,
                        color(174 + i % 3 * 18, 232, 249, 252))
                    if (i % 4 == 0) {
                        p.color = color(180, 251, 255, 255)
                        canvas.drawOval(RectF(tx - .75f * d, ty - .70f * d,
                            tx + .54f * d, ty + .16f * d), p)
                    }
                }
            }
        }
    }

    private fun spruce(canvas: Canvas, w: Float, h: Float) {
        // Composition stays on the upper-right of the banner. The BalanceHub
        // wordmark occupies the left; no needles touch its text rectangle.
        val bx = w * .985f
        val by = h * .15f
        firSprig(canvas, bx, by, w * .69f, h * .36f, 8.7f * d)
        firSprig(canvas, w * 1.03f, h * .08f, w * .77f, h * .17f, 8.9f * d)
        firSprig(canvas, w * 1.02f, h * .20f, w * .77f, h * .52f, 9.2f * d)
        firSprig(canvas, w * 1.02f, h * .23f, w * .92f, h * .70f, 8.0f * d)
        firSprig(canvas, w * .94f, h * .06f, w * .82f, h * .49f, 8.5f * d)
        firSprig(canvas, w * .94f, h * .16f, w * .66f, h * .28f, 10.0f * d)
        firSprig(canvas, w * 1.01f, h * .26f, w * .72f, h * .44f, 11.4f * d)
        firSprig(canvas, w * .99f, h * .03f, w * .82f, h * .29f, 10.1f * d)
        firSprig(canvas, w * .92f, h * .17f, w * .80f, h * .60f, 8.7f * d)

        // Irregularly piled snow has a blue-shadow underside and a bright
        // top. Neither the lamps nor the wordmark are covered.
        val snow = Path().apply {
            moveTo(w * .705f, h * .30f)
            cubicTo(w * .735f, h * .27f, w * .76f, h * .19f, w * .79f, h * .22f)
            cubicTo(w * .815f, h * .16f, w * .83f, h * .18f, w * .85f, h * .17f)
            cubicTo(w * .885f, h * .08f, w * .91f, h * .12f, w * .94f, h * .09f)
            cubicTo(w * .97f, h * .07f, w * .99f, h * .11f, w * 1.03f, h * .09f)
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.shader = null
        p.strokeWidth = 4.7f * d
        p.color = color(120, 69, 150, 168)
        canvas.withTranslation(0f, 1.6f * d) { canvas.drawPath(snow, p) }
        p.strokeWidth = 3.0f * d
        p.color = color(238, 234, 251, 253)
        canvas.drawPath(snow, p)
        p.strokeWidth = .78f * d
        p.color = color(252, 255, 255, 255)
        canvas.withTranslation(0f, -1.0f * d) { canvas.drawPath(snow, p) }
        p.style = Paint.Style.FILL

        for (i in 0..37) {
            // Non-uniform snow clumps on the spruce silhouette.
            val t = i / 37f
            val x = w * (.705f + t * .29f)
            val y = h * (.305f - .205f * sin(t * PI / 1.90).toFloat())
            val jitter = .7f * sin(i * 2.73).toFloat() * d
            p.color = color(140 + (i % 4) * 23, 238, 250, 252)
            canvas.drawOval(RectF(x - 1.2f * d, y - 1.2f * d + jitter,
                x + (1.0f + (i % 3) * .24f) * d,
                y + .36f * d + jitter), p)
        }
    }

    /** Fine rime on the top of a glass globe and a reflected warm bulb. */
    private fun glassSnow(canvas: Canvas, x: Float, y: Float, r: Float) {
        val crest = Path().apply {
            moveTo(x - r * .71f, y - r * .58f)
            cubicTo(x - r * .57f, y - r * .91f,
                x - r * .34f, y - r * .94f, x - r * .12f, y - r * .96f)
            cubicTo(x + r * .14f, y - r * 1.09f,
                x + r * .47f, y - r * .85f, x + r * .67f, y - r * .70f)
        }
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = .94f * d
        p.color = color(237, 244, 255, 255)
        canvas.drawPath(crest, p)
        p.style = Paint.Style.FILL
        for (i in 0..11) {
            val theta = (PI * (.18 + i * .055))
            val px = x + cos(theta).toFloat() * r * .92f
            val py = y - sin(theta).toFloat() * r * .91f
            p.color = color(110 + (i % 4) * 29, 250, 255, 255)
            canvas.drawCircle(px, py, (if (i % 3 == 0) .51f else .28f) * d, p)
        }
        // Amber light reflected inside the turquoise glass.
        p.color = color(108, 255, 222, 141)
        canvas.drawOval(RectF(x + r * .31f, y - r * .14f,
            x + r * .50f, y + r * .17f), p)
    }

    /** Two glossy glass baubles with translucent edges and warm reflections. */
    private fun glassBall(canvas: Canvas, x: Float, y: Float, r: Float) {
        line(canvas, x, y - r * 2.48f, x, y - r * 1.11f,
            .55f * d, color(230, 221, 208, 161))
        // Suspension fitting and tiny specular rim.
        p.shader = LinearGradient(x - r * .24f, y - r,
            x + r * .24f, y - r,
            intArrayOf(color(225, 125, 91, 24), color(255, 253, 225, 145),
                color(240, 131, 85, 28)), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(x - r * .24f, y - r * 1.16f,
            x + r * .24f, y - r * .88f), 1.2f * d, 1.2f * d, p)
        p.shader = null
        for (i in listOf(1.8f, 1.4f, 1.15f)) {
            p.color = color((48 / i).toInt(), 87, 229, 246)
            canvas.drawCircle(x, y, r * i, p)
        }
        p.shader = RadialGradient(x - r * .36f, y - r * .44f,
            r * 1.66f, intArrayOf(
                color(252, 207, 255, 254), color(255, 53, 216, 227),
                color(255, 9, 125, 157), color(255, 3, 44, 77)),
            floatArrayOf(0f, .24f, .64f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = .78f * d
        p.color = color(205, 199, 246, 249)
        canvas.drawCircle(x, y, r * .95f, p)
        p.color = color(210, 247, 255, 254)
        p.strokeWidth = .75f * d
        val reflection = Path().apply {
            moveTo(x - r * .67f, y + r * .10f)
            cubicTo(x - r * .86f, y - r * .35f,
                x - r * .49f, y - r * .79f, x - r * .17f, y - r * .80f)
        }
        canvas.drawPath(reflection, p)
        p.style = Paint.Style.FILL
        p.color = color(235, 250, 255, 253)
        canvas.drawOval(RectF(x - r * .49f, y - r * .70f,
            x - r * .27f, y - r * .40f), p)
        p.color = color(90, 251, 228, 132)
        canvas.drawOval(RectF(x + r * .34f, y - r * .15f,
            x + r * .57f, y + r * .13f), p)
        p.color = color(60, 151, 238, 252)
        canvas.drawCircle(x + r * .24f, y + r * .42f, r * .30f, p)
        flake(canvas, x + r * .12f, y + r * .05f, r * .39f, 165)
        glassSnow(canvas, x, y, r)
    }

    private fun goldenBow(canvas: Canvas, x: Float, y: Float, size: Float) {
        fun ribbon(path: Path, dark: Boolean) {
            p.shader = LinearGradient(x - size, y - size,
                x + size, y + size, intArrayOf(
                    color(255, if (dark) 147 else 255, if (dark) 89 else 230, 48),
                    color(255, 255, 233, 151),
                    color(255, 187, 111, 25)), null, Shader.TileMode.CLAMP)
            canvas.drawPath(path, p)
            p.shader = null
        }
        ribbon(Path().apply {
            moveTo(x, y)
            cubicTo(x - size * .65f, y - size * 1.15f,
                x - size * 1.45f, y - size * .61f, x - size * 1.22f, y)
            cubicTo(x - size * 1.32f, y + size * .37f,
                x - size * .45f, y + size * .36f, x, y)
            close()
        }, false)
        ribbon(Path().apply {
            moveTo(x, y)
            cubicTo(x + size * .55f, y - size * 1.13f,
                x + size * 1.36f, y - size * .82f, x + size * 1.30f, y - size * .07f)
            cubicTo(x + size * 1.42f, y + size * .31f,
                x + size * .42f, y + size * .45f, x, y)
            close()
        }, false)
        ribbon(Path().apply {
            moveTo(x - size * .14f, y)
            lineTo(x - size * .76f, y + size * 1.22f)
            lineTo(x - size * .13f, y + size * .89f)
            lineTo(x + size * .07f, y + size * .13f)
            close()
        }, true)
        ribbon(Path().apply {
            moveTo(x + size * .11f, y)
            lineTo(x + size * 1.08f, y + size * .96f)
            lineTo(x + size * .59f, y + size * 1.12f)
            lineTo(x - size * .11f, y + size * .11f)
            close()
        }, true)
        p.color = color(255, 246, 199, 86)
        canvas.drawCircle(x, y, size * .22f, p)
        p.color = color(240, 255, 242, 181)
        canvas.drawCircle(x - size * .06f, y - size * .07f, size * .075f, p)
        // Snow settles along the upper lip of the ribbon, not its tails.
        val rim = Path().apply {
            moveTo(x - size * 1.20f, y - size * .10f)
            quadTo(x - size * .84f, y - size * .71f, x - size * .43f, y - size * .60f)
            moveTo(x + size * .21f, y - size * .64f)
            quadTo(x + size * .88f, y - size * .85f, x + size * 1.20f, y - size * .29f)
        }
        p.style = Paint.Style.STROKE
        p.color = color(207, 246, 253, 254)
        p.strokeWidth = .9f * d
        canvas.drawPath(rim, p)
        p.style = Paint.Style.FILL
    }

    private fun star(canvas: Canvas, x: Float, y: Float, r: Float) {
        val path = Path()
        for (i in 0..9) {
            val a = -PI / 2 + i * PI / 5
            val rr = if (i % 2 == 0) r else r * .45f
            val px = x + cos(a).toFloat() * rr
            val py = y + sin(a).toFloat() * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        p.shader = LinearGradient(x - r, y - r, x + r, y + r,
            intArrayOf(color(255, 255, 250, 207), color(255, 255, 207, 89),
                color(255, 177, 106, 26)), null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, p)
        p.shader = null
        p.color = color(220, 255, 250, 210)
        p.style = Paint.Style.STROKE
        p.strokeWidth = .8f * d
        canvas.drawPath(path, p)
        p.style = Paint.Style.FILL
        // A touch of snow on the upper face with metallic highlights.
        line(canvas, x - r * .56f, y - r * .35f,
            x - r * .22f, y - r * .18f,
            .78f * d, color(214, 236, 251, 252))
        line(canvas, x - r * .06f, y - r * .72f,
            x + r * .06f, y - r * .75f,
            .74f * d, color(233, 251, 255, 255))
        p.color = color(225, 255, 247, 197)
        canvas.drawCircle(x - r * .23f, y - r * .21f, .55f * d, p)
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
                garland(canvas, w, h)
                flake(canvas, w * .53f, h * .35f, 5.3f * d)
                flake(canvas, w * .61f, h * .66f, 4.4f * d, 170)
                flake(canvas, w * .69f, h * .21f, 3.0f * d, 150)
                // Always render at native device density. The small legacy
                // ornament bitmap from rc4 caused pixelation when scaled.
                // These vectors preserve positions, but stay sharp.
                spruce(canvas, w, h)
                glassBall(canvas, w * .80f, h * .79f, 8.8f * d)
                glassBall(canvas, w * .96f, h * .63f, 4.5f * d)
                goldenBow(canvas, w * .875f, h * .36f, 14.0f * d)
                star(canvas, w * .923f, h * .61f, 11.0f * d)
            }
        }
    }
    override fun onBoundsChange(bounds: android.graphics.Rect) { original.bounds = bounds }
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

/** Empty-event art from the reference: snowy hills, firs and a glowing calendar. */
internal class WinterEmptyEventsDrawable(private val density: Float) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255
    private fun ink(a: Int, r: Int, g: Int, b: Int): Int =
        Color.argb(a * opacity / 255, r, g, b)
    private fun snowyTree(canvas: Canvas, x: Float, y: Float, height: Float) {
        p.color = ink(190, 162, 237, 243)
        p.strokeWidth = height * .035f
        canvas.drawLine(x, y - height * .50f, x, y, p)
        for (layer in 0..3) {
            val top = y - height * (.93f - layer * .18f)
            val bottom = y - height * (.55f - layer * .14f)
            val half = height * (.16f + layer * .09f)
            p.color = ink(240, 23, 98, 119)
            canvas.drawPath(Path().apply {
                moveTo(x, top)
                lineTo(x - half, bottom)
                quadTo(x, bottom - height * .035f, x + half, bottom)
                close()
            }, p)
            p.color = ink(215, 216, 251, 252)
            p.style = Paint.Style.STROKE
            p.strokeWidth = height * .035f
            canvas.drawLine(x - half * .8f, bottom - height * .03f,
                x, top + height * .065f, p)
            p.style = Paint.Style.FILL
        }
    }
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val d = density
        canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
        // Hills glow without a rectangular background.
        p.color = ink(65, 24, 142, 174)
        canvas.drawOval(RectF(w * .08f, h * .67f, w * .92f, h * .99f), p)
        p.color = ink(170, 105, 187, 207)
        canvas.drawPath(Path().apply {
            moveTo(0f, h * .86f)
            quadTo(w * .30f, h * .72f, w * .51f, h * .89f)
            quadTo(w * .80f, h * .70f, w, h * .83f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }, p)
        snowyTree(canvas, w * .14f, h * .81f, h * .34f)
        snowyTree(canvas, w * .26f, h * .78f, h * .23f)
        snowyTree(canvas, w * .87f, h * .79f, h * .36f)
        snowyTree(canvas, w * .76f, h * .80f, h * .22f)
        val cx = w * .50f
        val cy = h * .54f
        val r = h * .20f
        p.color = ink(32, 69, 220, 248)
        canvas.drawCircle(cx, cy, r * 1.4f, p)
        p.color = ink(235, 216, 247, 252)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 2.2f * d
        canvas.drawRoundRect(RectF(cx - r * .82f, cy - r * .72f,
            cx + r * .82f, cy + r * .74f), 10f * d, 10f * d, p)
        canvas.drawLine(cx - r * .82f, cy - r * .33f,
            cx + r * .82f, cy - r * .33f, p)
        for (xx in listOf(-.48f, .48f)) {
            canvas.drawLine(cx + r * xx, cy - r * .95f,
                cx + r * xx, cy - r * .57f, p)
        }
        for (i in 0..5) {
            val theta = i * PI / 3
            val dx = cos(theta).toFloat()
            val dy = sin(theta).toFloat()
            canvas.drawLine(cx, cy + r * .2f,
                cx + dx * r * .43f, cy + r * .2f + dy * r * .43f, p)
        }
        p.style = Paint.Style.FILL
        for (i in 0..18) {
            val x = w * (.10f + ((i * 37) % 80) / 100f)
            val y = h * (.12f + ((i * 29) % 54) / 100f)
            p.color = ink(if (i % 3 == 0) 230 else 140, 230, 250, 255)
            canvas.drawCircle(x, y, if (i % 4 == 0) 1.25f * d else .65f * d, p)
        }
        }
    }
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0,255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter=colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
