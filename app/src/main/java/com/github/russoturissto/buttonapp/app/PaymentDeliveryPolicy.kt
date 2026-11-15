package com.github.russoturissto.buttonapp.app

/**
 * Payment-only grace for delayed alarms. Normal reminders are still delivered
 * at their scheduled time. This window prevents a late exact alarm or a
 * post-upgrade recovery from silently losing an overdue payment reminder.
 * DailyReminderLedger still guarantees one alert per calendar day and ID.
 */
internal object PaymentDeliveryPolicy {
    const val LATE_GRACE_MS = 15 * 60_000L

    enum class Status { INVALID, EARLY, READY, EXPIRED }

    /** A trigger received before its planned window must be re-armed, not dropped. */
    fun status(firstAt: Long, dueAt: Long, now: Long): Status {
        if (firstAt <= 0L || dueAt <= 0L || firstAt > dueAt) return Status.INVALID
        if (now < firstAt) return Status.EARLY
        return if (now <= dueAt || now - dueAt < LATE_GRACE_MS)
            Status.READY else Status.EXPIRED
    }

    fun eligible(firstAt: Long, dueAt: Long, now: Long): Boolean =
        status(firstAt, dueAt, now) == Status.READY

    /** At least one second in the future avoids an immediate retry loop near a boundary. */
    fun earlyRetryAt(firstAt: Long, dueAt: Long, now: Long): Long? =
        if (status(firstAt, dueAt, now) == Status.EARLY)
            maxOf(firstAt, now + 1_000L) else null

    fun mayRecover(dueAt: Long, now: Long): Boolean =
        dueAt > 0L && now >= dueAt && now - dueAt < LATE_GRACE_MS
}
