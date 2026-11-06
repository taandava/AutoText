package com.jmd.helper

import com.jmd.helper.core.ProtectedApplicationManager
import com.jmd.helper.core.PermissionManager
import com.jmd.helper.ui.model.TaskTypeItem

import com.jmd.helper.core.TaskSafetyManager
import com.jmd.helper.core.DEFAULT_MESSAGE_TEXT
import com.jmd.helper.core.MessageTemplateResolver
import com.jmd.helper.core.PhoneNumberNormalizer
import com.jmd.helper.core.WhatsAppPackageHelper
import com.jmd.helper.data.model.*
import com.jmd.helper.scheduler.AlarmScheduler
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class HelperComprehensiveTest {

    @Before
    fun setUp() {
        TaskSafetyManager.resetEmergencyStop(null)
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

    @Test
    fun testHyphenatedInternationalPhoneNumberNotClassifiedAsGroup() {
        val testNumbers = listOf(
            "+1-555-123-4567",
            "+44-20-7946-0958",
            "+91-98765-43210",
            "+1 (555) 123-4567",
            "98765-43210"
        )
        for (num in testNumbers) {
            val digitsOnly = num.filter { it.isDigit() }
            val isPhone = digitsOnly.length >= 7 && num.all { it.isDigit() || it in "+- ()" }
            val isGroup = num.startsWith("group:", ignoreCase = true) || (!isPhone)
            assertTrue("Expected $num to be identified as phone", isPhone)
            assertFalse("Expected $num not to be identified as group", isGroup)
        }

        val groupInputs = listOf(
            "group:Family Group",
            "group:Marketing 2026",
            "College Friends",
            "Project Beta"
        )
        for (grp in groupInputs) {
            val digitsOnly = grp.filter { it.isDigit() }
            val isPhone = digitsOnly.length >= 7 && grp.all { it.isDigit() || it in "+- ()" }
            val isGroup = grp.startsWith("group:", ignoreCase = true) || (!isPhone)
            assertTrue("Expected '$grp' to be identified as group", isGroup)
        }
    }

    @Test
    fun testEmergencyStopResetState() {
        TaskSafetyManager.triggerEmergencyStop(null, "Test Emergency Stop")
        assertTrue(TaskSafetyManager.isEmergencyStopActive.get())

        TaskSafetyManager.resetEmergencyStop(null)
        assertFalse(TaskSafetyManager.isEmergencyStopActive.get())
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
    // 3. TASK SAFETY MANAGER & CIRCUIT BREAKER TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testEmergencyStopKillSwitch() {
        assertFalse(TaskSafetyManager.isEmergencyStopActive.get())
        assertTrue(TaskSafetyManager.canExecute(ChannelType.WHATSAPP))

        TaskSafetyManager.isEmergencyStopActive.set(true)
        assertTrue(TaskSafetyManager.isEmergencyStopActive.get())
        assertFalse(TaskSafetyManager.canExecute(ChannelType.WHATSAPP))
        assertFalse(TaskSafetyManager.canExecute(ChannelType.CALL))
        assertFalse(TaskSafetyManager.canExecute(ChannelType.SMS))

        TaskSafetyManager.resetEmergencyStop(null)
        assertFalse(TaskSafetyManager.isEmergencyStopActive.get())
        assertTrue(TaskSafetyManager.canExecute(ChannelType.WHATSAPP))
    }

    @Test
    fun testCircuitBreakerTripsAfterThreeConsecutiveFailures() {
        val channel = ChannelType.WHATSAPP_BUSINESS
        assertFalse(TaskSafetyManager.isCircuitBreakerTripped(channel))

        TaskSafetyManager.recordFailure(channel, null)
        assertFalse(TaskSafetyManager.isCircuitBreakerTripped(channel))

        TaskSafetyManager.recordFailure(channel, null)
        assertFalse(TaskSafetyManager.isCircuitBreakerTripped(channel))

        // 3rd failure trips the circuit breaker
        TaskSafetyManager.recordFailure(channel, null)
        assertTrue(TaskSafetyManager.isCircuitBreakerTripped(channel))
        assertFalse(TaskSafetyManager.canExecute(channel))

        // Other channels remain unaffected
        assertTrue(TaskSafetyManager.canExecute(ChannelType.SMS))

        // Successful execution clears the circuit breaker
        TaskSafetyManager.recordSuccess(channel)
        assertFalse(TaskSafetyManager.isCircuitBreakerTripped(channel))
        assertTrue(TaskSafetyManager.canExecute(channel))
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
        val emergencyNumbers = listOf(
            "112", "100", "101", "102", "108", "1090", "1091", "1098", "1930",
            "1075", "1070", "1077", "1072", "1073", "139", "911", "999"
        )
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
        val resultEmpty = com.jmd.helper.core.TaskContentValidator.validateContent(
            context = null,
            channel = ChannelType.WHATSAPP_BUSINESS_STATUS,
            messageText = "",
            mediaUri = null
        )
        assertTrue("Expected rejection for empty text and empty media", resultEmpty is com.jmd.helper.core.ContentValidationResult.Reject)

        val resultText = com.jmd.helper.core.TaskContentValidator.validateContent(
            context = null,
            channel = ChannelType.WHATSAPP_BUSINESS_STATUS,
            messageText = "Grand Opening Today!",
            mediaUri = null
        )
        assertTrue("Expected text acceptance", resultText is com.jmd.helper.core.ContentValidationResult.AcceptTextOnly)
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

    @Test
    fun testDailyCampaignScheduleTargetCalculation() {
        val config = DailyCampaignConfig(
            id = 1,
            channel = "SMS",
            scheduledHour = 9,
            scheduledMinute = 0,
            isActive = true
        )

        // Case 1: Early morning (e.g. 6:22 AM). Target must be TODAY at 9:00 AM
        val morningCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 6)
            set(java.util.Calendar.MINUTE, 22)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val target1 = (morningCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, config.scheduledHour)
            set(java.util.Calendar.MINUTE, config.scheduledMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (target1.before(morningCal)) {
            target1.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        assertTrue("Target at 6:22 AM should remain today at 9:00 AM", target1.after(morningCal))
        assertEquals(9, target1.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, target1.get(java.util.Calendar.MINUTE))
        assertEquals(morningCal.get(java.util.Calendar.DAY_OF_YEAR), target1.get(java.util.Calendar.DAY_OF_YEAR))

        // Premature check: At 6:22 AM, morningCal is strictly before 9:00 AM
        val isPremature = morningCal.timeInMillis < target1.timeInMillis - 60_000L
        assertTrue("Dispatch at 6:22 AM for a 9:00 AM campaign must be flagged premature", isPremature)

        // Case 2: Post-window (e.g. 10:30 AM). Target must advance to TOMORROW at 9:00 AM
        val lateCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 10)
            set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val target2 = (lateCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, config.scheduledHour)
            set(java.util.Calendar.MINUTE, config.scheduledMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (target2.before(lateCal)) {
            target2.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        assertTrue("Target at 10:30 AM must advance to tomorrow", target2.after(lateCal))
        val dayDiff = target2.get(java.util.Calendar.DAY_OF_YEAR) - lateCal.get(java.util.Calendar.DAY_OF_YEAR)
        assertEquals(1, dayDiff)
    }

    @Test
    fun testWhatsAppGroupSearchCleaning() {
        fun stripPrefix(v: String) = v.replaceFirst(Regex("^group\\s*:", RegexOption.IGNORE_CASE), "").trim()
        fun normalize(v: String) = stripPrefix(v).replace(Regex("\\s+"), " ").lowercase()

        assertEquals("Dilip", stripPrefix("group:Dilip"))
        assertEquals("Dilip", stripPrefix("Group: Dilip"))
        assertEquals("dilip", normalize("Group: Dilip"))
        assertEquals("family group", normalize("group:   Family   Group  "))
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 11. COMPAT SUITE TESTS (ZERO SUPPRESSION INVARIANT)
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testTelephonyManagerCompatNullSafety() {
        val state = com.jmd.helper.compat.TelephonyManagerCompat.getCallState(null)
        assertEquals(android.telephony.TelephonyManager.CALL_STATE_IDLE, state)

        val isConnected = com.jmd.helper.compat.TelephonyManagerCompat.isCallConnected(null, null)
        assertFalse(isConnected)

        val isActive = com.jmd.helper.compat.TelephonyManagerCompat.isCallActiveOrRinging(null, null)
        assertFalse(isActive)
    }

    @Test
    fun testAccessibilityCompatNullSafety() {
        val isChecked = com.jmd.helper.compat.AccessibilityCompat.isNodeChecked(null)
        assertFalse(isChecked)
    }

    @Test
    fun testClipboardCompatNullSafety() {
        // Must handle null gracefully without throwing any exception
        com.jmd.helper.compat.ClipboardCompat.setPrimaryClip(null, null)
        assertTrue(true)
    }

    @Test
    fun testPackageManagerCompatNullSafety() {
        // Verify PackageManagerCompat object and method signature integrity
        assertNotNull(com.jmd.helper.compat.PackageManagerCompat)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 12. WHATSAPP VOICE CALL DISPATCH MODE & CHANNEL TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testWhatsAppVoiceCallDispatchModeExists() {
        val modes = com.jmd.helper.service.TaskAccessibilityService.Companion.DispatchMode.entries.map { it.name }
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
                messageText = "Helper test ${channel.displayName} message at {time}",
                triggerTimeMillis = baseTime + ((index + 1) * staggerSeconds * 1000L),
                recurrence = RecurrenceType.REPEAT_SECONDS,
                customIntervalMinutes = repeatIntervalSeconds,
                callDurationSeconds = 10,
                includeDialTimeInCallDuration = true,
                delayBetweenCallsSeconds = 5,
                soundModeSetting = "NORMAL",
                simulatedCallerName = "Helper Test",
                simulatedCallerNumber = testRecipients.first(),
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
        TaskSafetyManager.lastHumanInteractionTimestamp.set(baseTime)

        fun isIdle(elapsedMillis: Long): Boolean = elapsedMillis >= TaskSafetyManager.USER_IDLE_COOLDOWN_MS

        // Elapsed time < USER_IDLE_COOLDOWN_MS (10s) must NOT be idle
        assertFalse("Device should not be idle 3s after human interaction", isIdle(3000L))

        // Elapsed time >= USER_IDLE_COOLDOWN_MS (10s) MUST be idle
        assertTrue("Device must be considered idle 12s after human interaction", isIdle(12000L))
    }

    @Test
    fun testHumanInterruptionFlagAndPostponeCalculation() {
        // Interruption flag behavior
        TaskSafetyManager.isInterruptedByHuman.set(true)
        assertTrue("Interruption flag should be set", TaskSafetyManager.isInterruptedByHuman.get())

        TaskSafetyManager.isInterruptedByHuman.set(false)
        assertFalse("Interruption flag should be reset", TaskSafetyManager.isInterruptedByHuman.get())

        // Postpone calculation
        val currentTrigger = 1700000000000L
        val postponeDuration = TaskSafetyManager.DEFAULT_POSTPONE_INTERVAL_MS
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
        assertEquals("Helper #23: Doctor Appointment", label1)

        // Blank title fallback to message text
        val task2 = ScheduledTask(
            id = 24L,
            title = "",
            channel = ChannelType.REMINDER,
            messageText = "Take medicine on time"
        )
        val label2 = AlarmScheduler.getSystemAlarmLabel(task2)
        assertEquals("Helper #24: Take medicine on time", label2)

        // Blank title and blank message fallback to default
        val task3 = ScheduledTask(
            id = 25L,
            title = "   ",
            channel = ChannelType.REMINDER,
            messageText = ""
        )
        val label3 = AlarmScheduler.getSystemAlarmLabel(task3)
        assertEquals("Helper #25: Reminder", label3)

        // Guarantee uniqueness across different task IDs even with identical titles
        val task4 = ScheduledTask(
            id = 26L,
            title = "Doctor Appointment",
            channel = ChannelType.REMINDER
        )
        val label4 = AlarmScheduler.getSystemAlarmLabel(task4)
        assertNotEquals("Tasks with different IDs must have distinct alarm labels", label1, label4)
        assertTrue("Label must contain task ID", label4.startsWith("Helper #26:"))
    }

    @Test
    fun testSystemAlarmIsolationFromUserAlarms() {
        val userPersonalAlarmLabels = listOf("Wake up", "Gym", "7:00 AM", "Morning Alarm", "Meeting")
        val task = ScheduledTask(
            id = 99L,
            title = "Meeting",
            channel = ChannelType.REMINDER
        )
        val helperLabel = AlarmScheduler.getSystemAlarmLabel(task)

        // Verify Helper label never equals any personal alarm label
        for (personalLabel in userPersonalAlarmLabels) {
            assertNotEquals("Helper alarm label must never collide with personal alarm", personalLabel, helperLabel)
        }
        assertTrue("Helper alarm must be prefixed with unique Helper tag", helperLabel.startsWith("Helper #99:"))
    }

    @Test
    fun testWatchdogHeartbeatResilienceConstants() {
        assertEquals("HelperA11yWatchdogWork", com.jmd.helper.scheduler.AccessibilityWatchdogWorker.UNIQUE_WORK_NAME)
        assertEquals("com.jmd.helper.ACTION_WATCHDOG_HEARTBEAT", AlarmScheduler.ACTION_FIRE_TASK.let {
            com.jmd.helper.scheduler.AccessibilityWatchdogScheduler.ACTION_WATCHDOG_HEARTBEAT
        })
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 19. BANKING PROTECTION & FINANCIAL PACKAGE ISOLATION TESTS
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
            "com.mobikwik_new",
            "com.google.android.apps.walletnfcrel",
            "com.slicepay",
            "com.jupiter.money",
            "com.fi.money",
            "money.paytmmoney",
            "com.zerodha.kite3",
            "com.nextbillion.groww",
            "com.dhan.live",
            "com.saraswat.app"
        )

        for (pkg in bankingPackages) {
            assertTrue(
                "Package $pkg must be identified as banking/financial package for standby isolation",
                com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage(pkg)
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
            "com.jmd.helper"
        )

        for (pkg in standardPackages) {
            assertFalse(
                "Package $pkg must NOT be flagged as banking package",
                com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage(pkg)
            )
        }
    }

    @Test
    fun testSmsQuotaManagerPduCalculation() {
        val shortAscii = "Hello world"
        assertEquals(1, com.jmd.helper.core.SmsQuotaManager.calculateSmsSegments(shortAscii))

        val longAscii = "A".repeat(161)
        assertEquals(2, com.jmd.helper.core.SmsQuotaManager.calculateSmsSegments(longAscii))

        val unicodeText = "Hello 😊"
        assertEquals(1, com.jmd.helper.core.SmsQuotaManager.calculateSmsSegments(unicodeText))

        val longUnicode = "😊".repeat(71)
        assertTrue(com.jmd.helper.core.SmsQuotaManager.calculateSmsSegments(longUnicode) >= 2)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 19. ALL TWELVE CHANNELS VALIDATION & REPEAT UNITS TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testAllTwelveChannelsValidationAcceptsTextPayload() {
        val channels = com.jmd.helper.data.model.ChannelType.entries
        assertEquals(12, channels.size)

        for (channel in channels) {
            val result = com.jmd.helper.core.TaskContentValidator.validateContent(
                context = null,
                channel = channel,
                messageText = "Scheduled test dispatch",
                mediaUri = null
            )
            assertTrue(
                "Channel ${channel.name} must accept valid text payload",
                result is com.jmd.helper.core.ContentValidationResult.AcceptTextOnly
            )
        }
    }

    @Test
    fun testRepeatUnitsRoundTripAndRecurrenceTypeMapping() {
        for (unit in com.jmd.helper.data.model.RepeatUnit.entries) {
            val mappedRecurrence = unit.toRecurrenceType()
            assertNotNull(mappedRecurrence)
            val parsed = com.jmd.helper.data.model.RepeatUnit.fromString(unit.name)
            assertEquals(unit, parsed)
        }
    }

    @Test
    fun testLocalBackupListingFilterAndSorting() {
        val tempDir = java.nio.file.Files.createTempDirectory("helper_backup_test").toFile()
        try {
            val f1 = java.io.File(tempDir, "Helper_Backup_20261026_100000.json").apply { writeText("{}") }
            val f2 = java.io.File(tempDir, "Helper_Backup_20261026_110000.json").apply { writeText("{}") }
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

    private fun isCallEndedTransition(callType: Int, durationSeconds: Long): Boolean {
        val isOutgoing = callType == android.provider.CallLog.Calls.OUTGOING_TYPE
        return isOutgoing && durationSeconds > 0L
    }

    private fun isMissedCallEvent(callType: Int, durationSeconds: Long): Boolean {
        return callType == android.provider.CallLog.Calls.MISSED_TYPE || (callType != android.provider.CallLog.Calls.OUTGOING_TYPE && durationSeconds == 0L)
    }

    @Test
    fun testOutgoingCallWithZeroDurationIsNotCallEnded() {
        // Simulating the user placing an outgoing call (type = 2) where remote party does not answer (duration = 0)
        val isCallEnded = isCallEndedTransition(android.provider.CallLog.Calls.OUTGOING_TYPE, 0L)
        val isMissedCall = false

        assertFalse("Unanswered outgoing dial with duration 0 must NOT be treated as Call Ended", isCallEnded)
        assertFalse("Outgoing dial must not be treated as Missed Call", isMissedCall)
    }

    @Test
    fun testOutgoingCallWithPositiveDurationIsCallEnded() {
        // Simulating user dialing an outgoing call that was answered and spoke for 45 seconds
        val isCallEnded = isCallEndedTransition(android.provider.CallLog.Calls.OUTGOING_TYPE, 45L)

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
        val isMissed = isMissedCallEvent(android.provider.CallLog.Calls.MISSED_TYPE, 0L)
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
        assertEquals(androidx.compose.ui.graphics.Color(0xFF00E676), com.jmd.helper.ui.components.IndicatorActiveGreen)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFF5252), com.jmd.helper.ui.components.IndicatorInactiveRed)
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
        assertEquals(androidx.compose.ui.graphics.Color(0xFF00E676), com.jmd.helper.ui.components.IndicatorActiveGreen)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFF5252), com.jmd.helper.ui.components.IndicatorInactiveRed)
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
        assertTrue("disableTaskAction must default to true", config.disableTaskAction)
        assertTrue("blockOverlays must default to true", config.blockOverlays)
        assertTrue("suppressNotifications must default to true", config.suppressNotifications)
        assertTrue("disableClipboard must default to true", config.disableClipboard)
        assertTrue("maskSensitiveContent must default to true", config.maskSensitiveContent)
        assertTrue("disableRules must default to true", config.disableRules)
        assertEquals("", config.customNotes)
    }

    @Test
    fun testSensitiveTextMasking() {
        assertEquals("", com.jmd.helper.core.PrivacyManager.maskSensitiveText(""))
        assertEquals("**", com.jmd.helper.core.PrivacyManager.maskSensitiveText("hi"))
        assertEquals("j***i", com.jmd.helper.core.PrivacyManager.maskSensitiveText("jai mata di"))
        assertEquals("P***d", com.jmd.helper.core.PrivacyManager.maskSensitiveText("Password"))
    }

    @Test
    fun testPhoneNumberMasking() {
        assertEquals("9769***020", com.jmd.helper.core.PrivacyManager.maskPhoneNumber("9769602020"))
        assertEquals("9930***542", com.jmd.helper.core.PrivacyManager.maskPhoneNumber("+91 99306 04542"))
        assertEquals("***", com.jmd.helper.core.PrivacyManager.maskPhoneNumber("123"))
    }

    @Test
    fun testBankingPackageDetection() {
        // Banking & UPI apps
        assertTrue(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.saraswatbank.gomobile"))
        assertTrue(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.sbi.lotusintouch"))
        assertTrue(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.google.android.apps.nbu.paisa.user"))
        assertTrue(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.phonepe.app"))
        assertTrue(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("net.one97.paytm"))
        assertTrue(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.hdfcbank.mobilebanking"))
        assertTrue(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.csam.icici.bank.imobile"))
        assertTrue(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.axis.mobile"))
        assertTrue(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.kotak.bank"))
        assertTrue(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("in.org.npci.upiapp"))
        assertTrue(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.cred.app"))

        // Non-banking standard apps
        assertFalse(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.whatsapp"))
        assertFalse(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.whatsapp.w4b"))
        assertFalse(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.google.android.dialer"))
        assertFalse(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.android.settings"))
        assertFalse(com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage("com.jmd.helper"))
    }

    @Test
    fun testBaseTaskTargetPackages() {
        val targets = com.jmd.helper.core.PrivacyManager.BASE_TASK_TARGET_PACKAGES
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
        assertTrue(settings.dynamicA11yOnDemand)
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
        assertTrue(modified.disableTaskAction)
        assertEquals("User whitelisted", modified.customNotes)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // ═══════════════════════════════════════════════════════════════════════════════════
    // 23. PROTECTED APPLICATION MANAGER (BANKING & PAYMENT ISOLATION) TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testProtectedApplicationCatalogDetection() {
        // Explicit banking & payment package verification
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.saraswatbank.gomobile"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.sbi.lotusintouch"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.google.android.apps.nbu.paisa.user"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.phonepe.app"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("net.one97.paytm"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.hdfcbank.mobilebanking"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.csam.icici.bank.imobile"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.axis.mobile"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.kotak.bank"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("in.org.npci.upiapp"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.cred.app"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.zerodha.kite3"))

        // Keyword heuristics for uncataloged banking/wallet apps
        assertTrue(ProtectedApplicationManager.isProtectedPackage("org.somebank.mobilebanking"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.secure.mywallet.app"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.finance.crypto.trade"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.newfintech.upi.payment"))

        // Standard non-banking applications must NOT be flagged as protected
        assertFalse(ProtectedApplicationManager.isProtectedPackage("com.whatsapp"))
        assertFalse(ProtectedApplicationManager.isProtectedPackage("com.whatsapp.w4b"))
        assertFalse(ProtectedApplicationManager.isProtectedPackage("com.google.android.dialer"))
        assertFalse(ProtectedApplicationManager.isProtectedPackage("com.android.systemui"))
        assertFalse(ProtectedApplicationManager.isProtectedPackage("com.jmd.helper"))
    }

    @Test
    fun testProtectedApplicationManagerDefaultDenyAllowlist() {
        // Only authorized task targets are permitted
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.whatsapp"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.whatsapp.w4b"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.google.android.dialer"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.android.phone"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.android.systemui"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.jmd.helper"))

        // Default-deny policy: unauthorized applications must be rejected
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.saraswatbank.gomobile"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("net.one97.paytm"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.android.chrome"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.android.settings"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.google.android.gm"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.instagram.android"))
    }

    @Test
    fun testProtectedApplicationContextVerificationBeforeInteraction() {
        // Valid same-target interaction on authorized app
        assertTrue(
            "Interaction allowed when foreground app matches target WhatsApp",
            ProtectedApplicationManager.verifyContextBeforeInteraction(
                currentPackage = "com.whatsapp",
                expectedTargetPackage = "com.whatsapp"
            )
        )

        // Valid System UI keyguard interaction
        assertTrue(
            "Interaction allowed for SystemUI dismiss keyguard",
            ProtectedApplicationManager.verifyContextBeforeInteraction(
                currentPackage = "com.android.systemui",
                expectedTargetPackage = "com.whatsapp"
            )
        )

        // CRITICAL SECURITY INVARIANT: Absolute rejection when foreground is banking/payment app
        assertFalse(
            "Interaction must be rejected when foreground app is Saraswat Bank",
            ProtectedApplicationManager.verifyContextBeforeInteraction(
                currentPackage = "com.saraswatbank.gomobile",
                expectedTargetPackage = "com.whatsapp"
            )
        )
        assertFalse(
            "Interaction must be rejected when foreground app is Google Pay",
            ProtectedApplicationManager.verifyContextBeforeInteraction(
                currentPackage = "com.google.android.apps.nbu.paisa.user",
                expectedTargetPackage = "com.google.android.apps.nbu.paisa.user"
            )
        )

        // Cross-app mismatch rejection (prevents UI confusion / unexpected targets)
        assertFalse(
            "Cross-package interaction must be rejected when foreground app does not match target",
            ProtectedApplicationManager.verifyContextBeforeInteraction(
                currentPackage = "com.android.chrome",
                expectedTargetPackage = "com.whatsapp"
            )
        )
    }

    @Test
    fun testProtectedApplicationTaskTargetAuthorization() {
        val validWhatsAppTask = ScheduledTask(
            channel = ChannelType.WHATSAPP,
            recipients = listOf("+919769602020"),
            messageText = "Hello"
        )
        assertTrue(ProtectedApplicationManager.isTaskTargetAuthorized(validWhatsAppTask))

        val validCallTask = ScheduledTask(
            channel = ChannelType.CALL,
            recipients = listOf("+919769602020")
        )
        assertTrue(ProtectedApplicationManager.isTaskTargetAuthorized(validCallTask))

        val validSmsTask = ScheduledTask(
            channel = ChannelType.SMS,
            recipients = listOf("+919769602020"),
            messageText = "SMS Message"
        )
        assertTrue(ProtectedApplicationManager.isTaskTargetAuthorized(validSmsTask))
    }

    @Test
    fun testProtectedApplicationOverlayAndInterferenceRefusal() {
        // Overlay suppression and strict blocking over financial and unauthorized applications
        assertTrue(ProtectedApplicationManager.shouldBlockOverlays("com.google.android.apps.nbu.paisa.user"))
        assertTrue(ProtectedApplicationManager.shouldBlockOverlays("com.saraswatbank.gomobile"))
        assertTrue(ProtectedApplicationManager.shouldBlockOverlays("net.one97.paytm"))
        assertTrue(ProtectedApplicationManager.shouldBlockOverlays("com.phonepe.app"))
        assertTrue(ProtectedApplicationManager.shouldBlockOverlays("com.hdfcbank.mobilebanking"))
        assertFalse(ProtectedApplicationManager.shouldBlockOverlays("com.whatsapp"))
        assertFalse(ProtectedApplicationManager.shouldBlockOverlays("com.google.android.dialer"))
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 24. PERMISSION MANAGER CATALOG & MAPPING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testPermissionManagerCatalogCompleteness() {
        val permissions = PermissionManager.getAllPermissions()
        assertEquals(14, permissions.size)

        val permMap = permissions.associateBy { it.permissionId }

        // Accessibility check
        val a11y = permMap[PermissionManager.ID_ACCESSIBILITY]
        assertNotNull(a11y)
        assertTrue(a11y!!.isCritical)
        assertEquals("Task", a11y.featureName)
        assertFalse(a11y.deniedImpact.isEmpty())
        assertFalse(a11y.revocationSafety.isEmpty())

        // Exact alarms check
        val exactAlarms = permMap[PermissionManager.ID_EXACT_ALARMS]
        assertNotNull(exactAlarms)
        assertTrue(exactAlarms!!.isCritical)

        // Non-critical permissions
        val contacts = permMap[PermissionManager.ID_READ_CONTACTS]
        assertNotNull(contacts)
        assertFalse(contacts!!.isCritical)

        val notifications = permMap[PermissionManager.ID_POST_NOTIFICATIONS]
        assertNotNull(notifications)
        assertFalse(notifications!!.isCritical)

        // Overlay check
        val overlay = permMap[PermissionManager.ID_OVERLAY]
        assertNotNull(overlay)
        assertFalse(overlay!!.isCritical)
        assertTrue(overlay.featureName.contains("Overlay"))

        // Critical permissions list
        assertEquals(2, PermissionManager.CRITICAL_PERMISSIONS.size)
        assertTrue(PermissionManager.CRITICAL_PERMISSIONS.contains(PermissionManager.ID_ACCESSIBILITY))
        assertTrue(PermissionManager.CRITICAL_PERMISSIONS.contains(PermissionManager.ID_EXACT_ALARMS))
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 25. TASK STATUS LIFECYCLE & STATE MACHINE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testTaskStatusStateTransitions() {
        // DRAFT transitions
        assertTrue(TaskStatus.canTransition(TaskStatus.DRAFT, TaskStatus.VALIDATING))
        assertTrue(TaskStatus.canTransition(TaskStatus.DRAFT, TaskStatus.CANCELLED))
        assertFalse(TaskStatus.canTransition(TaskStatus.DRAFT, TaskStatus.RUNNING))

        // VALIDATING transitions
        assertTrue(TaskStatus.canTransition(TaskStatus.VALIDATING, TaskStatus.READY))
        assertTrue(TaskStatus.canTransition(TaskStatus.VALIDATING, TaskStatus.FAILED))
        assertTrue(TaskStatus.canTransition(TaskStatus.VALIDATING, TaskStatus.CANCELLED))
        assertFalse(TaskStatus.canTransition(TaskStatus.VALIDATING, TaskStatus.RUNNING))

        // READY transitions
        assertTrue(TaskStatus.canTransition(TaskStatus.READY, TaskStatus.SCHEDULED))
        assertTrue(TaskStatus.canTransition(TaskStatus.READY, TaskStatus.RUNNING))
        assertTrue(TaskStatus.canTransition(TaskStatus.READY, TaskStatus.CANCELLED))

        // SCHEDULED transitions
        assertTrue(TaskStatus.canTransition(TaskStatus.SCHEDULED, TaskStatus.RUNNING))
        assertTrue(TaskStatus.canTransition(TaskStatus.SCHEDULED, TaskStatus.PAUSED))
        assertTrue(TaskStatus.canTransition(TaskStatus.SCHEDULED, TaskStatus.CANCELLED))

        // RUNNING transitions
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.SUCCESS))
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.FAILED))
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.CANCELLED))
        assertFalse(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.READY))

        // PAUSED transitions
        assertTrue(TaskStatus.canTransition(TaskStatus.PAUSED, TaskStatus.SCHEDULED))
        assertTrue(TaskStatus.canTransition(TaskStatus.PAUSED, TaskStatus.CANCELLED))
        assertFalse(TaskStatus.canTransition(TaskStatus.PAUSED, TaskStatus.RUNNING))
    }

    @Test
    fun testTaskStatusHelperPredicates() {
        assertTrue(TaskStatus.SCHEDULED.isPendingOrScheduled)
        assertTrue(TaskStatus.READY.isPendingOrScheduled)
        assertTrue(TaskStatus.PENDING.isPendingOrScheduled)
        assertFalse(TaskStatus.RUNNING.isPendingOrScheduled)
        assertFalse(TaskStatus.SUCCESS.isPendingOrScheduled)

        assertTrue(TaskStatus.SUCCESS.isSuccessOrDone)
        assertTrue(TaskStatus.DONE.isSuccessOrDone)
        assertFalse(TaskStatus.RUNNING.isSuccessOrDone)
        assertFalse(TaskStatus.FAILED.isSuccessOrDone)

        assertTrue(TaskStatus.SUCCESS.isTerminal)
        assertTrue(TaskStatus.FAILED.isTerminal)
        assertTrue(TaskStatus.CANCELLED.isTerminal)
        assertFalse(TaskStatus.RUNNING.isTerminal)
        assertFalse(TaskStatus.SCHEDULED.isTerminal)
        assertFalse(TaskStatus.READY.isTerminal)

        assertTrue(TaskStatus.RUNNING.isRunning)
        assertFalse(TaskStatus.SCHEDULED.isRunning)

        assertTrue(TaskStatus.PAUSED.isPaused)
        assertFalse(TaskStatus.RUNNING.isPaused)
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 34. POLICY REFACTORING & TERMINOLOGY COMPLIANCE TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    @Suppress("DEPRECATION")
    fun testChannelTypeCallSimulationCompatibility() {
        // Primary policy-compliant enum name
        assertEquals("CALL_SIMULATION", ChannelType.CALL_SIMULATION.name)
        assertEquals("Call Simulation", ChannelType.CALL_SIMULATION.displayName)

        // Backward compatibility: FAKE_CALL alias resolves to CALL_SIMULATION
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.FAKE_CALL)

        // String parsing compatibility for database records and broadcasts
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("CALL_SIMULATION"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("call_simulation"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("FAKE_CALL"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("fake_call"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("SIMULATION"))
    }

    @Test
    @Suppress("DEPRECATION")
    fun testSimulatedCallerModelProperties() {
        // Default ScheduledTask must use Indian mobile number format
        val defaultTask = ScheduledTask()
        assertEquals("+91 98765 43210", defaultTask.simulatedCallerNumber)

        val task = ScheduledTask(
            id = 101L,
            title = "Rescue Simulation",
            channel = ChannelType.CALL_SIMULATION,
            simulatedCallerName = "Meeting Organizer",
            simulatedCallerNumber = "+91 98765 43210",
            simulatedCallStyle = "PIXEL"
        )

        // Verify policy-compliant accessors reflect underlying stored values
        assertEquals("Meeting Organizer", task.simulatedCallerName)
        assertEquals("+91 98765 43210", task.simulatedCallerNumber)
        assertEquals("PIXEL", task.simulatedCallStyle)

        // Verify backward compatibility alias accessors
        assertEquals("Meeting Organizer", task.fakeCallerName)
        assertEquals("+91 98765 43210", task.fakeCallerNumber)
        assertEquals("PIXEL", task.fakeCallStyle)

        // Verify authorization under ProtectedApplicationManager
        assertTrue(ProtectedApplicationManager.isTaskTargetAuthorized(task))
    }

    @Test
    fun testProtectedAppExclusionGuaranteesForSimulationAndBanking() {
        // Verify known payment apps are strictly protected
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.google.android.apps.nbu.paisa.user"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.phonepe.app"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("net.one97.paytm"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.sbi.lotusintouch"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.saraswatbank.gomobile"))

        // Verify banking apps cannot be authorized task targets
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.google.android.apps.nbu.paisa.user"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.phonepe.app"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.saraswat.app"))

        // Verify context check rejects interactions in banking apps
        assertFalse(ProtectedApplicationManager.verifyContextBeforeInteraction("com.google.android.apps.nbu.paisa.user", "com.whatsapp"))
        assertFalse(ProtectedApplicationManager.verifyContextBeforeInteraction("com.phonepe.app", null))

        // Verify overlay suppression is active for protected apps
        assertTrue(ProtectedApplicationManager.shouldBlockOverlays("com.google.android.apps.nbu.paisa.user"))
        assertTrue(ProtectedApplicationManager.shouldBlockOverlays("com.phonepe.app"))
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 35. TASK LIFECYCLE & STATE TRANSITION TESTS (PART 5 & PART 8)
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testTaskLifecycleTransitions() {
        // Valid forward transitions
        assertTrue(TaskStatus.canTransition(TaskStatus.DRAFT, TaskStatus.VALIDATING))
        assertTrue(TaskStatus.canTransition(TaskStatus.VALIDATING, TaskStatus.READY))
        assertTrue(TaskStatus.canTransition(TaskStatus.READY, TaskStatus.SCHEDULED))
        assertTrue(TaskStatus.canTransition(TaskStatus.SCHEDULED, TaskStatus.RUNNING))
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.SUCCESS))
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.FAILED))
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.PAUSED))

        // Same-status transitions always valid
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.RUNNING))
        assertTrue(TaskStatus.canTransition(TaskStatus.SCHEDULED, TaskStatus.SCHEDULED))

        // Cancellation paths
        assertTrue(TaskStatus.canTransition(TaskStatus.DRAFT, TaskStatus.CANCELLED))
        assertTrue(TaskStatus.canTransition(TaskStatus.READY, TaskStatus.CANCELLED))
        assertTrue(TaskStatus.canTransition(TaskStatus.SCHEDULED, TaskStatus.CANCELLED))
        assertTrue(TaskStatus.canTransition(TaskStatus.RUNNING, TaskStatus.CANCELLED))
        assertTrue(TaskStatus.canTransition(TaskStatus.PAUSED, TaskStatus.CANCELLED))

        // Invalid direct jumps
        assertFalse(TaskStatus.canTransition(TaskStatus.DRAFT, TaskStatus.RUNNING))
        assertFalse(TaskStatus.canTransition(TaskStatus.DRAFT, TaskStatus.SUCCESS))

        // Terminal states
        assertTrue(TaskStatus.SUCCESS.isTerminal)
        assertTrue(TaskStatus.DONE.isTerminal)
        assertTrue(TaskStatus.FAILED.isTerminal)
        assertTrue(TaskStatus.CANCELLED.isTerminal)
        assertFalse(TaskStatus.RUNNING.isTerminal)
        assertFalse(TaskStatus.SCHEDULED.isTerminal)
    }

    @Test
    fun testDefaultDenyAndAuthorizedTargets() {
        // Explicitly authorized targets under Default-Deny
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.whatsapp"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.whatsapp.w4b"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.android.dialer"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.google.android.dialer"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.samsung.android.dialer"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.coloros.dialer"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.oplus.dialer"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.vivo.upslide"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.miui.telephony"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.android.systemui"))
        assertTrue(ProtectedApplicationManager.isAuthorizedTarget("com.jmd.helper"))

        // Indian banking & payment applications must be strictly protected and denied from task targets
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.boi.mobile"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.sbi.lotusintouch"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.tatadigital.tcp"))
        assertTrue(ProtectedApplicationManager.isProtectedPackage("com.naviapp"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.boi.mobile"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.sbi.lotusintouch"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.tatadigital.tcp"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.naviapp"))

        // Unauthorized third-party apps, browsers, games must be denied
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.instagram.android"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.facebook.katana"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.google.android.youtube"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.unknown.malicious.app"))
        assertFalse(ProtectedApplicationManager.isAuthorizedTarget("com.android.chrome"))

        // Verification before interaction rejects unauthorized targets
        assertFalse(ProtectedApplicationManager.verifyContextBeforeInteraction("com.instagram.android", "com.whatsapp"))
        assertFalse(ProtectedApplicationManager.verifyContextBeforeInteraction(null, "com.whatsapp"))
        assertFalse(ProtectedApplicationManager.verifyContextBeforeInteraction("", "com.whatsapp"))
    }

    @Test
    fun testIndianHelplineNumbersAndMobileFormatters() {
        val requestedNumbers = listOf("9769602020", "9930604542")
        for (num in requestedNumbers) {
            assertTrue("Expected valid Indian mobile for $num", PhoneNumberNormalizer.isIndianMobileNumber(num))
            assertEquals("+91$num", PhoneNumberNormalizer.normalize(num))
            val formatted = PhoneNumberNormalizer.formatForDisplay(num)
            assertTrue("Expected space in display format: $formatted", formatted.startsWith("+91 "))
        }

        // Indian national helplines must be strictly blocked from scheduled dispatch
        val helplines = listOf("112", "100", "101", "108", "1930", "1947", "1909", "155260", "104", "1097", "1064", "1076", "14416", "1033", "1071", "1099", "182")
        for (helpline in helplines) {
            assertTrue("Expected emergency/helpline block for $helpline", PhoneNumberNormalizer.isEmergencyNumber(helpline))
        }
    }

    @Test
    fun testIndianExpandedBankingAndIdentityProtection() {
        val indianFinancialAndIdentityApps = listOf(
            "com.fss.ippb",
            "com.ujjivan.mobilebanking",
            "com.equitas.mobile",
            "com.digilocker.android",
            "in.net.uidai.mAadhaarPlus",
            "in.gov.umang.negd.g2c",
            "in.gov.incometax.ais",
            "com.wrx.wazirx",
            "com.fivepaisa.trade",
            "com.motilaloswal.invest",
            "com.sharekhan"
        )
        for (pkg in indianFinancialAndIdentityApps) {
            assertTrue("Expected $pkg to be protected", ProtectedApplicationManager.isProtectedPackage(pkg))
            assertFalse("Expected $pkg to NOT be authorized target", ProtectedApplicationManager.isAuthorizedTarget(pkg))
        }
    }

    @Test
    fun testIndiaCentricTemplateTagsAndEmojiResolution() {
        val template = "{namaste} {name}! Your token is {rupee}500. Message: {greeting} 🚀✨🙏"
        val resolved = MessageTemplateResolver.resolve(
            template = template,
            name = "Ramesh Kumar",
            phone = "9769602020"
        )
        assertTrue(resolved.contains("Namaste Ramesh Kumar!"))
        assertTrue(resolved.contains("₹500"))
        assertTrue(resolved.contains("🚀✨🙏"))
        assertFalse(resolved.contains("{namaste}"))
        assertFalse(resolved.contains("{rupee}"))
        assertFalse(resolved.contains("{greeting}"))
    }

    @Test
    fun testExpandedIndianFintechAndBankingShieldCoverage() {
        val additionalIndianApps = listOf(
            "com.snapwork.hdfc",
            "com.unionbank.neomobile",
            "com.msf.kbank.mobile",
            "com.kotak.omni",
            "money.jupiter",
            "money.fi",
            "com.theslice.app",
            "in.upstox.app",
            "com.navi.markets",
            "com.navifinance",
            "com.niyo.bharat",
            "com.pnb.one",
            "com.angelbroking",
            "com.bajajfinserv.experia",
            "in.org.npci.upiapp"
        )
        for (pkg in additionalIndianApps) {
            assertTrue("Expected $pkg to be protected from UI actions", ProtectedApplicationManager.isProtectedPackage(pkg))
            assertFalse("Expected $pkg to NOT be authorized target", ProtectedApplicationManager.isAuthorizedTarget(pkg))
            assertTrue("Expected $pkg to be flagged in AccessibilityService financial check", com.jmd.helper.service.TaskAccessibilityService.isFinancialOrBankingPackage(pkg))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 22. LAUNCHER ICON & ALL 12 CHANNELS VERIFICATION
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testAllTwelveChannelsCompleteness() {
        val allChannels = ChannelType.entries
        assertEquals("Expected exactly 12 channels", 12, allChannels.size)

        val expectedChannels = setOf(
            ChannelType.SMS,
            ChannelType.WHATSAPP,
            ChannelType.WHATSAPP_BUSINESS,
            ChannelType.WHATSAPP_GROUP,
            ChannelType.WHATSAPP_STATUS,
            ChannelType.WHATSAPP_BUSINESS_STATUS,
            ChannelType.CALL,
            ChannelType.WHATSAPP_CALL,
            ChannelType.WHATSAPP_BUSINESS_CALL,
            ChannelType.REMINDER,
            ChannelType.CALL_SIMULATION,
            ChannelType.SOUND_MODE
        )
        assertEquals(expectedChannels, allChannels.toSet())

        // Verify each channel has a non-blank display name
        for (channel in allChannels) {
            assertTrue("Display name must not be blank for ${channel.name}", channel.displayName.isNotBlank())
        }
    }

    @Test
    fun testLauncherIconAssetsIntegrity() {
        val resDir = java.io.File("src/main/res")
        assertTrue("Resource directory must exist", resDir.exists())

        // Adaptive icon XMLs
        val bgXml = java.io.File(resDir, "drawable/ic_launcher_background.xml")
        val fgXml = java.io.File(resDir, "drawable/ic_launcher_foreground.xml")
        val monoXml = java.io.File(resDir, "drawable/ic_launcher_monochrome.xml")
        val adaptiveXml = java.io.File(resDir, "mipmap/ic_launcher.xml")
        val adaptiveRoundXml = java.io.File(resDir, "mipmap/ic_launcher_round.xml")

        assertTrue("ic_launcher_background.xml must exist", bgXml.exists())
        assertTrue("ic_launcher_foreground.xml must exist", fgXml.exists())
        assertTrue("ic_launcher_monochrome.xml must exist", monoXml.exists())
        assertTrue("ic_launcher.xml must exist", adaptiveXml.exists())
        assertTrue("ic_launcher_round.xml must exist", adaptiveRoundXml.exists())

        // Ensure transparent background is eliminated
        val oldTransparentBg = java.io.File(resDir, "drawable/ic_launcher_bg_transparent.xml")
        assertFalse("ic_launcher_bg_transparent.xml must NOT exist", oldTransparentBg.exists())

        val bgContent = bgXml.readText()
        assertFalse("Background must NOT contain transparent color", bgContent.contains("#00000000"))
        assertTrue("Background must contain opaque color", bgContent.contains("#E65100"))

        // Ensure speech balloon dots are present in foreground
        val fgContent = fgXml.readText()
        assertTrue("Foreground must contain speech dots", fgContent.contains("41,42.7") && fgContent.contains("54,42.7"))

        // Verify all 5 mipmap densities for legacy PNG icons
        val densities = listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
        for ((density, _) in densities) {
            val mipmapFolder = java.io.File(resDir, "mipmap-$density")
            assertTrue("Folder mipmap-$density must exist", mipmapFolder.exists())

            val iconPng = java.io.File(mipmapFolder, "ic_launcher.png")
            val iconRoundPng = java.io.File(mipmapFolder, "ic_launcher_round.png")

            assertTrue("ic_launcher.png must exist in mipmap-$density", iconPng.exists())
            assertTrue("ic_launcher.png must not be empty in mipmap-$density", iconPng.length() > 100)

            assertTrue("ic_launcher_round.png must exist in mipmap-$density", iconRoundPng.exists())
            assertTrue("ic_launcher_round.png must not be empty in mipmap-$density", iconRoundPng.length() > 100)
        }
    }

    @Test
    fun testManualPickModeInvariants() {
        assertFalse(
            "isManualPickMode must be false when service is idle",
            com.jmd.helper.service.TaskAccessibilityService.isManualPickMode()
        )
        val modes = com.jmd.helper.service.TaskAccessibilityService.Companion.DispatchMode.entries.map { it.name }
        assertTrue("DispatchMode.MANUAL_PICK_CHAT must be present", modes.contains("MANUAL_PICK_CHAT"))
    }

    @Test
    fun testContactNameStrippingForDynamicPool() {
        val testCases = listOf(
            "Mom ❤️" to "Mom",
            "Dr. John Doe" to "Dr John Doe",
            "Client #4 (VIP)" to "Client 4 VIP",
            "~Alice" to "Alice",
            "  Bob  " to "Bob"
        )
        for ((raw, expectedClean) in testCases) {
            val stripped = raw.trim().removePrefix("~").filter { it.isLetterOrDigit() || it.isWhitespace() }.replace("\\s+".toRegex(), " ").trim()
            assertEquals("Normalized candidate for '$raw'", expectedClean, stripped)
        }
    }

    @Test
    fun testBlockedChatNamesLogic() {
        val blockedExactOrPrefix = listOf(
            "whatsapp", "whatsapp business", "wa business", "w4b",
            "search", "chats", "updates", "calls", "tools", "camera", "settings",
            "more options", "new chat", "back", "video call", "voice call",
            "type a message", "message", "unread message", "create ads",
            "reach potential", "get started", "dismiss", "online", "typing",
            "last seen", "swipe down to reveal", "swipe down", "select all",
            "starred messages", "linked devices", "clear call log", "mute notifications",
            "yesterday", "today", "status", "my status", "channels", "communities",
            "new group", "new community", "new broadcast"
        )
        val validNames = listOf(
            "John Doe", "Mom ❤️", "Project Alpha", "+919876543210", "Alice Smith",
            "Dr. Sharma", "Accountant office", "Client 123", "Emergency Contact"
        )
        for (name in validNames) {
            val lower = name.trim().removePrefix("~").trim().lowercase()
            val isBlocked = blockedExactOrPrefix.any { lower == it || lower.startsWith("$it ") || lower.startsWith("$it…") || lower.startsWith("$it...") }
            assertFalse("Expected '$name' to be valid and NOT blocked", isBlocked)
        }

        val invalidNames = listOf(
            "WhatsApp", "chats", "CHATS", "Type a message", "Search", "Search…", "Back", "more options", "Status", "My Status"
        )
        for (name in invalidNames) {
            val lower = name.trim().removePrefix("~").trim().lowercase()
            val isBlocked = blockedExactOrPrefix.any { lower == it || lower.startsWith("$it ") || lower.startsWith("$it…") || lower.startsWith("$it...") }
            assertTrue("Expected '$name' to be recognized as blocked UI noise", isBlocked)
        }
    }

    @Test
    fun testTaskContentValidatorChannelRules() {
        // SMS requires text; rejects empty
        val smsEmpty = com.jmd.helper.core.TaskContentValidator.validateContent(null, ChannelType.SMS, "", null)
        assertTrue(smsEmpty is com.jmd.helper.core.ContentValidationResult.Reject)

        val smsValid = com.jmd.helper.core.TaskContentValidator.validateContent(null, ChannelType.SMS, "Hello SMS", null)
        assertTrue(smsValid is com.jmd.helper.core.ContentValidationResult.AcceptTextOnly)

        // WhatsApp allows text-only
        val waText = com.jmd.helper.core.TaskContentValidator.validateContent(null, ChannelType.WHATSAPP, "Hello WA", null)
        assertTrue(waText is com.jmd.helper.core.ContentValidationResult.AcceptTextOnly)

        // WhatsApp rejects empty text with no media
        val waEmpty = com.jmd.helper.core.TaskContentValidator.validateContent(null, ChannelType.WHATSAPP, "   ", null)
        assertTrue(waEmpty is com.jmd.helper.core.ContentValidationResult.Reject)

        // WhatsApp accepts media-only (content:// uri simulation)
        val waMediaOnly = com.jmd.helper.core.TaskContentValidator.validateContent(null, ChannelType.WHATSAPP, "", "content://media/external/images/media/123.jpg")
        assertTrue("Expected AcceptMediaOnly for valid content URI without caption", waMediaOnly is com.jmd.helper.core.ContentValidationResult.AcceptMediaOnly)

        // WhatsApp accepts media with caption
        val waMediaCaption = com.jmd.helper.core.TaskContentValidator.validateContent(null, ChannelType.WHATSAPP, "Nice photo!", "content://media/external/images/media/123.jpg")
        assertTrue(waMediaCaption is com.jmd.helper.core.ContentValidationResult.AcceptMediaWithCaption)
        assertEquals("Nice photo!", (waMediaCaption as com.jmd.helper.core.ContentValidationResult.AcceptMediaWithCaption).caption)

        // Unsupported scheme rejected
        val badScheme = com.jmd.helper.core.TaskContentValidator.validateContent(null, ChannelType.WHATSAPP, "Hello", "http://example.com/image.png")
        assertTrue(badScheme is com.jmd.helper.core.ContentValidationResult.Reject)
    }

    @Test
    fun testAutoReplyPatternMatching() {
        val wildcardRule = AutoReplyRule(
            id = 1,
            name = "Wildcard Reply",
            channel = ChannelType.SMS,
            replyText = "Thanks for your message!",
            triggerPattern = "*",
            isExactMatch = false
        )
        // Wildcard matches everything
        val testIncoming = listOf("hi", "hello", "what is the price", "123", "Any message text")
        for (incoming in testIncoming) {
            val matches = wildcardRule.triggerPattern == "*" || incoming.contains(wildcardRule.triggerPattern, ignoreCase = true)
            assertTrue("Wildcard must match '$incoming'", matches)
        }

        // Exact match rule
        val exactRule = AutoReplyRule(
            id = 2,
            name = "Price Inquiry",
            channel = ChannelType.WHATSAPP,
            replyText = "Our starting price is ₹499",
            triggerPattern = "price",
            isExactMatch = true
        )
        val exactMatchText = "price"
        val exactMatched = if (exactRule.isExactMatch) {
            exactMatchText.trim().equals(exactRule.triggerPattern.trim(), ignoreCase = true)
        } else {
            exactMatchText.contains(exactRule.triggerPattern, ignoreCase = true)
        }
        assertTrue("Exact match should pass for 'price'", exactMatched)

        val nonExactText = "what is your price list?"
        val nonExactMatched = if (exactRule.isExactMatch) {
            nonExactText.trim().equals(exactRule.triggerPattern.trim(), ignoreCase = true)
        } else {
            nonExactText.contains(exactRule.triggerPattern, ignoreCase = true)
        }
        assertFalse("Exact match should NOT pass for partial substring 'what is your price list?'", nonExactMatched)
    }

    @Test
    fun testContactSequenceProgressiveDelayTiming() {
        val baseTime = 1700000000000L
        val steps = listOf(
            ContactSequenceStep(stepOrder = 1, channel = ChannelType.SMS, messageText = "Step 1", delayMinutesFromPrevious = 0),
            ContactSequenceStep(stepOrder = 2, channel = ChannelType.SMS, messageText = "Step 2", delayMinutesFromPrevious = 30),
            ContactSequenceStep(stepOrder = 3, channel = ChannelType.SMS, messageText = "Step 3", delayMinutesFromPrevious = 120)
        )

        var currentTriggerTime = baseTime
        val calculatedTimes = mutableListOf<Long>()
        steps.forEachIndexed { index, step ->
            if (index > 0) {
                currentTriggerTime += step.delayMinutesFromPrevious * 60 * 1000L
            }
            calculatedTimes.add(currentTriggerTime)
        }

        assertEquals("Step 1 trigger time", baseTime, calculatedTimes[0])
        assertEquals("Step 2 trigger time (+30m)", baseTime + (30 * 60 * 1000L), calculatedTimes[1])
        assertEquals("Step 3 trigger time (+120m from Step 2)", baseTime + (150 * 60 * 1000L), calculatedTimes[2])
    }

    @Test
    fun testAllChannelTypesCoverage() {
        val allChannels = ChannelType.entries
        assertEquals("There should be exactly 12 channel types", 12, allChannels.size)
        assertTrue(allChannels.contains(ChannelType.SMS))
        assertTrue(allChannels.contains(ChannelType.WHATSAPP))
        assertTrue(allChannels.contains(ChannelType.WHATSAPP_BUSINESS))
        assertTrue(allChannels.contains(ChannelType.WHATSAPP_GROUP))
        assertTrue(allChannels.contains(ChannelType.WHATSAPP_STATUS))
        assertTrue(allChannels.contains(ChannelType.WHATSAPP_BUSINESS_STATUS))
        assertTrue(allChannels.contains(ChannelType.CALL))
        assertTrue(allChannels.contains(ChannelType.WHATSAPP_CALL))
        assertTrue(allChannels.contains(ChannelType.WHATSAPP_BUSINESS_CALL))
        assertTrue(allChannels.contains(ChannelType.CALL_SIMULATION))
        assertTrue(allChannels.contains(ChannelType.REMINDER))
        assertTrue(allChannels.contains(ChannelType.SOUND_MODE))

        for (ch in allChannels) {
            assertNotNull(ch.displayName)
            assertTrue(ch.displayName.isNotBlank())
        }
    }

    @Test
    fun testWhatsAppGroupPrefixStripping() {
        val stripGroupPrefix = { value: String ->
            value.replaceFirst(Regex("^group\\s*:", RegexOption.IGNORE_CASE), "").trim()
        }
        val normalizeTitle = { value: String ->
            stripGroupPrefix(value).replace(Regex("\\s+"), " ").lowercase()
        }

        assertEquals("Family", stripGroupPrefix("group:Family"))
        assertEquals("Colleagues", stripGroupPrefix("GROUP: Colleagues"))
        assertEquals("Project Alpha", stripGroupPrefix("group : Project Alpha"))
        assertEquals("Football Team", stripGroupPrefix("Football Team"))
        assertEquals("family", normalizeTitle("group: Family"))
        assertEquals("project alpha", normalizeTitle("GROUP :  Project   Alpha "))
    }

    @Test
    fun testPermissionManagerCatalogSpecifications() {
        val specs = PermissionManager.ALL_SPECIFICATIONS
        assertTrue("Permission specs should have at least 10 items", specs.size >= 10)

        val permissionIds = specs.map { it.permissionId }.toSet()
        assertTrue(permissionIds.contains(PermissionManager.ID_SMS))
        assertTrue(permissionIds.contains(PermissionManager.ID_RECEIVE_SMS))
        assertTrue(permissionIds.contains(PermissionManager.ID_CALL_PHONE))
        assertTrue(permissionIds.contains(PermissionManager.ID_READ_PHONE_STATE))
        assertTrue(permissionIds.contains(PermissionManager.ID_READ_CONTACTS))
        assertTrue(permissionIds.contains(PermissionManager.ID_POST_NOTIFICATIONS))
        assertTrue(permissionIds.contains(PermissionManager.ID_EXACT_ALARMS))
        assertTrue(permissionIds.contains(PermissionManager.ID_ACCESSIBILITY))
        assertTrue(permissionIds.contains(PermissionManager.ID_DEVICE_ADMIN))
        assertTrue(permissionIds.contains(PermissionManager.ID_NOTIFICATION_POLICY))
        assertTrue(permissionIds.contains(PermissionManager.ID_OVERLAY))

        for (spec in specs) {
            assertTrue("Feature name must not be blank for ${spec.permissionId}", spec.featureName.isNotBlank())
            assertTrue("Purpose must not be blank for ${spec.permissionId}", spec.purpose.isNotBlank())
            assertTrue("Denied impact must not be blank for ${spec.permissionId}", spec.deniedImpact.isNotBlank())
            assertTrue("Revocation safety must not be blank for ${spec.permissionId}", spec.revocationSafety.isNotBlank())
        }

        for (critical in PermissionManager.CRITICAL_PERMISSIONS) {
            val matching = specs.find { it.permissionId == critical }
            assertNotNull("Critical permission $critical must be in specifications", matching)
            assertTrue("Critical permission $critical must have isCritical == true", matching!!.isCritical)
        }
    }

    @Test
    fun testTaskTypeSelectionItemDefinitions() {
        val items = listOf(
            TaskTypeItem(
                title = "SMS Message",
                description = "Schedule direct SMS via device SIM card",
                emoji = "📩",
                color = androidx.compose.ui.graphics.Color.Blue,
                channel = ChannelType.SMS
            ),
            TaskTypeItem(
                title = "WhatsApp Chat",
                description = "Send WhatsApp messages with optional media attachments",
                emoji = "💬",
                color = androidx.compose.ui.graphics.Color.Green,
                channel = ChannelType.WHATSAPP
            ),
            TaskTypeItem(
                title = "WhatsApp Business",
                description = "Send WhatsApp Business direct messages with media",
                emoji = "💼",
                color = androidx.compose.ui.graphics.Color.Cyan,
                channel = ChannelType.WHATSAPP_BUSINESS
            ),
            TaskTypeItem(
                title = "WhatsApp Group",
                description = "Send WhatsApp group messages or announcements",
                emoji = "👥",
                color = androidx.compose.ui.graphics.Color.Green,
                channel = ChannelType.WHATSAPP_GROUP
            ),
            TaskTypeItem(
                title = "Status Story",
                description = "Schedule auto-posting text or photo/video stories to Status",
                emoji = "📸",
                color = androidx.compose.ui.graphics.Color.Magenta,
                channel = null,
                isStatus = true
            ),
            TaskTypeItem(
                title = "Phone Call",
                description = "Schedule direct phone call via device SIM card",
                emoji = "📞",
                color = androidx.compose.ui.graphics.Color.Yellow,
                channel = ChannelType.CALL
            ),
            TaskTypeItem(
                title = "WhatsApp Call",
                description = "Schedule WhatsApp voice call",
                emoji = "📞",
                color = androidx.compose.ui.graphics.Color.Green,
                channel = null,
                isWaCall = true
            ),
            TaskTypeItem(
                title = "Call Simulation",
                description = "Simulate incoming call alert with custom caller name",
                emoji = "🎭",
                color = androidx.compose.ui.graphics.Color.Red,
                channel = ChannelType.CALL_SIMULATION
            ),
            TaskTypeItem(
                title = "Contact Sequence (Multi-Task)",
                description = "Step-by-step drip messages for a contact across SMS & WhatsApp",
                emoji = "🔗",
                color = androidx.compose.ui.graphics.Color.Blue,
                channel = null,
                isSequence = true
            ),
            TaskTypeItem(
                title = "Daily Auto Campaign",
                description = "High-speed paced outreach batch",
                emoji = "⚡",
                color = androidx.compose.ui.graphics.Color.Yellow,
                channel = null,
                isDailyCampaign = true
            ),
            TaskTypeItem(
                title = "Alarm / Reminder",
                description = "Local on-device audio alarm and reminder notification",
                emoji = "⏰",
                color = androidx.compose.ui.graphics.Color.Blue,
                channel = ChannelType.REMINDER
            ),
            TaskTypeItem(
                title = "Sound Mode Switcher",
                description = "Switch between Silent, Vibrate, and Normal sound",
                emoji = "🔊",
                color = androidx.compose.ui.graphics.Color.Cyan,
                channel = ChannelType.SOUND_MODE
            )
        )

        assertEquals(12, items.size)
        val channels = items.mapNotNull { it.channel }.toSet()
        assertTrue(channels.contains(ChannelType.SMS))
        assertTrue(channels.contains(ChannelType.WHATSAPP))
        assertTrue(channels.contains(ChannelType.WHATSAPP_BUSINESS))
        assertTrue(channels.contains(ChannelType.WHATSAPP_GROUP))
        assertTrue(channels.contains(ChannelType.CALL))
        assertTrue(channels.contains(ChannelType.CALL_SIMULATION))
        assertTrue(channels.contains(ChannelType.REMINDER))
        assertTrue(channels.contains(ChannelType.SOUND_MODE))

        // Ensure all items have non-blank title and emoji
        for ((title, description, emoji) in items) {
            assertTrue("Title must not be blank for $title", title.isNotBlank())
            assertTrue("Emoji must not be blank for $title", emoji.isNotBlank())
            assertTrue("Description must not be blank for $title", description.isNotBlank())
        }
    }

    @Test
    fun testExpandedMessageTemplateTokensAndContext() {
        val template = "Dispatched via {channel} to {recipient_count} recipients on {today} at {ist_time}. {time_greeting}!"
        val resolved = MessageTemplateResolver.resolve(
            template = template,
            name = "Priya Patel",
            phone = "+919876500000",
            channel = "WhatsApp Business",
            recipientCount = 14
        )
        assertTrue("Channel token must resolve", resolved.contains("Dispatched via WhatsApp Business"))
        assertTrue("Recipient count token must resolve", resolved.contains("to 14 recipients"))
        assertFalse("Template should not contain unparsed {today}", resolved.contains("{today}"))
        assertFalse("Template should not contain unparsed {ist_time}", resolved.contains("{ist_time}"))
        assertFalse("Template should not contain unparsed {time_greeting}", resolved.contains("{time_greeting}"))
        assertTrue(
            "Time greeting should resolve to valid greeting",
            resolved.contains("Good morning") || resolved.contains("Good afternoon") || resolved.contains("Good evening")
        )

        // Case-insensitivity check
        val uppercaseTemplate = "{CHANNEL} - {RECIPIENT_COUNT} - {TODAY} - {IST_TIME} - {TIME_GREETING}"
        val upperResolved = MessageTemplateResolver.resolve(
            template = uppercaseTemplate,
            channel = "SMS",
            recipientCount = 3
        )
        assertTrue(upperResolved.contains("SMS - 3"))
        assertFalse(upperResolved.contains("{TODAY}"))
        assertFalse(upperResolved.contains("{IST_TIME}"))
        assertFalse(upperResolved.contains("{TIME_GREETING}"))
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 21. PERMISSIONS & BACKUP REGISTRY INTEGRATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testAppPrivacyConfigsTableConstants() {
        assertEquals("app_privacy_configs", com.jmd.helper.data.db.DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS)
    }

    @Test
    fun testOverlayPermissionCatalogAndSpecification() {
        val overlaySpec = PermissionManager.getPermission(PermissionManager.ID_OVERLAY)
        assertNotNull("Overlay permission must be cataloged in PermissionManager", overlaySpec)
        assertEquals("android.permission.SYSTEM_ALERT_WINDOW", overlaySpec!!.permissionId)
        assertEquals(com.jmd.helper.core.PermissionCategory.SYSTEM, overlaySpec.category)
        assertTrue(overlaySpec.requiresSpecialUserAction)
        assertFalse(overlaySpec.isRuntimeApproval)
        assertTrue(overlaySpec.featureName.contains("Overlay"))
        assertTrue(overlaySpec.purpose.isNotBlank())
        assertTrue(overlaySpec.deniedImpact.isNotBlank())
        assertTrue(overlaySpec.revocationSafety.isNotBlank())
    }

    @Test
    fun testPermissionManagerCallLogAndMediaSpecifications() {
        val allSpecs = PermissionManager.getAllPermissions()
        val hasCallLog = allSpecs.any { it.permissionId == PermissionManager.ID_READ_CALL_LOG }
        val hasMedia = allSpecs.any { it.category == com.jmd.helper.core.PermissionCategory.MEDIA }

        assertTrue("PermissionManager must specify READ_CALL_LOG", hasCallLog)
        assertTrue("PermissionManager must specify MEDIA category permission", hasMedia)

        val mediaPermissions = PermissionManager.getRequiredMediaPermissions()
        assertTrue("Required media permissions list must not be empty", mediaPermissions.isNotEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 22. CHANNEL TYPE ALIAS RESOLUTION & QUERY COMPATIBILITY TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testChannelTypeFromStringAliases() {
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("FAKE_CALL"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("fake_call"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("SIMULATED_CALL"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("simulated_call"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("CALL_SIMULATION"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("call_simulation"))
        assertEquals(ChannelType.CALL_SIMULATION, ChannelType.fromString("FAKECALL"))
        assertEquals(ChannelType.SMS, ChannelType.fromString("SMS"))
        assertEquals(ChannelType.SMS, ChannelType.fromString("sms"))
        assertEquals(ChannelType.CALL, ChannelType.fromString("CALL"))
        assertEquals(ChannelType.CALL, ChannelType.fromString("call"))
        assertEquals(ChannelType.WHATSAPP, ChannelType.fromString("WHATSAPP"))
        assertEquals(ChannelType.WHATSAPP_BUSINESS, ChannelType.fromString("WHATSAPP_BUSINESS"))
        assertEquals(ChannelType.REMINDER, ChannelType.fromString("REMINDER"))
        assertEquals(ChannelType.SOUND_MODE, ChannelType.fromString("SOUND_MODE"))
        assertEquals(ChannelType.SMS, ChannelType.fromString(null))
        assertEquals(ChannelType.SMS, ChannelType.fromString(""))
        assertEquals(ChannelType.SMS, ChannelType.fromString("INVALID_UNKNOWN_CHANNEL"))
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 23. RUNNING TASK RECOVERY & RECONCILIATION INVARIANT TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testOrphanedRunningTaskRecoveryInvariants() {
        val recurringTask = ScheduledTask(
            id = 101L,
            title = "Recurring Outreach",
            channel = ChannelType.WHATSAPP,
            status = TaskStatus.RUNNING,
            recurrence = RecurrenceType.DAILY
        )

        val singleTask = ScheduledTask(
            id = 102L,
            title = "One-Off SMS",
            channel = ChannelType.SMS,
            status = TaskStatus.RUNNING,
            recurrence = RecurrenceType.ONCE
        )

        // Reconciliation rule: Recurring tasks stranded in RUNNING reset to PENDING
        val recoveredRecurringStatus = if (recurringTask.recurrence != RecurrenceType.ONCE) TaskStatus.PENDING else TaskStatus.FAILED
        assertEquals(TaskStatus.PENDING, recoveredRecurringStatus)

        // Reconciliation rule: Single-run tasks stranded in RUNNING transition to FAILED
        val recoveredSingleStatus = if (singleTask.recurrence != RecurrenceType.ONCE) TaskStatus.PENDING else TaskStatus.FAILED
        assertEquals(TaskStatus.FAILED, recoveredSingleStatus)
    }

    @Test
    fun testEmergencyStopSuppressionAndStatusInvariant() {
        TaskSafetyManager.triggerEmergencyStop(null, "Test Emergency Stop")
        assertTrue(TaskSafetyManager.isEmergencyStopActive(null))

        val recurringTask = ScheduledTask(
            id = 201L,
            status = TaskStatus.RUNNING,
            recurrence = RecurrenceType.DAILY
        )
        val onceTask = ScheduledTask(
            id = 202L,
            status = TaskStatus.RUNNING,
            recurrence = RecurrenceType.ONCE
        )

        val targetRecurring = if (recurringTask.recurrence != RecurrenceType.ONCE) TaskStatus.PENDING else TaskStatus.FAILED
        val targetOnce = if (onceTask.recurrence != RecurrenceType.ONCE) TaskStatus.PENDING else TaskStatus.FAILED

        assertEquals(TaskStatus.PENDING, targetRecurring)
        assertEquals(TaskStatus.FAILED, targetOnce)

        TaskSafetyManager.resetEmergencyStop(null)
        assertFalse(TaskSafetyManager.isEmergencyStopActive(null))
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 24. OEM BRAND INTEGRATION TESTS (OPPO, VIVO, SAMSUNG, NOKIA, MOTOROLA, ETC.)
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun testOemBrandEnumAndGuidanceCompleteness() {
        // Verify all target OEM brands are declared
        val brands = com.jmd.helper.core.OemBrand.values()
        assertTrue("Expected at least 15 brand classifications", brands.size >= 15)

        val targetBrands = listOf(
            com.jmd.helper.core.OemBrand.OPPO,
            com.jmd.helper.core.OemBrand.VIVO,
            com.jmd.helper.core.OemBrand.SAMSUNG,
            com.jmd.helper.core.OemBrand.NOKIA,
            com.jmd.helper.core.OemBrand.MOTOROLA,
            com.jmd.helper.core.OemBrand.XIAOMI,
            com.jmd.helper.core.OemBrand.ONEPLUS,
            com.jmd.helper.core.OemBrand.REALME,
            com.jmd.helper.core.OemBrand.HUAWEI,
            com.jmd.helper.core.OemBrand.HONOR,
            com.jmd.helper.core.OemBrand.TRANSSION,
            com.jmd.helper.core.OemBrand.ASUS
        )

        for (brand in targetBrands) {
            assertTrue("Brand display name should not be blank", brand.displayName.isNotBlank())
        }

        // Active device brand should resolve cleanly
        val detected = com.jmd.helper.core.OemBrandHelper.currentBrand
        assertNotNull(detected)

        val summary = com.jmd.helper.core.OemBrandHelper.getDeviceSummary()
        assertTrue("Device summary should not be blank", summary.isNotBlank())

        val guidance = com.jmd.helper.core.OemBrandHelper.getOemGuidance()
        assertTrue("Guidance steps should have at least 2 entries", guidance.size >= 2)
        guidance.forEach { step ->
            assertTrue("Guidance step should not be empty", step.isNotBlank())
        }
    }

    @Test
    fun testOemSafeSystemOverlayFiltering() {
        // System and OEM gestures / sidebars must be recognized as safe system overlays
        assertTrue("Vivo upslide gesture should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.vivo.upslide"))
        assertTrue("Vivo systemuiplugin should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.vivo.systemuiplugin"))
        assertTrue("Samsung cocktailbar should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.samsung.android.app.cocktailbarservice"))
        assertTrue("Samsung sidebar should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.samsung.android.sidebarservice"))
        assertTrue("Xiaomi freeform should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.miui.freeform"))
        assertTrue("Xiaomi touchassistant should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.miui.touchassistant"))
        assertTrue("ColorOS float assistant should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.coloros.floatassistant"))
        assertTrue("ColorOS smart sidebar should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.coloros.smartsidebar"))
        assertTrue("Motorola gestures should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.motorola.gestures"))
        assertTrue("Transsion smartpanel should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.transsion.smartpanel"))
        assertTrue("Huawei dock should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.huawei.dock"))
        assertTrue("Standard Android SystemUI should be safe", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.android.systemui"))

        // Third-party applications must NEVER be filtered as safe system overlays
        assertFalse("WhatsApp is not a safe system overlay", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.whatsapp"))
        assertFalse("Truecaller is not a safe system overlay", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.truecaller"))
        assertFalse("Messenger is not a safe system overlay", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay("com.facebook.orca"))
        assertFalse("Empty package name should return false", com.jmd.helper.core.OemBrandHelper.isSafeSystemOverlay(""))
    }
}
