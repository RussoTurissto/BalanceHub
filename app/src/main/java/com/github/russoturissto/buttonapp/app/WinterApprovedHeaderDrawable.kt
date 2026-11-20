package com.github.russoturissto.buttonapp.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
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
    private val santaHat: Bitmap
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var opacity = 255

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 1f || h <= 1f) return

        canvas.withSave {
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            // Keep approved artwork at its natural scale, including minor overhang.
            // The title mask below still keeps the lamps off the native TextView.
            paint.alpha = opacity

        // The wire runs behind the real native title, never through its letters.
        canvas.withSave {
            if (wordmark.width > 0 && wordmark.height > 0) {
                val textTop = wordmark.top + wordmark.translationY
                val textWidth = wordmark.paint.measureText("BalanceHub") +
                    wordmark.letterSpacing * wordmark.textSize * 9f
                val right = (wordmark.left + textWidth + density * 2.5f)
                    .coerceAtMost(w)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    canvas.clipOutRect(wordmark.left - density * 2.5f,
                        textTop - density * 1.5f, right,
                        textTop + wordmark.height + density * 2.5f)
                } else {
                    @Suppress("DEPRECATION")
                    canvas.clipRect(wordmark.left - density * 2.5f,
                        textTop - density * 1.5f, right,
                        textTop + wordmark.height + density * 2.5f,
                        Region.Op.DIFFERENCE)
                }
            }
            drawCurvedGarland(canvas, w, h)
        }

        // Approved snow-covered spruce, pinecone, bow, glass baubles and star.
        // Preserve the artwork's aspect ratio across Android screen widths.
        val rightWidth = w * .45f
        val rightHeight = rightWidth * ornaments.height / ornaments.width
        val ornamentTop = (h - rightHeight) * .12f
        canvas.drawBitmap(ornaments, null,
            RectF(w - rightWidth, ornamentTop,
                w, ornamentTop + rightHeight), paint)

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
        val startX = -w * .012f
        val endX = w * .75f
        val startY = if (wordmark.height > 0) {
            wordmark.top + wordmark.translationY +
                wordmark.height - d * 4.5f
        } else h * .62f
        val endY = (startY - h * .21f).coerceAtLeast(3f * d)
        val sag = h * .30f

        fun yAt(t: Float): Float {
            val smoothDrop = sag * 4f * t * (1f - t)
            val smallIrregularity = d * .36f *
                kotlin.math.sin((t * 6.5f + .15f).toDouble()).toFloat()
            return startY + (endY - startY) * t + smoothDrop +
                smallIrregularity
        }

        val wire = Path()
        val segments = 160
        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val x = startX + (endX - startX) * t
            val y = yAt(t)
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
                val x = startX + (endX - startX) * t
                val y = yAt(t) - d * .65f
                art.color = Color.argb(205, 226, 244, 242)
                canvas.drawLine(x - d * 1.45f, y,
                    x + d * .85f, y - d * .13f, art)
            }
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 10) {
            val t = (i + .48f) / 10f
            val x = startX + (endX - startX) * t
            val wireY = yAt(t)
            val radius = d * (2.75f + (i % 3) * .25f)
            val centerY = wireY + radius * 1.65f
            val glowRadius = radius * 3.65f
            fill.style = Paint.Style.FILL
            fill.shader = RadialGradient(x, centerY, glowRadius,
                intArrayOf(Color.argb(195, 255, 242, 179),
                    Color.argb(117, 255, 184, 45),
                    Color.argb(35, 252, 134, 26), Color.TRANSPARENT),
                floatArrayOf(0f, .20f, .53f, 1f),
                Shader.TileMode.CLAMP)
            canvas.drawCircle(x, centerY, glowRadius, fill)
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
            fill.color = Color.argb(230, 255, 255, 243)
            canvas.drawOval(RectF(x - radius * .43f,
                centerY - radius * .64f, x - radius * .13f,
                centerY - radius * .24f), fill)
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
