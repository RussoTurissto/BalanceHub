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
