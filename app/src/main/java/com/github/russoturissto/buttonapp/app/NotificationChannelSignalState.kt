package com.github.russoturissto.buttonapp.app

/**
 * Compare OS-owned channel values with BalanceHub preferences. This audit
 * cannot assert that a speaker or vibration motor physically played.
 */
internal object NotificationChannelSignalState {
    fun issues(
        wantsSound: Boolean,
        channelHasSound: Boolean,
        wantsVibration: Boolean,
        channelHasVibration: Boolean,
        soundMatches: Boolean? = null,
        vibrationPatternMatches: Boolean? = null,
        channelCanAlert: Boolean = true
    ): List<String> = buildList {
        if (!channelCanAlert) {
            add("Android ограничил важность канала: звуковые уведомления запрещены")
        }
        if (wantsSound && !channelHasSound) add("Звук канала отключён в Android")
        if (!wantsSound && channelHasSound) {
            add("Android включил звук вопреки выбору в BalanceHub")
        }
        if (wantsSound && channelHasSound && soundMatches == false) {
            add("Мелодия канала Android отличается от выбранной в BalanceHub")
        }
        if (wantsVibration && !channelHasVibration) {
            add("Вибрация канала отключена в Android")
        }
        if (!wantsVibration && channelHasVibration) {
            add("Android включил вибрацию вопреки выбору в BalanceHub")
        }
        if (wantsVibration && channelHasVibration && vibrationPatternMatches == false) {
            add("Рисунок вибрации Android отличается от выбранного в BalanceHub")
        }
    }
}
