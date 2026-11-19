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
internal class BalanceHubHeaderDrawable(
    private val isDark: Boolean, private val winter: Boolean = false
) : Drawable() {
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

        val edge = if (winter) WinterWaveGeometry.edge(width, height) else Path().apply {
            moveTo(0f, height * .90f)
            cubicTo(width * .20f, height * .74f,
                width * .35f, height * 1.02f, width * .53f, height * .88f)
            cubicTo(width * .75f, height * .68f,
                width * .88f, height * .80f, width, height * .93f)
        }
        val fill = Path(edge).apply {
            lineTo(width, 0f)
            lineTo(0f, 0f)
            close()
        }
        val colors = if (winter) {
            if (isDark) intArrayOf(
                Color.rgb(9, 50, 70), Color.rgb(5, 77, 94),
                Color.rgb(2, 55, 75)
            ) else intArrayOf(
                Color.rgb(16, 81, 102), Color.rgb(12, 108, 125),
                Color.rgb(8, 70, 92)
            )
        } else if (isDark) intArrayOf(
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

        // Snow rests on the SAME bezier curve used by the light cable.
        // The translucent halo is outside the white core, just like the reference.
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        if (winter) {
            paint.color = Color.rgb(42, 153, 173)
            paint.alpha = opacity * 95 / 255
            paint.strokeWidth = 6.0f
            canvas.drawPath(edge, paint)
            paint.color = Color.rgb(193, 246, 252)
            paint.alpha = opacity * 220 / 255
            paint.strokeWidth = 3.6f
            canvas.drawPath(edge, paint)
            paint.color = Color.WHITE
            paint.alpha = opacity
            paint.strokeWidth = 1.8f
            canvas.drawPath(edge, paint)
        } else {
            paint.color = if (isDark) Color.rgb(39, 177, 174)
                else Color.rgb(26, 151, 149)
            paint.alpha = opacity * 155 / 255
            paint.strokeWidth = (height * .016f).coerceIn(1.2f, 2.8f)
            canvas.drawPath(edge, paint)
        }
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
