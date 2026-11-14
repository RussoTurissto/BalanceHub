package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Owner-test notes for BalanceHub 1.1.4-rc6. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("music", "Два способа вибрации Android",
            "Сравните стандартный сигнал и заданный рисунок в двух постоянных каналах."),
        ReleaseNote("settings", "Восстановление по подтверждению",
            "Если звук или вибрация расходятся с выбором в приложении, их можно применить к новому каналу."),
        ReleaseNote("check-circle", "Диагностика каналов и вибромотора",
            "Можно проверить вибромотор напрямую и сравнить ответ API с настройками Android."),
        ReleaseNote("music", "Системная громкость",
            "Системная мелодия больше не зависит от ползунка громкости встроенных звуков BalanceHub."),
        ReleaseNote("check-circle", "Данные остаются на месте",
            "Платежи, события, настройки банков и оформления сохраняются при обновлении.")
    )
}
