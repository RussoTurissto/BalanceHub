package com.github.russoturissto.buttonapp.app

import java.util.UUID

/**
 * Exactly three live categories. A new, never-reused ID is essential for
 * an explicit sound/vibration change: Android can resurrect a deleted channel
 * (and its old muted system settings) if its old ID is created again.
 *
 * Legacy configured.<revision> IDs are still accepted for in-place upgrades.
 */
internal object StableNotificationChannels {
    const val PAYMENT = "balancehub.channel.payment"
    const val EVENT = "balancehub.channel.event"
    const val UPDATE = "balancehub.channel.update"
    const val PROBE_PREFIX = "balancehub.channel.signal_test."

    fun id(category: String, revision: Int = 0): String {
        require(revision >= 0) { "Negative channel revision" }
        val base = when (category) {
            BalanceNotificationSounds.PAYMENT -> PAYMENT
            BalanceNotificationSounds.EVENT -> EVENT
            BalanceNotificationSounds.UPDATE -> UPDATE
            else -> throw IllegalArgumentException("Unknown notification category")
        }
        return if (revision == 0) base else "$base.configured.$revision"
    }

    /** Use a fresh suffix even after an application backup / settings restore. */
    fun freshId(category: String, revision: Int): String {
        require(revision > 0)
        return id(category, revision) + "." + UUID.randomUUID().toString().replace("-", "")
    }

    fun probeId(): String = PROBE_PREFIX + UUID.randomUUID().toString().replace("-", "")

    fun all(): Set<String> = setOf(PAYMENT, EVENT, UPDATE)

    /** Both old deterministic revisions and new nonce revisions are managed. */
    fun belongsToCategory(candidate: String, category: String): Boolean {
        val base = id(category)
        if (candidate == base) return true
        val configured = "$base.configured."
        if (!candidate.startsWith(configured)) return false
        val tail = candidate.removePrefix(configured)
        val revision = tail.substringBefore(".").toIntOrNull() ?: return false
        if (revision <= 0) return false
        if ('.' !in tail) return true // 1.1.4-rc4 and earlier
        val suffix = tail.substringAfter(".", "")
        return suffix.length == 32 && suffix.all { it in '0'..'9' || it in 'a'..'f' }
    }

    fun isManaged(candidate: String): Boolean =
        listOf(BalanceNotificationSounds.PAYMENT, BalanceNotificationSounds.EVENT,
            BalanceNotificationSounds.UPDATE).any { belongsToCategory(candidate, it) }

    fun isProbe(candidate: String): Boolean =
        candidate.startsWith(PROBE_PREFIX) &&
            candidate.removePrefix(PROBE_PREFIX).length == 32 &&
            candidate.removePrefix(PROBE_PREFIX).all {
                it in '0'..'9' || it in 'a'..'f'
            }

    fun isRetired(candidate: String): Boolean {
        val categories = listOf(BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT, BalanceNotificationSounds.UPDATE)
        return !isManaged(candidate) && categories.any {
            candidate == it || candidate.startsWith(it + "_tone_")
        }
    }
}
