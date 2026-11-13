package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Changelog actually shown to the user after the 1.1.3-rc2 upgrade. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("calendar-days", "Ежедневные напоминания",
            "Платежи и события напоминают о себе не чаще одного раза в день до назначенного срока. Повторный запуск приложения не создаёт дублей."),
        ReleaseNote("alarm-clock", "Правильное завершение периода",
            "Повторяющиеся платежи переходят к следующему сроку только после окончания текущего."),
        ReleaseNote("music", "Мелодии и вибрация Android",
            "В настройках показывается звук активного канала. Если он изменён или отключён в Android, BalanceHub сообщает об этом."),
        ReleaseNote("settings", "Компактные уведомления",
            "Настройки платежей, событий и обновлений стали короче. Проверки звука, вибромотора и системных каналов находятся в инженерном меню."),
        ReleaseNote("archive", "Архив в едином стиле",
            "Архив платежей получил компактные строки и раскрывающиеся действия восстановления и удаления."),
        ReleaseNote("check-circle", "Надёжность уведомлений",
            "Мелодии доступны Android без зависимости от временного кеша. Настройки и ранее сохранённые записи остаются на месте.")
    )
}
