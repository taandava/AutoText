package com.jmd.autotext

import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ScheduledTask
import com.jmd.autotext.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task-model coverage. Recurrence arithmetic is exercised against the real
 * engine in [RecurrenceEngineTest]; keeping it here would duplicate that logic.
 */
class TaskEngineTest {
    @Test
    fun testAllChannelTypesCoverage() {
        val channels = ChannelType.entries
        assertTrue(channels.contains(ChannelType.SMS))
        assertTrue(channels.contains(ChannelType.WHATSAPP))
        assertTrue(channels.contains(ChannelType.WHATSAPP_BUSINESS))
        assertTrue(channels.contains(ChannelType.WHATSAPP_GROUP))
        assertTrue(channels.contains(ChannelType.WHATSAPP_STATUS))
        assertTrue(channels.contains(ChannelType.CALL))
        assertTrue(channels.contains(ChannelType.REMINDER))
        assertTrue(channels.contains(ChannelType.FAKE_CALL))
    }

    @Test
    fun testScheduledTaskStatusTransitions() {
        var task = ScheduledTask(
            id = 42L,
            title = "Follow-up status",
            channel = ChannelType.WHATSAPP_STATUS,
            recipients = listOf("STATUS"),
            messageText = "Status update text"
        )
        assertEquals(TaskStatus.PENDING, task.status)

        task = task.copy(status = TaskStatus.DONE)
        assertEquals(TaskStatus.DONE, task.status)

        task = task.copy(status = TaskStatus.FAILED)
        assertEquals(TaskStatus.FAILED, task.status)
    }
}
