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

/** Only reads the ordinary, opt-in Android listener API. Never bypasses redaction. */
class SberPushNotificationListener : NotificationListenerService() {
    companion object {
        const val ACTION_BALANCE_UPDATED =
            "com.github.russoturissto.buttonapp.app.SBER_BALANCE_UPDATED"
        const val STATE_LOCKED = 1
        const val STATE_SCREEN_OFF = 2
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val bankEnabled = RussianBankCatalog.banks.any {
            it.id != "sber" && BankCardPreferences.enabled(this, it.id)
        }
        if (!SberBalanceStore.isEnabled(this) && !bankEnabled) return
        val cutoff = System.currentTimeMillis() - 15L * 60L * 1_000L
        try {
            activeNotifications?.asSequence()
                ?.filter { it.postTime >= cutoff &&
                    ((it.packageName == SberPushParser.SBER_PACKAGE &&
                        SberBalanceStore.isEnabled(this)) ||
                        (RussianBankCatalog.byPackage(it.packageName)?.let { bank ->
                            bank.id != "sber" && BankCardPreferences.enabled(this, bank.id)
                        } == true)) }
                ?.sortedBy { it.postTime }
                ?.forEach { processBankNotification(it) }
        } catch (_: SecurityException) { /* Access was revoked */ }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            (SberBalanceStore.isEnabled(this) ||
                RussianBankCatalog.banks.any { it.id != "sber" &&
                    BankCardPreferences.enabled(this, it.id) }) &&
            SberBalanceStore.isAccessGranted(this)) {
            try {
                requestRebind(ComponentName(this, SberPushNotificationListener::class.java))
            } catch (_: SecurityException) { /* Android may deny rebinding */ }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) processBankNotification(sbn)
    }

    private fun state(): Int {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        return (if (keyguard.isKeyguardLocked) STATE_LOCKED else 0) or
            (if (!power.isInteractive) STATE_SCREEN_OFF else 0)
    }

    private fun processOtherBankNotification(sbn: StatusBarNotification, bank: RussianBank) {
        if (!BankCardPreferences.enabled(this, bank.id)) return
        val notification = sbn.notification
        if ((notification.flags and
                (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT)) != 0)
            return
        val payload = SberNotificationText.extract(notification)
        val privacy = SberNotificationPrivacy.detect(
            payload.titles, payload.bodies, payload.rubleFieldsMask,
            payload.cueFieldsMask, payload.accountFieldsMask,
            SberNotificationPrivacy.localizedSystemMessage()
        )
        if (privacy == SberNotificationPrivacy.Evidence.SYSTEM_REDACTION) {
            RussianBankDiagnostics.record(this, bank, "Содержимое Push скрыто Android")
            sendBroadcast(Intent(ACTION_BALANCE_UPDATED).setPackage(packageName))
            return
        }
        val assessment = RussianBankPushParser.analyze(
            sbn.packageName, payload.bodies, payload.titles)
        val result = assessment.result
        val status = if (result == null) when (assessment.reason) {
            RussianBankPushParser.Reason.AMBIGUOUS -> "Неоднозначный остаток"
            RussianBankPushParser.Reason.INVALID -> "Некорректная сумма"
            else -> "Push без подтверждённого остатка"
        } else if (RussianBankBalanceStore.save(this, bank, result, sbn.postTime))
            "Баланс обновлён" else "Старый или повторный Push"
        RussianBankDiagnostics.record(this, bank, status)
        sendBroadcast(Intent(ACTION_BALANCE_UPDATED).setPackage(packageName))
    }

    private fun processBankNotification(sbn: StatusBarNotification) {
        val other = RussianBankCatalog.byPackage(sbn.packageName)
        if (other != null && other.id != "sber") {
            processOtherBankNotification(sbn, other)
            return
        }
        // Preserve the established Sber branch and its strict parser unchanged.
        if (!SberBalanceStore.isEnabled(this) ||
            sbn.packageName != SberPushParser.SBER_PACKAGE) return
        val n = sbn.notification
        val remoteFlags = (if (n.contentView != null) 1 else 0) or
            (if (n.bigContentView != null) 2 else 0) or
            (if (n.headsUpContentView != null) 4 else 0)
        val ignored = when {
            (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0 ->
                SberPushDiagnostics.Outcome.IGNORED_GROUP
            (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 ->
                SberPushDiagnostics.Outcome.IGNORED_ONGOING
            else -> null
        }
        val payload = if (ignored != null) SberNotificationText.Payload(
            emptyList(), emptyList(), 0
        ) else SberNotificationText.extract(n)
        val privacy = if (ignored != null) SberNotificationPrivacy.Evidence.NONE
            else SberNotificationPrivacy.detect(
                payload.titles, payload.bodies, payload.rubleFieldsMask,
                payload.cueFieldsMask, payload.accountFieldsMask,
                SberNotificationPrivacy.localizedSystemMessage()
            )
        val result = if (ignored != null || privacy ==
            SberNotificationPrivacy.Evidence.SYSTEM_REDACTION) null
            else SberPushEvaluation.analyze(sbn.packageName, payload)
        val outcome = when {
            ignored != null -> ignored
            privacy == SberNotificationPrivacy.Evidence.SYSTEM_REDACTION ->
                SberPushDiagnostics.Outcome.SYSTEM_REDACTED
            !payload.hasReadableText -> SberPushDiagnostics.Outcome.EMPTY_NOTIFICATION
            !payload.hasBody && result?.result == null ->
                SberPushDiagnostics.Outcome.TITLE_ONLY
            result?.reason == SberPushParser.Reason.AMBIGUOUS_BALANCE ->
                SberPushDiagnostics.Outcome.AMBIGUOUS_BALANCE
            result?.reason == SberPushParser.Reason.AMBIGUOUS_ACCOUNT ->
                SberPushDiagnostics.Outcome.AMBIGUOUS_ACCOUNT
            result?.reason == SberPushParser.Reason.INVALID_BALANCE ->
                SberPushDiagnostics.Outcome.INVALID_BALANCE
            result?.result == null &&
                privacy == SberNotificationPrivacy.Evidence.POSSIBLE_REDACTION ->
                SberPushDiagnostics.Outcome.POSSIBLY_REDACTED
            result?.result == null -> SberPushDiagnostics.Outcome.NO_BALANCE
            SberBalanceStore.save(this, result.result, sbn.postTime) ->
                SberPushDiagnostics.Outcome.UPDATED
            else -> SberPushDiagnostics.Outcome.OLD_NOTIFICATION
        }
        SberPushDiagnostics.record(this, sbn.postTime, outcome,
            payload.fieldsMask, payload.rubleFieldsMask, payload.cueFieldsMask,
            payload.accountFieldsMask, state(), sbn.id, privacy,
            payload.fieldLengths, remoteFlags)
        sendBroadcast(Intent(ACTION_BALANCE_UPDATED).setPackage(packageName))
    }
}
