package com.github.russoturissto.buttonapp.app

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * All parsing happens locally. Other applications' notifications are discarded
 * before their contents are read. No raw bank message is stored or uploaded.
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
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bodies = listOfNotNull(
            extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
        ).filter { it.isNotBlank() }.distinct()

        val outcome = if (bodies.isEmpty()) {
            SberPushDiagnostics.Outcome.EMPTY_NOTIFICATION
        } else {
            val analyses = bodies.map { body ->
                SberPushParser.analyze(sbn.packageName, "$title\n$body")
            }
            val accepted = analyses.mapNotNull { it.result }.distinct()
            when {
                accepted.map { it.kopecks to it.lastFour }.distinct().size > 1 ->
                    SberPushDiagnostics.Outcome.AMBIGUOUS_BALANCE
                accepted.isNotEmpty() -> {
                    if (SberBalanceStore.save(this, accepted.first(), sbn.postTime)) {
                        SberPushDiagnostics.Outcome.UPDATED
                    } else {
                        SberPushDiagnostics.Outcome.OLD_NOTIFICATION
                    }
                }
                else -> when {
                    analyses.any { it.reason == SberPushParser.Reason.AMBIGUOUS_BALANCE } ->
                        SberPushDiagnostics.Outcome.AMBIGUOUS_BALANCE
                    analyses.any { it.reason == SberPushParser.Reason.AMBIGUOUS_ACCOUNT } ->
                        SberPushDiagnostics.Outcome.AMBIGUOUS_ACCOUNT
                    analyses.any { it.reason == SberPushParser.Reason.INVALID_BALANCE } ->
                        SberPushDiagnostics.Outcome.INVALID_BALANCE
                    else -> SberPushDiagnostics.Outcome.NO_BALANCE
                }
            }
        }
        SberPushDiagnostics.record(this, sbn.postTime, outcome)
        sendBroadcast(Intent(ACTION_BALANCE_UPDATED).setPackage(packageName))
    }
}
