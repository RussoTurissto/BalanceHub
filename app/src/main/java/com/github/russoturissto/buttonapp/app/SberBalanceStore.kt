package com.github.russoturissto.buttonapp.app

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.AtomicFile
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Stores the latest Push balance per masked card/account identifier, without notification text. */
internal object SberBalanceStore {
    data class Snapshot(
        val kopecks: Long,
        val lastFour: String?,
        val observedAt: Long,
        val source: String = "push"
    )

    private val lock = Any()
    private const val PREFS = "balancehub_data"
    private const val ENABLED = "sber_push_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(ENABLED, enabled) }
        if (!enabled) {
            clear(context)
            SberPushDiagnostics.clear(context)
        }
    }

    fun isAccessGranted(context: Context): Boolean {
        val component = ComponentName(context, SberPushNotificationListener::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return manager.isNotificationListenerAccessGranted(component)
        }
        return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.split(':')?.mapNotNull(ComponentName::unflattenFromString)?.contains(component) == true
    }

    private fun storage(context: Context): AtomicFile =
        AtomicFile(File(context.noBackupFilesDir, "sber_balances.json"))

    fun all(context: Context): List<Snapshot> = synchronized(lock) { read(context) }

    /**
     * Only one bank balance is displayed. Card/account identifiers remain
     * separate internally because matching last-four digits prove no linkage.
     */
    fun latest(context: Context): Snapshot? = synchronized(lock) {
        SberBalanceCardPolicy.latest(read(context)) { it.observedAt }
    }

    private fun read(context: Context): List<Snapshot> {
        val raw = try {
            storage(context).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            return emptyList()
        }
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val time = item.optLong("at", -1L)
                val amount = item.optLong("amount", -1L)
                if (time <= 0 || amount < 0) return@mapNotNull null
                Snapshot(amount, item.optString("card").takeIf { it.matches(Regex("\\d{4}")) },
                    time, item.optString("source", "push"))
            }.sortedByDescending { it.observedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, result: SberPushParser.Result, time: Long): Boolean =
        synchronized(lock) {
            if (!isEnabled(context) || time <= 0L || time > System.currentTimeMillis() + 60_000L) {
                return@synchronized false
            }
            val previous = read(context).toMutableList()
            val account = result.lastFour
            val old = previous.firstOrNull { it.lastFour == account }
            // Ignore out-of-order/replayed notifications and unchanged refreshes.
            if (old != null && time <= old.observedAt) return@synchronized false
            previous.removeAll { it.lastFour == account }
            previous.add(Snapshot(result.kopecks, account, time))
            val array = JSONArray()
            previous.sortedByDescending { it.observedAt }.take(12).forEach { snapshot ->
                array.put(JSONObject().put("amount", snapshot.kopecks)
                    .put("card", snapshot.lastFour ?: JSONObject.NULL)
                    .put("at", snapshot.observedAt).put("source", snapshot.source))
            }
            val file = storage(context)
            var stream: java.io.FileOutputStream? = null
            try {
                stream = file.startWrite()
                stream.write(array.toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
                true
            } catch (_: Exception) {
                if (stream != null) file.failWrite(stream)
                false
            }
        }

    fun clear(context: Context) = synchronized(lock) {
        storage(context).delete()
    }
}
