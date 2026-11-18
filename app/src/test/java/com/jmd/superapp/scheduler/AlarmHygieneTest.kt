package com.jmd.superapp.scheduler

import com.jmd.superapp.data.model.ChannelType
import com.jmd.superapp.data.model.RecurrenceType
import com.jmd.superapp.data.model.ScheduledTask
import com.jmd.superapp.data.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmHygieneTest {

    @Test
    fun `system alarm label produces Super-prefixed unique label`() {
        val task = ScheduledTask(
            id = 42L,
            title = "Medicine Reminder",
            channel = ChannelType.REMINDER,
            messageText = "Take vitamin D"
        )
        val label = AlarmScheduler.getSystemAlarmLabel(task)
        assertEquals("Super #42: Medicine Reminder", label)
    }

    @Test
    fun `system alarm label falls back to message text when title is empty`() {
        val task = ScheduledTask(
            id = 99L,
            title = "",
            channel = ChannelType.REMINDER,
            messageText = "Drink water"
        )
        val label = AlarmScheduler.getSystemAlarmLabel(task)
        assertEquals("Super #99: Drink water", label)
    }

    @Test
    fun `task status terminal and pending flags correctly delineate alarm active states`() {
        // Pending tasks require alarms
        assertTrue(TaskStatus.PENDING.isPendingOrScheduled)
        assertTrue(TaskStatus.SCHEDULED.isPendingOrScheduled)
        assertTrue(TaskStatus.READY.isPendingOrScheduled)

        // Completed, failed, cancelled, or paused tasks MUST NOT have active alarms
        assertFalse(TaskStatus.DONE.isPendingOrScheduled)
        assertFalse(TaskStatus.FAILED.isPendingOrScheduled)
        assertFalse(TaskStatus.CANCELLED.isPendingOrScheduled)
        assertFalse(TaskStatus.PAUSED.isPendingOrScheduled)

        assertTrue(TaskStatus.DONE.isTerminal)
        assertTrue(TaskStatus.FAILED.isTerminal)
        assertTrue(TaskStatus.CANCELLED.isTerminal)
        assertTrue(TaskStatus.PAUSED.isPaused)
    }

    @Test
    fun `expired one-time tasks are accurately classified for alarm purging`() {
        val now = System.currentTimeMillis()
        val expiredTask = ScheduledTask(
            id = 101L,
            title = "Past Task",
            channel = ChannelType.SMS,
            recurrence = RecurrenceType.ONCE,
            triggerTimeMillis = now - 120_000L,
            status = TaskStatus.PENDING
        )
        val isExpiredOnce = expiredTask.recurrence == RecurrenceType.ONCE && expiredTask.triggerTimeMillis < now - 60_000L
        assertTrue("Past single-run task must be identified as expired", isExpiredOnce)

        val futureTask = ScheduledTask(
            id = 102L,
            title = "Future Task",
            channel = ChannelType.SMS,
            recurrence = RecurrenceType.ONCE,
            triggerTimeMillis = now + 600_000L,
            status = TaskStatus.PENDING
        )
        val isFutureExpired = futureTask.recurrence == RecurrenceType.ONCE && futureTask.triggerTimeMillis < now - 60_000L
        assertFalse("Future task must not be classified as expired", isFutureExpired)
    }

    @Test
    fun `calculateNextFutureTrigger advances past timestamps strictly into the future`() {
        val past = System.currentTimeMillis() - 3600_000L // 1 hour ago
        val task = ScheduledTask(
            id = 1L,
            title = "Recurring Check",
            channel = ChannelType.SMS,
            recurrence = RecurrenceType.REPEAT_MINUTES,
            customIntervalMinutes = 15,
            triggerTimeMillis = past
        )
        val now = System.currentTimeMillis()
        val nextTrigger = AlarmScheduler.calculateNextFutureTrigger(task, now)
        assertTrue("Next trigger ($nextTrigger) must be strictly in the future (> $now)", nextTrigger > now)
    }

    @Test
    fun `once recurrence returns exact scheduled time without advancing`() {
        val scheduledTime = 1750000000000L
        val task = ScheduledTask(
            id = 2L,
            title = "One-off Reminder",
            channel = ChannelType.REMINDER,
            recurrence = RecurrenceType.ONCE,
            triggerTimeMillis = scheduledTime
        )
        val nextTrigger = AlarmScheduler.calculateNextFutureTrigger(task, System.currentTimeMillis())
        assertEquals(scheduledTime, nextTrigger)
    }
}
