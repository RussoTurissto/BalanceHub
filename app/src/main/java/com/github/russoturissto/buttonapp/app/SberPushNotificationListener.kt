package com.github.russoturissto.buttonapp.app

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Opt-in notification integration. Android grants notification access globally;
 * this service immediately discards all notifications except the supported bank.
 */
class SberPushNotificationListener : NotificationListenerService() {
    companion object {
        const val ACTION_BALANCE_UPDATED =
            "com.github.russoturissto.buttonapp.app.SBER_BALANCE_UPDATED"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !SberBalanceStore.isEnabled(this) ||
            sbn.packageName != SberPushParser.SBER_PACKAGE) return
        if ((sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bodies = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
        ).filter { it.isNotBlank() }.distinct()
        val parsed = bodies.firstNotNullOfOrNull { body ->
            SberPushParser.parse(sbn.packageName, "$title\n$body")
        } ?: return
        if (SberBalanceStore.save(this, parsed, sbn.postTime)) {
            sendBroadcast(Intent(ACTION_BALANCE_UPDATED).setPackage(packageName))
        }
    }
}
