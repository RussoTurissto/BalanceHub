package com.github.russoturissto.buttonapp.app

internal data class ReleaseNote(val iconId: String, val title: String, val description: String)

/** Keep this list exclusive to changes actually introduced by this version. */
internal object AppReleaseNotes {
    val current: List<ReleaseNote> = listOf(
        ReleaseNote("settings", "Новая нижняя панель",
            "Все разделы теперь объединены в одну аккуратную панель. Настройки открываются через увеличенную шестерёнку справа."),
        ReleaseNote("sparkles", "Плавное переключение",
            "Бирюзовое выделение мягко перемещается между вкладками. Переходы одинаково работают при нажатиях и свайпах."),
        ReleaseNote("smartphone", "Комфорт на вашем экране",
            "Анимация следует настройкам Android и частоте обновления дисплея, не изменяя параметры телефона."),
        ReleaseNote("shield-check", "Больше стабильности",
            "Исправлены переходы во вложенные настройки Сбербанка и мобильной связи; нижняя панель больше не должна смещаться при переключении.")
    )
}
