package com.github.russoturissto.buttonapp.app

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.startsWith
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Navigation-only: never changes any sound, vibration or channel configuration. */
@RunWith(AndroidJUnit4::class)
class EngineeringMenuNavigationEmulatorTest {
    @get:Rule val scenario = activityScenarioRule<MainActivity>()

    private fun settingsLabel(title: String) = allOf(
        withText(title),
        isDescendantOfA(isAssignableFrom(ScrollView::class.java))
    )

    private fun bottomSettingsTab() = allOf(
        withContentDescription(startsWith("Настройки")),
        isDescendantOfA(isAssignableFrom(BalanceBottomNavigation::class.java))
    )

    private fun unlockEngineeringInActiveActivity(activity: MainActivity) {
        // Several layout copies can temporarily expose an identical heading.
        // Operate on the active Activity's concrete view, not a global matcher.
        fun findTitle(view: View): TextView? {
            if (view is TextView && view.text.toString() == "Настройки" &&
                view.contentDescription?.toString() == "Настройки" && view.isClickable) {
                return view
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    findTitle(view.getChildAt(i))?.let { return it }
                }
            }
            return null
        }
        val title = checkNotNull(findTitle(activity.window.decorView)) {
            "Engineering unlock heading is missing from Settings"
        }
        repeat(5) { check(title.performClick()) }
    }

    @Test fun channelActionsAreInEngineeringCategoriesButNotInOrdinarySettings() {
        // Enter via bottom navigation, then perform all five unlock taps on
        // the Settings heading belonging to this Activity.
        onView(bottomSettingsTab()).perform(click())
        scenario.scenario.onActivity { activity -> unlockEngineeringInActiveActivity(activity) }
        onView(withText("Системные каналы   ›")).perform(scrollTo(), click())
        for (category in listOf("Платежи", "События", "Обновления")) {
            onView(settingsLabel(category)).perform(scrollTo(), click())
            onView(withText("Канал уведомлений Android")).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(anyOf(withText("Повторно применить сигналы"),
                withText("Применить звук и вибрацию"))).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText("‹  Системные каналы")).perform(scrollTo(), click())
        }
        onView(withText("‹  Инженерное меню")).perform(scrollTo(), click())
        onView(withText("‹  Назад в настройки")).perform(scrollTo(), click())
        onView(settingsLabel("Уведомления")).perform(scrollTo(), click())
        for (category in listOf("Платежи", "События", "Обновления")) {
            onView(settingsLabel(category)).perform(scrollTo(), click())
            onView(withText("Звук")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Вибросигнал")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Проверить уведомление")).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText("Вернуться к системному звуку")).check(doesNotExist())
            onView(withText("Канал уведомлений Android")).check(doesNotExist())
            onView(withText("Повторно применить сигналы")).check(doesNotExist())
            onView(withContentDescription("Назад")).perform(scrollTo(), click())
        }
    }
}
