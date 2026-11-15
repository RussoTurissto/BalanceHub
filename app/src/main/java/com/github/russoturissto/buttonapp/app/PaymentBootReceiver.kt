package com.github.russoturissto.buttonapp.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.annotation.SuppressLint

/** Restore pending alarms after reboot, app replacement, clock or permission changes. */
@SuppressLint("UseKtx")
class PaymentBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        BalanceNotificationSounds.ensureDefaultChannels(context)
        PaymentArchiveStore.archiveDue(context)
        EventScheduler.restoreAfterBoot(context)
        val items = PaymentArchiveStore.advanceRepeatingForBoot(
            context, System.currentTimeMillis()) ?: return
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
