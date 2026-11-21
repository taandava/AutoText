package com.jmd.superapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MessageTemplateResolverTest {

    @Test
    fun testNameSubstitution() {
        val template = "Hello {name}, your task is ready."
        val resolved = MessageTemplateResolver.resolve(
            template = template,
            phone = "+919876543210",
            name = "Rahul Sharma"
        )
        assertEquals("Hello Rahul Sharma, your task is ready.", resolved)
    }

    @Test
    fun testEmptyNameSubstitution() {
        val template = "Notice for {name} regarding account."
        val resolved = MessageTemplateResolver.resolve(
            template = template,
            phone = "+919876543210",
            name = ""
        )
        assertEquals("Notice for  regarding account.", resolved)
    }

    @Test
    fun testTokensCatalogContainsName() {
        assertTrue(MessageVariables.MESSAGE.contains("{name}"))
        assertTrue(MessageVariables.REFERENCE.any { it.token == "{name}" })
    }

    @Test
    fun testDateFormatResolution() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 21, 14, 30, 0)
        }
        val timestamp = cal.timeInMillis

        val resolved = MessageTemplateResolver.resolve(
            template = "{date}",
            targetTimestamp = timestamp
        )
        // Default pattern dd/MMM/yyyy
        assertTrue(resolved.contains("21") && resolved.contains("2026"))
    }

    @Test
    fun testTimeFormatResolution() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 21, 14, 30, 0)
        }
        val timestamp = cal.timeInMillis

        val resolved = MessageTemplateResolver.resolve(
            template = "{time}",
            targetTimestamp = timestamp
        )
        // 14:30 in 12hr format is 02:30 PM (or locale specific PM)
        assertTrue(resolved.contains("02:30") || resolved.contains("2:30") || resolved.contains("14:30"))
    }

    @Test
    fun testWeekdayAndPhoneSubstitution() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 21, 14, 30, 0) // Saturday
        }
        val timestamp = cal.timeInMillis

        val template = "Day: {day}, Contact: {phone}"
        val resolved = MessageTemplateResolver.resolve(
            template = template,
            phone = "+91 98765-43210",
            targetTimestamp = timestamp
        )
        assertTrue(resolved.contains("Saturday"))
        assertTrue(resolved.contains("9876543210"))
    }

    @Test
    fun testCaseInsensitiveTokens() {
        val template = "{NAME} - {GREETING} - {RUPEE} - {DATE} - {TIME} - {DAY} - {PHONE}"
        val resolved = MessageTemplateResolver.resolve(
            template = template,
            phone = "+91 98765 43210",
            name = "Amit"
        )
        assertTrue(resolved.contains("Amit"))
        assertTrue(!resolved.contains("{NAME}"))
        assertTrue(!resolved.contains("{GREETING}"))
        assertTrue(!resolved.contains("{RUPEE}"))
        assertTrue(!resolved.contains("{DATE}"))
        assertTrue(!resolved.contains("{TIME}"))
        assertTrue(!resolved.contains("{DAY}"))
        assertTrue(!resolved.contains("{PHONE}"))
    }
}
