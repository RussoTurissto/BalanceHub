package com.github.russoturissto.buttonapp.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray

/** A separate due-time alarm advances cycles after daily reminders finish. */
internal object PaymentArchiveScheduler {
    private fun pending(context: Context, id: String, dueAt: Long): PendingIntent =
        PendingIntent.getBroadcast(context, id.hashCode(),
            Intent(context, PaymentArchiveReceiver::class.java).apply {
                putExtra("id", id)
                putExtra("dueAt", dueAt)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context, id: String, dueAt: Long) {
        val trigger = dueAt + DailyReminderPolicy.DUE_GRACE_MS
        if (trigger <= System.currentTimeMillis()) return
        ExactReminderAlarms.schedule(context, trigger, pending(context, id, dueAt))
    }

    fun cancel(context: Context, id: String) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(pending(context, id, 0L))
    }
}

class PaymentArchiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val dueAt = intent.getLongExtra("dueAt", -1L)
        if (dueAt <= 0L || System.currentTimeMillis() < dueAt) return
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val items = try {
            JSONArray(prefs.getString("payment_reminders", "[]") ?: "[]")
        } catch (_: Exception) { return }
        val item = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            .firstOrNull { it.optString("id") == id &&
                it.optLong("dueAt", -1L) == dueAt } ?: return
        val recurrence = item.optString("recurrence", "once")
        if (recurrence == "once") {
            if (PaymentArchiveStore.archiveById(context, id,
                PaymentArchiveStore.COMPLETED, expectedDue = dueAt)) {
                context.sendBroadcast(Intent(ACTION_PAYMENTS_CHANGED)
                    .setPackage(context.packageName))
            }
            return
        }
        val next = PaymentRecurrencePolicy.nextDue(dueAt, recurrence,
            System.currentTimeMillis()) ?: return
        val title = item.optString("title")
        val amount = item.optString("amount")
        val currency = item.optString("currency")
        val leadDays = item.optInt("leadDays", 0)
        item.put("dueAt", next)
        if (prefs.edit().putString("payment_reminders", items.toString()).commit()) {
            PaymentAlarmScheduler.schedule(context, id, title, amount, currency,
                next, leadDays, recurrence)
            context.sendBroadcast(Intent(ACTION_PAYMENTS_CHANGED)
                .setPackage(context.packageName))
        }
    }
    companion object {
        const val ACTION_PAYMENTS_CHANGED =
            "com.github.russoturissto.buttonapp.app.PAYMENTS_CHANGED"
    }
}
