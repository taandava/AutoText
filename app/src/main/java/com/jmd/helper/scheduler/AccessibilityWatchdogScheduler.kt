/**
 * Package: com.jmd.helper.scheduler
 * Module: Schedulers & Background Workers
 *
 * What it is:
 * Architecture definition and implementation for [AccessibilityWatchdogScheduler].
 *
 * What it does:
 * Schedules recurring exact-while-idle alarms with Android AlarmManager to periodically evaluate the binder health of [com.jmd.helper.service.TaskAccessibilityService].
 *
 * Linked With:
 * - Schedulers & Receivers: [android.app.AlarmManager], [com.jmd.helper.receiver.AccessibilityWatchdogReceiver]
 * - Core Security & Utilities: [android.util.Log], [androidx.work.WorkManager], [java.util.concurrent.TimeUnit]
 * - Android Framework & Jetpack: [android.app.PendingIntent], [android.os.Build], [androidx.work.ExistingPeriodicWorkPolicy], [androidx.work.PeriodicWorkRequestBuilder]
 */

package com.jmd.helper.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jmd.helper.receiver.AccessibilityWatchdogReceiver
import java.util.concurrent.TimeUnit

/**
 * What it is:
 * Watchdog alarm scheduler ensuring recurring checks every 15 minutes.
 *
 * What it does:
 * Integrates dual-layer redundancy: 1. High-frequency exact AlarmManager RTC_WAKEUP alarms. 2. Persistent periodic WorkManager tasks surviving battery optimization and deep sleep.
 *
 * Linked with:
 * - Architecture: [AccessibilityWatchdogScheduler], [AccessibilityWatchdogScheduler]
 */
object AccessibilityWatchdogScheduler {

    private const val TAG = "A11yScheduler"
    const val ACTION_WATCHDOG_HEARTBEAT = "com.jmd.helper.ACTION_WATCHDOG_HEARTBEAT"
    private const val REQUEST_CODE = 9911
    private const val INTERVAL_MILLIS = 15 * 60 * 1000L

    /**
     * What it is:
     * Schedules the next watchdog heartbeat alarm using exact-while-idle alarms, and ensures the background WorkManager watchdog worker is enqueued.
     *
     * What it does:
     * Executes [scheduleHeartbeat] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [AccessibilityWatchdogScheduler], [scheduleHeartbeat]
     * @param context Active Android [Context].
     */
    fun scheduleHeartbeat(context: Context) {
        enqueueWorkManagerWatchdog(context)

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
            }
            Log.d(TAG, "Scheduled next watchdog heartbeat for ${INTERVAL_MILLIS / 60000} minutes from now")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set exact watchdog alarm: ${e.message}")
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
            } catch (fallbackEx: Exception) {
                Log.w(TAG, "Fallback alarm scheduling failed: ${fallbackEx.message}")
            }
        }
    }

    /**
     * What it is:
     * Enqueues a persistent periodic [AccessibilityWatchdogWorker] to guarantee heartbeat execution every 15 minutes even under aggressive OEM power saving policies.
     *
     * What it does:
     * Executes [enqueueWorkManagerWatchdog] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [AccessibilityWatchdogScheduler], [enqueueWorkManagerWatchdog]
     * @param context Active Android [Context].
     */
    fun enqueueWorkManagerWatchdog(context: Context) {
        try {
            val workRequest = PeriodicWorkRequestBuilder<AccessibilityWatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                AccessibilityWatchdogWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Enqueued unique periodic AccessibilityWatchdogWorker")
        } catch (e: Exception) {
            Log.w(TAG, "Could not enqueue WorkManager watchdog: ${e.message}")
        }
    }
}
