package com.jmd.autotext

import com.jmd.autotext.model.*
import com.jmd.autotext.util.PhoneNumberNormalizer
import com.jmd.autotext.util.SmsQuotaManager
import org.junit.Assert.*
import org.junit.Test

class ComprehensiveArchitectureAndFeaturesTest {

    @Test
    fun testSpecificUserTestNumbers() {
        val num1 = "9500910092"
        val num2 = "9930604542"

        // 1. Normalization
        assertEquals("+919500910092", PhoneNumberNormalizer.normalize(num1, "+91"))
        assertEquals("+919930604542", PhoneNumberNormalizer.normalize(num2, "+91"))

        // 2. WhatsApp Deep Link formatting
        assertEquals("919500910092", PhoneNumberNormalizer.formatForWhatsApp(num1, "+91"))
        assertEquals("919930604542", PhoneNumberNormalizer.formatForWhatsApp(num2, "+91"))

        // 3. Indian Mobile Number Validation
        assertTrue(PhoneNumberNormalizer.isMobileNumber(num1))
        assertTrue(PhoneNumberNormalizer.isMobileNumber(num2))
        assertTrue(PhoneNumberNormalizer.isMobileNumber("+91 95009 10092"))
        assertTrue(PhoneNumberNormalizer.isMobileNumber("+91 99306 04542"))
    }

    @Test
    fun testScheduledTaskAndPersonalizationWithTestNumbers() {
        val num1 = "9500910092"
        val num2 = "9930604542"

        fun personalize(template: String, name: String, phone: String, date: String, time: String): String {
            return template
                .replace("{name}", name, ignoreCase = true)
                .replace("{first_name}", (name.split(" ").firstOrNull() ?: name), ignoreCase = true)
                .replace("{phone}", phone, ignoreCase = true)
                .replace("{date}", date, ignoreCase = true)
                .replace("{time}", time, ignoreCase = true)
        }

        val templateText = "Hello {name}! Important update regarding your request. Please call back at {phone}."

        val personalized1 = personalize(templateText, "Rajesh Kumar", num1, "12 Oct 2026", "11:00 AM")
        assertEquals("Hello Rajesh Kumar! Important update regarding your request. Please call back at 9500910092.", personalized1)
        assertEquals(1, SmsQuotaManager.calculateSmsSegments(personalized1))

        val personalized2 = personalize(templateText, "Priya Sharma", num2, "12 Oct 2026", "02:30 PM")
        assertEquals("Hello Priya Sharma! Important update regarding your request. Please call back at 9930604542.", personalized2)
        assertEquals(1, SmsQuotaManager.calculateSmsSegments(personalized2))
    }

    @Test
    fun testAutoReplyMatchingForTestNumbers() {
        val rule = AutoReplyRule(
            id = 100L,
            name = "Test Number Responder",
            channel = ChannelType.SMS,
            replyText = "Thanks for contacting us! We have received your query.",
            triggerPattern = "info, help, support",
            isExactMatch = false,
            startTimeHour = 0,
            startTimeMinute = 0,
            endTimeHour = 23,
            endTimeMinute = 59,
            maxDailyReplies = 3,
            isEnabled = true
        )

        fun matchesRule(r: AutoReplyRule, msg: String): Boolean {
            if (!r.isEnabled) return false
            val pat = r.triggerPattern.trim()
            if (pat == "*" || pat.isEmpty()) return true
            val keywords = pat.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val lowerMsg = msg.lowercase()
            return keywords.any { lowerMsg.contains(it) }
        }

        assertTrue(matchesRule(rule, "Hi, I need info about services."))
        assertTrue(matchesRule(rule, "Please provide support for my account."))
        assertFalse(matchesRule(rule, "Good morning, having coffee."))
    }

    @Test
    fun testWhatsAppGroupNameValidation() {
        fun isValidGroupName(name: String): Boolean {
            val trimmed = name.trim()
            if (trimmed.length !in 2..100) return false
            if (trimmed.contains("\n") || trimmed.contains("\r")) return false

            val stripped = trimmed.replace(Regex("""[\s+\-()]"""), "")
            return !(stripped.length >= 7 && stripped.all { it.isDigit() })
        }

        // Valid group names
        assertTrue(isValidGroupName("Project Alpha Group"))
        assertTrue(isValidGroupName("Family & Friends 2026 🎉"))
        assertTrue(isValidGroupName("Sales Team - North Region"))
        assertTrue(isValidGroupName("Tech Support"))

        // Invalid group names (individual phone numbers shown in WhatsApp chats or invalid length)
        assertFalse(isValidGroupName("A")) // Too short (<2)
        assertFalse(isValidGroupName("+91 91366 85544")) // Raw phone number
        assertFalse(isValidGroupName("+1 (555) 019-2834")) // Raw phone number
        assertFalse(isValidGroupName("9876543210")) // Raw digits
        assertFalse(isValidGroupName("Group with\nLinebreak")) // Contains newline
    }

