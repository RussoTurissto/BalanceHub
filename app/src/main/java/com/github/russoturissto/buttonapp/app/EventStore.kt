package com.github.russoturissto.buttonapp.app

import android.annotation.SuppressLint
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class PlannedEvent(
    val id: String,
    val title: String,
    val details: String,
    val dueAt: Long,
    val leadMinutes: Int,
    val recurrence: String,
    val iconId: String? = null
)
internal data class ArchivedEvent(
    val archiveId: String,
    val event: PlannedEvent,
    val archivedAt: Long,
    val reason: String
)

/** Independent, offline event storage. Active/history transitions are atomic. */
@SuppressLint("UseKtx")
internal object EventStore {
    private const val PREFS = "balancehub_data"
    private const val ACTIVE = "events_active_v1"
    private const val HISTORY = "events_archive_v1"

    private fun decode(json: JSONObject): PlannedEvent? = try {
        val event = PlannedEvent(
            json.getString("id"), json.getString("title"),
            json.optString("details", ""), json.getLong("dueAt"),
            json.getInt("lead"), json.optString("repeat", "once"),
            json.optString("icon").takeIf { IconRegistry.find(it) != null }
        )
        event.takeIf { it.id.isNotBlank() && it.title.isNotBlank() &&
            it.dueAt > 0 && it.leadMinutes in 0..10_080 &&
            EventDateRules.validRecurrence(it.recurrence) }
    } catch (_: Exception) { null }

    private fun encode(event: PlannedEvent): JSONObject = JSONObject()
        .put("id", event.id).put("title", event.title).put("details", event.details)
        .put("dueAt", event.dueAt).put("lead", event.leadMinutes)
        .put("repeat", event.recurrence)
        .put("icon", event.iconId ?: JSONObject.NULL)

    private fun array(raw: String?): JSONArray = try { JSONArray(raw ?: "[]") }
        catch (_: Exception) { JSONArray() }

    fun active(context: Context): List<PlannedEvent> {
        val data = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = array(data.getString(ACTIVE, "[]"))
        return (0 until items.length()).mapNotNull { items.optJSONObject(it)?.let(::decode) }
            .sortedBy { it.dueAt }
    }

    fun archived(context: Context): List<ArchivedEvent> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = array(prefs.getString(HISTORY, "[]"))
        return (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            val event = decode(entry) ?: return@mapNotNull null
            ArchivedEvent(entry.optString("archiveId"), event,
                entry.optLong("archivedAt"), entry.optString("reason", "completed"))
        }.sortedByDescending { it.archivedAt }
    }

    @Synchronized
    fun save(context: Context, event: PlannedEvent): Boolean {
        if (event.title.isBlank() || event.id.isBlank() || event.dueAt <= 0 ||
            !EventDateRules.validRecurrence(event.recurrence) ||
            event.leadMinutes !in 0..10_080) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val kept = active(context).filterNot { it.id == event.id } + event
        val result = JSONArray()
        kept.forEach { result.put(encode(it)) }
        return prefs.edit().putString(ACTIVE, result.toString()).commit()
    }

    @Synchronized
    fun archive(context: Context, id: String, reason: String,
        nextForRepeated: Boolean = false): PlannedEvent? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val original = active(context)
        val selected = original.firstOrNull { it.id == id } ?: return null
        val now = System.currentTimeMillis()
        val next = if (nextForRepeated)
            EventDateRules.nextOccurrence(selected.dueAt, selected.recurrence, now)
        else null
        val kept = original.filterNot { it.id == id } +
            listOfNotNull(next?.let { selected.copy(dueAt = it) })
        val jsonActive = JSONArray().also { a -> kept.forEach { a.put(encode(it)) } }
        val jsonHistory = array(prefs.getString(HISTORY, "[]"))
        jsonHistory.put(encode(selected)
            .put("archiveId", UUID.randomUUID().toString())
            .put("archivedAt", now).put("reason", reason))
        return selected.takeIf {
            prefs.edit().putString(ACTIVE, jsonActive.toString())
                .putString(HISTORY, jsonHistory.toString()).commit()
        }
    }

    @Synchronized
    fun restore(context: Context, archiveId: String, dueAt: Long): PlannedEvent? {
        if (dueAt <= System.currentTimeMillis()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val history = array(prefs.getString(HISTORY, "[]"))
        val active = active(context)
        var selected: PlannedEvent? = null
        val remaining = JSONArray()
        for (i in 0 until history.length()) {
            val entry = history.optJSONObject(i) ?: continue
            if (entry.optString("archiveId") == archiveId && selected == null)
                selected = decode(entry)
            else remaining.put(entry)
        }
        val old = selected ?: return null
        val id = if (active.any { it.id == old.id }) UUID.randomUUID().toString()
            else old.id
        val item = old.copy(id = id, dueAt = dueAt)
        val updated = JSONArray().also { a ->
            (active + item).forEach { a.put(encode(it)) }
        }
        return item.takeIf {
            prefs.edit().putString(ACTIVE, updated.toString())
                .putString(HISTORY, remaining.toString()).commit()
        }
    }

    @Synchronized
    fun deleteArchived(context: Context, archiveId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val data = array(prefs.getString(HISTORY, "[]"))
        val result = JSONArray()
        var changed = false
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            if (item.optString("archiveId") == archiveId) changed = true
            else result.put(item)
        }
        return changed && prefs.edit().putString(HISTORY, result.toString()).commit()
    }

    /** Advance only an unchanged due timestamp; stale alarm broadcasts are harmless. */
    @Synchronized
    fun advance(context: Context, id: String, expectedDue: Long, now: Long):
        PlannedEvent? {
        val original = active(context)
        val old = original.firstOrNull { it.id == id && it.dueAt == expectedDue }
            ?: return null
        val next = EventDateRules.nextOccurrence(old.dueAt, old.recurrence, now)
            ?: return null
        val updated = old.copy(dueAt = next)
        return updated.takeIf { save(context, updated) }
    }

    @Synchronized
    fun refreshRepetitions(context: Context, now: Long = System.currentTimeMillis()) {
        active(context).filter { it.dueAt <= now && it.recurrence != "once" }
            .forEach { advance(context, it.id, it.dueAt, now) }
    }
}
