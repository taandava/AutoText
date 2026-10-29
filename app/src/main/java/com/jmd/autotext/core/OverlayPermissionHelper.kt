/**
 * Package: com.jmd.autotext.core
 * Module: Core Automation Utilities
 *
 * Provides system overlay window permission verification and system settings intent routing.
 */
package com.jmd.autotext.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Helper utility for inspecting and requesting [Settings.ACTION_MANAGE_OVERLAY_PERMISSION].
 */
object OverlayPermissionHelper {

    /**
     * Determines whether the app has permission to draw on top of other applications.
     *
     * @param context Active Android [Context].
     * @return True if granted, false otherwise.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Constructs an explicit intent directing the user to the system overlay settings screen for this app.
     *
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
}
