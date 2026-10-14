package com.jmd.autotext.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jmd.autotext.data.AppRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DailyCampaignScheduler {

    private fun getPeriodicWorkName(campaignId: Long) = "autotext_daily_campaign_work_$campaignId"
    private fun getImmediateWorkName(campaignId: Long) = "autotext_daily_campaign_immediate_$campaignId"

    fun scheduleOrUpdate(context: Context, campaignId: Long = 1) {
        val repo = AppRepository(context)
        val config = repo.getDailyCampaignConfig(campaignId)
        val workManager = WorkManager.getInstance(context)
        val uniqueWorkName = getPeriodicWorkName(campaignId)

        if (!config.isActive) {
            workManager.cancelUniqueWork(uniqueWorkName)
            return
        }

        // Calculate initial delay until scheduled hour & minute
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, config.scheduledHour)
            set(Calendar.MINUTE, config.scheduledMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelayMinutes = ((target.timeInMillis - now.timeInMillis) / 60000).coerceAtLeast(0)

        val inputData = Data.Builder()
            .putLong(DailyCampaignWorker.KEY_CAMPAIGN_ID, campaignId)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<DailyCampaignWorker>(24, TimeUnit.HOURS)
            .setInputData(inputData)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .addTag("DAILY_CAMPAIGN_$campaignId")
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun scheduleAll(context: Context) {
        scheduleOrUpdate(context, 1L)
        scheduleOrUpdate(context, 2L)
    }

    fun triggerNow(context: Context, campaignId: Long = 1) {
        val data = Data.Builder()
            .putBoolean(DailyCampaignWorker.KEY_MANUAL_TRIGGER, true)
            .putLong(DailyCampaignWorker.KEY_CAMPAIGN_ID, campaignId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DailyCampaignWorker>()
            .setInputData(data)
            .addTag("DAILY_CAMPAIGN_MANUAL_$campaignId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            getImmediateWorkName(campaignId),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancel(context: Context, campaignId: Long = 1) {
        WorkManager.getInstance(context).cancelUniqueWork(getPeriodicWorkName(campaignId))
    }
}
