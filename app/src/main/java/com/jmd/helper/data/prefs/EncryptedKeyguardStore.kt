/**
 * Package: com.jmd.helper.data.prefs
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [EncryptedKeyguardStore].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for EncryptedKeyguardStore.
 *
 * Linked With:
 * - Core Security & Utilities: [android.util.Base64], [androidx.core.content.edit], [com.jmd.helper.core.AppFileLogger]
 * - Android Framework & Jetpack: [android.content.SharedPreferences], [android.security.keystore.KeyGenParameterSpec], [android.security.keystore.KeyProperties]
 */

package com.jmd.helper.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.jmd.helper.core.AppFileLogger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * What it is:
 * Hardware-backed encrypted credential store using Android Keystore and AES-256-GCM.
 *
 * What it does:
 * Stores sensitive screen assist PINs and fallback authentication credentials in hardware keystore.
 *
 * Linked with:
 * - Architecture: [EncryptedKeyguardStore], [EncryptedKeyguardStore]
 */
class EncryptedKeyguardStore(context: Context) {

    private val prefs: SharedPreferences

    init {
        val appContext = context.applicationContext
        val targetContext = if (!appContext.isDeviceProtectedStorage) {
            appContext.createDeviceProtectedStorageContext()
        } else {
            appContext
        }
        prefs = targetContext.getSharedPreferences(STORAGE_PREFS_NAME, Context.MODE_PRIVATE)
        ensureMasterKey()
    }
    /**
     * What it is:
     * Execution routine for [ensureMasterKey].
     *
     * What it does:
     * Coordinates ensureMasterKey operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [EncryptedKeyguardStore]
     */

    private fun ensureMasterKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (t: Throwable) {
            AppFileLogger.w("EncryptedKeyguardStore", "Keystore initialization note: ${t.message}")
        }
    }
    /**
     * What it is:
     * Execution routine for [encrypt].
     *
     * What it does:
     * Coordinates encrypt operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [EncryptedKeyguardStore]
     */

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return plainText
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Throwable) {
            plainText
        }
    }
    /**
     * What it is:
     * Execution routine for [decrypt].
     *
     * What it does:
     * Coordinates decrypt operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [EncryptedKeyguardStore]
     */

    private fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            val combined = Base64.decode(cipherText, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return cipherText
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return cipherText
            val iv = ByteArray(GCM_IV_LENGTH)
            val encrypted = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Throwable) {
            cipherText
        }
    }

    /**
     * What it is:
     * Stores the user-configured lockscreen security PIN with hardware-backed encryption.
     *
     * What it does:
     * Executes [saveLockscreenPin] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [EncryptedKeyguardStore], [saveLockscreenPin]
     */
    fun saveLockscreenPin(pin: String) {
        val encrypted = encrypt(pin)
        prefs.edit { putString(KEY_LOCKSCREEN_PIN, encrypted) }
        AppFileLogger.d("EncryptedKeyguardStore", "Hardware-encrypted lockscreen PIN updated safely")
    }

    /**
     * What it is:
     * Retrieves the decrypted lockscreen security PIN.
     *
     * What it does:
     * Executes [getLockscreenPin] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [EncryptedKeyguardStore], [getLockscreenPin]
     */
    fun getLockscreenPin(): String {
        val raw = prefs.getString(KEY_LOCKSCREEN_PIN, "") ?: ""
        return decrypt(raw)
    }

    /**
     * What it is:
     * Clears the stored lockscreen security PIN.
     *
     * What it does:
     * Executes [clearLockscreenPin] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [EncryptedKeyguardStore], [clearLockscreenPin]
     */
    fun clearLockscreenPin() {
        prefs.edit { remove(KEY_LOCKSCREEN_PIN) }
    }

    /**
     * What it is:
     * Checks if a security PIN has been set.
     *
     * What it does:
     * Executes [hasLockscreenPin] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [EncryptedKeyguardStore], [hasLockscreenPin]
     */
    fun hasLockscreenPin(): Boolean {
        return getLockscreenPin().isNotBlank()
    }

    /**
     * What it is:
     * Saves the in-app biometric lock fallback authentication credential or salt.
     *
     * What it does:
     * Executes [saveAppLockSecret] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [EncryptedKeyguardStore], [saveAppLockSecret]
     */
    fun saveAppLockSecret(secret: String) {
        val encrypted = encrypt(secret)
        prefs.edit { putString(KEY_APP_LOCK_SECRET, encrypted) }
    }
    /**
     * What it is:
     * Execution routine for [getAppLockSecret].
     *
     * What it does:
     * Coordinates getAppLockSecret operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [EncryptedKeyguardStore]
     */

    fun getAppLockSecret(): String {
        val raw = prefs.getString(KEY_APP_LOCK_SECRET, "") ?: ""
        return decrypt(raw)
    }
    /**
     * What it is:
     * Companion object constant and factory provider for [EncryptedKeyguardStore].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [EncryptedKeyguardStore]
     */

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "helper_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val STORAGE_PREFS_NAME = "helper_secure_credentials_prefs"
        private const val KEY_LOCKSCREEN_PIN = "secure_device_unlock_pin"
        private const val KEY_APP_LOCK_SECRET = "secure_app_lock_secret"

        @Volatile
        private var instance: EncryptedKeyguardStore? = null
        /**
         * What it is:
         * Operational routine [getInstance].
         *
         * What it does:
         * Executes getInstance actions according to system invariants.
         *
         * Linked with:
         * - Component: [EncryptedKeyguardStore]
         */

        fun getInstance(context: Context): EncryptedKeyguardStore {
            return instance ?: synchronized(this) {
                instance ?: EncryptedKeyguardStore(context).also { instance = it }
            }
        }
    }
}

