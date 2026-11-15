package com.github.russoturissto.buttonapp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build

/**
 * Trial-and-confirm sound changes for Events and Updates.
 * Payments deliberately retain the proven PaymentToneChange implementation.
 * Candidate channels never replace active channels until the user confirms.
 */
@android.annotation.SuppressLint("UseKtx")
internal object CategoryToneChange {
    private const val PREFS = "balancehub_notification_sounds"

    data class Trial(
        val id: String, val key: String, val gain: Int,
        val tested: Boolean, val automatic: Boolean
    )

    private fun valid(category: String): Boolean =
        category == BalanceNotificationSounds.EVENT ||
            category == BalanceNotificationSounds.UPDATE

    private fun prefix(category: String) = "tone_trial_" + category + "_"
    private fun pendingKey(category: String, field: String) = prefix(category) + field
    private fun previousKey(category: String, field: String) =
        "tone_previous_" + category + "_" + field

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun label(category: String) =
        if (category == BalanceNotificationSounds.EVENT) "событий" else "обновлений"

    fun previousWorkingId(context: Context, category: String): String? {
        if (!valid(category)) return null
        return prefs(context).getString(previousKey(category, "id"), null)
            ?.takeIf { StableNotificationChannels.belongsToCategory(it, category) }
    }

    fun pending(context: Context, category: String): Trial? {
        if (!valid(category)) return null
        val p = prefs(context)
        val id = p.getString(pendingKey(category, "id"), null) ?: return null
        if (!StableNotificationChannels.belongsToCategory(id, category)) return null
        val key = p.getString(pendingKey(category, "key"), null) ?: return null
        if (key != "system" && key != "silent" &&
            BalanceNotificationSounds.tones.none { it.key == key }) return null
        return Trial(id, key, p.getInt(pendingKey(category, "gain"), 100).coerceIn(0, 100),
            p.getBoolean(pendingKey(category, "tested"), false),
            p.getBoolean(pendingKey(category, "automatic"), false))
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

    private fun clearPending(
        editor: android.content.SharedPreferences.Editor, category: String
    ): android.content.SharedPreferences.Editor =
        listOf("id", "key", "gain", "revision", "tested", "automatic")
            .fold(editor) { e, field -> e.remove(pendingKey(category, field)) }

    /** No working sound, channel ID or vibration preference changes at this stage. */
    @Synchronized
    fun begin(context: Context, category: String, key: String, gain: Int,
        automatic: Boolean = PaymentAutomaticSound.shouldOffer(context)): String? {
        if (!valid(category)) return "Неизвестная категория уведомлений"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return "Проверка канала доступна на Android 8 и новее"
        if (automatic && !PaymentAutomaticSound.shouldOffer(context))
            return "Автоматический звук доступен только с совместимостью ColorOS"
        if (key != "system" && key != "silent" &&
            BalanceNotificationSounds.tones.none { it.key == key })
            return "Неизвестная мелодия"
        val level = gain.coerceIn(0, 100)
        val currentId = BalanceNotificationSounds.ensureChannel(context, category)
        val old = manager(context).getNotificationChannel(currentId)
            ?: return "Рабочий канал " + label(category) + " недоступен"
        if (old.importance < NotificationManager.IMPORTANCE_DEFAULT)
            return "Канал отключён в Android: сначала разрешите уведомления"
        val desired = if (automatic) PaymentAutomaticSound.playableUri(context, key, level)
            else selectedUri(context, key, level)
        if (key != "silent" && (key == "system" || level > 0) && desired == null)
            return "Не удалось открыть выбранную мелодию"
        if (desired != null && key != "system") {
            BalanceNotificationSounds.audioProbe(context, desired)?.let {
                return "Мелодия недоступна: " + it
            }
        }
        val reuse = automatic && PaymentAutomaticSound.enabled(context, category) &&
            old.sound == null && !old.shouldVibrate()
        val revision = prefs(context).getInt("channel_revision_" + category, 0)
            .coerceAtLeast(0) + (if (reuse) 0 else 1)
        val id = if (reuse) currentId else StableNotificationChannels.freshId(category, revision)
        return try {
            if (!reuse) {
                val channel = NotificationChannel(id,
                    (if (category == BalanceNotificationSounds.EVENT) "События"
                     else "Обновления") +
                        (if (automatic) " · автоматический звук"
                         else " · проверка нового звука"),
                    old.importance)
                val sound = if (automatic) null else desired
                channel.setSound(sound,
                    if (sound == null) null else AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                val mode = BalanceNotificationSounds.vibrationMode(context, category)
                if (BalanceNotificationSounds.colorOsCompatibility(context) ||
                    !NotificationVibrationMode.enabled(mode)) channel.enableVibration(false)
                else NotificationChannelVibration.apply(channel,
                    NotificationVibrationMode.pattern(mode))
                manager(context).createNotificationChannel(channel)
            }
            if (manager(context).getNotificationChannel(id) == null)
                return "Android не создал пробный канал"
            val former = pending(context, category)
            val saved = prefs(context).edit()
                .putString(pendingKey(category, "id"), id)
                .putString(pendingKey(category, "key"), key)
                .putInt(pendingKey(category, "gain"), level)
                .putInt(pendingKey(category, "revision"), revision)
                .putBoolean(pendingKey(category, "tested"), false)
                .putBoolean(pendingKey(category, "automatic"), automatic)
                .commit()
            if (!saved) {
                if (!reuse) manager(context).deleteNotificationChannel(id)
                return "Не удалось сохранить пробный канал"
            }
            if (former != null && former.id != id && former.id != currentId) {
                try { manager(context).deleteNotificationChannel(former.id) }
                catch (_: Exception) { }
            }
            null
        } catch (e: Exception) {
            if (!reuse) {
                try { manager(context).deleteNotificationChannel(id) }
                catch (_: Exception) { }
            }
            "Не удалось подготовить проверку: " + e.javaClass.simpleName
        }
    }

    @Synchronized
    fun markTested(context: Context, category: String, id: String): Boolean =
        pending(context, category)?.id == id &&
            prefs(context).edit().putBoolean(pendingKey(category, "tested"), true).commit()

    fun problem(context: Context, category: String, trial: Trial): String? {
        if (!valid(category) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return "Системный канал недоступен"
        val channel = manager(context).getNotificationChannel(trial.id)
            ?: return "Пробный канал не найден"
        if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT)
            return "Android отключил уведомления пробного канала"
        if (trial.automatic) {
            if (channel.sound != null)
                return "В пробном канале включён системный звук — возможен двойной сигнал"
            if (channel.shouldVibrate())
                return "Отключите системную вибрацию пробного канала"
            if (trial.key != "silent" &&
                (trial.key == "system" || trial.gain > 0) &&
                PaymentAutomaticSound.playableUri(context, trial.key, trial.gain) == null)
                return "Не удалось открыть мелодию"
            return null
        }
        val expected = selectedUri(context, trial.key, trial.gain)
        if (!NotificationToneIdentity.same(expected?.toString(), channel.sound?.toString()))
            return "Android назначил другой звук. Проверьте пробный канал."
        if (channel.sound != null && trial.key != "system") {
            BalanceNotificationSounds.audioProbe(context, channel.sound!!)?.let {
                return "Android не читает WAV: " + it
            }
        }
        if (BalanceNotificationSounds.colorOsCompatibility(context) &&
            channel.shouldVibrate())
            return "Отключите вибрацию пробного канала для прямой вибрации BalanceHub"
        return null
    }

    /** Only a tested, confirmed channel can replace the current one. */
    @Synchronized
    fun accept(context: Context, category: String): String? {
        val trial = pending(context, category) ?: return "Нет неподтверждённой мелодии"
        if (!trial.tested) return "Сначала отправьте тестовое уведомление"
        problem(context, category, trial)?.let { return it }
        val p = prefs(context)
        val revision = p.getInt(pendingKey(category, "revision"), 0)
        if (revision <= 0) return "Не удалось проверить номер канала"
        val oldId = BalanceNotificationSounds.channelId(context, category)
        val editor = clearPending(p.edit(), category)
            .putString("channel_active_id_" + category, trial.id)
            .putInt("channel_revision_" + category, revision)
            .putString(category, trial.key)
            .putInt("volume_" + category, trial.gain)
            .putBoolean(PaymentAutomaticSound.enabledKey(category), trial.automatic)
        if (oldId != trial.id) {
            editor.putString(previousKey(category, "id"), oldId)
                .putString(previousKey(category, "key"),
                    BalanceNotificationSounds.selected(context, category))
                .putInt(previousKey(category, "gain"),
                    BalanceNotificationSounds.volume(context, category))
                .putBoolean(previousKey(category, "automatic"),
                    PaymentAutomaticSound.enabled(context, category))
        }
        return if (editor.commit()) null else "Не удалось подтвердить новую мелодию"
    }

    fun canRestorePrevious(context: Context, category: String): Boolean {
        if (!valid(category) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            !PaymentAutomaticSound.enabled(context, category)) return false
        val p = prefs(context)
        if (p.getBoolean(previousKey(category, "automatic"), true)) return false
        val id = previousWorkingId(context, category) ?: return false
        val channel = manager(context).getNotificationChannel(id) ?: return false
        return channel.importance >= NotificationManager.IMPORTANCE_DEFAULT &&
            channel.sound != null && p.getString(previousKey(category, "key"), null) != null
    }

    fun previousSoundTitle(context: Context, category: String): String {
        val key = prefs(context).getString(previousKey(category, "key"), null)
        return when (key) {
            "system" -> "Системный звук"
            "silent" -> "Без звука"
            else -> BalanceNotificationSounds.tones.firstOrNull { it.key == key }
                ?.title ?: "Предыдущая мелодия"
        }
    }

    @Synchronized
    fun restorePrevious(context: Context, category: String): String? {
        if (!canRestorePrevious(context, category))
            return "Прежний рабочий канал недоступен. Текущий звук сохранён."
        val p = prefs(context)
        val oldId = previousWorkingId(context, category) ?: return "Канал не найден"
        val key = p.getString(previousKey(category, "key"), null) ?: return "Мелодия не найдена"
        val gain = p.getInt(previousKey(category, "gain"), 100).coerceIn(0, 100)
        cancel(context, category)
        val currentId = BalanceNotificationSounds.channelId(context, category)
        val saved = p.edit()
            .putString("channel_active_id_" + category, oldId)
            .putString(category, key)
            .putInt("volume_" + category, gain)
            .putBoolean(PaymentAutomaticSound.enabledKey(category), false)
            .putString(previousKey(category, "id"), currentId)
            .putString(previousKey(category, "key"),
                BalanceNotificationSounds.selected(context, category))
            .putInt(previousKey(category, "gain"),
                BalanceNotificationSounds.volume(context, category))
            .putBoolean(previousKey(category, "automatic"), true)
            .commit()
        return if (saved) null else "Не удалось вернуть предыдущую мелодию"
    }

    @Synchronized
    fun cancel(context: Context, category: String) {
        if (!valid(category) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val trial = pending(context, category) ?: return
        if (!clearPending(prefs(context).edit(), category).commit()) return
        if (trial.id != BalanceNotificationSounds.channelId(context, category)) {
            try { manager(context).deleteNotificationChannel(trial.id) }
            catch (_: Exception) { }
        }
    }
}
