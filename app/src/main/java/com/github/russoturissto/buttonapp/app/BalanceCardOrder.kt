package com.github.russoturissto.buttonapp.app

/** Keep the configured order within each group, while putting known amounts first. */
internal object BalanceCardOrder {
    fun <T> withBalanceFirst(items: List<T>, hasBalance: (T) -> Boolean): List<T> {
        val (withBalance, withoutBalance) = items.partition(hasBalance)
        return withBalance + withoutBalance
    }
}
