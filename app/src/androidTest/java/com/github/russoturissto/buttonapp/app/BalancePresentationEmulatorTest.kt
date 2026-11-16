package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Regression: skipping onboarding must not leave a gold border on Balances. */
@RunWith(AndroidJUnit4::class)
class BalancePresentationEmulatorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun findNav(node: View): BalanceBottomNavigation? {
        if (node is BalanceBottomNavigation) return node
        if (node is ViewGroup) {
            for (i in 0 until node.childCount) {
                findNav(node.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findButton(node: View, title: String): Button? {
        if (node is Button && node.text.toString() == title) return node
        if (node is ViewGroup) {
            for (i in 0 until node.childCount) {
                findButton(node.getChildAt(i), title)?.let { return it }
            }
        }
        return null
    }

    @Test fun skipAndRestartClearPersistentNavigationHighlight() {
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val oldPresent = prefs.contains("onboarding_completed")
        val oldValue = prefs.getBoolean("onboarding_completed", false)
        try {
            assertTrue(prefs.edit().putBoolean("onboarding_completed", false).commit())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val nav = checkNotNull(findNav(activity.window.decorView))
                    val balances = checkNotNull(nav.section(1))
                    assertNotNull("First tutorial step highlights Balances", balances.background)
                    assertTrue(checkNotNull(findButton(activity.window.decorView,
                        "Пропустить")).performClick())
                    assertNull("Skipped tutorial left a stale gold outline",
                        balances.background)
                    assertEquals(0f, balances.elevation, 0.01f)
                    assertFalse(balances.isSelected)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val nav = checkNotNull(findNav(activity.window.decorView))
                    assertNull("Outline returned after activity recreation",
                        nav.section(1)?.background)
                    // Re-enter the same tutorial without modifying settings.
                    val restart = MainActivity::class.java
                        .getDeclaredMethod("restartTutorial")
                    restart.isAccessible = true
                    restart.invoke(activity)
                    val balances = checkNotNull(nav.section(1))
                    assertNotNull("Restart must highlight again", balances.background)
                    assertTrue(balances.performClick())
                    assertNull("Going forward must remove old highlight",
                        balances.background)
                    assertTrue(checkNotNull(findButton(activity.window.decorView,
                        "‹ Назад")).performClick())
                    assertNotNull("Back to first step must highlight Balances",
                        balances.background)
                    assertTrue(checkNotNull(findButton(activity.window.decorView,
                        "Пропустить")).performClick())
                    assertNull("Skipping after rewind must clean nav background",
                        balances.background)
                    assertEquals(0f, balances.elevation, 0.01f)
                }
            }
        } finally {
            val editor = prefs.edit()
            if (oldPresent) editor.putBoolean("onboarding_completed", oldValue)
            else editor.remove("onboarding_completed")
            editor.commit()
        }
    }

    private fun allTextViews(view: View): List<TextView> {
        val views = mutableListOf<TextView>()
        if (view is TextView) views.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) views.addAll(allTextViews(view.getChildAt(i)))
        }
        return views
    }

    @Test fun bankAndMobilePillsShare24spAndDoNotEllipsizeLargeValues() {
        // Both paths use the same component; test the actual Android TextView.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pill = MainActivity::class.java.getDeclaredMethod(
                    "balanceAmountPill", String::class.java,
                    Boolean::class.javaPrimitiveType)
                pill.isAccessible = true
                val longText = "177 356,75 ₽"
                val mobile = pill.invoke(activity, longText, true) as TextView
                assertEquals(24f, mobile.textSize /
                    activity.resources.displayMetrics.scaledDensity, 0.05f)
                assertNull("A balance must never be ellipsized", mobile.ellipsize)
                assertEquals(activity.resources.displayMetrics.density * 10f,
                    mobile.paddingLeft.toFloat(), activity.resources.displayMetrics.density)

                val rowMethod = MainActivity::class.java.getDeclaredMethod(
                    "bankBalanceAccountRow", BankAccountBalance::class.java)
                rowMethod.isAccessible = true
                val row = rowMethod.invoke(activity,
                    BankAccountBalance(17_735_675L, "0479",
                        System.currentTimeMillis())) as LinearLayout
                val amounts = allTextViews(row).filter { it.text.contains("177") }
                assertEquals("Bank row should contain one balance", 1, amounts.size)
                assertEquals(24f, amounts.single().textSize /
                    activity.resources.displayMetrics.scaledDensity, 0.05f)
                assertNull(amounts.single().ellipsize)
            }
        }
    }
}
