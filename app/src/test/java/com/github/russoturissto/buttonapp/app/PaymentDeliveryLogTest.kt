package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentDeliveryLogTest {
    private val due = 1_800_000_000_000L

    @Test fun earlySignalShowsNegativeTimingInsteadOfZeroDelay() {
        assertEquals("Опережение относительно плана: 46 сек",
            PaymentDeliveryLog.timingLine(due, due - 46_000L))
        assertEquals("Опережение относительно плана: 1 сек",
            PaymentDeliveryLog.timingLine(due, due - 500L))
    }

    @Test fun onTimeAndLateSignalKeepPositiveDelay() {
        assertEquals("Задержка Android: 0 сек",
            PaymentDeliveryLog.timingLine(due, due))
        assertEquals("Задержка Android: 60 сек",
            PaymentDeliveryLog.timingLine(due, due + 60_000L))
    }

    @Test fun unknownTimestampHasNoMisleadingDelay() {
        assertNull(PaymentDeliveryLog.timingLine(0L, due))
        assertNull(PaymentDeliveryLog.timingLine(due, 0L))
    }
}
