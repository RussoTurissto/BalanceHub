package com.github.russoturissto.buttonapp.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** One alarm at the actual due time, distinct from the earlier reminder alarm. */
internal object PaymentArchiveScheduler {
    private fun pending(context: Context, id: String, dueAt: Long): PendingIntent =
        PendingIntent.getBroadcast(context, id.hashCode(),
            Intent(context, PaymentArchiveReceiver::class.java).apply {
                putExtra("id", id)
                putExtra("dueAt", dueAt)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context, id: String, dueAt: Long) {
        if (dueAt <= System.currentTimeMillis()) return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt,
                pending(context, id, dueAt))
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
        if (dueAt <= 0L) return
        PaymentArchiveStore.archiveById(context, id, PaymentArchiveStore.COMPLETED,
            expectedDue = dueAt)
    }
}
