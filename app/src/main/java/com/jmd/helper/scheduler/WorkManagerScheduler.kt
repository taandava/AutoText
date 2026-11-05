package com.jmd.helper.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jmd.helper.core.AppFileLogger
import java.util.concurrent.TimeUnit

/**
 * Manages background WorkManager tasks with battery and idle constraints.
 */
object WorkManagerScheduler {

    /**
     * Enqueues daily database maintenance to run when the device is idle and not low on battery.
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

