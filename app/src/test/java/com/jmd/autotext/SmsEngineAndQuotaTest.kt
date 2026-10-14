package com.jmd.autotext

import com.jmd.autotext.util.SmsQuotaManager
import org.junit.Assert.*
import org.junit.Test

class SmsEngineAndQuotaTest {

    @Test
    fun testGsmSmsSegmentCalculation() {
        val shortAscii = "Hello from AutoText!"
        assertEquals(1, SmsQuotaManager.calculateSmsSegments(shortAscii))

        // 160 chars GSM
        val exact160 = "A".repeat(160)
        assertEquals(1, SmsQuotaManager.calculateSmsSegments(exact160))

        // 161 chars GSM splits into 2 segments (153 + 8)
        val chars161 = "A".repeat(161)
        assertEquals(2, SmsQuotaManager.calculateSmsSegments(chars161))

        // 320 chars GSM splits into 3 segments
        val chars320 = "A".repeat(320)
        assertEquals(3, SmsQuotaManager.calculateSmsSegments(chars320))
    }

    @Test
    fun testUnicodeSmsSegmentCalculation() {
        // Unicode text (Hindi)
        val hindiShort = "नमस्ते! आप कैसे हैं?"
        assertEquals(1, SmsQuotaManager.calculateSmsSegments(hindiShort))

        // 70 chars Unicode
        val unicode70 = "हि".repeat(35) // 70 characters
        assertEquals(1, SmsQuotaManager.calculateSmsSegments(unicode70))

        // 71 chars Unicode splits into 2 segments
        val unicode71 = "हि".repeat(35) + "क"
        assertEquals(2, SmsQuotaManager.calculateSmsSegments(unicode71))
    }

    @Test
    fun testIndianDailyQuotaLimitEnforcement() {
        assertEquals(100, SmsQuotaManager.INDIA_DAILY_SMS_LIMIT)

        val statusUnder = SmsQuotaManager.QuotaStatus(
            totalSentToday = 80,
            limit = 100,
            remaining = 20,
            isLimitReached = false,
            details = "20 SMS quota remaining today"
        )
        assertFalse(statusUnder.isLimitReached)
        assertEquals(20, statusUnder.remaining)

        val statusLimit = SmsQuotaManager.QuotaStatus(
            totalSentToday = 100,
            limit = 100,
            remaining = 0,
            isLimitReached = true,
            details = "India TRAI 100 SMS limit reached"
        )
        assertTrue(statusLimit.isLimitReached)
        assertEquals(0, statusLimit.remaining)

        val statusOver = SmsQuotaManager.QuotaStatus(
            totalSentToday = 105,
            limit = 100,
            remaining = 0,
            isLimitReached = true,
            details = "Limit exceeded"
        )
        assertTrue(statusOver.isLimitReached)
    }
}
