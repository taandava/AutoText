package com.jmd.superapp.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure normalization logic that every recipient input flows through.
 */
class PhoneNumberNormalizerTest {

    @Test
    fun `ten digit indian number gains country code`() {
        assertEquals("+919876543210", PhoneNumberNormalizer.normalize("9876543210"))
    }

    @Test
    fun `leading zero national format is normalized`() {
        assertEquals("+919876543210", PhoneNumberNormalizer.normalize("09876543210"))
    }

    @Test
    fun `international prefix with spaces is normalized`() {
        assertEquals("+919876543210", PhoneNumberNormalizer.normalize("+91 98765 43210"))
    }

    @Test
    fun `double zero international prefix is normalized`() {
        assertEquals("+919876543210", PhoneNumberNormalizer.normalize("0091 98765 43210"))
    }

    @Test
    fun `whatsapp form strips the plus`() {
        assertEquals("919876543210", PhoneNumberNormalizer.formatForWhatsApp("9876543210"))
    }

    @Test
    fun `text without digits normalizes to empty`() {
        assertEquals("", PhoneNumberNormalizer.normalize("not a number"))
    }

    @Test
    fun `short alphanumeric sender is rejected as mobile`() {
        assertEquals(false, PhoneNumberNormalizer.isMobileNumber("VM-HDFCBK"))
    }

    @Test
    fun `user test numbers normalize correctly for SMS and WhatsApp`() {
        val testNumbers = listOf("9167298798", "9930604542", "9769602020")
        for (num in testNumbers) {
            val normalized = PhoneNumberNormalizer.normalize(num)
            val waFormat = PhoneNumberNormalizer.formatForWhatsApp(num)
            assertEquals("+91$num", normalized)
            assertEquals("91$num", waFormat)
            assertEquals(true, PhoneNumberNormalizer.isMobileNumber(num))
            assertEquals(true, PhoneNumberNormalizer.isMobileNumber(normalized))
        }
    }
}
