package com.jmd.autotext.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.jmd.autotext.util.AppFileLogger

/**
 * DeviceAdminReceiver for AutoText.
 * Grants hardware-level screen lock capability (lockNow) so the device can be
 * automatically re-locked after automated messaging tasks finish.
 */
class AutoTextDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled for AutoText")
        AppFileLogger.i(context, TAG, "Device Admin enabled successfully")
        Toast.makeText(context, "AutoText Device Administration enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled for AutoText")
        AppFileLogger.i(context, TAG, "Device Admin disabled")
    }

    companion object {
        private const val TAG = "AutoTextDeviceAdmin"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, AutoTextDeviceAdminReceiver::class.java)
        }

        fun isAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            return dpm?.isAdminActive(getComponentName(context)) == true
        }

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

        fun requestActivation(context: Context): Boolean {
            val primaryIntent = getAddDeviceAdminIntent(context)
            try {
                if (primaryIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(primaryIntent)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Standard ADD_DEVICE_ADMIN intent failed: ${e.message}")
            }

            // Fallback 1: Open system Device Admin Apps settings list directly (works across ColorOS, MIUI, OneUI, Stock)
            val fallbackIntents = listOf(
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$DeviceAdminSettingsActivity")),
                Intent().setComponent(ComponentName("com.android.settings", "com.oplus.settings.feature.security.OplusDeviceAdminAdd")).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                },
                Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS),
                Intent(android.provider.Settings.ACTION_SETTINGS)
            )

            for (fb in fallbackIntents) {
                try {
                    fb.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    if (fb.resolveActivity(context.packageManager) != null) {
                        context.startActivity(fb)
                        return true
                    }
                } catch (_: Exception) {}
            }
            return false
        }

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
                    Log.w(TAG, "Device Admin is not active, cannot call lockNow()")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing lockNow()", e)
                AppFileLogger.e(context, TAG, "Error in lockNow(): ${e.message}")
                false
            }
        }
    }
}
