package com.github.russoturissto.buttonapp.app

import java.util.Calendar

/** Local day boundary for one-off event expiration, including daylight-saving days. */
internal object EventArchivePolicy {
    fun endOfDayExclusive(dueAt: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = dueAt
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

    fun shouldArchiveOnce(dueAt: Long, now: Long): Boolean =
        dueAt > 0 && now >= endOfDayExclusive(dueAt)
}
