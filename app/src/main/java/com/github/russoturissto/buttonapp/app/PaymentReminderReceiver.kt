package com.github.russoturissto.buttonapp.app

import android.app.AlarmManager
import android.app.PendingIntent
import java.util.Calendar
import org.json.JSONArray
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class PaymentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = BalanceNotificationSounds.ensureChannel(
            context, BalanceNotificationSounds.PAYMENT)
        val title = intent.getStringExtra("title") ?: "Предстоящий платёж"
        val amount = intent.getStringExtra("amount").orEmpty()
        val currency = intent.getStringExtra("currency").orEmpty()
        val displayAmount = PaymentCurrencyFormatter.amountWithCurrency(amount, currency)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("Скоро платёж: $title")
            .setContentText(displayAmount)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Не забудьте оплатить: $title — $displayAmount"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        BalanceNotificationSounds.configureLegacySound(
            context, BalanceNotificationSounds.PAYMENT, builder)
        val notification = builder.build()
        try {
            NotificationManagerCompat.from(context).notify(
                (intent.getStringExtra("id") ?: title).hashCode(), notification
            )
        } catch (_: SecurityException) {
            // Notifications are disabled or permission has not been granted.
        }
        scheduleNextOccurrence(context, intent)
    }
    private fun scheduleNextOccurrence(context: Context, intent: Intent) {
        val recurrence = intent.getStringExtra("recurrence") ?: "once"
        if (recurrence != "weekly" && recurrence != "monthly") return
        val id = intent.getStringExtra("id") ?: return
        val preferences = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val records = JSONArray(preferences.getString("payment_reminders", "[]") ?: "[]")
        val now = System.currentTimeMillis()
        for (index in 0 until records.length()) {
            val record = records.getJSONObject(index)
            if (record.optString("id") != id || record.optString("recurrence", "once") != recurrence) continue
            val originalDue = record.getLong("dueAt")
            val remindedDue = intent.getLongExtra("dueAt", -1L)
            val isT2 = id.startsWith("t2_subscription_") ||
                id.startsWith("mobile_subscription_")
            // Auto-generated T2 payments keep the current debit visible until
            // it passes, even after the reminder is delivered the day before.
            if ((isT2 && remindedDue < originalDue) ||
                (!isT2 && originalDue != remindedDue)) return
            val leadDays = record.getInt("leadDays")
            val date = Calendar.getInstance().apply { timeInMillis = remindedDue }
            var nextTrigger: Long
            do {
                if (recurrence == "weekly") date.add(Calendar.DAY_OF_YEAR, 7)
                else date.add(Calendar.MONTH, 1)
                nextTrigger = date.timeInMillis - leadDays * 86400000L
            } while (nextTrigger <= now)
            if (isT2) {
                if (originalDue <= now) {
                    val upcoming = Calendar.getInstance().apply {
                        timeInMillis = originalDue
                    }
                    while (upcoming.timeInMillis <= now) upcoming.add(Calendar.MONTH, 1)
                    record.put("dueAt", upcoming.timeInMillis)
                    preferences.edit { putString("payment_reminders", records.toString()) }
                }
            } else {
                record.put("dueAt", date.timeInMillis)
                preferences.edit { putString("payment_reminders", records.toString()) }
            }
            val nextIntent = Intent(context, PaymentReminderReceiver::class.java).apply {
                putExtra("id", id)
                putExtra("title", record.getString("title"))
                putExtra("amount", record.getString("amount"))
                putExtra("currency", record.getString("currency"))
                putExtra("leadDays", leadDays)
                putExtra("dueAt", date.timeInMillis)
                putExtra("recurrence", recurrence)
            }
            val pending = PendingIntent.getBroadcast(context, id.hashCode(), nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pending)
            return
        }
    }
}
