/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [PrivacyManager].
 *
 * What it does:
 * Centralized, thread-safe privacy engine governing application exclusions, accessibility target scoping, sensitive content masking, overlay suppression, and clipboard protection.
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.repo.PrivacyRepository]
 * - Models & Entities: [com.jmd.helper.data.model.AppPrivacyConfig], [com.jmd.helper.data.model.GlobalPrivacySettings]
 * - Services & Engines: [com.jmd.helper.service.TaskAccessibilityService]
 * - Core Security & Utilities: [android.app.ActivityManager], [android.util.Log], [com.jmd.helper.data.prefs.PreferenceManager], [java.util.concurrent.ConcurrentHashMap]
 * - Android Framework & Jetpack: [android.os.Build], [kotlinx.coroutines.Dispatchers], [kotlinx.coroutines.delay], [kotlinx.coroutines.withContext]
 */

package com.jmd.helper.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.jmd.helper.data.model.AppPrivacyConfig
import com.jmd.helper.data.model.GlobalPrivacySettings
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.data.repo.PrivacyRepository
import com.jmd.helper.service.TaskAccessibilityService
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * What it is:
 * Central singleton orchestrating privacy rules and app visibility restrictions across Helper.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [PrivacyManager].
 *
 * Linked with:
 * - Architecture: [PrivacyManager], [PrivacyManager]
 */
class PrivacyManager private constructor(private val appContext: Context) {

    private val repository = PrivacyRepository(appContext)
    private val prefs = PreferenceManager(appContext)

    // Thread-safe in-memory cache of package configurations for sub-millisecond lookups
    private val configCache = ConcurrentHashMap<String, AppPrivacyConfig>()

    @Volatile
    private var isInitialized = false
    /**
     * What it is:
     * Companion object constant and factory provider for [PrivacyManager].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [PrivacyManager]
     */

