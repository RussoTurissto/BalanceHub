package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RussianMobileOperatorsTest {
    @Test fun recognizesT2ByName() {
        assertEquals("T2 (Tele2)", RussianMobileOperators.find("", "Tele2")?.displayName)
        assertEquals("T2 (Tele2)", RussianMobileOperators.find("", "t2")?.displayName)
    }

    @Test fun recognizesT2ByNetworkCode() {
        assertEquals("T2 (Tele2)", RussianMobileOperators.find("25020", "")?.displayName)
    }

    @Test fun recognizesMirandaAndMks() {
        assertEquals("Миранда", RussianMobileOperators.find("25054", "Miranda")?.displayName)
        assertEquals("МКС (Лугаком)", RussianMobileOperators.find("", "МКС")?.displayName)
        assertEquals("*101#", RussianMobileOperators.find("", "МКС")?.balanceUssd)
        assertEquals("*100#", RussianMobileOperators.find("", "Miranda")?.balanceUssd)
    }

    @Test fun allNamedOperatorsHaveDistinctStyleIcons() {
        assertTrue(RussianMobileOperators.all.size >= 18)
        assertTrue(RussianMobileOperators.all.all { it.shortLabel.isNotBlank() &&
            it.colorHex.startsWith("#") })
    }

    @Test fun recognizesOtherOperators() {
        assertEquals("МТС", RussianMobileOperators.find("", "МТС")?.displayName)
        assertEquals("Билайн", RussianMobileOperators.find("", "Beeline")?.displayName)
        assertEquals("МегаФон", RussianMobileOperators.find("25002", "")?.displayName)
    }

    @Test fun nameTakesPrecedenceOverNetworkCode() {
        assertEquals("T2 (Tele2)", RussianMobileOperators.find("25001", "TELE2")?.displayName)
    }

    @Test fun unknownOperatorIsNotGuessed() {
        assertNull(RussianMobileOperators.find("", ""))
        assertNull(RussianMobileOperators.find("12345", "Unidentified"))
    }
}
