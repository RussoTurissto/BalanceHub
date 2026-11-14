package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Owner-test notes for BalanceHub 1.1.4-rc9. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("music", "Кристалл на OnePlus",
            "Для новых каналов платежей пробуем совместимый адрес системной медиатеки."),
        ReleaseNote("settings", "Распознавание мелодии",
            "Одинаковый аудиофайл определяется и при другом адресе MediaStore."),
        ReleaseNote("check-circle", "Рабочий канал сохранён",
            "Обновление не пересоздаёт действующие каналы и не сбрасывает звук и вибрацию."),
        ReleaseNote("music", "Системная громкость",
            "Системная мелодия больше не зависит от ползунка громкости встроенных звуков BalanceHub."),
        ReleaseNote("check-circle", "Данные остаются на месте",
            "Платежи, события, настройки банков и оформления сохраняются при обновлении.")
    )
}
