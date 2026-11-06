package com.jmd.helper

import android.content.Context
import app.cash.turbine.test
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.data.db.room.RoomTypeConverters
import com.jmd.helper.data.model.BackupPayloadDto
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.RecurrenceType
import com.jmd.helper.data.model.TaskBackupDto
import com.jmd.helper.data.model.TaskStatus
import com.jmd.helper.whatsapp.AccessibilityNodeUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
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
            ),
            dailyCampaigns = listOf(
                com.jmd.helper.data.model.CampaignBackupDto(
                    id = 1,
                    title = "Daily Client Followup",
                    channel = "WHATSAPP",
                    messageTemplate = "Good morning {name}!",
                    dailyLimit = 25,
                    simSlot = 0,
                    isActive = true
                )
            ),
            contacts = listOf(
                com.jmd.helper.data.model.ContactBackupDto(
                    id = 10,
                    displayName = "Rohit Mehta",
                    phoneNumber = "+919876543210",
                    normalizedPhone = "919876543210",
                    isWhatsAppUser = true,
                    lastReachedTimestamp = 1735689600000L,
                    reachCount = 3
                )
            )
        )

        val serialized = json.encodeToString(originalPayload)
        assertTrue(serialized.contains("Monthly Report Dispatch"))
        assertTrue(serialized.contains("+919876543210"))
        assertTrue(serialized.contains("Daily Client Followup"))
        assertTrue(serialized.contains("Rohit Mehta"))

        val deserialized = json.decodeFromString<BackupPayloadDto>(serialized)
        assertEquals(1, deserialized.tasks.size)
        assertEquals("Monthly Report Dispatch", deserialized.tasks[0].title)
        assertEquals("+919876543210", deserialized.tasks[0].recipient)
        assertEquals("WHATSAPP", deserialized.tasks[0].channel)
        assertEquals(1, deserialized.dailyCampaigns.size)
        assertEquals("Daily Client Followup", deserialized.dailyCampaigns[0].title)
        assertEquals(1, deserialized.contacts.size)
        assertEquals("Rohit Mehta", deserialized.contacts[0].displayName)
        assertTrue(deserialized.contacts[0].isWhatsAppUser)
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

    @Test
    fun `test task backup dto preserves recipient names during roundtrip`() {
        val taskWithNames = TaskBackupDto(
            id = 202,
            title = "Multi-Recipient Task",
            message = "Meeting reminder",
            recipient = "+919876543210,+919123456789",
            recipientNames = "Alice Johnson, Bob Smith",
            scheduledTime = 1735689600000L,
            channel = "WHATSAPP",
            status = "PENDING"
        )
        val payload = BackupPayloadDto(
            version = 2,
            app = "Helper",
            tasks = listOf(taskWithNames)
        )

        val serialized = json.encodeToString(payload)
        assertTrue(serialized.contains("Alice Johnson, Bob Smith"))

        val deserialized = json.decodeFromString<BackupPayloadDto>(serialized)
        assertEquals("Alice Johnson, Bob Smith", deserialized.tasks[0].recipientNames)
        assertEquals("+919876543210,+919123456789", deserialized.tasks[0].recipient)
    }

    @Test
    fun `test legacy backup json without recipientNames deserializes cleanly`() {
        val legacyJson = """
            {
                "version": 2,
                "app": "Helper",
                "tasks": [
                    {
                        "id": 303,
                        "title": "Legacy Task",
                        "message": "Hello",
                        "recipient": "+919999988888",
                        "scheduledTime": 1735689600000,
                        "channel": "SMS",
                        "status": "COMPLETED"
                    }
                ]
            }
        """.trimIndent()

        val deserialized = json.decodeFromString<BackupPayloadDto>(legacyJson)
        assertEquals(1, deserialized.tasks.size)
        assertEquals("Legacy Task", deserialized.tasks[0].title)
        assertEquals("", deserialized.tasks[0].recipientNames)
    }

    @Test
    fun `test accessibility node utils gesture timestamp update`() {
        val before = System.currentTimeMillis()
        AccessibilityNodeUtils.lastGestureTimestamp = before
        assertEquals(before, AccessibilityNodeUtils.lastGestureTimestamp)

        val updated = before + 500L
        AccessibilityNodeUtils.lastGestureTimestamp = updated
        assertEquals(updated, AccessibilityNodeUtils.lastGestureTimestamp)
    }

    @Test
    fun `test accessibility health manager duration overloads`() = runTest {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.checkCallingOrSelfPermission(any()) } returns -1 // PERMISSION_DENIED

        // Duration overload with explicit duration
        val resultDuration = AccessibilityHealthManager.awaitServiceReadiness(mockContext, 10.milliseconds)
        // Service is not running in test JVM environment, so expected to return false without throwing
        assertEquals(false, resultDuration)

        // Legacy Long millisecond overload
        val resultLong = AccessibilityHealthManager.awaitServiceReadiness(mockContext, 10L)
        assertEquals(false, resultLong)

        // Synchronous waitForServiceReadiness Duration overload
        val resultSyncDuration = AccessibilityHealthManager.waitForServiceReadiness(mockContext, 10.milliseconds)
        assertEquals(false, resultSyncDuration)
    }

    @Test
    fun `test helper app initializer returns empty dependencies for on demand startup`() {
        val initializer = com.jmd.helper.core.HelperAppInitializer()
        val dependencies = initializer.dependencies()
        assertNotNull(dependencies)
        assertTrue(dependencies.isEmpty())
    }

    @Test
    fun `test helper application implements workmanager configuration provider`() {
        val app = com.jmd.helper.HelperApplication()
        assertTrue(
            "HelperApplication must implement Configuration.Provider",
            androidx.work.Configuration.Provider::class.java.isAssignableFrom(com.jmd.helper.HelperApplication::class.java)
        )
        val config = app.workManagerConfiguration
        assertNotNull(config)
    }

    @Test
    fun `test semantic success tokens and channel simulation backward compatibility`() {
        assertEquals(com.jmd.helper.data.model.ChannelType.CALL_SIMULATION, com.jmd.helper.data.model.ChannelType.SIMULATED_CALL)
        assertEquals(com.jmd.helper.data.model.ChannelType.CALL_SIMULATION, com.jmd.helper.data.model.ChannelType.fromString("CALL_SIMULATION"))
        assertEquals(com.jmd.helper.data.model.ChannelType.CALL_SIMULATION, com.jmd.helper.data.model.ChannelType.fromString("SIMULATED_CALL"))
    }
}
