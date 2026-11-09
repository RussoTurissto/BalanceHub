package com.github.russoturissto.buttonapp.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar
import org.json.JSONArray

/** Android clears scheduled alarms on reboot; restore future reminders. */
class PaymentBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Process one-off payments that expired while the phone was turned off.
        PaymentArchiveStore.archiveDue(context)
        EventScheduler.restoreAfterBoot(context)
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val records = try {
            JSONArray(prefs.getString("payment_reminders", "[]") ?: "[]")
        } catch (_: Exception) { return }
        val now = System.currentTimeMillis()
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (index in 0 until records.length()) {
            try {
                val payment = records.getJSONObject(index)
                val id = payment.getString("id")
                val recurrence = payment.optString("recurrence", "once")
                val lead = payment.getInt("leadDays")
                val due = Calendar.getInstance().apply {
                    timeInMillis = payment.getLong("dueAt")
                }
                var trigger = due.timeInMillis - lead * 86400000L
                if (recurrence == "monthly" || recurrence == "weekly") {
                    while (trigger <= now) {
                        if (recurrence == "monthly") due.add(Calendar.MONTH, 1)
                        else due.add(Calendar.DAY_OF_YEAR, 7)
                        trigger = due.timeInMillis - lead * 86400000L
                    }
                }
                if (recurrence == "once" && due.timeInMillis > now) {
                    PaymentArchiveScheduler.schedule(context, id, due.timeInMillis)
                }
                if (trigger <= now) continue
                val alert = Intent(context, PaymentReminderReceiver::class.java).apply {
                    putExtra("id", id)
                    putExtra("title", payment.getString("title"))
                    putExtra("amount", payment.getString("amount"))
                    putExtra("currency", payment.getString("currency"))
                    putExtra("dueAt", due.timeInMillis)
                    putExtra("leadDays", lead)
                    putExtra("recurrence", recurrence)
                }
                val pending = PendingIntent.getBroadcast(
                    context, id.hashCode(), alert,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } catch (_: Exception) {
                // Corrupt legacy reminder must not block other reminders.
            }
        }
    }
}
