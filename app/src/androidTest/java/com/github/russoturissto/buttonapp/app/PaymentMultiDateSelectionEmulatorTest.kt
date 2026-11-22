package com.github.russoturissto.buttonapp.app

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class PaymentMultiDateSelectionEmulatorTest {
    private fun day(number: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2027, Calendar.JANUARY, number)
    }.timeInMillis

    private fun openCalendar(
        scenario: ActivityScenario<MainActivity>, dates: Set<Long>,
        onSelected: (Set<Long>) -> Unit
    ) {
        scenario.onActivity { activity ->
            val picker = MainActivity::class.java.getDeclaredMethod(
                "showThemedMultiDateCalendar", Set::class.java,
                kotlin.jvm.functions.Function1::class.java)
            picker.isAccessible = true
            picker.invoke(activity, dates, onSelected)
        }
    }

    @Test fun multipleTapsToggleDatesWithoutClosingAndDoneCommitsOnce() {
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
                var saved: Set<Long>? = null
                val original = setOf(day(15), day(17))
                openCalendar(scenario, original) { saved = it }
                onView(withText("Выбрано дат: 2")).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withContentDescription("Дата 15.01.2027, выбрана")).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                onView(withContentDescription("Дата 16.01.2027, не выбрана")).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                    .perform(click())
                // Confirm the tap actually changes the selected day, not just
                // the header count. This must remain a real Espresso touch.
                onView(withContentDescription("Дата 16.01.2027, выбрана")).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                onView(withText("Выбрано дат: 3")).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withContentDescription("Дата 15.01.2027, выбрана")).inRoot(isDialog())
                    .perform(click())
                onView(withContentDescription("Дата 15.01.2027, не выбрана")).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                onView(withText("Выбрано дат: 2")).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withContentDescription("Дата 15.01.2027, не выбрана")).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                assertEquals(null, saved)
                onView(withText("Готово")).inRoot(isDialog()).perform(click())
                assertEquals(setOf(day(16), day(17)), saved)
                onView(withText("Выберите даты платежей")).check(doesNotExist())

                // Reopening shows the saved selection, but Cancel cannot alter it.
                openCalendar(scenario, checkNotNull(saved)) { saved = it }
                onView(withContentDescription("Дата 16.01.2027, выбрана")).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                onView(withContentDescription("Дата 18.01.2027, не выбрана")).inRoot(isDialog())
                    .perform(click())
                onView(withContentDescription("Дата 18.01.2027, выбрана")).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                onView(withText("Выбрано дат: 3")).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withText("Отмена")).inRoot(isDialog()).perform(click())
                assertEquals(setOf(day(16), day(17)), saved)
                onView(withText("Выберите даты платежей")).check(doesNotExist())
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
}
