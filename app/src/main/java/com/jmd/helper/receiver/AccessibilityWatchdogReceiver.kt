

/**
 * Package: com.jmd.helper.receiver
 * Module: Broadcast Receivers Layer
 *
 * Periodic watchdog receiver testing accessibility service liveness and self-healing when dead.
 *
 * Linked With:
 * - Health Manager: [com.jmd.helper.core.AccessibilityHealthManager]
 * - Scheduler: [com.jmd.helper.scheduler.AccessibilityWatchdogScheduler]
 * - Service: [com.jmd.helper.service.TaskAccessibilityService]
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
 * Watchdog broadcast receiver monitoring accessibility service health and auto-recovery.
 */
class AccessibilityWatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "A11yWatchdog"
    }

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
