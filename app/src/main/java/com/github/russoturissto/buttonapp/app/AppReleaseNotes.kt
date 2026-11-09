package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Draft for the owner-approved BalanceHub 1.1.0 release. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("home", "Главная: платежи и события",
            "Новая двухколоночная сводка, общий календарь и раскрывающиеся списки."),
        ReleaseNote("calendar-days", "Общий календарь",
            "Платежи отмечаются бирюзовым, события сине-бирюзовым; в один день дата разделена по диагонали."),
        ReleaseNote("archive", "Архивация записей",
            "Завершившиеся единоразовые записи переходят в архив с сохранением деталей. Повторения продолжаются."),
        ReleaseNote("alarm-clock", "Выбор времени в стиле BalanceHub",
            "Тематический циферблат в 24-часовом формате и ввод времени с клавиатуры."),
        ReleaseNote("music", "15 звуков уведомлений",
            "Фирменные сигналы для платежей, событий и обновлений с предварительным прослушиванием."),
        ReleaseNote("settings", "Инженерные настройки",
            "Технические функции перенесены в скрытое меню; обычные настройки стали проще."),
        ReleaseNote("check-circle", "Удобный ввод",
            "Клавиатура предлагает заглавную букву в начале текста.")
    )
}
