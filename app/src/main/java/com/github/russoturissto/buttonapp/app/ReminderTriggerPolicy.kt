package com.github.russoturissto.buttonapp.app

import java.util.Calendar

/** Select an actionable reminder even when a new entry's lead time has elapsed. */
internal object ReminderTriggerPolicy {
    fun payment(dueAt: Long, leadDays: Int, now: Long): Long? {
        if (dueAt <= 0L || leadDays !in 0..30) return null
        // Recover a due-time reminder that was delayed by Doze or app upgrade.
        // It is not a second alert: DailyReminderLedger still deduplicates days.
        if (dueAt <= now) return if (PaymentDeliveryPolicy.mayRecover(dueAt, now))
            now + 1_000L else null
        val chosen = Calendar.getInstance().apply {
            timeInMillis = dueAt
            add(Calendar.DAY_OF_YEAR, -leadDays)
        }.timeInMillis
        return if (chosen <= now) now + 1_000L else chosen
    }
}
