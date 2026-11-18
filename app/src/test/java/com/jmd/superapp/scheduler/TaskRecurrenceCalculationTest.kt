package com.jmd.superapp.scheduler

import com.jmd.superapp.data.model.ChannelType
import com.jmd.superapp.data.model.RecurrenceType
import com.jmd.superapp.data.model.ScheduledTask
import com.jmd.superapp.data.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskRecurrenceCalculationTest {

    private fun sampleTask(recurrence: RecurrenceType, trigger: Long, interval: Int = 0, days: List<Int> = emptyList()) = ScheduledTask(
        id = 100L,
        title = "Recurrence Test",
        channel = ChannelType.SMS,
        recipients = listOf("+919167298798"),
        messageText = "Hello",
        triggerTimeMillis = trigger,
        recurrence = recurrence,
        customIntervalMinutes = interval,
        selectedDaysOfWeek = days,
        status = TaskStatus.PENDING
    )

    @Test
    fun `once recurrence with future trigger preserves trigger`() {
        val now = 1000000L
        val futureTrigger = 1500000L
        val task = sampleTask(RecurrenceType.ONCE, futureTrigger)
        assertEquals(futureTrigger, AlarmScheduler.calculateNextFutureTrigger(task, now))
    }

    @Test
    fun `once recurrence with past trigger advances to next future occurrence of scheduled time`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -2) // 2 days ago at 14:30
        }
        val pastTrigger = cal.timeInMillis
        val now = System.currentTimeMillis()
        val task = sampleTask(RecurrenceType.ONCE, pastTrigger)
        val next = AlarmScheduler.calculateNextFutureTrigger(task, now)
        assertTrue("Next trigger must be in the future", next > now)
        val resultCal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(14, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, resultCal.get(Calendar.MINUTE))
    }

    @Test
    fun `daily recurrence advances past now by whole days`() {
        val now = System.currentTimeMillis()
        val past = now - (2 * 24 * 60 * 60 * 1000L) // 2 days ago
        val task = sampleTask(RecurrenceType.DAILY, past)
        val next = AlarmScheduler.calculateNextFutureTrigger(task, now)
        assertTrue("Next trigger must be strictly in the future", next > now)
        val diff = next - past
        assertEquals("Must advance by exact multiple of 24h", 0L, diff % (24 * 60 * 60 * 1000L))
    }

    @Test
    fun `repeat seconds advances in the future by interval`() {
        val now = 100000L
        val past = 50000L
        val task = sampleTask(RecurrenceType.REPEAT_SECONDS, past, interval = 20)
        val next = AlarmScheduler.calculateNextFutureTrigger(task, now)
        assertTrue(next > now)
        assertEquals(110000L, next) // 50000 + 3*20000 = 110000
    }

    @Test
    fun `repeat minutes advances in future by minutes interval`() {
        val now = System.currentTimeMillis()
        val past = now - (35 * 60 * 1000L)
        val task = sampleTask(RecurrenceType.REPEAT_MINUTES, past, interval = 10)
        val next = AlarmScheduler.calculateNextFutureTrigger(task, now)
        assertTrue(next > now)
        val diffMinutes = (next - past) / (60 * 1000L)
        assertEquals(0L, diffMinutes % 10L)
    }

    @Test
    fun `weekdays recurrence lands only on monday through friday`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        val saturday = cal.timeInMillis
        val task = sampleTask(RecurrenceType.WEEKDAYS, saturday)
        val next = AlarmScheduler.calculateNextFutureTrigger(task, saturday)
        val resultCal = Calendar.getInstance().apply { timeInMillis = next }
        val dow = resultCal[Calendar.DAY_OF_WEEK]
        assertTrue(dow != Calendar.SATURDAY && dow != Calendar.SUNDAY)
        assertEquals(Calendar.MONDAY, dow)
    }
}
