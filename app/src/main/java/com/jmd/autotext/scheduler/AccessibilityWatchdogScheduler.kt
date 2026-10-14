package com.jmd.autotext.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AccessibilityWatchdogScheduler {

    private const val TAG = "A11yScheduler"
    const val ACTION_WATCHDOG_HEARTBEAT = "com.jmd.autotext.ACTION_WATCHDOG_HEARTBEAT"
    private const val REQUEST_CODE = 9911
    private const val INTERVAL_MILLIS = 15 * 60 * 1000L // 15 minutes (Energy-efficient background keep-alive)

    fun scheduleHeartbeat(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AccessibilityWatchdogReceiver::class.java).apply {
            action = ACTION_WATCHDOG_HEARTBEAT
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + INTERVAL_MILLIS
        try {
            // Using standard alarm instead of aggressive exact while-idle wakeups to prevent CPU thrashing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerTime, pi)
            }
            Log.d(TAG, "Scheduled next watchdog heartbeat for ${INTERVAL_MILLIS / 60000} minutes from now")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set watchdog alarm: ${e.message}")
            try {
                am.set(AlarmManager.RTC_WAKEUP, triggerTime, pi)
            } catch (ignored: Exception) {}
        }
    }
}
