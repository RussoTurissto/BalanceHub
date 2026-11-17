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
                    val form = LinearLayout(activity)
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

    private fun findSwitch(view: View): SwitchCompat? {
        if (view is SwitchCompat) return view
        if (view is ViewGroup) for (i in 0 until view.childCount)
            findSwitch(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun eventWeekdayChipsToggleWithoutChangingTheInterval() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val form = LinearLayout(activity)
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
