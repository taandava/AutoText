

/**
 * Package: com.jmd.autotext.receiver
 * Module: Broadcast Receivers Layer
 *
 * Periodic watchdog receiver testing accessibility service liveness and self-healing when dead.
 *
 * Linked With:
 * - Health Manager: [com.jmd.autotext.core.AccessibilityHealthManager]
 * - Scheduler: [com.jmd.autotext.scheduler.AccessibilityWatchdogScheduler]
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 */
package com.jmd.autotext.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.scheduler.AccessibilityWatchdogScheduler

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
            "AutoText:WatchdogWakeLock"
        )
        wakeLock?.acquire(2000L)

        try {
            val now = System.currentTimeMillis()
            val heartbeatStale = (now - AccessibilityHealthManager.lastHeartbeatTime) > 30 * 60 * 1000L
            val prefs = com.jmd.autotext.data.prefs.PreferenceManager(context)
            val isBankingShield = prefs.isBankingShieldEnabled
            val isA11yDead = !AccessibilityHealthManager.isServiceRunning() || heartbeatStale

            val isDynamicA11y = prefs.isDynamicA11yOnDemandEnabled
            if (isA11yDead && !isBankingShield && !isDynamicA11y) {
                Log.d(TAG, "Watchdog heartbeat tick: A11y dead or stale (stale=$heartbeatStale). Reviving services...")
                AccessibilityHealthManager.ensureAllServicesActive(context)
            } else if (isBankingShield || isDynamicA11y) {
                Log.d(TAG, "Watchdog heartbeat tick: Dynamic a11y or banking shield active; skipping auto-heal.")
            }
            AccessibilityHealthManager.healNotificationListener(context)
            AccessibilityHealthManager.recordHeartbeat()
            if (!AutoTextDeviceAdminReceiver.isAdminActive(context)) {
                AutoTextDeviceAdminReceiver.ensureActive(context)
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
