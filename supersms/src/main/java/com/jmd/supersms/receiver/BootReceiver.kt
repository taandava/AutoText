package com.jmd.supersms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jmd.supersms.core.AppFileLogger
import com.jmd.supersms.core.DispatchJournal
import com.jmd.supersms.scheduler.AlarmScheduler
import com.jmd.supersms.scheduler.DailyCampaignScheduler

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isFullBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"

        if (isFullBoot) {
            AppFileLogger.action(context, TAG, "Device boot/package replace ($action) detected. Rescheduling active alarms and campaigns.")
            DispatchJournal.reconcileStale(context)
            AlarmScheduler(context).rescheduleAllPending()
            DailyCampaignScheduler.scheduleAll(context)
        }
    }
}
