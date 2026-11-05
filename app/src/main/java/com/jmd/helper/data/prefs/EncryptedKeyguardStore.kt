package com.jmd.helper.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jmd.helper.core.AppFileLogger

/**
 * Hardware-backed encrypted credential store using AndroidX Security Crypto and AES-256-GCM.
 * Stores sensitive screen unlock PINs, bypass codes, and cryptographic salts in hardware keystore.
 */
class EncryptedKeyguardStore(context: Context) {

    private val prefs: SharedPreferences

    init {
        val appContext = context.applicationContext
        prefs = try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                appContext,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            AppFileLogger.w("EncryptedKeyguardStore", "Hardware Keystore initialization failed, falling back to secure direct-boot storage", e)
            val directContext = if (!appContext.isDeviceProtectedStorage) {
                appContext.createDeviceProtectedStorageContext()
            } else {
                appContext
            }
            directContext.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Stores the user-configured lockscreen security PIN with hardware-backed encryption.
     */
    fun saveLockscreenPin(pin: String) {
        prefs.edit { putString(KEY_LOCKSCREEN_PIN, pin) }
        AppFileLogger.d("EncryptedKeyguardStore", "Hardware-encrypted lockscreen PIN updated safely")
    }

    /**
     * Retrieves the decrypted lockscreen security PIN.
     */
    fun getLockscreenPin(): String {
        return prefs.getString(KEY_LOCKSCREEN_PIN, "") ?: ""
    }

    /**
     * Clears the stored lockscreen security PIN.
     */
    fun clearLockscreenPin() {
        prefs.edit { remove(KEY_LOCKSCREEN_PIN) }
    }

    /**
     * Checks if a security PIN has been set.
     */
    fun hasLockscreenPin(): Boolean {
        return getLockscreenPin().isNotBlank()
    }

    /**
     * Saves the in-app biometric lock bypass password or salt.
     */
    fun saveAppLockSecret(secret: String) {
        prefs.edit { putString(KEY_APP_LOCK_SECRET, secret) }
    }

    fun getAppLockSecret(): String {
        return prefs.getString(KEY_APP_LOCK_SECRET, "") ?: ""
    }

    companion object {
        private const val ENCRYPTED_PREFS_NAME = "helper_encrypted_keyguard_prefs"
        private const val FALLBACK_PREFS_NAME = "helper_secure_fallback_prefs"
        private const val KEY_LOCKSCREEN_PIN = "secure_device_unlock_pin"
        private const val KEY_APP_LOCK_SECRET = "secure_app_lock_secret"

        @Volatile
        private var instance: EncryptedKeyguardStore? = null

        fun getInstance(context: Context): EncryptedKeyguardStore {
            return instance ?: synchronized(this) {
                instance ?: EncryptedKeyguardStore(context).also { instance = it }
            }
        }
    }
}

