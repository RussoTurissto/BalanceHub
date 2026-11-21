package com.github.russoturissto.buttonapp.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Color
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Region
import android.os.Build
import androidx.core.graphics.withSave
import androidx.core.graphics.get
import androidx.core.graphics.set
import android.graphics.drawable.Drawable
import android.widget.TextView

/**
 * Approval-locked festive artwork cropped from the exact owner-approved mockup.
 * The photo fir, glass baubles, snow, bow and hat are unchanged.
 * The former garland raster had a wide opaque light bar and hard-edged fragments:
 * its light cord and bulbs are redrawn as a dedicated independent vector layer.
 *
 * The BalanceHub title remains a real, accessible, independent TextView.
 * The hat follows the measured capital H. The garland is clipped away from
 * the wordmark's bounds, so it can never cross the white letters.
 *
 * Only header foreground pixels are affected. Header sizing, padding and
 * child positions do not change when the winter mode is enabled.
 */
internal class WinterApprovedHeaderDrawable(
    private val density: Float,
    private val wordmark: TextView,
    private val ornaments: Bitmap,
    private val santaHat: Bitmap,
    private val garlandOnly: Boolean = false,
    private val darkMode: Boolean = true
) : Drawable() {
    private companion object { const val PHOTO_WIDTH_FRACTION = 0.45f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var opacity = 255

    // Retouch ONLY the actual snowy photographic wire (72 of 368 source
    // columns). A second painted strand alongside it always looked doubled.
    // This preserves the original wire's snow texture, bulbs, fir and bow.
    private val warmPhoto by lazy(LazyThreadSafetyMode.NONE) {
        val photo = ornaments.copy(Bitmap.Config.ARGB_8888, true)
        val xs = floatArrayOf(0f, 10f, 20f, 30f, 40f, 50f, 60f, 70f)
        val ys = floatArrayOf(145.5f, 143.5f, 142f, 141.5f,
            141f, 139f, 137f, 135f)
        for (x in 0 until minOf(72, photo.width)) {
            val k = (x / 10).coerceIn(0, xs.lastIndex - 1)
            val center = ys[k] + (ys[k + 1] - ys[k]) *
                ((x - xs[k]) / (xs[k + 1] - xs[k]))
            val first = (center - 5f).toInt().coerceAtLeast(0)
            val last = (center + 6f).toInt().coerceAtMost(photo.height - 1)
            for (y in first..last) {
                val p = photo[x, y]
                val red = Color.red(p)
                val green = Color.green(p)
                val blue = Color.blue(p)
                val brightness = (red + green + blue) / 3f
                val neutral = kotlin.math.abs(red - green) < 35 ||
                    blue > green * .70f
                if (brightness <= 45f || green <= 52 || blue <= 37 || !neutral)
                    continue
                val strength = ((brightness - 30f) / 225f).coerceIn(0f, 1f)
                val tint = ((5.2f - kotlin.math.abs(y - center)) / 2.8f)
                    .coerceIn(0f, 1f) *
                    (.7f + .3f * ((72f - x) / 12f).coerceIn(0f, 1f))
                fun blend(old: Int, gold: Float) =
                    (old + (gold - old) * tint).toInt().coerceIn(0, 255)
                photo[x, y] = Color.argb(Color.alpha(p),
                    blend(red, 95f + 157f * strength),
                    blend(green, 56f + 156f * strength),
                    blend(blue, 22f + 98f * strength)))
            }
        }
        photo
    }

    // Both drawable layers use this same cubic. Its endpoint is calibrated
    // to the actual cable entering approved_ornaments.webp, not to a guessed
    // bend drawn over the photograph.
    private data class CableGeometry(
        val startX: Float, val seamX: Float,
        val startY: Float, val seamY: Float,
        val control1Y: Float, val control2Y: Float
    ) {
        fun xAt(t: Float): Float = startX + (seamX - startX) * t
        fun yAt(t: Float): Float {
            val u = 1f - t
            return u * u * u * startY +
                3f * u * u * t * control1Y +
                3f * u * t * t * control2Y + t * t * t * seamY
        }
        fun yAtX(x: Float): Float {
            val t = ((x - startX) / (seamX - startX)).coerceIn(0f, 1f)
            return yAt(t)
        }
        // End tangent of the cubic, continued into the photographed cable.
        fun continuationY(x: Float): Float =
            seamY + 3f * (seamY - control2Y) / (seamX - startX) * (x - seamX)
    }

    private fun cableGeometry(w: Float, h: Float): CableGeometry {
        val seam = w * (1f - PHOTO_WIDTH_FRACTION)
        val photoHeight = w * PHOTO_WIDTH_FRACTION *
            ornaments.height.toFloat() / ornaments.width
        val photoTop = (h - photoHeight) * .12f
        val startY = h * .67f
        // At the left-most pixels of the owner-approved photo the real wire
        // is around 69.5% down; 72% was the source of the visible kink.
        val seamY = photoTop + photoHeight * .695f
        return CableGeometry(-w * .012f, seam, startY, seamY,
            startY + h * .10f, seamY + h * .095f)
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 1f || h <= 1f) return

        canvas.withSave {
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            // Keep approved artwork at its natural scale, including minor overhang.
            // The title mask below still keeps the lamps off the native TextView.
            paint.alpha = opacity

        // The cord belongs in the header BACKGROUND so the actual native
        // title is drawn on top. Cutting out the entire TextView rectangle
        // made most of the wire and lamps disappear on real Android devices.
        if (garlandOnly) {
            // The real ornaments bitmap already includes the right-hand bulbs.
            // Clip the vector strand at the photo's exact left edge: no second
            // cable or glow can appear across the photographic garland.
            canvas.clipRect(0f, 0f, w * (1f - PHOTO_WIDTH_FRACTION), h)
            drawCurvedGarland(canvas, w, h)
            return@withSave
        }

        // Approved snow-covered spruce, pinecone, bow, glass baubles and star.
        // Preserve the artwork's aspect ratio across Android screen widths.
        val rightWidth = w * PHOTO_WIDTH_FRACTION
        val rightHeight = rightWidth * ornaments.height / ornaments.width
        val ornamentTop = (h - rightHeight) * .12f
        canvas.drawBitmap(warmPhoto, null,
            RectF(w - rightWidth, ornamentTop,
                w, ornamentTop + rightHeight), paint)
        // Only warm, translucent halos are laid over the original photo
        // bulbs. No replacement bulbs and no second strand are introduced.
        drawPhotographicLightHalos(canvas, w - rightWidth, ornamentTop,
            rightWidth, rightHeight)
        // The photograph starts with a tiny alpha-fringed gap before its
        // physical cable. Add only a short foreground wire connector; bulbs
        // are never redrawn over the photographed lights.
        drawCableJoin(canvas, w, h)

        if (wordmark.width > 0 && wordmark.height > 0) {
            val p = wordmark.paint
            // Measured text metrics, including its configured letter spacing,
            // place the hat on the actual uppercase H, not an X-percent guess.
            val startH = wordmark.left + p.measureText("Balance") +
                wordmark.letterSpacing * wordmark.textSize * 7f
            val hatWidth = wordmark.textSize * 1.89f
            val hatHeight = hatWidth * santaHat.height / santaHat.width
            val textTop = wordmark.top + wordmark.translationY
            // The cap sits a little lower, with its white brim on the H.
            // Shift the trailing pompom away from the following lowercase u.
            // Keep the original approved bitmap (including its pompom).
            // Align the furry brim to the measured capital-H cap height.
            // In RC8 the body was too far left and the hanging pompom covered u.
            val glyphCapTop = textTop + wordmark.baseline -
                wordmark.textSize * .73f
            val x = startH - hatWidth * .30f
            val y = glyphCapTop - hatHeight * .49f
            canvas.withSave {
                val pompomLeft = x + hatWidth * .81f
                val pompomTop = y + hatHeight * .67f
                // Hide only the misplaced dangling pompom, not the plush brim.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    canvas.clipOutRect(pompomLeft, pompomTop,
                        x + hatWidth, y + hatHeight)
                } else {
                    @Suppress("DEPRECATION")
                    canvas.clipRect(pompomLeft, pompomTop,
                        x + hatWidth, y + hatHeight, Region.Op.DIFFERENCE)
                }
                canvas.drawBitmap(santaHat, null,
                    RectF(x, y, x + hatWidth, y + hatHeight), paint)
            }
        }
        // Draw hoarfrost after the header's opaque gradient and photograph:
        // the screen background frost alone was hidden by this child view.
        drawHeaderPerimeterFrost(canvas, w, h)
        }
    }

    private fun drawCableJoin(canvas: Canvas, w: Float, h: Float) {
        val geometry = cableGeometry(w, h)
        val d = density
        val photoScale = w * PHOTO_WIDTH_FRACTION / ornaments.width
        val fromX = geometry.seamX - d * 2.8f
        val toX = geometry.seamX + photoScale * 6.0f
        val targetY = geometry.seamY + photoScale * .35f

        // This is ONLY the sub-centimeter join to the retinted original
        // photographic wire. Never paint a second cable along the photograph.
        val continuous = Path().apply {
            val points = 12
            for (i in 0..points) {
                val x = fromX + (geometry.seamX - fromX) * i / points
                val y = geometry.yAtX(x)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            cubicTo(geometry.seamX + photoScale * 1.9f, geometry.seamY,
                geometry.seamX + photoScale * 4.0f, targetY,
                toX, targetY)
        }
        val wire = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val stops = floatArrayOf(0f, .28f, .76f, 1f)
        wire.strokeWidth = d * 1.7f
        wire.shader = LinearGradient(fromX, geometry.seamY, toX, targetY,
            intArrayOf(Color.TRANSPARENT, Color.argb(205, 76, 58, 36),
                Color.argb(186, 77, 60, 37), Color.TRANSPARENT),
            stops, Shader.TileMode.CLAMP)
        canvas.drawPath(continuous, wire)
        wire.strokeWidth = d * .85f
        wire.shader = LinearGradient(fromX, geometry.seamY, toX, targetY,
            intArrayOf(Color.TRANSPARENT, Color.argb(245, 219, 174, 99),
                Color.argb(218, 225, 187, 115), Color.TRANSPARENT),
            stops, Shader.TileMode.CLAMP)
        canvas.drawPath(continuous, wire)
        wire.shader = null
    }

    private fun drawPhotographicLightHalos(
        canvas: Canvas, left: Float, top: Float, width: Float, height: Float
    ) {
        // Original 368x206 approved_ornaments.webp has these six actual bulbs.
        // Their glass and filament stay intact: this adds ambient light only.
        val lights = arrayOf(
            31f to 154f, 82f to 144f, 106f to 122f,
            194f to 67f, 277f to 62f, 354f to 63f
        )
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        lights.forEachIndexed { index, (px, py) ->
            val x = left + width * px / ornaments.width
            val y = top + height * py / ornaments.height
            val radius = density * (if (index < 3) 12.5f else 10.0f)
            val strength = if (index < 3) 67 else 42
            glow.shader = RadialGradient(x, y, radius,
                intArrayOf(
                    Color.argb(strength, 255, 244, 191),
                    Color.argb(strength / 2, 255, 176, 42),
                    Color.argb(9, 251, 119, 24),
                    Color.TRANSPARENT
                ), floatArrayOf(0f, .22f, .61f, 1f),
                Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, radius, glow)
            // The first photographed bulbs were noticeably cold/gray next
            // to the hand-rendered amber glass. Warm their existing glass
            // interior only; preserve the photographed socket and silhouette.
            if (index < 3) {
                val glassRadius = density * (if (index == 0) 3.5f else 3.0f)
                glow.shader = RadialGradient(x, y, glassRadius,
                    intArrayOf(
                        Color.argb(145, 255, 250, 207),
                        Color.argb(112, 255, 208, 102),
                        Color.TRANSPARENT
                    ), floatArrayOf(0f, .38f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(x, y, glassRadius, glow)
            }
        }
        glow.shader = null
    }

    private fun drawHeaderPerimeterFrost(canvas: Canvas, w: Float, h: Float) {
        val d = density
        canvas.withSave {
            // The header covers the underlying screen's top and side frost.
            // Retain only a narrow 3-sided perimeter, not a divider at the
            // bottom of the header or a film over interactive content.
            val allowed = Path().apply {
                addRect(0f, 0f, w, (29f * d).coerceAtMost(h),
                    Path.Direction.CW)
                addRect(0f, 0f, (17f * d).coerceAtMost(w), h,
                    Path.Direction.CW)
                addRect((w - 17f * d).coerceAtLeast(0f), 0f, w, h,
                    Path.Direction.CW)
            }
            canvas.clipPath(allowed)
            WinterNaturalFrost.render(canvas, w, h, d, darkMode,
                WinterSurface.SCREEN, opacity, 0x6e1e1234L)
            // An unbroken fine crystalline rim joins the feathery colonies.
            // Keep its width below 1dp so it never resembles another garland.
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = d * .55f
                strokeCap = Paint.Cap.ROUND
                color = if (darkMode) Color.argb(opacity * 158 / 255, 217, 248, 252)
                    else Color.argb(opacity * 170 / 255, 171, 237, 249)
            }
            val ridge = Path()
            for (i in 0..100) {
                val x = w * i / 100f
                val y = d * (.9f + .22f *
                    kotlin.math.sin(i * 1.33).toFloat())
                if (i == 0) ridge.moveTo(x, y) else ridge.lineTo(x, y)
            }
            canvas.drawPath(ridge, line)

            // Sparse fronds alone left the middle of the top edge bare.
            // Tiny uneven crystalline stems knit the continuous rim into
            // the organic corners without forming a repeated stamped motif.
            val growths = (w / (23f * d)).toInt().coerceAtLeast(5)
            line.strokeWidth = d * .34f
            for (i in 0 until growths) {
                val wave = kotlin.math.sin(i * 2.41).toFloat()
                val x = w * (i + .50f + wave * .17f) / growths
                val length = d * (4.0f + (i * 17 % 11) * .48f)
                val drift = kotlin.math.sin(i * 1.71).toFloat() * length * .30f
                val xEnd = x + drift
                val startY = d * 1.1f
                canvas.drawLine(x, startY, xEnd, length, line)
                for (branch in 1..3) {
                    val t = branch / 4f
                    val bx = x + drift * t
                    val by = startY + (length - startY) * t
                    val barb = length * (.15f + (i + branch) % 3 * .04f)
                    canvas.drawLine(bx, by,
                        bx + barb, by - barb * .56f, line)
                    canvas.drawLine(bx, by,
                        bx - barb * .85f, by - barb * .49f, line)
                }
            }
        }
    }

    /**
     * Independent, smoothly sagging light cord. The old approved_garland.webp
     * contained a flat rectangular highlight that survived every RC6–RC8 resize.
     * Draw directly at screen resolution, with snowy cable, glass, warm filaments,
     * and soft radial halos; keep the ornament photo as a separate top layer.
     */
    private fun drawCurvedGarland(canvas: Canvas, w: Float, h: Float) {
        val d = density
        val geometry = cableGeometry(w, h)
        // All bulb sockets are placed on the same physical continuous cable
        // that leads to the left-most wire in the photograph.

        val wire = Path()
        val segments = 160
        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val x = geometry.xAt(t)
            val y = geometry.yAt(t)
            if (i == 0) wire.moveTo(x, y) else wire.lineTo(x, y)
        }

        val art = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        art.color = Color.argb(185, 36, 34, 23)
        art.strokeWidth = d * 1.65f
        canvas.drawPath(wire, art)
        art.color = Color.rgb(181, 136, 75)
        art.strokeWidth = d * .76f
        canvas.drawPath(wire, art)

        // Irregular patches of snow, without the old horizontal white strip.
        art.strokeWidth = d * .73f
        for (i in 0..100) {
            if (i % 7 == 2 || i % 9 == 5 || i % 13 == 4) {
                val t = (i + .35f) / 101f
                val x = geometry.xAt(t)
                val y = geometry.yAt(t) - d * .65f
                art.color = Color.argb(205, 226, 244, 242)
                canvas.drawLine(x - d * 1.45f, y,
                    x + d * .85f, y - d * .13f, art)
            }
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 10) {
            val t = (i + .48f) / 10f
            val x = geometry.xAt(t)
            val wireY = geometry.yAt(t)
            val radius = d * (2.75f + (i % 3) * .25f)
            val centerY = wireY + radius * 1.65f
            fill.style = Paint.Style.FILL
            // Wide scattered glow plus a tighter amber halo. Neither is an
            // opaque disk: the native text/background remain fully visible.
            val ambientRadius = radius * 7.2f
            fill.shader = RadialGradient(x, centerY, ambientRadius,
                intArrayOf(
                    Color.argb(64, 255, 210, 100),
                    Color.argb(38, 255, 166, 38),
                    Color.argb(11, 242, 117, 20),
                    Color.TRANSPARENT
                ), floatArrayOf(0f, .20f, .54f, 1f),
                Shader.TileMode.CLAMP)
            canvas.drawCircle(x, centerY, ambientRadius, fill)
            val innerRadius = radius * 3.45f
            fill.shader = RadialGradient(x, centerY, innerRadius,
                intArrayOf(
                    Color.argb(162, 255, 250, 197),
                    Color.argb(119, 255, 193, 67),
                    Color.argb(45, 255, 135, 27),
                    Color.TRANSPARENT
                ), floatArrayOf(0f, .16f, .52f, 1f),
                Shader.TileMode.CLAMP)
            canvas.drawCircle(x, centerY, innerRadius, fill)
            fill.shader = null

            // Dark-metal socket plus the tiny suspension strand.
            art.strokeWidth = d * .55f
            art.color = Color.argb(230, 104, 76, 37)
            canvas.drawLine(x, wireY, x, centerY - radius * .95f, art)
            val socket = RectF(x - radius * .40f,
                centerY - radius * 1.10f, x + radius * .40f,
                centerY - radius * .65f)
            fill.color = Color.rgb(169, 140, 81)
            canvas.drawRoundRect(socket, d * .35f, d * .35f, fill)

            // Translucent pear-shaped glass and a warm bright inner filament.
            val glass = Path().apply {
                moveTo(x, centerY - radius)
                cubicTo(x + radius * .85f, centerY - radius * .95f,
                    x + radius * 1.10f, centerY - radius * .05f,
                    x + radius * .76f, centerY + radius * .63f)
                cubicTo(x + radius * .43f, centerY + radius * 1.06f,
                    x - radius * .43f, centerY + radius * 1.06f,
                    x - radius * .76f, centerY + radius * .63f)
                cubicTo(x - radius * 1.10f, centerY - radius * .05f,
                    x - radius * .85f, centerY - radius * .95f,
                    x, centerY - radius)
                close()
            }
            fill.shader = RadialGradient(x - radius * .23f,
                centerY - radius * .32f, radius * 1.45f,
                intArrayOf(Color.rgb(255, 255, 232),
                    Color.rgb(255, 226, 121),
                    Color.rgb(247, 169, 49), Color.argb(210, 164, 84, 19)),
                floatArrayOf(0f, .23f, .64f, 1f), Shader.TileMode.CLAMP)
            canvas.drawPath(glass, fill)
            fill.shader = null

            art.color = Color.argb(215, 255, 225, 128)
            art.strokeWidth = d * .44f
            canvas.drawPath(glass, art)
            // Individually visible hot filament suspended inside the glass.
            art.color = Color.argb(236, 255, 247, 204)
            art.strokeWidth = d * .32f
            canvas.drawLine(x - radius * .27f, centerY + radius * .16f,
                x - radius * .15f, centerY - radius * .14f, art)
            canvas.drawLine(x - radius * .15f, centerY - radius * .14f,
                x + radius * .16f, centerY + radius * .12f, art)
            canvas.drawLine(x + radius * .16f, centerY + radius * .12f,
                x + radius * .26f, centerY - radius * .15f, art)
            fill.color = Color.argb(228, 255, 255, 243)
            canvas.drawOval(RectF(x - radius * .52f,
                centerY - radius * .66f, x - radius * .24f,
                centerY - radius * .19f), fill)
            // Fine reflection along the upper right of the pear-shaped glass.
            art.color = Color.argb(155, 255, 250, 223)
            art.strokeWidth = d * .23f
            canvas.drawLine(x + radius * .38f, centerY - radius * .56f,
                x + radius * .56f, centerY - radius * .31f, art)
        }
    }

    override fun setAlpha(alpha: Int) {
        opacity = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
