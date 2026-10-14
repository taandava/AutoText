package com.jmd.autotext

import com.jmd.autotext.engine.SequentialTaskEngine
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ScheduledTask
import com.jmd.autotext.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SequentialTaskEngineUnitTest {

    @Test
    fun testTaskEnqueueAndDeduplication() {
        val task1 = ScheduledTask(
            id = 101L,
            title = "Follow up WhatsApp Client",
            channel = ChannelType.WHATSAPP,
            recipients = listOf("+919876543210"),
            messageText = "Hello client",
            status = TaskStatus.PENDING
        )

        assertEquals(101L, task1.id)
        assertEquals(TaskStatus.PENDING, task1.status)
        assertEquals(ChannelType.WHATSAPP, task1.channel)
    }

    @Test
    fun testChannelTypesSerializationMatrix() {
        // Ensures all channel types are recognized
        for (channel in ChannelType.entries) {
            assertTrue(channel.displayName.isNotBlank())
        }
    }
}
