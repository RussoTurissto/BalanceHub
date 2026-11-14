package com.github.russoturissto.buttonapp.app

import android.app.NotificationChannel
import android.os.Build
import android.os.VibrationEffect

/**
 * An OS channel is immutable after registration. Configure the waveform only
 * while building a new channel. Android/OEM vibration policy remains authoritative.
 */
internal object NotificationChannelVibration {
    fun apply(channel: NotificationChannel, pattern: LongArray?) {
        if (pattern == null) {
            channel.enableVibration(true)
            return
        }
        require(pattern.size >= 2 && pattern.any { it > 0L })
        // API 35+ supports an explicit effect. Older versions use the waveform.
        channel.vibrationPattern = pattern
        if (Build.VERSION.SDK_INT >= 35) {
            channel.setVibrationEffect(VibrationEffect.createWaveform(pattern, -1))
        }
        channel.enableVibration(true)
    }

    fun hasRequestedPattern(channel: NotificationChannel, pattern: LongArray): Boolean =
        pattern.contentEquals(channel.vibrationPattern)
}
