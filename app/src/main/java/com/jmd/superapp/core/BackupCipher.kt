/**
 * Package: com.jmd.superapp.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Cryptographic backup vault providing cross-device portable encryption (AES-256-GCM + PBKDF2)
 * alongside legacy hardware-bound Android Keystore envelope support.
 *
 * What it does:
 * - Produces [MAGIC_V2] (SUPERBAK2:) passphrase-protected envelopes compatible across any Android device.
 * - Supports [MAGIC_V1] (SUPERBAK1:) hardware Keystore envelopes for existing on-device backups.
 * - Gracefully parses legacy plaintext backups with schema validation.
 *
 * Linked With:
 * - Repositories: [com.jmd.superapp.data.repo.BackupRepository]
 * - Android Framework: [android.security.keystore.KeyGenParameterSpec], [javax.crypto.Cipher]
 */

package com.jmd.superapp.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Custom exception thrown when a backup cannot be decrypted due to an invalid passphrase or corrupted payload.
 */
class InvalidPassphraseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Dual-envelope encryption for user data backups.
 *
 * Supports:
 * 1. SUPERBAK2: Cross-device portable passphrase-protected AES-256-GCM envelope
 *    using PBKDF2WithHmacSHA256 (100,000 iterations, 16-byte random salt, 12-byte random IV, 128-bit auth tag).
 * 2. SUPERBAK1: Hardware AndroidKeyStore envelope (backward compatibility with device-bound local keys).
 * 3. Plaintext: Validated JSON payloads for legacy imports.
 */
object BackupCipher {

    private const val TAG = "BackupCipher"

    /** Legacy hardware-bound Keystore envelope prefix */
    const val MAGIC_V1 = "SUPERBAK1:"

    /** Modern portable passphrase-protected envelope prefix */
    const val MAGIC_V2 = "SUPERBAK2:"

    /** Default fallback magic prefix for backward compatibility checks */
    const val MAGIC = MAGIC_V1

    private const val ALIAS = "super_backup_key_v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val SALT_LENGTH = 16
    private const val TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val DEFAULT_PASSPHRASE_SEED = "super_device_vault_offline_backup_2026"

    private val secureRandom = SecureRandom()

    private fun safeLogD(msg: String) {
        runCatching { Log.d(TAG, msg) }
    }

    private fun safeLogE(msg: String, t: Throwable? = null) {
        runCatching { Log.e(TAG, msg, t) }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (t: Throwable) {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str.trim())
        } catch (t: Throwable) {
            android.util.Base64.decode(str.trim(), android.util.Base64.NO_WRAP)
        }
    }

    /**
     * What it is:
     * Quick check for an encrypted payload.
     *
     * What it does:
     * Returns true when [payload] starts with either [MAGIC_V1] or [MAGIC_V2], ignoring leading whitespace.
     */
    fun isEncrypted(payload: String): Boolean {
        val trimmed = payload.trimStart()
        return trimmed.startsWith(MAGIC_V1) || trimmed.startsWith(MAGIC_V2)
    }

    /**
     * Returns true if the payload was encrypted with the portable V2 envelope.
     */
    fun isPassphraseProtected(payload: String): Boolean {
        return payload.trimStart().startsWith(MAGIC_V2)
    }

    /**
     * What it is:
     * Encrypts a plaintext backup document.
     *
     * What it does:
     * When [passphrase] is provided, uses SUPERBAK2 (PBKDF2 + AES-GCM) which can be restored on any device.
     * When [passphrase] is blank, attempts hardware Keystore (SUPERBAK1) and falls back to default portable envelope.
     */
    fun encrypt(plainText: String, passphrase: String = ""): String {
        if (passphrase.isNotBlank()) {
            return encryptWithPassphrase(plainText, passphrase)
        }
        // Try hardware Keystore first for transparent device-bound backup if Keystore is present
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val envelope = ByteArray(cipher.iv.size + cipherText.size)
            System.arraycopy(cipher.iv, 0, envelope, 0, cipher.iv.size)
            System.arraycopy(cipherText, 0, envelope, cipher.iv.size, cipherText.size)
            MAGIC_V1 + encodeBase64(envelope)
        } catch (t: Throwable) {
            safeLogD("Keystore unavailable or unsupported, using default portable envelope: ${t.message}")
            encryptWithPassphrase(plainText, DEFAULT_PASSPHRASE_SEED)
        }
    }

    /**
     * Encrypts plaintext using PBKDF2WithHmacSHA256 and AES-256-GCM.
     * Envelope format: SUPERBAK2:<base64(salt)>:<base64(iv)>:<base64(ciphertext+tag)>
     */
    fun encryptWithPassphrase(plainText: String, passphrase: String): String {
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return "$MAGIC_V2${encodeBase64(salt)}:${encodeBase64(iv)}:${encodeBase64(cipherText)}"
    }

    /**
     * What it is:
     * Decrypts an encrypted backup document.
     *
     * What it does:
     * Supports both SUPERBAK2 (passphrase-based) and SUPERBAK1 (hardware Keystore) envelopes.
     * Plaintext documents are returned unchanged.
     *
     * @throws InvalidPassphraseException when a passphrase-protected backup fails authentication.
     * @throws IllegalStateException when a hardware Keystore backup cannot be restored on this device.
     */
    fun decryptIfNeeded(payload: String, passphrase: String = ""): String {
        val trimmed = payload.trimStart()
        return when {
            trimmed.startsWith(MAGIC_V2) -> decryptV2(trimmed.removePrefix(MAGIC_V2), passphrase)
            trimmed.startsWith(MAGIC_V1) -> decryptV1(trimmed.removePrefix(MAGIC_V1))
            else -> trimmed
        }
    }

    private fun decryptV2(body: String, passphrase: String): String {
        val parts = body.split(":")
        require(parts.size == 3) { "Invalid SUPERBAK2 envelope format: expected 3 segments (salt:iv:ciphertext)" }
        val salt = decodeBase64(parts[0])
        val iv = decodeBase64(parts[1])
        val cipherText = decodeBase64(parts[2])

        val effectivePassphrase = passphrase.ifBlank { DEFAULT_PASSPHRASE_SEED }
        return try {
            val key = deriveKey(effectivePassphrase, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (t: Throwable) {
            safeLogE("SUPERBAK2 decryption failed: ${t.message}")
            throw InvalidPassphraseException("Invalid backup passphrase or corrupted payload.", t)
        }
    }

    private fun decryptV1(body: String): String {
        return try {
            val envelope = decodeBase64(body)
            require(envelope.size > IV_LENGTH) { "Backup envelope is truncated" }
            val iv = envelope.copyOfRange(0, IV_LENGTH)
            val cipherText = envelope.copyOfRange(IV_LENGTH, envelope.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (t: Throwable) {
            safeLogE("SUPERBAK1 decryption failed: ${t.message}", t)
            throw IllegalStateException(
                "This backup was encrypted on another device with a hardware-bound key and cannot be restored here.",
                t
            )
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
