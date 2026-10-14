package com.jmd.autotext.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmd.autotext.util.AppFileLogger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("BootReceiver", "Boot or upgrade detected ($action), rescheduling alarms...")
            AppFileLogger.action(context, "BootReceiver", "Device boot/package replace ($action) detected. Rescheduling alarms and campaigns.")
            AlarmScheduler(context).rescheduleAllPending()
            DailyCampaignScheduler.scheduleAll(context)
            AccessibilityWatchdogScheduler.scheduleHeartbeat(context)
        }
    }
}
