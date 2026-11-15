package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit

/** Exact known Android app package names. No QUERY_ALL_PACKAGES permission. */
internal data class RussianBank(
    val id: String, val name: String, val packages: List<String>, val monogram: String
)

internal object RussianBankCatalog {
    // Presence is not proof of an app's publisher/signing identity or a readable Push balance.
    val banks: List<RussianBank> = listOf(
        RussianBank("sber", "Сбербанк", listOf("ru.sberbankmobile"), "С"),
        RussianBank("vtb", "ВТБ", listOf("ru.vtb24.mobilebanking.android"), "В"),
        RussianBank("tbank", "Т-Банк", listOf("com.idamob.tinkoff.android"), "Т")
    )
    init {
        require(banks.map { it.id }.distinct().size == banks.size)
        require(banks.flatMap { it.packages }.distinct().size ==
            banks.sumOf { it.packages.size })
    }
    fun byId(id: String): RussianBank? = banks.firstOrNull { it.id == id }
    fun byPackage(packageName: String): RussianBank? =
        banks.firstOrNull { packageName in it.packages }

    fun installedPackage(context: Context, bank: RussianBank): String? =
        bank.packages.firstOrNull { name ->
            try {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(name, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) { false }
        }
    fun available(context: Context): List<RussianBank> = banks.filter {
        it.id == "sber" || installedPackage(context, it) != null ||
            BankCardPreferences.enabled(context, it.id)
    }
}

/** Card visibility is separate from bank Push access and balance storage. */
internal object BankCardPreferences {
    private const val PREFS = "balancehub_bank_cards_v1"
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun visible(context: Context, id: String): Boolean =
        prefs(context).getBoolean("visible_$id", true)
    fun setVisible(context: Context, id: String, visible: Boolean) {
        prefs(context).edit { putBoolean("visible_$id", visible) }
    }
    fun enabled(context: Context, id: String): Boolean =
        if (id == "sber") SberBalanceStore.isEnabled(context)
        else prefs(context).getBoolean("push_$id", false)

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        if (id == "sber") {
            SberBalanceStore.setEnabled(context, enabled)
            return
        }
        if (RussianBankCatalog.byId(id) == null) return
        prefs(context).edit { putBoolean("push_$id", enabled) }
        if (!enabled) RussianBankBalanceStore.clear(context, id)
    }
}
