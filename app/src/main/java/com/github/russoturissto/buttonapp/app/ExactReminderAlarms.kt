package com.github.russoturissto.buttonapp.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/** Honor the user's exact-alarm permission; never silently cancel reminders. */
internal object ExactReminderAlarms {
    fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
    }

    fun schedule(context: Context, triggerAt: Long, pending: PendingIntent) {
        if (triggerAt <= System.currentTimeMillis()) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (allowed(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                return
            } catch (_: SecurityException) {
                // Permission was revoked while an alarm was being prepared.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }
}
