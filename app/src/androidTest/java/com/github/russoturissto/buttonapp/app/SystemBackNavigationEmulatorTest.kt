package com.github.russoturissto.buttonapp.app

import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.startsWith
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercise Android's real Back dispatch, not navigation buttons. No data,
 * sound, vibration, alarm, notification or bank settings are changed.
 */
@RunWith(AndroidJUnit4::class)
class SystemBackNavigationEmulatorTest {
    @get:Rule val scenario = activityScenarioRule<MainActivity>()

    private fun tab(label: String) = allOf(
        withContentDescription(startsWith(label)),
        isDescendantOfA(isAssignableFrom(BalanceBottomNavigation::class.java))
    )

    private fun assertHome() =
        onView(tab("Главная")).check(matches(isSelected()))

    @Test fun allOtherMainTabsReturnHomeOnSystemBack() {
        for (label in listOf("Балансы", "Платежи", "События", "Настройки")) {
            onView(tab(label)).perform(click())
            onView(tab(label)).check(matches(isSelected()))
            pressBackUnconditionally()
            assertHome()
        }
    }

    @Test fun nestedNotificationSettingsReturnOnePagePerBackThenHome() {
        onView(tab("Настройки")).perform(click())
        onView(withText("Уведомления")).perform(scrollTo(), click())
        onView(withText("Платежи")).perform(scrollTo(), click())

        // Notification category -> notifications list -> settings root -> Home.
        pressBackUnconditionally()
        onView(tab("Настройки")).check(matches(isSelected()))
        onView(withText("События")).check(matches(isDisplayed()))
        pressBackUnconditionally()
        onView(tab("Настройки")).check(matches(isSelected()))
        onView(withText("Уведомления")).check(matches(isDisplayed()))
        pressBackUnconditionally()
        assertHome()
    }

    @Test fun paymentAndEventArchiveBackFirstClosesSubmenuThenReturnsHome() {
        onView(tab("Платежи")).perform(click())
        onView(withText("Архив платежей")).perform(scrollTo(), click())
        onView(withText("‹  К платежам")).check(matches(isDisplayed()))
        pressBackUnconditionally()
        onView(tab("Платежи")).check(matches(isSelected()))
        onView(withText("Архив платежей")).check(matches(isDisplayed()))
        pressBackUnconditionally()
        assertHome()

        onView(tab("События")).perform(click())
        onView(withText("Архив событий")).perform(scrollTo(), click())
        onView(withText("‹  К событиям")).check(matches(isDisplayed()))
        pressBackUnconditionally()
        onView(tab("События")).check(matches(isSelected()))
        onView(withText("Архив событий")).check(matches(isDisplayed()))
        pressBackUnconditionally()
        assertHome()
    }
}
