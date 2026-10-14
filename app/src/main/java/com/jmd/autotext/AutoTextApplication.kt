package com.jmd.autotext

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.jmd.autotext.scheduler.AlarmScheduler

class AutoTextApplication : Application() {

    companion object {
        const val CHANNEL_TASKS = "channel_tasks"
        const val CHANNEL_REMINDERS = "channel_reminders"
        const val CHANNEL_SERVICE = "channel_service"

        @Volatile
        lateinit var instance: AutoTextApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()

        // Offload database migrations/queries and alarm scheduling to background to keep main thread blazing fast
        Thread {
            try {
                // Reschedule pending alarms on launch
                AlarmScheduler(this).rescheduleAllPending()
                com.jmd.autotext.scheduler.DailyCampaignScheduler.scheduleAll(this)
                // Start accessibility watchdog heartbeat (silent exact AlarmManager)
                com.jmd.autotext.scheduler.AccessibilityWatchdogScheduler.scheduleHeartbeat(this)

                // Clean up any historical invalid group names from the database
                com.jmd.autotext.data.repository.WhatsAppRepository(this).cleanupInvalidGroupNames()
            } catch (_: Exception) {}
        }.start()

        // Ensure any legacy persistent notification is cancelled
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(com.jmd.autotext.service.TaskForegroundService.NOTIFICATION_ID)
        } catch (ignored: Exception) {}
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return

            // Delete legacy persistent service notification channel so no fixed notification persists
            try {
                notificationManager.deleteNotificationChannel(CHANNEL_SERVICE)
            } catch (ignored: Exception) {}

            val tasksChannel = NotificationChannel(
                CHANNEL_TASKS,
                getString(R.string.notification_channel_tasks),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_tasks_desc)
                enableVibration(true)
            }

            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_reminders_desc)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(listOf(tasksChannel, remindersChannel))
        }
    }
}
