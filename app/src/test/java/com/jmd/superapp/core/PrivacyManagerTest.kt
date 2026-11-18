package com.jmd.superapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying PrivacyManager masking functions and base target packages.
 */
class PrivacyManagerTest {

    @Test
    fun testMaskSensitiveText_emptyAndBlank() {
        assertEquals("", PrivacyManager.maskSensitiveText(""))
        assertEquals("", PrivacyManager.maskSensitiveText("   "))
    }

    @Test
    fun testMaskSensitiveText_shortStrings() {
        assertEquals("**", PrivacyManager.maskSensitiveText("a"))
        assertEquals("**", PrivacyManager.maskSensitiveText("ab"))
    }

    @Test
    fun testMaskSensitiveText_normalStrings() {
        assertEquals("M***M", PrivacyManager.maskSensitiveText("Meeting at 5 PM"))
        assertEquals("H***o", PrivacyManager.maskSensitiveText("Hello"))
    }

    @Test
    fun testMaskPhoneNumber_emptyAndShort() {
        assertEquals("***", PrivacyManager.maskPhoneNumber(""))
        assertEquals("***", PrivacyManager.maskPhoneNumber("12345"))
    }

    @Test
    fun testMaskPhoneNumber_standard10Digit() {
        // "9876543210" -> "9876***210"
        assertEquals("9876***210", PrivacyManager.maskPhoneNumber("9876543210"))
    }

    @Test
    fun testMaskPhoneNumber_withCountryCode() {
        // "+919876543210" has 12 digits, core is last 10 digits "9876543210" -> "9876***210"
        assertEquals("9876***210", PrivacyManager.maskPhoneNumber("+919876543210"))
    }

    @Test
    fun testBaseTaskTargetPackages_containsTargetApps() {
        val targets = PrivacyManager.BASE_TASK_TARGET_PACKAGES
        assertTrue(targets.contains("com.whatsapp"))
        assertTrue(targets.contains("com.whatsapp.w4b"))
        assertTrue(targets.contains("android"))
        assertTrue(targets.contains("com.android.systemui"))
        assertTrue(targets.contains("com.google.android.dialer"))
        // Crucial invariant: never contains banking or payment app packages
        assertFalse(targets.contains("com.google.android.apps.nbu.paisa.user"))
        assertFalse(targets.contains("net.one97.paytm"))
    }
}
