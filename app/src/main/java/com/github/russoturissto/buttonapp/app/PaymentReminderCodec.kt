package com.github.russoturissto.buttonapp.app

import org.json.JSONArray
import org.json.JSONObject

/** Keep each user's actual payment edit distinct from background archiving. */
internal object PaymentActiveMergePolicy {
    /**
     * Apply the fields the user actually edited, not an entire stale UI record.
     * Background date advancement and unknown/legacy fields must survive.
     */
    private fun applyUiFields(
        current: JSONObject, originalJson: String, proposed: JSONObject
    ): JSONObject? {
        val original = try { JSONObject(originalJson) } catch (_: Exception) {
            return null
        }
        val result = JSONObject(current.toString())
        val keys = linkedSetOf<String>()
        val oldKeys = original.keys()
        while (oldKeys.hasNext()) keys.add(oldKeys.next())
        val newKeys = proposed.keys()
        while (newKeys.hasNext()) keys.add(newKeys.next())
        for (key in keys) {
            if (key == "id") continue
            val beforeHas = original.has(key)
            val afterHas = proposed.has(key)
            val beforeValue = original.opt(key)
            val afterValue = proposed.opt(key)
            // Comparing JSON values, not serialized object key order, avoids
            // accidentally restoring unrelated values from a stale screen.
            val equal = if (beforeValue === JSONObject.NULL ||
                afterValue === JSONObject.NULL) {
                beforeValue === afterValue
            } else beforeValue == afterValue
            if (beforeHas != afterHas || !equal) {
                if (afterHas) result.put(key, afterValue)
                else result.remove(key)
            }
        }
        return result
    }

    fun merge(
        current: JSONArray,
        archive: JSONArray,
        before: Map<String, String>,
        proposed: JSONArray
    ): JSONArray? {
        val after = linkedMapOf<String, JSONObject>()
        for (i in 0 until proposed.length()) {
            val record = proposed.optJSONObject(i) ?: return null
            val id = record.optString("id")
            if (id.isBlank() || after.put(id, record) != null) return null
        }
        val archived = mutableSetOf<String>()
        for (i in 0 until archive.length()) {
            val record = archive.optJSONObject(i) ?: return null
            record.optString("id").takeIf { it.isNotBlank() }?.let(archived::add)
        }
        val deleted = before.keys - after.keys
        val modified = after.filter { (id, item) -> before[id] != item.toString() }
            .toMutableMap()
        val merged = JSONArray()
        val seen = mutableSetOf<String>()
        for (i in 0 until current.length()) {
            val item = current.optJSONObject(i)
            if (item == null) {
                // Preserve unreadable/unknown records for subsequent recovery.
                merged.put(current.opt(i))
                continue
            }
            val id = item.optString("id")
            if (id.isBlank()) {
                merged.put(item)
                continue
            }
            if (!seen.add(id)) return null
            if (id in deleted) continue
            val edit = modified.remove(id)
            if (edit == null) {
                merged.put(item)
                continue
            }
            // A concurrent record with the same ID is not necessarily ours.
            // Never overwrite it, or revive a record already archived.
            if (id in archived) return null
            val baselineJson = before[id] ?: return null
            val combined = applyUiFields(item, baselineJson, edit) ?: return null
            merged.put(combined)
        }
        for ((id, item) in modified) {
            // A stale UI cannot re-add a removed/archived record. Only genuinely
            // new IDs are appended; concurrent additions are never overwritten.
            if (id !in archived && id !in before) merged.put(item)
        }
        return merged
    }
}

internal data class PaymentReminder(
    val id: String, val title: String, val amount: String, val currency: String,
    val dueAt: Long, val leadDays: Int, val recurrence: String = "once",
    val iconId: String? = null,
    val plan: PaymentPlan? = null
)

/** Fault-isolated JSON codec: one bad legacy record cannot crash the activity. */
internal object PaymentReminderCodec {
    fun decode(record: JSONObject): PaymentReminder? {
        return try {
            // Android JSONObject may stringify JSON null as "null".
            if (listOf("id", "title", "amount", "currency", "dueAt", "leadDays")
                    .any { !record.has(it) || record.isNull(it) }) return null
            val id = record.getString("id").takeIf { it.isNotBlank() } ?: return null
            val title = record.getString("title").takeIf { it.isNotBlank() } ?: return null
            val dueAt = record.getLong("dueAt").takeIf { it > 0L } ?: return null
            val lead = record.getInt("leadDays").takeIf { it in 0..365 } ?: return null
            val recurrence = record.optString("recurrence", "once")
                .takeIf { it in setOf("once", "weekly", "monthly", "custom") } ?: return null
            PaymentReminder(id, title, record.getString("amount"),
                record.getString("currency"), dueAt, lead, recurrence,
                record.optString("icon").takeIf { IconRegistry.find(it) != null },
                PaymentPlanCodec.decode(record.optJSONObject("scheduleV2")))
                .takeIf { recurrence != "custom" || it.plan != null }
        } catch (_: Exception) {
            null
        }
    }

    fun encode(payment: PaymentReminder): JSONObject = JSONObject()
        .put("id", payment.id).put("title", payment.title)
        .put("amount", payment.amount).put("currency", payment.currency)
        .put("dueAt", payment.dueAt).put("leadDays", payment.leadDays)
        .put("recurrence", payment.recurrence)
        .put("icon", payment.iconId ?: JSONObject.NULL)
        .also { if (payment.plan != null) it.put("scheduleV2", PaymentPlanCodec.encode(payment.plan)) }
}
