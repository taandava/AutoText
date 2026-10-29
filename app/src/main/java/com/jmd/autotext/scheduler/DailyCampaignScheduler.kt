

/**
 * Package: com.jmd.autotext.scheduler
 * Module: Schedulers Layer
 *
 * Enqueues and coordinates WorkManager periodic background workers and immediate one-time
 * batch executions for daily outreach campaigns.
 *
 * Linked With:
 * - Worker: [com.jmd.autotext.scheduler.DailyCampaignWorker]
 * - Repository: [com.jmd.autotext.data.repo.DailyCampaignRepository]
 * - UI Screens: `DailyCampaignScreen`
 */
package com.jmd.autotext.scheduler

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.receiver.TaskAlarmReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager scheduler governing daily automated campaign execution cycles.
 */
object DailyCampaignScheduler {

    private fun getPeriodicWorkName(campaignId: Long) = "autotext_daily_campaign_work_$campaignId"
    private fun getImmediateWorkName(campaignId: Long) = "autotext_daily_campaign_immediate_$campaignId"

    /**
     * Convenience alias for [scheduleOrUpdate].
     *
     * @param context Active Android [Context].
     * @param campaignId Unique database identifier of the target campaign configuration.
     */
    fun schedule(context: Context, campaignId: Long = 1) = scheduleOrUpdate(context, campaignId)

    /**
     * Computes the initial delay until the configured trigger hour/minute and schedules or updates
     * a 24-hour [PeriodicWorkRequestBuilder] in [WorkManager]. If the campaign is inactive, cancels work.
     *
     * @param context Active Android [Context].
     * @param campaignId Unique campaign configuration ID to schedule or update.
     */
    fun scheduleOrUpdate(context: Context, campaignId: Long = 1) {
        val repo = AppRepository(context)
        val config = repo.getDailyCampaignConfig(campaignId)
        val prefs = com.jmd.autotext.data.prefs.PreferenceManager(context)
        val workManager = WorkManager.getInstance(context)
        val uniqueWorkName = getPeriodicWorkName(campaignId)

        if (!config.isActive || !prefs.isTasksMasterEnabled || com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive(context)) {
            cancel(context, campaignId)
            return
        }

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, config.scheduledHour)
            set(Calendar.MINUTE, config.scheduledMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.before(now)) {
            // Target time has already passed today. Advance to tomorrow's window.
            // Never spontaneously trigger immediate message batches on system events / reboot.
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Schedule exact wake-up alarm to overcome Doze mode restrictions
        scheduleExactCampaignAlarm(context, campaignId, target.timeInMillis)

        val initialDelayMinutes = ((target.timeInMillis - now.timeInMillis) / 60000).coerceAtLeast(0)

        val inputData = Data.Builder()
            .putLong(DailyCampaignWorker.KEY_CAMPAIGN_ID, campaignId)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<DailyCampaignWorker>(24, TimeUnit.HOURS)
            .setInputData(inputData)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .addTag("DAILY_CAMPAIGN_$campaignId")
            .addTag("all_daily_campaigns")
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Iterates through all saved campaign configurations in the database and arms/updates each schedule.
     *
     * @param context Active Android [Context].
     */
    fun scheduleAll(context: Context) {
        val repo = AppRepository(context)
        val all = repo.getAllDailyCampaignConfigs()
        for ((id) in all) {
            scheduleOrUpdate(context, id)
        }
    }

    /**
     * Dispatches an immediate [OneTimeWorkRequestBuilder] via [WorkManager] bypassing the scheduled time.
     *
     * @param context Active Android [Context].
     * @param campaignId Target campaign identifier to trigger immediately.
     */
    fun triggerNow(context: Context, campaignId: Long = 1) {
        val repo = AppRepository(context)
        val config = repo.getDailyCampaignConfig(campaignId)
        val prefs = com.jmd.autotext.data.prefs.PreferenceManager(context)
        if (!config.isActive) {
            AppFileLogger.w(context, "DailyCampaignScheduler", "triggerNow aborted: Campaign #$campaignId is inactive.")
            return
        }
        if (!prefs.isTasksMasterEnabled) {
            AppFileLogger.w(context, "DailyCampaignScheduler", "triggerNow aborted: Task Scheduler Engine is paused.")
            return
        }
        if (com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive(context)) {
            AppFileLogger.w(context, "DailyCampaignScheduler", "triggerNow aborted: Emergency Stop active.")
            return
        }

        val data = Data.Builder()
            .putBoolean(DailyCampaignWorker.KEY_MANUAL_TRIGGER, true)
            .putLong(DailyCampaignWorker.KEY_CAMPAIGN_ID, campaignId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DailyCampaignWorker>()
            .setInputData(data)
            .addTag("DAILY_CAMPAIGN_MANUAL_$campaignId")
            .addTag("all_daily_campaigns")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            getImmediateWorkName(campaignId),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Cancels the periodic background WorkManager task for the specified campaign.
     *
     * @param context Active Android [Context].
     * @param campaignId Unique campaign configuration ID to cancel.
     */
    fun cancel(context: Context, campaignId: Long = 1) {
        val workManager = WorkManager.getInstance(context)
        val periodicName = getPeriodicWorkName(campaignId)
        val immediateName = getImmediateWorkName(campaignId)
        workManager.cancelUniqueWork(periodicName)
        workManager.cancelUniqueWork(immediateName)
        workManager.cancelAllWorkByTag("DAILY_CAMPAIGN_$campaignId")
        workManager.cancelAllWorkByTag("DAILY_CAMPAIGN_MANUAL_$campaignId")
        cancelExactCampaignAlarm(context, campaignId)
        AppFileLogger.action(context, "DailyCampaignScheduler", "Cancelled all periodic and immediate work and alarms for Campaign #$campaignId")
    }

    /**
     * Cancels all scheduled background work and alarms for all campaigns in the database.
     */
    fun cancelAllCampaigns(context: Context) {
        val repo = AppRepository(context)
        val all = repo.getAllDailyCampaignConfigs()
        for (cfg in all) {
            cancel(context, cfg.id)
        }
        WorkManager.getInstance(context).cancelAllWorkByTag("all_daily_campaigns")
        AppFileLogger.action(context, "DailyCampaignScheduler", "Cancelled all daily campaign workers and alarms")
    }

    private fun scheduleExactCampaignAlarm(context: Context, campaignId: Long, triggerMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_FIRE_CAMPAIGN
            putExtra("campaign_id", campaignId)
        }
        val requestCode = 100000 + campaignId.toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            AppFileLogger.action(context, "DailyCampaignScheduler", "Armed exact alarm for Daily Campaign #$campaignId at ${Date(triggerMillis)}")
        } catch (e: Exception) {
            Log.e("DailyCampaignScheduler", "Failed to set exact campaign alarm: ${e.message}", e)
        }
    }

    private fun cancelExactCampaignAlarm(context: Context, campaignId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_FIRE_CAMPAIGN
            putExtra("campaign_id", campaignId)
        }
        val requestCode = 100000 + campaignId.toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
