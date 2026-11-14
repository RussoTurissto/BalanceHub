package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Owner-test notes for BalanceHub 1.1.4-rc7. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("music", "Три точных рисунка вибрации",
            "Короткий, длинный и двойной сигналы проверяются в независимых каналах Android."),
        ReleaseNote("settings", "Восстановление по подтверждению",
            "Если звук или вибрация расходятся с выбором в приложении, их можно применить к новому каналу."),
        ReleaseNote("check-circle", "Тест мелодии Кристалл",
            "Отдельный тест проверяет звук из медиатеки Android и длительную вибрацию."),
        ReleaseNote("music", "Системная громкость",
            "Системная мелодия больше не зависит от ползунка громкости встроенных звуков BalanceHub."),
        ReleaseNote("check-circle", "Данные остаются на месте",
            "Платежи, события, настройки банков и оформления сохраняются при обновлении.")
    )
}
