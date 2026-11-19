/**
 * Package: com.jmd.superapp.scheduler
 * Module: Schedulers & Background Workers
 *
 * What it is:
 * Architecture definition and implementation for [AccessibilityWatchdogWorker].
 *
 * What it does:
 * Background WorkManager [CoroutineWorker] executing resilient watchdog monitoring of [com.jmd.superapp.service.TaskAccessibilityService] and [com.jmd.superapp.service.TaskNotificationListenerService].
 *
 * Linked With:
 * - Schedulers & Receivers: [androidx.work.CoroutineWorker], [androidx.work.WorkerParameters]
 * - Services & Engines: [com.jmd.superapp.service.TaskAccessibilityService]
 * - Core Security & Utilities: [android.util.Log], [com.jmd.superapp.core.AccessibilityHealthManager], [com.jmd.superapp.core.ProtectedApplicationManager], [com.jmd.superapp.data.prefs.PreferenceManager]
 */

package com.jmd.superapp.scheduler

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jmd.superapp.core.AccessibilityHealthManager
import com.jmd.superapp.core.ProtectedApplicationManager
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.service.TaskAccessibilityService

/**
 * What it is:
 * Dual-redundant background worker evaluating accessibility and notification listener service health every 15 minutes to survive aggressive OEM process terminations and Doze mode.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [AccessibilityWatchdogWorker].
 *
 * Linked with:
 * - Architecture: [AccessibilityWatchdogWorker], [AccessibilityWatchdogWorker]
 */
class AccessibilityWatchdogWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    /**
     * What it is:
     * Companion object constant and factory provider for [AccessibilityWatchdogWorker].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [AccessibilityWatchdogWorker]
     */

    companion object {
        private const val TAG = "A11yWatchdogWorker"
        const val UNIQUE_WORK_NAME = "SuperA11yWatchdogWork"
    }
    /**
     * What it is:
     * Execution routine for [doWork].
     *
     * What it does:
     * Coordinates doWork operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AccessibilityWatchdogWorker]
     */

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
