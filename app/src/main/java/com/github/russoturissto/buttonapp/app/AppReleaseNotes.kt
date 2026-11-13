package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** User-facing notes for 1.1.3-rc3. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("music", "Три системных канала",
            "Уведомления Android теперь разделены только на обновления, платежи и события. Дублирующиеся старые каналы BalanceHub очищаются."),
        ReleaseNote("calendar-days", "Исправлены напоминания о событиях",
            "Недавно завершённое событие может напомнить о себе даже тогда, когда Android запустил будильник с опозданием."),
        ReleaseNote("settings", "Звук и вибрация",
            "BalanceHub показывает действующие настройки канала. Выбрать мелодию и вибрацию можно в настройках Android."),
        ReleaseNote("alarm-clock", "Без повторов в течение дня",
            "Перезапуск приложения не создаёт дубликатов, а давно завершённые события не вызывают неожиданные напоминания."),
        ReleaseNote("check-circle", "Сохранены ваши данные",
            "Платежи, события, банковские настройки и темы остаются без изменений после обновления.")
    )
}
