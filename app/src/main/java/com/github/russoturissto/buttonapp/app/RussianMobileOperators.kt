package com.github.russoturissto.buttonapp.app

import java.util.Locale

/**
 * Offline registry for Russian mobile providers, including regional and MVNO brands.
 * The host network's MCC/MNC is only a hint: SIM names are checked first.
 * The USSD command is present only for a documented balance inquiry.
 */
internal data class RussianMobileOperator(
    val displayName: String,
    val shortLabel: String,
    val namePatterns: List<String>,
    val networkCodes: Set<String> = emptySet(),
    val colorHex: String = "#24575B",
    val balanceUssd: String? = null
)

internal object RussianMobileOperators {
    val all: List<RussianMobileOperator> = listOf(
        RussianMobileOperator("T2 (Tele2)", "t2",
            listOf("tele2", "t2"), setOf("25020"), "#083B37", "*105#"),
        RussianMobileOperator("МТС", "МТС",
            listOf("mts", "мтс"), setOf("25001"), "#CE1737", "*100#"),
        RussianMobileOperator("МегаФон", "М",
            listOf("megafon", "мегафон"), setOf("25002"), "#23A848", "*100#"),
        RussianMobileOperator("Билайн", "б",
            listOf("beeline", "билайн", "vimpelcom"), setOf("25099"), "#E4B714", "*102#"),
        RussianMobileOperator("Yota", "Y",
            listOf("yota", "йота"), colorHex = "#168EC1", balanceUssd = "*101#"),
        RussianMobileOperator("СберМобайл", "С",
            listOf("sbermobile", "сбермобайл"), colorHex = "#148B58", balanceUssd = "*100#"),
        RussianMobileOperator("Т-Мобайл", "Т",
            listOf("т-мобайл", "тинькофф мобайл", "t-mobile", "tinkoff mobile"),
            colorHex = "#D5A90C"),
        RussianMobileOperator("Миранда", "М",
            listOf("миранда", "miranda", "miranda-media"), setOf("25054"),
            "#6D43B5", "*100#"),
        RussianMobileOperator("МКС (Лугаком)", "МКС",
            listOf("мкс", "лугаком", "mks", "mcs", "lugacom"),
            colorHex = "#7AAE36", balanceUssd = "*101#"),
        RussianMobileOperator("Мотив", "М",
            listOf("motiv", "мотив"), colorHex = "#E66B22"),
        RussianMobileOperator("Летай", "Л",
            listOf("letai", "летай", "таттелеком"), colorHex = "#2778AB"),
        RussianMobileOperator("Ростелеком", "Р",
            listOf("rostelecom", "ростелеком"), colorHex = "#6F42AB"),
        RussianMobileOperator("Волна", "В",
            listOf("volna", "волна мобайл"), colorHex = "#1C91C4"),
        RussianMobileOperator("Win Mobile", "W",
            listOf("win mobile", "к-телеком", "winmobile"), colorHex = "#E55468"),
        RussianMobileOperator("Севмобайл", "С",
            listOf("севмобайл", "sevmobile"), colorHex = "#3187AC"),
        RussianMobileOperator("Феникс", "Ф",
            listOf("феникс", "phoenix"), colorHex = "#3B638B"),
        RussianMobileOperator("ГПБ Мобайл", "Г",
            listOf("гпб мобайл", "gp b mobile", "газпромбанк мобайл"), colorHex = "#166496"),
        RussianMobileOperator("ВТБ Мобайл", "В",
            listOf("втб мобайл", "vtb mobile"), colorHex = "#1669AA"),
        RussianMobileOperator("Газпромбанк Мобайл", "Г",
            listOf("gazprombank mobile"), colorHex = "#1A5C89"),
        RussianMobileOperator("Альфа-Мобайл", "А",
            listOf("альфа-мобайл", "alfa mobile"), colorHex = "#BA1538"),
        RussianMobileOperator("MCN Telecom", "MCN",
            listOf("mcn telecom", "мсн телеком"), colorHex = "#F06C34"),
        RussianMobileOperator("Тинькофф Мобайл", "Т",
            listOf("tinkoff mobile legacy"), colorHex = "#D5A90C"),
        RussianMobileOperator("DANYCOM", "D",
            listOf("danycom", "даником"), colorHex = "#4098B8"),
        RussianMobileOperator("ОСНОВА Телеком", "О",
            listOf("основа телеком"), colorHex = "#417B87")
    )

    fun byName(name: String): RussianMobileOperator? =
        all.firstOrNull { it.displayName.equals(name.trim(), ignoreCase = true) }

    fun find(networkCode: String, networkName: String): RussianMobileOperator? {
        val normalized = networkName.lowercase(Locale.ROOT)
        return all.firstOrNull { operator ->
            operator.namePatterns.any { normalized.contains(it) }
        } ?: all.firstOrNull { networkCode in it.networkCodes }
    }
}
