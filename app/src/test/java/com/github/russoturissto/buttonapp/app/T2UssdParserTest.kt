package com.github.russoturissto.buttonapp.app

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class T2UssdParserTest {
    private val screenshot = """
        На сегодня 21:38 вам доступны безлимитные звонки на t2 РФ, 800 мин. на
        остальные номера России и 40.00 ГБ из пакетов по тарифу.
        Накопленные остатки: 27457 мин. и 2720.67 ГБ.
        Пакеты действуют до 12.11.26 23:59. Параметры тарифа: *107#.
    """.trimIndent()

    @Test fun recognizesPackagesEvenWithoutBalance() {
        val parsed = T2UssdParser.parse(screenshot)
        assertNull(parsed.balance)
        assertEquals("800", parsed.planMinutes)
        assertEquals("40.00", parsed.planGigabytes)
        assertEquals("27457", parsed.savedMinutes)
        assertEquals("2720.67", parsed.savedGigabytes)
        assertTrue(parsed.hasRecognizedData)
    }

    @Test fun estimatesDateOnlyForMidnightExpiry() {
        val parsed = T2UssdParser.parse(screenshot)
        val cal = Calendar.getInstance().apply {
            timeInMillis = requireNotNull(T2UssdParser.estimatedNextDebit(parsed.packagesExpireAt))
        }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
        assertEquals(13, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun otherExpirationTimeDoesNotPretendToBeChargeDate() {
        val parsed = T2UssdParser.parse("Пакеты действуют до 12.11.26 18:00.")
        assertNotNull(parsed.packagesExpireAt)
        assertNull(T2UssdParser.estimatedNextDebit(parsed.packagesExpireAt))
    }

    @Test fun rejectsInvalidDate() {
        assertNull(T2UssdParser.parse("Пакеты действуют до 32.11.26 23:59").packagesExpireAt)
    }

    @Test fun readsActualBalanceWithoutGuessingFromMinutes() {
        assertEquals("509.80", T2UssdParser.parse("Баланс: 509,80 руб.").balance)
        assertNull(T2UssdParser.parse(screenshot).balance)
        assertFalse(T2UssdParser.parse("Неизвестный текст").hasRecognizedData)
    }
}
