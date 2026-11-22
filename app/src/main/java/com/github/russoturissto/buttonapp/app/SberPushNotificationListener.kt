package com.github.russoturissto.buttonapp.app

import android.app.KeyguardManager
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import java.lang.ref.WeakReference
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Only reads the ordinary, opt-in Android listener API. Never bypasses redaction. */
class SberPushNotificationListener : NotificationListenerService() {
    companion object {
        const val ACTION_BALANCE_UPDATED =
            "com.github.russoturissto.buttonapp.app.SBER_BALANCE_UPDATED"
        const val STATE_LOCKED = 1
        const val STATE_SCREEN_OFF = 2

        @Volatile private var liveListener: WeakReference<SberPushNotificationListener>? = null
        @Volatile private var lastRebindRequest = 0L

        fun isConnected(): Boolean = liveListener?.get()?.listenerConnected == true

        /** Only invoke the platform API after the listener has connected. */
        fun refreshForeground(context: Context) {
            val listener = liveListener?.get()
            if (listener?.listenerConnected == true) {
                listener.scanActiveBankNotifications()
                return
            }
            val anyEnabled = RussianBankCatalog.banks.any {
                BankCardPreferences.enabled(context, it.id)
            }
            if (!anyEnabled || !SberBalanceStore.isAccessGranted(context) ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastRebindRequest < 60_000L) return
            lastRebindRequest = now
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(context, SberPushNotificationListener::class.java))
            } catch (_: SecurityException) { /* System controls binding. */ }
              catch (_: IllegalStateException) { /* Not available on this device. */ }
        }
    }

    @Volatile private var listenerConnected = false
    private val handledFingerprint = LinkedHashMap<String, Int>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        listenerConnected = true
        liveListener = WeakReference(this)
        scanActiveBankNotifications()
    }

    override fun onListenerDisconnected() {
        listenerConnected = false
        if (liveListener?.get() === this) liveListener = null
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

    override fun onDestroy() {
        listenerConnected = false
        if (liveListener?.get() === this) liveListener = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) dispatchBankNotification(sbn, recovered = false)
    }

    /**
     * Foreground reconciliation recovers notifications missed while an activity is
     * visible (also after returning from the shade). No bank app is launched and
     * no private notification content is persisted.
     */
    private fun scanActiveBankNotifications() {
        if (!listenerConnected) return
        val cutoff = System.currentTimeMillis() - 15L * 60L * 1_000L
        try {
            activeNotifications?.asSequence()?.filter { it.postTime >= cutoff }
                ?.sortedBy { it.postTime }
                ?.forEach { dispatchBankNotification(it, recovered = true) }
        } catch (_: SecurityException) { /* Permission revoked. */ }
          catch (_: RuntimeException) { /* Device temporarily lost the listener. */ }
    }

    private fun dispatchBankNotification(sbn: StatusBarNotification, recovered: Boolean) {
        val bank = RussianBankCatalog.byPackage(sbn.packageName) ?: return
        if (!BankCardPreferences.enabled(this, bank.id)) return
        val payload = SberNotificationText.extract(sbn.notification)
        // Fingerprints are memory-only. No text, amounts, keys or account data
        // are written to diagnostics, and identical callbacks are not replayed.
        val fingerprint = ((31 * sbn.postTime.hashCode() +
            sbn.notification.flags) * 31 + payload.titles.hashCode()) * 31 +
            payload.bodies.hashCode()
        val key = sbn.key
        if (!BankPushRescanPolicy.shouldProcess(handledFingerprint[key],
                fingerprint, sbn.postTime, System.currentTimeMillis(), recovered)) return
        handledFingerprint[key] = fingerprint
        if (handledFingerprint.size > 160) {
            handledFingerprint.remove(handledFingerprint.keys.first())
        }
        val probed = BankPushForegroundProbe.arrival(this, bank.id,
            recovered, sbn.postTime)
        processBankNotification(sbn)
        if (probed) {
            val status = if (bank.id == "sber")
                SberPushDiagnostics.latest(this)?.outcome?.name ?: "Обработано"
            else RussianBankDiagnostics.last(this, bank) ?: "Обработано"
            BankPushForegroundProbe.outcome(this, bank.id, status)
        }
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
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            RussianBankDiagnostics.record(this, bank, "Групповое уведомление",
                deliveryState = state(), postedAt = sbn.postTime,
                notificationKey = sbn.key)
            return
        }
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            RussianBankDiagnostics.record(this, bank, "Постоянное уведомление",
                deliveryState = state(), postedAt = sbn.postTime,
                notificationKey = sbn.key)
            return
        }
        val payload = SberNotificationText.extract(notification)
        val privacy = SberNotificationPrivacy.detect(
            payload.titles, payload.bodies, payload.rubleFieldsMask,
            payload.cueFieldsMask, payload.accountFieldsMask,
            SberNotificationPrivacy.localizedSystemMessage()
        )
        if (privacy == SberNotificationPrivacy.Evidence.SYSTEM_REDACTION) {
            RussianBankDiagnostics.record(this, bank, "Содержимое Push скрыто Android",
                payload, privacy, state(), postedAt = sbn.postTime,
                notificationKey = sbn.key)
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
        RussianBankDiagnostics.record(this, bank, status, payload, privacy, state(),
            postedAt = sbn.postTime, notificationKey = sbn.key)
        sendBroadcast(Intent(ACTION_BALANCE_UPDATED).setPackage(packageName))
    }

    private fun processBankNotification(sbn: StatusBarNotification) {
        val other = RussianBankCatalog.byPackage(sbn.packageName)
        if (other != null && other.id != "sber") {
            processOtherBankNotification(sbn, other)
            return
        }
        if (!SberBalanceStore.isEnabled(this) ||
            sbn.packageName != SberPushParser.SBER_PACKAGE) return
        val n = sbn.notification
        val remoteFlags = (if (n.contentView != null) 1 else 0) or
            (if (n.bigContentView != null) 2 else 0) or
            (if (n.headsUpContentView != null) 4 else 0)
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val groupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val payload = if (ongoing) SberNotificationText.Payload(
            emptyList(), emptyList(), 0
        ) else SberNotificationText.extract(n)
        val privacy = if (ongoing) SberNotificationPrivacy.Evidence.NONE
            else SberNotificationPrivacy.detect(
                payload.titles, payload.bodies, payload.rubleFieldsMask,
                payload.cueFieldsMask, payload.accountFieldsMask,
                SberNotificationPrivacy.localizedSystemMessage()
            )
        val assessment = if (ongoing || privacy ==
            SberNotificationPrivacy.Evidence.SYSTEM_REDACTION) null
            else SberPushEvaluation.analyze(sbn.packageName, payload)
        // A group summary may contain a single genuine balance; summaries with
        // multiple operations are never interpreted as a fresh balance.
        val groupWithoutSingleBalance = groupSummary &&
            assessment?.balances?.size != 1
        val saveResults = if (!ongoing && !groupWithoutSingleBalance &&
            assessment?.reason == SberPushParser.Reason.ACCEPTED) {
            assessment.balances.map { SberBalanceStore.save(this, it, sbn.postTime) }
        } else emptyList()
        val outcome = when {
            ongoing -> SberPushDiagnostics.Outcome.IGNORED_ONGOING
            privacy == SberNotificationPrivacy.Evidence.SYSTEM_REDACTION ->
                SberPushDiagnostics.Outcome.SYSTEM_REDACTED
            !payload.hasReadableText && payload.hasHistoricMessages ->
                SberPushDiagnostics.Outcome.HISTORIC_ONLY
            groupWithoutSingleBalance -> SberPushDiagnostics.Outcome.IGNORED_GROUP
            !payload.hasReadableText -> SberPushDiagnostics.Outcome.EMPTY_NOTIFICATION
            assessment?.reason == SberPushParser.Reason.AMBIGUOUS_BALANCE ->
                SberPushDiagnostics.Outcome.AMBIGUOUS_BALANCE
            assessment?.reason == SberPushParser.Reason.AMBIGUOUS_ACCOUNT ->
                SberPushDiagnostics.Outcome.AMBIGUOUS_ACCOUNT
            assessment?.reason == SberPushParser.Reason.INVALID_BALANCE ->
                SberPushDiagnostics.Outcome.INVALID_BALANCE
            saveResults.any { it } -> SberPushDiagnostics.Outcome.UPDATED
            saveResults.isNotEmpty() -> SberPushDiagnostics.Outcome.OLD_NOTIFICATION
            !payload.hasBody -> SberPushDiagnostics.Outcome.TITLE_ONLY
            privacy == SberNotificationPrivacy.Evidence.POSSIBLE_REDACTION ->
                SberPushDiagnostics.Outcome.POSSIBLY_REDACTED
            payload.rubleFieldsMask == 0 ->
                SberPushDiagnostics.Outcome.NO_RUBLE_AMOUNT
            payload.cueFieldsMask == 0 ->
                SberPushDiagnostics.Outcome.NO_BALANCE_LABEL
            else -> SberPushDiagnostics.Outcome.NO_BALANCE
        }
        SberPushDiagnostics.record(this, sbn.postTime, outcome,
            payload.fieldsMask, payload.rubleFieldsMask, payload.cueFieldsMask,
            payload.accountFieldsMask, state(), sbn.id, privacy,
            payload.fieldLengths, remoteFlags)
        sendBroadcast(Intent(ACTION_BALANCE_UPDATED).setPackage(packageName))
    }
}
