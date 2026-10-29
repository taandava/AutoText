/**
 * Package: com.jmd.autotext.receiver
 * Module: Broadcast Receivers Layer
 *
 * Listens for system clock and timezone changes to immediately recalculate and reschedule
 * all pending task alarms and daily outreach timers.
 *
 * Linked With:
 * - Scheduler: [com.jmd.autotext.scheduler.AlarmScheduler]
 * - Diagnostics: [com.jmd.autotext.core.AppFileLogger]
 */
package com.jmd.autotext.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.scheduler.AlarmScheduler

/**
 * System broadcast receiver triggered on [Intent.ACTION_TIME_CHANGED] and [Intent.ACTION_TIMEZONE_CHANGED].
 */
class TimeChangeReceiver : BroadcastReceiver() {

    /**
     * Reschedules all pending tasks whenever device time or timezone shifts to maintain execution accuracy.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
            val prefs = com.jmd.autotext.data.prefs.PreferenceManager(context)
            if (!prefs.isTasksMasterEnabled || com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive(context)) {
                Log.d("TimeChangeReceiver", "Master task scheduler is paused or emergency stop active. Suppressing reschedule.")
                AlarmScheduler(context).cancelAll()
                com.jmd.autotext.scheduler.DailyCampaignScheduler.cancelAllCampaigns(context)
                return
            }
            Log.d("TimeChangeReceiver", "Time change detected ($action), rescheduling alarms...")
            AppFileLogger.action(context, "TimeChangeReceiver", "System time/timezone changed ($action). Rescheduling alarms.")
            AlarmScheduler(context).rescheduleAllPending()
            com.jmd.autotext.scheduler.DailyCampaignScheduler.scheduleAll(context)
        }
    }
}
