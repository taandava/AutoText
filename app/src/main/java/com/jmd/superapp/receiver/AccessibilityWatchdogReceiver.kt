/**
 * Package: com.jmd.superapp.receiver
 * Module: Broadcast Receivers Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [AccessibilityWatchdogReceiver].
 *
 * What it does:
 * Periodic watchdog receiver testing accessibility service liveness and self-healing when unresponsive.
 *
 * Linked With:
 * - Schedulers & Receivers: [com.jmd.superapp.scheduler.AccessibilityWatchdogScheduler]
 * - Services & Engines: [com.jmd.superapp.service.TaskAccessibilityService]
 * - Core Security & Utilities: [android.os.PowerManager], [android.util.Log], [com.jmd.superapp.core.AccessibilityHealthManager], [com.jmd.superapp.core.ProtectedApplicationManager]
 * - Android Framework & Jetpack: [android.content.BroadcastReceiver]
 */

package com.jmd.superapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.jmd.superapp.core.AccessibilityHealthManager
import com.jmd.superapp.core.ProtectedApplicationManager
import com.jmd.superapp.scheduler.AccessibilityWatchdogScheduler
import com.jmd.superapp.service.TaskAccessibilityService

/**
 * What it is:
 * Watchdog broadcast receiver monitoring accessibility service health and auto-recovery.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [AccessibilityWatchdogReceiver].
 *
 * Linked with:
 * - Architecture: [AccessibilityWatchdogReceiver], [AccessibilityWatchdogReceiver]
 */
class AccessibilityWatchdogReceiver : BroadcastReceiver() {
    /**
     * What it is:
     * Companion object constant and factory provider for [AccessibilityWatchdogReceiver].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [AccessibilityWatchdogReceiver]
     */

    companion object {
        private const val TAG = "A11yWatchdog"
    }
    /**
     * What it is:
     * Execution routine for [onReceive].
     *
     * What it does:
     * Coordinates onReceive operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AccessibilityWatchdogReceiver]
     */

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Super:WatchdogWakeLock"
        )
        wakeLock?.acquire(2000L)

        try {
            val now = System.currentTimeMillis()
            val heartbeatStale = (now - AccessibilityHealthManager.lastHeartbeatTime) > 30 * 60 * 1000L
            val prefs = com.jmd.superapp.data.prefs.PreferenceManager(context)
            val fg = TaskAccessibilityService.currentForegroundPackage
            val isBanking = fg != null && (TaskAccessibilityService.isFinancialOrBankingPackage(fg) || ProtectedApplicationManager.isProtectedPackage(fg))
            val isA11yUnresponsive = !AccessibilityHealthManager.isServiceRunning() || heartbeatStale
            val shouldReviveA11y = if (prefs.isDynamicA11yOnDemandEnabled) {
                TaskAccessibilityService.isDispatchingActive() && isA11yUnresponsive
            } else {
                isA11yUnresponsive
            }

            if (shouldReviveA11y && !prefs.isA11yUserPaused && !isBanking) {
                Log.d(TAG, "Watchdog heartbeat tick: A11y inactive or stale while dispatching. Reviving services...")
                AccessibilityHealthManager.ensureAllServicesActive(context)
            } else if (prefs.isDynamicA11yOnDemandEnabled && !TaskAccessibilityService.isDispatchingActive() &&
                AccessibilityHealthManager.hasWriteSecureSettings(context)
            ) {
                if (AccessibilityHealthManager.isAccessibilityEnabledInSystem(context)) {
                    AccessibilityHealthManager.disableAccessibilityService(context)
                }
                Log.d(TAG, "Watchdog heartbeat tick: On-demand standby idle; a11y detached.")
            } else if (isBanking) {
                Log.d(TAG, "Watchdog heartbeat tick: banking app active; isolation guard holds, a11y stays bound.")
            }
            AccessibilityHealthManager.healNotificationListener(context)
            AccessibilityHealthManager.recordHeartbeat()
            if (!SuperDeviceAdminReceiver.isAdminActive(context)) {
                SuperDeviceAdminReceiver.ensureActive(context)
            }
            AccessibilityWatchdogScheduler.scheduleHeartbeat(context)
        } catch (t: Throwable) {
            Log.e(TAG, "Error in watchdog service verification", t)
        } finally {
            try {
                wakeLock?.let {
                    if (it.isHeld) it.release()
                }
            } catch (t: Throwable) {
                Log.d(TAG, "WakeLock release: ${t.message}")
            }
        }
    }
}
