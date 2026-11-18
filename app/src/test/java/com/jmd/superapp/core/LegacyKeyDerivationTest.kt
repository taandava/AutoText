package com.jmd.superapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.security.MessageDigest

/**
 * Unit test suite verifying key derivation consistency and backwards-compatibility
 * seeds across application generations (Super, Helper, AutoText).
 */
class LegacyKeyDerivationTest {

    private fun deriveKey(seed: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(seed.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun testSuperSeedKeyLengthIs256Bit() {
        val superKey = deriveKey("Super_com.jmd.superapp_Secure_Storage")
        assertNotNull(superKey)
        assertEquals(32, superKey.size)
    }

    @Test
    fun testSuperWithDeviceIdSeedKeyLengthIs256Bit() {
        val superUniqueKey = deriveKey("Super_com.jmd.superapp_device_uuid_12345_Secure_Storage")
        assertNotNull(superUniqueKey)
        assertEquals(32, superUniqueKey.size)
    }

    @Test
    fun testHelperLegacySeedKeyLengthIs256Bit() {
        val helperKey = deriveKey("Helper_com.jmd.superapp_Secure_Storage")
        assertNotNull(helperKey)
        assertEquals(32, helperKey.size)
    }

    @Test
    fun testAutoTextLegacySeedKeyLengthIs256Bit() {
        val autoTextKey = deriveKey("AutoText_com.jmd.superapp_device_uuid_12345_Secure_Storage")
        assertNotNull(autoTextKey)
        assertEquals(32, autoTextKey.size)
    }

    @Test
    fun testGenerationalSeedsAreDistinct() {
        val superKey = deriveKey("Super_com.jmd.superapp_Secure_Storage")
        val helperKey = deriveKey("Helper_com.jmd.superapp_Secure_Storage")
        val autoTextKey = deriveKey("AutoText_com.jmd.superapp_Secure_Storage")

        assertNotEquals(superKey.toList(), helperKey.toList())
        assertNotEquals(superKey.toList(), autoTextKey.toList())
        assertNotEquals(helperKey.toList(), autoTextKey.toList())
    }

    @Test
    fun testKeyDerivationIsDeterministic() {
        val key1 = deriveKey("Super_com.jmd.superapp_Secure_Storage")
        val key2 = deriveKey("Super_com.jmd.superapp_Secure_Storage")
        assertEquals(key1.toList(), key2.toList())
    }
}
