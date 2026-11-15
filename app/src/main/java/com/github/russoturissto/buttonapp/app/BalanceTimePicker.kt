package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** BalanceHub-native, fully themed 24-hour radial time picker with manual input. */
internal object BalanceTimePicker {
    fun show(context: Context, hour: Int, minute: Int, onTime: (Int, Int) -> Unit) {
        val night = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val ink = if (night) Color.rgb(239, 249, 247) else Color.rgb(30, 57, 59)
        val muted = if (night) Color.rgb(165, 189, 191) else Color.rgb(104, 129, 130)
        val accent = if (night) Color.rgb(87, 169, 167) else Color.rgb(35, 131, 131)
        val surface = if (night) Color.rgb(36, 66, 70) else Color.rgb(238, 250, 247)
        val soft = if (night) Color.rgb(39, 83, 85) else Color.rgb(218, 242, 238)
        val d = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * d + .5f).toInt()
        fun shape(color: Int, radius: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
        fun label(value: String, size: Float, bold: Boolean = false) =
            TextView(context).apply {
                text = value
                textSize = size
                setTextColor(ink)
                gravity = Gravity.CENTER
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            }

        var selectedHour = hour.coerceIn(0, 23)
        var selectedMinute = minute.coerceIn(0, 59)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(15))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(label("Выберите время", 18f, true))
        val selectors = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
        }
        val hourTab = label("%02d".format(java.util.Locale.ROOT, selectedHour), 32f, true)
        val colon = label(":", 28f)
        val minuteTab = label("%02d".format(java.util.Locale.ROOT, selectedMinute), 32f, true)
        val hourEdit = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(2))
            setText("%02d".format(java.util.Locale.ROOT, selectedHour))
            setTextColor(ink)
            textSize = 15f
            gravity = Gravity.CENTER
            background = shape(soft, 11)
            contentDescription = "Часы от 0 до 23"
        }
        val minuteEdit = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(2))
            setText("%02d".format(java.util.Locale.ROOT, selectedMinute))
            setTextColor(ink)
            textSize = 15f
            gravity = Gravity.CENTER
            background = shape(soft, 11)
            contentDescription = "Минуты от 0 до 59"
        }
        lateinit var clock: Clock
        fun refresh() {
            hourTab.text = "%02d".format(java.util.Locale.ROOT, selectedHour)
            minuteTab.text = "%02d".format(java.util.Locale.ROOT, selectedMinute)
            hourTab.setTextColor(if (clock.hoursMode) accent else muted)
            minuteTab.setTextColor(if (clock.hoursMode) muted else accent)
            hourEdit.setText("%02d".format(java.util.Locale.ROOT, selectedHour))
            minuteEdit.setText("%02d".format(java.util.Locale.ROOT, selectedMinute))
            clock.hour = selectedHour
            clock.minute = selectedMinute
            clock.invalidate()
        }
        clock = Clock(context, night, selectedHour, selectedMinute) { h, m ->
            selectedHour = h
            selectedMinute = m
            refresh()
        }
        hourTab.apply {
            contentDescription = "Выбрать часы"
            isClickable = true
            setOnClickListener { clock.hoursMode = true; refresh() }
        }
        minuteTab.apply {
            contentDescription = "Выбрать минуты"
            isClickable = true
            setOnClickListener { clock.hoursMode = false; refresh() }
        }
        selectors.addView(hourTab, LinearLayout.LayoutParams(dp(70), dp(58)))
        selectors.addView(colon, LinearLayout.LayoutParams(dp(22), dp(58)))
        selectors.addView(minuteTab, LinearLayout.LayoutParams(dp(70), dp(58)))
        root.addView(selectors)
        val side = if (context.resources.configuration.screenWidthDp < 360) 238 else 262
        root.addView(clock, LinearLayout.LayoutParams(dp(side), dp(side)))
        root.addView(label("Коснитесь циферблата или введите время вручную", 11f).apply {
            setTextColor(muted)
            setPadding(0, dp(10), 0, dp(6))
        })
        val manual = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        manual.addView(label("Часы", 12f), LinearLayout.LayoutParams(0, dp(41), 1f))
        manual.addView(hourEdit, LinearLayout.LayoutParams(dp(60), dp(41)))
        manual.addView(label("Минуты", 12f), LinearLayout.LayoutParams(0, dp(41), 1f))
        manual.addView(minuteEdit, LinearLayout.LayoutParams(dp(60), dp(41)))
        root.addView(manual)
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(13), 0, 0)
        }
        val dialog = AlertDialog.Builder(context,
            if (night) R.style.BalanceHubDialogDark else R.style.BalanceHubDialogLight)
            .setView(root).create()
        val cancel = label("Отмена", 14f, true).apply {
            isClickable = true
            setTextColor(muted)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { dialog.dismiss() }
        }
        val confirm = label("ОК", 14f, true).apply {
            isClickable = true
            setTextColor(accent)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                val h = hourEdit.text.toString().toIntOrNull()
                val m = minuteEdit.text.toString().toIntOrNull()
                if (h == null || h !in 0..23 || m == null || m !in 0..59) {
                    Toast.makeText(context, "Введите время: часы 0–23, минуты 0–59",
                        Toast.LENGTH_SHORT).show()
                } else {
                    onTime(h, m)
                    dialog.dismiss()
                }
            }
        }
        footer.addView(cancel, LinearLayout.LayoutParams(dp(100), dp(44)))
        footer.addView(confirm, LinearLayout.LayoutParams(dp(74), dp(44)))
        root.addView(footer)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (night) intArrayOf(Color.rgb(36, 66, 70), Color.rgb(26, 46, 54))
                else intArrayOf(Color.WHITE, Color.rgb(233, 247, 243))
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), if (night) Color.rgb(72, 132, 127)
                    else Color.rgb(172, 217, 208))
            })
        }
        refresh()
        dialog.show()
    }

    private class Clock(context: Context, private val night: Boolean,
        var hour: Int, var minute: Int, private val changed: (Int, Int) -> Unit
    ) : View(context) {
        var hoursMode = true
            set(value) { field = value; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val accent = if (night) Color.rgb(71, 178, 167) else Color.rgb(39, 146, 141)
        private val dialColor = if (night) Color.rgb(42, 80, 84) else Color.rgb(222, 243, 238)
        private val textColor = if (night) Color.rgb(239, 249, 247) else Color.rgb(35, 71, 75)
        init {
            contentDescription = "Часы. Два кольца для 24-часового времени"
            isClickable = true
            isFocusable = true
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * .49f
            paint.color = dialColor
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, radius, paint)
            val outerRadius = radius * .79f
            val innerRadius = radius * .54f
            val index = if (hoursMode) hour % 12 else minute / 5f
            val angle = index.toDouble() * PI / 6.0
            val selectedR = if (hoursMode && (hour == 0 || hour >= 13)) innerRadius
                else outerRadius
            val dotX = cx + selectedR * sin(angle).toFloat()
            val dotY = cy - selectedR * cos(angle).toFloat()
            paint.color = accent
            paint.strokeWidth = radius * .025f
            canvas.drawLine(cx, cy, dotX, dotY, paint)
            canvas.drawCircle(dotX, dotY, radius * .15f, paint)
            canvas.drawCircle(cx, cy, radius * .025f, paint)
            paint.typeface = android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            for (i in 0..11) {
                val a = i.toDouble() * PI / 6.0
                val x = cx + outerRadius * sin(a).toFloat()
                val y = cy - outerRadius * cos(a).toFloat()
                val number = if (hoursMode) {
                    if (i == 0) 12 else i
                } else (i * 5)
                paint.textSize = radius * .19f
                paint.color = if (hoursMode && hour in 1..12 && hour == number ||
                    !hoursMode && minute == number) Color.WHITE else textColor
                canvas.drawText(number.toString().padStart(if (hoursMode) 1 else 2, '0'),
                    x, y - (paint.ascent() + paint.descent()) / 2, paint)
                if (hoursMode) {
                    val innerNumber = if (i == 0) 0 else i + 12
                    val ix = cx + innerRadius * sin(a).toFloat()
                    val iy = cy - innerRadius * cos(a).toFloat()
                    paint.textSize = radius * .14f
                    paint.color = if (hour == innerNumber) Color.WHITE else textColor
                    canvas.drawText(innerNumber.toString(),
                        ix, iy - (paint.ascent() + paint.descent()) / 2, paint)
                }
            }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                    val cx = width / 2f
                    val cy = height / 2f
                    val distance = kotlin.math.hypot(event.x - cx, event.y - cy)
                    if (distance > 0.49f * min(width, height) || distance < 5f) return true
                    val angle = (atan2(event.x - cx, cy - event.y) + 2 * PI) % (2 * PI)
                    if (hoursMode) {
                        val index = ((angle / (2 * PI) * 12).roundToInt() % 12)
                        val inner = distance < min(width, height) * .32f
                        val result = if (inner) (if (index == 0) 0 else index + 12)
                            else (if (index == 0) 12 else index)
                        changed(result, minute)
                        if (event.actionMasked == MotionEvent.ACTION_UP) hoursMode = false
                    } else {
                        val result = ((angle / (2 * PI) * 60).roundToInt() % 60)
                        changed(hour, result)
                    }
                    return true
                }
                else -> return super.onTouchEvent(event)
            }
        }
        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
