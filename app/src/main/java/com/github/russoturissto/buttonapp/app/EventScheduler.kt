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
    const val ACTION_EVENTS_CHANGED =
        "com.github.russoturissto.buttonapp.app.EVENTS_CHANGED"

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
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Keep a separate one-minute grace period so due-time reminder
        // notifications can fire before the one-off event is archived.
        EventDateRules.alertAt(event.dueAt, event.leadMinutes, now)?.let {
            ExactReminderAlarms.schedule(context, it,
                pending(context, event.id, event.dueAt, true))
        }
        // Do not advance the active recurrence before the same-minute reminder
        // receiver has had a chance to read its original due timestamp.
        val completeAt = if (event.recurrence == "once")
            EventArchivePolicy.archiveAt(event.dueAt)
        else event.dueAt + 60_000L
        if (completeAt > now) ExactReminderAlarms.schedule(context,
            completeAt, pending(context, event.id, event.dueAt, false))
    }

    fun scheduleFollowingDay(context: Context, event: PlannedEvent, now: Long) {
        val first = DailyReminderPolicy.firstEvent(event.dueAt, event.leadMinutes)
        DailyReminderPolicy.following(first, event.dueAt, now)?.let { next ->
            ExactReminderAlarms.schedule(context, next,
                pending(context, event.id, event.dueAt, true))
        }
    }

    fun restoreAfterBoot(context: Context) {
        EventStore.reconcileElapsed(context)
        for (event in EventStore.active(context)) schedule(context, event)
        // An inexact alarm can be lost by a reboot or superseded by the
        // due-time archiver. Recover only recently completed occurrences.
        val now = System.currentTimeMillis()
        for (archived in EventStore.archived(context)) {
            val event = archived.event
            if (EventDeliveryPolicy.archivedOccurrenceEligible(
                    archived.reason, event.dueAt, archived.archivedAt, now)) {
                context.sendBroadcast(Intent(context, EventReminderReceiver::class.java)
                    .putExtra("id", event.id).putExtra("dueAt", event.dueAt))
            }
        }
    }
}

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val due = intent.getLongExtra("dueAt", -1L)
        val now = System.currentTimeMillis()
        val activeEvent = EventStore.active(context).firstOrNull {
            it.id == id && it.dueAt == due
        }
        // The due-time receiver or onResume may have already archived it.
        // Completed events are eligible for a bounded catch-up window.
        val archived = if (activeEvent == null) EventStore.archived(context)
            .firstOrNull { it.event.id == id && it.event.dueAt == due &&
                EventDeliveryPolicy.archivedOccurrenceEligible(
                    it.reason, due, it.archivedAt, now) } else null
        val event = activeEvent ?: archived?.event ?: return
        val first = DailyReminderPolicy.firstEvent(due, event.leadMinutes)
        if (!EventDeliveryPolicy.canNotify(first, due, now)) return
        if (activeEvent != null) EventScheduler.scheduleFollowingDay(context, event, now)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        val channelId = BalanceNotificationSounds.ensureChannel(
            context, BalanceNotificationSounds.EVENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            if (manager.getNotificationChannel(channelId)?.importance ==
                NotificationManager.IMPORTANCE_NONE) return
        }
        val noticeId = ReminderNoticeIds.event(id, due,
            DailyReminderPolicy.dayKey(now))
        val reservation = DailyReminderLedger.reserve(context,
            "event", id, due, now, noticeId) ?: return
        val launch = PendingIntent.getActivity(context, ("event_open_" + id).hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_tab", 3)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message = event.details.ifBlank { "Запланированное событие" }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("Скоро событие: " + event.title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(launch)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        BalanceNotificationSounds.configureLegacySound(
            context, BalanceNotificationSounds.EVENT, builder)
        try {
            notifications.notify(noticeId, builder.build())
            reservation.previousNotificationId?.takeIf { it != noticeId }?.let {
                notifications.cancel(it)
            }
        } catch (_: SecurityException) {
            DailyReminderLedger.rollback(context, "event", id, reservation)
        }
    }
}

class EventDueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val due = intent.getLongExtra("dueAt", -1L)
        if (due <= 0L) return
        val event = EventStore.active(context).firstOrNull {
            it.id == id && it.dueAt == due
        } ?: return
        // Deliver a missed due-day reminder before an occurrence is archived,
        // even when Android delivered its alarm later than planned.
        if (EventDeliveryPolicy.canNotify(
                DailyReminderPolicy.firstEvent(due, event.leadMinutes),
                due, System.currentTimeMillis())) {
            EventReminderReceiver().onReceive(context,
                Intent(context, EventReminderReceiver::class.java)
                    .putExtra("id", id).putExtra("dueAt", due))
        }
        if (event.recurrence == "once") {
            EventStore.reconcileElapsed(context)
            if (EventStore.active(context).none { it.id == id }) {
                context.sendBroadcast(Intent(EventScheduler.ACTION_EVENTS_CHANGED)
                    .setPackage(context.packageName))
            }
        } else if (System.currentTimeMillis() >= due + 60_000L) {
            if (EventStore.archive(context, id, "completed", nextForRepeated = true) != null) {
                EventStore.active(context).firstOrNull { it.id == id }?.let {
                    EventScheduler.schedule(context, it)
                }
                context.sendBroadcast(Intent(EventScheduler.ACTION_EVENTS_CHANGED)
                    .setPackage(context.packageName))
            }
        }
    }
}
