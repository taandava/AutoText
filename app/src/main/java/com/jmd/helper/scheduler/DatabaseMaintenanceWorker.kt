/**
 * Package: com.jmd.helper.scheduler
 * Module: Schedulers & Background Workers
 *
 * What it is:
 * Architecture definition and implementation for [DatabaseMaintenanceWorker].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for DatabaseMaintenanceWorker.
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.db.DatabaseHelper]
 * - Schedulers & Receivers: [androidx.work.CoroutineWorker], [androidx.work.WorkerParameters]
 * - Core Security & Utilities: [com.jmd.helper.core.AppFileLogger]
 * - Android Framework & Jetpack: [kotlinx.coroutines.Dispatchers], [kotlinx.coroutines.withContext]
 */

package com.jmd.helper.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.data.db.DatabaseHelper
import com.jmd.helper.data.prefs.AppDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What it is:
 * Background WorkManager worker performing periodic system hygiene and maintenance: 1.
 *
 * What it does:
 * Pruning execution logs older than 30 days. 2. Self-healing orphaned tasks stuck in RUNNING state. 3. Reclaiming SQLite disk space via VACUUM and updating optimizer statistics via ANALYZE. 4. Updating maintenance telemetry in AppDataStore.
 *
 * Linked with:
 * - Architecture: [DatabaseMaintenanceWorker], [DatabaseMaintenanceWorker]
 */
class DatabaseMaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    /**
     * What it is:
     * Execution routine for [doWork].
     *
     * What it does:
     * Coordinates doWork operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DatabaseMaintenanceWorker]
     */

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            AppFileLogger.i("DatabaseMaintenanceWorker", "Starting scheduled background database maintenance")
            val dbHelper = DatabaseHelper.getInstance(applicationContext)
            val db = dbHelper.writableDatabase

            // 1. Prune logs older than 30 days
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val deletedLogs = db.delete(DatabaseHelper.TABLE_LOGS, "timestamp < ?", arrayOf(thirtyDaysAgo.toString()))
            AppFileLogger.i("DatabaseMaintenanceWorker", "Pruned $deletedLogs old execution logs")

            // 2. Reconcile orphaned running tasks
            val tenMinutesAgo = System.currentTimeMillis() - (10L * 60 * 1000)
            val cv = android.content.ContentValues().apply {
                put("status", "PENDING")
            }
            val resetTasks = db.update(DatabaseHelper.TABLE_TASKS, cv, "status = 'RUNNING' AND trigger_time < ?", arrayOf(tenMinutesAgo.toString()))
            if (resetTasks > 0) {
                AppFileLogger.w("DatabaseMaintenanceWorker", "Reconciled $resetTasks orphaned RUNNING tasks to PENDING")
            }

            // 3. Compact SQLite WAL and rebuild query analyzer indices
            try {
                db.execSQL("PRAGMA optimize;")
                db.execSQL("VACUUM;")
                db.execSQL("ANALYZE;")
                AppFileLogger.i("DatabaseMaintenanceWorker", "VACUUM and ANALYZE executed successfully")
            } catch (e: Exception) {
                AppFileLogger.w("DatabaseMaintenanceWorker", "VACUUM optimization note: ${e.message}")
            }

            // 4. Update DataStore maintenance timestamp
            val dataStore = AppDataStore.getInstance(applicationContext)
            dataStore.setLastMaintenanceTimestamp(System.currentTimeMillis())

            AppFileLogger.i("DatabaseMaintenanceWorker", "Database maintenance completed successfully")
            Result.success()
        } catch (e: Exception) {
            AppFileLogger.e("DatabaseMaintenanceWorker", "Database maintenance failed: ${e.message}", e)
            Result.retry()
        }
    }
    /**
     * What it is:
     * Companion object constant and factory provider for [DatabaseMaintenanceWorker].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [DatabaseMaintenanceWorker]
     */

    companion object {
        const val WORK_NAME = "helper_database_maintenance_work"
    }
}
