package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

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
        for ((id, values) in snapshots) {
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

/**
 * Bounded, per-bank metadata diagnostics. No source message text, amount,
 * account suffix or sender is persisted. Sber has an independent store.
 */
internal object RussianBankDiagnostics {
    data class Event(
        val receivedAt: Long,
        val status: String,
        val fieldsMask: Int,
        val rubleFieldsMask: Int,
        val cueFieldsMask: Int,
        val accountFieldsMask: Int,
        val fieldLengths: List<Int>,
        val deliveryState: Int,
        val privacy: String,
        val fingerprint: String = ""
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences("balancehub_ru_bank_diagnostics_v1", Context.MODE_PRIVATE)

    private fun allowed(bank: RussianBank) = bank.id == "vtb" || bank.id == "tbank"

    @Synchronized
    fun recent(context: Context, bank: RussianBank): List<Event> {
        if (!allowed(bank)) return emptyList()
        val raw = prefs(context).getString("events_" + bank.id, "[]") ?: "[]"
        val array = try { JSONArray(raw) } catch (_: Exception) { return emptyList() }
        return (0 until minOf(array.length(), 6)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val at = item.optLong("at", 0L)
            if (at <= 0L) return@mapNotNull null
            val lengths = item.optJSONArray("lengths")
            Event(
                at,
                item.optString("status", "Неизвестно").take(100),
                item.optInt("fields", 0),
                item.optInt("rubles", 0),
                item.optInt("cues", 0),
                item.optInt("account", 0),
                if (lengths == null) emptyList() else
                    (0 until minOf(lengths.length(), SberNotificationText.Field.entries.size))
                        .map { lengths.optInt(it, 0).coerceIn(0, 8192) },
                item.optInt("state", -1),
                item.optString("privacy", "NONE"),
                item.optString("keyHash", "")
            )
        }
    }

    @Synchronized
    fun record(
        context: Context,
        bank: RussianBank,
        status: String,
        payload: SberNotificationText.Payload? = null,
        privacy: SberNotificationPrivacy.Evidence? = null,
        deliveryState: Int = -1,
        postedAt: Long = 0L,
        notificationKey: String? = null
    ) {
        if (!allowed(bank)) return
        // Hash Android's key without storing it, since tags can contain identifiers.
        val fingerprint = if (notificationKey == null || postedAt <= 0L) ""
            else MessageDigest.getInstance("SHA-256")
                .digest((notificationKey + "#" + postedAt).toByteArray(Charsets.UTF_8))
                .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val previous = recent(context, bank)
        if (fingerprint.isNotEmpty() && previous.any { it.fingerprint == fingerprint }) return
        val now = postedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val entry = JSONObject()
            .put("at", now)
            .put("status", status.take(100))
            .put("fields", payload?.fieldsMask ?: 0)
            .put("rubles", payload?.rubleFieldsMask ?: 0)
            .put("cues", payload?.cueFieldsMask ?: 0)
            .put("account", payload?.accountFieldsMask ?: 0)
            .put("state", deliveryState)
            .put("privacy", privacy?.name ?: "NONE")
            .put("keyHash", fingerprint)
        val lengths = JSONArray()
        payload?.fieldLengths?.take(SberNotificationText.Field.entries.size)?.forEach {
            lengths.put(it.coerceIn(0, 8192))
        }
        entry.put("lengths", lengths)
        val history = JSONArray().put(entry)
        // Keep at most six metadata records and preserve existing UI status keys.
        previous.take(5).forEach { item ->
            val sizes = JSONArray()
            item.fieldLengths.forEach { sizes.put(it) }
            history.put(JSONObject()
                .put("at", item.receivedAt)
                .put("status", item.status)
                .put("fields", item.fieldsMask)
                .put("rubles", item.rubleFieldsMask)
                .put("cues", item.cueFieldsMask)
                .put("account", item.accountFieldsMask)
                .put("state", item.deliveryState)
                .put("privacy", item.privacy)
                .put("keyHash", item.fingerprint)
                .put("lengths", sizes))
        }
        prefs(context).edit {
            putString("status_" + bank.id, status)
            putLong("time_" + bank.id, now)
            putString("events_" + bank.id, history.toString())
            if (status == "Баланс обновлён") putLong("success_" + bank.id, now)
        }
    }

    @Synchronized
    fun clear(context: Context, id: String) {
        if (id != "vtb" && id != "tbank") return
        prefs(context).edit {
            remove("status_" + id)
            remove("time_" + id)
            remove("events_" + id)
            remove("success_" + id)
        }
    }

    fun last(context: Context, bank: RussianBank): String? =
        if (allowed(bank)) prefs(context).getString("status_" + bank.id, null) else null

    fun lastSuccess(context: Context, bank: RussianBank): Long? =
        if (allowed(bank)) prefs(context).getLong("success_" + bank.id, 0L)
            .takeIf { it > 0L } else null
}
