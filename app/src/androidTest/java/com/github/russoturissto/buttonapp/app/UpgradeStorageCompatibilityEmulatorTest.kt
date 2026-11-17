package com.github.russoturissto.buttonapp.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** Compatibility smoke tests using the exact v1.2.1 persisted JSON keys. */
@RunWith(AndroidJUnit4::class)
class UpgradeStorageCompatibilityEmulatorTest {
    private fun future(days: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, days)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun legacyPaymentAndEventSnapshotsRemainReadableAndUnmodified() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("balancehub_data", 0)
        val oldPayments = prefs.getString("payment_reminders", null)
        val oldEvents = prefs.getString("events_active_v1", null)
        try {
            val p = JSONObject().put("id", "legacy-upgrade-payment")
                .put("title", "Стандартный платёж")
                .put("amount", "100.50").put("currency", "RUB")
                .put("dueAt", future(15)).put("leadDays", 3)
                .put("recurrence", "monthly")
            val e = JSONObject().put("id", "legacy-upgrade-event")
                .put("title", "Событие").put("details", "")
                .put("dueAt", future(8)).put("lead", 60)
                .put("repeat", "weekly")
            val legacyPayments = JSONArray().put(p).toString()
            val legacyEvents = JSONArray().put(e).toString()
            assertTrue(prefs.edit().putString("payment_reminders", legacyPayments)
                .putString("events_active_v1", legacyEvents).commit())
            val loaded = PaymentArchiveStore.activeSnapshot(context)
            assertEquals("legacy-upgrade-payment",
                PaymentReminderCodec.decode(loaded!!.getJSONObject(0))?.id)
            assertEquals("legacy-upgrade-event", EventStore.active(context).single().id)
            assertEquals(legacyPayments, prefs.getString("payment_reminders", null))
            assertEquals(legacyEvents, prefs.getString("events_active_v1", null))
        } finally {
            val edit = prefs.edit()
            if (oldPayments == null) edit.remove("payment_reminders")
            else edit.putString("payment_reminders", oldPayments)
            if (oldEvents == null) edit.remove("events_active_v1")
            else edit.putString("events_active_v1", oldEvents)
            edit.commit()
        }
    }

    @Test fun archiveRestorationCreatesIndependentCardWithoutRemovingOriginal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("balancehub_data", 0)
        val oldPayments = prefs.getString("payment_reminders", null)
        val oldArchive = prefs.getString("payment_archive", null)
        try {
            val archived = JSONObject().put("id", "archived-original-upgrade")
                .put("title", "Архивный платёж").put("amount", "50")
                .put("currency", "RUB").put("dueAt", future(-5))
                .put("leadDays", 2).put("recurrence", "once")
                .put("archiveReason", "deleted").put("archivedAt", future(-2))
            assertTrue(prefs.edit().putString("payment_reminders", "[]")
                .putString("payment_archive", JSONArray().put(archived).toString()).commit())
            val restored = PaymentArchiveStore.restore(context,
                "archived-original-upgrade", future(12))
            assertNotNull(restored)
            assertNotEquals("archived-original-upgrade", restored!!.id)
            val active = PaymentArchiveStore.activeSnapshot(context)!!
            assertEquals(restored.id,
                PaymentReminderCodec.decode(active.getJSONObject(0))?.id)
            assertEquals("archived-original-upgrade",
                PaymentArchiveStore.archived(context).single().id)
        } finally {
            val edit = prefs.edit()
            if (oldPayments == null) edit.remove("payment_reminders")
            else edit.putString("payment_reminders", oldPayments)
            if (oldArchive == null) edit.remove("payment_archive")
            else edit.putString("payment_archive", oldArchive)
            edit.commit()
        }
    }
}
