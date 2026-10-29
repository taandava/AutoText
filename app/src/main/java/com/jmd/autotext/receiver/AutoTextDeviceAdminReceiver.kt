

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
 * DeviceAdminReceiver providing screen lock capabilities for security relocking post-automation.
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
                    "AutoText requires Device Administration to automatically re-lock the phone after scheduled WhatsApp and SMS messages complete."
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
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "AutoText requires Device Administration to automatically re-lock the phone after scheduled WhatsApp and SMS messages complete.")
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
         * Verifies or activates device administrator status, checking if root/privileged activation is feasible.
         *
         * @param context Active context.
         * @return `true` if active or successfully activated; `false` otherwise.
         */
        fun ensureActive(context: Context): Boolean {
            if (isAdminActive(context)) return true
            Log.d(TAG, "Device Admin is inactive; checking privileged activation...")
            return try {
                val comp = getComponentName(context).flattenToString()
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "dpm set-active-admin $comp"))
                val completed = process.waitFor(1200L, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!completed) {
                    process.destroy()
                    return false
                }
                isAdminActive(context)
            } catch (e: Exception) {
                Log.d(TAG, "Privileged dpm activation not supported: ${e.message}")
                false
            }
        }

        /**
         * Instantly locks the device screen via [DevicePolicyManager.lockNow].
         *
         * Used to enforce security relocking post-automation when WhatsApp automation finishes.
         *
         * @param context Active context.
         * @return `true` if lockNow executed successfully; `false` on failure.
         */
        fun lockNow(context: Context): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                val comp = getComponentName(context)
                if (dpm != null && dpm.isAdminActive(comp)) {
                    Log.i(TAG, "Locking device via DevicePolicyManager.lockNow()")
                    AppFileLogger.i(context, TAG, "Device locked via DevicePolicyManager")
                    dpm.lockNow()
                    true
                } else {
                    Log.w(TAG, "Device Admin is not active, attempting ensureActive before lockNow()")
                    if (ensureActive(context) && dpm?.isAdminActive(comp) == true) {
                        dpm.lockNow()
                        true
                    } else {
                        Log.w(TAG, "Device Admin could not be activated, cannot call lockNow()")
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing lockNow()", e)
                AppFileLogger.e(context, TAG, "Error in lockNow(): ${e.message}")
                false
            }
        }
    }
}
