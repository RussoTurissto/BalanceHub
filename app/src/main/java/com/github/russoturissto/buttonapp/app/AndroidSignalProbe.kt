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
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Two isolated Android notification probes: one uses the OS default vibration,
 * the other specifies a waveform. Neither changes the Payments/Events/Updates
 * channels. Reusing each probe ID is intentional: it lets the user compare the
 * *same* system channel before and after leaving Android Settings.
 */
@SuppressLint("UseKtx")
internal object AndroidSignalProbe {
    private const val PREFS = "balancehub_android_signal_probe"

    enum class Mode(val preferenceKey: String, val label: String) {
        SYSTEM("last_probe_channel", "Системная вибрация"),
        PATTERN("last_pattern_probe_channel", "Заданный рисунок")
    }

    data class Result(val id: String?, val report: String)

    // Two short pulses with a gap, intentionally different from the platform default.
    fun explicitPattern(): LongArray = longArrayOf(0L, 180L, 120L, 220L)

    fun channelId(context: Context, mode: Mode = Mode.SYSTEM): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(mode.preferenceKey, null)?.takeIf(StableNotificationChannels::isProbe)

    fun report(context: Context, mode: Mode = Mode.SYSTEM): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "Системные каналы недоступны на этой версии Android"
        }
        val id = channelId(context, mode) ?: return "Проверка «${mode.label}» ещё не запускалась"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val channel = manager.getNotificationChannel(id)
            ?: return "Тестовый канал отсутствует в Android. ID: $id"
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "Обычный (со звуком)"
            AudioManager.RINGER_MODE_VIBRATE -> "Только вибрация"
            else -> "Без звука"
        }
        return buildString {
            append("Тест: ").append(mode.label)
            append("\nID тестового канала: ").append(id)
            append("\nРежим телефона: ").append(ringerMode)
            append("\nВажность: ").append(channel.importance)
            append("\nЗвук: ").append(when (channel.sound) {
                null -> "Нет"
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ->
                    "Системная мелодия по умолчанию"
                else -> "Android выбрал другую мелодию"
            })
            append("\nВибрация (ответ API): ").append(if (channel.shouldVibrate())
                "Включена" else "Выключена")
            append("\nРисунок вибрации (ответ API): ").append(
                channel.vibrationPattern?.joinToString(",") ?: "Системный / не указан")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                append("\nЗвук изменён в Android: ").append(channel.hasUserSetSound())
            }
            append("\n\nЭто данные API Android, а не подтверждение физической вибрации. ")
            append("Переключатель в системных настройках может отличаться. ")
            append("Сравнивайте оба режима телефона на одном и том же ID.")
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
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return Result(null, "В Android не выбрана системная мелодия уведомления")
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val previousId = channelId(context, mode)
        val existing = previousId?.let { manager.getNotificationChannel(it) }
        val id = if (existing == null) StableNotificationChannels.probeId()
            else previousId ?: return Result(null, "Не удалось определить ID тестового канала")
        return try {
            if (existing == null) {
                val name = if (mode == Mode.SYSTEM)
                    "BalanceHub · Тест системной вибрации"
                else "BalanceHub · Тест заданной вибрации"
                val channel = NotificationChannel(
                    id, name, NotificationManager.IMPORTANCE_HIGH)
                channel.setSound(sound, AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                if (mode == Mode.PATTERN) {
                    channel.vibrationPattern = explicitPattern()
                }
                // Boolean last for OEMs that modify it when a waveform is assigned.
                channel.enableVibration(true)
                manager.createNotificationChannel(channel)
                if (manager.getNotificationChannel(id) == null) {
                    return Result(null, "Android не создал новый тестовый канал")
                }
                val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(mode.preferenceKey, id).commit()
                if (!stored) {
                    manager.deleteNotificationChannel(id)
                    return Result(null, "Не удалось запомнить тестовый канал")
                }
            }
            // Do not recreate an existing channel: Android owns its user settings.
            val notice = NotificationCompat.Builder(context, id)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle("BalanceHub: ${mode.label}")
                .setContentText("Проверка звука и вибрации канала Android")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            notifier.notify(ReminderNoticeIds.test(), notice)
            Result(id, "Тестовое уведомление отправлено. " +
                "Это отдельный канал; настройки платежей и событий не изменены.\n\n" +
                report(context, mode) +
                "\n\nОткройте настройки этого канала и сравните звук и вибрацию.")
        } catch (error: SecurityException) {
            Result(id, "Android не разрешил отправку теста: " +
                error.javaClass.simpleName + "\n\n" + report(context, mode))
        } catch (error: RuntimeException) {
            Result(id, "Android отклонил тест: " +
                error.javaClass.simpleName + "\n\n" + report(context, mode))
        }
    }
}
