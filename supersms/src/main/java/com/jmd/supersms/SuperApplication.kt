package com.jmd.supersms

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import com.jmd.supersms.core.AppFileLogger
import com.jmd.supersms.core.DispatchJournal
import com.jmd.supersms.data.prefs.PreferenceManager
import com.jmd.supersms.data.repo.AppRepository
import com.jmd.supersms.receiver.TaskAlarmReceiver
import com.jmd.supersms.scheduler.AlarmScheduler
import com.jmd.supersms.scheduler.DailyCampaignScheduler
import com.jmd.supersms.service.TaskForegroundService
import kotlinx.coroutines.*

class SuperApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()

    val applicationScope: CoroutineScope
        get() = Companion.applicationScope

    companion object {
        private const val TAG = "SuperSMSApp"
        const val CHANNEL_TASKS = "tasks_channel"
        const val CHANNEL_CAMPAIGN = "campaign_channel"

        lateinit var instance: SuperApplication
            private set

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun createNotificationChannels(context: Context) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

            val taskChannel = NotificationChannel(
                CHANNEL_TASKS,
                context.getString(R.string.notification_channel_tasks),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_tasks_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            val campaignChannel = NotificationChannel(
                CHANNEL_CAMPAIGN,
                "Daily Campaigns",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Daily outreach progress and summary notifications"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(listOf(taskChannel, campaignChannel))
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
                AppFileLogger.e(
                    this,
                    TAG,
                    "FATAL UNCAUGHT EXCEPTION on [${thread.name}]: ${throwable.stackTraceToString()}",
                    throwable
                )
            } catch (loggingError: Throwable) {
                Log.e(TAG, "Failed writing crash log", loggingError)
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }

        createNotificationChannels(this)

        val initExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled exception during background initialization", throwable)
            AppFileLogger.e(this, TAG, "Background init error: ${throwable.message}", throwable)
        }

        applicationScope.launch(initExceptionHandler) {
            try {
                val repo = AppRepository(this@SuperApplication)
                repo.recoverInterruptedRunningTasks()
                DispatchJournal.reconcileStale(this@SuperApplication)

                AlarmScheduler.cleanOrphanedAndCompletedAlarms(this@SuperApplication)
                try {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm?.cancelAll()
                } catch (t: Throwable) {
                    Log.d(TAG, "Notification cancel note: ${t.message}")
                }
                AlarmScheduler(this@SuperApplication).rescheduleAllPending()
                DailyCampaignScheduler.scheduleAll(this@SuperApplication)
            } catch (e: Exception) {
                Log.e(TAG, "Error during background initialization", e)
                AppFileLogger.e(this@SuperApplication, TAG, "Recoverable issue in background init: ${e.message}", e)
            }
        }

        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.cancel(TaskForegroundService.NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.d(TAG, "Cancel legacy notification: ${e.message}")
        }
    }
}
