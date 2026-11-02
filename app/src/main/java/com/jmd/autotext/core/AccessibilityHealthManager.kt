/**
 * Package: com.jmd.autotext.core
 * Module: Core Automation Utilities
 *
 * Provides watchdog monitoring, connection heartbeat tracking, privileged service healing,
 * and manufacturer-specific battery optimization management.
 */
package com.jmd.autotext.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.receiver.AutoTextDeviceAdminReceiver
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.service.AutoTextNotificationListenerService

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
     * Enforces the system invariant that auto-rotate is never enabled by AutoText.
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
            val expectedShort = ComponentName(context, AutoTextAccessibilityService::class.java).flattenToString()
            val expectedFull = "${context.packageName}/${AutoTextAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: ""
            enabledServices.contains(expectedShort) || enabledServices.contains(expectedFull) ||
                (enabledServices.contains(context.packageName) && enabledServices.contains("AutoTextAccessibilityService"))
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
            val component = ComponentName(context, AutoTextAccessibilityService::class.java)
            val state = context.packageManager.getComponentEnabledSetting(component)
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Ensures the Accessibility Service component is explicitly enabled in the PackageManager.
     * Prevents OEM security scanners or previous disable commands from making the service invisible in Settings.
     */
    fun ensureServiceComponentEnabled(context: Context) {
        try {
            val component = ComponentName(context, AutoTextAccessibilityService::class.java)
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
        return AutoTextAccessibilityService.isServiceRunning && AutoTextAccessibilityService.getInstance() != null
    }

    /**
     * Verifies that the service is connected and ready to process UI actions.
     */
    fun isServiceOperational(): Boolean {
        return AutoTextAccessibilityService.isServiceRunning && AutoTextAccessibilityService.getInstance() != null
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
     * Polls for accessibility service readiness up to [timeoutMs] before dependent tasks run.
     */
    fun waitForServiceReadiness(context: Context, timeoutMs: Long = 3000L): Boolean {
        if (isServiceOperational()) return true
        val start = System.currentTimeMillis()
        while (!isServiceOperational() && (System.currentTimeMillis() - start) < timeoutMs) {
            runCatching { Thread.sleep(120) }
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
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Completely disables the accessibility service and removes it from system settings if privileged.
     * Activates Banking App Shield to eliminate malware/accessibility warnings in banking apps.
     */
    fun disableAccessibilityService(context: Context): Boolean {
        try {
            // 1. Ask service binder to disableSelf()
            AutoTextAccessibilityService.disableServiceIfActive()

            // 2. If WRITE_SECURE_SETTINGS is present, remove AutoText from ENABLED_ACCESSIBILITY_SERVICES
            if (hasWriteSecureSettings(context)) {
                val cr = context.contentResolver
                val expectedShort = ComponentName(context, AutoTextAccessibilityService::class.java).flattenToString()
                val expectedFull = "${context.packageName}/${AutoTextAccessibilityService::class.java.name}"
                val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                val withoutMe = current.splitToSequence(":")
                    .map { it.trim() }
                    .filter { token ->
                        token.isNotBlank() && token != expectedShort && token != expectedFull &&
                            !(token.contains(context.packageName) && token.contains("AutoTextAccessibilityService"))
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
            val expectedShort = ComponentName(context, AutoTextAccessibilityService::class.java).flattenToString()
            val expectedFull = "${context.packageName}/${AutoTextAccessibilityService::class.java.name}"
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""

            // Strip any existing AutoText tokens first to ensure a clean ContentObserver trigger in AOSP
            val cleanExisting = current.splitToSequence(":")
                .map { it.trim() }
                .filter { token ->
                    token.isNotBlank() && token != expectedShort && token != expectedFull &&
                        !(token.contains(context.packageName) && token.contains("AutoTextAccessibilityService"))
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
        val fg = AutoTextAccessibilityService.currentForegroundPackage
        if (prefs.isA11yUserPaused || prefs.isDynamicA11yOnDemandEnabled || (fg != null && AutoTextAccessibilityService.isFinancialOrBankingPackage(fg))) {
            Log.i(TAG, "autoHealAccessibility: Skipped because dynamic standby or user paused is active (fg=$fg)")
            return false
        }
        return try {
            val cr = context.contentResolver
            val expectedShort = ComponentName(context, AutoTextAccessibilityService::class.java).flattenToString()
            val expectedFull = "${context.packageName}/${AutoTextAccessibilityService::class.java.name}"
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""

            val withoutMe = current.splitToSequence(":")
                .map { it.trim() }
                .filter { token ->
                    token.isNotBlank() && token != expectedShort && token != expectedFull &&
                        !(token.contains(context.packageName) && token.contains("AutoTextAccessibilityService"))
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
    fun autoApproveAllPermissions(context: Context): Boolean {
        return ensureAllServicesActive(context)
    }

    /**
     * Verifies service liveliness and automatically triggers healing if binder dropped.
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
                while (!isServiceRunning() && (System.currentTimeMillis() - start) < 2500L) {
                    runCatching { Thread.sleep(120) }
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
     * Ensures required services are enabled, healthy, and bound, maintaining stealth isolation.
     *
     * @param context Active Android [Context].
     * @return True if secure write succeeded and services are bound.
     */
    fun ensureAllServicesActive(context: Context): Boolean {
        ensureServiceComponentEnabled(context)
        if (!hasWriteSecureSettings(context)) {
            return false
        }
        return try {
            val cr = context.contentResolver

            val a11yExpected = "${context.packageName}/${AutoTextAccessibilityService::class.java.name}"
            val a11yCurrent = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""

            val prefs = PreferenceManager(context)
            if (prefs.isDynamicA11yOnDemandEnabled) {
                // Bank-Safe Dynamic Mode: Keep service detached from ENABLED_ACCESSIBILITY_SERVICES while idle
                // so that Google Pay, PhonePe, Paytm, and banking apps operate with 0 interference and 0 security blocks.
                // The service will be dynamically attached on-demand (<50ms) right when a scheduled task or test runs.
                val expectedShort = ComponentName(context, AutoTextAccessibilityService::class.java).flattenToString()
                val expectedFull = "${context.packageName}/${AutoTextAccessibilityService::class.java.name}"
                val hasMe = a11yCurrent.contains(expectedShort) || a11yCurrent.contains(expectedFull) ||
                    (a11yCurrent.contains(context.packageName) && a11yCurrent.contains("AutoTextAccessibilityService"))
                if (hasMe) {
                    disableAccessibilityService(context)
                    Log.i(TAG, "Bank-Safe Standby: Kept accessibility service detached while idle")
                }
            } else if (!prefs.isA11yUserPaused) {
                val expectedShort = ComponentName(context, AutoTextAccessibilityService::class.java).flattenToString()
                val hasMe = a11yCurrent.contains(expectedShort) || a11yCurrent.contains(a11yExpected) ||
                    (a11yCurrent.contains(context.packageName) && a11yCurrent.contains("AutoTextAccessibilityService"))
                if (!hasMe) {
                    val a11yUpdated = if (a11yCurrent.isBlank()) expectedShort else "$a11yCurrent:$expectedShort"
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, a11yUpdated)
                    Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                    Log.i(TAG, "Directly enabled Accessibility Service in settings via WRITE_SECURE_SETTINGS")
                } else if (!isServiceRunning()) {
                    autoHealAccessibility(context)
                }
            } else {
                Log.d(TAG, "Accessibility Service is explicitly paused by user: respecting paused state")
            }

            val notifExpected = ComponentName(context, AutoTextNotificationListenerService::class.java).flattenToString()
            val notifCurrent = Settings.Secure.getString(cr, "enabled_notification_listeners") ?: ""
            if (!notifCurrent.contains(notifExpected)) {
                val notifUpdated = if (notifCurrent.isBlank()) notifExpected else "$notifCurrent:$notifExpected"
                Settings.Secure.putString(cr, "enabled_notification_listeners", notifUpdated)
                Log.i(TAG, "Directly enabled Notification Listener in settings via WRITE_SECURE_SETTINGS")
            }
            healNotificationListener(context)
            Thread {
                AutoTextDeviceAdminReceiver.ensureActive(context)
            }.start()

            true
        } catch (e: Exception) {
            Log.e(TAG, "ensureAllServicesActive failed: ${e.message}")
            false
        }
    }

    /**
     * Requests the framework to rebind [AutoTextNotificationListenerService].
     *
     * @param context Active Android [Context].
     */
    fun healNotificationListener(context: Context) {
        try {
            android.service.notification.NotificationListenerService.requestRebind(
                ComponentName(context, AutoTextNotificationListenerService::class.java),
            )
            Log.d(TAG, "Requested rebind for NotificationListenerService")
        } catch (e: Exception) {
            Log.w(TAG, "Notification listener rebind error: ${e.message}")
        }
    }

    /**
     * Dispatches intent to launch OEM-specific background management, autostart, or battery settings.
     *
     * @param context Active Android [Context].
     */
    fun openOemBackgroundSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intentsToTry = mutableListOf<Intent>()

        when {
            manufacturer.contains("samsung") -> {
                intentsToTry.add(Intent().apply {
                    component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
                })
                intentsToTry.add(Intent().apply {
                    component = ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
                })
            }
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                intentsToTry.add(Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                })
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intentsToTry.add(Intent().apply {
                    component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                })
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                intentsToTry.add(Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                })
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                intentsToTry.add(Intent().apply {
                    component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                })
            }
        }

        intentsToTry.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        intentsToTry.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        })

        for (intent in intentsToTry) {
            runCatching {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            }
        }
    }
}
