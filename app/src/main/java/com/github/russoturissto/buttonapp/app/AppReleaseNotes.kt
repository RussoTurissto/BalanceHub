package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Short post-install highlights for the approved BalanceHub 1.2.0 release. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("calendar-days", "Платежи и события",
            "Удобнее выбирать дату и время. Напоминания и архив работают надёжнее."),
        ReleaseNote("music", "Уведомления",
            "Отдельные мелодии, громкость и вибрация. Громкость меняется без подтверждений."),
        ReleaseNote("check-circle", "Банковские балансы",
            "Улучшено распознавание Push-уведомлений ВТБ и Т-Банка."),
        ReleaseNote("home", "Удобная навигация",
            "Жест «Назад» возвращает в предыдущее меню или на «Главную» и сворачивает приложение."),
        ReleaseNote("clock", "Настройки и диагностика",
            "Проще управлять настройками и проверять уведомления и банковские Push."),
        ReleaseNote("check-circle", "Стабильность",
            "Улучшена сохранность данных при работе и обновлении приложения.")
    )
}
