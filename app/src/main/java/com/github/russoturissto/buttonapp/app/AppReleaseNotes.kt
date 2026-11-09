package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Visible changelog for the BalanceHub 1.1.1 update. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("music", "15 новых звуков с собственным характером",
            "Хрусталь, капля, бамбук, бриз, орбита и другие — теперь не вариации одной мелодии."),
        ReleaseNote("music", "Громкость внутри BalanceHub",
            "Общая или отдельная настройка звуков платежей, событий и обновлений."),
        ReleaseNote("calendar-days", "Единые размеры карточек",
            "Блоки событий выровнены с платежами: сетка, значки и отступы."),
        ReleaseNote("settings", "Настройки без лишних блоков",
            "Звуки по умолчанию свёрнуты, разрешение точных напоминаний показывается только при необходимости."),
        ReleaseNote("credit-card", "Помощь по балансу Сбербанка",
            "Объяснения работы Push, пошаговое подключение и диагностика без передачи данных в интернет."),
        ReleaseNote("check-circle", "Понятное отсутствие данных",
            "В деталях мобильной связи и Сбербанка отображаются дружелюбные пояснения, когда баланс неизвестен."),
        ReleaseNote("archive", "Советы по напоминаниям и безопасности",
            "Обновлённая справка по платежам, событиям, уведомлениям и конфиденциальности.")
    )
}
