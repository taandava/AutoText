/**
 * Package: com.jmd.helper.service
 * Module: Background Services & Quick Tiles
 *
 * What it is:
 * Architecture definition and implementation for [TaskTileService].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for TaskTileService.
 *
 * Linked With:
 * - Services & Engines: [android.service.quicksettings.Tile], [android.service.quicksettings.TileService]
 * - Core Security & Utilities: [com.jmd.helper.core.AccessibilityHealthManager], [com.jmd.helper.core.showAppToast], [com.jmd.helper.data.prefs.PreferenceManager]
 * - Android Framework & Jetpack: [android.os.Build], [android.provider.Settings]
 */

package com.jmd.helper.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.showAppToast
import com.jmd.helper.data.prefs.PreferenceManager

/**
 * What it is:
 * System Quick Settings Tile for Helper Accessibility Service.
 *
 * What it does:
 * Allows users to 1-tap pause Accessibility (via disableSelf) before opening banking or payment apps, and 1-tap resume or open Accessibility Settings.
 *
 * Linked with:
 * - Architecture: [TaskTileService], [TaskTileService]
 */
class TaskTileService : TileService() {
    /**
     * What it is:
     * Execution routine for [onStartListening].
     *
     * What it does:
     * Coordinates onStartListening operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskTileService]
     */

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }
    /**
     * What it is:
     * Execution routine for [onClick].
     *
     * What it does:
     * Coordinates onClick operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskTileService]
     */

    override fun onClick() {
        super.onClick()
        val context = applicationContext ?: this
        val prefs = PreferenceManager(context)
        val isRunning = AccessibilityHealthManager.isServiceOperational()

        if (isRunning) {
            // Tap to pause accessibility immediately for Banking / Payment security
            prefs.isA11yUserPaused = true
            TaskAccessibilityService.disableServiceIfActive()
            if (AccessibilityHealthManager.hasWriteSecureSettings(context)) {
                AccessibilityHealthManager.disableAccessibilityService(context)
            }
            showAppToast("Helper A11y Paused. Banking & payment apps protected.")
            updateTileState()
        } else {
            // Tap to resume / enable accessibility
            prefs.isA11yUserPaused = false
            if (AccessibilityHealthManager.hasWriteSecureSettings(context)) {
                AccessibilityHealthManager.enableAccessibilityService(context)
                showAppToast("Helper A11y Active.")
                updateTileState()
            } else {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                showAppToast("Tap Helper in Downloaded Apps to enable")
                launchSettingsAndCollapse(intent)
            }
        }
    }
    /**
     * What it is:
     * Execution routine for [launchSettingsAndCollapse].
     *
     * What it does:
     * Coordinates launchSettingsAndCollapse operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskTileService]
     */

    private fun launchSettingsAndCollapse(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            try {
                val method = TileService::class.java.getMethod("startActivityAndCollapse", Intent::class.java)
                method.invoke(this, intent)
            } catch (e: Throwable) {
                com.jmd.helper.core.AppFileLogger.w("TaskTileService", "startActivityAndCollapse fallback: ${e.message}")
                startActivity(intent)
            }
        }
    }
    /**
     * What it is:
     * Execution routine for [updateTileState].
     *
     * What it does:
     * Coordinates updateTileState operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskTileService]
     */

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = AccessibilityHealthManager.isServiceOperational()
        if (isRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Helper Assistant"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Active (Tap to Pause)"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Helper Assistant"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Paused (Banking Safe)"
            }
        }
        tile.updateTile()
    }
}
