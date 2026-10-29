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

        // Sync persisted emergency stop state
        val initialPrefs = PreferenceManager(this)
        if (initialPrefs.isEmergencyStopActive) {
            AutomationSafetyManager.isEmergencyStopActive.set(true)
        }

        // 2. Offload database queries, contact hygiene, and alarm reschedulings to background daemon thread
        Thread {
            try {
                // Re-arm all active and pending task alarms across SMS, WhatsApp, Calls, and Reminders
                val prefs = PreferenceManager(this)
                if (!prefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(this)) {
                    Log.i(TAG, "Master task scheduler is paused or emergency stop active. Suppressing background schedule initialization.")
                    AlarmScheduler(this).cancelAll()
                    DailyCampaignScheduler.cancelAllCampaigns(this)
                } else {
                    AlarmScheduler(this).rescheduleAllPending()

                // Arm daily campaign schedulers for automated outreach cycles
                    DailyCampaignScheduler.scheduleAll(this)
                }

                // Start silent exact AlarmManager heartbeat for accessibility watchdog monitoring
                AccessibilityWatchdogScheduler.scheduleHeartbeat(this)

                // Proactively verify and request binding for Accessibility and NotificationListener services
                AccessibilityHealthManager.ensureAllServicesActive(this)
                AccessibilityHealthManager.enforceAutoRotateInvariant(this)

                // Self-heal and purge any individual contacts that were previously misclassified as groups
                AppRepository(this).cleanupMistakenGroups()
            } catch (e: Exception) {
                Log.e(TAG, "Error during background initialization", e)
                AppFileLogger.e(this, TAG, "Non-fatal error during background initialization: ${e.message}", e)
            }
        }.start()

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

