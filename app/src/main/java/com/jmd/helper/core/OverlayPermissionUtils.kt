/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [OverlayPermissionUtils].
 *
 * What it does:
 * Provides system overlay window permission verification and system settings intent routing.
 *
 * Linked With:
 * - Core Security & Utilities: [androidx.core.net.toUri]
 * - Android Framework & Jetpack: [android.provider.Settings]
 */

package com.jmd.helper.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Helper utility for inspecting and requesting [Settings.ACTION_MANAGE_OVERLAY_PERMISSION].
 */
typealias OverlayPermissionUtils = OverlayPermissionHelper
/**
 * What it is:
 * Component definition for [OverlayPermissionHelper].
 *
 * What it does:
 * Provides domain logic and lifecycle management for OverlayPermissionHelper.
 *
 * Linked with:
 * - Component: [OverlayPermissionUtils]
 */

object OverlayPermissionHelper {

    /**
     * What it is:
     * Determines whether the app has permission to draw on top of other applications.
     *
     * What it does:
     * Executes [canDrawOverlays] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [OverlayPermissionUtils], [canDrawOverlays]
     * @param context Active Android [Context].
     * @return True if granted, false otherwise.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * What it is:
     * Constructs an explicit intent directing the user to the system overlay settings screen for this app.
     *
     * What it does:
     * Executes [getManageOverlayIntent] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [OverlayPermissionUtils], [getManageOverlayIntent]
     * @param context Active Android [Context].
     * @return Prepared [Intent] with NEW_TASK flags.
     */
    fun getManageOverlayIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * What it is:
     * Constructs a generic intent directing the user to the system-wide overlay apps list.
     *
     * What it does:
     * Useful as a fallback when an OEM settings application does not support direct package URIs.
     *
     * Linked with:
     * - Architecture: [OverlayPermissionUtils], [getManageOverlayGenericIntent]
     * @return Prepared generic [Intent] with NEW_TASK flags.
     */
    fun getManageOverlayGenericIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * What it is:
     * Safely launches the system overlay settings screen with multi-tiered fallback handling.
     *
     * What it does:
     * 1. Attempts direct app-specific overlay toggle via `package:<packageName>`. 2. Attempts OEM-specific floating window / overlay manager screens (Xiaomi, Vivo, Oppo, Honor). 3. Falls back to generic system overlay list if package-specific route is not supported by OEM ROM. 4. Falls back to application details settings if overlay action is completely unavailable.
     *
     * Linked with:
     * - Architecture: [OverlayPermissionUtils], [launchOverlaySettings]
     * @param context Active Android [Context].
     * @return True if an intent was successfully dispatched; false otherwise.
     */
    fun launchOverlaySettings(context: Context): Boolean {
        // 1. Direct standard package overlay intent
        try {
            context.startActivity(getManageOverlayIntent(context))
            return true
        } catch (e: Exception) {
            AppFileLogger.w("OverlayPermissionHelper", "Direct package overlay intent failed: ${e.message}, trying OEM-specific overlay intents")
        }

        // 2. OEM-specific floating window intents
        for (oemIntent in OemBrandHelper.getSpecialOverlayIntents(context)) {
            try {
                oemIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(oemIntent)
                return true
            } catch (e: Exception) {
                AppFileLogger.d("OverlayPermissionHelper", "OEM overlay intent fallback: ${e.message}")
            }
        }

        // 3. Generic system-wide overlay list
        try {
            context.startActivity(getManageOverlayGenericIntent())
            return true
        } catch (e2: Exception) {
            AppFileLogger.w("OverlayPermissionHelper", "Generic overlay intent failed: ${e2.message}, falling back to App Details")
        }

        // 4. App Details settings
        return try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appDetailsIntent)
            true
        } catch (e3: Exception) {
            AppFileLogger.e("OverlayPermissionHelper", "All overlay settings intents failed: ${e3.message}", e3)
            false
        }
    }
}
