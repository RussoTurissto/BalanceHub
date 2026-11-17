package com.github.russoturissto.buttonapp.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import java.util.Calendar
import java.util.Date

/**
 * Each occurrence has its own alarm identity. A saved per-card index lets the
 * app cancel every stale PendingIntent when dates or exceptions are edited.
 */
@SuppressLint("UseKtx", "ApplySharedPref")
internal object PaymentOccurrenceScheduler {
    private const val PREFS = "balancehub_occurrence_alarms_v2"
    private fun requestCode(card: String, source: String): Int =
        ("payment_occurrence|" + card + "|" + source).hashCode()

    private fun intent(context: Context, card: String, source: String,
        expected: Long = 0L): Intent =
        Intent(context, PaymentOccurrenceReceiver::class.java).apply {
            action = "balancehub.occurrence.$card.$source"
            putExtra("card_id", card)
            putExtra("source_id", source)
            putExtra("expected_at", expected)
        }

    private fun pending(context: Context, card: String, source: String,
        expected: Long = 0L): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode(card, source), intent(context, card, source, expected),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun cancel(context: Context, card: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val keys = try { JSONArray(prefs.getString(card, "[]")) }
            catch (_: Exception) { JSONArray() }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until keys.length()) {
            val source = keys.optString(i)
            if (source.isEmpty()) continue
            val found = PendingIntent.getBroadcast(context, requestCode(card, source),
                intent(context, card, source), PendingIntent.FLAG_NO_CREATE or
                    PendingIntent.FLAG_IMMUTABLE)
            if (found != null) {
                manager.cancel(found)
                found.cancel()
            }
        }
        prefs.edit().remove(card).commit()
        PaymentArchiveScheduler.cancel(context, card)
    }

    fun schedule(context: Context, payment: PaymentReminder) {
        // Existing v1.2.1 alarms must not fire alongside the new per-occurrence alarms.
        PaymentAlarmScheduler.cancel(context, payment.id)
        cancel(context, payment.id)
        val now = System.currentTimeMillis()
        val defaultEnd = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, 18)
        }.timeInMillis
        val futureStart = payment.plan?.startsAt ?: 0L
        val selectedEnd = payment.plan?.selectedDates?.maxOrNull() ?: 0L
        val end = maxOf(defaultEnd,
            if (futureStart > defaultEnd) Calendar.getInstance().apply {
                timeInMillis = futureStart
                add(Calendar.MONTH, 18)
            }.timeInMillis else defaultEnd,
            selectedEnd + 86_400_000L)
        val keys = JSONArray()
        val projected = PaymentOccurrenceEngine.occurrences(payment,
            PaymentOccurrenceEngine.day(now), end)
            .filter { !it.skipped && it.dueAt > now }
            .sortedBy { it.firstReminderAt() }
            .take(24)
        for (occ in projected) {
            val trigger = nextTrigger(occ, now) ?: continue
            val source = occ.id
            val p = pending(context, payment.id, source, trigger)
            ExactReminderAlarms.schedule(context, trigger, p)
            keys.put(source)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(payment.id, keys.toString()).commit()
        val last = PaymentOccurrenceEngine.lastScheduledAt(payment)
        val deadline = PaymentArchiveStore.archiveDeadline(payment)
        if (last != null && deadline != null)
            PaymentArchiveScheduler.scheduleDeadline(context, payment.id, deadline, last)
        PaymentScheduleMaintenance.schedule(context)
    }

    fun nextTrigger(occ: PaymentOccurrence, now: Long): Long? {
        if (occ.skipped || occ.dueAt <= now) return null
        val first = occ.firstReminderAt()
        // When a payment is created or restored inside the reminder window,
        // notify promptly. A manually moved payment must not replay an
        // already-passed reminder time from earlier today.
        if (first <= now && !occ.manuallyMoved) return now + 2_000L
        val cursor = Calendar.getInstance().apply { timeInMillis = maxOf(first, now) }
        val stop = Calendar.getInstance().apply { timeInMillis = occ.dueAt }
        var tries = 0
        while (!cursor.after(stop) && tries++ < 400) {
            val candidate = occ.reminderOnDay(cursor.timeInMillis)
            if (candidate != null && candidate > now) return candidate
            cursor.add(Calendar.DAY_OF_YEAR, 1)
            cursor.set(Calendar.HOUR_OF_DAY, 0)
            cursor.set(Calendar.MINUTE, 0)
        }
        return null
    }

    fun rearmEarly(context: Context, cardId: String,
        sourceId: String, expectedAt: Long) {
        ExactReminderAlarms.schedule(context, expectedAt,
            pending(context, cardId, sourceId, expectedAt))
    }

    /** A delivered occurrence remains eligible for its next local-day alert. */
    fun rescheduleAfterDelivery(context: Context, payment: PaymentReminder,
        source: String, now: Long) {
        val end = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, 18)
        }.timeInMillis
        val occ = PaymentOccurrenceEngine.occurrences(payment,
            Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.MONTH, -18)
            }.timeInMillis, end).firstOrNull { source in it.sourceIds } ?: return
        if (occ.skipped || occ.dueAt <= now) return
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val next = nextTrigger(occ, tomorrow - 1L) ?: return
        ExactReminderAlarms.schedule(context, next,
            pending(context, payment.id, source, next))
    }

    fun find(context: Context, card: String): PaymentReminder? {
        val entries = PaymentArchiveStore.activeSnapshot(context) ?: return null
        return (0 until entries.length()).mapNotNull {
            entries.optJSONObject(it)?.let(PaymentReminderCodec::decode)
        }.firstOrNull { it.id == card }
    }
}


