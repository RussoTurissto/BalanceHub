package com.github.russoturissto.buttonapp.app

/**
 * Compare the signals requested inside BalanceHub with those actually stored by
 * Android. Notifications on Android 8+ follow the system channel, not app prefs.
 * No claim is made that the speaker or vibration motor was physically triggered.
 */
internal object NotificationChannelSignalState {
    fun issues(
        wantsSound: Boolean,
        channelHasSound: Boolean,
        wantsVibration: Boolean,
        channelHasVibration: Boolean
    ): List<String> = buildList {
        if (wantsSound && !channelHasSound) add("Звук канала отключён в Android")
        if (!wantsSound && channelHasSound) add("Android включил звук вопреки выбору в BalanceHub")
        if (wantsVibration && !channelHasVibration) add("Вибрация канала отключена в Android")
        if (!wantsVibration && channelHasVibration) {
            add("Android включил вибрацию вопреки выбору в BalanceHub")
        }
    }
}
