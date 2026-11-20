package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.pm.PackageInfoCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unlike a pure Gradle compilation, this checks real Android UI drawing.
 * A regression to the invisible wire/empty ornament of rc1 is caught by
 * checking turquoise glass ornaments and the warm golden garland/bow pixels.
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
        var turquoise = 0
        var gold = 0
        val x0 = (header.width * .60f).toInt()
        for (y in 0 until header.height step 2) {
            for (x in x0 until header.width step 2) {
                val pixel = header.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Glass-blue ornaments must be bright, not just the dark blue sky.
                if (x > header.width * .72f && y > header.height * .40f &&
                    g > 135 && b > 155 && g > r + 65 && b > r + 75) turquoise++
                if (r > 178 && g > 142 && b < 160 && r > b + 45) gold++
            }
        }
        assertTrue("Turquoise glass bauble not visible in " + appearance +
            ": " + turquoise, turquoise >= 12)
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
            // Public Pictures survives instrumentation cleanup / app uninstall.
            // Private Android/data folders can disappear before adb pull runs.
            val details = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name + ".png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/BalanceHubWinter")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = checkNotNull(context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, details))
            val stream = checkNotNull(context.contentResolver.openOutputStream(uri))
            stream.use {
                assertTrue("PNG output failed", image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            context.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
        } finally {
            image.recycle()
        }
    }

    /** Compare the functional View tree, not decorative colors or outlines. */
    private fun functionalLayout(activity: MainActivity): List<String> {
        val container = activity.findViewById<ViewGroup>(android.R.id.content)
        val screen = container.getChildAt(0) as ViewGroup
        val header = screen.getChildAt(0) as ViewGroup
        val headerTitle = header.getChildAt(0)
        val scroller = screen.getChildAt(1) as ViewGroup
        val root = scroller.getChildAt(0)
        val tree = mutableListOf<String>()
        tree += "BalanceHubTitle:${headerTitle.left},${headerTitle.top}," +
            "${headerTitle.width},${headerTitle.height},${headerTitle.translationY}"
        fun walk(view: View, path: String) {
            if (view.visibility != View.VISIBLE) return
            if (view is TextView && view.text.isNotBlank()) {
                tree += "text:${view.text}@$path:${view.left},${view.top}," +
                    "${view.width},${view.height}"
            }
            if (view.isClickable) {
                tree += "button:${view.javaClass.simpleName}@$path:" +
                    "${view.left},${view.top},${view.width},${view.height}"
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    walk(view.getChildAt(i), "$path/$i")
                }
            }
        }
        walk(root, "root")
        return tree
    }

    @Test fun winterOnlyChangesDecorationAcrossAllTabs() {
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val oldOnboarding = prefs.all["onboarding_completed"]
        val oldWelcome = prefs.all["release_welcome_version"]
        val oldAppearance = prefs.all["appearance"]
        val oldOverride = prefs.all["winter_theme_test_mode"]
        val oldPaymentsExpanded = prefs.all["payments_list_expanded"]
        val oldEventsExpanded = prefs.all["event_list_expanded"]
        val code = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0))
        val chooseTab = MainActivity::class.java.getDeclaredMethod(
            "selectTab", Int::class.javaPrimitiveType).apply { isAccessible = true }
        val redraw = MainActivity::class.java.getDeclaredMethod(
            "renderTab").apply { isAccessible = true }
        val layouts = mutableMapOf<String, List<String>>()
        try {
            for (appearance in listOf("night", "day")) {
                for (season in listOf("normal", "winter")) {
                    val winter = season == "winter"
                    assertTrue(prefs.edit().putBoolean("onboarding_completed", true)
                        .putLong("release_welcome_version", code)
                        .putBoolean("payments_list_expanded", false)
                        .putBoolean("event_list_expanded", false)
                        .putString("appearance", appearance)
                        .putString("winter_theme_test_mode",
                            if (winter) WinterThemePolicy.FORCE_ON
                            else WinterThemePolicy.FORCE_OFF).commit())
                    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                        for (tab in 0..4) {
                            scenario.onActivity { activity ->
                                chooseTab.invoke(activity, tab)
                            }
                            instrumentation.waitForIdleSync()
                            scenario.onActivity { activity ->
                                layouts["$season-$appearance-$tab"] =
                                    functionalLayout(activity)
                                if (!winter) screenshot(activity,
                                    "normal-$appearance-tab$tab", false)
                            }
                        }
                        // Collapsible payment/event lists must share their
                        // exact positions and actions in both appearances.
                        for ((tab, fieldName) in listOf(
                            2 to "upcomingListExpanded", 3 to "eventListExpanded")) {
                            scenario.onActivity { activity ->
                                chooseTab.invoke(activity, tab)
                                MainActivity::class.java.getDeclaredField(fieldName)
                                    .apply { isAccessible = true }
                                    .setBoolean(activity, true)
                                redraw.invoke(activity)
                            }
                            instrumentation.waitForIdleSync()
                            scenario.onActivity { activity ->
                                layouts["$season-$appearance-$tab-expanded"] =
                                    functionalLayout(activity)
                                screenshot(activity, "$season-$appearance-tab$tab-expanded", false)
                            }
                        }
                    }
                }
                for (tab in 0..4) {
                    assertEquals("Theme changed functional layout on tab $tab ($appearance)",
                        layouts["normal-$appearance-$tab"],
                        layouts["winter-$appearance-$tab"])
                }
                for (tab in listOf(2, 3)) {
                    assertEquals("Theme changed expanded tab $tab ($appearance)",
                        layouts["normal-$appearance-$tab-expanded"],
                        layouts["winter-$appearance-$tab-expanded"])
                }
            }
        } finally {
            val editor = prefs.edit()
            if (oldOnboarding is Boolean) editor.putBoolean(
                "onboarding_completed", oldOnboarding) else editor.remove("onboarding_completed")
            if (oldWelcome is Long) editor.putLong(
                "release_welcome_version", oldWelcome) else editor.remove("release_welcome_version")
            if (oldAppearance is String) editor.putString(
                "appearance", oldAppearance) else editor.remove("appearance")
            if (oldOverride is String) editor.putString(
                "winter_theme_test_mode", oldOverride) else editor.remove("winter_theme_test_mode")
            if (oldPaymentsExpanded is Boolean) editor.putBoolean(
                "payments_list_expanded", oldPaymentsExpanded)
                else editor.remove("payments_list_expanded")
            if (oldEventsExpanded is Boolean) editor.putBoolean(
                "event_list_expanded", oldEventsExpanded)
                else editor.remove("event_list_expanded")
            editor.commit()
        }
    }

    @Test fun frostIsUniquePerWindowAndStableBetweenDraws() {
        val d = context.resources.displayMetrics.density
        val width = (240f * d).toInt()
        val height = (130f * d).toInt()
        fun sample(drawable: android.graphics.drawable.Drawable): Bitmap {
            drawable.setBounds(0, 0, width, height)
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .also { drawable.draw(Canvas(it)) }
        }
        fun frost(): WinterFrostDrawable = WinterFrostDrawable(
            android.graphics.drawable.ColorDrawable(Color.rgb(8, 51, 66)),
            d, true, WinterSurface.CARD)
        val first = frost()
        val other = frost()
        val a = sample(first)
        val same = sample(first)
        val b = sample(other)
        try {
            var differences = 0
            for (y in 1 until height step 5) {
                for (x in 1 until width step 5) {
                    assertTrue("Frost moved during redraw at $x, $y",
                        a.getPixel(x, y) == same.getPixel(x, y))
                    if (a.getPixel(x, y) != b.getPixel(x, y)) differences++
                }
            }
            assertTrue("Two cards reused the same crystal pattern: $differences",
                differences > 20)
        } finally {
            a.recycle()
            same.recycle()
            b.recycle()
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
