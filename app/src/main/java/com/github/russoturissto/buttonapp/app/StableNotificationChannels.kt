package com.github.russoturissto.buttonapp.app

/**
 * Three visible categories. A user-confirmed sound/vibration change needs a new
 * channel ID on Android 8+, because existing channel alert settings are immutable.
 * Only one generation of each category remains active.
 */
internal object StableNotificationChannels {
    const val PAYMENT = "balancehub.channel.payment"
    const val EVENT = "balancehub.channel.event"
    const val UPDATE = "balancehub.channel.update"

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

    fun all(): Set<String> = setOf(PAYMENT, EVENT, UPDATE)

    fun isManaged(id: String): Boolean = all().any { base ->
        id == base || (id.startsWith("$base.configured.") &&
            id.removePrefix("$base.configured.").toIntOrNull()?.let { it > 0 } == true)
    }

    fun isRetired(id: String): Boolean {
        val categories = listOf(BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT, BalanceNotificationSounds.UPDATE)
        return !isManaged(id) && categories.any {
            id == it || id.startsWith(it + "_tone_")
        }
    }
}
