package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Release text must be updated whenever new functionality is shipped. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("calendar-days", "События и важные даты",
            "Планируйте встречи, праздники и личные дела. Настраивайте повторы и время уведомлений, завершайте события и сохраняйте их в архиве."),
        ReleaseNote("credit-card", "Платежи стали удобнее",
            "Новое напоминание теперь создаётся в отдельном окне. Список платежей остаётся перед глазами."),
        ReleaseNote("sparkles", "Иконки, которые понимают смысл",
            "Общая библиотека тематических иконок помогает узнавать платежи и события с первого взгляда. Иконку можно выбрать вручную."),
        ReleaseNote("settings", "Обновлённая навигация",
            "Добавлена вкладка «События», а настройки открываются через компактную шестерёнку. Приложение работает в портретной ориентации.")
    )
}
