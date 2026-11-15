package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorBrandMarksTest {
    @Test fun everyRegisteredOperatorHasDedicatedIcon() {
        val operators = RussianMobileOperators.all
        assertTrue(operators.isNotEmpty())
        for (op in operators) {
            assertNotEquals("No icon for " + op.displayName,
                OperatorBrandMark.UNKNOWN, OperatorBrandMarks.fromOperator(op))
        }
        assertEquals(operators.size, operators.map {
            OperatorBrandMarks.fromOperator(it)
        }.toSet().size)
    }

    @Test fun t2MirandaAndMksHaveDistinctGraphics() {
        val list = RussianMobileOperators.all
        assertEquals(OperatorBrandMark.T2, OperatorBrandMarks.fromOperator(
            list.first { it.displayName == "T2 (Tele2)" }))
        assertEquals(OperatorBrandMark.MIRANDA, OperatorBrandMarks.fromOperator(
            list.first { it.displayName == "Миранда" }))
        assertEquals(OperatorBrandMark.MKS, OperatorBrandMarks.fromOperator(
            list.first { it.displayName == "МКС (Лугаком)" }))
    }

    @Test fun unknownSIMStillHasDrawableFallback() {
        assertEquals(OperatorBrandMark.UNKNOWN, OperatorBrandMarks.fromOperator(null))
    }
}
