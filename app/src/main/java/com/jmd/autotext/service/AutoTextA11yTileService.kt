package com.jmd.autotext.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.prefs.PreferenceManager

/**
 * System Quick Settings Tile for AutoText Accessibility Service.
 *
 * Allows users to 1-tap pause Accessibility (via disableSelf) before opening Google Pay or banking apps,
 * and 1-tap resume or open Accessibility Settings.
 */
@RequiresApi(Build.VERSION_CODES.N)
class AutoTextA11yTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val context = applicationContext ?: this
        val prefs = PreferenceManager(context)
        val isRunning = AccessibilityHealthManager.isServiceOperational()

        if (isRunning) {
            // Tap to pause accessibility immediately for Google Pay / Banking security
            prefs.isA11yUserPaused = true
            AutoTextAccessibilityService.disableServiceIfActive()
            if (AccessibilityHealthManager.hasWriteSecureSettings(context)) {
                AccessibilityHealthManager.disableAccessibilityService(context)
            }
            showAppToast("AutoText A11y Paused. Google Pay & Bank apps unblocked.")
            updateTileState()
        } else {
            // Tap to resume / enable accessibility
            prefs.isA11yUserPaused = false
            if (AccessibilityHealthManager.hasWriteSecureSettings(context)) {
                AccessibilityHealthManager.enableAccessibilityService(context)
                showAppToast("AutoText A11y Active.")
                updateTileState()
            } else {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                showAppToast("Tap AutoText in Downloaded Apps to enable")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        val pi = android.app.PendingIntent.getActivity(
                            this,
                            0,
                            intent,
                            android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        startActivityAndCollapse(pi)
                    } catch (_: Throwable) {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(intent)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = AccessibilityHealthManager.isServiceOperational()
        if (isRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "AutoText A11y"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Active (Tap to Pause for GPay)"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "AutoText A11y"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Paused (Safe for GPay)"
            }
        }
        tile.updateTile()
    }
}

