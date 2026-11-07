package com.github.russoturissto.buttonapp.app

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Opt-in Sber Push processing. Only the allowlisted bank package is handled.
 * Text never leaves this callback or enters persistent diagnostic storage.
 */
class SberPushNotificationListener : NotificationListenerService() {
    companion object {
        const val ACTION_BALANCE_UPDATED =
            "com.github.russoturissto.buttonapp.app.SBER_BALANCE_UPDATED"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !SberBalanceStore.isEnabled(this) ||
            sbn.packageName != SberPushParser.SBER_PACKAGE) return
        // Group headers and ongoing status notifications can contain no body.
        // They should not replace a meaningful last financial notification.
        if ((sbn.notification.flags and (Notification.FLAG_ONGOING_EVENT or
                Notification.FLAG_GROUP_SUMMARY)) != 0) return

        val payload = SberNotificationText.extract(sbn.notification)
        val assessment = SberPushEvaluation.analyze(sbn.packageName, payload)
        val outcome = when {
            !payload.hasReadableText -> SberPushDiagnostics.Outcome.EMPTY_NOTIFICATION
            !payload.hasBody && assessment.result == null ->
                SberPushDiagnostics.Outcome.TITLE_ONLY
            assessment.reason == SberPushParser.Reason.AMBIGUOUS_BALANCE ->
                SberPushDiagnostics.Outcome.AMBIGUOUS_BALANCE
            assessment.reason == SberPushParser.Reason.AMBIGUOUS_ACCOUNT ->
                SberPushDiagnostics.Outcome.AMBIGUOUS_ACCOUNT
            assessment.reason == SberPushParser.Reason.INVALID_BALANCE ->
                SberPushDiagnostics.Outcome.INVALID_BALANCE
            assessment.result == null -> SberPushDiagnostics.Outcome.NO_BALANCE
            SberBalanceStore.save(this, assessment.result, sbn.postTime) ->
                SberPushDiagnostics.Outcome.UPDATED
            else -> SberPushDiagnostics.Outcome.OLD_NOTIFICATION
        }
        SberPushDiagnostics.record(this, sbn.postTime, outcome, payload.fieldsMask)
        sendBroadcast(Intent(ACTION_BALANCE_UPDATED).setPackage(packageName))
    }
}
