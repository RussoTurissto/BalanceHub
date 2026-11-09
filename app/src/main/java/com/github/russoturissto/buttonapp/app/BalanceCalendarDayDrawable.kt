package com.github.russoturissto.buttonapp.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Unified calendar language: teal payment, muted blue event, diagonal combination. */
@SuppressLint("UseKtx")
internal class BalanceCalendarDayDrawable(
    private val context: Context,
    private val night: Boolean,
    private val payment: Boolean,
    private val event: Boolean,
    private val selected: Boolean,
    private val today: Boolean
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = context.resources.displayMetrics.density
    private val radius get() = 10f * d
    private val paymentColor get() = if (night) Color.rgb(25, 112, 108)
        else Color.rgb(207, 239, 230)
    private val eventColor get() = if (night) Color.rgb(63, 101, 123)
        else Color.rgb(217, 231, 243)

    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)
        val round = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(round)
        paint.style = Paint.Style.FILL
        paint.color = when {
            event -> eventColor
            payment -> paymentColor
            selected -> if (night) Color.rgb(38, 78, 81) else Color.rgb(226, 248, 242)
            else -> Color.TRANSPARENT
        }
        canvas.drawRect(rect, paint)
        if (payment && event) {
            paint.color = paymentColor
            canvas.drawPath(Path().apply {
                moveTo(rect.left, rect.top)
                lineTo(rect.right, rect.top)
                lineTo(rect.left, rect.bottom)
                close()
            }, paint)
            paint.color = if (night) Color.argb(150, 176, 226, 224)
                else Color.argb(190, 140, 186, 188)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = d
            canvas.drawLine(rect.right, rect.top, rect.left, rect.bottom, paint)
        }
        canvas.restore()
        if (today || selected) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (if (today) 2f else 1.5f) * d
            paint.color = if (today) Color.rgb(45, 221, 212)
                else if (night) Color.rgb(166, 236, 211)
                else Color.rgb(41, 148, 141)
            canvas.drawRoundRect(RectF(rect.left + d, rect.top + d, rect.right - d,
                rect.bottom - d), radius, radius, paint)
        }
        paint.style = Paint.Style.FILL
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
