package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Direct haptic diagnostic; real reminders use notification channels. */
internal object BalanceVibration {
    @Suppress("DEPRECATION")
    private fun motor(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager)?.defaultVibrator
        } else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun available(context: Context): Boolean = motor(context)?.hasVibrator() == true

    @Suppress("DEPRECATION")
    fun preview(context: Context, category: String): Boolean {
        val motor = motor(context) ?: return false
        if (!motor.hasVibrator()) return false
        return try {
            val pulse = BalanceNotificationSounds.vibrationPattern(category)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                motor.vibrate(VibrationEffect.createWaveform(pulse, -1))
            } else motor.vibrate(pulse, -1)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}
