package com.jmd.superapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MessageTemplateResolverTest {

    @Test
    fun `resolve handles empty template cleanly`() {
        assertEquals("", MessageTemplateResolver.resolve(""))
    }

    @Test
    fun `resolve replaces name and first name correctly`() {
        val template = "Hello {name}, your first name is {first_name}!"
        val result = MessageTemplateResolver.resolve(
            template = template,
            name = "Aarav Sharma",
            phone = "+919167298798"
        )
        assertEquals("Hello Aarav Sharma, your first name is Aarav!", result)
    }

    @Test
    fun `resolve falls back to there when name is blank`() {
        val template = "Hi {name}!"
        val result = MessageTemplateResolver.resolve(template = template, name = "")
        assertEquals("Hi there!", result)
    }

    @Test
    fun `resolve replaces phone number`() {
        val template = "Contacting {phone}"
        val result = MessageTemplateResolver.resolve(
            template = template,
            phone = "+91 99306 04542"
        )
        assertEquals("Contacting +919930604542", result)
    }

    @Test
    fun `resolve replaces date and time tokens accurately`() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 17)
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        val timestamp = cal.timeInMillis

        val template = "Scheduled on {date_dd_mm_yyyy} ({day}) at {time_24h} for {channel}"
        val result = MessageTemplateResolver.resolve(
            template = template,
            name = "Test",
            targetTimestamp = timestamp,
            channel = "WhatsApp"
        )
        assertTrue("Expected 17-11-2026 in result: $result", result.contains("17-11-2026"))
        assertTrue("Expected Tuesday in result: $result", result.contains("Tuesday"))
        assertTrue("Expected 21:30 in result: $result", result.contains("21:30"))
        assertTrue("Expected WhatsApp in result: $result", result.contains("WhatsApp"))
    }

    @Test
    fun `resolve is case-insensitive for tokens`() {
        val template = "Hi {NAME}, today is {DATE_WORDS} with fee {RUPEE}500"
        val result = MessageTemplateResolver.resolve(
            template = template,
            name = "Kavita",
            phone = "9769602020"
        )
        assertTrue("Expected Kavita in result: $result", result.contains("Hi Kavita"))
        assertTrue("Expected Rupee symbol in result: $result", result.contains("₹500"))
    }
}
