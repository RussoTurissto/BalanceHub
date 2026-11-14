package com.github.russoturissto.buttonapp.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Stable, isolated notification tests. Never mutate an existing channel:
 * Android owns any sound/vibration changes made in system settings.
 *
 * Each vibration mode has a separate ID because OS channels cannot change
 * their vibration pattern once registered.
 */
@SuppressLint("UseKtx")
internal object AndroidSignalProbe {
    private const val PREFS = "balancehub_android_signal_probe"

    enum class Mode(
        val preferenceKey: String,
        val label: String,
        val vibrationMode: String? = null,
        val customTone: Boolean = false
    ) {
        SYSTEM("last_probe_channel", "Системная вибрация"),
        // Keep the rc6 test channel and its previously saved Android settings.
        PATTERN("last_pattern_probe_channel", "Старый тест: два импульса"),
        SHORT("short_vibration_probe_rc7", "Короткая вибрация",
            NotificationVibrationMode.SHORT),
        LONG("long_vibration_probe_rc7", "Длинная вибрация",
            NotificationVibrationMode.LONG),
        DOUBLE("double_vibration_probe_rc7", "Двойная вибрация",
            NotificationVibrationMode.DOUBLE),
        TONE("crystal_long_probe_rc7", "Кристалл + длинная вибрация",
            NotificationVibrationMode.LONG, true)
    }

    data class Result(val id: String?, val report: String)

    /** Kept for compatibility with the existing rc6 double-pulse probe. */
    fun explicitPattern(): LongArray = longArrayOf(0L, 180L, 120L, 220L)

    fun requestedPattern(mode: Mode): LongArray? =
        if (mode == Mode.PATTERN) explicitPattern()
        else mode.vibrationMode?.let(NotificationVibrationMode::pattern)

    fun channelId(context: Context, mode: Mode = Mode.SYSTEM): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(mode.preferenceKey, null)?.takeIf(StableNotificationChannels::isProbe)

    private fun defaultNotificationToneIsMissing(context: Context): Boolean =
        RingtoneManager.getActualDefaultRingtoneUri(
            context, RingtoneManager.TYPE_NOTIFICATION) == null

