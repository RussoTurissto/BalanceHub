package com.github.russoturissto.buttonapp.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray

/** One system alert per local calendar day during an active payment cycle. */
class PaymentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val due = intent.getLongExtra("dueAt", -1L)
        val test = id == "balancehub-test-notification"
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val item = if (!test) try {
            val items = JSONArray(prefs.getString("payment_reminders", "[]") ?: "[]")
            (0 until items.length()).mapNotNull { items.optJSONObject(it) }
                .firstOrNull { it.optString("id") == id &&
                    it.optLong("dueAt", -1L) == due }
        } catch (_: Exception) { null } else null
        if (!test && item == null) return
        if (!test) PaymentDeliveryLog.received(context,
            intent.getLongExtra(PaymentDeliveryLog.EXTRA_EXPECTED_AT, -1L), now, due)
        val leadDays = if (test) 0 else item!!.optInt("leadDays", 0)
        val first = if (test) now else DailyReminderPolicy.firstPayment(due, leadDays)
        if (!test) {
            when (PaymentDeliveryPolicy.status(first, due, now)) {
                PaymentDeliveryPolicy.Status.EARLY -> {
                    val retryAt = PaymentDeliveryPolicy.earlyRetryAt(first, due, now)
                        ?: return
                    val mode = PaymentAlarmScheduler.rearmEarly(context, intent, retryAt)
                    PaymentDeliveryLog.skipped(context,
                        if (mode == "expired" || mode == "invalid")
                            "Ранний сигнал: не удалось повторно запланировать"
                        else "Сигнал раньше срока — переназначен")
                    return
                }
                PaymentDeliveryPolicy.Status.EXPIRED -> {
                    PaymentDeliveryLog.skipped(context, "Срок допустимой доставки истёк")
                    return
                }
                PaymentDeliveryPolicy.Status.INVALID -> {
                    PaymentDeliveryLog.skipped(context, "Некорректный срок платежа")
                    return
                }
                PaymentDeliveryPolicy.Status.READY -> Unit
            }
        }
        if (!test) DailyReminderPolicy.following(first, due, now)?.let {
            PaymentAlarmScheduler.scheduleFollowingDay(context, intent, it)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
            if (!test) PaymentDeliveryLog.skipped(context, "Нет разрешения на уведомления")
            return
        }
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) {
            if (!test) PaymentDeliveryLog.skipped(context, "Уведомления отключены Android")
            return
        }
        val channelId = BalanceNotificationSounds.ensureChannel(
            context, BalanceNotificationSounds.PAYMENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            if (manager.getNotificationChannel(channelId)?.importance ==
                NotificationManager.IMPORTANCE_NONE) {
                if (!test) PaymentDeliveryLog.skipped(context, "Канал платежей выключен")
                return
            }
        }
        val title = intent.getStringExtra("title") ?: "Предстоящий платёж"
        val amount = intent.getStringExtra("amount").orEmpty()
        val currency = intent.getStringExtra("currency").orEmpty()
        val text = PaymentCurrencyFormatter.amountWithCurrency(amount, currency)
        val noticeId = ReminderNoticeIds.payment(id, due,
            DailyReminderPolicy.dayKey(now))
        val reservation = if (!test) DailyReminderLedger.reserve(context,
            "payment", id, due, now, noticeId) else null
        if (!test && reservation == null) {
            PaymentDeliveryLog.skipped(context, "Дубль в тот же день предотвращён")
            return
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("Скоро платёж: $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Не забудьте оплатить: $title — $text"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        BalanceNotificationSounds.configureLegacySound(
            context, BalanceNotificationSounds.PAYMENT, builder)
        try {
            notifications.notify(noticeId, builder.build())
            if (!test) PaymentDeliveryLog.posted(context)
            ColorOsDirectVibration.afterNotification(
                context, BalanceNotificationSounds.PAYMENT, channelId)
            // Only an explicitly accepted silent payment channel can play its
            // own WAV. The original ColorOS channel never gets double audio.
            PaymentAutomaticSound.afterNotification(context, channelId)
            reservation?.previousNotificationId?.takeIf { it != noticeId }?.let {
                notifications.cancel(it)
            }
        } catch (_: SecurityException) {
            if (!test) PaymentDeliveryLog.skipped(context, "Android отклонил отправку")
            if (reservation != null) DailyReminderLedger.rollback(
                context, "payment", id, reservation)
        }
    }
}
