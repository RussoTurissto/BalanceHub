package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.widget.ScrollView
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.startsWith
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseHistoryNavigationEmulatorTest {
    @Test fun welcomeContainsOnlyInstalledReleaseChangesAndNoHistoryAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val currentNotes = AppReleaseNotes.forVersion(version)
        org.junit.Assume.assumeTrue(currentNotes.isNotEmpty())
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val previousOnboarding = prefs.all["onboarding_completed"]
        val previousWelcome = prefs.all["release_welcome_version"]
        val versionCode = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0))
        try {
            assertTrue(prefs.edit()
                .putBoolean("onboarding_completed", true)
                .putLong("release_welcome_version", 0L)
                .commit())
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withText("Что нового в версии " + version))
                    .check(matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
                onView(withText(currentNotes.first().title))
                    .check(matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
                // History is available in Settings > Updates, never on the welcome dialog.
                onView(withText("История изменений")).check(doesNotExist())
                onView(withText("Начать пользоваться")).perform(click())
                org.junit.Assert.assertEquals(versionCode,
                    prefs.getLong("release_welcome_version", -1L))
            }
        } finally {
            val edit = prefs.edit()
            if (previousOnboarding is Boolean) edit.putBoolean(
                "onboarding_completed", previousOnboarding)
            else edit.remove("onboarding_completed")
            if (previousWelcome is Long) edit.putLong(
                "release_welcome_version", previousWelcome)
            else edit.remove("release_welcome_version")
            edit.commit()
        }
    }

    @Test fun updateSettingsShowStableHistoryAndBackReturnsToUpdates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val completed = prefs.all["onboarding_completed"]
        val welcome = prefs.all["release_welcome_version"]
        val code = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0))
        try {
            assertTrue(prefs.edit()
                .putBoolean("onboarding_completed", true)
                .putLong("release_welcome_version", code).commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    // Navigate using the persistent bottom bar.
                    fun findNav(view: android.view.View): BalanceBottomNavigation? {
                        if (view is BalanceBottomNavigation) return view
                        if (view is android.view.ViewGroup)
                            for (i in 0 until view.childCount) {
                                findNav(view.getChildAt(i))?.let { return it }
                            }
                        return null
                    }
                    assertTrue(checkNotNull(findNav(activity.window.decorView))
                        .section(4)!!.performClick())
                }
                val page = isDescendantOfA(isAssignableFrom(ScrollView::class.java))
                onView(allOf(withText("Обновления"), page)).perform(scrollTo(), click())
                onView(allOf(withText("История изменений"), page))
                    .perform(scrollTo(), click())
                onView(withText("Версия 1.2.0")).check(matches(
                    androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
                onView(withText("Версия 1.2.1-rc3")).check(doesNotExist())
                onView(withText("Версия 1.2.1-rc4")).check(doesNotExist())
                // Click the row that owns the listener, not its non-clickable label.
                onView(withContentDescription(startsWith("Версия 1.2.0,")))
                    .perform(scrollTo(), click())
                onView(withText("• Улучшены банковские балансы."))
                    .check(matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
                pressBackUnconditionally()
                onView(withText("Проверить обновления")).perform(scrollTo())
                    .check(matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            }
        } finally {
            val edit = prefs.edit()
            if (completed is Boolean) edit.putBoolean("onboarding_completed", completed)
            else edit.remove("onboarding_completed")
            if (welcome is Long) edit.putLong("release_welcome_version", welcome)
            else edit.remove("release_welcome_version")
            edit.commit()
        }
    }
}