    @Test
    fun testAutoForwardRuleMatching() {
        val rule = AutoForwardRule(
            id = 1L,
            name = "Bank OTP Forwarding",
            sourceType = "SMS",
            forwardToNumber = "+919500910092",
            filterSender = "HDFCBK",
            filterKeyword = "OTP",
            isEnabled = true
        )

        fun matches(r: AutoForwardRule, sender: String, message: String): Boolean {
            if (!r.isEnabled) return false
            val matchSender = r.filterSender.isEmpty() || sender.contains(r.filterSender, ignoreCase = true)
            val matchKeyword = r.filterKeyword.isEmpty() || message.contains(r.filterKeyword, ignoreCase = true)
            return matchSender && matchKeyword
        }

        // Matching sender and keyword
        assertTrue(matches(rule, "VM-HDFCBK", "Your OTP for transaction is 482910."))

        // Non-matching sender
        assertFalse(matches(rule, "VM-AIRTEL", "Your OTP for login is 123456."))

        // Non-matching keyword
        assertFalse(matches(rule, "VM-HDFCBK", "Your account balance statement for October."))
    }

    @Test
    fun testOvernightTimeWindowMatching() {
        fun inTimeWindow(startHour: Int, startMin: Int, endHour: Int, endMin: Int, currentHour: Int, currentMin: Int): Boolean {
            val startMinutes = startHour * 60 + startMin
            val endMinutes = endHour * 60 + endMin
            val currentMinutes = currentHour * 60 + currentMin

            return if (startMinutes <= endMinutes) {
                currentMinutes in startMinutes..endMinutes
            } else {
                currentMinutes >= startMinutes || currentMinutes <= endMinutes
            }
        }

        // Night Window: 22:00 (10 PM) to 07:00 (7 AM)
        assertTrue(inTimeWindow(22, 0, 7, 0, 23, 30)) // 11:30 PM -> Inside
        assertTrue(inTimeWindow(22, 0, 7, 0, 2, 15))  // 02:15 AM -> Inside
        assertTrue(inTimeWindow(22, 0, 7, 0, 7, 0))   // 07:00 AM -> Inside
        assertFalse(inTimeWindow(22, 0, 7, 0, 12, 0))  // 12:00 PM -> Outside

        // Daytime Window: 09:00 AM to 05:00 PM (17:00)
        assertTrue(inTimeWindow(9, 0, 17, 0, 10, 30)) // 10:30 AM -> Inside
        assertFalse(inTimeWindow(9, 0, 17, 0, 18, 0)) // 06:00 PM -> Outside
    }

    @Test
    fun testContactSequenceStepDelays() {
        val steps = listOf(
            ContactSequenceStep(stepOrder = 1, channel = ChannelType.SMS, messageText = "Step 1: Welcome!", delayMinutesFromPrevious = 0),
            ContactSequenceStep(stepOrder = 2, channel = ChannelType.WHATSAPP, messageText = "Step 2: Follow up", delayMinutesFromPrevious = 30),
            ContactSequenceStep(stepOrder = 3, channel = ChannelType.CALL, messageText = "Step 3: Call reminder", delayMinutesFromPrevious = 1440)
        )

        val startTime = 1000000000000L

        fun calculateStepTriggerTime(baseTime: Long, stepList: List<ContactSequenceStep>, targetStepOrder: Int): Long {
            var cumulativeDelay = 0L
            for (step in stepList) {
                if (step.stepOrder <= targetStepOrder) {
                    cumulativeDelay += step.delayMinutesFromPrevious * 60 * 1000L
                }
            }
            return baseTime + cumulativeDelay
        }

        assertEquals(startTime, calculateStepTriggerTime(startTime, steps, 1))
        assertEquals(startTime + 30 * 60 * 1000L, calculateStepTriggerTime(startTime, steps, 2))
        assertEquals(startTime + (30 + 1440) * 60 * 1000L, calculateStepTriggerTime(startTime, steps, 3))
    }

    @Test
    fun testExtendedPhoneNumberFormats() {
        // International 00 prefix
        assertEquals("+447911123456", PhoneNumberNormalizer.normalize("00447911123456", "+91"))

        // WhatsApp format removal of '+' and leading spaces
        assertEquals("447911123456", PhoneNumberNormalizer.formatForWhatsApp("+44 7911 123456", "+44"))

        // Australian number
        assertTrue(PhoneNumberNormalizer.isMobileNumber("+61 412 345 678"))
        // UAE number
        assertTrue(PhoneNumberNormalizer.isMobileNumber("+971 50 123 4567"))
    }
}
