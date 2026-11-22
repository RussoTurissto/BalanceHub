package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Engineering sessions use a scoped fake preferences namespace, never user data. */
@RunWith(AndroidJUnit4::class)
class BankPushForegroundProbeEmulatorTest {
    @Test fun foregroundShadeAndBackgroundSessionsAreIsolatedByBank() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "foreground-probe-test-" + System.nanoTime()
        val isolated = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                target.getSharedPreferences("$key-$name", mode)
        }
        try {
            BankPushForegroundProbe.begin(isolated, "sber",
                BankPushForegroundProbe.Mode.OPEN)
            assertNull(BankPushForegroundProbe.snapshot(isolated, "vtb"))
            assertFalse(BankPushForegroundProbe.arrival(isolated, "vtb",
                false, System.currentTimeMillis()))
            assertTrue(BankPushForegroundProbe.arrival(isolated, "sber",
                false, System.currentTimeMillis()))
            BankPushForegroundProbe.outcome(isolated, "sber", "Баланс обновлён")
            val open = BankPushForegroundProbe.snapshot(isolated, "sber")!!
            assertEquals(1, open.callbacks)
            assertEquals(0, open.recovered)
            assertEquals("Баланс обновлён", open.lastOutcome)

            BankPushForegroundProbe.begin(isolated, "sber",
                BankPushForegroundProbe.Mode.SHADE)
            val start = BankPushForegroundProbe.snapshot(isolated, "sber")!!.startedAt
            assertFalse(BankPushForegroundProbe.arrival(isolated, "sber",
                true, start - 60_000L))
            assertTrue(BankPushForegroundProbe.arrival(isolated, "sber",
                true, start + 1))
            assertEquals(1, BankPushForegroundProbe.snapshot(isolated, "sber")!!.recovered)

            BankPushForegroundProbe.begin(isolated, "sber",
                BankPushForegroundProbe.Mode.BACKGROUND)
            assertEquals(0, BankPushForegroundProbe.snapshot(isolated, "sber")!!.callbacks)
            assertEquals(BankPushForegroundProbe.Mode.BACKGROUND,
                BankPushForegroundProbe.snapshot(isolated, "sber")!!.mode)
        } finally {
            target.getSharedPreferences("$key-balancehub_bank_foreground_probe_v1",
                Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
