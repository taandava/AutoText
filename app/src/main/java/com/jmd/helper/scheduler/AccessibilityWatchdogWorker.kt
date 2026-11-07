/**
 * Package: com.jmd.helper.scheduler
 * Module: Schedulers Layer
 *
 * Background WorkManager [CoroutineWorker] executing resilient watchdog monitoring of
 * [com.jmd.helper.service.TaskAccessibilityService] and [com.jmd.helper.service.TaskNotificationListenerService].
 *
 * Linked With:
 * - Scheduler: [com.jmd.helper.scheduler.AccessibilityWatchdogScheduler]
 * - Health Manager: [com.jmd.helper.core.AccessibilityHealthManager]
 * - Receiver: [com.jmd.helper.receiver.AccessibilityWatchdogReceiver]
 */
package com.jmd.helper.scheduler

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.ProtectedApplicationManager
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.service.TaskAccessibilityService

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
        const val UNIQUE_WORK_NAME = "HelperA11yWatchdogWork"
    }

    override suspend fun doWork(): Result {
        return try {
            // Uninterrupted background task: keep accessibility and notification services active
            val prefs = PreferenceManager(applicationContext)
            val fg = TaskAccessibilityService.currentForegroundPackage
            val isBanking = fg != null && (TaskAccessibilityService.isFinancialOrBankingPackage(fg) || ProtectedApplicationManager.isProtectedPackage(fg))
            if (!prefs.isA11yUserPaused && !isBanking) {
                AccessibilityHealthManager.ensureAllServicesActive(applicationContext)
            } else {
                Log.d(TAG, "Watchdog skipping auto-heal: banking app or user paused active")
            }
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
