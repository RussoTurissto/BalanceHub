package com.github.russoturissto.buttonapp.app

/** Prevents foreground reconciliation from replaying unchanged notifications. */
internal object BankPushRescanPolicy {
    private const val RECENT_WINDOW_MS = 15L * 60L * 1_000L

    fun shouldProcess(previousFingerprint: Int?, fingerprint: Int,
                      postedAt: Long, now: Long, recovered: Boolean): Boolean =
        (!recovered || (postedAt > 0L &&
            postedAt >= now - RECENT_WINDOW_MS && postedAt <= now + 60_000L)) &&
            previousFingerprint != fingerprint
}
