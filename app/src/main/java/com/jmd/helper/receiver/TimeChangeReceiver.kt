/**
 * Package: com.jmd.helper.receiver
 * Module: Broadcast Receivers Layer
 *
 * Listens for system clock and timezone changes to immediately recalculate and reschedule
 * all pending task alarms and daily outreach timers.
 *
 * Linked With:
 * - Scheduler: [com.jmd.helper.scheduler.AlarmScheduler]
 * - Diagnostics: [com.jmd.helper.core.AppFileLogger]
 */
package com.jmd.helper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.scheduler.AlarmScheduler

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
            Log.d("TimeChangeReceiver", "Time change detected ($action), rescheduling alarms...")
            AppFileLogger.action(context, "TimeChangeReceiver", "System time/timezone changed ($action). Rescheduling alarms.")
            AlarmScheduler(context).rescheduleAllPending()
            com.jmd.helper.scheduler.DailyCampaignScheduler.scheduleAll(context)
        }
    }
}
