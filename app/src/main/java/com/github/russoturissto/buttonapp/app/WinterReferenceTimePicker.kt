package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.util.Locale

/**
 * Three-line time wheel of the approved winter reference. Both columns can be
 * stepped one unit at a time; tapping the selected value allows exact numeric
 * entry for accessibility and fast changes.
 */
internal object WinterReferenceTimePicker {
    fun show(context: Context, hour: Int, minute: Int, onTime: (Int, Int) -> Unit) {
        val d = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * d + .5f).toInt()
        val ink = Color.rgb(237, 250, 254)
        val subdued = Color.rgb(168, 211, 225)
        val cyan = Color.rgb(34, 219, 221)
        var hh = hour.coerceIn(0, 23)
        var mm = minute.coerceIn(0, 59)
        fun number(value: Int): String = "%02d".format(Locale.ROOT, value)
        fun rounded(color: Int, stroke: Boolean = false): GradientDrawable =
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(13).toFloat()
                if (stroke) setStroke(dp(1), cyan)
            }
        fun caption(text: String, size: Float, strong: Boolean = false): TextView =
            TextView(context).apply {
                this.text = text
                textSize = size
                gravity = Gravity.CENTER
                setTextColor(if (strong) ink else subdued)
                includeFontPadding = false
                if (strong) setTypeface(null, android.graphics.Typeface.BOLD)
            }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(17))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(caption("Выбор времени", 17f, true),
            LinearLayout.LayoutParams(-1, dp(42)))
        val columns = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val hourView = arrayOf(
            caption("", 18f), caption("", 24f, true), caption("", 18f)
        )
        val minuteView = arrayOf(
            caption("", 18f), caption("", 24f, true), caption("", 18f)
        )
        var editorOpen = false
        lateinit var dialog: AlertDialog
        fun refresh() {
            listOf(hh - 1, hh, hh + 1).forEachIndexed { i, v ->
                hourView[i].text = number((v + 24) % 24)
            }
            listOf(mm - 1, mm, mm + 1).forEachIndexed { i, v ->
                minuteView[i].text = number((v + 60) % 60)
            }
        }
        fun manual(hourPart: Boolean) {
            if (editorOpen) return
            editorOpen = true
            val limit = if (hourPart) 23 else 59
            val input = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(2))
                setText(number(if (hourPart) hh else mm))
                selectAll()
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(ink)
                background = rounded(Color.rgb(9, 47, 63), true)
                setPadding(dp(10), dp(12), dp(10), dp(12))
            }
            val editDialog = AlertDialog.Builder(context,
                R.style.BalanceHubDialogWinterDark)
                .setTitle(if (hourPart) "Часы (0–23)" else "Минуты (0–59)")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Готово", null)
                .create()
            editDialog.setOnShowListener {
                editDialog.window?.setBackgroundDrawable(
                    WinterFrostDrawable(rounded(Color.rgb(7, 39, 55)),
                        d, true, WinterSurface.DIALOG))
                editDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val value = input.text.toString().toIntOrNull()
                    if (value == null || value !in 0..limit) {
                        input.error = "Число от 0 до $limit"
                    } else {
                        if (hourPart) hh = value else mm = value
                        refresh()
                        editDialog.dismiss()
                    }
                }
            }
            editDialog.setOnDismissListener { editorOpen = false }
            editDialog.show()
        }
        fun column(views: Array<TextView>, isHour: Boolean): LinearLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                for (i in views.indices) {
                    val target = views[i]
                    target.isClickable = true
                    target.isFocusable = true
                    if (i == 1) {
                        target.background = rounded(Color.rgb(8, 100, 116), true)
                        target.contentDescription = if (isHour) "Ввести часы"
                            else "Ввести минуты"
                        target.setOnClickListener { manual(isHour) }
                    } else {
                        target.contentDescription = if (isHour) "Изменить часы"
                            else "Изменить минуты"
                        target.setOnClickListener {
                            val delta = if (i == 0) -1 else 1
                            if (isHour) hh = (hh + delta + 24) % 24
                            else mm = (mm + delta + 60) % 60
                            refresh()
                        }
                    }
                    addView(target, LinearLayout.LayoutParams(
                        dp(73), dp(if (i == 1) 55 else 43)))
                }
            }
        columns.addView(column(hourView, true))
        columns.addView(caption(":", 23f, true).apply { setTextColor(cyan) },
            LinearLayout.LayoutParams(dp(22), dp(140)))
        columns.addView(column(minuteView, false))
        root.addView(columns, LinearLayout.LayoutParams(-1, dp(151)))
        root.addView(caption("Коснитесь часов или минут для точного ввода", 11f),
            LinearLayout.LayoutParams(-1, dp(34)))
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val cancel = caption("Отмена", 14f, true).apply {
            background = rounded(Color.rgb(20, 58, 75), true)
            setOnClickListener { dialog.dismiss() }
        }
        val choose = caption("Выбрать", 14f, true).apply {
            background = rounded(Color.rgb(12, 151, 158), true)
            setOnClickListener {
                onTime(hh, mm)
                dialog.dismiss()
            }
        }
        footer.addView(cancel, LinearLayout.LayoutParams(0, dp(48), 1f))
        footer.addView(android.view.View(context),
            LinearLayout.LayoutParams(dp(10), dp(1)))
        footer.addView(choose, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))
        refresh()
        dialog = AlertDialog.Builder(context, R.style.BalanceHubDialogWinterDark)
            .setView(root).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                WinterFrostDrawable(GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(9, 58, 77), Color.rgb(5, 35, 52))
                ).apply { cornerRadius = dp(22).toFloat() },
                    d, true, WinterSurface.DIALOG))
        }
        dialog.show()
    }
}
