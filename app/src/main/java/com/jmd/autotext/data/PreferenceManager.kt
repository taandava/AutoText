package com.jmd.autotext.data

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("autotext_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_APP_LOCK_PIN = "app_lock_pin"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DEFAULT_SIM = "default_sim_slot"
        private const val KEY_ASK_BEFORE_SEND = "default_ask_before_send"
        private const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"

        // Auto Unlock & Lock Screen settings
        private const val KEY_AUTO_UNLOCK_ENABLED = "auto_unlock_enabled"
        private const val KEY_AUTO_UNLOCK_TYPE = "auto_unlock_type" // "PIN", "SWIPE", "NONE"
        private const val KEY_DEVICE_UNLOCK_PIN = "device_unlock_pin"
        private const val KEY_AUTO_LOCK_AFTER_SEND = "auto_lock_after_send"
        private const val KEY_DEFAULT_COUNTRY_CODE = "default_country_code"
        private const val KEY_FOREGROUND_WATCHDOG_ENABLED = "foreground_watchdog_enabled"

        // Master feature switches
        private const val KEY_TASKS_MASTER_ENABLED = "tasks_master_enabled"
        private const val KEY_AUTOREPLY_MASTER_ENABLED = "autoreply_master_enabled"
        private const val KEY_AUTOFORWARD_MASTER_ENABLED = "autoforward_master_enabled"

        private const val KEY_UNIQUE_DEVICE_ID = "unique_device_id"
        private const val KEY_CUSTOM_DEVICE_NAME = "custom_device_name"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    var uniqueDeviceId: String
        get() {
            var id = prefs.getString(KEY_UNIQUE_DEVICE_ID, null)
            if (id.isNullOrBlank()) {
                id = "dev_" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)
                prefs.edit().putString(KEY_UNIQUE_DEVICE_ID, id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString(KEY_UNIQUE_DEVICE_ID, value).apply()

    var customDeviceName: String
        get() = prefs.getString(KEY_CUSTOM_DEVICE_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_DEVICE_NAME, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    fun getEffectiveDeviceName(): String {
        val custom = customDeviceName.trim()
        if (custom.isNotBlank()) return custom
        val raw = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
        return raw.ifBlank { "Android Phone" }
    }

    var isTasksMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_TASKS_MASTER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TASKS_MASTER_ENABLED, value).apply()

    var isAutoReplyMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOREPLY_MASTER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOREPLY_MASTER_ENABLED, value).apply()

    var isAutoForwardMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOFORWARD_MASTER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOFORWARD_MASTER_ENABLED, value).apply()

    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()

    var appLockPin: String
        get() = prefs.getString(KEY_APP_LOCK_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_APP_LOCK_PIN, value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var defaultSimSlot: Int
        get() = prefs.getInt(KEY_DEFAULT_SIM, 0)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_SIM, value).apply()

    var defaultAskBeforeSend: Boolean
        get() = prefs.getBoolean(KEY_ASK_BEFORE_SEND, false)
        set(value) = prefs.edit().putBoolean(KEY_ASK_BEFORE_SEND, value).apply()

    var countdownSeconds: Int
        get() = prefs.getInt(KEY_COUNTDOWN_SECONDS, 10)
        set(value) = prefs.edit().putInt(KEY_COUNTDOWN_SECONDS, value).apply()

    // Auto-Unlock and Screen Lock Properties (Secure Defaults: Off, No Plaintext Default PIN)
    var isAutoUnlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UNLOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UNLOCK_ENABLED, value).apply()

    var autoUnlockType: String
        get() = prefs.getString(KEY_AUTO_UNLOCK_TYPE, "PIN") ?: "PIN"
        set(value) = prefs.edit().putString(KEY_AUTO_UNLOCK_TYPE, value).apply()

    var deviceUnlockPin: String
        get() = prefs.getString(KEY_DEVICE_UNLOCK_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_UNLOCK_PIN, value).apply()

    var isAutoLockAfterSendEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOCK_AFTER_SEND, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LOCK_AFTER_SEND, value).apply()

    var defaultCountryCode: String
        get() = prefs.getString(KEY_DEFAULT_COUNTRY_CODE, "91") ?: "91"
        set(value) = prefs.edit().putString(KEY_DEFAULT_COUNTRY_CODE, value).apply()

    var isForegroundWatchdogEnabled: Boolean
        get() = prefs.getBoolean(KEY_FOREGROUND_WATCHDOG_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FOREGROUND_WATCHDOG_ENABLED, value).apply()

    var lastContactIngestionTime: Long
        get() = prefs.getLong("last_contact_ingestion_time", 0L)
        set(value) = prefs.edit().putLong("last_contact_ingestion_time", value).apply()
}
