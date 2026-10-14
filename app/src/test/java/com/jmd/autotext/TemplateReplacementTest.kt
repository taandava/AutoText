package com.jmd.autotext

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateReplacementTest {

    private fun applyTemplate(template: String, name: String, phone: String, date: String, time: String): String {
        return template
            .replace("{name}", name, ignoreCase = true)
            .replace("{first_name}", (name.split(" ").firstOrNull() ?: name), ignoreCase = true)
            .replace("{phone}", phone, ignoreCase = true)
            .replace("{date}", date, ignoreCase = true)
            .replace("{time}", time, ignoreCase = true)
    }

    @Test
    fun testNameAndPhonePlaceholderReplacement() {
        val template = "Hello {name}, your registered number is {phone}."
        val result = applyTemplate(template, "Rahul Sharma", "+919876543210", "10/10/2026", "10:00 AM")
        assertEquals("Hello Rahul Sharma, your registered number is +919876543210.", result)
    }

    @Test
    fun testFirstNamePlaceholderReplacement() {
        val template = "Hi {first_name}, how are you?"
        val result = applyTemplate(template, "Priya Patel", "+919876543210", "10/10/2026", "10:00 AM")
        assertEquals("Hi Priya, how are you?", result)
    }

    @Test
    fun testDateTimePlaceholderReplacement() {
        val template = "Meeting confirmed on {date} at {time}."
        val result = applyTemplate(template, "Amit", "+919876543210", "15 Oct 2026", "03:30 PM")
        assertEquals("Meeting confirmed on 15 Oct 2026 at 03:30 PM.", result)
    }

    @Test
    fun testCaseInsensitivePlaceholders() {
        val template = "Hey {NAME}, reminder for {TIME}."
        val result = applyTemplate(template, "John", "+15551234567", "10/10/2026", "09:00 AM")
        assertEquals("Hey John, reminder for 09:00 AM.", result)
    }
}
