package com.github.russoturissto.buttonapp.app

/**
 * A Sberbank card shows the most recent confirmed balance, never a sum.
 * Differing masked card/account identifiers are not proof of separate funds.
 */
internal object SberBalanceCardPolicy {
    fun <T> latest(snapshots: List<T>, observedAt: (T) -> Long): T? =
        snapshots.maxByOrNull(observedAt)
}
