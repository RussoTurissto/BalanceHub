package com.github.russoturissto.buttonapp.app

import android.content.Context
import androidx.core.content.edit

/**
 * Opt-in engineering probe for real bank notifications.
 * Stores no notification text, amounts, account numbers, keys or package names.
 */
internal object BankPushForegroundProbe {
    enum class Mode(val label: String) {
        OPEN("Открытый BalanceHub"),
        SHADE("Раскрытая шторка уведомлений"),
        BACKGROUND("Свёрнутый BalanceHub")
    }

    data class Snapshot(
        val mode: Mode,
        val startedAt: Long,
        val callbacks: Int,
        val recovered: Int,
        val lastSeenAt: Long,
        val lastOutcome: String
    )

    private const val PREFS = "balancehub_bank_foreground_probe_v1"
    private val lock = Any()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun begin(context: Context, bankId: String, mode: Mode) = synchronized(lock) {
        require(RussianBankCatalog.byId(bankId) != null)
        prefs(context).edit {
            putString("bank", bankId)
            putString("mode", mode.name)
            putLong("started", System.currentTimeMillis())
            putInt("callbacks", 0)
            putInt("recovered", 0)
            putLong("seen", 0L)
            remove("outcome")
        }
    }

    fun snapshot(context: Context, bankId: String): Snapshot? = synchronized(lock) {
        val p = prefs(context)
        if (p.getString("bank", null) != bankId) return@synchronized null
        val mode = try {
            Mode.valueOf(p.getString("mode", "") ?: "")
        } catch (_: IllegalArgumentException) {
            return@synchronized null
        }
        val started = p.getLong("started", 0L)
        if (started <= 0L) return@synchronized null
        Snapshot(mode, started, p.getInt("callbacks", 0),
            p.getInt("recovered", 0), p.getLong("seen", 0L),
            p.getString("outcome", "") ?: "")
    }

    /** Returns true only for a new event relevant to the active test session. */
    fun arrival(context: Context, bankId: String, recovered: Boolean,
                postedAt: Long): Boolean = synchronized(lock) {
        val p = prefs(context)
        val started = p.getLong("started", 0L)
        if (p.getString("bank", null) != bankId || started <= 0L ||
            (recovered && postedAt < started)) return@synchronized false
        val counter = if (recovered) "recovered" else "callbacks"
        p.edit {
            putInt(counter, p.getInt(counter, 0) + 1)
            putLong("seen", System.currentTimeMillis())
            remove("outcome")
        }
        true
    }

    fun outcome(context: Context, bankId: String, status: String) = synchronized(lock) {
        val p = prefs(context)
        if (p.getString("bank", null) == bankId && p.getLong("seen", 0) > 0)
            p.edit { putString("outcome", status.take(96)) }
    }
}
