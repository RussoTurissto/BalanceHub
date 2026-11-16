package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A temporary isolated app context keeps user balances untouched by tests. */
@RunWith(AndroidJUnit4::class)
class BankAccountVisibilityEmulatorTest {
    @Test fun hiddenAccountsContinueToReceiveAndRetainBalancesForAllThreeBanks() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = "0479"
        val marker = System.nanoTime().toString()
        val directory = File(target.cacheDir, "bank-account-test-$marker")
        directory.mkdirs()
        val touched = mutableListOf<String>()
        val isolated = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val scoped = "test-$marker-$name"
                if (!touched.contains(scoped)) touched.add(scoped)
                return target.getSharedPreferences(scoped, mode)
            }
        }
        try {
            SberBalanceStore.setEnabled(isolated, true)
            for (id in listOf("vtb", "tbank")) {
                BankCardPreferences.setEnabled(isolated, id, true)
            }
            val t0 = System.currentTimeMillis() - 20_000L
            val t1 = t0 + 1_000L
            val initialSber = SberPushParser.Result(
                10_500L, suffix, SberPushParser.Confidence.EXPLICIT)
            assertTrue(SberBalanceStore.save(isolated, initialSber, t0))
            for (id in listOf("vtb", "tbank")) {
                val bank = RussianBankCatalog.byId(id)!!
                assertTrue(RussianBankBalanceStore.save(isolated, bank,
                    RussianBankPushParser.Result(10_500L, suffix), t0))
            }
            for (id in listOf("sber", "vtb", "tbank")) {
                assertTrue(BankAccountVisibility.visible(isolated, id, suffix))
                BankAccountVisibility.setVisible(isolated, id, suffix, false)
                assertFalse(BankAccountVisibility.visible(isolated, id, suffix))
            }
            // Visibility is only a presentation flag and must not gate storage.
            assertTrue(SberBalanceStore.save(isolated,
                SberPushParser.Result(0L, suffix, SberPushParser.Confidence.EXPLICIT), t1))
            assertEquals(0L, SberBalanceStore.all(isolated)
                .first { it.lastFour == suffix }.kopecks)
            assertTrue(SberBalanceStore.save(isolated,
                SberPushParser.Result(55_000L, "8634",
                    SberPushParser.Confidence.EXPLICIT), t1 + 1_000L))
            assertEquals(2, SberBalanceStore.all(isolated).size)
            for (id in listOf("vtb", "tbank")) {
                val bank = RussianBankCatalog.byId(id)!!
                assertTrue(RussianBankBalanceStore.save(isolated, bank,
                    RussianBankPushParser.Result(0L, suffix), t1))
                assertEquals(0L, RussianBankBalanceStore.all(isolated, id)
                    .first { it.lastFour == suffix }.kopecks)
                // An independent second card must remain in storage.
                assertTrue(RussianBankBalanceStore.save(isolated, bank,
                    RussianBankPushParser.Result(55_000L, "8634"), t1 + 1_000L))
                assertEquals(2, RussianBankBalanceStore.all(isolated, id).size)
            }
            for (id in listOf("sber", "vtb", "tbank")) {
                assertFalse(BankAccountVisibility.visible(isolated, id, suffix))
                BankAccountVisibility.setVisible(isolated, id, suffix, true)
                assertTrue(BankAccountVisibility.visible(isolated, id, suffix))
            }
        } finally {
            touched.forEach { name ->
                target.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                    .clear().commit()
            }
            directory.deleteRecursively()
        }
    }
}
