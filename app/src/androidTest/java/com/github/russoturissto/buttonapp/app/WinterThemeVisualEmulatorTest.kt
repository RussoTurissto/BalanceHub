package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.pm.PackageInfoCompat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Unlike a pure Gradle compilation, this checks real Android UI drawing.
 * A regression to the invisible wire/empty ornament of rc1 is caught by
 * checking saturated red bauble pixels and warm golden bulb pixels.
 * Captures all five tabs in both appearance modes for a visual review.
 */
@RunWith(AndroidJUnit4::class)
class WinterThemeVisualEmulatorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun render(view: android.view.View): Bitmap {
        assertTrue("View was not laid out", view.width > 0 && view.height > 0)
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            .also { view.draw(Canvas(it)) }
    }

    private fun verifyVisibleDecor(header: Bitmap, appearance: String) {
        var red = 0
        var gold = 0
        val x0 = (header.width * .60f).toInt()
        for (y in 0 until header.height step 2) {
            for (x in x0 until header.width step 2) {
                val pixel = header.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                if (r > 158 && r > g + 55 && r > b + 45) red++
                if (r > 178 && g > 142 && b < 160 && r > b + 45) gold++
            }
        }
        assertTrue("Red Christmas bauble not visible in " + appearance + ": " + red,
            red >= 12)
        assertTrue("Golden light bulbs not visible in " + appearance + ": " + gold,
            gold >= 15)
    }

    private fun screenshot(activity: MainActivity, name: String, checkHome: Boolean) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val screen = content.getChildAt(0) as ViewGroup
        if (checkHome) {
            val header = screen.getChildAt(0)
            val bitmap = render(header)
            try {
                verifyVisibleDecor(bitmap, name)
            } finally {
                bitmap.recycle()
            }
        }
        val image = render(screen)
        try {
            val directory = File(context.getExternalFilesDir(null), "winter-previews")
            assertTrue("Cannot create visual snapshots directory",
                directory.isDirectory || directory.mkdirs())
            FileOutputStream(File(directory, name + ".png")).use {
                assertTrue("PNG output failed", image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            image.recycle()
        }
    }

    @Test fun festiveAppearanceIsVisibleOnAllTabsInDarkAndLight() {
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val oldOnboarding = prefs.all["onboarding_completed"]
        val oldWelcome = prefs.all["release_welcome_version"]
        val oldAppearance = prefs.all["appearance"]
        val oldOverride = prefs.all["winter_theme_test_mode"]
        val code = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0))
        try {
            val chooseTab = MainActivity::class.java.getDeclaredMethod(
                "selectTab", Int::class.javaPrimitiveType).apply { isAccessible = true }
            for (appearance in listOf("night", "day")) {
                assertTrue(prefs.edit().putBoolean("onboarding_completed", true)
                    .putLong("release_welcome_version", code)
                    .putString("appearance", appearance)
                    .putString("winter_theme_test_mode", WinterThemePolicy.FORCE_ON)
                    .commit())
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    for (tab in 0..4) {
                        scenario.onActivity { activity ->
                            if (tab != 0) chooseTab.invoke(activity, tab)
                        }
                        instrumentation.waitForIdleSync()
                        scenario.onActivity { activity ->
                            screenshot(activity, "winter-" + appearance + "-tab" + tab,
                                tab == 0)
                        }
                    }
                }
            }
        } finally {
            val editor = prefs.edit()
            if (oldOnboarding is Boolean) editor.putBoolean("onboarding_completed", oldOnboarding)
            else editor.remove("onboarding_completed")
            if (oldWelcome is Long) editor.putLong("release_welcome_version", oldWelcome)
            else editor.remove("release_welcome_version")
            if (oldAppearance is String) editor.putString("appearance", oldAppearance)
            else editor.remove("appearance")
            if (oldOverride is String) editor.putString("winter_theme_test_mode", oldOverride)
            else editor.remove("winter_theme_test_mode")
            editor.commit()
        }
    }
}
