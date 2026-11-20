/**
 * Package: com.jmd.superwa
 * Module: Application Root
 *
 * What it is:
 * Central Android Application entry point and process lifecycle orchestrator for Super.
 *
 * What it does:
 * - Bootstraps the application process and provides global singleton access.
 * - Sets up defensive global uncaught exception handler with immediate diagnostic log dumping.
 * - Creates and registers system-level notification channels for tasks and alarms.
 * - Enforces accessibility service component enablement and auto-rotation invariance.
 * - Spawns structured background coroutine to recover interrupted tasks, re-arm pending alarms,
 *   schedule daily campaigns, and launch the accessibility watchdog heartbeat.
 *
 * Linked With:
 * - Schedulers: [com.jmd.superwa.scheduler.AlarmScheduler], [com.jmd.superwa.scheduler.DailyCampaignScheduler],
 *   [com.jmd.superwa.scheduler.AccessibilityWatchdogScheduler]
 * - Receivers: [com.jmd.superwa.receiver.TaskAlarmReceiver]
 * - Core Managers: [com.jmd.superwa.core.AccessibilityHealthManager], [com.jmd.superwa.core.AppFileLogger]
 * - Data Repositories: [com.jmd.superwa.data.repo.AppRepository], [com.jmd.superwa.data.prefs.PreferenceManager]
 * - Services: [com.jmd.superwa.service.TaskForegroundService]
 */
package com.jmd.superwa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import com.jmd.superwa.core.AccessibilityHealthManager
import com.jmd.superwa.core.AppFileLogger
import com.jmd.superwa.core.DispatchJournal
import com.jmd.superwa.data.prefs.PreferenceManager
import com.jmd.superwa.data.repo.AppRepository
import com.jmd.superwa.receiver.TaskAlarmReceiver
import com.jmd.superwa.scheduler.AccessibilityWatchdogScheduler
import com.jmd.superwa.scheduler.AlarmScheduler
import com.jmd.superwa.scheduler.DailyCampaignScheduler
import com.jmd.superwa.service.TaskForegroundService
import kotlinx.coroutines.*

/**
 * What it is:
 * Root Application class for the Super Android task suite.
 *
 * What it does:
 * Coordinates system-wide services, registers user-facing notification categories,
 * launches watchdog monitors, and initializes scheduled outreach pipelines.
 *
 * Linked with:
 * - System runtime: [Application], [Configuration.Provider]
 * - Architecture: [SuperApplication.instance], [SuperApplication.applicationScope]
 */
class SuperApplication : Application(), Configuration.Provider {

    /**
     * What it is:
     * Custom WorkManager configuration provider.
     *
     * What it does:
     * Configures the logging level for background WorkManager jobs to INFO.
     *
     * Linked with:
     * - AndroidX WorkManager: [Configuration.Builder]
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    companion object {
        /** Logging tag for diagnostic system traces. */
        private const val TAG = "SuperApp"

        /** Notification channel identifier for task status updates and delivery receipts. */
        const val CHANNEL_TASKS = "channel_tasks"

        /** Legacy persistent service notification channel identifier targeted for removal. */
        const val CHANNEL_SERVICE = "channel_service"

        /**
         * Globally accessible singleton instance of [SuperApplication].
         * Backed by volatile storage to guarantee cross-thread memory visibility.
         */
        @Volatile
        lateinit var instance: SuperApplication
            private set

        /**
         * What it is:
         * System notification channel registration factory.
         *
         * What it does:
         * Creates and configures the system notification channels with appropriate sound,
         * vibration, and importance levels, while retiring obsolete legacy channels.
         *
         * Linked with:
         * - System: [NotificationManager], [NotificationChannel]
         * - Resources: [R.string.notification_channel_tasks]
         */
        fun createNotificationChannels(context: Context) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

            // Retire legacy persistent service and obsolete notification channels
            try {
                notificationManager.deleteNotificationChannel(CHANNEL_SERVICE)
                notificationManager.deleteNotificationChannel("channel_alerts_legacy")
            } catch (e: Exception) {
                Log.d(TAG, "Delete legacy channels: ${e.message}")
            }

            val expectedTasksName = context.getString(R.string.notification_channel_tasks)

            val existingTasks = notificationManager.getNotificationChannel(CHANNEL_TASKS)
            if (existingTasks != null && existingTasks.name != expectedTasksName) {
                try {
                    notificationManager.deleteNotificationChannel(CHANNEL_TASKS)
                } catch (e: Exception) {
                    Log.d(TAG, "Recreate tasks channel: ${e.message}")
                }
            }

