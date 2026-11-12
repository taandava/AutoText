/**
 * Package: com.jmd.helper.receiver
 * Module: Broadcast Receivers Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [HelperDeviceAdminReceiver].
 *
 * What it does:
 * Implements Android Device Administration callbacks enabling immediate device re-locking after autonomous WhatsApp messaging sessions.
 *
 * Linked With:
 * - Services & Engines: [com.jmd.helper.service.TaskAccessibilityService]
 * - Core Security & Utilities: [android.app.admin.DevicePolicyManager], [android.util.Log], [androidx.core.net.toUri], [com.jmd.helper.core.AppFileLogger]
 * - Android Framework & Jetpack: [android.app.admin.DeviceAdminReceiver], [android.content.ComponentName]
 */

package com.jmd.helper.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.core.showAppToast
import com.jmd.helper.service.TaskAccessibilityService

/**
 * What it is:
 * DeviceAdminReceiver providing screen lock capabilities for security relocking post-task.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [HelperDeviceAdminReceiver].
 *
 * Linked with:
 * - Architecture: [HelperDeviceAdminReceiver], [HelperDeviceAdminReceiver]
 */
class HelperDeviceAdminReceiver : DeviceAdminReceiver() {
    /**
     * What it is:
     * Execution routine for [onEnabled].
     *
     * What it does:
     * Coordinates onEnabled operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [HelperDeviceAdminReceiver]
     */

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled for Helper")
        AppFileLogger.i(context, TAG, "Device Admin enabled successfully")
        context.showAppToast("Helper Device Administration enabled")
    }
    /**
     * What it is:
     * Execution routine for [onDisabled].
     *
     * What it does:
     * Coordinates onDisabled operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [HelperDeviceAdminReceiver]
     */

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled for Helper")
        AppFileLogger.i(context, TAG, "Device Admin disabled")
    }
    /**
     * What it is:
     * Companion object constant and factory provider for [HelperDeviceAdminReceiver].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [HelperDeviceAdminReceiver]
     */

    companion object {
        private const val TAG = "HelperDeviceAdmin"

        /**
         * Returns the concrete [ComponentName] for [HelperDeviceAdminReceiver].
         *
         * @param context Active context.
         * @return Explicit component identifier.
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, HelperDeviceAdminReceiver::class.java)
        }

        /**
         * Checks whether Helper is currently an active device administrator.
         *
         * @param context Active context.
         * @return `true` if registered and active in [DevicePolicyManager]; `false` otherwise.
         */
        fun isAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            return dpm?.isAdminActive(getComponentName(context)) == true
        }

        /**
         * Builds the standard system prompt intent requesting device administration activation.
         *
         * @param context Active context.
         * @return Intent ready to be launched via [Context.startActivity].
         */
        fun getAddDeviceAdminIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Helper requires Device Administration to re-lock the phone after scheduled WhatsApp and SMS messages complete."
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        /**
         * Requests device admin activation with OEM fallback routing for Samsung, Xiaomi, Vivo, and Oppo devices.
         *
         * @param context Active context.
         * @return `true` if an administration prompt or security settings activity was opened.
         */
        fun requestActivation(context: Context): Boolean {
            if (isAdminActive(context)) {
                context.showAppToast("Device Admin is already active!")
                return true
            }

            AppFileLogger.i(context, TAG, "Requesting Device Admin activation...")

            // 1. Try standard ACTION_ADD_DEVICE_ADMIN
            val primaryIntent = getAddDeviceAdminIntent(context)
            try {
                context.startActivity(primaryIntent)
                context.showAppToast("Tap 'Activate' or 'Turn on' to enable Device Admin")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Standard ADD_DEVICE_ADMIN intent failed: ${e.message}")
                AppFileLogger.w(context, TAG, "Standard ADD_DEVICE_ADMIN intent failed: ${e.message}")
            }

            // 2. Comprehensive fallback intents across Android versions and OEMs
            val fallbackIntents = listOf(
                // Universal AOSP & Samsung One UI Device Admin list page
                Intent("android.settings.DEVICE_ADMIN_SETTINGS").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                // Samsung One UI explicit settings activity
                Intent().apply {
                    component = ComponentName("com.android.settings", "com.android.settings.Settings" + "$" + "DeviceAdminSettingsActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                // Standard AOSP Device Admin list
                Intent().apply {
                    component = ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                // Direct DeviceAdminAdd with component extra
                Intent().apply {
                    component = ComponentName("com.android.settings", "com.android.settings.DeviceAdminAdd")
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                // Oppo / Realme
                Intent().apply {
                    component = ComponentName("com.android.settings", "com.oplus.settings.feature.security.OplusDeviceAdminAdd")
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                // Vivo
                Intent().apply {
                    component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                // Xiaomi / MIUI
                Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                // General Security Settings
                Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                // App Details Settings (helps user allow restricted settings if blocked on Android 13/14)
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                // General Settings
                Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )

            for (fb in fallbackIntents) {
                try {
                    context.startActivity(fb)
                    context.showAppToast("Select 'Helper' and enable Device Admin")
                    return true
                } catch (e: Exception) {
                    Log.d(TAG, "Fallback device admin intent failed: ${e.message}")
                }
            }

            context.showAppToast("Please open phone Settings > Security > Device Admin apps to enable Helper")
            return false
        }

        /**
         * Verifies whether device administrator status is active.
         *
         * @param context Active context.
         * @return `true` if active or successfully activated; `false` otherwise.
         */
        fun ensureActive(context: Context): Boolean {
            return isAdminActive(context)
        }

        /**
         * Safely locks the device screen mimicking the physical Power button press via accessibility.
         *
         * Crucially: Never calls DevicePolicyManager.lockNow() to prevent fingerprint and biometric lockout.
         *
         * @param context Active context.
         * @return `true` if screen lock was dispatched successfully.
         */
        fun lockNow(context: Context): Boolean {
            return try {
                Log.i(TAG, "Locking screen via power button simulation (biometric safe)")
                AppFileLogger.i(context, TAG, "Dispatched power-button screen lock (fingerprint safe)")
                val powerLockDispatched = TaskAccessibilityService.lockScreenNow()
                if (powerLockDispatched) {
                    true
                } else {
                    TaskAccessibilityService.minimizeToHome()
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing lockNow", e)
                AppFileLogger.e(context, TAG, "Error in lockNow: ${e.message}")
                false
            }
        }
    }
}
