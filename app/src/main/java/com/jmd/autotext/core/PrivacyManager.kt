/**
 * Package: com.jmd.autotext.core
 * Module: Privacy & App Visibility Layer
 *
 * Centralized, thread-safe privacy engine governing application exclusions,
 * accessibility target scoping, sensitive content masking, overlay suppression,
 * and clipboard protection.
 */
package com.jmd.autotext.core

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.jmd.autotext.data.model.AppPrivacyConfig
import com.jmd.autotext.data.model.GlobalPrivacySettings
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.data.repo.PrivacyRepository
import com.jmd.autotext.service.AutoTextAccessibilityService
import java.util.concurrent.ConcurrentHashMap

/**
 * Central singleton orchestrating privacy rules and app visibility restrictions across AutoText.
 */
class PrivacyManager private constructor(private val appContext: Context) {

    private val repository = PrivacyRepository(appContext)
    private val prefs = PreferenceManager(appContext)

    // Thread-safe in-memory cache of package configurations for sub-millisecond lookups
    private val configCache = ConcurrentHashMap<String, AppPrivacyConfig>()

    @Volatile
    private var isInitialized = false

    companion object {
        private const val TAG = "PrivacyManager"

        @Volatile
        private var instance: PrivacyManager? = null

        /**
         * Returns the thread-safe singleton instance of [PrivacyManager].
         */
        fun getInstance(context: Context): PrivacyManager {
            return instance ?: synchronized(this) {
                instance ?: PrivacyManager(context.applicationContext).also {
                    instance = it
                    it.initialize()
                }
            }
        }

        /**
         * Standard whitelist of essential system and automation target packages that AutoText
         * requires for normal operation (unless explicitly excluded by the user).
         */
        val BASE_AUTOMATION_TARGET_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.android.systemui",
            "com.google.android.systemui",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.vivo.upslide",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller"
        )

        /**
         * Masks sensitive message text (e.g. "Meeting at 5 PM" -> "M***M" or "[PROTECTED]").
         */
        fun maskSensitiveText(text: String): String {
            if (text.isBlank()) return ""
            val trimmed = text.trim()
            if (trimmed.length <= 2) return "**"
            return "${trimmed.first()}***${trimmed.last()}"
        }

