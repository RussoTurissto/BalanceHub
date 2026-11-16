package com.github.russoturissto.buttonapp.app

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Launch-time regression for fresh install, APK upgrade and ordinary restart. */
@RunWith(AndroidJUnit4::class)
class UpcomingSectionsVersionEmulatorTest {
    @Test fun listsCollapseOnlyWhenInstalledVersionChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val keys = listOf("payments_list_expanded", "event_list_expanded",
            "upcoming_lists_last_seen_code")
        val old = keys.associateWith { key -> prefs.all[key] }
        val code = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0))
        try {
            // Fresh install: no version marker despite saved temporary UI state.
            assertTrue(prefs.edit()
                .putBoolean("payments_list_expanded", true)
                .putBoolean("event_list_expanded", true)
                .remove("upcoming_lists_last_seen_code").commit())
            ActivityScenario.launch(MainActivity::class.java).use {
                assertFalse(prefs.getBoolean("payments_list_expanded", true))
                assertFalse(prefs.getBoolean("event_list_expanded", true))
                assertEquals(code, prefs.getLong("upcoming_lists_last_seen_code", -1L))
            }

            // Upgrade: old version marker exists and both lists were expanded.
            assertTrue(prefs.edit()
                .putBoolean("payments_list_expanded", true)
                .putBoolean("event_list_expanded", true)
                .putLong("upcoming_lists_last_seen_code", code - 1).commit())
            ActivityScenario.launch(MainActivity::class.java).use {
                assertFalse(prefs.getBoolean("payments_list_expanded", true))
                assertFalse(prefs.getBoolean("event_list_expanded", true))
                assertEquals(code, prefs.getLong("upcoming_lists_last_seen_code", -1L))
            }

            // Same APK: user-selected state must survive ordinary process start.
            assertTrue(prefs.edit()
                .putBoolean("payments_list_expanded", true)
                .putBoolean("event_list_expanded", true)
                .putLong("upcoming_lists_last_seen_code", code).commit())
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTrue(prefs.getBoolean("payments_list_expanded", false))
                assertTrue(prefs.getBoolean("event_list_expanded", false))
                assertEquals(code, prefs.getLong("upcoming_lists_last_seen_code", -1L))
            }
        } finally {
            val editor = prefs.edit()
            old.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    else -> editor.remove(key)
                }
            }
            editor.commit()
        }
    }
}
