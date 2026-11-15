package com.github.russoturissto.buttonapp.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentActiveMergePolicyTest {
    private fun payment(id: String, due: Long = 1_800_000_000_000L) =
        JSONObject().put("id", id).put("title", "Оплата")
            .put("amount", "100").put("currency", "RUB")
            .put("dueAt", due).put("leadDays", 1)
            .put("recurrence", "monthly").put("icon", JSONObject.NULL)

    private fun baseline(vararg records: JSONObject) =
        records.associate { it.getString("id") to it.toString() }

    @Test fun uiEditDoesNotResurrectArchivedPayment() {
        val a = payment("a")
        val b = payment("b")
        val result = PaymentActiveMergePolicy.merge(
            JSONArray(), JSONArray().put(a), baseline(a, b),
            JSONArray().put(a).put(b))
        assertNotNull(result)
        assertEquals(1, result!!.length())
        assertEquals("b", result.getJSONObject(0).getString("id"))
    }

    @Test fun uiChangePreservesUnrelatedBackgroundUpdates() {
        val a = payment("a")
        val b = payment("b")
        val bNew = payment("b", 1_900_000_000_000L)
        val edited = payment("a").put("amount", "200")
        val result = PaymentActiveMergePolicy.merge(
            JSONArray().put(a).put(bNew), JSONArray(), baseline(a, b),
            JSONArray().put(edited).put(b))
        assertNotNull(result)
        assertEquals("200", result!!.getJSONObject(0).getString("amount"))
        assertEquals(bNew.getLong("dueAt"), result.getJSONObject(1).getLong("dueAt"))
    }

    @Test fun malformedRowIsPreservedAndOneBadRecordIsIsolated() {
        val intact = payment("ok")
        val broken = JSONObject().put("id", "broken").put("title", JSONObject.NULL)
        val result = PaymentActiveMergePolicy.merge(
            JSONArray().put(broken).put(intact), JSONArray(),
            baseline(intact), JSONArray().put(intact))
        assertNotNull(result)
        assertEquals(broken.toString(), result!!.getJSONObject(0).toString())
        assertNull(PaymentReminderCodec.decode(broken))
        assertNotNull(PaymentReminderCodec.decode(intact))
    }

    @Test fun malformedOrDuplicatedMutationsNeverOverwriteCurrentState() {
        val a = payment("a")
        assertNull(PaymentActiveMergePolicy.merge(
            JSONArray().put(a), JSONArray(), baseline(a),
            JSONArray().put(a).put(a)))
        assertNull(PaymentActiveMergePolicy.merge(
            JSONArray().put(a).put(a), JSONArray(), baseline(a),
            JSONArray().put(payment("a").put("amount", "300"))))
        assertFalse(PaymentReminderCodec.decode(
            JSONObject().put("id", "x").put("title", "Bad")) != null)
        assertTrue(PaymentReminderCodec.decode(a)!!.id == "a")
    }
}
