package com.github.russoturissto.buttonapp.app

import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An actual launch smoke test; do not use obsolete template view IDs. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val rule = activityScenarioRule<MainActivity>()

    @Test fun appOpensWithoutCrashing() {
        rule.scenario.onActivity { activity ->
            assertFalse(activity.isFinishing)
            assertNotNull(activity.window.decorView)
        }
    }
}