    private fun toneUri(context: Context, mode: Mode): Uri? {
        if (!mode.customTone) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            BalanceSystemToneStore.uri(context, "crystal", 100)
        } else {
            Uri.parse("content://" + context.packageName + ".sounds/crystal/v3/100")
        }
    }

    fun report(context: Context, mode: Mode = Mode.SYSTEM): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "Системные каналы недоступны на этой версии Android"
        }
        val id = channelId(context, mode)
            ?: return "Проверка «" + mode.label + "» ещё не запускалась"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val channel = manager.getNotificationChannel(id)
            ?: return "Тестовый канал отсутствует в Android. ID: " + id
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringtoneState = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "Обычный (со звуком)"
            AudioManager.RINGER_MODE_VIBRATE -> "Только вибрация"
            else -> "Без звука"
        }
        val expected = requestedPattern(mode)
        val soundDescription = when {
            channel.sound == null -> "Нет (канал без мелодии)"
            channel.sound == RingtoneManager.getDefaultUri(
                RingtoneManager.TYPE_NOTIFICATION) && defaultNotificationToneIsMissing(context) ->
                "По умолчанию; реальная системная мелодия не назначена"
            channel.sound == RingtoneManager.getDefaultUri(
                RingtoneManager.TYPE_NOTIFICATION) -> "Системная мелодия по умолчанию"
            channel.sound?.authority == "media" -> "Мелодия из медиатеки Android"
            else -> "Мелодия выбрана в Android или другом источнике"
        }
        return buildString {
            append("Тест: ").append(mode.label)
            append("\nID тестового канала: ").append(id)
            append("\nРежим телефона: ").append(ringtoneState)
            append("\nГромкость уведомлений: ")
                .append(audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
                .append("/").append(audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION))
            append("\nВажность: ").append(channel.importance)
            append("\nЗвук: ").append(soundDescription)
            append("\nИсточник звука: ").append(channel.sound?.scheme ?: "нет")
                .append(" / ").append(channel.sound?.authority ?: "нет")
            append("\nВибрация (ответ API): ")
                .append(if (channel.shouldVibrate()) "Включена" else "Выключена")
            append("\nЗапрошенный рисунок: ")
                .append(expected?.joinToString(",") ?: "Системный")
            append("\nРисунок канала (ответ API): ")
                .append(channel.vibrationPattern?.joinToString(",") ?: "Системный / не указан")
            if (expected != null) {
                append("\nРисунок сохранён без изменений: ")
                    .append(NotificationChannelVibration.hasRequestedPattern(channel, expected))
            }
            if (Build.VERSION.SDK_INT >= 35) {
                append("\nЭффект API 35+: ")
                    .append(if (channel.vibrationEffect == null) "Не указан" else "Сохранён")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                append("\nМелодия выбрана вручную в Android: ")
                    .append(channel.hasUserSetSound())
            }
            append("\n\nОтвет API не подтверждает фактический звук или рисунок вибрации.")
            append(" Если Android заменил сигнал своим, сравните настройки именно этого канала.")
        }
    }

    fun send(context: Context, mode: Mode = Mode.SYSTEM): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return Result(null, "Тест системного канала доступен на Android 8 и новее")
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            return Result(null, "Сначала разрешите уведомления BalanceHub в Android")
        }
        val notifier = NotificationManagerCompat.from(context)
        if (!notifier.areNotificationsEnabled()) {
            return Result(null, "Уведомления BalanceHub отключены в Android")
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val previousId = channelId(context, mode)
        val existing = previousId?.let { manager.getNotificationChannel(it) }
        val id = if (existing == null) StableNotificationChannels.probeId()
            else previousId ?: return Result(null, "Нет ID тестового канала")
        return try {
            if (existing == null) {
                val sound = toneUri(context, mode)
                    ?: return Result(null, "Не удалось получить мелодию для отдельного теста")
                if (mode.customTone) {
                    val wavError = BalanceNotificationSounds.audioProbe(context, sound)
                    if (wavError != null) {
                        return Result(null, "Мелодия Кристалл недоступна Android: " + wavError)
                    }
                }
                val channel = NotificationChannel(id, "BalanceHub · " + mode.label,
                    NotificationManager.IMPORTANCE_HIGH)
                channel.setSound(sound, AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                NotificationChannelVibration.apply(channel, requestedPattern(mode))
                manager.createNotificationChannel(channel)
                val actual = manager.getNotificationChannel(id)
                    ?: return Result(null, "Android не создал новый тестовый канал")
                if (mode.customTone && actual.sound != sound) {
                    manager.deleteNotificationChannel(id)
                    return Result(null, "Android не сохранил мелодию Кристалл для теста")
                }
                val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(mode.preferenceKey, id).commit()
                if (!stored) {
                    manager.deleteNotificationChannel(id)
                    return Result(null, "Не удалось запомнить тестовый канал")
                }
            }
            // Never recreate a user-modified channel on repeated tests.
            val notice = NotificationCompat.Builder(context, id)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle("BalanceHub: " + mode.label)
                .setContentText("Проверка реального звука и вибрации Android")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            notifier.notify(ReminderNoticeIds.test(), notice)
            Result(id, "Тестовое уведомление отправлено. Рабочие каналы не изменены.\n\n" +
                report(context, mode) +
                "\n\nПроверьте физический отклик и откройте настройки этого канала.")
        } catch (error: SecurityException) {
            Result(id, "Android запретил тест: " + error.javaClass.simpleName +
                "\n\n" + report(context, mode))
        } catch (error: RuntimeException) {
            Result(id, "Android отклонил тест: " + error.javaClass.simpleName +
                "\n\n" + report(context, mode))
        }
    }
}
