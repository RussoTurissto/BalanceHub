package com.github.russoturissto.buttonapp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build

/**
 * Payments only. A new Android sound channel is a candidate, never the active
 * channel, until a person hears a real notification and explicitly accepts it.
 * Existing payment reminders continue using the previously confirmed channel.
 */
internal object PaymentToneChange {
    private const val NAME = "balancehub_notification_sounds"
    private const val PENDING_ID = "payment_sound_trial_id"
    private const val PENDING_KEY = "payment_sound_trial_key"
    private const val PENDING_GAIN = "payment_sound_trial_gain"
    private const val PENDING_REVISION = "payment_sound_trial_revision"
    private const val PENDING_TESTED = "payment_sound_trial_tested"
    private const val PREVIOUS_ID = "payment_previous_working_channel_id"
    private const val CATEGORY = BalanceNotificationSounds.PAYMENT

    data class Trial(val id: String, val key: String, val gain: Int, val tested: Boolean)

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun previousWorkingId(context: Context): String? =
        prefs(context).getString(PREVIOUS_ID, null)
            ?.takeIf { StableNotificationChannels.belongsToCategory(it, CATEGORY) }

    fun pending(context: Context): Trial? {
        val prefs = prefs(context)
        val id = prefs.getString(PENDING_ID, null) ?: return null
        if (!StableNotificationChannels.belongsToCategory(id, CATEGORY)) return null
        val key = prefs.getString(PENDING_KEY, null) ?: return null
        if (key != "system" && key != "silent" &&
            BalanceNotificationSounds.tones.none { it.key == key }) return null
        return Trial(id, key, prefs.getInt(PENDING_GAIN, 100).coerceIn(0, 100),
            prefs.getBoolean(PENDING_TESTED, false))
    }

    private fun selectedUri(context: Context, key: String, gain: Int): Uri? {
        if (key == "silent" || (key != "system" && gain == 0)) return null
        if (key == "system")
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val original = BalanceSystemToneStore.uri(context, key, gain) ?: return null
        return if (BalanceNotificationSounds.colorOsCompatibility(context))
            BalanceSystemToneStore.pickerCompatibleUri(context, original) ?: original
        else original
    }

    private fun clearPending(editor: android.content.SharedPreferences.Editor) = editor
        .remove(PENDING_ID).remove(PENDING_KEY).remove(PENDING_GAIN)
        .remove(PENDING_REVISION).remove(PENDING_TESTED)

