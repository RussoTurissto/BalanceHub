package com.github.russoturissto.buttonapp.app

/**
 * Offline operator identity catalog. MCC/MNC is used only as a hint:
 * virtual operators and number portability can make it ambiguous.
 * USSD commands must be verified before enabling automated calls.
 */
internal data class RussianMobileOperator(
    val displayName: String,
    val shortLabel: String,
    val namePatterns: List<String>,
    val networkCodes: Set<String> = emptySet()
)

internal object RussianMobileOperators {
    private val entries = listOf(
        RussianMobileOperator("T2 (Tele2)", "t2", listOf("tele2", "t2"), setOf("25020")),
        RussianMobileOperator("МТС", "МТС", listOf("mts", "мтс"), setOf("25001")),
        RussianMobileOperator("Билайн", "б", listOf("beeline", "билайн", "vimpelcom"), setOf("25099")),
        RussianMobileOperator("МегаФон", "М", listOf("megafon", "мегафон"), setOf("25002")),
        RussianMobileOperator("Yota", "Y", listOf("yota", "йота")),
        RussianMobileOperator("Т-Мобайл", "Т", listOf("t-mobile", "т-мобайл", "тинькофф мобайл")),
        RussianMobileOperator("СберМобайл", "С", listOf("sbermobile", "сбермобайл")),
        RussianMobileOperator("Ростелеком", "Р", listOf("rostelecom", "ростелеком"))
    )

    fun find(networkCode: String, networkName: String): RussianMobileOperator? {
        val normalized = networkName.lowercase(java.util.Locale.ROOT)
        return entries.firstOrNull { operator ->
            operator.namePatterns.any { normalized.contains(it) }
        } ?: entries.firstOrNull { networkCode in it.networkCodes }
    }
}
