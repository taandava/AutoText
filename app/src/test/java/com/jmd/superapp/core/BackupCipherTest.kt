package com.jmd.superapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for backup envelope encryption, PBKDF2 passphrase protection, header validation, and plaintext fallback.
 */
class BackupCipherTest {

    @Test
    fun `isEncrypted detects both V1 and V2 magic headers`() {
        assertTrue(BackupCipher.isEncrypted("SUPERBAK1:AQIDBA=="))
        assertTrue(BackupCipher.isEncrypted("   SUPERBAK1:AQIDBA=="))
        assertTrue(BackupCipher.isEncrypted("SUPERBAK2:c2FsdA==:aXY=:Y2lwaGVy"))
        assertTrue(BackupCipher.isEncrypted("   SUPERBAK2:c2FsdA==:aXY=:Y2lwaGVy"))
    }

    @Test
    fun `isPassphraseProtected correctly identifies V2 envelopes`() {
        assertTrue(BackupCipher.isPassphraseProtected("SUPERBAK2:c2FsdA==:aXY=:Y2lwaGVy"))
        assertFalse(BackupCipher.isPassphraseProtected("SUPERBAK1:AQIDBA=="))
        assertFalse(BackupCipher.isPassphraseProtected("{\"version\": 2}"))
    }

    @Test
    fun `isEncrypted returns false for plaintext json`() {
        assertFalse(BackupCipher.isEncrypted("{\"version\": 2, \"app\": \"Super\"}"))
        assertFalse(BackupCipher.isEncrypted(""))
        assertFalse(BackupCipher.isEncrypted("   "))
    }

    @Test
    fun `decryptIfNeeded returns plaintext json unchanged`() {
        val plain = "{\"tasks\": [], \"contacts\": []}"
        assertEquals(plain, BackupCipher.decryptIfNeeded(plain))
    }

    @Test
    fun `passphrase encryption and decryption roundtrip succeeds`() {
        val payload = "{\"tasks\": [{\"id\": 1, \"title\": \"Test Meeting\"}]}"
        val passphrase = "MySecretPassphrase#2026!"

        val encrypted = BackupCipher.encryptWithPassphrase(payload, passphrase)
        assertTrue(encrypted.startsWith(BackupCipher.MAGIC_V2))
        assertTrue(BackupCipher.isEncrypted(encrypted))
        assertTrue(BackupCipher.isPassphraseProtected(encrypted))

        val decrypted = BackupCipher.decryptIfNeeded(encrypted, passphrase)
        assertEquals(payload, decrypted)
    }

    @Test
    fun `passphrase decryption fails when wrong passphrase provided`() {
        val payload = "{\"sensitive\": \"data\"}"
        val correctPass = "CorrectPassword123"
        val wrongPass = "WrongPassword456"

        val encrypted = BackupCipher.encryptWithPassphrase(payload, correctPass)

        assertThrows(InvalidPassphraseException::class.java) {
            BackupCipher.decryptIfNeeded(encrypted, wrongPass)
        }
    }

    @Test
    fun `default portable encryption roundtrip succeeds without custom passphrase`() {
        val payload = "{\"auto_reply\": [{\"id\": 10, \"keyword\": \"hello\"}]}"

        // When AndroidKeyStore is missing in JVM unit tests, encrypt() transparently falls back to portable envelope
        val encrypted = BackupCipher.encrypt(payload)
        assertTrue(BackupCipher.isEncrypted(encrypted))

        val decrypted = BackupCipher.decryptIfNeeded(encrypted)
        assertEquals(payload, decrypted)
    }
}
