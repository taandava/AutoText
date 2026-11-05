package com.jmd.helper

import android.content.Context
import app.cash.turbine.test
import com.jmd.helper.data.db.room.RoomTypeConverters
import com.jmd.helper.data.model.BackupPayloadDto
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.RecurrenceType
import com.jmd.helper.data.model.TaskBackupDto
import com.jmd.helper.data.model.TaskStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying the newly implemented architectural components:
 * 1. Kotlinx Serialization (reflection-free JSON roundtrip).
 * 2. Room Type Converters for domain enums.
 * 3. Turbine Coroutine Flow assertion pipeline.
 * 4. MockK mocking framework integration.
 */
class ModernStackArchitectureTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `test kotlinx serialization roundtrip for backup payload`() {
        val originalPayload = BackupPayloadDto(
            version = 2,
            app = "Helper",
            tasks = listOf(
                TaskBackupDto(
                    id = 101,
                    title = "Monthly Report Dispatch",
                    message = "Your invoice is ready",
                    recipient = "+919876543210",
                    scheduledTime = 1735689600000L,
                    channel = "WHATSAPP",
                    status = "PENDING",
                    recurrence = "REPEAT_MONTHS",
                    recurrenceIntervalMins = 0,
                    mediaUri = "content://media/external/images/1",
                    callDurationSeconds = 0,
                    simSlot = 1,
                    soundMode = null
                )
            )
        )

        val serialized = json.encodeToString(originalPayload)
        assertTrue(serialized.contains("Monthly Report Dispatch"))
        assertTrue(serialized.contains("+919876543210"))

        val deserialized = json.decodeFromString<BackupPayloadDto>(serialized)
        assertEquals(1, deserialized.tasks.size)
        assertEquals("Monthly Report Dispatch", deserialized.tasks[0].title)
        assertEquals("+919876543210", deserialized.tasks[0].recipient)
        assertEquals("WHATSAPP", deserialized.tasks[0].channel)
    }

    @Test
    fun `test room type converters correctly map domain enums`() {
        val converters = RoomTypeConverters()

        // ChannelType
        assertEquals("SMS", converters.fromChannelType(ChannelType.SMS))
        assertEquals(ChannelType.WHATSAPP, converters.toChannelType("WHATSAPP"))
        assertEquals(ChannelType.CALL, converters.toChannelType("CALL"))
        assertEquals(ChannelType.SMS, converters.toChannelType("UNKNOWN_CHANNEL"))

        // TaskStatus
        assertEquals("PENDING", converters.fromTaskStatus(TaskStatus.PENDING))
        assertEquals(TaskStatus.SUCCESS, converters.toTaskStatus("SUCCESS"))
        assertEquals(TaskStatus.FAILED, converters.toTaskStatus("FAILED"))
        assertEquals(TaskStatus.PENDING, converters.toTaskStatus("UNKNOWN_STATUS"))

        // RecurrenceType
        assertEquals("REPEAT_DAYS", converters.fromRecurrenceType(RecurrenceType.REPEAT_DAYS))
        assertEquals(RecurrenceType.REPEAT_WEEKS, converters.toRecurrenceType("REPEAT_WEEKS"))
        assertEquals(RecurrenceType.ONCE, converters.toRecurrenceType("UNKNOWN"))
    }

    @Test
    fun `test turbine coroutine flow assertion pipeline`() = runTest {
        val flow = flowOf("INIT", "RUNNING", "COMPLETED")

        flow.test {
            assertEquals("INIT", awaitItem())
            assertEquals("RUNNING", awaitItem())
            assertEquals("COMPLETED", awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `test mockk idiomatic kotlin mocking on android context`() {
        val mockContext = mockk<Context>()
        every { mockContext.packageName } returns "com.jmd.helper"

        assertEquals("com.jmd.helper", mockContext.packageName)
        verify(exactly = 1) { mockContext.packageName }
    }

    @Test
    fun `test kotlinx datetime conversion and formatting utilities`() {
        val testEpoch = 1735689600000L // 2025-01-01 00:00:00 UTC
        val iso = com.jmd.helper.core.AppDateTimeUtils.formatIsoDateTime(testEpoch)
        assertTrue(iso.isNotBlank())
        assertTrue(iso.contains(":"))

        val displayTime = com.jmd.helper.core.AppDateTimeUtils.formatDisplayTime(testEpoch)
        assertTrue(displayTime.contains("AM") || displayTime.contains("PM"))
    }

    @Test
    fun `test app trace executes section block transparently`() {
        val result = com.jmd.helper.core.AppTrace.section("UnitTestingTrace") {
            42 * 2
        }
        assertEquals(84, result)
    }

    @Test
    fun `test diagnostic report health metrics calculations`() {
        val testItems = listOf(
            com.jmd.helper.core.diagnostic.DiagnosticResultItem(
                id = "test_1",
                title = "Database Subsystem",
                category = "Storage",
                level = com.jmd.helper.core.diagnostic.DiagnosticLevel.PASS,
                summary = "OK",
                details = "Pass details"
            ),
            com.jmd.helper.core.diagnostic.DiagnosticResultItem(
                id = "test_2",
                title = "Notification Subsystem",
                category = "Alerts",
                level = com.jmd.helper.core.diagnostic.DiagnosticLevel.WARNING,
                summary = "Missing channel",
                details = "Warning details",
                canAutoFix = true,
                fixActionDescription = "Create channel"
            )
        )

        val report = com.jmd.helper.core.diagnostic.DiagnosticReport(
            totalTests = 2,
            passedCount = 1,
            warningCount = 1,
            failedCount = 0,
            items = testItems
        )

        assertEquals(50, report.overallHealthPercentage)
        assertEquals(false, report.isPerfect)
        assertEquals(false, report.hasCriticalFailures)
        assertEquals(2, report.items.size)
    }
}
