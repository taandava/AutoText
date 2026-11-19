/**
 * Package: com.jmd.superwa.data.prefs
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [PreferenceManager].
 *
 * What it does:
 * Provides typed configuration storage for app preferences, default SIM bindings, security PIN settings, keyguard unlock options, UI branding, and Direct Boot (FBE) device-protected storage.
 *
 * Linked With:
 * - Core Security & Utilities: [androidx.core.content.edit], [android.util.Log], [java.util.UUID]
 */

package com.jmd.superwa.data.prefs

import android.content.*
import androidx.core.content.edit
import android.util.Log
import java.util.UUID

/**
 * What it is:
 * Encapsulated storage manager for user preferences and direct-boot security configurations.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [PreferenceManager].
 *
 * Linked with:
 * - Architecture: [PreferenceManager], [PreferenceManager]
 */
class PreferenceManager(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        val target = try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
            val isUnlocked = userManager?.isUserUnlocked != false
            if (isUnlocked) {
                context.getSharedPreferences("super_settings", Context.MODE_PRIVATE)
            } else {
                val deContext = context.createDeviceProtectedStorageContext()
                deContext.getSharedPreferences("super_settings", Context.MODE_PRIVATE)
            }
        } catch (e: Exception) {
            try {
                val deContext = context.createDeviceProtectedStorageContext()
                deContext.getSharedPreferences("super_settings", Context.MODE_PRIVATE)
            } catch (inner: Exception) {
                context.getSharedPreferences("super_settings", Context.MODE_PRIVATE)
            }
        }
        migrateLegacyPreferences(target, "helper_settings")
        migrateLegacyPreferences(target, "autotext_settings")
        target
    }

    val sharedPreferences: SharedPreferences
        get() = prefs

    private val dePrefs: SharedPreferences by lazy {
        val target = try {
            val deContext = context.createDeviceProtectedStorageContext()
            deContext.getSharedPreferences("super_de_prefs", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            prefs
        }
        migrateLegacyPreferences(target, "helper_de_prefs")
        migrateLegacyPreferences(target, "autotext_de_prefs")
        target
    }
    /**
     * What it is:
     * Execution routine for [migrateLegacyPreferences].
     *
     * What it does:
     * Coordinates migrateLegacyPreferences operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [PreferenceManager]
     */

    private fun migrateLegacyPreferences(target: SharedPreferences, legacyName: String) {
        try {
            if (target.all.isEmpty()) {
                val legacy = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
                val allLegacy = legacy.all
                if (allLegacy.isNotEmpty()) {
                    target.edit {
                        for ((k, v) in allLegacy) {
                            when (v) {
                                is Boolean -> putBoolean(k, v)
                                is Int -> putInt(k, v)
                                is Long -> putLong(k, v)
                                is Float -> putFloat(k, v)
                                is String -> putString(k, v)
                                is Set<*> -> putStringSet(k, v.filterIsInstance<String>().toSet())
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy settings migration note: ${t.message}")
        }
    }
    /**
     * What it is:
     * Companion object constant and factory provider for [PreferenceManager].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [PreferenceManager]
     */

    companion object {
        private const val TAG = "PreferenceManager"
        private const val KEY_DEFAULT_SIM = "default_sim_slot"

        private const val KEY_SCREEN_ASSIST_ENABLED = "screen_assist_enabled"
        private const val KEY_SCREEN_ASSIST_TYPE = "screen_assist_type"
        private const val KEY_SCREEN_ASSIST_PIN = "screen_assist_pin"
        private const val KEY_DEVICE_UNLOCK_PIN = "device_unlock_pin"
        private const val KEY_AUTO_LOCK_AFTER_SEND = "auto_lock_after_send"
        private const val KEY_DEFAULT_COUNTRY_CODE = "default_country_code"

        private const val KEY_TASKS_MASTER_ENABLED = "tasks_master_enabled"
        private const val KEY_AUTOREPLY_MASTER_ENABLED = "autoreply_master_enabled"

        private const val KEY_UNIQUE_DEVICE_ID = "unique_device_id"

        private const val KEY_APP_THEME = "app_theme_mode"
        private const val KEY_FALLBACK_CLICK_STRATEGY = "fallback_click_strategy"
        private const val KEY_FALLBACK_MAX_RETRIES = "fallback_max_retries"
        private const val KEY_ENABLE_GESTURE_FALLBACK = "enable_gesture_fallback"
        private const val KEY_CUSTOM_CLICK_COORDINATE_OFFSET_Y = "custom_click_coordinate_offset_y"
        private const val KEY_CUSTOM_APP_NAME = "custom_app_name"
        private const val KEY_APP_ICON_VARIANT = "app_icon_variant"
        private const val KEY_LAST_CONTACT_INGESTION_TIME = "last_contact_ingestion_time"
        private const val KEY_BANKING_SHIELD_ENABLED = "banking_shield_enabled"
        private const val KEY_STRICT_BANKING_DETACH = "strict_banking_detach_enabled"
        private const val KEY_PRIVACY_MODE_ENABLED = "privacy_mode_enabled"
        private const val KEY_AUTO_EXCLUDE_BANKING = "privacy_auto_exclude_banking"
        private const val KEY_PROTECTED_PACKAGES = "protected_app_packages"
        private const val KEY_MASK_NOTIFICATIONS_LOCKSCREEN = "privacy_mask_notifications_lockscreen"
        private const val KEY_MASK_EXECUTION_LOGS = "privacy_mask_execution_logs"
        private const val KEY_AUTO_CLEAR_CLIPBOARD_SECONDS = "privacy_auto_clear_clipboard_seconds"
        private const val KEY_SMART_A11Y_SCOPING = "privacy_smart_a11y_scoping"
        private const val KEY_DYNAMIC_A11Y_ON_DEMAND = "privacy_dynamic_a11y_on_demand"
        private const val KEY_A11Y_USER_PAUSED = "a11y_user_paused"
        private const val KEY_SUPPRESS_NOTIFS_OVER_EXCLUDED = "privacy_suppress_notifs_over_excluded"
        private const val KEY_BLOCK_OVERLAYS_OVER_EXCLUDED = "privacy_block_overlays_over_excluded"
        private const val KEY_SMS_QUOTA_LIMIT_ENABLED = "sms_quota_limit_enabled"
        private const val KEY_PREVENT_SYSTEM_AUTO_ROTATE = "prevent_system_auto_rotate"

        // Variable Formatting Keys
        private const val KEY_CUSTOM_DATE_FORMAT = "custom_date_format"
        private const val KEY_CUSTOM_DATE_PATTERN = "custom_date_pattern"
        private const val KEY_CUSTOM_DATE_SEPARATOR = "custom_date_separator"
        private const val KEY_CUSTOM_TIME_FORMAT = "custom_time_format"
        private const val KEY_CUSTOM_GREETING_STYLE = "custom_greeting_style"
        private const val KEY_CUSTOM_GREETING_TEXT = "custom_greeting_text"
        private const val KEY_CUSTOM_NAMASTE_TEXT = "custom_namaste_text"
        private const val KEY_CUSTOM_CURRENCY_SYMBOL = "custom_currency_symbol"
        private const val KEY_CUSTOM_INDIAN_NUMBERING = "custom_indian_numbering"

        // Channel & Automation Customization Keys
        private const val KEY_WHATSAPP_TYPING_DELAY_MS = "whatsapp_typing_delay_ms"
        private const val KEY_AUTO_SMS_FALLBACK_ENABLED = "auto_sms_fallback_enabled"
        private const val KEY_CALL_SIM_DURATION_SECONDS = "call_sim_duration_seconds"
        private const val KEY_CALL_SIM_DEFAULT_NAME = "call_sim_default_name"
        private const val KEY_CALL_SIM_DEFAULT_NUMBER = "call_sim_default_number"
        private const val KEY_CALL_SIM_DEFAULT_STYLE = "call_sim_default_style"
        private const val KEY_AUTOREPLY_COOLDOWN_SECONDS = "autoreply_cooldown_seconds"

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "Super_Hardware_Key"
        private const val LEGACY_KEYSTORE_ALIAS = "Helper_Hardware_Key"
    }

    var isPreventSystemAutoRotateEnabled: Boolean
        get() = prefs.getBoolean(KEY_PREVENT_SYSTEM_AUTO_ROTATE, true)
        set(value) = prefs.edit { putBoolean(KEY_PREVENT_SYSTEM_AUTO_ROTATE, value) }

    var isTasksMasterEnabled: Boolean
        get() = true
        set(_) {}

    var isAutoReplyMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOREPLY_MASTER_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTOREPLY_MASTER_ENABLED, value) }



    /**
     * What it is:
     * Resolves or generates a hardware-backed 256-bit AES key inside the Android Keystore.
     *
     * What it does:
     * Hardware isolation protects encryption keys against memory dumping or physical extraction.
     *
     * Linked with:
     * - Architecture: [PreferenceManager], [getOrCreateHardwareKey]
     */
    private fun getOrCreateHardwareKey(): javax.crypto.SecretKey? {
        return try {
            val keyStore = java.security.KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                (keyStore.getEntry(KEYSTORE_ALIAS, null) as? java.security.KeyStore.SecretKeyEntry)?.secretKey
            } else if (keyStore.containsAlias(LEGACY_KEYSTORE_ALIAS)) {
                (keyStore.getEntry(LEGACY_KEYSTORE_ALIAS, null) as? java.security.KeyStore.SecretKeyEntry)?.secretKey
            } else if (keyStore.containsAlias("AutoText_Hardware_Key")) {
                (keyStore.getEntry("AutoText_Hardware_Key", null) as? java.security.KeyStore.SecretKeyEntry)?.secretKey
            } else {
                val keyGen = javax.crypto.KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER
                )
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }
        } catch (t: Throwable) {
            // AndroidKeyStore is unavailable in JVM unit tests or pre-unlock direct boot storage
            null
        }
    }
    /**
     * What it is:
     * Execution routine for [encryptCredentialWithDerivedKey].
     *
     * What it does:
     * Coordinates encryptCredentialWithDerivedKey operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [PreferenceManager]
     */
    private fun encryptCredentialWithDerivedKey(credential: String): String {
        return try {
            val keyBytes = getDeviceEncryptionKey()
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }
            val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, spec)
            val ciphertext = cipher.doFinal(credential.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            "ENC:" + android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            credential
        }
    }

    /**
     * What it is:
     * Encrypts sensitive credentials (such as the device lockscreen PIN) using Hardware Keystore AES/GCM/NoPadding when available, with quick fallback to Direct Boot compatible derived AES-GCM.
     *
     * What it does:
     * Executes [encryptCredential] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PreferenceManager], [encryptCredential]
     * @param credential Plaintext string to encrypt.
     * @return Base64 ciphertext string prefixed with "HW_ENC:" or "ENC:".
     */
    private fun encryptCredential(credential: String): String {
        if (credential.isEmpty()) return ""
        // 1. Primary: Hardware Keystore encryption (hardware-isolated cryptographic keys)
        try {
            val hwKey = getOrCreateHardwareKey()
            if (hwKey != null) {
                val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, hwKey)
                val iv = cipher.iv
                val ciphertext = cipher.doFinal(credential.toByteArray(Charsets.UTF_8))
                val combined = ByteArray(iv.size + ciphertext.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
                return "HW_ENC:" + android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
            }
        } catch (t: Throwable) {
            android.util.Log.d(TAG, "Hardware Keystore encryption unavailable; using derived fallback: ${t.message}")
        }

        // 2. Secondary fallback: Package-derived AES-GCM (Direct Boot & unit test compatibility)
        return encryptCredentialWithDerivedKey(credential)
    }

    /**
     * What it is:
     * Decrypts AES/GCM encrypted credentials prefixed with "ENC:".
     *
     * What it does:
     * First attempts decryption using the device-hardware bound key. If that fails (e.g. legacy stored credential), falls back to the legacy package seed and directly re-encrypts using the modern key. Transparently returns legacy plaintext values for backwards compatibility.
     *
     * Linked with:
     * - Architecture: [PreferenceManager], [decryptCredential]
     * @param stored Stored encrypted or legacy plaintext string.
     * @return Decrypted plaintext credential string.
     */
    private fun decryptCredential(stored: String): String {
        if (stored.isEmpty()) return ""
        // 1. Hardware Keystore ciphertext decryption
        if (stored.startsWith("HW_ENC:")) {
            try {
                val hwKey = getOrCreateHardwareKey()
                if (hwKey != null) {
                    val encoded = stored.substring(7)
                    val combined = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
                    if (combined.size > 12) {
                        val iv = ByteArray(12)
                        System.arraycopy(combined, 0, iv, 0, 12)
                        val ciphertext = ByteArray(combined.size - 12)
                        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.size)
                        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, hwKey, spec)
                        val decryptedBytes = cipher.doFinal(ciphertext)
                        return String(decryptedBytes, Charsets.UTF_8)
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.d(TAG, "Hardware Keystore decryption error: ${t.message}")
            }
        }

        if (!stored.startsWith("ENC:")) return stored // transparent legacy plain migration

        val decrypted = try {
            decryptCredentialWithKey(stored, getDeviceEncryptionKey())
        } catch (e: Exception) {
            ""
        }
        if (decrypted.isNotEmpty()) return decrypted

        val superUniqueDecrypted = try {
            decryptCredentialWithKey(stored, getSuperUniqueDeviceEncryptionKey())
        } catch (e: Exception) {
            ""
        }
        if (superUniqueDecrypted.isNotEmpty()) return superUniqueDecrypted

        val helperDecrypted = try {
            decryptCredentialWithKey(stored, getHelperDeviceEncryptionKey())
        } catch (e: Exception) {
            ""
        }
        if (helperDecrypted.isNotEmpty()) return helperDecrypted

        // Fallback to legacy package-only key for seamless upgrade
        return try {
            val legacyDecrypted = decryptCredentialWithKey(stored, getLegacyDeviceEncryptionKey())
            if (legacyDecrypted.isNotEmpty()) {
                // Opportunistically upgrade stored ciphertext to hardware-bound key
                try {
                    val upgraded = encryptCredential(legacyDecrypted)
                    prefs.edit { putString(KEY_DEVICE_UNLOCK_PIN, upgraded) }
                    try { dePrefs.edit { putString(KEY_DEVICE_UNLOCK_PIN, encryptCredentialWithDerivedKey(legacyDecrypted)) } } catch (e: Exception) { android.util.Log.d(TAG, "Direct boot PIN mirror failed: ${e.message}") }
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Keystore PIN upgrade failed: ${e.message}")
                }
            }
            legacyDecrypted
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * What it is:
     * Execution routine for [decryptCredentialWithKey].
     *
     * What it does:
     * Coordinates decryptCredentialWithKey operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [PreferenceManager]
     */
    private fun decryptCredentialWithKey(stored: String, keyBytes: ByteArray): String {
        val encoded = stored.substring(4)
        val combined = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        if (combined.size <= 12) return ""
        val iv = ByteArray(12)
        System.arraycopy(combined, 0, iv, 0, 12)
        val ciphertext = ByteArray(combined.size - 12)
        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.size)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, spec)
        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * What it is:
     * Derives a 256-bit AES cryptographic key bound to the local application package.
     *
     * What it does:
     * Compatible with Direct Boot (FBE) storage prior to device unlock.
     *
     * Linked with:
     * - Architecture: [PreferenceManager], [getDeviceEncryptionKey]
     * @return 32-byte SHA-256 derived key.
     */
    private fun getDeviceEncryptionKey(): ByteArray {
        val seed = "Super_${context.packageName ?: "com.jmd.superwa"}_Secure_Storage"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(seed.toByteArray(Charsets.UTF_8))
    }

    private fun getSuperUniqueDeviceEncryptionKey(): ByteArray {
        val storedId = prefs.getString(KEY_UNIQUE_DEVICE_ID, null) ?: ""
        val seed = if (storedId.isNotBlank()) {
            "Super_${context.packageName ?: "com.jmd.superwa"}_${storedId}_Secure_Storage"
        } else {
            "Super_${context.packageName ?: "com.jmd.superwa"}_Secure_Storage"
        }
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(seed.toByteArray(Charsets.UTF_8))
    }

    private fun getHelperDeviceEncryptionKey(): ByteArray {
        val seed = "Helper_${context.packageName ?: "com.jmd.superwa"}_Secure_Storage"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(seed.toByteArray(Charsets.UTF_8))
    }

    /**
     * What it is:
     * Legacy key derivation preserved for backwards compatibility with credentials encrypted with random device id seed.
     *
     * What it does:
     * Executes [getLegacyDeviceEncryptionKey] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PreferenceManager], [getLegacyDeviceEncryptionKey]
     */
    private fun getLegacyDeviceEncryptionKey(): ByteArray {
        val storedId = prefs.getString(KEY_UNIQUE_DEVICE_ID, null) ?: ""
        val seed = if (storedId.isNotBlank()) {
            "AutoText_${context.packageName ?: "com.jmd.superwa"}_${storedId}_Secure_Storage"
        } else {
            "AutoText_${context.packageName ?: "com.jmd.superwa"}_Secure_Storage"
        }
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(seed.toByteArray(Charsets.UTF_8))
    }

    var defaultSimSlot: Int
        get() = prefs.getInt(KEY_DEFAULT_SIM, 0)
        set(value) = prefs.edit { putInt(KEY_DEFAULT_SIM, value) }

    var isScreenAssistEnabled: Boolean
        get() {
            return try {
                val targetPrefs = if (!androidx.core.os.UserManagerCompat.isUserUnlocked(context)) dePrefs else prefs
                targetPrefs.getBoolean(KEY_SCREEN_ASSIST_ENABLED, false)
            } catch (e: Exception) {
                dePrefs.getBoolean(KEY_SCREEN_ASSIST_ENABLED, false)
            }
        }
        set(value) {
            prefs.edit {
                putBoolean(KEY_SCREEN_ASSIST_ENABLED, value)
            }
            try {
                dePrefs.edit {
                    putBoolean(KEY_SCREEN_ASSIST_ENABLED, value)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update device-protected screen assist enabled: ${e.message}")
            }
        }

    var screenAssistType: String
        get() {
            return try {
                val targetPrefs = if (!androidx.core.os.UserManagerCompat.isUserUnlocked(context)) dePrefs else prefs
                targetPrefs.getString(KEY_SCREEN_ASSIST_TYPE, "PIN") ?: "PIN"
            } catch (e: Exception) {
                dePrefs.getString(KEY_SCREEN_ASSIST_TYPE, "PIN") ?: "PIN"
            }
        }
        set(value) {
            prefs.edit {
                putString(KEY_SCREEN_ASSIST_TYPE, value)
            }
            try {
                dePrefs.edit {
                    putString(KEY_SCREEN_ASSIST_TYPE, value)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update device-protected screen assist type: ${e.message}")
            }
        }

    var screenAssistPin: String
        get() {
            val raw = try {
                val targetPrefs = if (!androidx.core.os.UserManagerCompat.isUserUnlocked(context)) dePrefs else prefs
                if (targetPrefs.contains(KEY_SCREEN_ASSIST_PIN)) {
                    targetPrefs.getString(KEY_SCREEN_ASSIST_PIN, "").orEmpty()
                } else {
                    targetPrefs.getString(KEY_DEVICE_UNLOCK_PIN, "").orEmpty()
                }
            } catch (e: Exception) {
                if (dePrefs.contains(KEY_SCREEN_ASSIST_PIN)) {
                    dePrefs.getString(KEY_SCREEN_ASSIST_PIN, "").orEmpty()
                } else {
                    dePrefs.getString(KEY_DEVICE_UNLOCK_PIN, "").orEmpty()
                }
            }
            return decryptCredential(raw)
        }
        set(value) {
            val enc = encryptCredential(value)
            prefs.edit {
                putString(KEY_SCREEN_ASSIST_PIN, enc)
                putString(KEY_DEVICE_UNLOCK_PIN, enc)
            }
            try {
                // For Direct Boot storage, store derived-key ciphertext so it is readable pre-unlock
                val deEnc = encryptCredentialWithDerivedKey(value)
                dePrefs.edit {
                    putString(KEY_SCREEN_ASSIST_PIN, deEnc)
                    putString(KEY_DEVICE_UNLOCK_PIN, deEnc)
                }
            } catch (e: Exception) {
                android.util.Log.d(TAG, "Direct boot screen assist PIN save failed: ${e.message}")
            }
        }

    /**
     * Backward-compatible alias for [screenAssistPin].
     */
    var deviceUnlockPin: String
        get() = screenAssistPin
        set(value) { screenAssistPin = value }

    var isAutoLockAfterSendEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOCK_AFTER_SEND, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_LOCK_AFTER_SEND, value) }

    var defaultCountryCode: String
        get() {
            val stored = prefs.getString(KEY_DEFAULT_COUNTRY_CODE, "+91") ?: "+91"
            return if (stored.startsWith("+")) stored else "+$stored"
        }
        set(value) {
            val normalized = if (value.startsWith("+")) value else "+$value"
            prefs.edit { putString(KEY_DEFAULT_COUNTRY_CODE, normalized) }
        }

    var lastContactIngestionTime: Long
        get() = prefs.getLong(KEY_LAST_CONTACT_INGESTION_TIME, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_CONTACT_INGESTION_TIME, value) }

    var appThemeMode: String
        get() {
            return try {
                if (!androidx.core.os.UserManagerCompat.isUserUnlocked(context)) {
                    dePrefs.getString(KEY_APP_THEME, "SYSTEM") ?: "SYSTEM"
                } else {
                    prefs.getString(KEY_APP_THEME, "SYSTEM") ?: "SYSTEM"
                }
            } catch (e: Exception) {
                dePrefs.getString(KEY_APP_THEME, "SYSTEM") ?: "SYSTEM"
            }
        }
        set(value) {
            prefs.edit { putString(KEY_APP_THEME, value) }
            try { dePrefs.edit { putString(KEY_APP_THEME, value) } } catch (e: Exception) { android.util.Log.d(TAG, "Direct boot theme save failed: ${e.message}") }
        }

    var isDynamicColorEnabled: Boolean
        get() = prefs.getBoolean("dynamic_color_enabled", false)
        set(value) = prefs.edit { putBoolean("dynamic_color_enabled", value) }

    var fallbackClickStrategy: String
        get() = prefs.getString(KEY_FALLBACK_CLICK_STRATEGY, "SMART_FALLBACK") ?: "SMART_FALLBACK"
        set(value) = prefs.edit { putString(KEY_FALLBACK_CLICK_STRATEGY, value) }

    var fallbackMaxRetries: Int
        get() = prefs.getInt(KEY_FALLBACK_MAX_RETRIES, 45)
        set(value) = prefs.edit { putInt(KEY_FALLBACK_MAX_RETRIES, value) }

    var isGestureFallbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_GESTURE_FALLBACK, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_GESTURE_FALLBACK, value) }

    var customClickCoordinateOffsetY: Int
        get() = prefs.getInt(KEY_CUSTOM_CLICK_COORDINATE_OFFSET_Y, 0)
        set(value) = prefs.edit { putInt(KEY_CUSTOM_CLICK_COORDINATE_OFFSET_Y, value) }

    var customAppName: String
        get() = prefs.getString(KEY_CUSTOM_APP_NAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CUSTOM_APP_NAME, value) }

    var appIconVariant: String
        get() = prefs.getString(KEY_APP_ICON_VARIANT, "DEFAULT") ?: "DEFAULT"
        set(value) = prefs.edit { putString(KEY_APP_ICON_VARIANT, value) }

    var whatsappDispatchDelaySeconds: Int
        get() = prefs.getInt("wa_dispatch_delay_seconds", 8)
        set(value) = prefs.edit { putInt("wa_dispatch_delay_seconds", value.coerceIn(3, 30)) }

    var contactSyncIntervalHours: Int
        get() = prefs.getInt("contact_sync_interval_hours", 12)
        set(value) = prefs.edit { putInt("contact_sync_interval_hours", value) }

    var dailySmsSafetyLimit: Int
        get() = prefs.getInt("daily_sms_safety_limit", 100)
        set(value) = prefs.edit { putInt("daily_sms_safety_limit", value.coerceIn(10, 50000)) }

    /**
     * Cellular SMS Quota Safety Enforcement switch.
     * When enabled, enforces [dailySmsSafetyLimit] (default 100 SMS/day).
     * When disabled, enables unlimited personal task dispatch without regulatory throttling.
     */
    var isSmsQuotaLimitEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMS_QUOTA_LIMIT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_SMS_QUOTA_LIMIT_ENABLED, value) }

    var isDispatchNotificationSoundEnabled: Boolean
        get() = prefs.getBoolean("dispatch_notification_sound", false)
        set(value) = prefs.edit { putBoolean("dispatch_notification_sound", value) }

    /**
     * Bank-Safe Standby & Uninterrupted Mode: Active by default.
     * Ensures all scheduled tasks (Calls, SMS, WhatsApp, Alarms) run uninterrupted while
     * strictly isolating from banking, payment, and UPI applications so they never detect or flag accessibility.
     */
    var isBankingShieldEnabled: Boolean
        get() = prefs.getBoolean(KEY_BANKING_SHIELD_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_BANKING_SHIELD_ENABLED, value) }

    /**
     * Opt-in strict mode: detach the task service while a banking app is in the foreground.
     * Off by default, because package isolation already protects banking apps without
     * interrupting scheduled work or making the service flicker in system settings.
     */
    var isStrictBankingDetachEnabled: Boolean
        get() = prefs.getBoolean(KEY_STRICT_BANKING_DETACH, false)
        set(value) = prefs.edit { putBoolean(KEY_STRICT_BANKING_DETACH, value) }

    /** Alias for [isBankingShieldEnabled] reflecting uninterrupted standby task. */
    
    var isPrivacyModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_MODE_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_PRIVACY_MODE_ENABLED, value) }

    var isAutoExcludeBankingEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_EXCLUDE_BANKING, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_EXCLUDE_BANKING, value) }

    var isMaskNotificationsOnLockScreenEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASK_NOTIFICATIONS_LOCKSCREEN, true)
        set(value) = prefs.edit { putBoolean(KEY_MASK_NOTIFICATIONS_LOCKSCREEN, value) }

    var isMaskExecutionLogsEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASK_EXECUTION_LOGS, false)
        set(value) = prefs.edit { putBoolean(KEY_MASK_EXECUTION_LOGS, value) }

    var autoClearClipboardSeconds: Int
        get() = prefs.getInt(KEY_AUTO_CLEAR_CLIPBOARD_SECONDS, 15)
        set(value) = prefs.edit { putInt(KEY_AUTO_CLEAR_CLIPBOARD_SECONDS, value.coerceIn(0, 300)) }

    var isSmartAccessibilityScopingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMART_A11Y_SCOPING, true)
        set(value) = prefs.edit { putBoolean(KEY_SMART_A11Y_SCOPING, value) }

    var isDynamicA11yOnDemandEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_A11Y_ON_DEMAND, false)
        set(value) = prefs.edit { putBoolean(KEY_DYNAMIC_A11Y_ON_DEMAND, value) }

    var isA11yUserPaused: Boolean
        get() = prefs.getBoolean(KEY_A11Y_USER_PAUSED, false)
        set(value) = prefs.edit { putBoolean(KEY_A11Y_USER_PAUSED, value) }

    var isSuppressNotifsOverExcludedEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUPPRESS_NOTIFS_OVER_EXCLUDED, true)
        set(value) = prefs.edit { putBoolean(KEY_SUPPRESS_NOTIFS_OVER_EXCLUDED, value) }

    var isBlockOverlaysOverExcludedEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_OVERLAYS_OVER_EXCLUDED, true)
        set(value) = prefs.edit { putBoolean(KEY_BLOCK_OVERLAYS_OVER_EXCLUDED, value) }

    /**
     * User-curated list of banking, payment and UPI packages that must never be inspected.
     * The Settings app picker writes this set; [com.jmd.superwa.core.ProtectedApplicationManager]
     * merges it with the built-in catalog so automation avoids these apps entirely.
     */
    /** One-time guard: legacy heuristic group rows were cleared on the first launch of this build. */
    var groupsResetV2: Boolean
        get() = prefs.getBoolean("groups_reset_v2", false)
        set(value) = prefs.edit().putBoolean("groups_reset_v2", value).apply()

    var protectedPackages: Set<String>
        get() = prefs.getStringSet(KEY_PROTECTED_PACKAGES, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_PROTECTED_PACKAGES, value) }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // VARIABLE FORMATTING
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /** Date format style for {date} token. Default "dd MMM yyyy". */
    var customDateFormat: String
        get() = prefs.getString(KEY_CUSTOM_DATE_FORMAT, "dd MMM yyyy") ?: "dd MMM yyyy"
        set(value) = prefs.edit { putString(KEY_CUSTOM_DATE_FORMAT, value) }

    /** Custom user-defined SimpleDateFormat pattern when customDateFormat == "CUSTOM". */
    var customDateCustomPattern: String
        get() = prefs.getString(KEY_CUSTOM_DATE_PATTERN, "dd-MM-yyyy") ?: "dd-MM-yyyy"
        set(value) = prefs.edit { putString(KEY_CUSTOM_DATE_PATTERN, value) }

    /** Explicit date separator ("auto", "-", "/", ".", " ", ""). */
    var customDateSeparator: String
        get() = prefs.getString(KEY_CUSTOM_DATE_SEPARATOR, "auto") ?: "auto"
        set(value) = prefs.edit { putString(KEY_CUSTOM_DATE_SEPARATOR, value) }

    /** Time format style for {time} token. Default "hh:mm a". */
    var customTimeFormat: String
        get() = prefs.getString(KEY_CUSTOM_TIME_FORMAT, "hh:mm a") ?: "hh:mm a"
        set(value) = prefs.edit { putString(KEY_CUSTOM_TIME_FORMAT, value) }

    /** Greeting style for {greeting} token: "DYNAMIC", "NAMASTE", "PRANAM", "HELLO", "HI", "CUSTOM". */
    var customGreetingStyle: String
        get() = prefs.getString(KEY_CUSTOM_GREETING_STYLE, "DYNAMIC") ?: "DYNAMIC"
        set(value) = prefs.edit { putString(KEY_CUSTOM_GREETING_STYLE, value) }

    /** Custom greeting text when customGreetingStyle == "CUSTOM". */
    var customGreetingCustomText: String
        get() = prefs.getString(KEY_CUSTOM_GREETING_TEXT, "Hello") ?: "Hello"
        set(value) = prefs.edit { putString(KEY_CUSTOM_GREETING_TEXT, value) }

    /** Wording used by the NAMASTE greeting style. Default "Namaste". */
    var customNamasteText: String
        get() = prefs.getString(KEY_CUSTOM_NAMASTE_TEXT, "Namaste") ?: "Namaste"
        set(value) = prefs.edit { putString(KEY_CUSTOM_NAMASTE_TEXT, value) }

    /** Currency symbol for the {rupee} token. Default "â‚¹". */
    var customCurrencySymbol: String
        get() = prefs.getString(KEY_CUSTOM_CURRENCY_SYMBOL, "â‚¹") ?: "â‚¹"
        set(value) = prefs.edit { putString(KEY_CUSTOM_CURRENCY_SYMBOL, value) }

    /** Indian number grouping format (1,00,000 vs 100,000). Default true. */
    var isIndianNumberFormattingEnabled: Boolean
        get() = prefs.getBoolean(KEY_CUSTOM_INDIAN_NUMBERING, true)
        set(value) = prefs.edit { putBoolean(KEY_CUSTOM_INDIAN_NUMBERING, value) }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // CHANNEL & AUTOMATION CUSTOMIZATIONS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /** WhatsApp typing simulation delay in milliseconds before dispatching. Default 400ms. */
    var whatsappTypingDelayMs: Int
        get() = prefs.getInt(KEY_WHATSAPP_TYPING_DELAY_MS, 400)
        set(value) = prefs.edit { putInt(KEY_WHATSAPP_TYPING_DELAY_MS, value.coerceIn(0, 3000)) }

    /** Automatic SMS fallback when WhatsApp chat cannot be resolved or is not installed. */
    var isAutoSmsFallbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SMS_FALLBACK_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_SMS_FALLBACK_ENABLED, value) }

    /** Ring duration for simulated incoming call activity in seconds. Default 30s. */
    var callSimulationRingDurationSeconds: Int
        get() = prefs.getInt(KEY_CALL_SIM_DURATION_SECONDS, 30)
        set(value) = prefs.edit { putInt(KEY_CALL_SIM_DURATION_SECONDS, value.coerceIn(5, 120)) }

    /** Default caller display name for incoming call simulation. */
    var callSimulationDefaultName: String
        get() = prefs.getString(KEY_CALL_SIM_DEFAULT_NAME, "Boss / Office") ?: "Boss / Office"
        set(value) = prefs.edit { putString(KEY_CALL_SIM_DEFAULT_NAME, value) }

    /** Default caller phone number for incoming call simulation. */
    var callSimulationDefaultNumber: String
        get() = prefs.getString(KEY_CALL_SIM_DEFAULT_NUMBER, "+91 98765 43210") ?: "+91 98765 43210"
        set(value) = prefs.edit { putString(KEY_CALL_SIM_DEFAULT_NUMBER, value) }

    /** Default visual skin style for incoming call simulation ("PIXEL", "SAMSUNG", "WHATSAPP"). */
    var callSimulationDefaultStyle: String
        get() = prefs.getString(KEY_CALL_SIM_DEFAULT_STYLE, "PIXEL") ?: "PIXEL"
        set(value) = prefs.edit { putString(KEY_CALL_SIM_DEFAULT_STYLE, value) }

    /** Auto-reply cooldown period in seconds per sender. Default 60s. */
    var autoReplyCooldownSeconds: Int
        get() = prefs.getInt(KEY_AUTOREPLY_COOLDOWN_SECONDS, 60)
        set(value) = prefs.edit { putInt(KEY_AUTOREPLY_COOLDOWN_SECONDS, value.coerceIn(10, 600)) }
}
