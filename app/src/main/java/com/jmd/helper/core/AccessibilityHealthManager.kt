/**
 * Package: com.jmd.helper.core
 * Module: Core Task Utilities
 *
 * Provides watchdog monitoring, connection heartbeat tracking, privileged service healing,
 * and manufacturer-specific battery optimization management.
 */
package com.jmd.helper.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.HelperApplication
import com.jmd.helper.receiver.HelperDeviceAdminReceiver
import com.jmd.helper.service.TaskAccessibilityService
import com.jmd.helper.service.TaskNotificationListenerService

/**
 * Service lifecycle supervisor and watchdog manager for Android Accessibility and Notification listeners.
 *
 * Enforces critical system invariant: **NEVER alters device display auto-rotate settings or unrelated OS configurations.**
 */
object AccessibilityHealthManager {

    private const val TAG = "A11yHealth"

    /** Milliseconds timestamp of the most recent service heartbeat. */
    @Volatile
    var lastHeartbeatTime: Long = System.currentTimeMillis()

    /**
     * Records a heartbeat pulse from the active accessibility service to verify live IPC connection.
     */
    fun recordHeartbeat() {
        lastHeartbeatTime = System.currentTimeMillis()
    }

    /**
     * Enforces the system invariant that auto-rotate is never enabled by Helper.
     * When [PreferenceManager.isPreventSystemAutoRotateEnabled] is true, checks [Settings.System.ACCELEROMETER_ROTATION]
     * and forces it to 0 (portrait locked) if enabled.
     */
    fun enforceAutoRotateInvariant(context: Context) {
        val prefs = PreferenceManager(context.applicationContext)
        if (!prefs.isPreventSystemAutoRotateEnabled) return

        try {
            val cr = context.contentResolver
            val isAutoRotateOn = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            if (isAutoRotateOn) {
                if (Settings.System.canWrite(context) || hasWriteSecureSettings(context)) {
                    Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0)
                    Log.i(TAG, "enforceAutoRotateInvariant: Reset system auto-rotate to OFF (portrait locked).")
                } else {
                    Log.d(TAG, "enforceAutoRotateInvariant: write permission not held, skipping.")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "enforceAutoRotateInvariant skipped: ${e.message}")
        }
    }

    /**
     * Inspects system secure settings to verify whether this app's accessibility service is enabled.
     *
     * @param context Active Android [Context].
     * @return True if registered in Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.
     */
    fun isAccessibilityEnabledInSystem(context: Context): Boolean {
        return try {
            val expectedShort = ComponentName(context, TaskAccessibilityService::class.java).flattenToString()
            val expectedFull = "${context.packageName}/${TaskAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: ""
            enabledServices.contains(expectedShort) || enabledServices.contains(expectedFull) ||
                (enabledServices.contains(context.packageName) && enabledServices.contains("TaskAccessibilityService"))
        } catch (e: Exception) {
            Log.w(TAG, "Error checking enabled accessibility services: ${e.message}")
            false
        }
    }

    enum class ServiceState {
        OPERATIONAL,
        STANDBY,
        ENABLED_BUT_NOT_BOUND,
        DISABLED
    }