    /** Never touches the active channel or its saved selected sound. */
    @Synchronized
    fun begin(context: Context, key: String, gain: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return "Проверка канала доступна на Android 8 и новее"
        if (key != "silent" && key != "system" &&
            BalanceNotificationSounds.tones.none { it.key == key })
            return "Неизвестная мелодия платежей"
        val level = gain.coerceIn(0, 100)
        val currentId = BalanceNotificationSounds.ensureChannel(context, CATEGORY)
        val old = manager(context).getNotificationChannel(currentId)
            ?: return "Действующий канал платежей недоступен"
        if (old.importance < NotificationManager.IMPORTANCE_DEFAULT)
            return "Канал платежей отключён в Android. Сначала разрешите звуковые уведомления."
        val desired = selectedUri(context, key, level)
        if (key != "silent" && (key == "system" || level > 0) && desired == null)
            return "Не удалось предоставить новую мелодию Android"
        if (desired != null && key != "system") {
            val issue = BalanceNotificationSounds.audioProbe(context, desired)
            if (issue != null) return "Мелодия недоступна: " + issue
        }
        val revision = prefs(context).getInt("channel_revision_" + CATEGORY, 0)
            .coerceAtLeast(0) + 1
        val id = StableNotificationChannels.freshId(CATEGORY, revision)
        val candidate = NotificationChannel(id, "Платежи · проверка нового звука",
            old.importance)
        candidate.setSound(desired, if (desired == null) null else AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        val mode = BalanceNotificationSounds.vibrationMode(context, CATEGORY)
        if (BalanceNotificationSounds.colorOsCompatibility(context) ||
            !NotificationVibrationMode.enabled(mode)) candidate.enableVibration(false)
        else NotificationChannelVibration.apply(candidate,
            NotificationVibrationMode.pattern(mode))
        return try {
            manager(context).createNotificationChannel(candidate)
            if (manager(context).getNotificationChannel(id) == null)
                return "Android не создал пробный канал платежей"
            // Do not remove the previous trial until the replacement is durable.
            val previous = pending(context)
            val saved = prefs(context).edit()
                .putString(PENDING_ID, id).putString(PENDING_KEY, key)
                .putInt(PENDING_GAIN, level).putInt(PENDING_REVISION, revision)
                .putBoolean(PENDING_TESTED, false).commit()
            if (!saved) {
                manager(context).deleteNotificationChannel(id)
                return "Не удалось запомнить пробный канал"
            }
            if (previous != null && previous.id != id) {
                try { manager(context).deleteNotificationChannel(previous.id) }
                catch (_: Exception) { /* The active channel is unaffected. */ }
            }
            null
        } catch (error: Exception) {
            try { manager(context).deleteNotificationChannel(id) } catch (_: Exception) { }
            "Не удалось подготовить проверку: " + error.javaClass.simpleName
        }
    }

    /** The test must be sent from the exact prospective channel, not the active one. */
    @Synchronized
    fun markTested(context: Context, id: String): Boolean {
        val trial = pending(context) ?: return false
        return trial.id == id && prefs(context).edit().putBoolean(PENDING_TESTED, true).commit()
    }

    /** Validate the current OS state again after the user visited ColorOS settings. */
    fun problem(context: Context, trial: Trial): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return "Системные каналы недоступны"
        val channel = manager(context).getNotificationChannel(trial.id)
            ?: return "Пробный канал больше не существует"
        if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT)
            return "ColorOS отключила звуковые уведомления пробного канала"
        val expected = selectedUri(context, trial.key, trial.gain)
        val actual = channel.sound
        if (!NotificationToneIdentity.same(expected?.toString(), actual?.toString()))
            return "ColorOS назначила другой звук. Выберите нужную мелодию в настройках пробного канала Android."
        if (actual != null && trial.key != "system") {
            val issue = BalanceNotificationSounds.audioProbe(context, actual)
            if (issue != null) return "Android не читает выбранный WAV: " + issue
        }
        // In direct vibration mode Android channel vibration would suppress the
        // known-good ColorOS motor route and may replace LONG with default.
        if (BalanceNotificationSounds.colorOsCompatibility(context) &&
            channel.shouldVibrate())
            return "Отключите вибрацию пробного системного канала: BalanceHub запускает выбранный длинный или другой рисунок отдельно."
        return null
    }

    /**
     * An explicit user confirmation is necessary: API sound metadata does not
     * prove that the OnePlus speaker really played the chosen ringtone.
     */
    @Synchronized
    fun accept(context: Context): String? {
        val trial = pending(context) ?: return "Нет неподтверждённой мелодии"
        if (!trial.tested) return "Сначала отправьте реальное тестовое уведомление"
        problem(context, trial)?.let { return it }
        val oldId = BalanceNotificationSounds.channelId(context, CATEGORY)
        val revision = prefs(context).getInt(PENDING_REVISION, 0)
        if (revision <= 0) return "Не удалось проверить поколение канала"
        val saved = clearPending(prefs(context).edit())
            .putString("channel_active_id_" + CATEGORY, trial.id)
            .putInt("channel_revision_" + CATEGORY, revision)
            .putString(CATEGORY, trial.key)
            .putInt("volume_" + CATEGORY, trial.gain)
            .putString(PREVIOUS_ID, oldId)
            .commit()
        // Deliberately retain the former working payment channel for rollback.
        // Unused-channel cleanup excludes that channel; events/updates untouched.
        return if (saved) null else "Не удалось применить подтверждённую мелодию"
    }

    /** Cancel only the trial channel; never change active payment preferences. */
    @Synchronized
    fun cancel(context: Context) {
        val trial = pending(context) ?: return
        if (!clearPending(prefs(context).edit()).commit()) return
        if (trial.id != BalanceNotificationSounds.channelId(context, CATEGORY)) {
            try { manager(context).deleteNotificationChannel(trial.id) }
            catch (_: Exception) { }
        }
    }
}
