package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Per-bank Push balances; Sber's existing balance store is intentionally untouched. */
internal object RussianBankBalanceStore {
    data class Snapshot(
        val kopecks: Long, val lastFour: String?, val observedAt: Long
    )
    private val lock = Any()
    private fun file(context: Context): AtomicFile =
        AtomicFile(File(context.noBackupFilesDir, "russian_bank_balances.json"))

    private fun read(context: Context): Map<String, List<Snapshot>> {
        val json = try {
            JSONObject(file(context).openRead().bufferedReader().use { it.readText() })
        } catch (_: Exception) { return emptyMap() }
        val result = mutableMapOf<String, List<Snapshot>>()
        for (bank in RussianBankCatalog.banks) {
            if (bank.id == "sber") continue
            val values = json.optJSONArray(bank.id) ?: continue
            result[bank.id] = (0 until values.length()).mapNotNull { i ->
                val item = values.optJSONObject(i) ?: return@mapNotNull null
                val amount = item.optLong("value", -1L)
                val at = item.optLong("at", -1L)
                val suffix = item.optString("suffix", "").takeIf {
                    it.matches(Regex("\\d{4}"))
                }
                if (amount < 0 || at <= 0) null else Snapshot(amount, suffix, at)
            }.sortedByDescending { it.observedAt }.take(12)
        }
        return result
    }

    private fun write(context: Context, snapshots: Map<String, List<Snapshot>>) {
        val json = JSONObject()
        snapshots.forEach { (id, values) ->
            val array = JSONArray()
            values.take(12).forEach {
                array.put(JSONObject().put("value", it.kopecks).put("at", it.observedAt)
                    .put("suffix", it.lastFour ?: JSONObject.NULL))
            }
            json.put(id, array)
        }
        val af = file(context)
        val output = af.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            af.finishWrite(output)
        } catch (e: Exception) {
            af.failWrite(output)
            throw e
        }
    }
    fun latest(context: Context, id: String): Snapshot? = synchronized(lock) {
        read(context)[id]?.maxByOrNull { it.observedAt }
    }
    fun save(context: Context, bank: RussianBank,
        result: RussianBankPushParser.Result, received: Long): Boolean = synchronized(lock) {
        if (bank.id == "sber" || !BankCardPreferences.enabled(context, bank.id) ||
            received <= 0 || received > System.currentTimeMillis() + 60_000) return@synchronized false
        val data = read(context).toMutableMap()
        val previous = data[bank.id].orEmpty()
        if (previous.any { it.lastFour == result.lastFour && it.observedAt >= received })
            return@synchronized false
        data[bank.id] = (listOf(Snapshot(result.kopecks, result.lastFour, received)) +
            previous.filterNot { it.lastFour == result.lastFour })
            .sortedByDescending { it.observedAt }.take(12)
        try { write(context, data); true } catch (_: Exception) { false }
    }
    fun clear(context: Context, id: String) = synchronized(lock) {
        val data = read(context).toMutableMap()
        if (data.remove(id) != null) try { write(context, data) } catch (_: Exception) {}
    }
}

internal object RussianBankDiagnostics {
    private fun prefs(context: Context) =
        context.getSharedPreferences("balancehub_ru_bank_diagnostics_v1", Context.MODE_PRIVATE)
    fun record(context: Context, bank: RussianBank, status: String) {
        // Metadata only: no notification text, amount, account or sender.
        prefs(context).edit().putString("status_" + bank.id, status)
            .putLong("time_" + bank.id, System.currentTimeMillis()).apply()
    }
    fun last(context: Context, bank: RussianBank): String? =
        prefs(context).getString("status_" + bank.id, null)
}