    /**
     * Inspects whether the Accessibility Service component is enabled in the PackageManager.
     */
    fun isServiceComponentEnabled(context: Context): Boolean {
        return try {
            val component = ComponentName(context, TaskAccessibilityService::class.java)
            val state = context.packageManager.getComponentEnabledSetting(component)
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (e: Exception) {
            AppFileLogger.d(context, TAG, "isServiceComponentEnabled note: ${e.message}")
            true
        }
    }

    /**
     * Ensures the Accessibility Service component is explicitly enabled in the PackageManager.
     * Prevents OEM security scanners or previous disable commands from making the service invisible in Settings.
     */
    fun ensureServiceComponentEnabled(context: Context) {
        try {
            val component = ComponentName(context, TaskAccessibilityService::class.java)
            val pm = context.packageManager
            val state = pm.getComponentEnabledSetting(component)
            if (state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.i(TAG, "ensureServiceComponentEnabled: Set component state to ENABLED in PackageManager")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure service component enabled: ${e.message}")
        }
    }

    /**
     * Evaluates the comprehensive lifecycle state of the Accessibility Service,
     * cleanly differentiating between Android Settings configuration, PackageManager component state,
     * and real-time binder liveliness.
     */
    fun getServiceState(context: Context): ServiceState {
        if (!isServiceComponentEnabled(context)) {
            return ServiceState.DISABLED
        }
        if (isServiceOperational()) {
            return ServiceState.OPERATIONAL
        }
        val prefs = PreferenceManager(context)
        if (prefs.isA11yUserPaused) {
            return ServiceState.STANDBY
        }
        val settingEnabled = isAccessibilityEnabledInSystem(context)
        return when {
            settingEnabled -> ServiceState.ENABLED_BUT_NOT_BOUND
            else -> ServiceState.DISABLED
        }
    }

    /**
     * Inspects whether the accessibility service instance binder is actively connected and running in memory.
     *
     * @return True if live service instance is non-null and running.
     */
    fun isServiceRunning(): Boolean {
        return TaskAccessibilityService.isServiceRunning && TaskAccessibilityService.getInstance() != null
    }

    /**
     * Verifies that the service is connected and ready to process UI actions.
     */
    fun isServiceOperational(): Boolean {
        return TaskAccessibilityService.isServiceRunning && TaskAccessibilityService.getInstance() != null
    }

    /**
     * Alias for [isServiceOperational].
     */
    fun isServiceConnected(): Boolean = isServiceOperational()

    /**
     * Checks if the Accessibility Service is enabled in system settings.
     */
    fun isAccessibilitySettingEnabled(context: Context): Boolean = isAccessibilityEnabledInSystem(context)

    /**
     * Polls for accessibility service readiness up to [timeout] before dependent tasks run.
     */
    fun waitForServiceReadiness(
        context: Context,
        timeout: Duration = 5.seconds,
    ): Boolean = waitForServiceReadiness(context, timeout.inWholeMilliseconds)

    /**
     * Polls for accessibility service readiness up to [timeoutMs] before dependent tasks run.
     * Legacy overload accepting milliseconds.
     */
    fun waitForServiceReadiness(context: Context, timeoutMs: Long): Boolean {
        if (isServiceOperational()) return true
        if (hasWriteSecureSettings(context) && !isAccessibilityEnabledInSystem(context)) {
            enableAccessibilityService(context)
        }
        val start = System.currentTimeMillis()
        while (!isServiceOperational() && (System.currentTimeMillis() - start) < timeoutMs) {
            runCatching { Thread.sleep(120) }
        }
        return isServiceOperational()
    }

    /**
     * Cooperatively suspends polling for accessibility service readiness up to [timeout] before dependent tasks run.
     * Prevents blocking worker threads in coroutine scopes.
     */
    suspend fun awaitServiceReadiness(
        context: Context,
        timeout: Duration = 5.seconds,
    ): Boolean = awaitServiceReadiness(context, timeout.inWholeMilliseconds)

    /**
     * Cooperatively suspends polling for accessibility service readiness up to [timeoutMs] before dependent tasks run.
     * Legacy overload accepting milliseconds.
     */
    suspend fun awaitServiceReadiness(context: Context, timeoutMs: Long): Boolean {
        if (isServiceOperational()) return true
        if (hasWriteSecureSettings(context) && !isAccessibilityEnabledInSystem(context)) {
            enableAccessibilityService(context)
        }
        val start = System.currentTimeMillis()
        while (!isServiceOperational() && (System.currentTimeMillis() - start) < timeoutMs) {
            delay(100.milliseconds)
        }
        return isServiceOperational()
    }

    /**
     * Verifies if app holds the privileged [android.Manifest.permission.WRITE_SECURE_SETTINGS] permission.
     *
     * @param context Active Android [Context].
     * @return True if granted via ADB or system image.
     */
    fun hasWriteSecureSettings(context: Context): Boolean {
        return try {
            context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            AppFileLogger.d(context, TAG, "hasWriteSecureSettings note: ${e.message}")
            false
        }
    }

    /**
     * Completely disables the accessibility service and removes it from system settings if privileged.
     * Activates Banking App Shield to ensure complete financial application isolation and prevent accessibility conflicts with banking and payment apps.
     */
    fun disableAccessibilityService(context: Context): Boolean {
        try {
            // 1. Ask service binder to disableSelf()
            TaskAccessibilityService.disableServiceIfActive()

            // 2. If WRITE_SECURE_SETTINGS is present, remove Helper from ENABLED_ACCESSIBILITY_SERVICES
            if (hasWriteSecureSettings(context)) {
                val cr = context.contentResolver
                val expectedShort = ComponentName(context, TaskAccessibilityService::class.java).flattenToString()
                val expectedFull = "${context.packageName}/${TaskAccessibilityService::class.java.name}"
                val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                val withoutMe = current.splitToSequence(":")
                    .map { it.trim() }
                    .filter { token ->
                        token.isNotBlank() && token != expectedShort && token != expectedFull &&
                            !(token.contains(context.packageName) && token.contains("TaskAccessibilityService"))
                    }
                    .distinct()
                    .joinToString(":")
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, withoutMe)
                if (withoutMe.isBlank()) {
                    Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "0")
                }
                Log.i(TAG, "Bank Shield: Detached accessibility service from secure settings (remaining=$withoutMe)")
            }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "disableAccessibilityService error: ${e.message}")
            return false
        }
    }

    /**
     * Connects the accessibility service on-demand via Secure Settings.
     */
    fun enableAccessibilityService(context: Context): Boolean {
        try {
            ensureServiceComponentEnabled(context)
            if (!hasWriteSecureSettings(context)) {
                Log.w(TAG, "enableAccessibilityService: WRITE_SECURE_SETTINGS not granted")
                return false
            }
            val cr = context.contentResolver
            val expectedShort = ComponentName(context, TaskAccessibilityService::class.java).flattenToString()
            val expectedFull = "${context.packageName}/${TaskAccessibilityService::class.java.name}"
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""

            // Strip any existing Helper tokens first to ensure a clean ContentObserver trigger in AOSP
            val cleanExisting = current.splitToSequence(":")
                .map { it.trim() }
                .filter { token ->
                    token.isNotBlank() && token != expectedShort && token != expectedFull &&
                        !(token.contains(context.packageName) && token.contains("TaskAccessibilityService"))
                }
                .distinct()
                .joinToString(":")

            val updated = if (cleanExisting.isBlank()) expectedShort else "$cleanExisting:$expectedShort"
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
            Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            Log.i(TAG, "enableAccessibilityService: Enabled accessibility service via Secure Settings ($updated)")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "enableAccessibilityService error: ${e.message}")
            return false
        }
    }

    /**
     * Recycles the accessibility service registration via Secure Settings to heal dropped binders.
     *
     * Strictly avoids writing invalid dummy component names and never touches system auto-rotate settings.
     * Skips healing if Banking Shield is enabled.
     *
     * @param context Active Android [Context].
     * @return True if successfully re-registered in secure settings.
     */
    fun autoHealAccessibility(context: Context): Boolean {
        if (!hasWriteSecureSettings(context)) {
            Log.w(TAG, "autoHealAccessibility: WRITE_SECURE_SETTINGS not granted")
            return false
        }
        val prefs = PreferenceManager(context)
        val fg = TaskAccessibilityService.currentForegroundPackage
        if (prefs.isA11yUserPaused || (fg != null && (TaskAccessibilityService.isFinancialOrBankingPackage(fg) || ProtectedApplicationManager.isProtectedPackage(fg)))) {
            Log.i(TAG, "autoHealAccessibility: Skipped because banking app is foreground or user paused is active (fg=$fg)")
            return false
        }
        if (prefs.isDynamicA11yOnDemandEnabled && !TaskAccessibilityService.isDispatchingActive()) {
            Log.i(TAG, "autoHealAccessibility: Skipped because dynamic on-demand is active and idle")
            return false
        }
        return try {
            val cr = context.contentResolver
            val expectedShort = ComponentName(context, TaskAccessibilityService::class.java).flattenToString()
            val expectedFull = "${context.packageName}/${TaskAccessibilityService::class.java.name}"
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""

            val withoutMe = current.splitToSequence(":")
                .map { it.trim() }
                .filter { token ->
                    token.isNotBlank() && token != expectedShort && token != expectedFull &&
                        !(token.contains(context.packageName) && token.contains("TaskAccessibilityService"))
                }
                .distinct()
                .joinToString(":")

            // Silently cycle service registration to force framework binder detachment
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, withoutMe)
            runCatching { Thread.sleep(120) }

            val updated = if (withoutMe.isBlank()) expectedShort else "$withoutMe:$expectedShort"
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
            Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")

            Log.i(TAG, "Successfully auto-healed Accessibility Service via Secure Settings!")
            true
        } catch (e: Exception) {
            Log.w(TAG, "autoHealAccessibility failed: ${e.message}")
            false
        }
    }

    /**
     * Verifies and ensures all background services (Accessibility, Notification Listener, Device Admin)
     * are active and healthy using standard Android APIs.
     *
     * @param context Active Android [Context].
     * @return True if services are confirmed active.
     */
    @Deprecated("Use ensureAllServicesActive instead for policy-compliant naming", ReplaceWith("ensureAllServicesActive(context)"))
    fun autoApproveAllPermissions(context: Context): Boolean {
        return ensureAllServicesActive(context)
    }

    /**
     * Verifies service liveliness and directly triggers healing if binder dropped.
     *
     * @param context Active Android [Context].
     * @return True if service is alive and connected.
     */
    fun ensureServiceAlive(context: Context): Boolean {
        if (isServiceRunning()) {
            return true
        }

        if (hasWriteSecureSettings(context)) {
            Log.i(TAG, "Accessibility Service binder is inactive. Enabling on-demand via WRITE_SECURE_SETTINGS...")
            val healed = enableAccessibilityService(context)
            if (healed) {
                val start = System.currentTimeMillis()
                while (!isServiceRunning() && (System.currentTimeMillis() - start) < 4000L) {
                    runCatching { Thread.sleep(100) }
                }
                if (isServiceRunning()) {
                    Log.i(TAG, "Accessibility Service binder successfully revived and confirmed connected!")
                    return true
                }
            }
        }

        return isServiceRunning()
    }

    /**
     * Ensures required services are enabled, healthy, and bound, maintaining bank-safe standby isolation.
     *
     * @param context Active Android [Context].
     * @return True if secure write succeeded and services are bound.
     */
    fun ensureAllServicesActive(context: Context): Boolean {
        ensureServiceComponentEnabled(context)
        return hasWriteSecureSettings(context) && try {
            val cr = context.contentResolver

            val a11yExpected = "${context.packageName}/${TaskAccessibilityService::class.java.name}"
            val a11yCurrent = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""

            val prefs = PreferenceManager(context)
            val fg = TaskAccessibilityService.currentForegroundPackage
            val isBankingInForeground = fg != null && (TaskAccessibilityService.isFinancialOrBankingPackage(fg) || ProtectedApplicationManager.isProtectedPackage(fg))

            if (isBankingInForeground) {
                // Banking App Shield Active: Immediately detach accessibility service from system settings
                // so banking and UPI payment apps cannot see or detect it
                disableAccessibilityService(context)
                Log.i(TAG, "Banking Shield Always-Active: Retracted accessibility service because banking app ($fg) is active")
            } else if (!prefs.isA11yUserPaused) {
                if (prefs.isDynamicA11yOnDemandEnabled && !TaskAccessibilityService.isDispatchingActive()) {
                    // Dynamic On-Demand Mode: In idle state with no active tasks running,
                    // keep accessibility service detached so Google Pay and banking apps operate without payment blocks.
                    if (isAccessibilityEnabledInSystem(context)) {
                        disableAccessibilityService(context)
                        Log.i(TAG, "On-Demand Standby: Safely detached idle accessibility service for banking/UPI compatibility")
                    }
                } else {
                    val expectedShort = ComponentName(context, TaskAccessibilityService::class.java).flattenToString()
                    val hasMe = a11yCurrent.contains(expectedShort) || a11yCurrent.contains(a11yExpected) ||
                        (a11yCurrent.contains(context.packageName) && a11yCurrent.contains("TaskAccessibilityService"))
                    if (!hasMe) {
                        val a11yUpdated = if (a11yCurrent.isBlank()) expectedShort else "$a11yCurrent:$expectedShort"
                        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, a11yUpdated)
                        Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                        Log.i(TAG, "Directly enabled Accessibility Service in settings via WRITE_SECURE_SETTINGS")
                    } else if (!isServiceRunning()) {
                        autoHealAccessibility(context)
                    }
                }
            } else {
                Log.d(TAG, "Accessibility Service is explicitly paused by user: respecting paused state")
            }

            val notifExpected = ComponentName(context, TaskNotificationListenerService::class.java).flattenToString()
            val notifCurrent = Settings.Secure.getString(cr, "enabled_notification_listeners") ?: ""
            if (!notifCurrent.contains(notifExpected)) {
                val notifUpdated = if (notifCurrent.isBlank()) notifExpected else "$notifCurrent:$notifExpected"
                Settings.Secure.putString(cr, "enabled_notification_listeners", notifUpdated)
                Log.i(TAG, "Directly enabled Notification Listener in settings via WRITE_SECURE_SETTINGS")
            }
            healNotificationListener(context)
            try {
                val appScope = (context.applicationContext as? HelperApplication)?.applicationScope
                    ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
                appScope.launch(Dispatchers.IO) {
                    HelperDeviceAdminReceiver.ensureActive(context)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Device admin activation launch note: ${t.message}")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "ensureAllServicesActive failed: ${e.message}")
            false
        }
    }

    /**
     * Requests the framework to rebind [TaskNotificationListenerService].
     *
     * @param context Active Android [Context].
     */
    fun healNotificationListener(context: Context) {
        try {
            android.service.notification.NotificationListenerService.requestRebind(
                ComponentName(context, TaskNotificationListenerService::class.java),
            )
            Log.d(TAG, "Requested rebind for NotificationListenerService")
        } catch (e: Exception) {
            Log.w(TAG, "Notification listener rebind error: ${e.message}")
        }
    }

    /**
     * Dispatches intent to launch OEM-specific background management, autostart, or battery settings.
     * Delegates to [OemBrandHelper] covering Vivo, Oppo, Samsung, Xiaomi, Motorola, Nokia, OnePlus, Realme,
     * Huawei, Honor, Transsion, and Asus.
     *
     * @param context Active Android [Context].
     */
    fun openOemBackgroundSettings(context: Context) {
        OemBrandHelper.openOemBackgroundSettings(context)
    }
}