        /**
         * Masks recipient phone numbers for privacy-safe logs and lockscreen alerts (e.g. "9769602020" -> "9769***020").
         */
        fun maskPhoneNumber(phone: String): String {
            val digits = phone.filter { it.isDigit() }
            if (digits.length < 7) return "***"
            val coreDigits = if (digits.length >= 10) digits.takeLast(10) else digits
            val prefix = coreDigits.take(4)
            val suffix = coreDigits.takeLast(3)
            return "$prefix***$suffix"
        }
    }

    /**
     * Initializes the privacy engine cache and seeds default banking exclusions if needed.
     */
    @Synchronized
    fun initialize() {
        if (isInitialized) return
        try {
            // Seed known financial packages on initial launch
            repository.seedDefaultBankingConfigs(AutoTextAccessibilityService.KNOWN_FINANCIAL_PACKAGE_PREFIXES)

            // Populate in-memory cache
            refreshCache()
            isInitialized = true
            Log.d(TAG, "PrivacyManager initialized with ${configCache.size} package configs")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing PrivacyManager", e)
        }
    }

    /**
     * Reloads configuration cache from persistent SQLite storage.
     */
    fun refreshCache() {
        try {
            val configs = repository.getAllConfigs()
            configCache.clear()
            for (c in configs) {
                configCache[c.packageName.trim()] = c
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing privacy cache", e)
        }
    }

    /**
     * Retrieves all cached package privacy configurations.
     */
    fun getAllConfigs(): List<AppPrivacyConfig> = configCache.values.toList().sortedBy { it.appName.lowercase() }

    /**
     * Retrieves the specific configuration for a package, or creates a default exclusion if it's a known banking app.
     */
    fun getConfigForPackage(packageName: String): AppPrivacyConfig? {
        val trimmed = packageName.trim()
        val cached = configCache[trimmed]
        if (cached != null) return cached

        // Check persistent storage
        val fromDb = repository.getConfigForPackage(trimmed)
        if (fromDb != null) {
            configCache[trimmed] = fromDb
            return fromDb
        }

        // If it's a known financial package and auto-exclude is on, generate dynamic default
        if (prefs.isAutoExcludeBankingEnabled && AutoTextAccessibilityService.isFinancialOrBankingPackage(trimmed)) {
            val autoConfig = AppPrivacyConfig(
                packageName = trimmed,
                appName = trimmed.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                isExcluded = true,
                disableAutomation = true,
                blockOverlays = true,
                suppressNotifications = true,
                disableClipboard = true,
                maskSensitiveContent = true,
                disableRules = true,
                customNotes = "Auto-detected Banking App"
            )
            configCache[trimmed] = autoConfig
            repository.saveConfig(autoConfig)
            return autoConfig
        }

        return null
    }

    /**
     * Saves or updates an individual package configuration in cache and database.
     */
    fun saveConfig(config: AppPrivacyConfig): Boolean {
        val success = repository.saveConfig(config)
        if (success) {
            configCache[config.packageName.trim()] = config
            updateAccessibilityScoping()
        }
        return success
    }

    /**
     * Saves multiple configurations in a single atomic transaction.
     */
    fun saveConfigs(configs: List<AppPrivacyConfig>): Boolean {
        val success = repository.saveConfigs(configs)
        if (success) {
            for (c in configs) {
                configCache[c.packageName.trim()] = c
            }
            updateAccessibilityScoping()
        }
        return success
    }

    /**
     * Deletes a package privacy configuration.
     */
    fun deleteConfig(packageName: String): Boolean {
        val trimmed = packageName.trim()
        val success = repository.deleteConfig(trimmed)
        if (success) {
            configCache.remove(trimmed)
            updateAccessibilityScoping()
        }
        return success
    }

    /**
     * Resets all privacy configurations and preferences to factory default shield.
     */
    fun resetAllSettings(): Boolean {
        val success = repository.deleteAllConfigs()
        configCache.clear()
        prefs.isPrivacyModeEnabled = true
        prefs.isAutoExcludeBankingEnabled = true
        prefs.isMaskNotificationsOnLockScreenEnabled = true
        prefs.isMaskExecutionLogsEnabled = false
        prefs.autoClearClipboardSeconds = 15
        prefs.isSmartAccessibilityScopingEnabled = true
        prefs.isDynamicA11yOnDemandEnabled = false
        prefs.isSuppressNotifsOverExcludedEnabled = true
        prefs.isBlockOverlaysOverExcludedEnabled = true

        // Re-seed default banking rules
        repository.seedDefaultBankingConfigs(AutoTextAccessibilityService.KNOWN_FINANCIAL_PACKAGE_PREFIXES)
        refreshCache()
        updateAccessibilityScoping()
        return success
    }

    /**
     * Checks whether an application is marked as excluded from AutoText.
     */
    fun isAppExcluded(packageName: String): Boolean {
        if (!prefs.isPrivacyModeEnabled) return false
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) return false

        val config = getConfigForPackage(trimmed)
        if (config != null && config.isExcluded) return true

        if (prefs.isAutoExcludeBankingEnabled && AutoTextAccessibilityService.isFinancialOrBankingPackage(trimmed)) {
            return true
        }
        return false
    }

    /**
     * Checks whether automation actions are disallowed for a specific package.
     */
    fun isAutomationBlockedForPackage(packageName: String): Boolean {
        if (!prefs.isPrivacyModeEnabled) return false
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) return false

        val config = getConfigForPackage(trimmed)
        if (config != null) {
            return config.isExcluded && config.disableAutomation
        }
        if (prefs.isAutoExcludeBankingEnabled && AutoTextAccessibilityService.isFinancialOrBankingPackage(trimmed)) {
            return true
        }
        return false
    }

    /**
     * Checks whether floating overlays and popups should be blocked over the currently active foreground application.
     */
    fun isOverlayBlockedForActiveApp(): Boolean {
        if (!prefs.isPrivacyModeEnabled || !prefs.isBlockOverlaysOverExcludedEnabled) return false
        val activePkg = getActiveForegroundPackage() ?: return false
        val config = getConfigForPackage(activePkg)
        if (config != null && config.isExcluded && config.blockOverlays) return true
        if (prefs.isAutoExcludeBankingEnabled && AutoTextAccessibilityService.isFinancialOrBankingPackage(activePkg)) {
            return true
        }
        return false
    }

    /**
     * Checks whether non-critical notification popups should be suppressed over the currently active foreground application.
     */
    fun isNotificationSuppressedForActiveApp(): Boolean {
        if (!prefs.isPrivacyModeEnabled || !prefs.isSuppressNotifsOverExcludedEnabled) return false
        val activePkg = getActiveForegroundPackage() ?: return false
        val config = getConfigForPackage(activePkg)
        if (config != null && config.isExcluded && config.suppressNotifications) return true
        if (prefs.isAutoExcludeBankingEnabled && AutoTextAccessibilityService.isFinancialOrBankingPackage(activePkg)) {
            return true
        }
        return false
    }

    /**
     * Checks whether automatic clipboard interaction is disabled for a given target package.
     */
    fun isClipboardDisabledForPackage(packageName: String): Boolean {
        if (!prefs.isPrivacyModeEnabled) return false
        val trimmed = packageName.trim()
        val config = getConfigForPackage(trimmed)
        if (config != null && config.isExcluded && config.disableClipboard) return true
        if (prefs.isAutoExcludeBankingEnabled && AutoTextAccessibilityService.isFinancialOrBankingPackage(trimmed)) {
            return true
        }
        return false
    }

    /**
     * Checks whether incoming auto-reply or trigger rules originating from a package are disabled.
     */
    fun isRulesDisabledForPackage(packageName: String): Boolean {
        if (!prefs.isPrivacyModeEnabled) return false
        val trimmed = packageName.trim()
        val config = getConfigForPackage(trimmed)
        if (config != null && config.isExcluded && config.disableRules) return true
        if (prefs.isAutoExcludeBankingEnabled && AutoTextAccessibilityService.isFinancialOrBankingPackage(trimmed)) {
            return true
        }
        return false
    }

    /**
     * Checks whether sensitive message content and recipient numbers related to a package should be masked.
     */
    fun shouldMaskSensitiveContent(packageName: String? = null): Boolean {
        if (!prefs.isPrivacyModeEnabled) return false
        if (prefs.isMaskExecutionLogsEnabled) return true
        if (packageName != null) {
            val config = getConfigForPackage(packageName.trim())
            if (config != null && config.isExcluded && config.maskSensitiveContent) return true
            if (prefs.isAutoExcludeBankingEnabled && AutoTextAccessibilityService.isFinancialOrBankingPackage(packageName)) {
                return true
            }
        }
        return false
    }

    /**
     * Masks sensitive message text (e.g. "Meeting at 5 PM" -> "M***M" or "[PROTECTED]").
     */
    fun maskSensitiveText(text: String): String = Companion.maskSensitiveText(text)

    /**
     * Masks recipient phone numbers for privacy-safe logs and lockscreen alerts (e.g. "9769602020" -> "9769***020").
     */
    fun maskPhoneNumber(phone: String): String = Companion.maskPhoneNumber(phone)

    /**
     * Detects the currently active foreground application package using AccessibilityService or UsageStatsManager.
     */
    fun getActiveForegroundPackage(): String? {
        // Priority 1: Instant active package from Accessibility Service
        val a11yPkg = AutoTextAccessibilityService.currentForegroundPackage
        if (!a11yPkg.isNullOrBlank()) return a11yPkg

        // Priority 2: UsageStatsManager (if permission granted)
        try {
            val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val now = System.currentTimeMillis()
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 10000, now)
                if (!stats.isNullOrEmpty()) {
                    val topStat = stats.maxByOrNull { it.lastTimeUsed }
                    if (topStat != null && topStat.packageName != appContext.packageName) {
                        return topStat.packageName
                    }
                }
            }
        } catch (_: Throwable) {}

        // Priority 3: ActivityManager running tasks fallback
        try {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val processes = am?.runningAppProcesses
            if (!processes.isNullOrEmpty()) {
                val fgProcess = processes.firstOrNull { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                if (fgProcess != null && fgProcess.processName != appContext.packageName) {
                    return fgProcess.processName
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    /**
     * Restricts the AccessibilityService package scope to only authorized target packages,
     * ensuring excluded applications (including banking and payment apps) are completely omitted.
     */
    fun updateAccessibilityScoping() {
        val service = AutoTextAccessibilityService.getInstance() ?: return
        try {
            val info = service.serviceInfo ?: return
            if (prefs.isSmartAccessibilityScopingEnabled) {
                val allowedPackages = BASE_AUTOMATION_TARGET_PACKAGES.toMutableSet()
                allowedPackages.add(appContext.packageName)

                // Remove any packages explicitly marked as excluded
                for ((pkg, config) in configCache) {
                    if (config.isExcluded) {
                        allowedPackages.remove(pkg)
                    }
                }
                info.packageNames = allowedPackages.toTypedArray()
                Log.d(TAG, "Accessibility service scoped to ${allowedPackages.size} packages: $allowedPackages")
            } else {
                info.packageNames = null
            }
            service.serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "Error updating accessibility service scoping", e)
        }
    }

    /**
     * If dynamic on-demand mode is active, ensures accessibility service is turned on right before
     * executing an automation task.
     */
    fun prepareAccessibilityForAutomation(): Boolean {
        if (!prefs.isDynamicA11yOnDemandEnabled) return true
        return try {
            AccessibilityHealthManager.enableAccessibilityService(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "prepareAccessibilityForAutomation error: ${e.message}")
            false
        }
    }

    /**
     * If dynamic on-demand mode is active, cleanly disables the accessibility service after an automation task finishes.
     */
    fun releaseAccessibilityAfterAutomation() {
        if (!prefs.isDynamicA11yOnDemandEnabled) return
        try {
            AccessibilityHealthManager.disableAccessibilityService(appContext)
            Log.i(TAG, "Dynamic on-demand accessibility service disabled after automation")
        } catch (e: Exception) {
            Log.w(TAG, "releaseAccessibilityAfterAutomation error: ${e.message}")
        }
    }
}
