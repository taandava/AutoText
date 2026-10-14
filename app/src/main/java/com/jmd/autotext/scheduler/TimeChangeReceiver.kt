package com.jmd.autotext.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmd.autotext.util.AppFileLogger

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
            Log.d("TimeChangeReceiver", "Time change detected ($action), rescheduling alarms...")
            AppFileLogger.action(context, "TimeChangeReceiver", "System time/timezone changed ($action). Rescheduling alarms.")
            AlarmScheduler(context).rescheduleAllPending()
        }
    }
}
