

/**
 * Package: com.jmd.autotext.data.prefs
 * Module: Preferences Layer
 *
 * Provides typed configuration storage for app preferences, master kill switches, default SIM bindings,
 * security PIN settings, keyguard unlock options, UI branding, and Direct Boot (FBE) device-protected storage.
 *
 * Linked With:
 * - Direct Boot Receivers: [com.jmd.autotext.receiver.BootReceiver], [com.jmd.autotext.receiver.TaskAlarmReceiver]
 * - UI Screens: `SettingsScreen`, `MainAppHost`
 * - Core Safety: [com.jmd.autotext.core.AutomationSafetyManager], [com.jmd.autotext.core.SmsQuotaManager]
 * - WhatsApp Engines: [com.jmd.autotext.whatsapp.KeyguardUnlockEngine]
 */
package com.jmd.autotext.data.prefs

import android.content.*
import androidx.core.content.edit
import java.util.UUID

/**
 * Encapsulated storage manager for user preferences and direct-boot security configurations.
 */
class PreferenceManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("autotext_settings", Context.MODE_PRIVATE)

    private val dePrefs: SharedPreferences by lazy {
        try {
            val deContext = context.createDeviceProtectedStorageContext()
            deContext.getSharedPreferences("autotext_de_prefs", Context.MODE_PRIVATE)
        } catch (_: Exception) {
            prefs
        }
    }

    companion object {
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_DEFAULT_SIM = "default_sim_slot"

        private const val KEY_AUTO_UNLOCK_ENABLED = "auto_unlock_enabled"
        private const val KEY_AUTO_UNLOCK_TYPE = "auto_unlock_type"
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
        private const val KEY_SMS_QUOTA_LIMIT_ENABLED = "sms_quota_limit_enabled"
        private const val KEY_EMERGENCY_STOP_ACTIVE = "emergency_stop_active"
        private const val KEY_PREVENT_SYSTEM_AUTO_ROTATE = "prevent_system_auto_rotate"
    }

    var isEmergencyStopActive: Boolean
        get() = prefs.getBoolean(KEY_EMERGENCY_STOP_ACTIVE, false)
        set(value) = prefs.edit { putBoolean(KEY_EMERGENCY_STOP_ACTIVE, value) }

    var isPreventSystemAutoRotateEnabled: Boolean
        get() = prefs.getBoolean(KEY_PREVENT_SYSTEM_AUTO_ROTATE, true)
        set(value) = prefs.edit { putBoolean(KEY_PREVENT_SYSTEM_AUTO_ROTATE, value) }

    var isTasksMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_TASKS_MASTER_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_TASKS_MASTER_ENABLED, value) }

    var isAutoReplyMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOREPLY_MASTER_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTOREPLY_MASTER_ENABLED, value) }

    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_APP_LOCK_ENABLED, value) }

    /**
     * Encrypts sensitive secrets (such as the device lockscreen PIN) using AES/GCM/NoPadding.
     * Generates a 12-byte cryptographic nonce per encryption and prepends "ENC:".
     *
     * @param secret Plaintext string to encrypt.
     * @return Base64 ciphertext string prefixed with "ENC:", or original string if encryption fails.
     */
    private fun encryptSecret(secret: String): String {
        if (secret.isEmpty()) return ""
        return try {
            val keyBytes = getDeviceSecretKey()
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }
            val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, spec)
            val ciphertext = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            "ENC:" + android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            secret
        }
    }

    /**
     * Decrypts AES/GCM encrypted secrets prefixed with "ENC:".
     * First attempts decryption using the device-hardware bound key. If that fails (e.g. legacy stored secret),
     * falls back to the legacy package seed and automatically re-encrypts using the modern key.
     * Transparently returns legacy plaintext values for backwards compatibility.
     *
     * @param stored Stored encrypted or legacy plaintext string.
     * @return Decrypted plaintext secret string.
     */
    private fun decryptSecret(stored: String): String {
        if (stored.isEmpty()) return ""
        if (!stored.startsWith("ENC:")) return stored // transparent legacy plain migration
        val decrypted = try {
            decryptSecretWithKey(stored, getDeviceSecretKey())
        } catch (_: Exception) {
            ""
        }
        if (decrypted.isNotEmpty()) return decrypted

        // Fallback to legacy package-only key for seamless upgrade
        return try {
            val legacyDecrypted = decryptSecretWithKey(stored, getLegacyDeviceSecretKey())
            if (legacyDecrypted.isNotEmpty()) {
                // Opportunistically upgrade stored ciphertext to hardware-bound key
                try {
                    val upgraded = encryptSecret(legacyDecrypted)
                    prefs.edit { putString(KEY_DEVICE_UNLOCK_PIN, upgraded) }
                    try { dePrefs.edit { putString(KEY_DEVICE_UNLOCK_PIN, upgraded) } } catch (_: Exception) {}
                } catch (_: Exception) {}
            }
            legacyDecrypted
        } catch (_: Exception) {
            ""
        }
    }

    private fun decryptSecretWithKey(stored: String, keyBytes: ByteArray): String {
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
     * Derives a 256-bit AES cryptographic key bound to the local application package.
     * Compatible with Direct Boot (FBE) storage prior to device unlock.
     *
     * @return 32-byte SHA-256 derived key.
     */
    private fun getDeviceSecretKey(): ByteArray {
        val seed = "AutoText_${context.packageName ?: "com.jmd.autotext"}_Secure_Storage"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(seed.toByteArray(Charsets.UTF_8))
    }

    /**
     * Legacy key derivation preserved for backwards compatibility with secrets encrypted with random device id seed.
     */
    private fun getLegacyDeviceSecretKey(): ByteArray {
        val storedId = prefs.getString(KEY_UNIQUE_DEVICE_ID, null) ?: ""
        val seed = if (storedId.isNotBlank()) {
            "AutoText_${context.packageName ?: "com.jmd.autotext"}_${storedId}_Secure_Storage"
        } else {
            "AutoText_${context.packageName ?: "com.jmd.autotext"}_Secure_Storage"
        }
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(seed.toByteArray(Charsets.UTF_8))
    }

    var defaultSimSlot: Int
        get() = prefs.getInt(KEY_DEFAULT_SIM, 0)
        set(value) = prefs.edit { putInt(KEY_DEFAULT_SIM, value) }

    var isAutoUnlockEnabled: Boolean
        get() {
            return try {
                if (!androidx.core.os.UserManagerCompat.isUserUnlocked(context)) {
                    dePrefs.getBoolean(KEY_AUTO_UNLOCK_ENABLED, false)
                } else {
                    prefs.getBoolean(KEY_AUTO_UNLOCK_ENABLED, false)
                }
            } catch (_: Exception) {
                dePrefs.getBoolean(KEY_AUTO_UNLOCK_ENABLED, false)
            }
        }
        set(value) {
            prefs.edit { putBoolean(KEY_AUTO_UNLOCK_ENABLED, value) }
            try { dePrefs.edit { putBoolean(KEY_AUTO_UNLOCK_ENABLED, value) } } catch (_: Exception) {}
        }

    var autoUnlockType: String
        get() {
            return try {
                if (!androidx.core.os.UserManagerCompat.isUserUnlocked(context)) {
                    dePrefs.getString(KEY_AUTO_UNLOCK_TYPE, "PIN") ?: "PIN"
                } else {
                    prefs.getString(KEY_AUTO_UNLOCK_TYPE, "PIN") ?: "PIN"
                }
            } catch (_: Exception) {
                dePrefs.getString(KEY_AUTO_UNLOCK_TYPE, "PIN") ?: "PIN"
            }
        }
        set(value) {
            prefs.edit { putString(KEY_AUTO_UNLOCK_TYPE, value) }
            try { dePrefs.edit { putString(KEY_AUTO_UNLOCK_TYPE, value) } } catch (_: Exception) {}
        }

    var deviceUnlockPin: String
        get() {
            val raw = try {
                if (!androidx.core.os.UserManagerCompat.isUserUnlocked(context)) {
                    dePrefs.getString(KEY_DEVICE_UNLOCK_PIN, "").orEmpty()
                } else {
                    prefs.getString(KEY_DEVICE_UNLOCK_PIN, "").orEmpty()
                }
            } catch (_: Exception) {
                dePrefs.getString(KEY_DEVICE_UNLOCK_PIN, "").orEmpty()
            }
            return decryptSecret(raw).ifBlank { "2020" }
        }
        set(value) {
            val enc = encryptSecret(value)
            prefs.edit { putString(KEY_DEVICE_UNLOCK_PIN, enc) }
            try { dePrefs.edit { putString(KEY_DEVICE_UNLOCK_PIN, enc) } } catch (_: Exception) {}
        }

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
            } catch (_: Exception) {
                dePrefs.getString(KEY_APP_THEME, "SYSTEM") ?: "SYSTEM"
            }
        }
        set(value) {
            prefs.edit { putString(KEY_APP_THEME, value) }
            try { dePrefs.edit { putString(KEY_APP_THEME, value) } } catch (_: Exception) {}
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
        get() = prefs.getString(KEY_CUSTOM_APP_NAME, "AutoText") ?: "AutoText"
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
     * When disabled, enables unlimited personal automation dispatch without regulatory throttling.
     */
    var isSmsQuotaLimitEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMS_QUOTA_LIMIT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_SMS_QUOTA_LIMIT_ENABLED, value) }

    var isDispatchNotificationSoundEnabled: Boolean
        get() = prefs.getBoolean("dispatch_notification_sound", false)
        set(value) = prefs.edit { putBoolean("dispatch_notification_sound", value) }

    /**
     * Stealth Protection & Uninterrupted Mode: Active by default.
     * Ensures all scheduled tasks (Calls, SMS, WhatsApp, Alarms) run uninterrupted while
     * strictly isolating from banking, payment, and UPI applications so they never detect or flag accessibility.
     */
    var isBankingShieldEnabled: Boolean
        get() = prefs.getBoolean(KEY_BANKING_SHIELD_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_BANKING_SHIELD_ENABLED, value) }

    /** Alias for [isBankingShieldEnabled] reflecting uninterrupted stealth automation. */
    var isStealthProtectionEnabled: Boolean
        get() = isBankingShieldEnabled
        set(value) { isBankingShieldEnabled = value }

}
