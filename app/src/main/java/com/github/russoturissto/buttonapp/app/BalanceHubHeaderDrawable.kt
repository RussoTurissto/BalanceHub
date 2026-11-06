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

/**
 * Full-bleed teal gradient with a subtle curved wave. Drawn without changing
 * the header's measured size or the brand wordmark position.
 */
internal class BalanceHubHeaderDrawable(private val isDark: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255
    private var filter: ColorFilter? = null

    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        if (width <= 0f || height <= 0f) return
        val colors = if (isDark) intArrayOf(
            Color.rgb(66, 142, 149),
            Color.rgb(33, 125, 128),
            Color.rgb(12, 88, 91)
        ) else intArrayOf(
            Color.rgb(93, 179, 178),
            Color.rgb(62, 151, 156),
            Color.rgb(23, 123, 122)
        )
        paint.shader = LinearGradient(
            0f, 0f, width, height * .75f,
            colors, null, Shader.TileMode.CLAMP
        )
        paint.colorFilter = filter
        paint.alpha = opacity
        canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat(), paint)
        paint.shader = null

        // A low-contrast curved ribbon like the selected gradient mockup.
        val ribbon = Path().apply {
            moveTo(0f, height * .71f)
            cubicTo(width * .23f, height * .64f,
                width * .34f, height * .97f, width * .60f, height * .77f)
            cubicTo(width * .79f, height * .61f,
                width * .89f, height * .67f, width, height * .78f)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        paint.color = if (isDark) Color.rgb(0, 67, 76) else Color.rgb(16, 112, 110)
        paint.alpha = opacity * 70 / 255
        canvas.drawPath(ribbon, paint)
        paint.alpha = opacity
        paint.colorFilter = filter
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
