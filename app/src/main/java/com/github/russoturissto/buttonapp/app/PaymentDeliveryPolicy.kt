package com.github.russoturissto.buttonapp.app

/**
 * Payment-only grace for delayed alarms. Normal reminders are still delivered
 * at their scheduled time. This window prevents a late exact alarm or a
 * post-upgrade recovery from silently losing an overdue payment reminder.
 * DailyReminderLedger still guarantees one alert per calendar day and ID.
 */
internal object PaymentDeliveryPolicy {
    const val LATE_GRACE_MS = 15 * 60_000L

    fun eligible(firstAt: Long, dueAt: Long, now: Long): Boolean =
        firstAt > 0L && dueAt > 0L && now >= firstAt &&
            (now <= dueAt || now - dueAt < LATE_GRACE_MS)

    fun mayRecover(dueAt: Long, now: Long): Boolean =
        dueAt > 0L && now >= dueAt && now - dueAt < LATE_GRACE_MS
}
