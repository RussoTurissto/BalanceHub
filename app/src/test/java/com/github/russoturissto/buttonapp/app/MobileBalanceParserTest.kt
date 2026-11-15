package com.github.russoturissto.buttonapp.app

import org.junit.Assert.*
import org.junit.Test

class MobileBalanceParserTest {
    @Test fun balancesFromDifferentNetworks() {
        assertEquals("509.80", MobileBalanceService.parseBalance("Баланс: 509,80 руб."))
        assertEquals("120.40", MobileBalanceService.parseBalance("На счете 120.40 р."))
        assertEquals("95", MobileBalanceService.parseBalance("Ваш баланс: 95 ₽"))
    }
    @Test fun noFalseBalanceFromAllowanceMessage() {
        assertNull(MobileBalanceService.parseBalance(
            "Пакеты действуют до 12.11.26 23:59, 800 мин. и 40.00 ГБ"
        ))
    }
}
