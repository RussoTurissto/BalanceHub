package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCardOrderTest {
    private data class Item(val name: String, val amount: Long?)

    private fun order(items: List<Item>): List<String> =
        BalanceCardOrder.withBalanceFirst(items) { it.amount != null }.map { it.name }

    @Test fun bankWithNoDataMovesBelowBanksWithBalances() {
        val banks = listOf(Item("Сбербанк", 2189L), Item("ВТБ", null),
            Item("Т-Банк", 18831L))
        assertEquals(listOf("Сбербанк", "Т-Банк", "ВТБ"), order(banks))
    }

    @Test fun mobileOperatorsSortIndependentlyAndKeepTheirRelativeOrder() {
        val operators = listOf(Item("МТС", null), Item("T2", 0L),
            Item("МегаФон", 450L), Item("Билайн", null))
        assertEquals(listOf("T2", "МегаФон", "МТС", "Билайн"), order(operators))
    }

    @Test fun newlyReceivedOrLostBalanceRepositionsOnlyItsCard() {
        val original = listOf(Item("Сбербанк", 2189L), Item("ВТБ", null),
            Item("Т-Банк", 18831L))
        assertEquals(listOf("Сбербанк", "Т-Банк", "ВТБ"), order(original))
        assertEquals(listOf("Сбербанк", "ВТБ", "Т-Банк"),
            order(original.map { if (it.name == "ВТБ") it.copy(amount = 500L)
                else if (it.name == "Т-Банк") it.copy(amount = null) else it }))
    }

    @Test fun emptyAllKnownAndAllUnknownListsDoNotChange() {
        assertEquals(emptyList<String>(), order(emptyList()))
        assertEquals(listOf("A", "B"), order(listOf(Item("A", 0L), Item("B", 5L))))
        assertEquals(listOf("A", "B"), order(listOf(Item("A", null), Item("B", null))))
    }
}
