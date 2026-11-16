package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Short post-install notes for the 1.2.1 release-candidate series. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("check-circle", "Тестовые версии BalanceHub",
            "Новые сборки выходят как публичные Pre-release с последовательными номерами rc."),
        ReleaseNote("download", "Удобное скачивание APK",
            "Тестовая APK доступна на GitHub и по прямой ссылке."),
        ReleaseNote("home", "Привычная работа приложения",
            "Платежи, события, банковские балансы, звуки и жест «Назад» не изменены.")
    )
}
