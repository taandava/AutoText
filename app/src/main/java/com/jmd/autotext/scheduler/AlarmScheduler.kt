

/**
 * Package: com.jmd.autotext.scheduler
 * Module: Alarm Scheduling Layer
 *
 * Configures exact RTC_WAKEUP alarms with Android AlarmManager across API versions (API 26 through API 36+),
 * strictly verifying [AlarmManager.canScheduleExactAlarms] on Android 12+ (API 31+) and falling back
 * to inexact windowed alarms when exact alarm privileges are restricted.
 *
 * Linked With:
 * - Receiver: [com.jmd.autotext.receiver.TaskAlarmReceiver]
 * - Engine: [com.jmd.autotext.engine.SequentialTaskEngine]
 * - Repository: [com.jmd.autotext.data.repo.AppRepository]
 * - Diagnostics: [com.jmd.autotext.core.AppFileLogger]
 */
package com.jmd.autotext.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.core.AutomationSafetyManager
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.RecurrenceType
import com.jmd.autotext.data.model.ScheduledTask
import com.jmd.autotext.data.model.TaskStatus
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.receiver.TaskAlarmReceiver
import java.util.Calendar

/**
 * Manages exact alarm registration, cancellation, and recurrence calculations.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
        ?: (context.getSystemService(AlarmManager::class.java))

    // --- 1-word / 2-word shortened function aliases ---
    fun schedule(task: ScheduledTask) = scheduleTask(task)
    fun cancel(taskId: Long) = cancelTask(taskId)

    /**
     * Schedules a task for exact or windowed execution using Android's [AlarmManager].
     * Checks [AlarmManager.canScheduleExactAlarms] on Android 12+ (API 31+) and falls back
     * to `setAndAllowWhileIdle` if exact alarm scheduling is not granted by the user.
     * For [ChannelType.REMINDER], registers an alarm clock with the system clock app.
     *
     * @param task [ScheduledTask] configuration to schedule.
     */
    fun scheduleTask(task: ScheduledTask) {
        if (task.status == TaskStatus.CANCELLED) {
            Log.w(TAG, "Refusing to schedule CANCELLED Task #${task.id}")
            AlarmScheduler(context).cancelTask(task.id, task)
            return
        }
        if (task.status == TaskStatus.DONE && task.recurrence == RecurrenceType.ONCE) {
            Log.w(TAG, "Refusing to schedule DONE Task #${task.id}")
            AlarmScheduler(context).cancelTask(task.id, task)
            return
        }
        if (task.status == TaskStatus.FAILED && task.recurrence == RecurrenceType.ONCE) {
            Log.w(TAG, "Refusing to schedule FAILED Task #${task.id}")
            AlarmScheduler(context).cancelTask(task.id, task)
            return
        }
        val prefs = PreferenceManager(context)
        if (!prefs.isTasksMasterEnabled) {
            Log.w(TAG, "Refusing to schedule Task #${task.id}: Task Scheduler Master Switch is OFF")
            AlarmScheduler(context).cancelTask(task.id, task)
            return
        }
        if (AutomationSafetyManager.isEmergencyStopActive(context)) {
            Log.w(TAG, "Refusing to schedule Task #${task.id}: Emergency Stop is ACTIVE")
            AlarmScheduler(context).cancelTask(task.id, task)
            return
        }

        val now = System.currentTimeMillis()
        if (task.recurrence == RecurrenceType.ONCE && task.triggerTimeMillis < now - 60_000L) {
            Log.w(TAG, "Task #${task.id} scheduled time has already passed (${now - task.triggerTimeMillis}ms ago). Cancelling to avoid spontaneous trigger.")
            AlarmScheduler(context).cancelTask(task.id, task)
            val repo = AppRepository(context)
            repo.updateTaskStatus(task.id, TaskStatus.CANCELLED)
            return
        }

        val triggerTime = if (task.triggerTimeMillis <= now) {
            calculateNextFutureTrigger(task, now)
        } else {
            task.triggerTimeMillis
        }

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_TASK
            putExtra(EXTRA_TASK_ID, task.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            if (task.channel == ChannelType.REMINDER) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
                try {
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d(TAG, "Scheduled system AlarmClock for task ${task.id} (${task.title}) at $triggerTime")
                    AppFileLogger.action(context, TAG, "Scheduled System Alarm Clock for Task #${task.id} (${task.title}) at $triggerTime")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed setAlarmClock, falling back to setExactAndAllowWhileIdle", e)
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
                setDeviceSystemAlarm(task)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
                Log.d(TAG, "Scheduled task ${task.id} (${task.title}) for trigger at $triggerTime")
                AppFileLogger.action(context, TAG, "Scheduled Task #${task.id} (${task.title}) for trigger at $triggerTime")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission missing, fallback to setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            AppFileLogger.w(context, TAG, "Exact alarm permission missing for Task #${task.id}, used setAndAllowWhileIdle fallback")
        }
    }

    /**
     * Integrates with Android's system clock app using [AlarmClock.ACTION_SET_ALARM]
     * to set an audible device-level alarm for [ChannelType.REMINDER] tasks.
     * Uses a unique, task-specific label pattern to prevent colliding with or dismissing
     * unrelated personal alarms.
     *
     * @param context Active Android [Context].
     * @param task Reminder task configuration.
     */
    fun setDeviceSystemAlarm(task: ScheduledTask) {
        setDeviceSystemAlarm(context, task)
    }

    /**
     * Dismisses the device-level system alarm from Android's system clock app using [AlarmClock.ACTION_DISMISS_ALARM].
     * Specifically matches ONLY the exact unique label set for this task (`AutoText #<id>: ...`)
     * using [AlarmClock.ALARM_SEARCH_MODE_LABEL], strictly preserving all other user-configured alarms.
     *
     * @param task Target reminder task to dismiss from system clock.
     */
    fun dismissDeviceSystemAlarm(task: ScheduledTask) {
        dismissDeviceSystemAlarm(context, task)
    }

    /**
     * Cancels any pending [AlarmManager] alarms associated with the specified task ID,
     * and if the task is a [ChannelType.REMINDER], dismisses its corresponding system clock alarm.
     *
     * @param taskId Unique identifier of the task to cancel.
     * @param task Optional pre-loaded [ScheduledTask] instance (if available).
     */
    fun cancelTask(taskId: Long, task: ScheduledTask? = null) {
        val targetTask = task ?: runCatching { AppRepository(context).getTaskById(taskId) }.getOrNull()
        if (targetTask?.channel == ChannelType.REMINDER) {
            dismissDeviceSystemAlarm(targetTask)
        }

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_TASK
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for task $taskId")
            AppFileLogger.action(context, TAG, "Cancelled alarm for Task #$taskId")
        }
        try {
            com.jmd.autotext.engine.SequentialTaskEngine.getInstance(context).cancelTask(taskId)
        } catch (t: Throwable) {
            AppFileLogger.w(context, TAG, "Failed to cancel task #$taskId in SequentialTaskEngine: ${t.message}")
        }
    }

    /**
     * Cancels all scheduled alarms for all tasks currently registered in the database.
     */
    fun cancelAll() {
        val repository = AppRepository(context)
        val allTasks = repository.getAllTasks()
        for (task in allTasks) {
            AlarmScheduler(context).cancelTask(task.id, task)
        }
        AppFileLogger.action(context, TAG, "Cancelled all active alarms for ${allTasks.size} tasks")
    }

    /**
     * Scans the database for all pending tasks and re-registers alarms with the [AlarmManager].
     * Invoked after device boot, timezone alterations, or app upgrades.
     */
    fun rescheduleAllPending() {
        val prefs = PreferenceManager(context)
        if (!prefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(context)) {
            Log.d(TAG, "Master task scheduler is paused or emergency stop is active. Suppressing rescheduleAllPending.")
            cancelAll()
            return
        }
        val repository = AppRepository(context)
        val pendingTasks = repository.getPendingTasks()
        Log.d(TAG, "Rescheduling ${pendingTasks.size} pending tasks")
        for (task in pendingTasks) {
            scheduleTask(task)
        }
        AppFileLogger.action(context, TAG, "Rescheduled ${pendingTasks.size} pending tasks across system event")
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        const val ACTION_FIRE_TASK = "com.jmd.autotext.ACTION_FIRE_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_MANUAL_RUN = "com.jmd.autotext.EXTRA_MANUAL_RUN"

        private const val MAX_DAY_ADVANCE = 400

        /**
         * Generates a unique, non-colliding system alarm label identifying this exact task.
         * Prepends `AutoText #<id>: ` to ensure searches specifically match only alarms
         * scheduled by this application and never dismiss user personal alarms.
         *
         * @param task Target [ScheduledTask].
         * @return Formatted unique alarm label.
         */
        @JvmStatic
        fun getSystemAlarmLabel(task: ScheduledTask): String {
            val cleanTitle = task.title.ifBlank { task.messageText.ifBlank { "Reminder" } }.trim()
            return "AutoText #${task.id}: $cleanTitle"
        }

        /**
         * Integrates with Android's system clock app using [AlarmClock.ACTION_SET_ALARM]
         * to set an audible device-level alarm for [ChannelType.REMINDER] tasks.
         *
         * @param context Active Android [Context].
         * @param task Reminder task configuration.
         */
        @JvmStatic
        fun setDeviceSystemAlarm(context: Context, task: ScheduledTask) {
            if (task.channel != ChannelType.REMINDER) return
            try {
                val now = System.currentTimeMillis()
                val triggerTime = if (task.triggerTimeMillis <= now) {
                    calculateNextFutureTrigger(task, now)
                } else {
                    task.triggerTimeMillis
                }
                if (triggerTime <= now) {
                    Log.w(TAG, "Cannot register system clock alarm for task #${task.id}: trigger time is in the past ($triggerTime <= $now)")
                    return
                }
                val cal = Calendar.getInstance().apply { timeInMillis = triggerTime }
                val hour = cal[Calendar.HOUR_OF_DAY]
                val minute = cal[Calendar.MINUTE]
                val message = getSystemAlarmLabel(task)

                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    putExtra(AlarmClock.EXTRA_VIBRATE, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.i(TAG, "Registered system clock app alarm for task #${task.id} ($message at $hour:$minute)")
                AppFileLogger.action(context, TAG, "Registered System Clock App alarm for Task #${task.id} ($message) at $hour:$minute")
            } catch (e: Exception) {
                Log.w(TAG, "Could not set System Clock alarm via ACTION_SET_ALARM: ${e.message}")
            }
        }

        /**
         * Dismisses the device-level system alarm from Android's system clock app using [AlarmClock.ACTION_DISMISS_ALARM].
         * Uses [AlarmClock.ALARM_SEARCH_MODE_LABEL] with [getSystemAlarmLabel] to guarantee that
         * only alarms set by this task are dismissed, completely leaving other user alarms intact.
         *
         * @param context Active Android [Context].
         * @param task Target reminder task.
         */
        fun dismissDeviceSystemAlarm(context: Context, task: ScheduledTask) {
            if (task.channel != ChannelType.REMINDER) return
            try {
                val label = getSystemAlarmLabel(task)
                val dismissIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dismissIntent)
                Log.i(TAG, "Dismissed system clock alarm for task #${task.id} (label: $label)")
                AppFileLogger.action(context, TAG, "Dismissed System Clock alarm for Task #${task.id} (label: $label)")
            } catch (e: Exception) {
                Log.w(TAG, "Could not dismiss System Clock alarm via ACTION_DISMISS_ALARM: ${e.message}")
            }
        }

        /**
         * Computes the epoch millisecond timestamp for the next trigger of a [ScheduledTask]
         * based on its recurrence type, interval, and selected days of week.
         *
         * @param task Target [ScheduledTask].
         * @param fromTime Reference millisecond timestamp (defaults to current time).
         * @return Next trigger timestamp in epoch milliseconds.
         */
        /**
         * Calculates the next trigger timestamp strictly in the future relative to [now],
         * preserving the user's scheduled time of day for recurring tasks.
         */
        fun calculateNextFutureTrigger(task: ScheduledTask, now: Long = System.currentTimeMillis()): Long {
            if (task.recurrence == RecurrenceType.ONCE) {
                return task.triggerTimeMillis
            }
            val cal = Calendar.getInstance().apply {
                timeInMillis = if (task.triggerTimeMillis > 0) task.triggerTimeMillis else now
            }
            var guard = 0
            while (cal.timeInMillis <= now && guard < 1000) {
                guard++
                when (task.recurrence) {
                    RecurrenceType.REPEAT_SECONDS -> {
                        val intervalSec = if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 30
                        cal.add(Calendar.SECOND, maxOf(10, intervalSec))
                    }
                    RecurrenceType.REPEAT_MINUTES -> {
                        val intervalMins = if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 15
                        cal.add(Calendar.MINUTE, maxOf(1, intervalMins))
                    }
                    RecurrenceType.REPEAT_HOURS -> {
                        val intervalHours = if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 1
                        cal.add(Calendar.HOUR_OF_DAY, maxOf(1, intervalHours))
                    }
                    RecurrenceType.REPEAT_DAYS, RecurrenceType.DAILY -> {
                        val intervalDays = if (task.customIntervalMinutes > 0 && task.recurrence == RecurrenceType.REPEAT_DAYS) task.customIntervalMinutes else 1
                        cal.add(Calendar.DAY_OF_YEAR, maxOf(1, intervalDays))
                    }
                    RecurrenceType.REPEAT_WEEKS, RecurrenceType.WEEKLY -> {
                        val intervalWeeks = if (task.customIntervalMinutes > 0 && task.recurrence == RecurrenceType.REPEAT_WEEKS) task.customIntervalMinutes else 1
                        cal.add(Calendar.WEEK_OF_YEAR, maxOf(1, intervalWeeks))
                    }
                    RecurrenceType.REPEAT_MONTHS, RecurrenceType.MONTHLY -> {
                        val intervalMonths = if (task.customIntervalMinutes > 0 && task.recurrence == RecurrenceType.REPEAT_MONTHS) task.customIntervalMinutes else 1
                        cal.add(Calendar.MONTH, maxOf(1, intervalMonths))
                    }
                    RecurrenceType.WEEKDAYS -> {
                        advanceUntil(cal) {
                            val dow = it[Calendar.DAY_OF_WEEK]
                            dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
                        }
                    }
                    RecurrenceType.WEEKENDS -> {
                        advanceUntil(cal) {
                            val dow = it[Calendar.DAY_OF_WEEK]
                            dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
                        }
                    }
                    RecurrenceType.YEARLY -> {
                        cal.add(Calendar.YEAR, 1)
                    }
                    RecurrenceType.CUSTOM_INTERVAL -> {
                        val intervalMins = if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 60
                        cal.add(Calendar.MINUTE, intervalMins)
                    }
                    RecurrenceType.ONCE -> break
                }
                if (task.selectedDaysOfWeek.isNotEmpty() && task.recurrence in listOf(RecurrenceType.WEEKLY, RecurrenceType.WEEKDAYS, RecurrenceType.DAILY)) {
                    while (cal[Calendar.DAY_OF_WEEK] !in task.selectedDaysOfWeek && guard < 1000) {
                        guard++
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }
            return cal.timeInMillis
        }

        @JvmStatic
        fun calculateNextTrigger(task: ScheduledTask, fromTime: Long = System.currentTimeMillis()): Long =
            calculateNextTrigger(task.recurrence, task.customIntervalMinutes, fromTime, task.selectedDaysOfWeek)

        /**
         * Core recurrence calculation resolving the next trigger time.
         * Handles ONCE, DAILY, WEEKDAYS, WEEKENDS, WEEKLY, MONTHLY, YEARLY, and CUSTOM_INTERVAL.
         *
         * @param recurrence [RecurrenceType] pattern.
         * @param customIntervalMinutes Interval minutes for custom schedules.
         * @param fromTime Base timestamp to calculate forward from.
         * @param selectedDaysOfWeek Specific Calendar days of week selected by user (if any).
         * @return Calculated next trigger timestamp in epoch milliseconds.
         */
        @JvmStatic
        fun calculateNextTrigger(
            recurrence: RecurrenceType,
            customIntervalMinutes: Int,
            fromTime: Long,
            selectedDaysOfWeek: List<Int> = emptyList()
        ): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = fromTime }

            if (selectedDaysOfWeek.isNotEmpty() && (recurrence in listOf(RecurrenceType.WEEKLY, RecurrenceType.WEEKDAYS, RecurrenceType.DAILY))) {
                advanceUntil(cal) {
                    it[Calendar.DAY_OF_WEEK] in selectedDaysOfWeek
                }
                return cal.timeInMillis
            }

            return when (recurrence) {
                RecurrenceType.ONCE -> fromTime

                RecurrenceType.REPEAT_SECONDS -> {
                    val intervalSec = if (customIntervalMinutes > 0) customIntervalMinutes else 30
                    cal.add(Calendar.SECOND, maxOf(10, intervalSec))
                    cal.timeInMillis
                }

                RecurrenceType.REPEAT_MINUTES -> {
                    val intervalMins = if (customIntervalMinutes > 0) customIntervalMinutes else 15
                    cal.add(Calendar.MINUTE, maxOf(1, intervalMins))
                    cal.timeInMillis
                }

                RecurrenceType.REPEAT_HOURS -> {
                    val intervalHours = if (customIntervalMinutes > 0) customIntervalMinutes else 1
                    cal.add(Calendar.HOUR_OF_DAY, maxOf(1, intervalHours))
                    cal.timeInMillis
                }

                RecurrenceType.REPEAT_DAYS, RecurrenceType.DAILY -> {
                    val intervalDays = if (customIntervalMinutes > 0 && recurrence == RecurrenceType.REPEAT_DAYS) customIntervalMinutes else 1
                    cal.add(Calendar.DAY_OF_YEAR, maxOf(1, intervalDays))
                    cal.timeInMillis
                }

                RecurrenceType.REPEAT_WEEKS, RecurrenceType.WEEKLY -> {
                    val intervalWeeks = if (customIntervalMinutes > 0 && recurrence == RecurrenceType.REPEAT_WEEKS) customIntervalMinutes else 1
                    cal.add(Calendar.WEEK_OF_YEAR, maxOf(1, intervalWeeks))
                    cal.timeInMillis
                }

                RecurrenceType.REPEAT_MONTHS, RecurrenceType.MONTHLY -> {
                    val intervalMonths = if (customIntervalMinutes > 0 && recurrence == RecurrenceType.REPEAT_MONTHS) customIntervalMinutes else 1
                    cal.add(Calendar.MONTH, maxOf(1, intervalMonths))
                    cal.timeInMillis
                }

                RecurrenceType.WEEKDAYS -> {
                    advanceUntil(cal) {
                        val dow = it[Calendar.DAY_OF_WEEK]
                        dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
                    }
                    cal.timeInMillis
                }

                RecurrenceType.WEEKENDS -> {
                    advanceUntil(cal) {
                        val dow = it[Calendar.DAY_OF_WEEK]
                        dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
                    }
                    cal.timeInMillis
                }

                RecurrenceType.YEARLY -> {
                    cal.add(Calendar.YEAR, 1)
                    cal.timeInMillis
                }

                RecurrenceType.CUSTOM_INTERVAL -> {
                    val intervalMins = if (customIntervalMinutes > 0) customIntervalMinutes else 60
                    cal.add(Calendar.MINUTE, intervalMins)
                    cal.timeInMillis
                }
            }
        }

        private fun advanceUntil(cal: Calendar, predicate: (Calendar) -> Boolean) {
            var guard = 0
            do {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                guard++
            } while (!predicate(cal) && guard < MAX_DAY_ADVANCE)
        }
    }
}
