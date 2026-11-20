package com.github.russoturissto.buttonapp.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
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
 * No procedural approximations of the fir, glass baubles, snow, bow or garland.
 * These are three offline WebP layers (ornaments, lights, Santa hat).
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
    private val garland: Bitmap,
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

        // Warm lamps and uneven snow lie BELOW the wordmark.
        canvas.withSave {
            if (wordmark.width > 0 && wordmark.height > 0) {
                val top = wordmark.top + wordmark.translationY
                val left = wordmark.left - density * 2.5f
                val textWidth = wordmark.paint.measureText("BalanceHub") +
                    wordmark.letterSpacing * wordmark.textSize * 9f
                // TextView is MATCH_PARENT; protect only the rendered letters.
                val right = (wordmark.left + textWidth + density * 2.5f)
                    .coerceAtMost(w)
                val bottom = top + wordmark.height + density * 2.5f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    canvas.clipOutRect(left, top - density * 1.5f, right, bottom)
                } else {
                    // Android 6–7 fallback for the app's minSdk 23.
                    @Suppress("DEPRECATION")
                    canvas.clipRect(left, top - density * 1.5f, right, bottom,
                        Region.Op.DIFFERENCE)
                }
            }
            // Never stretch the approved 860×134 garland independently in Y.
            // Anchor its bulbs near the original bottom edge while preserving
            // the bitmap's natural aspect ratio on every screen width.
            val garlandWidth = w * .87f
            val garlandHeight = garlandWidth * garland.height / garland.width
            val garlandBottom = h * 1.01f
            canvas.drawBitmap(garland, null,
                RectF(0f, garlandBottom - garlandHeight,
                    garlandWidth, garlandBottom), paint)
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
            val x = startH - hatWidth * .43f
            val y = textTop - hatHeight * .30f
            canvas.drawBitmap(santaHat, null,
                RectF(x, y, x + hatWidth, y + hatHeight), paint)
        }
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