    companion object {
        private const val TAG = "PrivacyManager"

        @Volatile
        private var instance: PrivacyManager? = null

        /**
         * What it is:
         * Returns the thread-safe singleton instance of [PrivacyManager].
         *
         * What it does:
         * Executes [getInstance] logic in accordance with system invariants.
         *
         * Linked with:
         * - Architecture: [PrivacyManager], [getInstance]
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
         * Standard allowlist of essential system and task target packages that Helper
         * requires for normal operation (unless explicitly excluded by the user).
         */
        val BASE_TASK_TARGET_PACKAGES = setOf(
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
         * What it is:
         * Masks sensitive message text (e.g.
         *
         * What it does:
         * "Meeting at 5 PM" -> "M***M" or `[PROTECTED]`).
         *
         * Linked with:
         * - Architecture: [PrivacyManager], [maskSensitiveText]
         */
        fun maskSensitiveText(text: String): String {
            if (text.isBlank()) return ""
            val trimmed = text.trim()
            if (trimmed.length <= 2) return "**"
            return "${trimmed.first()}***${trimmed.last()}"
        }

        /**
         * What it is:
         * Masks recipient phone numbers for privacy-safe logs and lockscreen alerts (e.g.
         *
         * What it does:
         * "9769602020" -> "9769***020").
         *
         * Linked with:
         * - Architecture: [PrivacyManager], [maskPhoneNumber]
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
     * What it is:
     * Initializes the privacy engine cache and seeds default banking exclusions if needed.
     *
     * What it does:
     * Executes [initialize] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [initialize]
     */
    @Synchronized
    fun initialize() {
        if (isInitialized) return
        try {
            // Seed known financial packages on initial launch
            repository.seedDefaultBankingConfigs(TaskAccessibilityService.KNOWN_FINANCIAL_PACKAGE_PREFIXES)

            // Populate in-memory cache
            refreshCache()
            isInitialized = true
            Log.d(TAG, "PrivacyManager initialized with ${configCache.size} package configs")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing PrivacyManager", e)
        }
    }

    /**
     * What it is:
     * Reloads configuration cache from persistent SQLite storage.
     *
     * What it does:
     * Executes [refreshCache] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [refreshCache]
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
     * What it is:
     * Retrieves all cached package privacy configurations.
     *
     * What it does:
     * Executes [getAllConfigs] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [getAllConfigs]
     */
    fun getAllConfigs(): List<AppPrivacyConfig> = configCache.values.toList().sortedBy { it.appName.lowercase() }

    /**
     * What it is:
     * Retrieves the specific configuration for a package, or creates a default exclusion if it's a known banking app.
     *
     * What it does:
     * Executes [getConfigForPackage] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [getConfigForPackage]
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
        if (prefs.isAutoExcludeBankingEnabled && TaskAccessibilityService.isFinancialOrBankingPackage(trimmed)) {
            val autoConfig = AppPrivacyConfig(
                packageName = trimmed,
                appName = trimmed.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                isExcluded = true,
                disableTaskAction = true,
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
     * What it is:
     * Saves or updates an individual package configuration in cache and database.
     *
     * What it does:
     * Executes [saveConfig] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [saveConfig]
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
     * What it is:
     * Saves multiple configurations in a single atomic transaction.
     *
     * What it does:
     * Executes [saveConfigs] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [saveConfigs]
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
     * What it is:
     * Deletes a package privacy configuration.
     *
     * What it does:
     * Executes [deleteConfig] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [deleteConfig]
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
     * What it is:
     * Resets all privacy configurations and preferences to factory default shield.
     *
     * What it does:
     * Executes [resetAllSettings] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [resetAllSettings]
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
        prefs.isDynamicA11yOnDemandEnabled = true
        prefs.isSuppressNotifsOverExcludedEnabled = true
        prefs.isBlockOverlaysOverExcludedEnabled = true

        // Re-seed default banking rules
        repository.seedDefaultBankingConfigs(TaskAccessibilityService.KNOWN_FINANCIAL_PACKAGE_PREFIXES)
        refreshCache()
        updateAccessibilityScoping()
        return success
    }

    /**
     * What it is:
     * Checks whether an application is marked as excluded from Helper.
     *
     * What it does:
     * Executes [isAppExcluded] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [isAppExcluded]
     */
    fun isAppExcluded(packageName: String): Boolean {
        if (!prefs.isPrivacyModeEnabled) return false
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) return false

        val config = getConfigForPackage(trimmed)
        if (config != null && config.isExcluded) return true

        if (prefs.isAutoExcludeBankingEnabled && TaskAccessibilityService.isFinancialOrBankingPackage(trimmed)) {
            return true
        }
        return false
    }

    /**
     * What it is:
     * Checks whether task actions are disallowed for a specific package.
     *
     * What it does:
     * Executes [isTaskBlockedForPackage] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [isTaskBlockedForPackage]
     */
    fun isTaskBlockedForPackage(packageName: String): Boolean {
        if (!prefs.isPrivacyModeEnabled) return false
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) return false

        val config = getConfigForPackage(trimmed)
        if (config != null) {
            return config.isExcluded && config.disableTaskAction
        }
        if (prefs.isAutoExcludeBankingEnabled && TaskAccessibilityService.isFinancialOrBankingPackage(trimmed)) {
            return true
        }
        return false
    }

    /**
     * What it is:
     * Checks whether floating overlays and popups should be blocked over the currently active foreground application.
     *
     * What it does:
     * Executes [isOverlayBlockedForActiveApp] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [isOverlayBlockedForActiveApp]
     */
    fun isOverlayBlockedForActiveApp(): Boolean {
        if (!prefs.isPrivacyModeEnabled || !prefs.isBlockOverlaysOverExcludedEnabled) return false
        val activePkg = getActiveForegroundPackage() ?: return false
        val config = getConfigForPackage(activePkg)
        if (config != null && config.isExcluded && config.blockOverlays) return true
        if (prefs.isAutoExcludeBankingEnabled && TaskAccessibilityService.isFinancialOrBankingPackage(activePkg)) {
            return true
        }
        return false
    }

