package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Owner-test notes for BalanceHub 1.1.4-rc10. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("check-circle", "Платежи: контроль доставки",
            "Журнал планирования, задержки и фактической отправки уведомлений."),
        ReleaseNote("settings", "Настройки энергосбережения",
            "Проверка точных сигналов и исключения из оптимизации Android."),
        ReleaseNote("check-circle", "Защита от пропущенных платежей",
            "Опоздавший сигнал принимается до 15 минут после срока без повторов."),
        ReleaseNote("music", "Системная громкость",
            "Системная мелодия больше не зависит от ползунка громкости встроенных звуков BalanceHub."),
        ReleaseNote("check-circle", "Данные остаются на месте",
            "Платежи, события, настройки банков и оформления сохраняются при обновлении.")
    )
}
