package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Bank switches, Push settings and account rows must remain in one panel per bank. */
@RunWith(AndroidJUnit4::class)
class BankSettingsGroupingEmulatorTest {
    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount)
            .flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun bankSectionsHaveOneHeadingAndOneContinuousSettingsPanel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val keys = listOf("onboarding_completed", "release_welcome_version",
            "app_lock_offer_seen")
        val before = keys.associateWith { prefs.all[it] }
        val code = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0))
        try {
            assertTrue(prefs.edit()
                .putBoolean("onboarding_completed", true)
                .putLong("release_welcome_version", code)
                .putBoolean("app_lock_offer_seen", true).commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val selectTab = MainActivity::class.java.getDeclaredMethod(
                        "selectTab", Int::class.javaPrimitiveType).apply {
                        isAccessible = true
                    }
                    val openPage = MainActivity::class.java.getDeclaredMethod(
                        "openSettingsPage", String::class.java).apply {
                        isAccessible = true
                    }
                    selectTab.invoke(activity, 4)
                    openPage.invoke(activity, "banks")
                    val content = MainActivity::class.java.getDeclaredField(
                        "contentContainer").apply { isAccessible = true }
                        .get(activity) as LinearLayout
                    val expected = RussianBankCatalog.available(activity)
                    val headings = (0 until content.childCount).filter { index ->
                        (content.getChildAt(index) as? TextView)?.text?.toString()
                            ?.startsWith("Карты и счета · ") == true
                    }
                    assertEquals(expected.size, headings.size)
                    headings.forEachIndexed { index, position ->
                        val bank = expected[index]
                        assertEquals("Карты и счета · " + bank.name,
                            (content.getChildAt(position) as TextView).text.toString())
                        val panel = content.getChildAt(position + 2) as LinearLayout
                        val rows = (0 until panel.childCount)
                            .map { panel.getChildAt(it) }
                            .filterIsInstance<LinearLayout>()
                        assertTrue("Expected visibility, Push and account state", rows.size >= 3)
                        assertTrue(texts(rows[0]).contains(bank.name))
                        assertTrue(texts(rows[1]).contains("Получение Push"))
                        if (index < headings.lastIndex) {
                            // One panel per bank, then the next bank heading.
                            assertEquals(position + 4, headings[index + 1])
                        }
                    }
                    // The system-wide notification access remains after all bank panels.
                    val androidAccess = content.getChildAt(headings.last() + 4)
                    assertTrue(texts(androidAccess).contains("Доступ Android"))
                }
            }
        } finally {
            val edit = prefs.edit()
            before.forEach { (key, value) ->
                when (value) {
                    is Boolean -> edit.putBoolean(key, value)
                    is Long -> edit.putLong(key, value)
                    else -> edit.remove(key)
                }
            }
            edit.commit()
        }
    }
}
