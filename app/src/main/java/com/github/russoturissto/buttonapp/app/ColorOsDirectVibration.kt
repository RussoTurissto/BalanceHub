package com.github.russoturissto.buttonapp.app

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit

/**
 * Opt-in OnePlus/ColorOS workaround. OS still delivers notifications and their
 * sound; only haptics are sent separately with notification usage.
 *
 * The direct command is skipped when a system channel has vibration enabled,
 * so we do not produce a duplicate hardware pulse. Respect the user's DND,
 * ringer and app/category notification settings rather than bypassing them.
 */
internal object ColorOsDirectVibration {
    enum class Result {
        DISABLED, VIBRATION_OFF, CHANNEL_BLOCKED, SYSTEM_VIBRATION,
        SILENT, DO_NOT_DISTURB, HARDWARE_UNAVAILABLE, SENT, ERROR
    }

    /** Pure gate; testable without touching the OS vibrator. */
    fun canVibrate(
        compatibility: Boolean, wantsVibration: Boolean, notificationsEnabled: Boolean,
        channelCanAlert: Boolean, systemChannelVibrates: Boolean,
        silentMode: Boolean, normalNotificationPolicy: Boolean
    ): Boolean = compatibility && wantsVibration && notificationsEnabled &&
        channelCanAlert && !systemChannelVibrates && !silentMode && normalNotificationPolicy

    @Suppress("DEPRECATION")
    private fun motor(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager)?.defaultVibrator
        } else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private fun record(context: Context, category: String, result: Result): Result {
        context.getSharedPreferences("balancehub_coloros_direct_vibration", Context.MODE_PRIVATE)
            .edit {
                putString("last_result_" + category, result.name)
                putLong("last_time_" + category, System.currentTimeMillis())
            }
        return result
    }

    fun lastResult(context: Context, category: String): String {
        val prefs = context.getSharedPreferences("balancehub_coloros_direct_vibration",
            Context.MODE_PRIVATE)
        val result = prefs.getString("last_result_" + category, null)
            ?: return "Прямой сигнал ещё не отправлялся"
        val time = prefs.getLong("last_time_" + category, 0L)
        return result + " (" + java.text.DateFormat.getDateTimeInstance()
            .format(java.util.Date(time)) + ")"
    }

    /**
     * Call immediately after notify() succeeded, including from AlarmManager
     * BroadcastReceivers. No activity or foreground service is required for
     * this short, notification-usage vibration.
     */
    @Suppress("DEPRECATION")
    fun afterNotification(context: Context, category: String, channelId: String): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return Result.DISABLED
        if (!BalanceNotificationSounds.colorOsCompatibility(context))
            return Result.DISABLED
        if (!BalanceNotificationSounds.vibrationFor(context, category) ||
            !BalanceNotificationSounds.vibrationEnabled(context))
            return record(context, category, Result.VIBRATION_OFF)
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled())
            return record(context, category, Result.CHANNEL_BLOCKED)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            manager.getNotificationChannel(channelId) else null
        if (channel == null || channel.importance <
            NotificationManager.IMPORTANCE_DEFAULT) {
            return record(context, category, Result.CHANNEL_BLOCKED)
        }
        // Do not produce an extra vibration when the user enabled native
        // vibration from the channel settings.
        if (channel.shouldVibrate()) {
            return record(context, category, Result.SYSTEM_VIBRATION)
        }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT)
            return record(context, category, Result.SILENT)
        if (manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)
            return record(context, category, Result.DO_NOT_DISTURB)
        if (!canVibrate(
                true, true, true, true, false,
                audio.ringerMode == AudioManager.RINGER_MODE_SILENT, true)) {
            return record(context, category, Result.VIBRATION_OFF)
        }
        val vibrator = motor(context)
        if (vibrator?.hasVibrator() != true)
            return record(context, category, Result.HARDWARE_UNAVAILABLE)
        return try {
            val waveform = BalanceNotificationSounds.selectedVibrationPattern(context, category)
            val attributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(VibrationEffect.createWaveform(waveform, -1),
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_NOTIFICATION).build())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(waveform, -1), attributes)
            } else {
                vibrator.vibrate(waveform, -1, attributes)
            }
            record(context, category, Result.SENT)
        } catch (_: SecurityException) {
            record(context, category, Result.ERROR)
        } catch (_: RuntimeException) {
            record(context, category, Result.ERROR)
        }
    }
}