/** One shared weekly maintenance alarm replenishes bounded occurrence horizons. */
internal object PaymentScheduleMaintenance {
    fun schedule(context: Context) {
        val next = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
        }.timeInMillis
        val intent = Intent(context, PaymentScheduleMaintenanceReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, 73571, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ExactReminderAlarms.schedule(context, next, pending)
    }
}

class PaymentScheduleMaintenanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PaymentArchiveStore.archiveDue(context)
        val entries = PaymentArchiveStore.activeSnapshot(context) ?: return
        for (i in 0 until entries.length()) {
            val item = entries.optJSONObject(i) ?: continue
            PaymentReminderCodec.decode(item)?.let {
                PaymentOccurrenceScheduler.schedule(context, it)
            }
        }
        PaymentScheduleMaintenance.schedule(context)
    }
}
@SuppressLint("UseKtx", "ApplySharedPref")
class PaymentOccurrenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cardId = intent.getStringExtra("card_id") ?: return
        val sourceId = intent.getStringExtra("source_id") ?: return
        val expected = intent.getLongExtra("expected_at", 0L)
        if (expected <= 0) return
        val payment = PaymentOccurrenceScheduler.find(context, cardId) ?: return
        val now = System.currentTimeMillis()
        if (now + 1_000L < expected) {
            PaymentOccurrenceScheduler.rearmEarly(context, cardId, sourceId, expected)
            return
        }
        if (DailyReminderPolicy.dayKey(now) != DailyReminderPolicy.dayKey(expected)) {
            // An old alarm reached Android after local midnight: re-plan the
            // card rather than showing yesterday's reminder today.
            PaymentOccurrenceScheduler.schedule(context, payment)
            return
        }
        val from = Calendar.getInstance().apply {
            timeInMillis = expected
            add(Calendar.DAY_OF_YEAR, -40)
        }.timeInMillis
        val to = Calendar.getInstance().apply {
            timeInMillis = expected
            add(Calendar.DAY_OF_YEAR, 40)
        }.timeInMillis
        val occurrences = PaymentOccurrenceEngine.occurrences(payment, from, to)
        val target = occurrences.firstOrNull { sourceId in it.sourceIds && !it.skipped }
            ?: return
        if (target.dueAt < now - PaymentDeliveryPolicy.LATE_GRACE_MS) return
        PaymentDeliveryLog.received(context, expected, now, target.dueAt)
        // Old alarm intents from earlier dates/rules are rejected.
        val expectedDay = DailyReminderPolicy.dayKey(expected)
        val valid = target.firstReminderAt() <= expected &&
            DailyReminderPolicy.dayKey(now) == expectedDay &&
            now <= target.dueAt + PaymentDeliveryPolicy.LATE_GRACE_MS
        if (!valid) return
        PaymentOccurrenceScheduler.rescheduleAfterDelivery(context, payment, sourceId, now)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        val channelId = BalanceNotificationSounds.ensureChannel(
            context, BalanceNotificationSounds.PAYMENT)
        val system = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 &&
            system.getNotificationChannel(channelId)?.importance ==
            NotificationManager.IMPORTANCE_NONE) return

        // Group only charges from the SAME card sharing this reminder clock.
        val candidates = occurrences.filter { item ->
            !item.skipped && item.dueAt >= now - PaymentDeliveryPolicy.LATE_GRACE_MS &&
                item.reminderHour == target.reminderHour &&
                item.reminderMinute == target.reminderMinute &&
                item.firstReminderAt() <= now &&
                item.reminderOnDay(expected)?.let {
                    it <= now && DailyReminderPolicy.dayKey(it) == expectedDay
                } == true
        }
        val group = (candidates + target).distinctBy { it.id }
            .sortedWith(compareBy({ it.dueAt }, { it.id }))
        if (group.isEmpty()) return
        val ledger = context.getSharedPreferences("balancehub_occurrence_delivery_v2",
            Context.MODE_PRIVATE)
        val day = DailyReminderPolicy.dayKey(now)
        val unseen = group.filter { occ ->
            ledger.getInt(cardId + "|" + occ.id, 0) != day
        }
        if (unseen.isEmpty()) {
            PaymentDeliveryLog.skipped(context, "Дублирующий сигнал начисления")
            return
        }
        val noticeId = ("payment_bundle|" + cardId + "|" + day + "|" +
            target.reminderHour + ":" + target.reminderMinute).hashCode()
        val existing = system.activeNotifications.any { it.id == noticeId }
        val entries = group.map { item ->
            java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale("ru"))
                .format(Date(item.dueAt)) + " — " +
                PaymentCurrencyFormatter.amountWithCurrency(item.amount, item.currency)
        }
        val message = entries.joinToString("\n")
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_tab", 2)
            putExtra("open_payment_id", cardId)
        }
        val launch = PendingIntent.getActivity(context,
            ("payment_open|" + cardId).hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("Скоро платёж: " + payment.title)
            .setContentText(entries.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(launch)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        BalanceNotificationSounds.configureLegacySound(
            context, BalanceNotificationSounds.PAYMENT, builder)
        try {
            notifications.notify(noticeId, builder.build())
            PaymentDeliveryLog.posted(context)
            PaymentNotificationRefresh.remember(context, cardId, noticeId,
                target.reminderHour, target.reminderMinute)
            val edit = ledger.edit()
            unseen.forEach { edit.putInt(cardId + "|" + it.id, day) }
            edit.commit()
            if (!existing) {
                ColorOsDirectVibration.afterNotification(context,
                    BalanceNotificationSounds.PAYMENT, channelId)
                PaymentAutomaticSound.afterNotification(context, channelId)
            }
        } catch (_: SecurityException) {
            // Re-delivery is allowed if Android refused permission.
        }
    }
}
