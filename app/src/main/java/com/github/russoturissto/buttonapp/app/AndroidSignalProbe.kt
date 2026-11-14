package com.github.russoturissto.buttonapp.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Independent Android baseline. This is intentionally NOT a Payments/Events/
 * Updates channel: it isolates the OEM's treatment of the platform ringtone
 * and default vibration from BalanceHub's custom WAV and vibration patterns.
 *
 * Only created on explicit user request. A unique ID prevents resurrection of
 * old, user-muted Android channels. At most one active probe is retained.
 */
internal object AndroidSignalProbe {
    private const val PREFS = "balancehub_android_signal_probe"
    private const val ACTIVE_ID = "last_probe_channel"

    data class Result(val id: String?, val report: String)

    fun channelId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ACTIVE_ID, null)?.takeIf(StableNotificationChannels::isProbe)

    fun report(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "Системные каналы недоступны на этой версии Android"
        }
        val id = channelId(context) ?: return "Проверка ещё не запускалась"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val channel = manager.getNotificationChannel(id)
            ?: return "Тестовый канал отсутствует в Android. ID: $id"
        return buildString {
            append("ID тестового канала: ").append(id)
            append("\nВажность: ").append(channel.importance)
            append("\nЗвук: ").append(when (channel.sound) {
                null -> "Нет"
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ->
                    "Системная мелодия по умолчанию"
                else -> "Android выбрал другую мелодию"
            })
            append("\nВибрация: ").append(if (channel.shouldVibrate())
                "Включена" else "Выключена")
            append("\nРисунок вибрации: ").append(
                channel.vibrationPattern?.joinToString(",") ?: "Системный")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                append("\nЗвук изменён в Android: ").append(channel.hasUserSetSound())
            }
        }
    }

    fun send(context: Context): Result {
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
        val id = StableNotificationChannels.probeId()
        return try {
            val channel = NotificationChannel(id,
                "BalanceHub · Тест системных сигналов", NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(sound, AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            // Crucial: do not attach a custom waveform; ask Android for its own
            // default notification vibration, as commonly used by messengers.
            channel.enableVibration(true)
            manager.createNotificationChannel(channel)
            val actual = manager.getNotificationChannel(id)
                ?: return Result(null, "Android не создал новый тестовый канал")
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previous = channelId(context)
            if (!prefs.edit().putString(ACTIVE_ID, id).commit()) {
                manager.deleteNotificationChannel(id)
                return Result(null, "Не удалось запомнить тестовый канал")
            }
            previous?.takeIf { it != id }?.let { previousId ->
                // Never delete an unrelated user/app channel.
                if (StableNotificationChannels.isProbe(previousId)) {
                    manager.deleteNotificationChannel(previousId)
                }
            }
            val notice = NotificationCompat.Builder(context, id)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle("BalanceHub: системный тест")
                .setContentText("Стандартный звук и вибрация Android")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            notifier.notify(ReminderNoticeIds.test(), notice)
            Result(id, "Тестовое уведомление отправлено. " +
                "Это отдельный канал, настройки платежей не изменены.\n\n" +
                report(context) + "\n\nПроверьте звук и вибрацию на телефоне.")
        } catch (error: SecurityException) {
            Result(id, "Android не разрешил отправку теста. " +
                error.javaClass.simpleName + "\n\n" + report(context))
        } catch (error: RuntimeException) {
            Result(id, "Android отклонил тестовый канал: " +
                error.javaClass.simpleName + "\n\n" + report(context))
        }
    }
}
