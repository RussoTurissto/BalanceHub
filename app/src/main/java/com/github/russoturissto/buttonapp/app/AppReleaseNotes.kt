package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** User-facing changes for the 1.1.4-rc1 notification settings build. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("music", "Звук внутри BalanceHub",
            "Выбирайте одну из 15 мелодий для платежей, событий и обновлений прямо в приложении."),
        ReleaseNote("settings", "Пять вариантов вибрации",
            "Выключено, по умолчанию, короткий, длинный и двойной вибросигнал для каждой категории."),
        ReleaseNote("music", "Громкость каждой категории",
            "Для встроенных мелодий можно отдельно настроить громкость. Выбор сразу сохраняется в канале уведомлений."),
        ReleaseNote("check-circle", "Простые настройки",
            "Обычное меню стало компактнее. Проверки каналов и восстановление перенесены в инженерный раздел."),
        ReleaseNote("check-circle", "Данные остаются на месте",
            "Платежи, события, настройки банков и оформления сохраняются при обновлении.")
    )
}
