package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** List only changes shipped with the current application update. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("download", "Обновления без браузера",
            "BalanceHub проверяет новые релизы GitHub. Скачивайте APK прямо в приложении " +
            "и следите за ходом загрузки."),
        ReleaseNote("shield-check", "Безопасная установка",
            "Перед установкой проверяются SHA-256, версия, пакет и сертификат подписи. " +
            "Установку подтверждает Android."),
        ReleaseNote("bell", "Уведомления о новых версиях",
            "Управляйте оповещениями и вручную проверяйте обновления в настройках. " +
            "Фоновая проверка учитывает энергосбережение телефона."),
        ReleaseNote("settings", "Компактная нижняя панель",
            "Плавающая панель стала уже и ниже. Плавные переходы между разделами " +
            "и крупная шестерёнка сохранены.")
    )
}
