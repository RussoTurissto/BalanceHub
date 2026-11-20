package com.github.russoturissto.buttonapp.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
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

        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.clipRect(0f, 0f, w, h)
        paint.alpha = opacity

        // Warm lamps and uneven snow lie BELOW the wordmark.
        val wireSave = canvas.save()
        if (wordmark.width > 0 && wordmark.height > 0) {
            val top = wordmark.top + wordmark.translationY
            canvas.clipOutRect(
                wordmark.left - density * 2.5f,
                top - density * 1.5f,
                wordmark.right + density * 2.5f,
                top + wordmark.height + density * 2.5f
            )
        }
        canvas.drawBitmap(garland, null,
            RectF(0f, h * .70f, w * .87f, h * 1.045f), paint)
        canvas.restoreToCount(wireSave)

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
            val x = startH - hatWidth * .23f
            val y = textTop - hatHeight * .69f
            // The lower edge of the plush brim rests on the top of the H.
            canvas.drawBitmap(santaHat, null,
                RectF(x, y, x + hatWidth, y + hatHeight), paint)
        }
        canvas.restoreToCount(save)
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
