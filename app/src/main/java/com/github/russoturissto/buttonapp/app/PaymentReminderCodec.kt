package com.github.russoturissto.buttonapp.app

import org.json.JSONArray
import org.json.JSONObject

/** Keep each user's actual payment edit distinct from background archiving. */
internal object PaymentActiveMergePolicy {
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
        val changed = after.filter { (id, item) -> before[id] != item.toString() }
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
            val replacement = changed.remove(id)
            // Never restore something already moved to the archive.
            if (replacement != null) {
                if (id !in archived) merged.put(replacement)
            } else merged.put(item)
        }
        for ((id, item) in changed) {
            if (id !in archived) merged.put(item)
        }
        return merged
    }
}

internal data class PaymentReminder(
    val id: String, val title: String, val amount: String, val currency: String,
    val dueAt: Long, val leadDays: Int, val recurrence: String = "once",
    val iconId: String? = null
)

/** Fault-isolated JSON codec: one bad legacy record cannot crash the activity. */
internal object PaymentReminderCodec {
    fun decode(record: JSONObject): PaymentReminder? = try {
        val id = record.getString("id").takeIf { it.isNotBlank() } ?: return null
        val title = record.getString("title").takeIf { it.isNotBlank() } ?: return null
        val dueAt = record.getLong("dueAt").takeIf { it > 0L } ?: return null
        val lead = record.getInt("leadDays").takeIf { it in 0..365 } ?: return null
        val recurrence = record.optString("recurrence", "once")
            .takeIf { it in setOf("once", "weekly", "monthly") } ?: return null
        PaymentReminder(id, title, record.getString("amount"),
            record.getString("currency"), dueAt, lead, recurrence,
            record.optString("icon").takeIf { IconRegistry.find(it) != null })
    } catch (_: Exception) {
        null
    }

    fun encode(payment: PaymentReminder): JSONObject = JSONObject()
        .put("id", payment.id).put("title", payment.title)
        .put("amount", payment.amount).put("currency", payment.currency)
        .put("dueAt", payment.dueAt).put("leadDays", payment.leadDays)
        .put("recurrence", payment.recurrence)
        .put("icon", payment.iconId ?: JSONObject.NULL)
}
