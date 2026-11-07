package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SberNotificationPrivacyTest {
    private val p = SberNotificationPrivacy

    @Test fun detectsEnglishAndroidPlaceholder() {
        assertEquals(p.Evidence.SYSTEM_REDACTION, p.detect(
            listOf("Сбербанк"), listOf("Sensitive notification content hidden"),
            0, 0, 0, null))
    }

    @Test fun detectsLocalizedAndroidPlaceholder() {
        assertEquals(p.Evidence.SYSTEM_REDACTION, p.detect(
            listOf("Сбербанк"), listOf("Чувствительное содержимое скрыто"),
            0, 0, 0, "Чувствительное содержимое скрыто"))
    }

    @Test fun genericBankMessageIsOnlySuspicious() {
        assertEquals(p.Evidence.POSSIBLE_REDACTION, p.detect(
            listOf("Сбербанк"), listOf("Уведомление об операции"),
            0, 0, 0, null))
    }

    @Test fun balanceIsNeverClassifiedAsRedacted() {
        assertEquals(p.Evidence.NONE, p.detect(
            listOf("Сбербанк"), listOf("Теперь у тебя: 40,89 ₽"),
            4, 0, 0, null))
    }

    @Test fun anotherKindOfPushCanBeNonFinancial() {
        assertEquals(p.Evidence.NO_FINANCIAL_DATA, p.detect(
            listOf("Новости"), listOf("Расписание обновлено"),
            0, 0, 0, null))
    }
}
