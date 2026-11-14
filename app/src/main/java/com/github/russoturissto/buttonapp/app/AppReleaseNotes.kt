package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Owner-test notes for BalanceHub 1.1.4-rc8. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("music", "Совместимость с ColorOS",
            "Для OnePlus можно включить прямую вибрацию при сохранении звука Android."),
        ReleaseNote("settings", "Без двойного вибросигнала",
            "Системная вибрация канала отключается при подтверждённом переходе на совместимость."),
        ReleaseNote("check-circle", "Фоновая проверка напоминаний",
            "Можно испытать звук и длинную вибрацию с закрытым приложением и экраном."),
        ReleaseNote("music", "Системная громкость",
            "Системная мелодия больше не зависит от ползунка громкости встроенных звуков BalanceHub."),
        ReleaseNote("check-circle", "Данные остаются на месте",
            "Платежи, события, настройки банков и оформления сохраняются при обновлении.")
    )
}
