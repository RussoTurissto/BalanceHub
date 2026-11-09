package com.github.russoturissto.buttonapp.app

/** One minute after due time, leaving the due-time notification a short grace period. */
internal object EventArchivePolicy {
    const val DUE_REMINDER_GRACE_MS = 60_000L

    fun archiveAt(dueAt: Long): Long = dueAt + DUE_REMINDER_GRACE_MS

    fun shouldArchiveOnce(dueAt: Long, now: Long): Boolean =
        dueAt > 0L && now >= archiveAt(dueAt)
}
