package com.jmd.autotext

import com.jmd.autotext.model.*
import org.junit.Assert.*
import org.junit.Test

class ModelsAndRulesTest {

    @Test
    fun testScheduledTaskDefaultValues() {
        val task = ScheduledTask(
            title = "Test Reminder",
            channel = ChannelType.SMS,
            recipients = listOf("+919876543210"),
            messageText = "Hello"
        )

        assertEquals("Test Reminder", task.title)
        assertEquals(ChannelType.SMS, task.channel)
        assertEquals(TaskStatus.PENDING, task.status)
        assertEquals(RecurrenceType.ONCE, task.recurrence)
        assertFalse(task.askBeforeSend)
    }

    @Test
    fun testChannelTypesDisplayNames() {
        assertEquals("SMS", ChannelType.SMS.displayName)
        assertEquals("WhatsApp", ChannelType.WHATSAPP.displayName)
        assertEquals("WA Business", ChannelType.WHATSAPP_BUSINESS.displayName)
        assertEquals("Phone Call", ChannelType.CALL.displayName)
        assertEquals("Fake Call", ChannelType.FAKE_CALL.displayName)
        assertEquals("Reminder", ChannelType.REMINDER.displayName)
    }

    @Test
    fun testDailyCampaignConfig() {
        val smsConfig = DailyCampaignConfig(id = 1L, channel = "SMS", dailyLimit = 80)
        assertEquals(1L, smsConfig.id)
        assertEquals("SMS", smsConfig.channel)
        assertEquals(80, smsConfig.dailyLimit)

        val waConfig = DailyCampaignConfig(id = 2L, channel = "WHATSAPP", dailyLimit = 50)
        assertEquals(2L, waConfig.id)
        assertEquals("WHATSAPP", waConfig.channel)
        assertEquals(50, waConfig.dailyLimit)
    }

    @Test
    fun testAutoReplyRuleMatching() {
        fun matches(pattern: String, isExact: Boolean, incomingMessage: String): Boolean {
            val text = incomingMessage.trim()
            val pat = pattern.trim()
            if (pat == "*" || pat.isEmpty()) return true
            if (isExact) {
                return text.equals(pat, ignoreCase = true)
            }
            val keywords = pat.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            return keywords.any { text.lowercase().contains(it) }
        }

        // Wildcard
        assertTrue(matches("*", false, "Any incoming text here"))
        assertTrue(matches("", false, "Any incoming text"))

        // Exact match
        assertTrue(matches("Hello", true, "hello"))
        assertFalse(matches("Hello", true, "Hello World"))

        // Contains keywords
        assertTrue(matches("price, cost, rate", false, "What is the price of this service?"))
        assertTrue(matches("price, cost, rate", false, "how much does it cost?"))
        assertFalse(matches("price, cost, rate", false, "Good morning, how are you?"))
    }
}