            // Channel for task completions, execution failures, and message dispatches
            val tasksChannel = NotificationChannel(
                CHANNEL_TASKS,
                expectedTasksName,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_tasks_desc)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(listOf(tasksChannel))
        }
    }

    /**
     * What it is:
     * Application-wide coroutine scope backed by SupervisorJob and IO dispatcher.
     *
     * What it does:
     * Executes non-blocking background initialization and asynchronous maintenance routines.
     *
     * Linked with:
     * - Coroutines: [SupervisorJob], [Dispatchers.IO]
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * What it is:
     * Primary application process lifecycle entry point.
     *
     * What it does:
     * Initializes the application process, registers notification channels, attaches uncaught exception handler,
     * and launches background initialization routines without blocking the main UI thread.
     *
     * Linked with:
     * - Exception logger: [Thread.setDefaultUncaughtExceptionHandler], [AppFileLogger], [TaskAlarmReceiver.dumpExecutionLogsToFile]
     * - Watchdogs: [AccessibilityHealthManager.ensureServiceComponentEnabled], [AccessibilityWatchdogScheduler.scheduleHeartbeat]
     * - Repositories: [AppRepository.recoverInterruptedRunningTasks], [AppRepository.purgeAllGroupsFromPool]
     * - Schedulers: [AlarmScheduler.rescheduleAllPending], [DailyCampaignScheduler.scheduleAll]
     */
    override fun onCreate() {
        super.onCreate()
        instance = this

        // 0. Install Global Uncaught Exception Handler with Quick Diagnostic File Dumping
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "UNHANDLED EXCEPTION in thread ${thread.name}", throwable)
                AppFileLogger.e(this, TAG, "UNCAUGHT_EXCEPTION in thread ${thread.name}: ${throwable.message}", throwable)
                TaskAlarmReceiver.dumpExecutionLogsToFile(this)
            } catch (t: Throwable) {
                Log.e(TAG, "Error in diagnostic dump handler", t)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // 1. Register system notification channels required on Android 8.0 (API 26) and above
        createNotificationChannels(this)

        // 1a. Ensure accessibility service component is active in PackageManager
        AccessibilityHealthManager.ensureServiceComponentEnabled(this)

        // 2. Offload database queries, contact hygiene, and alarm reschedulings to structured background coroutine
        val initExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled exception during background initialization coroutine", throwable)
            AppFileLogger.e(this, TAG, "Background init coroutine error: ${throwable.message}", throwable)
        }

        applicationScope.launch(initExceptionHandler) {
            try {
                val prefs = PreferenceManager(this@SuperApplication)
                val repo = AppRepository(this@SuperApplication)
                prefs.isDynamicA11yOnDemandEnabled = false
                prefs.isA11yUserPaused = false
                if (com.jmd.superwa.core.AppVariantHelper.isSuperWa) {
                    com.jmd.superwa.core.ProtectedApplicationManager.setUserProtectedPackages(prefs.protectedPackages)
                    AccessibilityHealthManager.ensureAllServicesActive(this@SuperApplication)
                }
                // Reconcile and recover any tasks left stranded in RUNNING status from a previous process termination
                repo.recoverInterruptedRunningTasks()
                // Turn dispatch claims abandoned by a process kill into retryable failures
                DispatchJournal.reconcileStale(this@SuperApplication)
                // Only drop malformed group rows. A blanket purge here erased every harvested group on each launch.
                // One-time reset: group rows written by the old preview/author heuristics were unreliable.
        // Clearing them lets the next harvest classify groups from saved-sender previews.
        if (!prefs.groupsResetV2) {
            val removed = repo.purgeAllGroupEntries()
            prefs.groupsResetV2 = true
            android.util.Log.i("SuperApplication", "Group reset: removed $removed legacy group rows")
        }
        repo.purgeCorruptedGroupsFromPool()

                // Purge any stale/completed/inactive alarms and re-arm pending tasks across channels
                AlarmScheduler.cleanOrphanedAndCompletedAlarms(this@SuperApplication)
                try {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.cancelAll()
                } catch (t: Throwable) {
                    Log.d(TAG, "Notification cancelAll note: ${t.message}")
                }
                AlarmScheduler(this@SuperApplication).rescheduleAllPending()

                // Arm daily campaign schedulers for scheduled outreach cycles
                DailyCampaignScheduler.scheduleAll(this@SuperApplication)

                if (com.jmd.superwa.core.AppVariantHelper.isSuperWa) {
                    // Start silent exact AlarmManager heartbeat for accessibility watchdog monitoring
                    AccessibilityWatchdogScheduler.scheduleHeartbeat(this@SuperApplication)

                    // Proactively verify and request binding for Accessibility and NotificationListener services
                    if (!prefs.isA11yUserPaused) {
                        AccessibilityHealthManager.ensureAllServicesActive(this@SuperApplication)
                    }
                    AccessibilityHealthManager.enforceAutoRotateInvariant(this@SuperApplication)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during background initialization", e)
                AppFileLogger.e(this@SuperApplication, TAG, "Recoverable issue during background initialization: ${e.message}", e)
            }
        }

        // 3. Ensure any legacy persistent foreground notifications are cancelled
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.cancel(TaskForegroundService.NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.d(TAG, "Cancel legacy notification: ${e.message}")
        }
    }
}

