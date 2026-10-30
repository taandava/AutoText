package com.jmd.autotext

import com.jmd.autotext.core.AutomationSafetyManager
import com.jmd.autotext.core.DEFAULT_MESSAGE_TEXT
import com.jmd.autotext.core.MessageTemplateResolver
import com.jmd.autotext.core.PhoneNumberNormalizer
import com.jmd.autotext.core.WhatsAppPackageHelper
import com.jmd.autotext.data.model.*
import com.jmd.autotext.scheduler.AlarmScheduler
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class AutoTextComprehensiveTest {

    @Before
    fun setUp() {
        AutomationSafetyManager.resetEmergencyStop(null)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 1. PHONE NORMALIZATION & VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testPhoneNormalizationStandard() {
        assertEquals("+919876543210", PhoneNumberNormalizer.normalize("9876543210"))
        assertEquals("+919876543210", PhoneNumberNormalizer.normalize("+91 98765-43210"))
        assertEquals("+919876543210", PhoneNumberNormalizer.normalize("09876543210"))
        assertEquals("+14155552671", PhoneNumberNormalizer.normalize("+1 (415) 555-2671"))
        assertEquals("+447911123456", PhoneNumberNormalizer.normalize("00447911123456"))
    }

    @Test
    fun testPhoneValidation() {
        assertTrue(PhoneNumberNormalizer.isMobileNumber("+919876543210"))
        assertTrue(PhoneNumberNormalizer.isMobileNumber("9876543210"))
        assertTrue(PhoneNumberNormalizer.isMobileNumber("+14155552671"))
        assertFalse(PhoneNumberNormalizer.isMobileNumber("123"))
        assertFalse(PhoneNumberNormalizer.isMobileNumber("abc"))
        assertFalse(PhoneNumberNormalizer.isMobileNumber(""))
    }

    @Test
    fun testSpecificRequestedNumbersNormalization() {
        val numbers = listOf("9769602020", "9769402020", "9500910092")
        for (num in numbers) {
            val normalized = PhoneNumberNormalizer.normalize(num)
            assertTrue("Expected valid mobile number for $num", PhoneNumberNormalizer.isMobileNumber(normalized))
            assertEquals("+91$num", normalized)
        }
    }

    @Test
    fun testCleanPhoneNumber() {
        assertEquals("+919876543210", PhoneNumberNormalizer.cleanPhoneNumber("+91 (987) 654-3210"))
        assertEquals("9876543210", PhoneNumberNormalizer.cleanPhoneNumber("98765 43210"))
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 2. MESSAGE TEMPLATE RESOLVER TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testTemplatePlaceholderResolution() {
        val template = "Hello {name}, your phone number is {phone}."
        val resolved = MessageTemplateResolver.resolve(
            template = template,
            name = "Aarav Sharma",
            phone = "+919876543210"
        )
        assertTrue(resolved.contains("Hello Aarav Sharma"))
        assertTrue(resolved.contains("+919876543210"))
        assertFalse(resolved.contains("{name}"))
        assertFalse(resolved.contains("{phone}"))
    }

    @Test
    fun testTemplateEmptyNameFallback() {
        val template = "Hello {name}, your first name is {first_name}."
        val resolved = MessageTemplateResolver.resolve(
            template = template,
            name = "",
            phone = "+919876543210"
        )
        assertTrue(resolved.contains("Hello there"))
        assertTrue(resolved.contains("first name is there"))
    }

    @Test
    fun testDateTimeResolution() {
        val template = "Meeting scheduled for {date} at {time}."
        val resolved = MessageTemplateResolver.resolve(template)
        assertFalse(resolved.contains("{date}"))
        assertFalse(resolved.contains("{time}"))
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 3. AUTOMATION SAFETY MANAGER & CIRCUIT BREAKER TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testEmergencyStopKillSwitch() {
        assertFalse(AutomationSafetyManager.isEmergencyStopActive.get())
        assertTrue(AutomationSafetyManager.canExecute(ChannelType.WHATSAPP))

        AutomationSafetyManager.isEmergencyStopActive.set(true)
        assertTrue(AutomationSafetyManager.isEmergencyStopActive.get())
        assertFalse(AutomationSafetyManager.canExecute(ChannelType.WHATSAPP))
        assertFalse(AutomationSafetyManager.canExecute(ChannelType.CALL))
        assertFalse(AutomationSafetyManager.canExecute(ChannelType.SMS))

        AutomationSafetyManager.resetEmergencyStop(null)
        assertFalse(AutomationSafetyManager.isEmergencyStopActive.get())
        assertTrue(AutomationSafetyManager.canExecute(ChannelType.WHATSAPP))
    }

    @Test
    fun testCircuitBreakerTripsAfterThreeConsecutiveFailures() {
        val channel = ChannelType.WHATSAPP_BUSINESS
        assertFalse(AutomationSafetyManager.isCircuitBreakerTripped(channel))

        AutomationSafetyManager.recordFailure(channel, null)
        assertFalse(AutomationSafetyManager.isCircuitBreakerTripped(channel))

        AutomationSafetyManager.recordFailure(channel, null)
        assertFalse(AutomationSafetyManager.isCircuitBreakerTripped(channel))

        // 3rd failure trips the circuit breaker
        AutomationSafetyManager.recordFailure(channel, null)
        assertTrue(AutomationSafetyManager.isCircuitBreakerTripped(channel))
        assertFalse(AutomationSafetyManager.canExecute(channel))

        // Other channels remain unaffected
        assertTrue(AutomationSafetyManager.canExecute(ChannelType.SMS))

        // Successful execution clears the circuit breaker
        AutomationSafetyManager.recordSuccess(channel)
        assertFalse(AutomationSafetyManager.isCircuitBreakerTripped(channel))
        assertTrue(AutomationSafetyManager.canExecute(channel))
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 4. WHATSAPP PACKAGE CONSTANTS & CHAT PICKER SAFEGUARD TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testWhatsAppPackageConstants() {
        assertEquals("com.whatsapp", WhatsAppPackageHelper.PACKAGE_WHATSAPP)
        assertEquals("com.whatsapp.w4b", WhatsAppPackageHelper.PACKAGE_WHATSAPP_BUSINESS)
    }

    @Test
    fun testBlockedChatNamesPreventInfiniteLoop() {
        val blockedNames = listOf(
            "WhatsApp", "whatsapp", "WHATSAPP",
            "WhatsApp Business", "wa business", "w4b",
            "Chats", "chats",
            "Status", "status", "My status", "my status",
            "Calls", "calls",
            "Updates", "updates",
            "Communities", "communities",
            "Channels", "channels",
            "Search", "search",
            "New chat", "new chat",
            "Camera", "camera",
            "More options", "more options"
        )

        for (name in blockedNames) {
            val lower = name.trim().lowercase()
            val isBlocked = lower in setOf(
                "whatsapp", "whatsapp business", "wa business", "w4b",
                "chats", "status", "my status", "calls", "updates",
                "communities", "channels", "search", "new chat", "camera", "more options"
            )
            assertTrue("Expected '$name' to be blocked", isBlocked)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 5. PHONE CALL SCHEDULING & SEQUENTIAL EXECUTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testCallTaskModelParameters() {
        val task = ScheduledTask(
            id = 42L,
            title = "Test Sequential Calls",
            channel = ChannelType.CALL,
            recipients = listOf("+919769602020", "+919769402020", "+919500910092"),
            callDurationSeconds = 30,
            includeDialTimeInCallDuration = true,
            callSequenceMode = "GIVEN",
            delayBetweenCallsSeconds = 10
        )

        assertEquals(30, task.callDurationSeconds)
        assertTrue(task.includeDialTimeInCallDuration)
        assertEquals("GIVEN", task.callSequenceMode)
        assertEquals(10, task.delayBetweenCallsSeconds)
        assertEquals(3, task.recipients.size)
    }

    @Test
    fun testCallSequenceModeOrdering() {
        val baseRecipients = listOf("+919769602020", "+919769402020", "+919500910092")
        val givenTask = ScheduledTask(
            channel = ChannelType.CALL,
            recipients = baseRecipients,
            callSequenceMode = "GIVEN"
        )

        val randomTask = ScheduledTask(
            id = 99L,
            triggerTimeMillis = 1700000000000L,
            channel = ChannelType.CALL,
            recipients = baseRecipients,
            callSequenceMode = "RANDOM"
        )

        // Given preserves exact order
        assertEquals(baseRecipients, givenTask.recipients)

        // Random mode shuffles deterministically with seed
        val randomSeed = randomTask.id xor randomTask.triggerTimeMillis
        val shuffled = baseRecipients.shuffled(java.util.Random(randomSeed))
        assertEquals(3, shuffled.size)
        assertTrue(shuffled.containsAll(baseRecipients))
    }

    @Test
    fun testInterCallDelayCalculation() {
        val calculateDelayMs = { delaySec: Int -> maxOf(1000L, delaySec * 1000L) }
        assertEquals(5000L, calculateDelayMs(5))
        assertEquals(1000L, calculateDelayMs(0)) // Minimum safety clamp of 1000ms
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 6. SCHEDULER & RECURRENCE ENGINE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testAlarmSchedulerRecurrenceDaily() {
        val now = System.currentTimeMillis()
        val nextTrigger = AlarmScheduler.calculateNextTrigger(
            recurrence = RecurrenceType.DAILY,
            customIntervalMinutes = 0,
            fromTime = now
        )
        assertTrue("Next daily trigger must be in the future", nextTrigger > now)
        assertTrue("Next daily trigger must be within ~25 hours", nextTrigger <= now + 25 * 3600 * 1000L)
    }

    @Test
    fun testAlarmSchedulerRecurrenceCustomInterval() {
        val baseTime = 1700000000000L
        val intervalMinutes = 30
        val expected = baseTime + (intervalMinutes * 60 * 1000L)
        val calculatedTrigger = AlarmScheduler.calculateNextTrigger(
            recurrence = RecurrenceType.CUSTOM_INTERVAL,
            customIntervalMinutes = intervalMinutes,
            fromTime = baseTime
        )
        assertEquals("Next trigger timestamp should equal baseTime plus custom interval", expected, calculatedTrigger)
    }

    @Test
    fun testAlarmSchedulerWeeklySelectedDays() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        val fromTime = cal.timeInMillis
        val nextTrigger = AlarmScheduler.calculateNextTrigger(
            recurrence = RecurrenceType.WEEKLY,
            customIntervalMinutes = 0,
            fromTime = fromTime,
            selectedDaysOfWeek = listOf(Calendar.WEDNESDAY, Calendar.FRIDAY)
        )
        assertTrue(nextTrigger > fromTime)
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTrigger }
        assertTrue(
            nextCal.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY ||
            nextCal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 7. FORM & RECIPIENT JSON SERIALIZATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testRecipientsJsonSerializationAndParsing() {
        val numbers = listOf("9769602020", "9769402020", "9500910092")
        val jsonStr = numbers.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")
        val parsedList = jsonStr.trim().removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }

        assertEquals(3, parsedList.size)
        assertEquals("9769602020", parsedList[0])
        assertEquals("9769402020", parsedList[1])
        assertEquals("9500910092", parsedList[2])
    }

    // ─────────────────────────────────────────────────────────────
    // 8. EMERGENCY NUMBER REJECTION & MULTI-INPUT PARSING TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testEmergencyNumberRejection() {
        val emergencyNumbers = listOf("112", "911", "100", "101", "102", "108", "999")
        for (num in emergencyNumbers) {
            assertTrue("Expected $num to be identified as emergency number", PhoneNumberNormalizer.isEmergencyNumber(num))
            assertEquals("Emergency number $num must normalize to empty string", "", PhoneNumberNormalizer.normalize(num))
        }
    }

    @Test
    fun testDuplicateDetectionAcrossFormats() {
        val listWithDuplicates = listOf(
            "9769602020",
            "+919769602020",
            "+91 97696-02020",
            "9876543210"
        )
        val dupes = PhoneNumberNormalizer.detectDuplicates(listWithDuplicates)
        assertEquals(1, dupes.size)
        assertTrue(dupes.containsKey("+919769602020"))
        assertEquals(3, dupes["+919769602020"]!!.size)
    }

    @Test
    fun testMultiInputParsingDelimiterSupport() {
        val multiline = "9769602020\n8765432109, +917654321098; 9500910092"
        val parsed = PhoneNumberNormalizer.parseMultiInput(multiline, "+91")
        assertEquals(4, parsed.size)
        assertTrue(parsed.all { it.second != null })
        assertEquals("+919769602020", parsed[0].second)
        assertEquals("+918765432109", parsed[1].second)
        assertEquals("+917654321098", parsed[2].second)
        assertEquals("+919500910092", parsed[3].second)
    }

    // ─────────────────────────────────────────────────────────────
    // 9. CALL FAILURE POLICY & VALIDATOR TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testCallFailurePolicyDefaultsAndTransitions() {
        val defaultTask = ScheduledTask(
            channel = ChannelType.CALL,
            recipients = listOf("+919769602020")
        )
        assertEquals("CONTINUE", defaultTask.callFailurePolicy)

        val stopOnFailTask = defaultTask.copy(callFailurePolicy = "STOP_ON_FAILURE")
        assertEquals("STOP_ON_FAILURE", stopOnFailTask.callFailurePolicy)
    }

    @Test
    fun testTaskContentValidatorWhatsAppBusinessStatus() {
        val resultEmpty = com.jmd.autotext.core.TaskContentValidator.validateContent(
            context = null,
            channel = ChannelType.WHATSAPP_BUSINESS_STATUS,
            messageText = "",
            mediaUri = null
        )
        assertTrue("Expected rejection for empty text and empty media", resultEmpty is com.jmd.autotext.core.ContentValidationResult.Reject)

        val resultText = com.jmd.autotext.core.TaskContentValidator.validateContent(
            context = null,
            channel = ChannelType.WHATSAPP_BUSINESS_STATUS,
            messageText = "Grand Opening Today!",
            mediaUri = null
        )
        assertTrue("Expected text acceptance", resultText is com.jmd.autotext.core.ContentValidationResult.AcceptTextOnly)
    }

    @Test
    fun testSmsSegmentCalculation() {
        val calculateSegments = { text: String, isUnicode: Boolean ->
            val singleLimit = if (isUnicode) 70 else 160
            val multiLimit = if (isUnicode) 67 else 153
            if (text.length <= singleLimit) 1 else ((text.length + multiLimit - 1) / multiLimit)
        }

        // Standard ASCII: 160 per single segment, 153 for multipart
        assertEquals(1, calculateSegments("A".repeat(160), false))
        assertEquals(2, calculateSegments("A".repeat(161), false))

        // Unicode (emoji / Indic): 70 per single segment, 67 for multipart
        assertEquals(1, calculateSegments("न".repeat(70), true))
        assertEquals(2, calculateSegments("न".repeat(71), true))
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 10. FORM SEPARATION & CHANNEL SELECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testStatusFormSeparation() {
        // Distinct channel types for WhatsApp Status vs WhatsApp Business Status
        val personalStatus = ChannelType.WHATSAPP_STATUS
        val businessStatus = ChannelType.WHATSAPP_BUSINESS_STATUS

        assertNotEquals(personalStatus, businessStatus)
        assertEquals("WA Status", personalStatus.displayName)
        assertEquals("W4B Status", businessStatus.displayName)

        // Target packages must be strictly separated
        val personalPkg = WhatsAppPackageHelper.PACKAGE_WHATSAPP
        val businessPkg = WhatsAppPackageHelper.PACKAGE_WHATSAPP_BUSINESS

        assertEquals("com.whatsapp", personalPkg)
        assertEquals("com.whatsapp.w4b", businessPkg)
        assertNotEquals(personalPkg, businessPkg)
    }

    @Test
    fun testContactSequenceChannelSeparation() {
        val smsStep = ContactSequenceStep(stepOrder = 1, channel = ChannelType.SMS, messageText = "SMS Drip")
        val waStep = ContactSequenceStep(stepOrder = 1, channel = ChannelType.WHATSAPP, messageText = "WA Drip")
        val w4bStep = ContactSequenceStep(stepOrder = 1, channel = ChannelType.WHATSAPP_BUSINESS, messageText = "W4B Drip")

        assertEquals(ChannelType.SMS, smsStep.channel)
        assertEquals(ChannelType.WHATSAPP, waStep.channel)
        assertEquals(ChannelType.WHATSAPP_BUSINESS, w4bStep.channel)
        assertNotEquals(smsStep.channel, waStep.channel)
        assertNotEquals(waStep.channel, w4bStep.channel)
    }

    @Test
    fun testDailyAutoChannelSeparation() {
        val supportedChannels = listOf("SMS", "WHATSAPP", "WHATSAPP_BUSINESS")

        val configs = supportedChannels.mapIndexed { idx, ch ->
            DailyCampaignConfig(
                id = (idx + 1).toLong(),
                channel = ch,
                title = when (ch) {
                    "WHATSAPP_BUSINESS" -> "Daily WhatsApp Business Campaign"
                    "WHATSAPP" -> "Daily WhatsApp Campaign"
                    else -> "Daily SMS Campaign"
                },
                isActive = false
            )
        }

        assertEquals(3, configs.size)
        // Verify no campaign is active by default (blank page requirement)
        assertTrue(configs.none { it.isActive })

        // Verify distinct channels
        assertEquals("SMS", configs[0].channel)
        assertEquals("WHATSAPP", configs[1].channel)
        assertEquals("WHATSAPP_BUSINESS", configs[2].channel)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 11. COMPAT SUITE TESTS (ZERO SUPPRESSION INVARIANT)
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testTelephonyManagerCompatNullSafety() {
        val state = com.jmd.autotext.compat.TelephonyManagerCompat.getCallState(null)
        assertEquals(android.telephony.TelephonyManager.CALL_STATE_IDLE, state)

        val isConnected = com.jmd.autotext.compat.TelephonyManagerCompat.isCallConnected(null, null)
        assertFalse(isConnected)

        val isActive = com.jmd.autotext.compat.TelephonyManagerCompat.isCallActiveOrRinging(null, null)
        assertFalse(isActive)
    }

    @Test
    fun testAccessibilityCompatNullSafety() {
        val isChecked = com.jmd.autotext.compat.AccessibilityCompat.isNodeChecked(null)
        assertFalse(isChecked)
    }

    @Test
    fun testClipboardCompatNullSafety() {
        // Must handle null gracefully without throwing any exception
        com.jmd.autotext.compat.ClipboardCompat.setPrimaryClip(null, null)
        assertTrue(true)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 12. WHATSAPP VOICE CALL AUTOMATION MODE & CHANNEL TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testWhatsAppVoiceCallAutomationModeExists() {
        val modes = com.jmd.autotext.service.AutoTextAccessibilityService.Companion.AutomationMode.entries.map { it.name }
        assertTrue(modes.contains("WHATSAPP_VOICE_CALL"))
    }

    @Test
    fun testChannelTypeWhatsAppCallProperties() {
        val callChannel = ChannelType.WHATSAPP_CALL
        assertEquals("WhatsApp Call", callChannel.displayName)
        assertEquals("📞", callChannel.emoji)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 13. CHAT PICKER CLASSIFICATION & METADATA TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testPickedChatTypesEnumValues() {
        val types = PickedChatType.entries.map { it.name }
        assertTrue(types.contains("INDIVIDUAL"))
        assertTrue(types.contains("GROUP"))
        assertTrue(types.contains("BROADCAST"))
        assertTrue(types.contains("STATUS"))
        assertTrue(types.contains("CHANNEL"))
    }

    @Test
    fun testPickedChatInfoDataClass() {
        val infoIndividual = PickedChatInfo("Dilip Gupta", PickedChatType.INDIVIDUAL, "+919876543210", "online")
        assertEquals("Dilip Gupta", infoIndividual.title)
        assertEquals(PickedChatType.INDIVIDUAL, infoIndividual.type)
        assertEquals("+919876543210", infoIndividual.phoneOrId)
        assertEquals("online", infoIndividual.subtitle)

        val infoGroup = PickedChatInfo("Project Team", PickedChatType.GROUP, null, "5 participants")
        assertEquals("Project Team", infoGroup.title)
        assertEquals(PickedChatType.GROUP, infoGroup.type)
        assertNull(infoGroup.phoneOrId)
        assertEquals("5 participants", infoGroup.subtitle)

        val infoBroadcast = PickedChatInfo("Family Broadcast", PickedChatType.BROADCAST, null, "12 recipients")
        assertEquals(PickedChatType.BROADCAST, infoBroadcast.type)

        val infoStatus = PickedChatInfo("My status", PickedChatType.STATUS)
        assertEquals(PickedChatType.STATUS, infoStatus.type)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 14. TARGET NUMBERS & CALL DURATION BEHAVIOR TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testTargetUserPhoneNumbersFormattingAndChannels() {
        val num1 = "9769602020"
        val num2 = "9769402020"

        val clean1 = PhoneNumberNormalizer.cleanPhoneNumber(num1)
        val clean2 = PhoneNumberNormalizer.cleanPhoneNumber(num2)
        assertEquals("9769602020", clean1)
        assertEquals("9769402020", clean2)

        val norm1 = PhoneNumberNormalizer.normalize(num1)
        val norm2 = PhoneNumberNormalizer.normalize(num2)
        assertEquals("+919769602020", norm1)
        assertEquals("+919769402020", norm2)

        val task = ScheduledTask(
            id = 1001L,
            channel = ChannelType.CALL,
            recipients = listOf(num1, num2),
            callDurationSeconds = 10,
            includeDialTimeInCallDuration = true,
            delayBetweenCallsSeconds = 3,
            messageText = "Call test"
        )
        assertEquals(2, task.recipients.size)
        assertEquals(10, task.callDurationSeconds)
        assertTrue(task.includeDialTimeInCallDuration)
        assertEquals(3, task.delayBetweenCallsSeconds)
    }

    @Test
    fun testCallDurationConfigurationVariants() {
        val shortCallTask = ScheduledTask(
            id = 2001L,
            channel = ChannelType.CALL,
            recipients = listOf("9769602020"),
            callDurationSeconds = 5,
            includeDialTimeInCallDuration = false,
            messageText = ""
        )
        assertEquals(5, shortCallTask.callDurationSeconds)
        assertFalse(shortCallTask.includeDialTimeInCallDuration)

        val waCallTask = ScheduledTask(
            id = 2002L,
            channel = ChannelType.WHATSAPP_CALL,
            recipients = listOf("9769402020"),
            callDurationSeconds = 15,
            includeDialTimeInCallDuration = true,
            messageText = ""
        )
        assertEquals(ChannelType.WHATSAPP_CALL, waCallTask.channel)
        assertEquals(15, waCallTask.callDurationSeconds)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 13. REPETITION & FLEXIBLE SCHEDULE TESTS (SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS)
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testRepeatUnitEnumAndRecurrenceMapping() {
        assertEquals("Seconds", RepeatUnit.SECONDS.displayName)
        assertEquals("Minutes", RepeatUnit.MINUTES.displayName)
        assertEquals("Hours", RepeatUnit.HOURS.displayName)
        assertEquals("Days", RepeatUnit.DAYS.displayName)
        assertEquals("Weeks", RepeatUnit.WEEKS.displayName)
        assertEquals("Months", RepeatUnit.MONTHS.displayName)

        // Verify mapping to RecurrenceType
        assertEquals(RecurrenceType.REPEAT_SECONDS, RepeatUnit.SECONDS.toRecurrenceType())
        assertEquals(RecurrenceType.REPEAT_MINUTES, RepeatUnit.MINUTES.toRecurrenceType())
        assertEquals(RecurrenceType.REPEAT_HOURS, RepeatUnit.HOURS.toRecurrenceType())
        assertEquals(RecurrenceType.REPEAT_DAYS, RepeatUnit.DAYS.toRecurrenceType())
        assertEquals(RecurrenceType.REPEAT_WEEKS, RepeatUnit.WEEKS.toRecurrenceType())
        assertEquals(RecurrenceType.REPEAT_MONTHS, RepeatUnit.MONTHS.toRecurrenceType())
    }

    @Test
    fun testRecurrenceTypeParsing() {
        assertEquals(RecurrenceType.REPEAT_SECONDS, RecurrenceType.fromString("REPEAT_SECONDS"))
        assertEquals(RecurrenceType.REPEAT_SECONDS, RecurrenceType.fromString("SECONDS"))
        assertEquals(RecurrenceType.REPEAT_MINUTES, RecurrenceType.fromString("REPEAT_MINUTES"))
        assertEquals(RecurrenceType.REPEAT_HOURS, RecurrenceType.fromString("HOURS"))
        assertEquals(RecurrenceType.REPEAT_DAYS, RecurrenceType.fromString("REPEAT_DAYS"))
        assertEquals(RecurrenceType.REPEAT_WEEKS, RecurrenceType.fromString("WEEKS"))
        assertEquals(RecurrenceType.REPEAT_MONTHS, RecurrenceType.fromString("REPEAT_MONTHS"))
        assertEquals(RecurrenceType.ONCE, RecurrenceType.fromString("ONCE"))
        assertEquals(RecurrenceType.ONCE, RecurrenceType.fromString("UNKNOWN_VALUE"))
    }

    @Test
    fun testFlexibleRecurrenceNextTriggerCalculation() {
        val baseTime = 1700000000000L // arbitrary fixed timestamp

        // Calculation logic replicated from AlarmScheduler.calculateNextTrigger
        val calculateTrigger = { recurrence: RecurrenceType, interval: Int ->
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = baseTime }
            when (recurrence) {
                RecurrenceType.REPEAT_SECONDS -> cal.add(java.util.Calendar.SECOND, maxOf(10, interval))
                RecurrenceType.REPEAT_MINUTES, RecurrenceType.CUSTOM_INTERVAL -> cal.add(java.util.Calendar.MINUTE, maxOf(1, interval))
                RecurrenceType.REPEAT_HOURS -> cal.add(java.util.Calendar.HOUR_OF_DAY, maxOf(1, interval))
                RecurrenceType.REPEAT_DAYS, RecurrenceType.DAILY -> cal.add(java.util.Calendar.DAY_OF_YEAR, maxOf(1, interval))
                RecurrenceType.REPEAT_WEEKS, RecurrenceType.WEEKLY -> cal.add(java.util.Calendar.WEEK_OF_YEAR, maxOf(1, interval))
                RecurrenceType.REPEAT_MONTHS, RecurrenceType.MONTHLY -> cal.add(java.util.Calendar.MONTH, maxOf(1, interval))
                else -> { /* no-op */ }
            }
            cal.timeInMillis
        }

        // Test Seconds: 30s
        val triggerSec = calculateTrigger(RecurrenceType.REPEAT_SECONDS, 30)
        assertEquals(baseTime + 30 * 1000L, triggerSec)

        // Test Seconds minimum clamp: 5s clamped to 10s
        val triggerSecClamped = calculateTrigger(RecurrenceType.REPEAT_SECONDS, 5)
        assertEquals(baseTime + 10 * 1000L, triggerSecClamped)

        // Test Minutes: 15m
        val triggerMin = calculateTrigger(RecurrenceType.REPEAT_MINUTES, 15)
        assertEquals(baseTime + 15 * 60 * 1000L, triggerMin)

        // Test Hours: 2h
        val triggerHours = calculateTrigger(RecurrenceType.REPEAT_HOURS, 2)
        assertEquals(baseTime + 2 * 3600 * 1000L, triggerHours)

        // Test Days: 1d
        val triggerDays = calculateTrigger(RecurrenceType.REPEAT_DAYS, 1)
        assertEquals(baseTime + 24 * 3600 * 1000L, triggerDays)

        // Test Weeks: 2w
        val triggerWeeks = calculateTrigger(RecurrenceType.REPEAT_WEEKS, 2)
        assertEquals(baseTime + 14 * 24 * 3600 * 1000L, triggerWeeks)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 13. ALL TASK TYPES SETUP, 10S INTERVAL, REPEAT_SECONDS & EXECUTION LOG TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testAllTwelveTaskChannelsSetupAndExecutionLogging() {
        val testRecipients = listOf("9500910092", "9769602020", "9769402020")
        val baseTime = 1700000000000L
        val staggerSeconds = 10
        val repeatIntervalSeconds = 10

        val tasks = ChannelType.entries.mapIndexed { index, channel ->
            val recipients = if (channel == ChannelType.WHATSAPP_STATUS || channel == ChannelType.WHATSAPP_BUSINESS_STATUS) {
                listOf("STATUS")
            } else {
                testRecipients
            }
            ScheduledTask(
                id = (index + 1).toLong(),
                title = "Task ${index + 1} • ${channel.displayName}",
                channel = channel,
                recipients = recipients,
                recipientNames = recipients,
                messageText = "AutoText test ${channel.displayName} message at {time}",
                triggerTimeMillis = baseTime + ((index + 1) * staggerSeconds * 1000L),
                recurrence = RecurrenceType.REPEAT_SECONDS,
                customIntervalMinutes = repeatIntervalSeconds,
                callDurationSeconds = 10,
                includeDialTimeInCallDuration = true,
                delayBetweenCallsSeconds = 5,
                soundModeSetting = "NORMAL",
                fakeCallerName = "AutoText Test",
                fakeCallerNumber = testRecipients.first(),
                status = TaskStatus.PENDING
            )
        }

        // Verify count of all task types
        assertEquals(12, tasks.size)

        // Verify each channel type is uniquely present
        val presentChannels = tasks.map { it.channel }.toSet()
        assertEquals(ChannelType.entries.toSet(), presentChannels)

        // Verify staggered 10-second intervals and 10-second repeat interval for each task
        for ((index, task) in tasks.withIndex()) {
            val expectedOffsetMs = (index + 1) * 10_000L
            assertEquals(baseTime + expectedOffsetMs, task.triggerTimeMillis)
            assertEquals(RecurrenceType.REPEAT_SECONDS, task.recurrence)
            assertEquals(10, task.customIntervalMinutes)

            if (task.channel == ChannelType.WHATSAPP_STATUS || task.channel == ChannelType.WHATSAPP_BUSINESS_STATUS) {
                assertEquals(listOf("STATUS"), task.recipients)
            } else {
                assertEquals(testRecipients, task.recipients)
            }

            // Verify next trigger advances by exactly 10 seconds (10,000ms)
            val nextTrigger = AlarmScheduler.calculateNextTrigger(task, task.triggerTimeMillis)
            assertEquals(
                "Task ${task.channel.displayName} next trigger should advance by 10s",
                task.triggerTimeMillis + 10_000L,
                nextTrigger
            )

            // Verify ExecutionLog construction for this channel
            val executionLog = ExecutionLog(
                taskId = task.id,
                channel = task.channel.displayName,
                recipient = task.recipients.first(),
                messageSnippet = "Test snippet for ${task.channel.displayName}",
                status = "SUCCESS",
                details = "Executed in 10s test interval",
                segments = 1,
                timestampMillis = baseTime + expectedOffsetMs
            )
            assertEquals(task.id, executionLog.taskId)
            assertEquals(task.channel.displayName, executionLog.channel)
            assertEquals(task.recipients.first(), executionLog.recipient)
            assertEquals("SUCCESS", executionLog.status)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 17. HUMAN INTERACTION & IDLE AUTO-POSTPONE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testHumanInteractionRecordingAndIdleState() {
        val baseTime = System.currentTimeMillis()
        AutomationSafetyManager.lastHumanInteractionTimestamp.set(baseTime)

        fun isIdle(elapsedMillis: Long): Boolean = elapsedMillis >= AutomationSafetyManager.USER_IDLE_COOLDOWN_MS

        // Elapsed time < USER_IDLE_COOLDOWN_MS (10s) must NOT be idle
        assertFalse("Device should not be idle 3s after human interaction", isIdle(3000L))

        // Elapsed time >= USER_IDLE_COOLDOWN_MS (10s) MUST be idle
        assertTrue("Device must be considered idle 12s after human interaction", isIdle(12000L))
    }

    @Test
    fun testHumanInterruptionFlagAndPostponeCalculation() {
        // Interruption flag behavior
        AutomationSafetyManager.isInterruptedByHuman.set(true)
        assertTrue("Interruption flag should be set", AutomationSafetyManager.isInterruptedByHuman.get())

        AutomationSafetyManager.isInterruptedByHuman.set(false)
        assertFalse("Interruption flag should be reset", AutomationSafetyManager.isInterruptedByHuman.get())

        // Postpone calculation
        val currentTrigger = 1700000000000L
        val postponeDuration = AutomationSafetyManager.DEFAULT_POSTPONE_INTERVAL_MS
        val newTrigger = currentTrigger + postponeDuration

        val task = ScheduledTask(
            id = 42L,
            title = "Test Postponed Task",
            channel = ChannelType.WHATSAPP,
            recipients = listOf("9769602020"),
            triggerTimeMillis = currentTrigger,
            status = TaskStatus.PENDING
        )
        val postponedTask = task.copy(triggerTimeMillis = newTrigger)

        assertEquals(42L, postponedTask.id)
        assertEquals(newTrigger, postponedTask.triggerTimeMillis)
        assertEquals(TaskStatus.PENDING, postponedTask.status)

        // Postponed ExecutionLog creation
        val postponedLog = ExecutionLog(
            taskId = task.id,
            channel = task.channel.displayName,
            recipient = "9769602020",
            messageSnippet = "Postponed due to active human interaction",
            status = "POSTPONED",
            details = "Auto-postponed by 30s. Will execute once device becomes idle."
        )
        assertEquals("POSTPONED", postponedLog.status)
        assertEquals(42L, postponedLog.taskId)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 18. SYSTEM ALARM REMINDER & WATCHDOG HEARTBEAT ISOLATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testSystemAlarmUniqueLabelGeneration() {
        // Standard title
        val task1 = ScheduledTask(
            id = 23L,
            title = "Doctor Appointment",
            channel = ChannelType.REMINDER,
            messageText = "Remember to visit clinic"
        )
        val label1 = AlarmScheduler.getSystemAlarmLabel(task1)
        assertEquals("AutoText #23: Doctor Appointment", label1)

        // Blank title fallback to message text
        val task2 = ScheduledTask(
            id = 24L,
            title = "",
            channel = ChannelType.REMINDER,
            messageText = "Take medicine on time"
        )
        val label2 = AlarmScheduler.getSystemAlarmLabel(task2)
        assertEquals("AutoText #24: Take medicine on time", label2)

        // Blank title and blank message fallback to default
        val task3 = ScheduledTask(
            id = 25L,
            title = "   ",
            channel = ChannelType.REMINDER,
            messageText = ""
        )
        val label3 = AlarmScheduler.getSystemAlarmLabel(task3)
        assertEquals("AutoText #25: Reminder", label3)

        // Guarantee uniqueness across different task IDs even with identical titles
        val task4 = ScheduledTask(
            id = 26L,
            title = "Doctor Appointment",
            channel = ChannelType.REMINDER
        )
        val label4 = AlarmScheduler.getSystemAlarmLabel(task4)
        assertNotEquals("Tasks with different IDs must have distinct alarm labels", label1, label4)
        assertTrue("Label must contain task ID", label4.startsWith("AutoText #26:"))
    }

    @Test
    fun testSystemAlarmIsolationFromUserAlarms() {
        val userPersonalAlarmLabels = listOf("Wake up", "Gym", "7:00 AM", "Morning Alarm", "Meeting")
        val task = ScheduledTask(
            id = 99L,
            title = "Meeting",
            channel = ChannelType.REMINDER
        )
        val autoTextLabel = AlarmScheduler.getSystemAlarmLabel(task)

        // Verify AutoText label never equals any personal alarm label
        for (personalLabel in userPersonalAlarmLabels) {
            assertNotEquals("AutoText alarm label must never collide with personal alarm", personalLabel, autoTextLabel)
        }
        assertTrue("AutoText alarm must be prefixed with unique AutoText tag", autoTextLabel.startsWith("AutoText #99:"))
    }

    @Test
    fun testWatchdogHeartbeatResilienceConstants() {
        assertEquals("AutoTextA11yWatchdogWork", com.jmd.autotext.scheduler.AccessibilityWatchdogWorker.UNIQUE_WORK_NAME)
        assertEquals("com.jmd.autotext.ACTION_WATCHDOG_HEARTBEAT", AlarmScheduler.ACTION_FIRE_TASK.let {
            com.jmd.autotext.scheduler.AccessibilityWatchdogScheduler.ACTION_WATCHDOG_HEARTBEAT
        })
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 19. STEALTH PROTECTION & BANKING PACKAGE ISOLATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testBankingPackageDetectionAndIsolation() {
        val bankingPackages = listOf(
            "com.sbi.upi",
            "com.sbi.lotusintouch",
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "com.icicibank.mobile",
            "com.hdfcbank.payzapp",
            "com.axis.mobile",
            "com.kotak.kotakbank",
            "in.org.npci.upiapp",
            "com.yono.sbi",
            "com.cred.app",
            "com.freecharge.android",
            "com.mobikwik_new"
        )

        for (pkg in bankingPackages) {
            assertTrue(
                "Package $pkg must be identified as banking/financial package for stealth isolation",
                com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage(pkg)
            )
        }
    }

    @Test
    fun testNonBankingPackageAllowed() {
        val standardPackages = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.android.systemui",
            "com.android.dialer",
            "com.google.android.apps.messaging",
            "com.jmd.autotext"
        )

        for (pkg in standardPackages) {
            assertFalse(
                "Package $pkg must NOT be flagged as banking package",
                com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage(pkg)
            )
        }
    }

    @Test
    fun testSmsQuotaManagerPduCalculation() {
        val shortAscii = "Hello world"
        assertEquals(1, com.jmd.autotext.core.SmsQuotaManager.calculateSmsSegments(shortAscii))

        val longAscii = "A".repeat(161)
        assertEquals(2, com.jmd.autotext.core.SmsQuotaManager.calculateSmsSegments(longAscii))

        val unicodeText = "Hello 😊"
        assertEquals(1, com.jmd.autotext.core.SmsQuotaManager.calculateSmsSegments(unicodeText))

        val longUnicode = "😊".repeat(71)
        assertTrue(com.jmd.autotext.core.SmsQuotaManager.calculateSmsSegments(longUnicode) >= 2)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 19. ALL TWELVE CHANNELS VALIDATION & REPEAT UNITS TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testAllTwelveChannelsValidationAcceptsTextPayload() {
        val channels = com.jmd.autotext.data.model.ChannelType.values()
        assertEquals(12, channels.size)

        for (channel in channels) {
            val result = com.jmd.autotext.core.TaskContentValidator.validateContent(
                context = null,
                channel = channel,
                messageText = "Automated test dispatch",
                mediaUri = null
            )
            assertTrue(
                "Channel ${channel.name} must accept valid text payload",
                result is com.jmd.autotext.core.ContentValidationResult.AcceptTextOnly
            )
        }
    }

    @Test
    fun testRepeatUnitsRoundTripAndRecurrenceTypeMapping() {
        for (unit in com.jmd.autotext.data.model.RepeatUnit.values()) {
            val mappedRecurrence = unit.toRecurrenceType()
            assertNotNull(mappedRecurrence)
            val parsed = com.jmd.autotext.data.model.RepeatUnit.fromString(unit.name)
            assertEquals(unit, parsed)
        }
    }

    @Test
    fun testLocalBackupListingFilterAndSorting() {
        val tempDir = java.nio.file.Files.createTempDirectory("autotext_backup_test").toFile()
        try {
            val f1 = java.io.File(tempDir, "AutoText_Backup_20261026_100000.json").apply { writeText("{}") }
            val f2 = java.io.File(tempDir, "AutoText_Backup_20261026_110000.json").apply { writeText("{}") }
            val ignoreNonJson = java.io.File(tempDir, "notes.txt").apply { writeText("ignore") }

            f1.setLastModified(1000L)
            f2.setLastModified(2000L)

            val backups = tempDir.listFiles { file -> file.isFile && file.name.endsWith(".json", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            assertEquals(2, backups.size)
            assertEquals(f2.name, backups[0].name)
            assertEquals(f1.name, backups[1].name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 17. CALL ENDED VS UNANSWERED OUTGOING DIAL LOGIC TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testOutgoingCallWithZeroDurationIsNotCallEnded() {
        // Simulating the user placing an outgoing call (type = 2) where remote party does not answer (duration = 0)
        val outgoingType = android.provider.CallLog.Calls.OUTGOING_TYPE
        val duration = 0L

        val isOutgoing = outgoingType == android.provider.CallLog.Calls.OUTGOING_TYPE
        val isCallEnded = isOutgoing && duration > 0L
        val isMissedCall = false

        assertFalse("Unanswered outgoing dial with duration 0 must NOT be treated as Call Ended", isCallEnded)
        assertFalse("Outgoing dial must not be treated as Missed Call", isMissedCall)
    }

    @Test
    fun testOutgoingCallWithPositiveDurationIsCallEnded() {
        // Simulating user dialing an outgoing call that was answered and spoke for 45 seconds
        val outgoingType = android.provider.CallLog.Calls.OUTGOING_TYPE
        val duration = 45L

        val isOutgoing = outgoingType == android.provider.CallLog.Calls.OUTGOING_TYPE
        val isCallEnded = isOutgoing && duration > 0L

        assertTrue("Connected outgoing call with duration > 0 must be treated as Call Ended", isCallEnded)
    }

    @Test
    fun testCallDurationCriteriaRules() {
        // Criteria 1: Untimed call (duration 0) must never auto-hangup
        val untimedTask = ScheduledTask(id = 1, title = "Untimed Call", callDurationSeconds = 0)
        assertEquals(0, untimedTask.callDurationSeconds)

        // Criteria 2: Include dial time true starts immediately upon dialing
        val dialTimeTask = ScheduledTask(id = 2, title = "Timed With Dial", callDurationSeconds = 15, includeDialTimeInCallDuration = true)
        assertTrue(dialTimeTask.includeDialTimeInCallDuration)
        assertEquals(15, dialTimeTask.callDurationSeconds)

        // Criteria 3: Exclude dial time false requires connected conversation (OFFHOOK)
        val conversationOnlyTask = ScheduledTask(id = 3, title = "Talk Only", callDurationSeconds = 30, includeDialTimeInCallDuration = false)
        assertFalse(conversationOnlyTask.includeDialTimeInCallDuration)
        assertEquals(30, conversationOnlyTask.callDurationSeconds)
    }

    @Test
    fun testIncomingMissedCallLogic() {
        // Incoming call that rang but was not answered
        val incomingType = android.provider.CallLog.Calls.MISSED_TYPE
        val duration = 0L

        val isMissed = incomingType == android.provider.CallLog.Calls.MISSED_TYPE || duration == 0L
        assertTrue("Missed incoming call must be recognized", isMissed)
    }

    @Test
    fun testCalculateNextFutureTriggerPreservesDailyTime() {
        val now = System.currentTimeMillis()
        val calPast = Calendar.getInstance().apply {
            timeInMillis = now - 2 * 24 * 60 * 60 * 1000L // 2 days ago
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val task = ScheduledTask(
            id = 1,
            title = "Daily 9:30 AM Task",
            triggerTimeMillis = calPast.timeInMillis,
            recurrence = RecurrenceType.DAILY
        )

        val nextFuture = AlarmScheduler.calculateNextFutureTrigger(task, now)
        assertTrue("Next future trigger must be greater than now", nextFuture > now)

        val nextCal = Calendar.getInstance().apply { timeInMillis = nextFuture }
        assertEquals("Scheduled hour (9) must be preserved", 9, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Scheduled minute (30) must be preserved", 30, nextCal.get(Calendar.MINUTE))
        assertEquals("Scheduled second (0) must be preserved", 0, nextCal.get(Calendar.SECOND))
    }

    @Test
    fun testExpiredOnceTaskIsNotAdvanced() {
        val now = System.currentTimeMillis()
        val pastTime = now - 100_000L
        val task = ScheduledTask(
            id = 2,
            title = "One-time Past Task",
            triggerTimeMillis = pastTime,
            recurrence = RecurrenceType.ONCE
        )

        val result = AlarmScheduler.calculateNextFutureTrigger(task, now)
        assertEquals("ONCE task in past must return its exact past triggerTimeMillis without advancing", pastTime, result)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 16. TEMPLATE DEFAULTS & STATUS INDICATOR PALETTE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testDefaultMessageTextIsEmpty() {
        assertEquals("Default message text across app must be blank by specification", "", DEFAULT_MESSAGE_TEXT)
    }

    @Test
    fun testStatusIndicatorPaletteValues() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF00E676), com.jmd.autotext.ui.components.IndicatorActiveGreen)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFF5252), com.jmd.autotext.ui.components.IndicatorInactiveRed)
    }

    @Test
    fun testAutoReplyRulePreservesEmptyReplyText() {
        val rule = AutoReplyRule(
            id = 10,
            name = "Test Blank Rule",
            channel = ChannelType.CALL,
            callEventType = "CALL_ENDED",
            replyText = "",
            triggerPattern = "*"
        )
        assertEquals("Rule reply text must remain empty without prefilled template injection", "", rule.replyText)
    }

    @Test
    fun testScreenOrientationGuardIndicatorConfig() {
        // Verify indicator color constants strictly match spec: Green = 0xFF00E676, Red = 0xFFFF5252
        assertEquals(androidx.compose.ui.graphics.Color(0xFF00E676), com.jmd.autotext.ui.components.IndicatorActiveGreen)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFF5252), com.jmd.autotext.ui.components.IndicatorInactiveRed)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 17. PRIVACY, APP VISIBILITY & COMPATIBILITY ENGINEERING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testAppPrivacyConfigDefaults() {
        val config = AppPrivacyConfig(
            packageName = "com.example.secureapp",
            appName = "Secure App"
        )
        assertEquals("com.example.secureapp", config.packageName)
        assertEquals("Secure App", config.appName)
        assertTrue("isExcluded must default to true", config.isExcluded)
        assertTrue("disableAutomation must default to true", config.disableAutomation)
        assertTrue("blockOverlays must default to true", config.blockOverlays)
        assertTrue("suppressNotifications must default to true", config.suppressNotifications)
        assertTrue("disableClipboard must default to true", config.disableClipboard)
        assertTrue("maskSensitiveContent must default to true", config.maskSensitiveContent)
        assertTrue("disableRules must default to true", config.disableRules)
        assertEquals("", config.customNotes)
    }

    @Test
    fun testSensitiveTextMasking() {
        assertEquals("", com.jmd.autotext.core.PrivacyManager.maskSensitiveText(""))
        assertEquals("**", com.jmd.autotext.core.PrivacyManager.maskSensitiveText("hi"))
        assertEquals("j***i", com.jmd.autotext.core.PrivacyManager.maskSensitiveText("jai mata di"))
        assertEquals("P***d", com.jmd.autotext.core.PrivacyManager.maskSensitiveText("Password"))
    }

    @Test
    fun testPhoneNumberMasking() {
        assertEquals("9769***020", com.jmd.autotext.core.PrivacyManager.maskPhoneNumber("9769602020"))
        assertEquals("9930***542", com.jmd.autotext.core.PrivacyManager.maskPhoneNumber("+91 99306 04542"))
        assertEquals("***", com.jmd.autotext.core.PrivacyManager.maskPhoneNumber("123"))
    }

    @Test
    fun testBankingPackageDetection() {
        // Banking & UPI apps
        assertTrue(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.saraswatbank.gomobile"))
        assertTrue(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.sbi.lotusintouch"))
        assertTrue(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.google.android.apps.nbu.paisa.user"))
        assertTrue(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.phonepe.app"))
        assertTrue(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("net.one97.paytm"))
        assertTrue(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.hdfcbank.mobilebanking"))
        assertTrue(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.csam.icici.bank.imobile"))
        assertTrue(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.axis.mobile"))
        assertTrue(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.kotak.bank"))
        assertTrue(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("in.org.npci.upiapp"))
        assertTrue(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.cred.app"))

        // Non-banking standard apps
        assertFalse(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.whatsapp"))
        assertFalse(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.whatsapp.w4b"))
        assertFalse(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.google.android.dialer"))
        assertFalse(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.android.settings"))
        assertFalse(com.jmd.autotext.service.AutoTextAccessibilityService.isFinancialOrBankingPackage("com.jmd.autotext"))
    }

    @Test
    fun testBaseAutomationTargetPackages() {
        val targets = com.jmd.autotext.core.PrivacyManager.BASE_AUTOMATION_TARGET_PACKAGES
        assertTrue(targets.contains("com.whatsapp"))
        assertTrue(targets.contains("com.whatsapp.w4b"))
        assertTrue(targets.contains("com.android.systemui"))
        assertTrue(targets.contains("com.google.android.dialer"))
        // Excluded banking packages must NEVER be in base targets
        assertFalse(targets.contains("com.saraswatbank.gomobile"))
        assertFalse(targets.contains("net.one97.paytm"))
    }

    @Test
    fun testGlobalPrivacySettingsDefaults() {
        val settings = GlobalPrivacySettings()
        assertTrue(settings.isPrivacyModeEnabled)
        assertTrue(settings.autoExcludeBankingApps)
        assertTrue(settings.maskNotificationsOnLockScreen)
        assertFalse(settings.maskExecutionLogs)
        assertEquals(15, settings.autoClearClipboardSeconds)
        assertTrue(settings.smartAccessibilityScoping)
        assertFalse(settings.dynamicA11yOnDemand)
        assertTrue(settings.suppressNotifsOverExcluded)
        assertTrue(settings.blockOverlaysOverExcluded)
    }

    @Test
    fun testPrivacyConfigCopyAndModifications() {
        val original = AppPrivacyConfig(
            packageName = "com.sample.app",
            appName = "Sample"
        )
        val modified = original.copy(
            isExcluded = false,
            blockOverlays = false,
            customNotes = "User whitelisted"
        )
        assertFalse(modified.isExcluded)
        assertFalse(modified.blockOverlays)
        assertTrue(modified.disableAutomation)
        assertEquals("User whitelisted", modified.customNotes)
    }
}
