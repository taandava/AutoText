package com.jmd.autotext.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object AccessibilityHealthManager {

    private const val TAG = "A11yHealth"

    /**
     * Checks if AutoTextAccessibilityService is registered in system secure settings
     */
    fun isAccessibilityEnabledInSystem(context: Context): Boolean {
        return try {
            val expected = "${context.packageName}/${AutoTextAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabledServices.contains(expected)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking enabled accessibility services: ${e.message}")
            false
        }
    }

    /**
     * Checks if the Accessibility Service binder is currently alive and active
     */
    fun isServiceRunning(): Boolean {
        return AutoTextAccessibilityService.isServiceRunning
    }

    /**
     * Checks if the app has permission to write secure settings
     */
    fun hasWriteSecureSettings(context: Context): Boolean {
        return try {
            context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Automatically heals and restarts the Accessibility Service binder connection
     * by toggling it in Secure Settings without requiring manual off/on from the user.
     * Works when WRITE_SECURE_SETTINGS is granted (e.g. via ADB or system privileges).
     */
    fun autoHealAccessibility(context: Context): Boolean {
        if (!hasWriteSecureSettings(context)) {
            Log.w(TAG, "autoHealAccessibility: WRITE_SECURE_SETTINGS not granted")
            return false
        }
        return try {
            val cr = context.contentResolver
            val expected = "${context.packageName}/${AutoTextAccessibilityService::class.java.name}"
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""

            // 1. Temporarily strip our service to trigger clean unbind in Android OS
            val withoutMe = current.split(":")
                .filter { it.isNotBlank() && it != expected }
                .joinToString(":")

            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, withoutMe)

            // Allow Android's AccessibilityManagerService time to process unbind
            runCatching { Thread.sleep(300) }

            // 2. Re-add our service to force Android OS to recreate the binder
            val updated = if (withoutMe.isBlank()) expected else "$withoutMe:$expected"
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
     * Proactively verifies that the Accessibility Service binder is active and functioning.
     * If dropped or inactive, automatically revives it using WRITE_SECURE_SETTINGS and waits for binder confirmation.
     * Returns true if confirmed alive and connected, false otherwise.
     */
    fun ensureServiceAlive(context: Context): Boolean {
        if (isServiceRunning()) {
            return true
        }

        if (hasWriteSecureSettings(context)) {
            Log.i(TAG, "Accessibility Service binder is dropped or inactive. Auto-healing now via WRITE_SECURE_SETTINGS...")
            val healed = autoHealAccessibility(context)
            if (healed) {
                // Poll up to 2.5 seconds for onServiceConnected to fire
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
     * Ensures all required services and permissions are active and enabled if WRITE_SECURE_SETTINGS is available.
     */
    fun ensureAllServicesActive(context: Context): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        return try {
            val cr = context.contentResolver

            // 1. Accessibility Service Check & Heal
            val a11yExpected = "${context.packageName}/${AutoTextAccessibilityService::class.java.name}"
            val a11yCurrent = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""

            if (!a11yCurrent.contains(a11yExpected)) {
                val a11yUpdated = if (a11yCurrent.isBlank()) a11yExpected else "$a11yCurrent:$a11yExpected"
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, a11yUpdated)
                Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                Log.i(TAG, "Directly enabled Accessibility Service in settings via WRITE_SECURE_SETTINGS")
            } else if (!isServiceRunning()) {
                autoHealAccessibility(context)
            }

            // 2. Notification Listener Service
            val notifExpected = ComponentName(context, AutoTextNotificationListenerService::class.java).flattenToString()
            val notifCurrent = Settings.Secure.getString(cr, "enabled_notification_listeners") ?: ""
            if (!notifCurrent.contains(notifExpected)) {
                val notifUpdated = if (notifCurrent.isBlank()) notifExpected else "$notifCurrent:$notifExpected"
                Settings.Secure.putString(cr, "enabled_notification_listeners", notifUpdated)
                Log.i(TAG, "Directly enabled Notification Listener in settings via WRITE_SECURE_SETTINGS")
            }
            healNotificationListener(context)

            true
        } catch (e: Exception) {
            Log.e(TAG, "ensureAllServicesActive failed: ${e.message}")
            false
        }
    }

    /**
     * Heals notification listener service if disconnected
     */
    fun healNotificationListener(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.service.notification.NotificationListenerService.requestRebind(
                    ComponentName(context, AutoTextNotificationListenerService::class.java)
                )
                Log.d(TAG, "Requested rebind for NotificationListenerService")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notification listener rebind error: ${e.message}")
        }
    }

    /**
     * Opens manufacturer-specific battery optimization or background app lock screens
     */
    fun openOemBackgroundSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intentsToTry = mutableListOf<Intent>()

        when {
            manufacturer.contains("samsung") -> {
                // Samsung Device Care / Battery
                intentsToTry.add(Intent().apply {
                    component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
                })
                intentsToTry.add(Intent().apply {
                    component = ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
                })
            }
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                // Xiaomi Autostart
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

        // Standard fallback
        intentsToTry.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        intentsToTry.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
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
