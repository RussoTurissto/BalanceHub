package com.github.russoturissto.buttonapp.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.withTranslation

/**
 * A real flowing lower edge, not a rectangular banner with an overlaid wave.
 * Paths scale to the drawable bounds for narrow/wide devices and both themes.
 */
internal class BalanceHubHeaderDrawable(private val isDark: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255
    private var filter: ColorFilter? = null

    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        if (width <= 0f || height <= 0f) return

        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        canvas.withTranslation(left, top) {

        val edge = Path().apply {
            moveTo(0f, height * .90f)
            cubicTo(width * .20f, height * .74f,
                width * .35f, height * 1.02f, width * .53f, height * .88f)
            cubicTo(width * .75f, height * .68f,
                width * .88f, height * .80f, width, height * .93f)
        }
        val fill = Path().apply {
            moveTo(0f, 0f)
            lineTo(width, 0f)
            lineTo(width, height * .93f)
            // Retrace the bezier edge backwards so the filled shape is
            // a single closed contour with a truly curved lower boundary.
            cubicTo(width * .88f, height * .80f,
                width * .75f, height * .68f, width * .53f, height * .88f)
            cubicTo(width * .35f, height * 1.02f,
                width * .20f, height * .74f, 0f, height * .90f)
            close()
        }
        val colors = if (isDark) intArrayOf(
            Color.rgb(66, 142, 149),
            Color.rgb(33, 125, 128),
            Color.rgb(12, 88, 91)
        ) else intArrayOf(
            Color.rgb(93, 179, 178),
            Color.rgb(62, 151, 156),
            Color.rgb(23, 123, 122)
        )
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, width, height * .80f,
            colors, null, Shader.TileMode.CLAMP
        )
        paint.alpha = opacity
        paint.colorFilter = filter
        canvas.drawPath(fill, paint)
        paint.shader = null

        // Restrained cyan highlight traces the actual fluid boundary.
        paint.color = if (isDark) Color.rgb(39, 177, 174)
            else Color.rgb(26, 151, 149)
        paint.alpha = opacity * 155 / 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (height * .016f).coerceIn(1.2f, 2.8f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(edge, paint)
        paint.style = Paint.Style.FILL
        }
    }

    override fun setAlpha(alpha: Int) {
        opacity = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        filter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
