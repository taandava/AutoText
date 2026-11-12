/**
 * Package: com.jmd.helper
 * Module: Application Root
 *
 * What it is:
 * Central Android Application entry point and process lifecycle orchestrator for Helper.
 *
 * What it does:
 * - Bootstraps the application process and provides global singleton access.
 * - Sets up defensive global uncaught crash handler with immediate diagnostic log dumping.
 * - Creates and registers system-level notification channels for tasks and alarms.
 * - Enforces accessibility service component enablement and auto-rotation invariance.
 * - Spawns structured background coroutine to recover interrupted tasks, re-arm pending alarms,
 *   schedule daily campaigns, and launch the accessibility watchdog heartbeat.
 *
 * Linked With:
 * - Schedulers: [com.jmd.helper.scheduler.AlarmScheduler], [com.jmd.helper.scheduler.DailyCampaignScheduler],
 *   [com.jmd.helper.scheduler.AccessibilityWatchdogScheduler]
 * - Receivers: [com.jmd.helper.receiver.TaskAlarmReceiver]
 * - Core Managers: [com.jmd.helper.core.AccessibilityHealthManager], [com.jmd.helper.core.AppFileLogger]
 * - Data Repositories: [com.jmd.helper.data.repo.AppRepository], [com.jmd.helper.data.prefs.PreferenceManager]
 * - Services: [com.jmd.helper.service.TaskForegroundService]
 */
package com.jmd.helper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.receiver.TaskAlarmReceiver
import com.jmd.helper.scheduler.AccessibilityWatchdogScheduler
import com.jmd.helper.scheduler.AlarmScheduler
import com.jmd.helper.scheduler.DailyCampaignScheduler
import com.jmd.helper.service.TaskForegroundService
import kotlinx.coroutines.*

/**
 * What it is:
 * Root Application class for the Helper Android task suite.
 *
 * What it does:
 * Coordinates system-wide services, registers user-facing notification categories,
 * launches watchdog monitors, and initializes scheduled outreach pipelines.
 *
 * Linked with:
 * - System runtime: [Application], [Configuration.Provider]
 * - Architecture: [HelperApplication.instance], [HelperApplication.applicationScope]
 */
class HelperApplication : Application(), Configuration.Provider {

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
        private const val TAG = "HelperApp"

        /** Notification channel identifier for task status updates and delivery receipts. */
        const val CHANNEL_TASKS = "channel_tasks"

        /** Notification channel identifier for full-screen reminder alarms, countdowns, and sound toggles. */
        const val CHANNEL_REMINDERS = "channel_reminders"

        /** Legacy persistent service notification channel identifier targeted for removal. */
        const val CHANNEL_SERVICE = "channel_service"

        /**
         * Globally accessible singleton instance of [HelperApplication].
         * Backed by volatile storage to guarantee cross-thread memory visibility.
         */
        @Volatile
        lateinit var instance: HelperApplication
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
         * - Resources: [R.string.notification_channel_tasks], [R.string.notification_channel_reminders]
         */
        fun createNotificationChannels(context: Context) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

            // Retire legacy persistent service notification channel so no sticky notification remains
            try {
                notificationManager.deleteNotificationChannel(CHANNEL_SERVICE)
            } catch (e: Exception) {
                Log.d(TAG, "Delete legacy service channel: ${e.message}")
            }

            // Channel for task completions, execution failures, and message dispatches
            val tasksChannel = NotificationChannel(
                CHANNEL_TASKS,
                context.getString(R.string.notification_channel_tasks),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_tasks_desc)
                enableVibration(true)
            }

            // High-importance channel for reminder alarms and timer alerts
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_reminders_desc)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(listOf(tasksChannel, remindersChannel))
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
     * Initializes the application process, registers notification channels, attaches crash handler,
     * and launches background initialization routines without blocking the main UI thread.
     *
     * Linked with:
     * - Crash logger: [Thread.setDefaultUncaughtExceptionHandler], [AppFileLogger], [TaskAlarmReceiver.dumpExecutionLogsToFile]
     * - Watchdogs: [AccessibilityHealthManager.ensureServiceComponentEnabled], [AccessibilityWatchdogScheduler.scheduleHeartbeat]
     * - Repositories: [AppRepository.recoverInterruptedRunningTasks], [AppRepository.purgeAllGroupsFromPool]
     * - Schedulers: [AlarmScheduler.rescheduleAllPending], [DailyCampaignScheduler.scheduleAll]
     */
    override fun onCreate() {
        super.onCreate()
        instance = this

        // 0. Install Global Crash Handler with Quick Diagnostic File Dumping
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "FATAL CRASH in thread ${thread.name}", throwable)
                AppFileLogger.e(this, TAG, "CRASH in thread ${thread.name}: ${throwable.message}", throwable)
                TaskAlarmReceiver.dumpExecutionLogsToFile(this)
            } catch (t: Throwable) {
                Log.e(TAG, "Error in crash dump handler", t)
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
                val prefs = PreferenceManager(this@HelperApplication)
                val repo = AppRepository(this@HelperApplication)
                // Reconcile and recover any tasks left stranded in RUNNING status from a previous process termination
                repo.recoverInterruptedRunningTasks()
                repo.purgeAllGroupsFromPool()

                // Re-arm all active and pending task alarms across SMS, WhatsApp, Calls, and Reminders
                AlarmScheduler(this@HelperApplication).rescheduleAllPending()

                // Arm daily campaign schedulers for scheduled outreach cycles
                DailyCampaignScheduler.scheduleAll(this@HelperApplication)

                // Start silent exact AlarmManager heartbeat for accessibility watchdog monitoring
                AccessibilityWatchdogScheduler.scheduleHeartbeat(this@HelperApplication)

                // Proactively verify and request binding for Accessibility and NotificationListener services
                if (!prefs.isA11yUserPaused) {
                    AccessibilityHealthManager.ensureAllServicesActive(this@HelperApplication)
                }
                AccessibilityHealthManager.enforceAutoRotateInvariant(this@HelperApplication)
            } catch (e: Exception) {
                Log.e(TAG, "Error during background initialization", e)
                AppFileLogger.e(this@HelperApplication, TAG, "Non-fatal error during background initialization: ${e.message}", e)
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

