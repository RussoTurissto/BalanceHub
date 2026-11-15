package com.github.russoturissto.buttonapp.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation

/**
 * Offline adaptation of the user-selected gradient Sber logo (variant C):
 * an open blue/green/yellow ring and green check, without a white tile.
 * The same scalable drawable is used on every Sber screen and in both themes.
 */
internal class SberBrandLogoDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private var alphaValue = 255
    private var tintFilter: ColorFilter? = null
    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
            withScale(bounds.width() / 100f, bounds.height() / 100f) {
                val ring = RectF(17f, 17f, 83f, 83f)
        paint.reset()
        paint.isAntiAlias = true
        paint.isDither = true
        paint.alpha = alphaValue
        paint.colorFilter = tintFilter
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 9f
        paint.strokeCap = Paint.Cap.ROUND
        paint.shader = SweepGradient(
            50f, 50f,
            intArrayOf(
                Color.rgb(9, 142, 228), Color.rgb(16, 182, 216),
                Color.rgb(47, 195, 104), Color.rgb(212, 225, 35),
                Color.rgb(32, 184, 80), Color.rgb(9, 142, 228)
            ),
            floatArrayOf(0f, 0.2f, 0.43f, 0.65f, 0.84f, 1f)
        )
        canvas.drawArc(ring, 35f, 302f, false, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.rgb(19, 185, 81)
        val check = Path().apply {
            moveTo(31f, 51f)
            lineTo(44f, 64f)
            lineTo(76f, 32f)
        }
        canvas.drawPath(check, paint)
            }
        }
    }
    override fun setAlpha(alpha: Int) { alphaValue = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        tintFilter = colorFilter
        invalidateSelf()
    }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
