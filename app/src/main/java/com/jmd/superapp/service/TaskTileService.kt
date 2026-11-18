/**
 * Package: com.jmd.superapp.service
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
 * - Core Security & Utilities: [com.jmd.superapp.core.AccessibilityHealthManager], [com.jmd.superapp.core.showAppToast], [com.jmd.superapp.data.prefs.PreferenceManager]
 * - Android Framework & Jetpack: [android.os.Build], [android.provider.Settings]
 */

package com.jmd.superapp.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.jmd.superapp.core.AccessibilityHealthManager
import com.jmd.superapp.core.showAppToast
import com.jmd.superapp.data.prefs.PreferenceManager

/**
 * What it is:
 * System Quick Settings Tile for Super Accessibility Service.
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
            showAppToast("Super A11y Paused. Banking & payment apps protected.")
            updateTileState()
        } else {
            // Tap to resume / enable accessibility
            prefs.isA11yUserPaused = false
            if (AccessibilityHealthManager.hasWriteSecureSettings(context)) {
                AccessibilityHealthManager.enableAccessibilityService(context)
                showAppToast("Super A11y Active.")
                updateTileState()
            } else {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                showAppToast("Tap Super in Downloaded Apps to enable")
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
                com.jmd.superapp.core.AppFileLogger.w("TaskTileService", "startActivityAndCollapse fallback: ${e.message}")
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
            tile.label = "Super Assistant"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Active"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Super Assistant"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Paused"
            }
        }
        tile.updateTile()
    }
}
