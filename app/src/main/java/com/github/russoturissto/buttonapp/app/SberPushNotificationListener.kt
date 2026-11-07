package com.github.russoturissto.buttonapp.app

import android.app.KeyguardManager
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Receives bank Push independently of Activity lifecycle, including when the
 * phone is locked or the screen is off, subject to Android system restrictions.
 * No raw text is stored, transmitted, logged, or included in the broadcast.
 */
class SberPushNotificationListener : NotificationListenerService() {
    companion object {
        const val ACTION_BALANCE_UPDATED =
            "com.github.russoturissto.buttonapp.app.SBER_BALANCE_UPDATED"
        const val STATE_LOCKED = 1
        const val STATE_SCREEN_OFF = 2
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!SberBalanceStore.isEnabled(this)) return
        // Recover a recent active notification when Android reconnects the
        // listener after an OS process restart, but never treat old Push as new.
        val cutoff = System.currentTimeMillis() - 15L * 60L * 1_000L
        try {
            activeNotifications?.asSequence()
                ?.filter { it.packageName == SberPushParser.SBER_PACKAGE &&
                    it.postTime >= cutoff }
                ?.sortedBy { it.postTime }
                ?.forEach { processBankNotification(it) }
        } catch (_: SecurityException) {
            // Access may have been revoked while Android was reconnecting.
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            SberBalanceStore.isEnabled(this) && SberBalanceStore.isAccessGranted(this)) {
            try {
                requestRebind(ComponentName(this, SberPushNotificationListener::class.java))
            } catch (_: SecurityException) { /* OS may deny rebinding */ }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) processBankNotification(sbn)
    }

    private fun deliveryState(): Int {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        var state = 0
        if (keyguard.isKeyguardLocked) state = state or STATE_LOCKED
        if (!power.isInteractive) state = state or STATE_SCREEN_OFF
        return state
    }

    private fun processBankNotification(sbn: StatusBarNotification) {
        if (!SberBalanceStore.isEnabled(this) ||
            sbn.packageName != SberPushParser.SBER_PACKAGE) return
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
        SberPushDiagnostics.record(this, sbn.postTime, outcome, payload.fieldsMask,
            payload.rubleFieldsMask, payload.cueFieldsMask, payload.accountFieldsMask,
            deliveryState())
        sendBroadcast(Intent(ACTION_BALANCE_UPDATED).setPackage(packageName))
    }
}
