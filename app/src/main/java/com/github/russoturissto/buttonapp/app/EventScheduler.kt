package com.github.russoturissto.buttonapp.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** AlarmManager-based reminders; exact alarm privilege is deliberately not requested. */
internal object EventScheduler {
    private fun pending(context: Context, id: String, dueAt: Long,
        reminder: Boolean): PendingIntent {
        val target = if (reminder) EventReminderReceiver::class.java
            else EventDueReceiver::class.java
        val intent = Intent(context, target).apply {
            putExtra("id", id)
            putExtra("dueAt", dueAt)
        }
        val code = ("event_" + (if (reminder) "reminder_" else "due_") + id).hashCode()
        return PendingIntent.getBroadcast(context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun cancel(context: Context, id: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pending(context, id, 0L, true))
        manager.cancel(pending(context, id, 0L, false))
    }

    fun schedule(context: Context, event: PlannedEvent) {
        cancel(context, event.id)
        val now = System.currentTimeMillis()
        if (event.dueAt <= now) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        EventDateRules.alertAt(event.dueAt, event.leadMinutes, now)?.let {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it,
                pending(context, event.id, event.dueAt, true))
        }
        // Do not advance the active recurrence before the same-minute reminder
        // receiver has had a chance to read its original due timestamp.
        if (event.recurrence != "once") manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, event.dueAt + 60_000L,
            pending(context, event.id, event.dueAt, false))
    }

    fun restoreAfterBoot(context: Context) {
        EventStore.refreshRepetitions(context)
        for (event in EventStore.active(context)) schedule(context, event)
    }
}

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val due = intent.getLongExtra("dueAt", -1L)
        val event = EventStore.active(context).firstOrNull {
            it.id == id && it.dueAt == due
        } ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val channelId = "event_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                channelId, "Напоминания о событиях",
                NotificationManager.IMPORTANCE_DEFAULT))
        }
        val launch = PendingIntent.getActivity(context, ("event_open_" + id).hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_tab", 3)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message = event.details.ifBlank { "Запланированное событие" }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("Скоро событие: " + event.title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(launch)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(
                ("event_notice_" + id).hashCode(), notification)
        } catch (_: SecurityException) { /* User disabled notifications */ }
    }
}

class EventDueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val due = intent.getLongExtra("dueAt", -1L)
        if (due <= 0L) return
        EventStore.advance(context, id, due, System.currentTimeMillis())?.let {
            EventScheduler.schedule(context, it)
        }
    }
}
