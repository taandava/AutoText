package com.jmd.autotext

import com.jmd.autotext.model.RecurrenceType
import com.jmd.autotext.scheduler.AlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for the production recurrence engine.
 *
 * These assertions call [AlarmScheduler.calculateNextTrigger] directly. The
 * previous version of the recurrence suite re-declared a local copy of the
 * algorithm and asserted that copy against itself, so it could never detect a
 * regression in the real scheduling code.
 */
class RecurrenceEngineTest {

    /** Builds a fixed local-time instant so expectations are timezone-stable. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun next(recurrence: RecurrenceType, from: Long, interval: Int = 0): Long =
        AlarmScheduler.calculateNextTrigger(recurrence, interval, from)

    private fun calendarOf(millis: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = millis }

    @Test
    fun dailyAdvancesExactlyOneCalendarDay() {
        assertEquals(at(2026, Calendar.MARCH, 11), next(RecurrenceType.DAILY, at(2026, Calendar.MARCH, 10)))
    }

    @Test
    fun dailyCrossesMonthAndYearBoundaries() {
        assertEquals(
            at(2026, Calendar.MARCH, 1),
            next(RecurrenceType.DAILY, at(2026, Calendar.FEBRUARY, 28))
        )
        assertEquals(
            at(2027, Calendar.JANUARY, 1),
            next(RecurrenceType.DAILY, at(2026, Calendar.DECEMBER, 31))
        )
    }

    @Test
    fun weeklyAdvancesExactlyOneWeek() {
        assertEquals(at(2026, Calendar.MARCH, 17), next(RecurrenceType.WEEKLY, at(2026, Calendar.MARCH, 10)))
    }

    @Test
    fun weekdaysSkipsSaturdayAndSunday() {
        // Friday 2026-03-13 must land on Monday 2026-03-16.
        val result = calendarOf(next(RecurrenceType.WEEKDAYS, at(2026, Calendar.MARCH, 13)))
        assertEquals(Calendar.MONDAY, result.get(Calendar.DAY_OF_WEEK))
        assertEquals(at(2026, Calendar.MARCH, 16), result.timeInMillis)
    }

    @Test
    fun weekendsSkipsWeekdays() {
        // Friday 2026-03-13 must land on Saturday 2026-03-14.
        val result = calendarOf(next(RecurrenceType.WEEKENDS, at(2026, Calendar.MARCH, 13)))
        assertEquals(Calendar.SATURDAY, result.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun weekendsFromSundayAdvancesToNextSaturday() {
        val result = calendarOf(next(RecurrenceType.WEEKENDS, at(2026, Calendar.MARCH, 15)))
        assertEquals(Calendar.SATURDAY, result.get(Calendar.DAY_OF_WEEK))
    }

    /**
     * Regression guard: plain Calendar.add(MONTH, 1) on 31 Jan produced
     * 3 March, silently skipping February.
     */
    @Test
    fun monthlyFromJanThirtyOneClampsToEndOfFebruary() {
        val result = calendarOf(next(RecurrenceType.MONTHLY, at(2026, Calendar.JANUARY, 31)))
        assertEquals(Calendar.FEBRUARY, result.get(Calendar.MONTH))
        assertEquals(28, result.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun monthlyFromJanThirtyOneClampsToFebTwentyNineInLeapYear() {
        val result = calendarOf(next(RecurrenceType.MONTHLY, at(2028, Calendar.JANUARY, 31)))
        assertEquals(Calendar.FEBRUARY, result.get(Calendar.MONTH))
        assertEquals(29, result.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun monthlyFromAugThirtyOneClampsToThirtyDays() {
        val result = calendarOf(next(RecurrenceType.MONTHLY, at(2026, Calendar.AUGUST, 31)))
        assertEquals(Calendar.SEPTEMBER, result.get(Calendar.MONTH))
        assertEquals(30, result.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun monthlyMidMonthIsUnaffectedByClamping() {
        val result = calendarOf(next(RecurrenceType.MONTHLY, at(2026, Calendar.MARCH, 15)))
        assertEquals(Calendar.APRIL, result.get(Calendar.MONTH))
        assertEquals(15, result.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun yearlyFromFebTwentyNineDegradesToFebTwentyEightOnNonLeapYear() {
        val result = calendarOf(next(RecurrenceType.YEARLY, at(2028, Calendar.FEBRUARY, 29)))
        assertEquals(2029, result.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, result.get(Calendar.MONTH))
        assertEquals(28, result.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun yearlyNormalDateKeepsSameMonthAndDay() {
        val result = calendarOf(next(RecurrenceType.YEARLY, at(2026, Calendar.JULY, 4)))
        assertEquals(2027, result.get(Calendar.YEAR))
        assertEquals(Calendar.JULY, result.get(Calendar.MONTH))
        assertEquals(4, result.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun customIntervalUsesConfiguredMinutes() {
        val result = calendarOf(
            next(RecurrenceType.CUSTOM_INTERVAL, at(2026, Calendar.MARCH, 10, hour = 9), 45)
        )
        assertEquals(9, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, result.get(Calendar.MINUTE))
    }

    @Test
    fun customIntervalFallsBackToSixtyMinutesWhenUnset() {
        val result = calendarOf(
            next(RecurrenceType.CUSTOM_INTERVAL, at(2026, Calendar.MARCH, 10, hour = 9), 0)
        )
        assertEquals(10, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))
    }

    @Test
    fun onceReturnsTheReferenceInstantUnchanged() {
        val from = at(2026, Calendar.MARCH, 10)
        assertEquals(from, next(RecurrenceType.ONCE, from))
    }

    /**
     * Every recurring rule must land strictly in the future, otherwise the
     * alarm fires immediately and loops.
     */
    @Test
    fun everyRecurringRuleAdvancesStrictlyForward() {
        val from = at(2026, Calendar.JANUARY, 31)

        RecurrenceType.entries
            .filter { it != RecurrenceType.ONCE }
            .forEach { recurrence ->
                val result = next(recurrence, from, interval = 30)
                assertTrue(
                    "$recurrence did not advance strictly forward (got $result, from $from)",
                    result > from
                )
            }
    }
}
