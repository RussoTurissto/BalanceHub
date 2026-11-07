package com.github.russoturissto.buttonapp.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation

/**
 * Scalable, offline operator pictograms for every registry entry.
 * Never relies on remote logos or SIM-provided bitmaps. Unknown providers get
 * an explicit SIM-card drawing. Graphic colors are independent of app theme.
 */
internal class OperatorBrandLogoDrawable(
    operator: RussianMobileOperator?
) : Drawable() {
    private val mark = OperatorBrandMarks.fromOperator(operator)
    private val operatorColor = operator?.colorHex?.toColorInt()
        ?: Color.rgb(21, 91, 91)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255
    private var filter: ColorFilter? = null
    private val white = Color.WHITE
    private val dark = Color.rgb(22, 29, 30)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        canvas.withTranslation(b.left.toFloat(), b.top.toFloat()) {
            canvas.withScale(b.width()/100f, b.height()/100f) {
                val bg = when (mark) {
                    OperatorBrandMark.T2 -> Color.rgb(8, 11, 14)
                    OperatorBrandMark.BEELINE, OperatorBrandMark.TMOBILE,
                    OperatorBrandMark.TINKOFF -> Color.rgb(255, 211, 28)
                    else -> operatorColor
                }
                round(canvas, bg, 0f, 0f, 100f, 100f, 20f)
                when(mark) {
                    OperatorBrandMark.T2 -> {
                        // T2's post-2024 black/white/fuchsia visual signature.
                        rect(canvas, Color.rgb(252, 35, 148), 9f, 38f, 22f, 54f)
                        word(canvas, "t2", 30f, 73f, 61f, white, true)
                    }
                    OperatorBrandMark.MTS -> {
                        // Three white letters around the red 2023 mark.
                        word(canvas, "М", 10f, 41f, 35f, white, true)
                        word(canvas, "Т", 61f, 42f, 34f, white, true)
                        word(canvas, "С", 60f, 83f, 36f, white, true)
                    }
                    OperatorBrandMark.MEGAFON -> {
                        circle(canvas, white, 50f, 50f, 33f)
                        circle(canvas, Color.rgb(110, 54, 166), 49f, 50f, 25f)
                        circle(canvas, white, 61f, 37f, 8f)
                        circle(canvas, white, 37f, 64f, 7f)
                    }
                    OperatorBrandMark.BEELINE -> {
                        circle(canvas, dark, 50f, 50f, 34f)
                        for (i in 0..2) {
                            round(canvas, bg, 27f, 29f+i*18f,
                                73f, 37f+i*18f, 3f)
                        }
                    }
                    OperatorBrandMark.YOTA -> {
                        circle(canvas, white, 50f, 23f, 10f)
                        line(canvas, white, 50f, 37f, 50f, 80f, 13f)
                        line(canvas, white, 50f, 52f, 28f, 36f, 12f)
                        line(canvas, white, 50f, 52f, 72f, 36f, 12f)
                    }
                    OperatorBrandMark.SBER -> {
                        arc(canvas, white, 17f, 17f, 83f, 83f, 180f, 290f, 8f)
                        line(canvas, white, 29f, 53f, 43f, 66f, 9f)
                        line(canvas, white, 43f, 66f, 75f, 34f, 9f)
                    }
                    OperatorBrandMark.TMOBILE, OperatorBrandMark.TINKOFF -> {
                        val shield = Path().apply {
                            moveTo(50f, 13f); lineTo(78f, 24f)
                            lineTo(75f, 60f); quadTo(67f, 78f, 50f, 89f)
                            quadTo(33f, 78f, 25f, 60f); lineTo(22f, 24f); close()
                        }
                        shape(canvas, dark, shield)
                        center(canvas, "Т", 68f, 43f, white)
                    }
                    OperatorBrandMark.MIRANDA -> {
                        arc(canvas, white, 18f, 18f, 82f, 82f, 208f, 124f, 8f)
                        arc(canvas, white, 31f, 37f, 69f, 77f, 212f, 116f, 7f)
                        circle(canvas, white, 50f, 75f, 6f)
                    }
                    OperatorBrandMark.MKS -> {
                        center(canvas, "МКС", 55f, 28f, white)
                        wave(canvas, white, 22f, 72f, 18f, 7f)
                    }
                    OperatorBrandMark.MOTIV -> {
                        line(canvas, white, 17f, 75f, 35f, 26f, 10f)
                        line(canvas, white, 35f, 26f, 50f, 58f, 10f)
                        line(canvas, white, 50f, 58f, 65f, 26f, 10f)
                        line(canvas, white, 65f, 26f, 83f, 75f, 10f)
                    }
                    OperatorBrandMark.LETAI -> {
                        val wing = Path().apply {
                            moveTo(19f, 76f)
                            cubicTo(35f, 32f, 61f, 20f, 83f, 27f)
                            cubicTo(65f, 50f, 43f, 66f, 19f, 76f); close()
                        }
                        shape(canvas, white, wing)
                    }
                    OperatorBrandMark.ROSTELECOM -> {
                        arc(canvas, white, 17f, 16f, 81f, 82f, 45f, 295f, 12f)
                        line(canvas, white, 51f, 50f, 78f, 83f, 10f)
                    }
                    OperatorBrandMark.VOLNA -> {
                        wave(canvas, white, 18f, 42f, 21f, 11f)
                        wave(canvas, white, 18f, 65f, 21f, 11f)
                    }
                    OperatorBrandMark.WIN -> {
                        line(canvas, white, 16f, 27f, 29f, 76f, 9f)
                        line(canvas, white, 29f, 76f, 50f, 44f, 9f)
                        line(canvas, white, 50f, 44f, 71f, 76f, 9f)
                        line(canvas, white, 71f, 76f, 84f, 27f, 9f)
                    }
                    OperatorBrandMark.SEV -> {
                        val sail = Path().apply {
                            moveTo(52f, 15f); lineTo(52f, 69f)
                            lineTo(20f, 69f); close()
                        }
                        shape(canvas, white, sail)
                        wave(canvas, white, 20f, 78f, 19f, 7f)
                    }
                    OperatorBrandMark.PHOENIX -> {
                        val flame = Path().apply {
                            moveTo(50f, 10f)
                            cubicTo(85f, 48f, 75f, 79f, 49f, 87f)
                            cubicTo(22f, 85f, 18f, 62f, 38f, 39f)
                            cubicTo(32f, 59f, 48f, 57f, 50f, 10f); close()
                        }
                        shape(canvas, white, flame)
                    }
                    OperatorBrandMark.GPB -> {
                        center(canvas, "ГПБ", 57f, 26f, white)
                        line(canvas, white, 25f, 72f, 75f, 72f, 7f)
                    }
                    OperatorBrandMark.VTB -> {
                        for (i in 0..2) line(canvas, white,
                            18f+i*5f, 28f+i*15f, 82f-i*5f, 28f+i*15f, 8f)
                    }
                    OperatorBrandMark.GAZPROM -> {
                        center(canvas, "Г", 74f, 67f, white)
                        circle(canvas, white, 49f, 20f, 8f)
                    }
                    OperatorBrandMark.ALFA -> {
                        line(canvas, white, 24f, 77f, 50f, 19f, 10f)
                        line(canvas, white, 50f, 19f, 76f, 77f, 10f)
                        line(canvas, white, 36f, 61f, 64f, 61f, 8f)
                    }
                    OperatorBrandMark.MCN -> {
                        circle(canvas, white, 27f, 25f, 10f)
                        circle(canvas, white, 73f, 25f, 10f)
                        circle(canvas, white, 50f, 75f, 11f)
                        line(canvas, white, 32f, 33f, 46f, 64f, 5f)
                        line(canvas, white, 68f, 33f, 54f, 64f, 5f)
                    }
                    OperatorBrandMark.DANYCOM -> {
                        arc(canvas, white, 19f, 19f, 81f, 81f, -90f, 280f, 11f)
                        circle(canvas, white, 77f, 70f, 6f)
                    }
                    OperatorBrandMark.OSNOVA -> {
                        circle(canvas, white, 50f, 50f, 31f)
                        circle(canvas, bg, 50f, 50f, 21f)
                        circle(canvas, white, 50f, 50f, 9f)
                    }
                    OperatorBrandMark.UNKNOWN -> {
                        // Every unidentified operator still has a proper SIM icon.
                        round(canvas, white, 28f, 16f, 72f, 84f, 7f)
                        round(canvas, bg, 36f, 28f, 64f, 61f, 3f)
                        line(canvas, bg, 40f, 70f, 60f, 70f, 5f)
                    }
                }
            }
        }
    }

    private fun paint(c: Int, style: Paint.Style = Paint.Style.FILL, stroke: Float = 1f) {
        p.color = c
        p.alpha = opacity
        p.colorFilter = filter
        p.style = style
        p.strokeWidth = stroke
        p.strokeCap = Paint.Cap.ROUND
    }
    private fun rect(ca: Canvas, c: Int, x: Float, y: Float, r: Float, b: Float) {
        paint(c); ca.drawRect(x,y,r,b,p)
    }
    private fun round(ca: Canvas, c: Int, x: Float, y: Float, r: Float, b: Float, radius: Float) {
        paint(c); ca.drawRoundRect(x,y,r,b,radius,radius,p)
    }
    private fun circle(ca: Canvas, c: Int, x: Float, y: Float, radius: Float) {
        paint(c); ca.drawCircle(x,y,radius,p)
    }
    private fun line(ca: Canvas, c: Int, x: Float, y: Float, ex: Float, ey: Float, width: Float) {
        paint(c,Paint.Style.STROKE,width); ca.drawLine(x,y,ex,ey,p)
    }
    private fun arc(ca: Canvas, c: Int, x: Float, y: Float, r: Float, b: Float,
                    start: Float, sweep: Float, width: Float) {
        paint(c,Paint.Style.STROKE,width)
        ca.drawArc(RectF(x,y,r,b),start,sweep,false,p)
    }
    private fun shape(ca: Canvas, c: Int, path: Path) {
        paint(c); ca.drawPath(path,p)
    }
    private fun word(ca: Canvas, t: String, x: Float, y: Float,
                     sz: Float, c: Int, bold: Boolean = false) {
        paint(c)
        p.typeface = Typeface.create("sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL)
        p.textSize = sz; p.textAlign = Paint.Align.LEFT
        ca.drawText(t,x,y,p)
    }
    private fun center(ca: Canvas, t: String, y: Float, sz: Float, c: Int) {
        paint(c)
        p.typeface = Typeface.create("sans-serif",Typeface.BOLD)
        p.textSize=sz; p.textAlign=Paint.Align.CENTER
        ca.drawText(t,50f,y,p)
        p.textAlign=Paint.Align.LEFT
    }
    private fun wave(ca: Canvas, c: Int, x: Float, y: Float, width: Float, stroke: Float) {
        val line = Path().apply {
            moveTo(x,y)
            cubicTo(x+width/2,y-width/2,x+width/2,y+width/2,x+width,y)
            cubicTo(x+width*1.5f,y-width/2,x+width*1.5f,y+width/2,x+width*2,y)
            cubicTo(x+width*2.5f,y-width/2,x+width*2.5f,y+width/2,x+width*3,y)
        }
        paint(c,Paint.Style.STROKE,stroke)
        ca.drawPath(line,p)
    }
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0,255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        filter=colorFilter; invalidateSelf()
    }
    @Deprecated("Deprecated in Android API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
