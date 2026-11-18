package com.jmd.superapp.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for SMS segment splitting and quota calculations.
 */
class SmsQuotaManagerTest {

    @Test
    fun `empty message counts as 1 segment`() {
        assertEquals(1, SmsQuotaManager.calculateSmsSegments("", null))
    }

    @Test
    fun `standard ascii under 160 chars is 1 segment`() {
        val text = "Hello this is a short test SMS message under 160 characters."
        assertEquals(1, SmsQuotaManager.calculateSmsSegments(text, null))
    }

    @Test
    fun `standard ascii over 160 chars splits into multiple segments`() {
        val text = "A".repeat(161)
        assertEquals(2, SmsQuotaManager.calculateSmsSegments(text, null))
    }

    @Test
    fun `unicode under 70 chars is 1 segment`() {
        val text = "नमस्ते, यह एक परीक्षण संदेश है।" // Hindi Unicode
        assertEquals(1, SmsQuotaManager.calculateSmsSegments(text, null))
    }

    @Test
    fun `unicode over 70 chars splits into multiple segments`() {
        val text = "नमस्ते".repeat(20) // 120 unicode characters
        assertEquals(2, SmsQuotaManager.calculateSmsSegments(text, null))
    }
}
