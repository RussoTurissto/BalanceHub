package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/**
 * Persist only event timestamp, field-availability flags and outcome code.
 * No notification text, transaction history, counterparties or monetary sums.
 */
internal object SberPushDiagnostics {
    enum class Outcome {
        UPDATED, NO_BALANCE, AMBIGUOUS_BALANCE, AMBIGUOUS_ACCOUNT,
        INVALID_BALANCE, OLD_NOTIFICATION, SAVE_FAILED, EMPTY_NOTIFICATION,
        TITLE_ONLY
    }

    data class Latest(
        val time: Long,
        val outcome: Outcome,
        val fieldsMask: Int,
        val lastSuccessAt: Long?
    )

    private val lock = Any()
    private fun file(context: Context) =
        AtomicFile(File(context.noBackupFilesDir, "sber_push_diagnostic.json"))

    fun latest(context: Context): Latest? = synchronized(lock) {
        val json = try {
            file(context).openRead().bufferedReader().use { JSONObject(it.readText()) }
        } catch (_: Exception) {
            return@synchronized null
        }
        val code = try {
            Outcome.valueOf(json.optString("outcome"))
        } catch (_: IllegalArgumentException) {
            return@synchronized null
        }
        val time = json.optLong("time", 0L)
        val mask = json.optInt("fields", 0)
        val success = json.optLong("lastSuccess", 0L).takeIf { it > 0L }
        if (time > 0L) Latest(time, code, mask, success) else null
    }

    fun record(context: Context, time: Long, outcome: Outcome, fieldsMask: Int) =
        synchronized(lock) {
            if (!SberBalanceStore.isEnabled(context) || time <= 0L ||
                time > System.currentTimeMillis() + 60_000L) return@synchronized
            val existing = latest(context)
            if (existing != null && time < existing.time) return@synchronized
            // Do not overwrite a useful result with an empty repost of the
            // same notification (same Android postTime).
            if (existing != null && time == existing.time &&
                rank(existing.outcome) > rank(outcome)) return@synchronized
            val success = if (outcome == Outcome.UPDATED) time
                else existing?.lastSuccessAt
            val json = JSONObject().put("time", time)
                .put("outcome", outcome.name)
                .put("fields", fieldsMask)
                .put("lastSuccess", success ?: JSONObject.NULL)
            val target = file(context)
            var stream: java.io.FileOutputStream? = null
            try {
                stream = target.startWrite()
                stream.write(json.toString().toByteArray(Charsets.UTF_8))
                target.finishWrite(stream)
            } catch (_: Exception) {
                if (stream != null) target.failWrite(stream)
            }
        }

    private fun rank(outcome: Outcome): Int = when (outcome) {
        Outcome.UPDATED -> 5
        Outcome.NO_BALANCE, Outcome.AMBIGUOUS_BALANCE,
        Outcome.AMBIGUOUS_ACCOUNT, Outcome.INVALID_BALANCE -> 4
        Outcome.OLD_NOTIFICATION, Outcome.SAVE_FAILED -> 3
        Outcome.TITLE_ONLY -> 2
        Outcome.EMPTY_NOTIFICATION -> 1
    }

    fun clear(context: Context) = synchronized(lock) { file(context).delete() }
}
