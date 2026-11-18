package com.github.russoturissto.buttonapp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Calendar windows must keep manually moved and skipped dates visible. */
class ScheduledCalendarRegressionTest {
    private fun at(day: Int, month: Int = Calendar.MARCH): Long =
        Calendar.getInstance().apply {
            clear()
            set(2027, month, day, 9, 0, 0)
        }.timeInMillis

    @Test fun standardWeeklyEventSupportsMovedAndSkippedOccurrences() {
        val origin = at(4)
        val first = EventOccurrenceEngine.sourceId(origin)
        val skipped = EventOccurrenceEngine.sourceId(at(11))
        val plan = EventPlan(anchorAt = origin, edits = mapOf(
            first to EventOccurrenceEdit(first, movedAt = at(13)),
            skipped to EventOccurrenceEdit(skipped, skipped = true)
        ))
        val event = PlannedEvent("evt", "Событие", "", origin, 60, "weekly",
            plan = plan)
        val dates = EventOccurrenceEngine.between(event, at(1), at(31))
        assertEquals(listOf(at(11), at(13), at(18), at(25)),
            dates.map { it.dueAt })
        assertTrue(dates.first().skipped)
        assertTrue(dates[1].moved)
        assertEquals(at(13), EventOccurrenceEngine.next(event, at(10))?.dueAt)
    }

    @Test fun oneOffEventMovedAcrossMonthIsStillVisible() {
        val id = EventOccurrenceEngine.sourceId(at(4))
        val event = PlannedEvent("evt", "Событие", "", at(4), 60, "once",
            plan = EventPlan(anchorAt = at(4),
                edits = mapOf(id to EventOccurrenceEdit(id, movedAt = at(2, Calendar.APRIL)))))
        val april = EventOccurrenceEngine.between(event,
            at(1, Calendar.APRIL), at(1, Calendar.MAY))
        assertEquals(1, april.size)
        assertEquals(at(2, Calendar.APRIL), april.single().dueAt)
    }

    @Test fun ordinaryPaymentMovedAcrossMonthRemainsEditable() {
        val id = PaymentOccurrenceEngine.sourceId(2027, Calendar.MARCH, 4)
        val plan = PaymentPlan(mode = "legacy", startsAt = at(4),
            edits = mapOf(id to PaymentOccurrenceEdit(id,
                movedAt = at(2, Calendar.APRIL), manualMove = true)))
        val payment = PaymentReminder("pay", "Аренда", "130", "RUB",
            at(4), 2, "once", plan = plan)
        val april = PaymentOccurrenceEngine.month(payment, 2027, Calendar.APRIL)
        assertEquals(1, april.size)
        assertEquals(at(2, Calendar.APRIL), april.single().dueAt)
        assertFalse(april.single().skipped)
    }
}
