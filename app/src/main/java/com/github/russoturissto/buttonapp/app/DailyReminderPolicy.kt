package com.github.russoturissto.buttonapp.app

import android.annotation.SuppressLint
import android.content.Context
import java.util.Calendar

/**
 * One reminder in each local calendar day from the requested lead time until due.
 * Calendar arithmetic preserves local hour across daylight-saving changes.
 */
internal object DailyReminderPolicy {
    const val DUE_GRACE_MS = 60_000L

    fun firstPayment(dueAt: Long, leadDays: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = dueAt
            add(Calendar.DAY_OF_YEAR, -leadDays)
        }.timeInMillis

    fun firstEvent(dueAt: Long, leadMinutes: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = dueAt
            add(Calendar.MINUTE, -leadMinutes)
        }.timeInMillis

    fun dayKey(now: Long): Int = Calendar.getInstance().apply {
        timeInMillis = now
    }.let {
        it.get(Calendar.YEAR) * 10000 +
            (it.get(Calendar.MONTH) + 1) * 100 + it.get(Calendar.DAY_OF_MONTH)
    }

    fun eligible(firstAt: Long, dueAt: Long, now: Long): Boolean =
        dueAt > 0L && firstAt > 0L && now >= firstAt &&
            (now < dueAt || (firstAt == dueAt && now < dueAt + DUE_GRACE_MS))

    /** Next scheduled local-day slot, never after the due moment. */
    fun following(firstAt: Long, dueAt: Long, now: Long): Long? {
        if (firstAt <= 0L || dueAt <= firstAt) return null
        val candidate = Calendar.getInstance().apply { timeInMillis = firstAt }
        var attempts = 0
        do {
            if (candidate.timeInMillis > now)
                return candidate.timeInMillis.takeIf { it < dueAt }
            candidate.add(Calendar.DAY_OF_YEAR, 1)
            attempts++
        } while (attempts <= 40 && candidate.timeInMillis < dueAt)
        return null
    }
}

/** Synchronous, persisted dedup across app restarts and re-delivered alarms. */
/** Synchronous commit is intentional: a failed durable reservation is not an alert. */
@SuppressLint("ApplySharedPref", "UseKtx")
internal object DailyReminderLedger {
    private const val NAME = "balancehub_daily_reminder_ledger_v1"
    data class Reservation(val previous: String?,
        val previousNotificationId: Int?, val value: String)

    @Synchronized
    fun reserve(context: Context, category: String, id: String,
        dueAt: Long, now: Long, noticeId: Int): Reservation? {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val key = category + "|" + id
        val previous = prefs.getString(key, null)
        val value = dueAt.toString() + "|" + DailyReminderPolicy.dayKey(now) +
            "|" + noticeId
        if (previous?.substringBeforeLast('|') == value.substringBeforeLast('|')) return null
        if (!prefs.edit().putString(key, value).commit()) return null
        return Reservation(previous, previous?.substringAfterLast('|')?.toIntOrNull(), value)
    }

    @Synchronized
    fun rollback(context: Context, category: String, id: String,
        reservation: Reservation) {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val key = category + "|" + id
        if (prefs.getString(key, null) != reservation.value) return
        val editor = prefs.edit()
        if (reservation.previous == null) editor.remove(key)
        else editor.putString(key, reservation.previous)
        editor.commit()
    }
}

/** Advance a recurring payment only after this period's due timestamp. */
internal object PaymentRecurrencePolicy {
    fun nextDue(dueAt: Long, recurrence: String, now: Long): Long? {
        if (dueAt <= 0L || recurrence !in listOf("weekly", "monthly")) return null
        val next = Calendar.getInstance().apply { timeInMillis = dueAt }
        var attempts = 0
        do {
            if (recurrence == "weekly") next.add(Calendar.DAY_OF_YEAR, 7)
            else next.add(Calendar.MONTH, 1)
            attempts++
        } while (next.timeInMillis <= now && attempts < 10_000)
        return next.timeInMillis.takeIf { it > now }
    }
}
