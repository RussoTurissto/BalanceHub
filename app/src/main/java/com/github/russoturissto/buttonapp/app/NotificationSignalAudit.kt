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
        lines.add("Вибросигнал BalanceHub: " +
            BalanceNotificationSounds.vibrationModeTitle(context, category))
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
            lines.add("Активный системный ID: $id")
            val categoryIds = manager.notificationChannels.map { it.id }
                .filter { StableNotificationChannels.belongsToCategory(it, category) }
            lines.add("Существующих каналов этой категории: " + categoryIds.size)
            if (categoryIds.any { it != id }) {
                lines.add("Другие ID этой категории: " +
                    categoryIds.filter { it != id }.take(5).joinToString(", "))
            }
            val channel = manager.getNotificationChannel(id)
            if (channel == null) {
                lines.add("Ошибка: канал уведомлений не найден")
            } else {
                lines.add("Важность канала: " + when (channel.importance) {
                    NotificationManager.IMPORTANCE_NONE -> "выключен"
                    NotificationManager.IMPORTANCE_MIN -> "минимальная, без звука"
                    NotificationManager.IMPORTANCE_LOW -> "низкая, без звука"
                    NotificationManager.IMPORTANCE_DEFAULT -> "обычная (звук разрешён)"
                    NotificationManager.IMPORTANCE_HIGH -> "высокая (звук разрешён)"
                    else -> "код " + channel.importance
                })
                lines.add("Вибрация канала (ответ API): " +
                    if (channel.shouldVibrate()) "включена" else "выключена")
                lines.add("ID проверяемого канала совпадает с активным: " +
                    (channel.id == BalanceNotificationSounds.channelId(context, category)))
                lines.add("Рисунок канала: " +
                    (channel.vibrationPattern?.joinToString(",") ?: "системный"))
                val sound = channel.sound
                val mediaTone = sound != null && BalanceNotificationSounds.tones.any {
                    BalanceSystemToneStore.cachedUri(context, it.key,
                        BalanceNotificationSounds.volume(context, category)) == sound
                }
                val isCustom = sound?.authority == context.packageName + ".sounds" ||
                    sound?.scheme == "android.resource" || mediaTone
                lines.add("Звук канала (ответ API): " + when {
                    sound == null -> "без звука"
                    mediaTone -> "фирменная мелодия через медиатеку Android"
                    isCustom -> "фирменная мелодия BalanceHub"
                    else -> "системная мелодия/выбор Android"
                })
                val issues = BalanceNotificationSounds.signalIssues(context, category)
                issues.forEach { lines.add("Несоответствие: " + it) }
                if (channel.importance >= NotificationManager.IMPORTANCE_DEFAULT &&
                    issues.isNotEmpty()) {
                    lines.add("Для применения выбранных сигналов используйте " +
                        "кнопку восстановления в настройках категории, " +
                        "либо настройте канал вручную в Android")
                }
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
        lines.add("Это сведения API Android, не измерение звука или вибромотора.")
        for (mode in AndroidSignalProbe.Mode.values()) {
            AndroidSignalProbe.channelId(context, mode)?.let { probe ->
                lines.add("Тестовый канал (${mode.label}): $probe")
            }
        }
        BalanceNotificationSounds.fallbackReason(context, category)?.let {
            lines.add("Резервная мелодия: $it")
        }
        return lines
    }
}
