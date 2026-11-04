/**
 * Package: com.jmd.autotext
 * Module: Application Root
 *
 * Serves as the central Android Application entry point and orchestrator for AutoText.
 * Manages global application lifecycle hooks, notification channels, background alarm
 * reconciliations, accessibility health monitoring, and defensive self-healing routines.
 *
 * Features & Architectural Workflow:
 * - Bootstrapping: Initializes application context singletons and registers high-priority notification channels.
 * - Non-blocking Concurrency: Offloads database queries, contact hygiene, and alarm reschedulings to a dedicated daemon thread.
 * - Resilience: Activates periodic accessibility service watchdog heartbeats and self-heals corrupted permission states.
 * - Legacy Migration: Automatically cleans up legacy persistent foreground service notifications.
 */
package com.jmd.autotext

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.AutomationSafetyManager
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.receiver.TaskAlarmReceiver
import com.jmd.autotext.scheduler.AccessibilityWatchdogScheduler
import com.jmd.autotext.scheduler.AlarmScheduler
import com.jmd.autotext.scheduler.DailyCampaignScheduler
import com.jmd.autotext.service.TaskForegroundService

/**
 * Root Application class for the AutoText Android automation suite.
 *
 * Coordinates system-wide services, registers user-facing notification categories,
 * launches watchdog monitors, and initializes scheduled outreach pipelines.
 */
class AutoTextApplication : Application() {

    companion object {
        /** Logging tag for diagnostic system traces. */
        private const val TAG = "AutoTextApp"

        /** Notification channel identifier for automated task status updates and delivery receipts. */
        const val CHANNEL_TASKS = "channel_tasks"

        /** Notification channel identifier for full-screen reminder alarms, countdowns, and sound toggles. */
        const val CHANNEL_REMINDERS = "channel_reminders"

        /** Legacy persistent service notification channel identifier targeted for automatic removal. */
        const val CHANNEL_SERVICE = "channel_service"

        /**
         * Globally accessible singleton instance of [AutoTextApplication].
         * Backed by volatile storage to guarantee cross-thread memory visibility.
         */
        @Volatile
        lateinit var instance: AutoTextApplication
            private set
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Initializes the application process, registers notification channels, and launches
     * background initialization routines without blocking the main UI thread.
     */
    override fun onCreate() {
        super.onCreate()
        instance = this

        // 0. Install Global Crash Handler with Automatic Diagnostic File Dumping
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
        createNotificationChannels()

        // 1a. Ensure accessibility service component is active in PackageManager
        AccessibilityHealthManager.ensureServiceComponentEnabled(this)

        // Sync persisted emergency stop state
        val initialPrefs = PreferenceManager(this)
        if (initialPrefs.isEmergencyStopActive) {
            AutomationSafetyManager.isEmergencyStopActive.set(true)
        }

        // 2. Offload database queries, contact hygiene, and alarm reschedulings to structured background coroutine
        val initExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled exception during background initialization coroutine", throwable)
            AppFileLogger.e(this, TAG, "Background init coroutine error: ${throwable.message}", throwable)
        }

        applicationScope.launch(initExceptionHandler) {
            try {
                // Reconcile and recover any tasks left stranded in RUNNING status from a previous process termination
                AppRepository(this@AutoTextApplication).recoverInterruptedRunningTasks()

                // Re-arm all active and pending task alarms across SMS, WhatsApp, Calls, and Reminders
                val prefs = PreferenceManager(this@AutoTextApplication)
                if (!prefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(this@AutoTextApplication)) {
                    Log.i(TAG, "Master task scheduler is paused or emergency stop active. Suppressing background schedule initialization.")
                    AlarmScheduler(this@AutoTextApplication).cancelAll()
                    DailyCampaignScheduler.cancelAllCampaigns(this@AutoTextApplication)
                } else {
                    AlarmScheduler(this@AutoTextApplication).rescheduleAllPending()

                    // Arm daily campaign schedulers for automated outreach cycles
                    DailyCampaignScheduler.scheduleAll(this@AutoTextApplication)
                }

                // Start silent exact AlarmManager heartbeat for accessibility watchdog monitoring
                AccessibilityWatchdogScheduler.scheduleHeartbeat(this@AutoTextApplication)

                // Proactively verify and request binding for Accessibility and NotificationListener services
                if (!prefs.isDynamicA11yOnDemandEnabled && !prefs.isBankingShieldEnabled && !prefs.isA11yUserPaused) {
                    AccessibilityHealthManager.ensureAllServicesActive(this@AutoTextApplication)
                }
                AccessibilityHealthManager.enforceAutoRotateInvariant(this@AutoTextApplication)

                // Self-heal and purge any individual contacts that were previously misclassified as groups
                AppRepository(this@AutoTextApplication).cleanupMistakenGroups()
            } catch (e: Exception) {
                Log.e(TAG, "Error during background initialization", e)
                AppFileLogger.e(this@AutoTextApplication, TAG, "Non-fatal error during background initialization: ${e.message}", e)
            }
        }

        // 3. Ensure any legacy persistent foreground notifications are cancelled
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(TaskForegroundService.NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.d(TAG, "Cancel legacy notification: ${e.message}")
        }
    }

    /**
     * Creates and configures the system notification channels with appropriate sound,
     * vibration, and importance levels, while retiring obsolete legacy channels.
     */
    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return

        // Retire legacy persistent service notification channel so no sticky notification remains
        try {
            notificationManager.deleteNotificationChannel(CHANNEL_SERVICE)
        } catch (e: Exception) {
            Log.d(TAG, "Delete legacy service channel: ${e.message}")
        }

        // Channel for task completions, execution failures, and message dispatches
        val tasksChannel = NotificationChannel(
            CHANNEL_TASKS,
            getString(R.string.notification_channel_tasks),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_tasks_desc)
            enableVibration(true)
        }

        // High-importance channel for reminder alarms and timer alerts
        val remindersChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_reminders_desc)
            enableVibration(true)
        }

        notificationManager.createNotificationChannels(listOf(tasksChannel, remindersChannel))
    }
}

