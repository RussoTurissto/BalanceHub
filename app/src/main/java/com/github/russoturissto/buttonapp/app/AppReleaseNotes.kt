package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Owner-test notes for BalanceHub 1.1.4-rc11. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("calendar-days", "Новые платежи и события",
            "При открытии дата и время совпадают с текущими часами телефона."),
        ReleaseNote("clock", "Точное время платежа",
            "Можно выбрать часы и минуты при создании и редактировании."),
        ReleaseNote("check-circle", "Дата и часы согласованы",
            "Выбор дня больше не сбрасывает время на 09:00."),
        ReleaseNote("music", "Системная громкость",
            "Системная мелодия больше не зависит от ползунка громкости встроенных звуков BalanceHub."),
        ReleaseNote("check-circle", "Данные остаются на месте",
            "Платежи, события, настройки банков и оформления сохраняются при обновлении.")
    )
}
