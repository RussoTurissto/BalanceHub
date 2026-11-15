package com.github.russoturissto.buttonapp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Short, user-selected payment notification audio. Android 17 prohibits audio
 * from a background BroadcastReceiver without a foreground service; keep the
 * service active only until this WAV finishes or the safety timeout expires.
 * Never used by Events, Updates or non-opted-in payment channels.
 */
class PaymentSoundPlaybackService : Service() {
    companion object {
        const val KEY = "payment_sound_key"
        const val GAIN = "payment_sound_gain"
        const val CHANNEL_ID = "payment_sound_channel"
        const val CATEGORY = "notification_sound_category"
        private const val FGS_CHANNEL = "balancehub.payment.audio.playback"
        private const val FGS_ID = 930045
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var watchdog: Runnable? = null
    private var activeCategory: String = BalanceNotificationSounds.PAYMENT

    private fun categoryLabel(category: String): String = when (category) {
        BalanceNotificationSounds.EVENT -> "событий"
        BalanceNotificationSounds.UPDATE -> "обновлений"
        else -> "платежей"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopPlayback() {
        watchdog?.let(handler::removeCallbacks)
        watchdog = null
        val current = player
        player = null
        if (current != null) {
            try { current.reset() } catch (_: Exception) { }
            try { current.release() } catch (_: Exception) { }
        }
    }

    private fun finish(result: PaymentAutomaticSound.Result) {
        PaymentAutomaticSound.record(this, result, activeCategory)
        stopPlayback()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun foregroundNotification(category: String): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(FGS_CHANNEL) == null) {
                val channel = NotificationChannel(FGS_CHANNEL,
                    "Воспроизведение звука уведомлений", NotificationManager.IMPORTANCE_LOW)
                channel.setSound(null, null)
                channel.enableVibration(false)
                manager.createNotificationChannel(channel)
            }
        }
        return NotificationCompat.Builder(this, FGS_CHANNEL)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle("BalanceHub · звук " + categoryLabel(category))
            .setContentText("Воспроизводится выбранное уведомление")
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        stopPlayback()
        val category = intent?.getStringExtra(CATEGORY)
            ?: BalanceNotificationSounds.PAYMENT
        if (category != BalanceNotificationSounds.PAYMENT &&
            category != BalanceNotificationSounds.EVENT &&
            category != BalanceNotificationSounds.UPDATE) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        activeCategory = category
        try {
            startForeground(FGS_ID, foregroundNotification(category))
        } catch (error: RuntimeException) {
            android.util.Log.w("BalanceHubPaymentAudio", "FGS notification failed", error)
            PaymentAutomaticSound.record(this,
                PaymentAutomaticSound.Result.PLAYBACK_FAILED, activeCategory)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val key = intent?.getStringExtra(KEY)
        val gain = intent?.getIntExtra(GAIN, 100) ?: 100
        val channelId = intent?.getStringExtra(CHANNEL_ID)
        if (key == null || channelId == null || !PaymentAutomaticSound.shouldOffer(this)) {
            finish(PaymentAutomaticSound.Result.PLAYBACK_FAILED)
            return START_NOT_STICKY
        }
        // Check OS controls again in the service, which may start after a delay.
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            manager.getNotificationChannel(channelId) else null
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        if (channel == null || channel.importance < NotificationManager.IMPORTANCE_DEFAULT ||
            channel.sound != null || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            finish(PaymentAutomaticSound.Result.CHANNEL_BLOCKED)
            return START_NOT_STICKY
        }
        val uri = PaymentAutomaticSound.playableUri(this, key, gain)
        if (uri == null) {
            finish(PaymentAutomaticSound.Result.SOUND_UNAVAILABLE)
            return START_NOT_STICKY
        }
        try {
            val current = MediaPlayer()
            player = current
            current.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            current.setDataSource(this, uri)
            current.setOnPreparedListener {
                try {
                    it.start()
                    PaymentAutomaticSound.record(this,
                        PaymentAutomaticSound.Result.PLAYING, activeCategory)
                } catch (error: RuntimeException) {
                    android.util.Log.w("BalanceHubPaymentAudio", "Audio start failed", error)
                    finish(PaymentAutomaticSound.Result.PLAYBACK_FAILED)
                }
            }
            current.setOnCompletionListener {
                finish(PaymentAutomaticSound.Result.FINISHED)
            }
            current.setOnErrorListener { _, _, _ ->
                finish(PaymentAutomaticSound.Result.PLAYBACK_FAILED)
                true
            }
            watchdog = Runnable {
                finish(PaymentAutomaticSound.Result.PLAYBACK_TIMED_OUT)
            }.also { handler.postDelayed(it, 15000L) }
            current.prepareAsync()
        } catch (error: Exception) {
            android.util.Log.w("BalanceHubPaymentAudio", "Audio preparation failed", error)
            finish(PaymentAutomaticSound.Result.PLAYBACK_FAILED)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
