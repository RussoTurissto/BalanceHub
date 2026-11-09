package com.github.russoturissto.buttonapp.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.annotation.SuppressLint
import java.util.Calendar
import org.json.JSONArray

/** Restore pending alarms after reboot, app replacement, clock or permission changes. */
@SuppressLint("UseKtx")
class PaymentBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        PaymentArchiveStore.archiveDue(context)
        EventScheduler.restoreAfterBoot(context)
        val prefs = context.getSharedPreferences("balancehub_data", Context.MODE_PRIVATE)
        val items = try {
            JSONArray(prefs.getString("payment_reminders", "[]") ?: "[]")
        } catch (_: Exception) { return }
        val now = System.currentTimeMillis()
        var advanced = false
        for (index in 0 until items.length()) {
            val payment = items.optJSONObject(index) ?: continue
            try {
                val recur = payment.optString("recurrence", "once")
                if (recur != "weekly" && recur != "monthly") continue
                val due = Calendar.getInstance().apply {
                    timeInMillis = payment.getLong("dueAt")
                }
                while (due.timeInMillis <= now) {
                    if (recur == "weekly") due.add(Calendar.DAY_OF_YEAR, 7)
                    else due.add(Calendar.MONTH, 1)
                    advanced = true
                }
                payment.put("dueAt", due.timeInMillis)
            } catch (_: Exception) { /* Ignore invalid records, keep others. */ }
        }
        // Commit before any near-immediate alarm can be delivered.
        if (advanced && !prefs.edit().putString("payment_reminders", items.toString()).commit())
            return
        for (i in 0 until items.length()) {
            val p = items.optJSONObject(i) ?: continue
            try {
                PaymentAlarmScheduler.schedule(context, p.getString("id"),
                    p.getString("title"), p.getString("amount"),
                    p.getString("currency"), p.getLong("dueAt"),
                    p.getInt("leadDays"), p.optString("recurrence", "once"))
            } catch (_: Exception) { /* Another entry must not block this recovery. */ }
        }
    }
}
