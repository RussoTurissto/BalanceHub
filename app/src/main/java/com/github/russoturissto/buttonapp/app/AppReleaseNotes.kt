package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Only changes shipped in this particular version belong in the welcome. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("home", "Главная стала удобнее",
            "Кнопка управления платежами теперь доступна над календарём. " +
            "Размеры блоков подстраиваются под фактическую высоту экрана."),
        ReleaseNote("calendar-days", "Даты больше не сливаются",
            "В календаре появились промежутки между выделенными датами, " +
            "а сегодняшнее число и выбранный день остаются заметными."),
        ReleaseNote("settings", "Более компактная навигация",
            "Нижняя панель стала немного уже. Плавное переключение разделов сохранено."),
        ReleaseNote("book-open", "Подробная помощь по банковским Push",
            "В настройках появилась инструкция по работе с Termux и ADB: " +
            "проверка доступа, подключение уведомлений и восстановление защиты.")
    )
}
