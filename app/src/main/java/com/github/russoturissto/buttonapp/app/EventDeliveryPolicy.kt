package com.github.russoturissto.buttonapp.app

/** Android may deliver inexact alarms after the 60-second archive grace. */
internal object EventDeliveryPolicy {
    /** Bounded catch-up window, so late reminders are not delivered days later. */
    const val MAX_DELAY_MS = 3L * 60L * 60L * 1000L

    fun canNotify(firstAt: Long, dueAt: Long, now: Long): Boolean =
        firstAt > 0L && dueAt > 0L && now >= firstAt &&
            now <= dueAt && dueAt - now >= 0L ||
            (firstAt > 0L && dueAt > 0L && now > dueAt &&
                now - dueAt <= MAX_DELAY_MS)

    fun archivedOccurrenceEligible(reason: String, dueAt: Long,
        archivedAt: Long, now: Long): Boolean =
        reason == "completed" && dueAt > 0L &&
            archivedAt >= dueAt && now >= dueAt &&
            now - dueAt <= MAX_DELAY_MS
}
