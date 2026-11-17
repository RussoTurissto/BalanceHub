package com.github.russoturissto.buttonapp.app

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CustomOccurrenceRulesTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int = 10): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    private fun payment(plan: PaymentPlan) = PaymentReminder(
        "card", "Internet", "100.00", "RUB", at(2027, 1, 1),
        2, "custom", null, plan
    )

    @Test fun nonexistentMonthDaysMergeOnlyWhenAutomaticAndSameSettings() {
        val plan = PaymentPlan(mode = "monthly", monthlyEnabled = true,
            monthlyDays = setOf(29, 30, 31), startsAt = at(2027, 1, 1))
        val charges = PaymentOccurrenceEngine.month(payment(plan), 2027, 1)
        assertEquals(1, charges.size)
        assertEquals(3, charges.single().sourceIds.size)
        assertEquals(28, Calendar.getInstance().apply {
            timeInMillis = charges.single().dueAt
        }.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun manualMoveNeverMergesWithAutomaticClamp() {
        val base = PaymentPlan(mode = "monthly", monthlyEnabled = true,
            monthlyDays = setOf(29, 30, 31), startsAt = at(2027, 1, 1))
        val id = PaymentOccurrenceEngine.sourceId(2027, 1, 31)
        val plan = base.copy(edits = mapOf(id to PaymentOccurrenceEdit(
            id, movedAt = at(2027, 2, 28), manualMove = true)))
        val charges = PaymentOccurrenceEngine.month(payment(plan), 2027, 1)
        assertEquals(2, charges.size)
        assertEquals(1, charges.count { it.manuallyMoved })
    }

    @Test fun separateAmountPreventsAutomaticMerge() {
        val base = PaymentPlan(mode = "monthly", monthlyEnabled = true,
            monthlyDays = setOf(29, 30, 31), startsAt = at(2027, 1, 1))
        val id = PaymentOccurrenceEngine.sourceId(2027, 1, 31)
        val charges = PaymentOccurrenceEngine.month(payment(base.copy(
            edits = mapOf(id to PaymentOccurrenceEdit(id, amount = "350.00")))), 2027, 1)
        assertEquals(2, charges.size)
        assertTrue(charges.any { it.amount == "350.00" })
    }

    @Test fun selectedDatesAndMonthlyDaysAreIndependent() {
        val date = at(2027, 7, 9)
        val original = PaymentPlan(mode = "dates", selectedDates = setOf(date),
            monthlyDays = setOf(14, 21), monthlyEnabled = false,
            startsAt = at(2027, 1, 1))
        assertEquals(1, PaymentOccurrenceEngine.month(payment(original), 2027, 6).size)
        val monthly = original.copy(mode = "monthly", monthlyEnabled = true)
        assertEquals(2, PaymentOccurrenceEngine.month(payment(monthly), 2027, 6).size)
        assertEquals(1, PaymentOccurrenceEngine.month(payment(original), 2027, 6).size)
    }

    @Test fun movedChargeUsesActualMonthAndBulkRule() {
        val date = at(2027, 1, 15)
        val source = PaymentOccurrenceEngine.sourceId(2027, 0, 15)
        val plan = PaymentPlan(mode = "dates", selectedDates = setOf(date),
            startsAt = at(2027, 1, 1),
            edits = mapOf(source to PaymentOccurrenceEdit(
                source, movedAt = at(2027, 3, 12), manualMove = true)),
            bulkEdits = listOf(PaymentBulkEdit(
                from = at(2027, 3, 1), amount = "125.00")))
        assertEquals(0, PaymentOccurrenceEngine.month(payment(plan), 2027, 0).size)
        val moved = PaymentOccurrenceEngine.month(payment(plan), 2027, 2)
        assertEquals(1, moved.size)
        assertEquals("125.00", moved.single().amount)
        assertTrue(moved.single().manuallyMoved)
    }

    @Test fun skippingDoesNotEraseChargeHistory() {
        val date = at(2027, 4, 18)
        val id = PaymentOccurrenceEngine.sourceId(2027, 3, 18)
        val plan = PaymentPlan(mode = "dates", selectedDates = setOf(date),
            startsAt = at(2027, 1, 1)).withEdit(
            PaymentOccurrenceEdit(id, skipped = true), "skip", date, date)
        assertTrue(PaymentOccurrenceEngine.month(payment(plan), 2027, 3)
            .single().skipped)
        assertEquals(1, plan.history.size)
    }

    @Test fun archivingSkippedLastChargeWaitsUntilEndOfDay() {
        val due = at(2027, 4, 18)
        val id = PaymentOccurrenceEngine.sourceId(2027, 3, 18)
        val p = payment(PaymentPlan(mode = "dates",
            selectedDates = setOf(due), startsAt = at(2027, 1, 1),
            edits = mapOf(id to PaymentOccurrenceEdit(id, skipped = true))))
        assertEquals(PaymentOccurrenceEngine.day(at(2027, 4, 19)),
            PaymentArchiveStore.archiveDeadline(p))
    }

    @Test fun eventMultipleWeekdaysEveryTwoWeeks() {
        val anchor = at(2027, 3, 1)
        val event = PlannedEvent("gym", "Gym", "", anchor, 60, "custom",
            plan = EventPlan(mode = "weekdays",
                weekdays = setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY),
                intervalWeeks = 2, anchorAt = anchor))
        val visits = EventOccurrenceEngine.between(
            event, at(2027, 3, 1), at(2027, 3, 22))
        assertEquals(9, visits.size)
    }

    @Test fun skipTwoVisitsNextWeekKeepsPermanentEventPattern() {
        val anchor = at(2027, 3, 1)
        val base = EventPlan(mode = "weekdays", intervalWeeks = 1,
            weekdays = setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY),
            anchorAt = anchor)
        val wed = EventOccurrenceEngine.sourceId(at(2027, 3, 10))
        val fri = EventOccurrenceEngine.sourceId(at(2027, 3, 12))
        val plan = base.copy(edits = mapOf(wed to EventOccurrenceEdit(wed, skipped = true),
            fri to EventOccurrenceEdit(fri, skipped = true)))
        val event = PlannedEvent("gym", "Gym", "", anchor, 60, "custom", plan = plan)
        val visits = EventOccurrenceEngine.between(event,
            at(2027, 3, 8), at(2027, 3, 20))
        assertEquals(2, visits.count { it.skipped })
        assertTrue(visits.any { !it.skipped && it.sourceId ==
            EventOccurrenceEngine.sourceId(at(2027, 3, 15)) })
    }
}
