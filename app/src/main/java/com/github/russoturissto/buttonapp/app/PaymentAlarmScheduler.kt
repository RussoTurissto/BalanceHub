package com.github.russoturissto.buttonapp.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Centralized payment scheduling used by UI, reboot and recurring payments. */
internal object PaymentAlarmScheduler {
    private fun pending(context: Context, id: String, title: String, amount: String,
        currency: String, dueAt: Long, leadDays: Int, recurrence: String,
        scheduledAt: Long): PendingIntent {
        val intent = Intent(context, PaymentReminderReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("amount", amount)
            putExtra("currency", currency)
            putExtra("id", id)
            putExtra("dueAt", dueAt)
            putExtra("leadDays", leadDays)
            putExtra("recurrence", recurrence)
            putExtra(PaymentDeliveryLog.EXTRA_EXPECTED_AT, scheduledAt)
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(context: Context, id: String, title: String, amount: String,
        currency: String, dueAt: Long, leadDays: Int, recurrence: String) {
        PaymentArchiveScheduler.schedule(context, id, dueAt)
        val now = System.currentTimeMillis()
        val trigger = ReminderTriggerPolicy.payment(dueAt, leadDays, now) ?: return
        val mode = ExactReminderAlarms.schedule(context, trigger,
            pending(context, id, title, amount, currency, dueAt, leadDays,
                recurrence, trigger))
        PaymentDeliveryLog.scheduled(context, trigger, mode)
    }

    /**
     * An early receiver cannot post yet. Re-arm the same PendingIntent identity,
     * so Android replaces the pending attempt rather than creating a duplicate.
     * Do not touch the archive alarm or notification channels here.
     */
    fun rearmEarly(context: Context, source: Intent, requestedAt: Long): String {
        val id = source.getStringExtra("id") ?: return "invalid"
        val dueAt = source.getLongExtra("dueAt", -1L)
        val trigger = maxOf(requestedAt, System.currentTimeMillis() + 1_000L)
        val pending = pending(context, id,
            source.getStringExtra("title").orEmpty(),
            source.getStringExtra("amount").orEmpty(),
            source.getStringExtra("currency").orEmpty(),
            dueAt,
            source.getIntExtra("leadDays", 0),
            source.getStringExtra("recurrence").orEmpty(),
            trigger)
        val mode = ExactReminderAlarms.schedule(context, trigger, pending)
        PaymentDeliveryLog.scheduled(context, trigger, mode)
        return mode
    }

    fun scheduleFollowingDay(context: Context, source: Intent, nextAt: Long) {
        val id = source.getStringExtra("id") ?: return
        val next = Intent(context, PaymentReminderReceiver::class.java).apply {
            putExtras(source)
            putExtra(PaymentDeliveryLog.EXTRA_EXPECTED_AT, nextAt)
        }
        val pending = PendingIntent.getBroadcast(context, id.hashCode(), next,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val mode = ExactReminderAlarms.schedule(context, nextAt, pending)
        PaymentDeliveryLog.scheduled(context, nextAt, mode)
    }

    fun cancel(context: Context, id: String) {
        PaymentArchiveScheduler.cancel(context, id)
        val pending = PendingIntent.getBroadcast(context, id.hashCode(),
            Intent(context, PaymentReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager)
                .cancel(pending)
            pending.cancel()
        }
    }
}
