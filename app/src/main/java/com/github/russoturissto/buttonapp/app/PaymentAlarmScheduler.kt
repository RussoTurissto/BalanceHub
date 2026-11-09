package com.github.russoturissto.buttonapp.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Centralized payment scheduling used by UI, reboot and recurring payments. */
internal object PaymentAlarmScheduler {
    private fun pending(context: Context, id: String, title: String, amount: String,
        currency: String, dueAt: Long, leadDays: Int, recurrence: String): PendingIntent {
        val intent = Intent(context, PaymentReminderReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("amount", amount)
            putExtra("currency", currency)
            putExtra("id", id)
            putExtra("dueAt", dueAt)
            putExtra("leadDays", leadDays)
            putExtra("recurrence", recurrence)
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(context: Context, id: String, title: String, amount: String,
        currency: String, dueAt: Long, leadDays: Int, recurrence: String) {
        if (recurrence == "once") PaymentArchiveScheduler.schedule(context, id, dueAt)
        val now = System.currentTimeMillis()
        val trigger = ReminderTriggerPolicy.payment(dueAt, leadDays, now) ?: return
        ExactReminderAlarms.schedule(context, trigger,
            pending(context, id, title, amount, currency, dueAt, leadDays, recurrence))
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
