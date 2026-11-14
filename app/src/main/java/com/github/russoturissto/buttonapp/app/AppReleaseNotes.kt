package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Owner-test notes for BalanceHub 1.1.4-rc3. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("music", "Проверка звука Android",
            "В каждой категории видно, какой звук действительно назначен системному каналу."),
        ReleaseNote("settings", "Восстановление по подтверждению",
            "Если звук или вибрация расходятся с выбором в приложении, их можно применить к новому каналу."),
        ReleaseNote("check-circle", "Диагностика уведомлений",
            "Перед тестом показывается предупреждение, когда Android отключил звук или вибрацию."),
        ReleaseNote("music", "Системная громкость",
            "Системная мелодия больше не зависит от ползунка громкости встроенных звуков BalanceHub."),
        ReleaseNote("check-circle", "Данные остаются на месте",
            "Платежи, события, настройки банков и оформления сохраняются при обновлении.")
    )
}
