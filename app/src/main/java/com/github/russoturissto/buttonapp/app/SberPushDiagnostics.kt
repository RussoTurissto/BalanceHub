package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Bounded metadata only: no text, banking amount, card number or sender is stored. */
internal object SberPushDiagnostics {
    enum class Outcome {
        UPDATED, NO_BALANCE, AMBIGUOUS_BALANCE, AMBIGUOUS_ACCOUNT,
        INVALID_BALANCE, OLD_NOTIFICATION, SAVE_FAILED, EMPTY_NOTIFICATION,
        TITLE_ONLY, SYSTEM_REDACTED, POSSIBLY_REDACTED, IGNORED_GROUP, IGNORED_ONGOING
    }

    data class Latest(
        val time: Long,
        val outcome: Outcome,
        val fieldsMask: Int,
        val lastSuccessAt: Long?,
        val rubleFieldsMask: Int,
        val cueFieldsMask: Int,
        val accountFieldsMask: Int,
        val deliveryState: Int,
        val receivedAt: Long = time,
        val notificationId: Int = 0,
        val privacy: SberNotificationPrivacy.Evidence =
            SberNotificationPrivacy.Evidence.NONE,
        val lengths: List<Int> = emptyList(),
        val remoteFlags: Int = 0
    )

    private val lock = Any()
    private fun storage(context: Context) =
        AtomicFile(File(context.noBackupFilesDir, "sber_push_diagnostic.json"))
    private fun readJson(context: Context): JSONObject? = try {
        storage(context).openRead().bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText())
        }
    } catch (_: Exception) { null }

    private fun decode(o: JSONObject, success: Long?): Latest? {
        val time = o.optLong("time", 0)
        if (time <= 0) return null
        val outcome = try { Outcome.valueOf(o.optString("outcome")) }
            catch (_: IllegalArgumentException) { return null }
        val privacy = try {
            SberNotificationPrivacy.Evidence.valueOf(o.optString("privacy", "NONE"))
        } catch (_: IllegalArgumentException) { SberNotificationPrivacy.Evidence.NONE }
        val arr = o.optJSONArray("lengths")
        val lengths = if (arr == null) emptyList() else
            (0 until minOf(arr.length(), SberNotificationText.Field.entries.size))
                .map { arr.optInt(it, 0).coerceIn(0, 8192) }
        return Latest(time, outcome, o.optInt("fields"), success, o.optInt("rubles"),
            o.optInt("cues"), o.optInt("account"), o.optInt("state", -1),
            o.optLong("received", time), o.optInt("nid"), privacy, lengths,
            o.optInt("remote"))
    }

    private fun read(context: Context): Pair<List<Latest>, Long?> {
        val obj = readJson(context) ?: return emptyList<Latest>() to null
        val success = obj.optLong("lastSuccess", 0).takeIf { it > 0 }
        val array = obj.optJSONArray("events")
        if (array == null) return listOfNotNull(decode(obj, success)) to success
        return (0 until minOf(array.length(), 6)).mapNotNull {
            array.optJSONObject(it)?.let { item -> decode(item, success) }
        } to success
    }

    fun recent(context: Context): List<Latest> = synchronized(lock) { read(context).first }
    fun latest(context: Context): Latest? = synchronized(lock) {
        read(context).first.firstOrNull()
    }

    fun record(
        context: Context, time: Long, outcome: Outcome, fieldsMask: Int,
        rublesMask: Int, cuesMask: Int, accountMask: Int, deliveryState: Int,
        notificationId: Int, privacy: SberNotificationPrivacy.Evidence,
        lengths: List<Int>, remoteFlags: Int
    ) = synchronized(lock) {
        val now = System.currentTimeMillis()
        if (!SberBalanceStore.isEnabled(context) || time <= 0 ||
            time > now + 60_000L) return@synchronized
        val (previous, lastSuccess) = read(context)
        val previousSame = previous.firstOrNull {
            it.time == time && it.notificationId == notificationId
        }
        val candidate = Latest(time, outcome, fieldsMask, lastSuccess, rublesMask,
            cuesMask, accountMask, deliveryState, now, notificationId, privacy,
            lengths.take(SberNotificationText.Field.entries.size), remoteFlags)
        val events = if (previousSame != null &&
            rank(previousSame.outcome) > rank(outcome)) previous else
            listOf(candidate) + previous.filterNot {
                it.time == time && it.notificationId == notificationId
            }
        val success = if (outcome == Outcome.UPDATED) time else lastSuccess
        val output = JSONObject().put("lastSuccess", success ?: JSONObject.NULL)
        val array = JSONArray()
        events.take(6).forEach { e ->
            val sizes = JSONArray()
            e.lengths.forEach { sizes.put(it) }
            array.put(JSONObject().put("time", e.time).put("received", e.receivedAt)
                .put("nid", e.notificationId).put("outcome", e.outcome.name)
                .put("fields", e.fieldsMask).put("rubles", e.rubleFieldsMask)
                .put("cues", e.cueFieldsMask).put("account", e.accountFieldsMask)
                .put("state", e.deliveryState).put("privacy", e.privacy.name)
                .put("lengths", sizes).put("remote", e.remoteFlags))
        }
        output.put("events", array)
        val f = storage(context)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = f.startWrite()
            stream.write(output.toString().toByteArray(Charsets.UTF_8))
            f.finishWrite(stream)
        } catch (_: Exception) {
            if (stream != null) f.failWrite(stream)
        }
    }

    private fun rank(o: Outcome): Int = when (o) {
        Outcome.UPDATED -> 7
        Outcome.SYSTEM_REDACTED -> 6
        Outcome.AMBIGUOUS_BALANCE, Outcome.AMBIGUOUS_ACCOUNT,
        Outcome.INVALID_BALANCE -> 5
        Outcome.POSSIBLY_REDACTED, Outcome.NO_BALANCE -> 4
        Outcome.OLD_NOTIFICATION, Outcome.SAVE_FAILED -> 3
        Outcome.TITLE_ONLY -> 2
        Outcome.EMPTY_NOTIFICATION, Outcome.IGNORED_GROUP,
        Outcome.IGNORED_ONGOING -> 1
    }
    fun clear(context: Context) = synchronized(lock) { storage(context).delete() }
}
