package com.github.russoturissto.buttonapp.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import android.os.PowerManager

/** Independent end-to-end tests: no fake payment or event records are created. */
class DiagnosticReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val PREFS = "balancehub_reminder_diagnostics"
        fun schedule(context: Context, category: String) {
            val key = if (category == BalanceNotificationSounds.EVENT) "event" else "payment"
            val scheduled = System.currentTimeMillis() + 120_000L
            val intent = Intent(context, DiagnosticReminderReceiver::class.java).apply {
                putExtra("category", category)
                putExtra("expected", scheduled)
            }
            val pending = PendingIntent.getBroadcast(context,
                ("diagnostic_" + key).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val mode = ExactReminderAlarms.schedule(context, scheduled, pending)
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putLong("${key}_planned", scheduled)
                putString("${key}_mode", mode)
                putBoolean("${key}_exact_allowed", ExactReminderAlarms.allowed(context))
                putBoolean("${key}_battery_unrestricted",
                    power.isIgnoringBatteryOptimizations(context.packageName))
                remove("${key}_delivered")
                remove("${key}_posted")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val category = intent.getStringExtra("category") ?: return
        if (category != BalanceNotificationSounds.EVENT &&
            category != BalanceNotificationSounds.PAYMENT) return
        val key = if (category == BalanceNotificationSounds.EVENT) "event" else "payment"
        val expected = intent.getLongExtra("expected", -1L)
        val actual = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (expected <= 0L || prefs.getLong("${key}_planned", -1) != expected) return
        prefs.edit { putLong("${key}_delivered", actual) }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return
        val channel = BalanceNotificationSounds.ensureChannel(context, category)
        val title = if (key == "payment") "Тест уведомления: платёж"
            else "Тест уведомления: событие"
        val delay = (actual - expected).coerceAtLeast(0L) / 1000L
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText("Срабатывание: задержка $delay сек")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(("diagnostic_" + key).hashCode(), notification)
            prefs.edit { putLong("${key}_posted", System.currentTimeMillis()) }
        } catch (_: SecurityException) { /* Android notification permission was denied. */ }
    }
}
