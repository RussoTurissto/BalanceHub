package com.github.russoturissto.buttonapp.app

import android.widget.ScrollView
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

    @Test fun channelActionsAreInEngineeringCategoriesButNotInOrdinarySettings() {
        // Bottom settings icon is the only view with this exact description on Home.
        onView(withContentDescription("Настройки")).perform(click())
        repeat(5) {
            onView(withContentDescription("Настройки")).perform(click())
        }
        onView(withText("Системные каналы   ›")).perform(scrollTo(), click())
        for (category in listOf("Платежи", "События", "Обновления")) {
            onView(settingsLabel(category)).perform(scrollTo(), click())
            onView(withText("Канал уведомлений Android")).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(anyOf(withText("Повторно применить сигналы"),
                withText("Применить звук и вибрацию"))).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText("‹  Системные каналы")).perform(click())
        }
        onView(withText("‹  Инженерное меню")).perform(click())
        onView(withText("‹  Назад в настройки")).perform(click())
        onView(settingsLabel("Уведомления")).perform(scrollTo(), click())
        for (category in listOf("Платежи", "События", "Обновления")) {
            onView(settingsLabel(category)).perform(scrollTo(), click())
            onView(withText("Звук")).check(matches(isDisplayed()))
            onView(withText("Вибросигнал")).check(matches(isDisplayed()))
            onView(withText("Проверить уведомление")).perform(scrollTo())
                .check(matches(isDisplayed()))
            onView(withText("Вернуться к системному звуку")).check(doesNotExist())
            onView(withText("Канал уведомлений Android")).check(doesNotExist())
            onView(withText("Повторно применить сигналы")).check(doesNotExist())
            onView(withContentDescription("Назад")).perform(click())
        }
    }
}
