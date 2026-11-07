package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/**
 * Stores only a timestamp and outcome code. Notification text, counterparties,
 * transaction amounts and message titles are never retained or logged.
 */
internal object SberPushDiagnostics {
    enum class Outcome {
        UPDATED, NO_BALANCE, AMBIGUOUS_BALANCE, AMBIGUOUS_ACCOUNT,
        INVALID_BALANCE, OLD_NOTIFICATION, SAVE_FAILED, EMPTY_NOTIFICATION
    }
    data class Latest(val time: Long, val outcome: Outcome)

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
        if (time > 0L) Latest(time, code) else null
    }

    fun record(context: Context, time: Long, outcome: Outcome) = synchronized(lock) {
        if (!SberBalanceStore.isEnabled(context) || time <= 0L ||
            time > System.currentTimeMillis() + 60_000L) return@synchronized
        if ((latest(context)?.time ?: -1L) > time) return@synchronized
        val value = JSONObject().put("time", time).put("outcome", outcome.name)
        val target = file(context)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = target.startWrite()
            stream.write(value.toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (_: Exception) {
            if (stream != null) target.failWrite(stream)
        }
    }

    fun clear(context: Context) = synchronized(lock) { file(context).delete() }
}