    /**
     * What it is:
     * Checks whether non-critical notification popups should be suppressed over the currently active foreground application.
     *
     * What it does:
     * Executes [isNotificationSuppressedForActiveApp] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [isNotificationSuppressedForActiveApp]
     */
    fun isNotificationSuppressedForActiveApp(): Boolean {
        if (!prefs.isPrivacyModeEnabled || !prefs.isSuppressNotifsOverExcludedEnabled) return false
        val activePkg = getActiveForegroundPackage() ?: return false
        val config = getConfigForPackage(activePkg)
        if (config != null && config.isExcluded && config.suppressNotifications) return true
        if (prefs.isAutoExcludeBankingEnabled && TaskAccessibilityService.isFinancialOrBankingPackage(activePkg)) {
            return true
        }
        return false
    }

    /**
     * What it is:
     * Checks whether quick clipboard interaction is disabled for a given target package.
     *
     * What it does:
     * Executes [isClipboardDisabledForPackage] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [isClipboardDisabledForPackage]
     */
    fun isClipboardDisabledForPackage(packageName: String): Boolean {
        if (!prefs.isPrivacyModeEnabled) return false
        val trimmed = packageName.trim()
        val config = getConfigForPackage(trimmed)
        if (config != null && config.isExcluded && config.disableClipboard) return true
        if (prefs.isAutoExcludeBankingEnabled && TaskAccessibilityService.isFinancialOrBankingPackage(trimmed)) {
            return true
        }
        return false
    }

    /**
     * What it is:
     * Checks whether incoming auto-reply or trigger rules originating from a package are disabled.
     *
     * What it does:
     * Executes [isRulesDisabledForPackage] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [isRulesDisabledForPackage]
     */
    fun isRulesDisabledForPackage(packageName: String): Boolean {
        if (!prefs.isPrivacyModeEnabled) return false
        val trimmed = packageName.trim()
        val config = getConfigForPackage(trimmed)
        if (config != null && config.isExcluded && config.disableRules) return true
        if (prefs.isAutoExcludeBankingEnabled && TaskAccessibilityService.isFinancialOrBankingPackage(trimmed)) {
            return true
        }
        return false
    }

    /**
     * What it is:
     * Checks whether sensitive message content and recipient numbers related to a package should be masked.
     *
     * What it does:
     * Executes [shouldMaskSensitiveContent] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [shouldMaskSensitiveContent]
     */
    fun shouldMaskSensitiveContent(packageName: String? = null): Boolean {
        if (!prefs.isPrivacyModeEnabled) return false
        if (prefs.isMaskExecutionLogsEnabled) return true
        if (packageName != null) {
            val config = getConfigForPackage(packageName.trim())
            if (config != null && config.isExcluded && config.maskSensitiveContent) return true
            if (prefs.isAutoExcludeBankingEnabled && TaskAccessibilityService.isFinancialOrBankingPackage(packageName)) {
                return true
            }
        }
        return false
    }

    /**
     * What it is:
     * Masks sensitive message text (e.g.
     *
     * What it does:
     * "Meeting at 5 PM" -> "M***M" or `[PROTECTED]`).
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [maskSensitiveText]
     */
    fun maskSensitiveText(text: String): String = Companion.maskSensitiveText(text)

    /**
     * What it is:
     * Masks recipient phone numbers for privacy-safe logs and lockscreen alerts (e.g.
     *
     * What it does:
     * "9769602020" -> "9769***020").
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [maskPhoneNumber]
     */
    fun maskPhoneNumber(phone: String): String = Companion.maskPhoneNumber(phone)

