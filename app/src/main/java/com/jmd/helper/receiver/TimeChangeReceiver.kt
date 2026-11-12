/**
 * Package: com.jmd.helper.receiver
 * Module: Broadcast Receivers Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [TimeChangeReceiver].
 *
 * What it does:
 * Listens for system clock and timezone changes to immediately recalculate and reschedule all pending task alarms and daily outreach timers.
 *
 * Linked With:
 * - Schedulers & Receivers: [com.jmd.helper.scheduler.AlarmScheduler]
 * - Core Security & Utilities: [android.util.Log], [com.jmd.helper.core.AppFileLogger]
 * - Android Framework & Jetpack: [android.content.BroadcastReceiver]
 */

package com.jmd.helper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.scheduler.AlarmScheduler

/**
 * What it is:
 * System broadcast receiver triggered on [Intent.ACTION_TIME_CHANGED] and [Intent.ACTION_TIMEZONE_CHANGED].
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [TimeChangeReceiver].
 *
 * Linked with:
 * - Architecture: [TimeChangeReceiver], [TimeChangeReceiver]
 */
class TimeChangeReceiver : BroadcastReceiver() {

    /**
     * What it is:
     * Reschedules all pending tasks whenever device time or timezone shifts to maintain execution accuracy.
     *
     * What it does:
     * Executes [onReceive] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TimeChangeReceiver], [onReceive]
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
