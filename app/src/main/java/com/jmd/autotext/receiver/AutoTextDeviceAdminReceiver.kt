

/**
 * Package: com.jmd.autotext.receiver
 * Module: Broadcast Receivers Layer
 *
 * Implements Android Device Administration callbacks enabling immediate device re-locking
 * after autonomous WhatsApp messaging sessions.
 *
 * Linked With:
 * - Engine: [com.jmd.autotext.whatsapp.KeyguardUnlockEngine]
 * - Diagnostics: [com.jmd.autotext.core.AppFileLogger]
 * - UI Screens: `PermissionScreen`
 */
package com.jmd.autotext.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.core.showAppToast

/**
 * DeviceAdminReceiver providing screen lock capabilities for security relocking post-task.
 */
class AutoTextDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled for AutoText")
        AppFileLogger.i(context, TAG, "Device Admin enabled successfully")
        context.showAppToast("AutoText Device Administration enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled for AutoText")
        AppFileLogger.i(context, TAG, "Device Admin disabled")
    }

    companion object {
        private const val TAG = "AutoTextDeviceAdmin"

        /**
         * Returns the concrete [ComponentName] for [AutoTextDeviceAdminReceiver].
         *
         * @param context Active context.
         * @return Explicit component identifier.
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, AutoTextDeviceAdminReceiver::class.java)
        }

        /**
         * Checks whether AutoText is currently an active device administrator.
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
                    "AutoText requires Device Administration to re-lock the phone after scheduled WhatsApp and SMS messages complete."
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
            val primaryIntent = getAddDeviceAdminIntent(context)
            try {
                context.startActivity(primaryIntent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Standard ADD_DEVICE_ADMIN intent failed: ${e.message}")
            }

            val fallbackIntents = listOf(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "AutoText requires Device Administration to re-lock the phone after scheduled WhatsApp and SMS messages complete.")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.DeviceAdminAdd")).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings" + "$" + "DeviceAdminSettingsActivity")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().setComponent(ComponentName("com.android.settings", "com.oplus.settings.feature.security.OplusDeviceAdminAdd")).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )

            for (fb in fallbackIntents) {
                try {
                    context.startActivity(fb)
                    return true
                } catch (e: Exception) {
                    Log.d(TAG, "Fallback device admin intent failed: ${e.message}")
                }
            }
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
                val powerLockDispatched = com.jmd.autotext.service.AutoTextAccessibilityService.lockScreenNow()
                if (powerLockDispatched) {
                    true
                } else {
                    com.jmd.autotext.service.AutoTextAccessibilityService.minimizeToHome()
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