    /**
     * What it is:
     * Detects the currently active foreground application package using AccessibilityService or ActivityManager.
     *
     * What it does:
     * Executes [getActiveForegroundPackage] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [getActiveForegroundPackage]
     */
    fun getActiveForegroundPackage(): String? {
        // Priority 1: Instant active package from Accessibility Service
        val a11yPkg = TaskAccessibilityService.currentForegroundPackage
        if (!a11yPkg.isNullOrBlank()) return a11yPkg

        // Priority 2: ActivityManager running tasks fallback
        try {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val processes = am?.runningAppProcesses
            if (!processes.isNullOrEmpty()) {
                val fgProcess = processes.firstOrNull { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                if (fgProcess != null && fgProcess.processName != appContext.packageName) {
                    return fgProcess.processName
                }
            }
        } catch (e: Throwable) { Log.d(TAG, "Foreground detection probe note: ${e.message}") }

        return null
    }

    /**
     * What it is:
     * Restricts the AccessibilityService package scope to only authorized target packages, ensuring excluded applications (including banking and payment apps) are completely omitted.
     *
     * What it does:
     * Executes [updateAccessibilityScoping] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [updateAccessibilityScoping]
     */
    fun updateAccessibilityScoping() {
        val service = TaskAccessibilityService.getInstance() ?: return
        try {
            val info = service.serviceInfo ?: return
            val allowedPackages = BASE_TASK_TARGET_PACKAGES.toMutableSet()
            allowedPackages.add(appContext.packageName)

            if (prefs.isSmartAccessibilityScopingEnabled) {
                // Remove any packages explicitly marked as excluded
                for ((pkg, config) in configCache) {
                    if (config.isExcluded) {
                        allowedPackages.remove(pkg)
                    }
                }
            }
            // Strict Invariant: info.packageNames MUST NEVER be null.
            // When packageNames is null, Android routes ALL app events (including banking & UPI) to the service,
            // which causes Google Pay, PhonePe, and NPCI security modules to block payment flows.
            info.packageNames = allowedPackages.toTypedArray()
            Log.d(TAG, "Accessibility service scoped to ${allowedPackages.size} packages: $allowedPackages")
            service.serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "Error updating accessibility service scoping", e)
        }
    }

    /**
     * What it is:
     * If dynamic on-demand mode is active, ensures accessibility service is turned on right before executing an task task.
     *
     * What it does:
     * Executes [prepareAccessibilityForTask] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [prepareAccessibilityForTask]
     */
    suspend fun prepareAccessibilityForTask(): Boolean = withContext(Dispatchers.IO) {
        if (!prefs.isDynamicA11yOnDemandEnabled) return@withContext true
        if (!AccessibilityHealthManager.hasWriteSecureSettings(appContext)) {
            // Cannot dynamically attach without WRITE_SECURE_SETTINGS; verify if user has service running manually
            return@withContext TaskAccessibilityService.getInstance() != null
        }
        return@withContext try {
            val enabled = AccessibilityHealthManager.enableAccessibilityService(appContext)
            if (!enabled) return@withContext false
            var waitMs = 0
            while (TaskAccessibilityService.getInstance() == null && waitMs < 4000) {
                delay(100)
                waitMs += 100
            }
            val ready = TaskAccessibilityService.getInstance() != null
            Log.d(TAG, "prepareAccessibilityForTask: service ready=$ready (waited ${waitMs}ms)")
            ready
        } catch (e: Exception) {
            Log.w(TAG, "prepareAccessibilityForTask error: ${e.message}")
            false
        }
    }

    /**
     * What it is:
     * If dynamic on-demand mode is active, cleanly disables the accessibility service after an task task finishes.
     *
     * What it does:
     * Executes [releaseAccessibilityAfterTask] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyManager], [releaseAccessibilityAfterTask]
     */
    fun releaseAccessibilityAfterTask() {
        if (!prefs.isDynamicA11yOnDemandEnabled) return
        if (!AccessibilityHealthManager.hasWriteSecureSettings(appContext)) {
            // Without WRITE_SECURE_SETTINGS, do not disableSelf() directly because the app cannot re-enable itself!
            return
        }
        try {
            AccessibilityHealthManager.disableAccessibilityService(appContext)
            Log.i(TAG, "Dynamic on-demand accessibility service disabled after task")
        } catch (e: Exception) {
            Log.w(TAG, "releaseAccessibilityAfterTask error: ${e.message}")
        }
    }
}

