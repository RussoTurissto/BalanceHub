package com.github.russoturissto.buttonapp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class PaymentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = "payment_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                channelId, "Напоминания о платежах", NotificationManager.IMPORTANCE_DEFAULT
            ))
        }
        val title = intent.getStringExtra("title") ?: "Предстоящий платёж"
        val amount = intent.getStringExtra("amount").orEmpty()
        val currency = intent.getStringExtra("currency").orEmpty()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("Скоро платёж: $title")
            .setContentText("$amount $currency")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Не забудьте оплатить: $title — $amount $currency"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(
                (intent.getStringExtra("id") ?: title).hashCode(), notification
            )
        } catch (_: SecurityException) {
            // Notifications are disabled or permission has not been granted.
        }
    }
}
