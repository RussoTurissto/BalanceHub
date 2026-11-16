package com.github.russoturissto.buttonapp.app

import android.content.Context
import androidx.core.content.edit

/** One confirmed Push balance per observed masked identifier, never a sum. */
internal data class BankAccountBalance(
    val kopecks: Long,
    val lastFour: String?,
    val observedAt: Long
)

/**
 * Older BalanceHub versions kept per-account snapshots, but displayed only one.
 * Keep the newest entry for each identifier without merging unknown and known.
 */
internal object BankAccountRows {
    fun newestPerAccount(items: List<BankAccountBalance>): List<BankAccountBalance> =
        items.sortedByDescending { it.observedAt }.distinctBy { it.lastFour }

    fun label(lastFour: String?): String =
        if (lastFour != null && lastFour.matches(Regex("\\d{4}")))
            "Карта / счёт •••• $lastFour"
        else "Карта / счёт без номера"
}

/**
 * Presentation-only preference. Hiding a row never disables the notification
 * listener, clears a balance, or changes which card an incoming Push updates.
 */
internal object BankAccountVisibility {
    private const val PREFS = "balancehub_bank_account_visibility_v1"
    private fun key(bankId: String, lastFour: String?): String {
        require(RussianBankCatalog.byId(bankId) != null)
        require(lastFour == null || lastFour.matches(Regex("\\d{4}")))
        return "show_${bankId}_${lastFour ?: "unknown"}"
    }

    fun visible(context: Context, bankId: String, lastFour: String?): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key(bankId, lastFour), true)

    fun setVisible(context: Context, bankId: String, lastFour: String?, show: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(key(bankId, lastFour), show)
        }
    }
}
