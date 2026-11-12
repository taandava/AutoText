/**
 * Package: com.jmd.helper.scheduler
 * Module: Schedulers & Background Workers
 *
 * What it is:
 * Architecture definition and implementation for [DailyCampaignScheduler].
 *
 * What it does:
 * Enqueues and coordinates WorkManager periodic background workers and immediate one-time batch executions for daily outreach campaigns.
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.repo.AppRepository]
 * - Schedulers & Receivers: [android.app.AlarmManager], [com.jmd.helper.receiver.TaskAlarmReceiver]
 * - Core Security & Utilities: [androidx.work.WorkManager], [android.util.Log], [com.jmd.helper.core.AppFileLogger], [java.util.Date]
 * - Android Framework & Jetpack: [androidx.work.Data], [androidx.work.ExistingPeriodicWorkPolicy], [androidx.work.ExistingWorkPolicy], [androidx.work.OneTimeWorkRequestBuilder]
 */

package com.jmd.helper.scheduler

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
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.receiver.TaskAlarmReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * What it is:
 * WorkManager scheduler governing daily scheduled campaign execution cycles.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [DailyCampaignScheduler].
 *
 * Linked with:
 * - Architecture: [DailyCampaignScheduler], [DailyCampaignScheduler]
 */
object DailyCampaignScheduler {
    /**
     * What it is:
     * Execution routine for [getPeriodicWorkName].
     *
     * What it does:
     * Coordinates getPeriodicWorkName operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DailyCampaignScheduler]
     */

    private fun getPeriodicWorkName(campaignId: Long) = "helper_daily_campaign_work_$campaignId"
    /**
     * What it is:
     * Execution routine for [getImmediateWorkName].
     *
     * What it does:
     * Coordinates getImmediateWorkName operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DailyCampaignScheduler]
     */
    private fun getImmediateWorkName(campaignId: Long) = "helper_daily_campaign_immediate_$campaignId"

    /**
     * What it is:
     * Convenience alias for [scheduleOrUpdate].
     *
     * What it does:
     * Executes [schedule] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignScheduler], [schedule]
     * @param context Active Android [Context].
     * @param campaignId Unique database identifier of the target campaign configuration.
     */
    fun schedule(context: Context, campaignId: Long = 1) = scheduleOrUpdate(context, campaignId)

    /**
     * What it is:
     * Computes the initial delay until the configured trigger hour/minute and schedules or updates a 24-hour [PeriodicWorkRequestBuilder] in [WorkManager].
     *
     * What it does:
     * If the campaign is inactive, cancels work.
     *
     * Linked with:
     * - Architecture: [DailyCampaignScheduler], [scheduleOrUpdate]
     * @param context Active Android [Context].
     * @param campaignId Unique campaign configuration ID to schedule or update.
     */
    fun scheduleOrUpdate(context: Context, campaignId: Long = 1) {
        val repo = AppRepository(context)
        val config = repo.getDailyCampaignConfig(campaignId)
        val prefs = com.jmd.helper.data.prefs.PreferenceManager(context)
        val workManager = WorkManager.getInstance(context)
        val uniqueWorkName = getPeriodicWorkName(campaignId)

        if (!config.isActive) {
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

        // Cancel any WorkManager periodic background workers to prevent WorkManager from ever
        // spontaneously firing campaign batches at premature times (e.g. on boot, network tick, or WorkManager update).
        // Exact AlarmManager RTC_WAKEUP is the single source of truth for exact wall-clock triggering.
        workManager.cancelUniqueWork(uniqueWorkName)
        workManager.cancelAllWorkByTag("DAILY_CAMPAIGN_$campaignId")
    }

    /**
     * What it is:
     * Iterates through all saved campaign configurations in the database and arms/updates each schedule.
     *
     * What it does:
     * Executes [scheduleAll] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignScheduler], [scheduleAll]
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
     * What it is:
     * Dispatches an immediate [OneTimeWorkRequestBuilder] via [WorkManager] overriding the scheduled delay for immediate execution.
     *
     * What it does:
     * Executes [triggerNow] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignScheduler], [triggerNow]
     * @param context Active Android [Context].
     * @param campaignId Target campaign identifier to trigger immediately.
     */
    fun triggerNow(context: Context, campaignId: Long = 1) {
        val repo = AppRepository(context)
        val config = repo.getDailyCampaignConfig(campaignId)
        val prefs = com.jmd.helper.data.prefs.PreferenceManager(context)
        if (!config.isActive) {
            AppFileLogger.w(context, "DailyCampaignScheduler", "triggerNow aborted: Campaign #$campaignId is inactive.")
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
     * What it is:
     * Cancels the periodic background WorkManager task for the specified campaign.
     *
     * What it does:
     * Executes [cancel] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignScheduler], [cancel]
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
     * What it is:
     * Cancels all scheduled background work and alarms for all campaigns in the database.
     *
     * What it does:
     * Executes [cancelAllCampaigns] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignScheduler], [cancelAllCampaigns]
     */
    fun cancelAllCampaigns(context: Context) {
        val repo = AppRepository(context)
        val all = repo.getAllDailyCampaignConfigs()
        for ((cfgId) in all) {
            cancel(context, cfgId)
        }
        WorkManager.getInstance(context).cancelAllWorkByTag("all_daily_campaigns")
        AppFileLogger.action(context, "DailyCampaignScheduler", "Cancelled all daily campaign workers and alarms")
    }
    /**
     * What it is:
     * Execution routine for [scheduleExactCampaignAlarm].
     *
     * What it does:
     * Coordinates scheduleExactCampaignAlarm operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DailyCampaignScheduler]
     */

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
    /**
     * What it is:
     * Execution routine for [cancelExactCampaignAlarm].
     *
     * What it does:
     * Coordinates cancelExactCampaignAlarm operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DailyCampaignScheduler]
     */

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
