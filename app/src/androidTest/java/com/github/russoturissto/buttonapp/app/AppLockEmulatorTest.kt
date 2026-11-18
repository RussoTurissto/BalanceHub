package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.pm.PackageInfoCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockEmulatorTest {
    @Test fun fourDigitPinIsStoredUsingAndroidKeystoreAndWrongPinIsRejected() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Each emulator test starts with its own clean installation.
        AppLockSecurity.disable(context)
        try {
            assertTrue(AppLockSecurity.savePin(context, "3492"))
            assertTrue(AppLockSecurity.enabled(context))
            assertEquals(AppLockSecurity.PinResult.WRONG,
                AppLockSecurity.verifyPin(context, "0000"))
            assertEquals(AppLockSecurity.PinResult.SUCCESS,
                AppLockSecurity.verifyPin(context, "3492"))
            val prefs = context.getSharedPreferences("balancehub_lock", Context.MODE_PRIVATE)
            assertFalse(prefs.all.values.any { it == "3492" })
            assertFalse(AppLockSecurity.fingerprintEnabled(context))
        } finally {
            AppLockSecurity.disable(context)
        }
    }

    @Test fun protectedScreenshotsAreOptInAndSettingIsPersisted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = AppLockSecurity.screenshotsAllowed(context)
        try {
            AppLockSecurity.setScreenshotsAllowed(context, false)
            assertFalse(AppLockSecurity.screenshotsAllowed(context))
            AppLockSecurity.setScreenshotsAllowed(context, true)
            assertTrue(AppLockSecurity.screenshotsAllowed(context))
        } finally {
            AppLockSecurity.setScreenshotsAllowed(context, before)
        }
    }

    @Test fun securitySettingsAreAvailableOnlyOnAndroid11AndNewer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(Build.VERSION.SDK_INT >= 30, AppLockSecurity.supported())
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val previousOnboarding = prefs.all["onboarding_completed"]
        val previousWelcome = prefs.all["release_welcome_version"]
        val previousOffer = prefs.all["app_lock_offer_seen"]
        val code = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0))
        try {
            prefs.edit().putBoolean("onboarding_completed", true)
                .putBoolean("app_lock_offer_seen", true)
                .putLong("release_welcome_version", code).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                if (Build.VERSION.SDK_INT >= 30) {
                    scenario.onActivity { activity ->
                        fun findNav(view: android.view.View): BalanceBottomNavigation? {
                            if (view is BalanceBottomNavigation) return view
                            if (view is android.view.ViewGroup)
                                for (i in 0 until view.childCount)
                                    findNav(view.getChildAt(i))?.let { return it }
                            return null
                        }
                        checkNotNull(findNav(activity.window.decorView))
                            .section(4)!!.performClick()
                        fun findRow(view: android.view.View): android.view.View? {
                            if (view.isClickable &&
                                view.contentDescription?.startsWith("Безопасность,") == true)
                                return view
                            if (view is android.view.ViewGroup)
                                for (i in 0 until view.childCount)
                                    findRow(view.getChildAt(i))?.let { return it }
                            return null
                        }
                        assertTrue(checkNotNull(findRow(activity.window.decorView)).performClick())
                    }
                    androidx.test.espresso.Espresso.onView(
                        androidx.test.espresso.matcher.ViewMatchers.withText("Защита приложения"))
                        .check(androidx.test.espresso.assertion.ViewAssertions.matches(
                            androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
                } else assertFalse(AppLockSecurity.supported())
            }
        } finally {
            val e = prefs.edit()
            if (previousOnboarding is Boolean)
                e.putBoolean("onboarding_completed", previousOnboarding)
            else e.remove("onboarding_completed")
            if (previousWelcome is Long) e.putLong("release_welcome_version", previousWelcome)
            else e.remove("release_welcome_version")
            if (previousOffer is Boolean) e.putBoolean("app_lock_offer_seen", previousOffer)
            else e.remove("app_lock_offer_seen")
            e.commit()
        }
    }
}
