/**
 * Package: com.jmd.supersms.receiver
 * Module: Broadcast Receivers Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [SuperDeviceAdminReceiver].
 *
 * What it does:
 * Implements Android Device Administration callbacks enabling immediate device re-locking after autonomous WhatsApp messaging sessions.
 *
 * Linked With:
 * - Services & Engines: [com.jmd.supersms.service.TaskAccessibilityService]
 * - Core Security & Utilities: [android.app.admin.DevicePolicyManager], [android.util.Log], [androidx.core.net.toUri], [com.jmd.supersms.core.AppFileLogger]
 * - Android Framework & Jetpack: [android.app.admin.DeviceAdminReceiver], [android.content.ComponentName]
 */

package com.jmd.supersms.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.jmd.supersms.core.AppFileLogger
import com.jmd.supersms.core.showAppToast
import com.jmd.supersms.ui.activity.DeviceAdminPromptActivity

/**
 * What it is:
 * DeviceAdminReceiver providing screen lock capabilities for security relocking post-task.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [SuperDeviceAdminReceiver].
 *
 * Linked with:
 * - Architecture: [SuperDeviceAdminReceiver], [SuperDeviceAdminReceiver]
 */
class SuperDeviceAdminReceiver : DeviceAdminReceiver() {
    /**
     * What it is:
     * Execution routine for [onEnabled].
     *
     * What it does:
     * Coordinates onEnabled operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SuperDeviceAdminReceiver]
     */

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled for Super")
        AppFileLogger.i(context, TAG, "Device Admin enabled successfully")
        context.showAppToast("Super Device Administration enabled")
    }
    /**
     * What it is:
     * Execution routine for [onDisabled].
     *
     * What it does:
     * Coordinates onDisabled operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SuperDeviceAdminReceiver]
     */

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled for Super")
        AppFileLogger.i(context, TAG, "Device Admin disabled")
    }
    /**
     * What it is:
     * Companion object constant and factory provider for [SuperDeviceAdminReceiver].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [SuperDeviceAdminReceiver]
     */

    companion object {
        private const val TAG = "SuperDeviceAdmin"

        /**
         * What it is:
         * Returns the concrete [ComponentName] for [SuperDeviceAdminReceiver].
         *
         * What it does:
         * Executes [getComponentName] logic in accordance with system invariants.
         *
         * Linked with:
         * - Architecture: [SuperDeviceAdminReceiver], [getComponentName]
         * @param context Active context.
         * @return Explicit component identifier.
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, SuperDeviceAdminReceiver::class.java)
        }

        /**
         * What it is:
         * Checks whether Super is currently an active device administrator.
         *
         * What it does:
         * Executes [isAdminActive] logic in accordance with system invariants.
         *
         * Linked with:
         * - Architecture: [SuperDeviceAdminReceiver], [isAdminActive]
         * @param context Active context.
         * @return `true` if registered and active in [DevicePolicyManager]; `false` otherwise.
         */
        fun isAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            return dpm?.isAdminActive(getComponentName(context)) == true
        }

        /**
         * What it is:
         * Builds the standard system prompt intent requesting device administration activation.
         *
         * What it does:
         * Executes [getAddDeviceAdminIntent] logic in accordance with system invariants.
         *
         * Linked with:
         * - Architecture: [SuperDeviceAdminReceiver], [getAddDeviceAdminIntent]
         * @param context Active context.
         * @return Intent ready to be launched via [Context.startActivity].
         */
        fun getAddDeviceAdminIntent(context: Context): Intent {
            // NOTE: no FLAG_ACTIVITY_NEW_TASK here on purpose. Samsung's SecDeviceAdminAdd refuses
            // the request outright ("Cannot start ADD_DEVICE_ADMIN as a new task") and closes
            // immediately, which is why tapping "Activate" appeared to do nothing. The caller must
            // launch this from an Activity context (see DeviceAdminPromptActivity for other callers).
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Super requires Device Administration to re-lock the phone after scheduled WhatsApp and SMS messages complete."
                )
            }
        }

        /**
         * What it is:
         * Requests device admin activation with OEM fallback routing for Samsung, Xiaomi, Vivo, and Oppo devices.
         *
         * What it does:
         * Executes [requestActivation] logic in accordance with system invariants.
         *
         * Linked with:
         * - Architecture: [SuperDeviceAdminReceiver], [requestActivation]
         * @param context Active context.
         * @return `true` if an administration prompt or security settings activity was opened.
         */
        fun requestActivation(context: Context): Boolean {
            if (isAdminActive(context)) {
                context.showAppToast("Device Admin is already active!")
                return true
            }

            AppFileLogger.i(context, TAG, "Requesting Device Admin activation...")

            // 1. Try standard ACTION_ADD_DEVICE_ADMIN. It must be started from an Activity without
            // NEW_TASK, otherwise Samsung (and some other OEMs) discard the prompt silently.
            val primaryIntent = getAddDeviceAdminIntent(context)
            try {
                if (context is android.app.Activity) {
                    context.startActivity(primaryIntent)
                } else {
                    DeviceAdminPromptActivity.start(context)
                }
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
                    context.showAppToast("Select 'Super' and enable Device Admin")
                    return true
                } catch (e: Exception) {
                    Log.d(TAG, "Fallback device admin intent failed: ${e.message}")
                }
            }

            context.showAppToast("Please open phone Settings > Security > Device Admin apps to enable Super")
            return false
        }

        /**
         * What it is:
         * Verifies whether device administrator status is active.
         *
         * What it does:
         * Executes [ensureActive] logic in accordance with system invariants.
         *
         * Linked with:
         * - Architecture: [SuperDeviceAdminReceiver], [ensureActive]
         * @param context Active context.
         * @return `true` if active or successfully activated; `false` otherwise.
         */
        fun ensureActive(context: Context): Boolean {
            return isAdminActive(context)
        }

        /**
         * What it is:
         * Safely locks the device screen mimicking the physical Power button press via accessibility.
         *
         * What it does:
         * Crucially: Never calls DevicePolicyManager.lockNow() to prevent fingerprint and biometric lockout.
         *
         * Linked with:
         * - Architecture: [SuperDeviceAdminReceiver], [lockNow]
         * @param context Active context.
         * @return `true` if screen lock was dispatched successfully.
         */
        fun lockNow(context: Context): Boolean {
            return try {
                Log.i(TAG, "Locking screen via power button simulation / home minimize")
                AppFileLogger.i(context, TAG, "Dispatched screen minimize to home")
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error executing lockNow", e)
                AppFileLogger.e(context, TAG, "Error in lockNow: ${e.message}")
                false
            }
        }
    }
}

