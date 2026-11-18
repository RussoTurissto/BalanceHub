package com.github.russoturissto.buttonapp.app

/** Short, non-technical notes shown to the user, never shared across RC builds. */
internal data class ReleaseNote(val iconId: String, val title: String, val description: String = "")

/**
 * A test APK describes only the changes first made in that exact candidate.
 * The stable APK consolidates the user-visible work from all its candidates.
 * History lists stable releases only (confirmed releases also come from GitHub).
 */
internal object AppReleaseNotes {
    private fun note(icon: String, text: String) = ReleaseNote(icon, text)

    private val rc1 = listOf(
        note("check-circle", "Тестовая сборка без новых функций.")
    )
    private val rc2 = listOf(
        note("credit-card", "Улучшено отображение банковских карт и счетов."),
        note("settings", "Добавлено управление видимостью отдельных счетов."),
        note("calendar-days", "Улучшено управление предстоящими платежами и событиями."),
        note("settings", "Улучшено управление настройками.")
    )
    private val rc3 = listOf(
        note("credit-card", "Единое оформление всех балансов."),
        note("wallet", "Более компактные банковские карточки."),
        note("check-circle", "Исправлены ошибки обучения.")
    )
    private val rc4 = listOf(
        note("check-circle", "Обновлена информация в окне «Что нового»."),
        note("clock", "Добавлена история изменений по версиям.")
    )

    private val securityRc1 = listOf(
        note("shield-check", "Добавлена защита приложения четырёхзначным кодом."),
        note("shield-check", "Можно включить вход по отпечатку пальца Android."),
        note("alarm-clock", "Добавлены настройки повторной блокировки."),
        note("settings", "В настройках появился раздел «Безопасность».")
    )

    private val securityRc2 = listOf(
        note("fingerprint", "Отпечаток пальца запрашивается при входе автоматически."),
        note("shield-check", "Добавлено управление разрешением снимков экрана."),
        note("calendar-days", "Платежи и события получили компактные списки и календари.")
    )

    private val stable123 = (securityRc1 + securityRc2).distinctBy { it.title }

    private val stable122 = listOf(
        note("wallet", "Платежи: улучшено выравнивание элементов."),
        note("calendar-days", "События: унифицировано оформление полей и повторений."),
        note("home", "Интерфейс: выровнены отступы и расстояния между элементами.")
    )

    private val stable120 = listOf(
        note("calendar-days", "Улучшены платежи, события и напоминания."),
        note("music", "Расширены настройки уведомлений."),
        note("credit-card", "Улучшены банковские балансы."),
        note("home", "Удобнее стала навигация."),
        note("settings", "Улучшены настройки и диагностика."),
        note("check-circle", "Повышена стабильность приложения.")
    )
    private val stable112 = listOf(
        note("settings", "Обновлено меню настроек."),
        note("music", "Расширено управление звуком и вибрацией.")
    )
    private val stable111 = listOf(
        note("music", "Добавлены новые звуки уведомлений."),
        note("calendar-days", "Улучшено оформление платежей и событий."),
        note("check-circle", "Расширена справка по банковским балансам.")
    )
    private val stable110 = listOf(
        note("home", "Обновлён главный экран."),
        note("calendar-days", "Улучшены события и платежи."),
        note("music", "Улучшены напоминания и уведомления.")
    )
    private val stable10144 = listOf(
        note("download", "Добавлено обновление приложения из настроек."),
        note("settings", "Улучшено управление обновлениями."),
        note("home", "Обновлена нижняя панель навигации.")
    )

    private val stableNotes = linkedMapOf(
        "1.2.3" to stable123,
        "1.2.2" to stable122,
        "1.2.1" to stable121(),
        "1.2.0" to stable120,
        "1.1.2" to stable112,
        "1.1.1" to stable111,
        "1.1.0" to stable110,
        "1.0.144" to stable10144,
        "1.0.142" to stable10144
    )

    private fun stable121(): List<ReleaseNote> =
        // LinkedHashMap preserves order, and removes repeated user-facing lines.
        (rc2 + rc3 + rc4).distinctBy { it.title }

    fun forVersion(versionName: String?): List<ReleaseNote> = when (versionName) {
        "1.2.3-rc1" -> securityRc1
        "1.2.3-rc2" -> securityRc2
        "1.2.1-rc1" -> rc1
        "1.2.1-rc2" -> rc2
        "1.2.1-rc3" -> rc3
        "1.2.1-rc4" -> rc4
        else -> if (versionName != null && !versionName.contains('-'))
            stableNotes[versionName].orEmpty()
        else emptyList()
    }

    fun forStableRelease(version: String): List<ReleaseNote> =
        stableNotes[version] ?: listOf(
            note("check-circle", "Обновления и исправления.")
        )

    /** Offline fallback: only stable versions actually published by November 2026. */
    fun offlineStableHistory(installedVersionName: String?): List<String> {
        val published = listOf(
            "1.2.0", "1.1.2", "1.1.1", "1.1.0", "1.0.144", "1.0.142"
        )
        return when (installedVersionName) {
            "1.2.3" -> listOf("1.2.3", "1.2.2", "1.2.1") + published
            "1.2.3-rc1", "1.2.3-rc2" -> listOf("1.2.2", "1.2.1") + published
            "1.2.2" -> listOf("1.2.2", "1.2.1") + published
            "1.2.1" -> listOf("1.2.1") + published
            else -> published
        }
    }
}
