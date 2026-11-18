package com.jmd.superapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStatusTest {

    @Test
    fun `fromString maps valid and legacy strings correctly`() {
        assertEquals(TaskStatus.PENDING, TaskStatus.fromString(null))
        assertEquals(TaskStatus.PENDING, TaskStatus.fromString(""))
        assertEquals(TaskStatus.SUCCESS, TaskStatus.fromString("SUCCESS"))
        assertEquals(TaskStatus.DONE, TaskStatus.fromString("DONE"))
        assertEquals(TaskStatus.SUCCESS, TaskStatus.fromString("SENT"))
        assertEquals(TaskStatus.SCHEDULED, TaskStatus.fromString("ACTIVE"))
        assertEquals(TaskStatus.PENDING, TaskStatus.fromString("PENDING"))
        assertEquals(TaskStatus.FAILED, TaskStatus.fromString("FAILED"))
        assertEquals(TaskStatus.CANCELLED, TaskStatus.fromString("CANCELLED"))
        assertEquals(TaskStatus.CANCELLED, TaskStatus.fromString("CANCELED"))
        assertEquals(TaskStatus.RUNNING, TaskStatus.fromString("RUNNING"))
    }

    @Test
    fun `canTransition enforces valid state machine progressions`() {
        // Identity
        assertTrue(TaskStatus.canTransition(TaskStatus.PENDING, TaskStatus.PENDING))
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.RUNNING))

        // Pending to Running
        assertTrue(TaskStatus.canTransition(TaskStatus.PENDING, TaskStatus.RUNNING))
        assertTrue(TaskStatus.canTransition(TaskStatus.SCHEDULED, TaskStatus.RUNNING))

        // Running to Success / Failed / Cancelled
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.SUCCESS))
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.FAILED))
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.CANCELLED))

        // Terminal states cannot directly jump to running without rescheduling
        assertFalse(TaskStatus.canTransition(TaskStatus.CANCELLED, TaskStatus.RUNNING))
    }

    @Test
    fun `ChannelType fromString maps channel variants accurately`() {
        assertEquals(ChannelType.SMS, ChannelType.fromString(null))
        assertEquals(ChannelType.SMS, ChannelType.fromString(""))
        assertEquals(ChannelType.WHATSAPP, ChannelType.fromString("WHATSAPP"))
        assertEquals(ChannelType.WHATSAPP_BUSINESS, ChannelType.fromString("WHATSAPP_BUSINESS"))
        assertEquals(ChannelType.WHATSAPP_GROUP, ChannelType.fromString("WHATSAPP_GROUP"))
        assertEquals(ChannelType.WHATSAPP_GROUP, ChannelType.fromString("GROUP"))
        assertEquals(ChannelType.WHATSAPP_STATUS, ChannelType.fromString("WHATSAPP_STATUS"))
        assertEquals(ChannelType.CALL, ChannelType.fromString("CALL"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("CALL_SIMULATION"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("SIMULATION"))
        assertEquals(ChannelType.REMINDER, ChannelType.fromString("REMINDER"))
    }
}
