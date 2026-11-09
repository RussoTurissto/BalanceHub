package com.github.russoturissto.buttonapp.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build

/**
 * 15 original, bundled sounds. All preferences are local to the device.
 * Android 8+ owns channel sounds after creation, so every selected built-in
 * tone has a stable, distinct channel ID instead of editing an existing one.
 */
@SuppressLint("UseKtx")
internal object BalanceNotificationSounds {
    const val PAYMENT = "payment_reminders"
    const val EVENT = "event_reminders"
    const val UPDATE = "balancehub_app_updates"

    data class Tone(val key: String, val title: String, val resource: Int)
    val tones = listOf(
        Tone("balancehub", "BalanceHub", R.raw.bh_sound_balancehub),
        Tone("crystal", "Кристалл", R.raw.bh_sound_crystal),
        Tone("drop", "Капля", R.raw.bh_sound_drop),
        Tone("aurora", "Аврора", R.raw.bh_sound_aurora),
        Tone("bamboo", "Бамбук", R.raw.bh_sound_bamboo),
        Tone("orbit", "Орбита", R.raw.bh_sound_orbit),
        Tone("pearl", "Жемчуг", R.raw.bh_sound_pearl),
        Tone("breeze", "Бриз", R.raw.bh_sound_breeze),
        Tone("spark", "Искра", R.raw.bh_sound_spark),
        Tone("lagoon", "Лагуна", R.raw.bh_sound_lagoon),
        Tone("pulse", "Пульс", R.raw.bh_sound_pulse),
        Tone("dawn", "Рассвет", R.raw.bh_sound_dawn),
        Tone("echo", "Эхо", R.raw.bh_sound_echo),
        Tone("comet", "Комета", R.raw.bh_sound_comet),
        Tone("harmony", "Гармония", R.raw.bh_sound_harmony)
    )

    private fun preferences(context: Context) =
        context.getSharedPreferences("balancehub_notification_sounds", Context.MODE_PRIVATE)

    fun selected(context: Context, category: String): String {
        val default = when (category) {
            PAYMENT -> "balancehub"
            EVENT -> "crystal"
            else -> "system"
        }
        val value = preferences(context).getString(category, default) ?: default
        return value.takeIf { it == "system" || it == "silent" ||
            tones.any { tone -> tone.key == it } } ?: default
    }

    fun title(context: Context, category: String): String =
        when (val key = selected(context, category)) {
            "system" -> "Системный звук"
            "silent" -> "Без звука"
            else -> tones.firstOrNull { it.key == key }?.title ?: "BalanceHub"
        }

    fun choose(context: Context, category: String, key: String) {
        if (key != "system" && key != "silent" && tones.none { it.key == key }) return
        preferences(context).edit().putString(category, key).apply()
    }

    private fun soundUri(context: Context, key: String): Uri? {
        if (key == "silent") return null
        if (key == "system") return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return Uri.parse("content://" + context.packageName + ".sounds/" + key)
    }

    fun legacyChannel(category: String): String = when (category) {
        PAYMENT -> "Напоминания о платежах"
        EVENT -> "Напоминания о событиях"
        else -> "Обновления BalanceHub"
    }

    fun channelId(context: Context, category: String): String {
        val key = selected(context, category)
        return if (key == "system") category else category + "_tone_" + key + "_v2"
    }

    fun ensureChannel(context: Context, category: String): String {
        val key = selected(context, category)
        val id = channelId(context, category)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(id) == null) {
                val channel = NotificationChannel(id, legacyChannel(category),
                    if (category == UPDATE) NotificationManager.IMPORTANCE_DEFAULT
                    else NotificationManager.IMPORTANCE_HIGH)
                if (key != "system") {
                    channel.setSound(soundUri(context, key),
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build())
                }
                manager.createNotificationChannel(channel)
            }
        }
        return id
    }

    /** For API 25 and earlier: newer Android versions use channel sound. */
    fun configureLegacySound(context: Context, category: String,
        builder: androidx.core.app.NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return
        builder.setSound(soundUri(context, selected(context, category)))
    }

    private var previewPlayer: MediaPlayer? = null

    fun stopPreview() {
        previewPlayer?.release()
        previewPlayer = null
    }

    fun preview(context: Context, key: String) {
        stopPreview()
        val tone = tones.firstOrNull { it.key == key } ?: return
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build())
            context.resources.openRawResourceFd(tone.resource)?.use { fd ->
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            } ?: return
            player.prepare()
            previewPlayer = player
            player.setOnCompletionListener {
                it.release()
                if (previewPlayer === it) previewPlayer = null
            }
            player.start()
        } catch (_: Exception) {
            stopPreview()
        }
    }
}
