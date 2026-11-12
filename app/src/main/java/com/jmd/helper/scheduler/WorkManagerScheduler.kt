/**
 * Package: com.jmd.helper.scheduler
 * Module: Schedulers & Background Workers
 *
 * What it is:
 * Architecture definition and implementation for [WorkManagerScheduler].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for WorkManagerScheduler.
 *
 * Linked With:
 * - Core Security & Utilities: [androidx.work.WorkManager], [com.jmd.helper.core.AppFileLogger], [java.util.concurrent.TimeUnit]
 * - Android Framework & Jetpack: [androidx.work.Constraints], [androidx.work.ExistingPeriodicWorkPolicy], [androidx.work.PeriodicWorkRequestBuilder]
 */

package com.jmd.helper.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jmd.helper.core.AppFileLogger
import java.util.concurrent.TimeUnit

/**
 * What it is:
 * Manages background WorkManager tasks with battery and idle constraints.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [WorkManagerScheduler].
 *
 * Linked with:
 * - Architecture: [WorkManagerScheduler], [WorkManagerScheduler]
 */
object WorkManagerScheduler {

    /**
     * What it is:
     * Enqueues daily database maintenance to run when the device is idle and not low on battery.
     *
     * What it does:
     * Executes [schedulePeriodicMaintenance] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WorkManagerScheduler], [schedulePeriodicMaintenance]
     */
    fun schedulePeriodicMaintenance(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val maintenanceRequest = PeriodicWorkRequestBuilder<DatabaseMaintenanceWorker>(
                24, TimeUnit.HOURS,
                6, TimeUnit.HOURS // flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                DatabaseMaintenanceWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                maintenanceRequest
            )
            AppFileLogger.i("WorkManagerScheduler", "Periodic database maintenance scheduled with 24h interval")
        } catch (e: Exception) {
            AppFileLogger.e("WorkManagerScheduler", "Failed to schedule maintenance worker: ${e.message}", e)
        }
    }
}
