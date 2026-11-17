package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** UI regression: recurrence choices use themed toggle chips without changing plans. */
@RunWith(AndroidJUnit4::class)
class RecurringChoiceChipsEmulatorTest {
    private fun find(view: View, description: String): TextView? {
        if (view is TextView && view.contentDescription?.toString() == description)
            return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                find(view.getChildAt(i), description)?.let { return it }
            }
        }
        return null
    }

    @Test fun monthlyPaymentChipsToggleAndKeepTheMonthlyRule() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val onboarding = prefs.all["onboarding_completed"]
        val welcome = prefs.all["release_welcome_version"]
        val code = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0))
        try {
            assertTrue(prefs.edit().putBoolean("onboarding_completed", true)
                .putLong("release_welcome_version", code).commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val form = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    val recurrence = Spinner(activity).apply {
                        adapter = ArrayAdapter(activity,
                            android.R.layout.simple_spinner_item,
                            listOf("once", "weekly", "monthly", "custom"))
                        setSelection(3)
                    }
                    val method = MainActivity::class.java.getDeclaredMethod(
                        "installCustomPaymentControls",
                        LinearLayout::class.java, Spinner::class.java, PaymentPlan::class.java)
                    method.isAccessible = true
                    method.invoke(activity, form, recurrence, PaymentPlan(
                        mode = "monthly", monthlyDays = setOf(5, 15),
                        monthlyEnabled = true))
                    assertEquals((4 * activity.resources.displayMetrics.density).toInt(),
                        (form.getChildAt(form.childCount - 1).layoutParams
                            as LinearLayout.LayoutParams).topMargin)
                    val density = activity.resources.displayMetrics.density
                    val monthHint = checkNotNull(findText(form, "Выберите числа месяца"))
                    val monthSummary = checkNotNull(findText(form, "Выбрано: 2 · 5, 15"))
                    val endHint = checkNotNull(findText(form, "Дата окончания не задана"))
                    assertCaptionAlignment(form,
                        listOf(monthHint, monthSummary, endHint), density)
                    monthHint.textSize = 20f
                    monthSummary.textSize = 20f
                    endHint.textSize = 20f
                    assertCaptionsFit(form, listOf(monthHint, monthSummary, endHint),
                        280, density)
                    val checked = checkNotNull(find(form, "Число месяца 15, выбрано"))
                    assertTrue(checked.isSelected)
                    assertNotNull(checked.background)
                    assertTrue(checked.performClick())
                    assertFalse(checked.isSelected)
                    assertEquals("Число месяца 15, не выбрано",
                        checked.contentDescription.toString())
                    val empty = checkNotNull(find(form, "Число месяца 25, не выбрано"))
                    assertTrue(empty.performClick())
                    assertTrue(empty.isSelected)
                    assertTrue(checkNotNull(find(form,
                        "Число месяца 25, выбрано")).isSelected)
                    val field = MainActivity::class.java.getDeclaredField("pendingPaymentPlan")
                    field.isAccessible = true
                    val plan = field.get(activity) as PaymentPlan
                    assertEquals(setOf(5, 25), plan.monthlyDays)
                    assertTrue(plan.monthlyEnabled)
                    assertEquals("monthly", plan.mode)
                    assertTrue(checkNotNull(find(form, "Число месяца 5, выбрано")).isSelected)
                    assertChipRowsFit(form, listOf(
                        "Число месяца 1, не выбрано",
                        "Число месяца 25, выбрано",
                        "Число месяца 31, не выбрано"
                    ), 280, activity.resources.displayMetrics.density)
                    val monthlySwitch = findSwitch(form)
                    assertTrue(checkNotNull(monthlySwitch).isChecked)
                    monthlySwitch.toggle()
                    assertFalse((field.get(activity) as PaymentPlan).monthlyEnabled)
                    monthlySwitch.toggle()
                    assertTrue((field.get(activity) as PaymentPlan).monthlyEnabled)
                }
            }
        } finally {
            val editor = prefs.edit()
            if (onboarding is Boolean) editor.putBoolean("onboarding_completed", onboarding)
            else editor.remove("onboarding_completed")
            if (welcome is Long) editor.putLong("release_welcome_version", welcome)
            else editor.remove("release_welcome_version")
            editor.commit()
        }
    }

    private fun findText(view: View, value: String): TextView? {
        if (view is TextView && view.text?.toString() == value) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findText(view.getChildAt(i), value)?.let { return it }
        }
        return null
    }

    private fun findHint(view: View, value: String): TextView? {
        if (view is TextView && view.hint?.toString() == value) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findHint(view.getChildAt(i), value)?.let { return it }
        }
        return null
    }

    /** Compare the visible text origin, including every enclosing panel's padding. */
    private fun textStartX(form: LinearLayout, label: TextView): Int {
        var offset = label.paddingLeft
        var current: View = label
        while (current !== form) {
            offset += current.left
            current = current.parent as? View
                ?: error("Label is not inside the recurrence form")
        }
        return offset
    }

    private fun findSpinner(view: View): Spinner? {
        if (view is Spinner) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findSpinner(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun assertCaptionAlignment(
        form: LinearLayout, captions: List<TextView>, density: Float
    ) {
        assertCaptionsFit(form, captions, 280, density)
        val modeText = checkNotNull(findSpinner(form)?.selectedView as? TextView)
        val actualModeStart = textStartX(form, modeText)
        val expectedStart = (14 * density + 0.5f).toInt()
        assertTrue("Custom mode text should align to the 14dp form grid",
            kotlin.math.abs(actualModeStart - expectedStart) <= 1)
        for (caption in captions) {
            val actualStart = textStartX(form, caption)
            assertTrue("Caption '${caption.text}' should align with custom mode text",
                kotlin.math.abs(actualStart - actualModeStart) <= 1)
        }
    }

    private fun assertCaptionsFit(form: LinearLayout, labels: List<TextView>,
        widthDp: Int, density: Float
    ) {
        val width = (widthDp * density).toInt()
        form.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        form.layout(0, 0, width, form.measuredHeight)
        labels.forEach { label ->
            assertTrue("Caption has no width", label.width > 0)
            assertTrue("Caption overflows parent", label.right <=
                (label.parent as View).width)
        }
    }

    private fun findSwitch(view: View): SwitchCompat? {
        if (view is SwitchCompat) return view
        if (view is ViewGroup) for (i in 0 until view.childCount)
            findSwitch(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun eventWeekdayChipsToggleWithoutChangingTheInterval() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val form = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                val recurrence = Spinner(activity).apply {
                    adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item,
                        listOf("a", "b", "c", "d", "e", "custom"))
                    setSelection(5)
                }
                var updated: EventPlan? = null
                val initial = EventPlan(mode = "weekdays",
                    weekdays = setOf(Calendar.MONDAY, Calendar.WEDNESDAY),
                    intervalWeeks = 2)
                val method = MainActivity::class.java.getDeclaredMethod(
                    "installCustomEventControls",
                    LinearLayout::class.java, Spinner::class.java,
                    EventPlan::class.java, Calendar::class.java,
                    kotlin.jvm.functions.Function1::class.java)
                method.isAccessible = true
                method.invoke(activity, form, recurrence, initial,
                    Calendar.getInstance(), { plan: EventPlan -> updated = plan })
                assertEquals((4 * activity.resources.displayMetrics.density).toInt(),
                    (form.getChildAt(form.childCount - 1).layoutParams
                        as LinearLayout.LayoutParams).topMargin)
                val density = activity.resources.displayMetrics.density
                val weekHint = checkNotNull(findText(form, "Выберите дни недели"))
                val endHint = checkNotNull(findText(form, "Без даты окончания"))
                assertCaptionAlignment(form, listOf(weekHint, endHint), density)
                val monday = checkNotNull(find(form, "День недели Пн, выбрано"))
                val wednesday = checkNotNull(find(form, "День недели Ср, выбрано"))
                val friday = checkNotNull(find(form, "День недели Пт, не выбрано"))
                assertTrue(monday.isSelected)
                assertTrue(wednesday.performClick())
                assertFalse(wednesday.isSelected)
                assertEquals("День недели Ср, не выбрано",
                    wednesday.contentDescription.toString())
                assertTrue(friday.performClick())
                assertTrue(friday.isSelected)
                assertEquals(setOf(Calendar.MONDAY, Calendar.FRIDAY),
                    checkNotNull(updated).weekdays)
                assertEquals(2, checkNotNull(updated).intervalWeeks)
                assertEquals("weekdays", checkNotNull(updated).mode)
                assertTrue("Removed interval label must stay hidden",
                    findText(form, "Повторять через число недель") == null)
                assertTrue("Removed interval input must stay hidden",
                    findHint(form, "Интервал в неделях") == null)
            }
        }
    }

    @Test fun newlyCreatedCustomEventDefaultsToEveryWeek() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val form = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val recurrence = Spinner(activity).apply {
                    adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item,
                        listOf("a", "b", "c", "d", "e", "custom"))
                    setSelection(5)
                }
                var updated: EventPlan? = null
                val method = MainActivity::class.java.getDeclaredMethod(
                    "installCustomEventControls",
                    LinearLayout::class.java, Spinner::class.java,
                    EventPlan::class.java, Calendar::class.java,
                    kotlin.jvm.functions.Function1::class.java)
                method.isAccessible = true
                method.invoke(activity, form, recurrence, null,
                    Calendar.getInstance(), { plan: EventPlan -> updated = plan })
                assertEquals(1, checkNotNull(updated).intervalWeeks)
                assertTrue("Removed interval label must not exist",
                    findText(form, "Повторять через число недель") == null)
                assertTrue("Removed input hint must not exist",
                    findHint(form, "Интервал в неделях") == null)
                val monday = checkNotNull(find(form, "День недели Пн, не выбрано"))
                assertTrue(monday.performClick())
                assertEquals(setOf(Calendar.MONDAY), checkNotNull(updated).weekdays)
                assertEquals(1, checkNotNull(updated).intervalWeeks)
            }
        }
    }

    @Test fun customDateCaptionsStayAlignedInPaymentAndEventDialogs() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                val paymentForm = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val paymentSpinner = Spinner(activity).apply {
                    adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item,
                        listOf("once", "weekly", "monthly", "custom"))
                    setSelection(3)
                }
                MainActivity::class.java.getDeclaredMethod("installCustomPaymentControls",
                    LinearLayout::class.java, Spinner::class.java,
                    PaymentPlan::class.java).apply { isAccessible = true }
                    .invoke(activity, paymentForm, paymentSpinner, null)
                val payDates = checkNotNull(findText(paymentForm, "Даты не выбраны"))
                val payEnd = checkNotNull(findText(paymentForm,
                    "Дата окончания не задана"))
                assertCaptionAlignment(paymentForm, listOf(payDates, payEnd), density)

                val eventForm = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val eventSpinner = Spinner(activity).apply {
                    adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item,
                        listOf("a", "b", "c", "d", "e", "custom"))
                    setSelection(5)
                }
                val datesPlan = EventPlan(mode = "dates")
                MainActivity::class.java.getDeclaredMethod("installCustomEventControls",
                    LinearLayout::class.java, Spinner::class.java,
                    EventPlan::class.java, Calendar::class.java,
                    kotlin.jvm.functions.Function1::class.java).apply {
                        isAccessible = true
                    }.invoke(activity, eventForm, eventSpinner, datesPlan,
                        Calendar.getInstance(), { _: EventPlan -> })
                val eventDates = checkNotNull(findText(eventForm, "Даты не выбраны"))
                val eventEnd = checkNotNull(findText(eventForm, "Без даты окончания"))
                assertCaptionAlignment(eventForm, listOf(eventDates, eventEnd), density)
            }
        }
    }

    private fun assertChipRowsFit(
        container: LinearLayout, labels: List<String>, widthDp: Int, density: Float
    ) {
        val px = (widthDp * density).toInt()
        container.measure(
            View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        container.layout(0, 0, px, container.measuredHeight)
        for (label in labels) {
            val chip = checkNotNull(find(container, label))
            val row = chip.parent as LinearLayout
            assertTrue("Chip $label has no width", chip.width > 0)
            assertTrue("Chip $label overflows its row", chip.right <= row.width)
            assertTrue("Chip $label starts outside its row", chip.left >= 0)
            assertTrue("Row must fit its container", row.width <= px)
        }
    }

    @Test fun eventWeekdayRowsFitNarrowDialogsAndLargeText() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val form = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                val recurrence = Spinner(activity).apply {
                    adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item,
                        listOf("a", "b", "c", "d", "e", "custom"))
                    setSelection(5)
                }
                val method = MainActivity::class.java.getDeclaredMethod(
                    "installCustomEventControls",
                    LinearLayout::class.java, Spinner::class.java,
                    EventPlan::class.java, Calendar::class.java,
                    kotlin.jvm.functions.Function1::class.java)
                method.isAccessible = true
                method.invoke(activity, form, recurrence,
                    EventPlan(mode = "weekdays"), Calendar.getInstance(),
                    { _: EventPlan -> })
                val labels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                    .map { "День недели $it, не выбрано" }
                // 20sp instead of the normal 14sp to exercise large text.
                labels.forEach { checkNotNull(find(form, it)).textSize = 20f }
                val density = activity.resources.displayMetrics.density
                assertChipRowsFit(form, labels, 280, density)
                assertChipRowsFit(form, labels, 360, density)
            }
        }
    }

}
