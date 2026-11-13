package com.github.russoturissto.buttonapp.app

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/**
 * Read-only, on-device engineering diagnostics. This does not change a
 * user-muted system channel and never claims to have measured audible output.
 */
internal object NotificationSignalAudit {
    fun report(context: Context, category: String): List<String> {
        val lines = mutableListOf<String>()
        val name = BalanceNotificationSounds.legacyChannel(category)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        lines.add("Канал: $name")
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            lines.add("Уведомления BalanceHub запрещены Android")
        }
        lines.add("Выбрана мелодия: " + BalanceNotificationSounds.title(context, category))
        lines.add("Громкость BalanceHub: " + BalanceNotificationSounds.volume(context, category) + "%")
        lines.add("Вибрация в BalanceHub: " +
            if (BalanceNotificationSounds.vibrationFor(context, category)) "включена" else "выключена")
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        lines.add("Режим звука Android: " + when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "обычный"
            AudioManager.RINGER_MODE_VIBRATE -> "вибрация"
            else -> "без звука"
        })
        lines.add("Системная громкость уведомлений: " +
            audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) + "/" +
            audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION))
        val filter = manager.currentInterruptionFilter
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            lines.add("Системный фильтр прерываний: $filter (проверьте «Не беспокоить»)")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val id = BalanceNotificationSounds.ensureChannel(context, category)
            val channel = manager.getNotificationChannel(id)
            if (channel == null) {
                lines.add("Ошибка: канал уведомлений не найден")
            } else {
                lines.add("Важность канала: " + when (channel.importance) {
                    NotificationManager.IMPORTANCE_NONE -> "выключен"
                    NotificationManager.IMPORTANCE_MIN -> "минимальная, без звука"
                    NotificationManager.IMPORTANCE_LOW -> "низкая, без звука"
                    NotificationManager.IMPORTANCE_DEFAULT -> "обычная, со звуком"
                    NotificationManager.IMPORTANCE_HIGH -> "высокая, со звуком"
                    else -> "код " + channel.importance
                })
                lines.add("Вибрация канала: " +
                    if (channel.shouldVibrate()) "включена" else "выключена")
                val sound = channel.sound
                val mediaTone = sound != null && BalanceNotificationSounds.tones.any {
                    BalanceSystemToneStore.cachedUri(context, it.key,
                        BalanceNotificationSounds.volume(context, category)) == sound
                }
                val isCustom = sound?.authority == context.packageName + ".sounds" ||
                    sound?.scheme == "android.resource" || mediaTone
                lines.add("Звук канала: " + when {
                    sound == null -> "без звука"
                    mediaTone -> "фирменная мелодия через медиатеку Android"
                    isCustom -> "фирменная мелодия BalanceHub"
                    else -> "системная мелодия/выбор Android"
                })
                if (sound != null && isCustom) {
                    val failure = BalanceNotificationSounds.audioProbe(context, sound)
                    lines.add("Доступ к WAV через Android: " +
                        if (failure == null) "подтверждён"
                        else "ОШИБКА: " + failure)
                    lines.add("Источник WAV: " + sound.scheme)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (channel.hasUserSetImportance()) {
                        lines.add("Важность канала выбрана в настройках Android")
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (channel.hasUserSetSound()) {
                        lines.add("Звук канала выбран в настройках Android")
                    }
                }
            }
        }
        BalanceNotificationSounds.fallbackReason(context, category)?.let {
            lines.add("Резервная мелодия: $it")
        }
        return lines
    }
}
