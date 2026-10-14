package com.jmd.autotext

import com.jmd.autotext.util.PhoneNumberNormalizer
import org.junit.Assert.*
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun testNormalizeStandardIndianNumber() {
        // Standard 10 digits
        val normalized = PhoneNumberNormalizer.normalize("9876543210", "+91")
        assertEquals("+919876543210", normalized)

        // With leading 0 (11 digits)
        val withZero = PhoneNumberNormalizer.normalize("09876543210", "+91")
        assertEquals("+919876543210", withZero)

        // Already with 91 (12 digits)
        val with91 = PhoneNumberNormalizer.normalize("919876543210", "+91")
        assertEquals("+919876543210", with91)

        // Already with +91
        val withPlus91 = PhoneNumberNormalizer.normalize("+91 98765 43210", "+91")
        assertEquals("+919876543210", withPlus91)
    }

    @Test
    fun testNormalizeWithFormattingChars() {
        val formatted = PhoneNumberNormalizer.normalize("+91 (987) 654-3210", "+91")
        assertEquals("+919876543210", formatted)

        val dashed = PhoneNumberNormalizer.normalize("9876-543-210", "+91")
        assertEquals("+919876543210", dashed)
    }

    @Test
    fun testFormatForWhatsApp() {
        val waNumber = PhoneNumberNormalizer.formatForWhatsApp("+91 98765 43210", "+91")
        assertEquals("919876543210", waNumber)

        val wa10Digit = PhoneNumberNormalizer.formatForWhatsApp("9876543210", "+91")
        assertEquals("919876543210", wa10Digit)

        val waInternational = PhoneNumberNormalizer.formatForWhatsApp("+1 555 123 4567", "+1")
        assertEquals("15551234567", waInternational)
    }

    @Test
    fun testIsMobileNumberIndianValid() {
        assertTrue(PhoneNumberNormalizer.isMobileNumber("9876543210"))
        assertTrue(PhoneNumberNormalizer.isMobileNumber("8876543210"))
        assertTrue(PhoneNumberNormalizer.isMobileNumber("7876543210"))
        assertTrue(PhoneNumberNormalizer.isMobileNumber("6876543210"))
        assertTrue(PhoneNumberNormalizer.isMobileNumber("+919876543210"))
        assertTrue(PhoneNumberNormalizer.isMobileNumber("09876543210"))
    }

    @Test
    fun testIsMobileNumberRejections() {
        // Alphanumeric sender ID
        assertFalse(PhoneNumberNormalizer.isMobileNumber("VM-HDFCBK"))
        assertFalse(PhoneNumberNormalizer.isMobileNumber("AIRTEL"))

        // Short codes
        assertFalse(PhoneNumberNormalizer.isMobileNumber("121"))
        assertFalse(PhoneNumberNormalizer.isMobileNumber("198"))

        // Toll-free
        assertFalse(PhoneNumberNormalizer.isMobileNumber("1800111222"))
        assertFalse(PhoneNumberNormalizer.isMobileNumber("1860123456"))

        // Dummy repeated digits
        assertFalse(PhoneNumberNormalizer.isMobileNumber("0000000000"))
        assertFalse(PhoneNumberNormalizer.isMobileNumber("1111111111"))

        // Indian landline (starts with 011, 022, etc., not starting with 6-9 in 10-digit national format)
        assertFalse(PhoneNumberNormalizer.isMobileNumber("0112345678"))

        // Blank / null
        assertFalse(PhoneNumberNormalizer.isMobileNumber(""))
        assertFalse(PhoneNumberNormalizer.isMobileNumber(null))
    }

    @Test
    fun testIsMobileNumberInternationalValid() {
        // UK (+44) mobile starting with 7
        assertTrue(PhoneNumberNormalizer.isMobileNumber("+447911123456"))
        assertFalse(PhoneNumberNormalizer.isMobileNumber("+441211123456")) // UK landline (0121)

        // UAE (+971) mobile starting with 5
        assertTrue(PhoneNumberNormalizer.isMobileNumber("+971501234567"))
        assertFalse(PhoneNumberNormalizer.isMobileNumber("+971201234567")) // UAE landline (02)

        // Australia (+61) mobile starting with 4
        assertTrue(PhoneNumberNormalizer.isMobileNumber("+61412345678"))
        assertFalse(PhoneNumberNormalizer.isMobileNumber("+61212345678")) // Australia landline (02)

        // US (+1) mobile vs toll-free
        assertTrue(PhoneNumberNormalizer.isMobileNumber("+14155552671"))
        assertFalse(PhoneNumberNormalizer.isMobileNumber("+18005551234")) // US toll free 800
    }
}
