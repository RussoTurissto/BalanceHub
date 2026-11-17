package com.github.russoturissto.buttonapp.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Index of already-visible payment bundle IDs; never generates a fresh alert. */
internal object PaymentNotificationRefresh {
    private const val PREFS = "balancehub_visible_payment_bundles_v2"

    @Synchronized
    fun remember(context: Context, cardId: String, id: Int, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val records = try { JSONArray(prefs.getString(cardId, "[]")) }
            catch (_: Exception) { JSONArray() }
        val today = DailyReminderPolicy.dayKey(System.currentTimeMillis())
        val newList = JSONArray()
        for (i in 0 until records.length()) {
            val r = records.optJSONObject(i) ?: continue
            if (r.optInt("day") == today && r.optInt("id") != id) newList.put(r)
        }
        newList.put(JSONObject().put("day", today).put("id", id)
            .put("hour", hour).put("minute", minute))
        prefs.edit().putString(cardId, newList.toString()).commit()
    }

    private fun active(context: Context): Set<Int> {
        if (Build.VERSION.SDK_INT < 23) return emptySet()
        val system = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        return system.activeNotifications.map { it.id }.toSet()
    }

    fun refresh(context: Context, payment: PaymentReminder) {
        if (Build.VERSION.SDK_INT < 23) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = try { JSONArray(prefs.getString(payment.id, "[]")) }
            catch (_: Exception) { JSONArray() }
        val now = System.currentTimeMillis()
        val day = DailyReminderPolicy.dayKey(now)
        val from = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -40)
        }.timeInMillis
        val to = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 40)
        }.timeInMillis
        val occurrences = PaymentOccurrenceEngine.occurrences(payment, from, to)
        val displayed = active(context)
        val remaining = JSONArray()
        val notifications = NotificationManagerCompat.from(context)
        for (i in 0 until stored.length()) {
            val record = stored.optJSONObject(i) ?: continue
            val id = record.optInt("id")
            if (record.optInt("day") != day || id !in displayed) continue
            val hour = record.optInt("hour")
            val minute = record.optInt("minute")
            val group = occurrences.filter { occ ->
                !occ.skipped && occ.dueAt >= now - PaymentDeliveryPolicy.LATE_GRACE_MS &&
                    occ.reminderHour == hour && occ.reminderMinute == minute &&
                    occ.firstReminderAt() <= now &&
                    occ.reminderOnDay(now)?.let {
                        it <= now && DailyReminderPolicy.dayKey(it) == day
                    } == true
            }
            if (group.isEmpty()) {
                notifications.cancel(id)
                continue
            }
            val channel = BalanceNotificationSounds.ensureChannel(
                context, BalanceNotificationSounds.PAYMENT)
            val launch = PendingIntent.getActivity(context,
                ("payment_open|" + payment.id).hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("open_tab", 2)
                    putExtra("open_payment_id", payment.id)
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val items = group.sortedBy { it.dueAt }.map {
                java.text.SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
                    .format(Date(it.dueAt)) + " — " +
                    PaymentCurrencyFormatter.amountWithCurrency(it.amount, it.currency)
            }
            val builder = NotificationCompat.Builder(context, channel)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle("Скоро платёж: " + payment.title)
                .setContentText(items.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(items.joinToString("\n")))
                .setContentIntent(launch)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
            try {
                notifications.notify(id, builder.build())
                remaining.put(record)
            } catch (_: SecurityException) {
                // Retain the record for a later refresh after permission changes.
                remaining.put(record)
            }
        }
        prefs.edit().putString(payment.id, remaining.toString()).commit()
    }

    fun cancelAll(context: Context, cardId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val records = try { JSONArray(prefs.getString(cardId, "[]")) }
            catch (_: Exception) { JSONArray() }
        val notifications = NotificationManagerCompat.from(context)
        for (i in 0 until records.length()) {
            val id = records.optJSONObject(i)?.optInt("id") ?: continue
            notifications.cancel(id)
        }
        prefs.edit().remove(cardId).commit()
    }
}
