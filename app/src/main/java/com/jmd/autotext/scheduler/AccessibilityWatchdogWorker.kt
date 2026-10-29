/**
 * Package: com.jmd.autotext.scheduler
 * Module: Schedulers Layer
 *
 * Background WorkManager [CoroutineWorker] executing resilient watchdog monitoring of
 * [com.jmd.autotext.service.AutoTextAccessibilityService] and [com.jmd.autotext.service.AutoTextNotificationListenerService].
 *
 * Linked With:
 * - Scheduler: [com.jmd.autotext.scheduler.AccessibilityWatchdogScheduler]
 * - Health Manager: [com.jmd.autotext.core.AccessibilityHealthManager]
 * - Receiver: [com.jmd.autotext.receiver.AccessibilityWatchdogReceiver]
 */
package com.jmd.autotext.scheduler

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.data.prefs.PreferenceManager

/**
 * Dual-redundant background worker evaluating accessibility and notification listener service
 * health every 15 minutes to survive aggressive OEM process terminations and Doze mode.
 */
class AccessibilityWatchdogWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "A11yWatchdogWorker"
        const val UNIQUE_WORK_NAME = "AutoTextA11yWatchdogWork"
    }

    override suspend fun doWork(): Result {
        return try {
            // Uninterrupted background automation: keep accessibility and notification services active
            AccessibilityHealthManager.ensureAllServicesActive(applicationContext)
            AccessibilityHealthManager.healNotificationListener(applicationContext)
            AccessibilityHealthManager.recordHeartbeat()
            AccessibilityWatchdogScheduler.scheduleHeartbeat(applicationContext)
            Log.d(TAG, "Watchdog background verification succeeded")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Watchdog worker execution error", t)
            Result.retry()
        }
    }
}
