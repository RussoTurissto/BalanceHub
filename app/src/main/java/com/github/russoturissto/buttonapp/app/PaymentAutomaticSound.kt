package com.github.russoturissto.buttonapp.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit

/**
 * Experimental payments-only route for ColorOS channels that accept a WAV URI
 * in their metadata but do not actually play it. The visible notification uses
 * a SILENT candidate channel; a short mediaPlayback foreground service plays
 * its tone. Never activate this for an existing user's working system channel.
 */
internal object PaymentAutomaticSound {
    private const val PREFS = "balancehub_notification_sounds"
    internal const val ENABLED = "payment_automatic_sound"
    private const val AUDIT = "balancehub_payment_automatic_audio"
    private const val CATEGORY = BalanceNotificationSounds.PAYMENT

    enum class Result {
        INACTIVE, SILENT_SELECTION, NOTIFICATIONS_BLOCKED, CHANNEL_BLOCKED,
        CHANNEL_HAS_SYSTEM_SOUND, RINGER_SILENT, RINGER_VIBRATE, DND,
        SOUND_UNAVAILABLE, SERVICE_REQUESTED, SERVICE_REJECTED,
        PLAYING, FINISHED, PLAYBACK_FAILED, PLAYBACK_TIMED_OUT
    }

    fun enabled(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun shouldOffer(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            BalanceNotificationSounds.colorOsCompatibility(context)

    internal fun playableUri(context: Context, key: String, gain: Int): Uri? = when {
        key == "silent" || (key != "system" && gain <= 0) -> null
        key == "system" -> RingtoneManager.getActualDefaultRingtoneUri(
            context, RingtoneManager.TYPE_NOTIFICATION)
        else -> BalanceSystemToneStore.uri(context, key, gain)
    }

    internal fun record(context: Context, result: Result) {
        context.getSharedPreferences(AUDIT, Context.MODE_PRIVATE).edit {
            putString("last", result.name)
            putLong("time", System.currentTimeMillis())
        }
    }

    fun lastResult(context: Context): String =
        context.getSharedPreferences(AUDIT, Context.MODE_PRIVATE)
            .getString("last", "Нет попыток воспроизведения") ?: "Нет данных"

    fun afterNotification(context: Context, channelId: String,
        key: String = BalanceNotificationSounds.selected(context, CATEGORY),
        gain: Int = BalanceNotificationSounds.volume(context, CATEGORY),
        trial: Boolean = false): Result {
        if ((!trial && !enabled(context)) || !shouldOffer(context))
            return Result.INACTIVE
        if (key == "silent" || (key != "system" && gain == 0))
            return Result.SILENT_SELECTION
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return Result.NOTIFICATIONS_BLOCKED
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled())
            return Result.NOTIFICATIONS_BLOCKED
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val channel = manager.getNotificationChannel(channelId)
            ?: return Result.CHANNEL_BLOCKED
        if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT)
            return Result.CHANNEL_BLOCKED
        // Never double-play audio if the user changed the channel sound in Android.
        if (channel.sound != null) return Result.CHANNEL_HAS_SYSTEM_SOUND
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> return Result.RINGER_SILENT
            AudioManager.RINGER_MODE_VIBRATE -> return Result.RINGER_VIBRATE
        }
        if (manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)
            return Result.DND
        if (playableUri(context, key, gain) == null) return Result.SOUND_UNAVAILABLE
        return try {
            val intent = Intent(context, PaymentSoundPlaybackService::class.java)
                .putExtra(PaymentSoundPlaybackService.KEY, key)
                .putExtra(PaymentSoundPlaybackService.GAIN, gain)
                .putExtra(PaymentSoundPlaybackService.CHANNEL_ID, channelId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else context.startService(intent)
            record(context, Result.SERVICE_REQUESTED)
            Result.SERVICE_REQUESTED
        } catch (error: RuntimeException) {
            android.util.Log.w("BalanceHubPaymentAudio", "FGS launch rejected", error)
            record(context, Result.SERVICE_REJECTED)
            Result.SERVICE_REJECTED
        }
    }
}
