package com.github.russoturissto.buttonapp.app

/** Exactly three stable Android channels; never encode user settings in their IDs. */
internal object StableNotificationChannels {
    const val PAYMENT = "balancehub.channel.payment"
    const val EVENT = "balancehub.channel.event"
    const val UPDATE = "balancehub.channel.update"

    fun id(category: String): String = when (category) {
        BalanceNotificationSounds.PAYMENT -> PAYMENT
        BalanceNotificationSounds.EVENT -> EVENT
        BalanceNotificationSounds.UPDATE -> UPDATE
        else -> throw IllegalArgumentException("Unknown notification category")
    }

    fun all(): Set<String> = setOf(PAYMENT, EVENT, UPDATE)

    fun isRetired(id: String): Boolean {
        val categories = listOf(BalanceNotificationSounds.PAYMENT,
            BalanceNotificationSounds.EVENT, BalanceNotificationSounds.UPDATE)
        return id !in all() && categories.any {
            id == it || id.startsWith(it + "_tone_")
        }
    }
}
