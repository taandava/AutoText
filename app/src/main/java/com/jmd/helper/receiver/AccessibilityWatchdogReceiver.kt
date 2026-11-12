/**
 * Package: com.jmd.helper.receiver
 * Module: Broadcast Receivers Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [AccessibilityWatchdogReceiver].
 *
 * What it does:
 * Periodic watchdog receiver testing accessibility service liveness and self-healing when dead.
 *
 * Linked With:
 * - Schedulers & Receivers: [com.jmd.helper.scheduler.AccessibilityWatchdogScheduler]
 * - Services & Engines: [com.jmd.helper.service.TaskAccessibilityService]
 * - Core Security & Utilities: [android.os.PowerManager], [android.util.Log], [com.jmd.helper.core.AccessibilityHealthManager], [com.jmd.helper.core.ProtectedApplicationManager]
 * - Android Framework & Jetpack: [android.content.BroadcastReceiver]
 */

package com.jmd.helper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.ProtectedApplicationManager
import com.jmd.helper.scheduler.AccessibilityWatchdogScheduler
import com.jmd.helper.service.TaskAccessibilityService

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
            "Helper:WatchdogWakeLock"
        )
        wakeLock?.acquire(2000L)

        try {
            val now = System.currentTimeMillis()
            val heartbeatStale = (now - AccessibilityHealthManager.lastHeartbeatTime) > 30 * 60 * 1000L
            val prefs = com.jmd.helper.data.prefs.PreferenceManager(context)
            val fg = TaskAccessibilityService.currentForegroundPackage
            val isBanking = fg != null && (TaskAccessibilityService.isFinancialOrBankingPackage(fg) || ProtectedApplicationManager.isProtectedPackage(fg))
            val isA11yDead = !AccessibilityHealthManager.isServiceRunning() || heartbeatStale
            val shouldReviveA11y = if (prefs.isDynamicA11yOnDemandEnabled) {
                TaskAccessibilityService.isDispatchingActive() && isA11yDead
            } else {
                isA11yDead
            }

            if (shouldReviveA11y && !prefs.isA11yUserPaused && !isBanking) {
                Log.d(TAG, "Watchdog heartbeat tick: A11y dead or stale while dispatching. Reviving services...")
                AccessibilityHealthManager.ensureAllServicesActive(context)
            } else if (isBanking || (prefs.isDynamicA11yOnDemandEnabled && !TaskAccessibilityService.isDispatchingActive())) {
                if (prefs.isDynamicA11yOnDemandEnabled && !TaskAccessibilityService.isDispatchingActive() && AccessibilityHealthManager.isAccessibilityEnabledInSystem(context)) {
                    AccessibilityHealthManager.disableAccessibilityService(context)
                }
                Log.d(TAG, "Watchdog heartbeat tick: On-demand idle or banking active; keeping a11y detached.")
            }
            AccessibilityHealthManager.healNotificationListener(context)
            AccessibilityHealthManager.recordHeartbeat()
            if (!HelperDeviceAdminReceiver.isAdminActive(context)) {
                HelperDeviceAdminReceiver.ensureActive(context)
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
