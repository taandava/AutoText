/**
 * Package: com.jmd.supersms.scheduler
 * Module: Schedulers & Background Workers
 *
 * What it is:
 * Architecture definition and implementation for [AlarmScheduler].
 *
 * What it does:
 * Configures exact RTC_WAKEUP alarms with Android AlarmManager across API versions (API 26 through API 36+), strictly verifying [AlarmManager.canScheduleExactAlarms] on Android 12+ (API 31+) and falling back to inexact windowed alarms when exact alarm privileges are restricted.
 *
 * Linked With:
 * - Repositories: [com.jmd.supersms.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.supersms.data.model.ChannelType], [com.jmd.supersms.data.model.RecurrenceType], [com.jmd.supersms.data.model.ScheduledTask], [com.jmd.supersms.data.model.TaskStatus]
 * - Schedulers & Receivers: [android.app.AlarmManager], [android.provider.AlarmClock], [com.jmd.supersms.receiver.TaskAlarmReceiver]
 * - Core Security & Utilities: [android.util.Log], [com.jmd.supersms.core.AppFileLogger], [com.jmd.supersms.core.TaskSafetyManager], [com.jmd.supersms.data.prefs.PreferenceManager]
 * - Android Framework & Jetpack: [android.app.PendingIntent], [android.os.Build]
 */

package com.jmd.supersms.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.jmd.supersms.core.AppFileLogger
import com.jmd.supersms.core.TaskSafetyManager
import com.jmd.supersms.data.model.ChannelType
import com.jmd.supersms.data.model.RecurrenceType
import com.jmd.supersms.data.model.ScheduledTask
import com.jmd.supersms.data.model.TaskStatus
import com.jmd.supersms.data.prefs.PreferenceManager
import com.jmd.supersms.data.repo.AppRepository
import com.jmd.supersms.receiver.TaskAlarmReceiver
import java.util.Calendar

/**
 * What it is:
 * Manages exact alarm registration, cancellation, and recurrence calculations.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [AlarmScheduler].
 *
 * Linked with:
 * - Architecture: [AlarmScheduler], [AlarmScheduler]
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
        ?: (context.getSystemService(AlarmManager::class.java))

    // --- 1-word / 2-word shortened function aliases ---
    /**
     * What it is:
     * Execution routine for [schedule].
     *
     * What it does:
     * Coordinates schedule operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AlarmScheduler]
     */
    fun schedule(task: ScheduledTask) = scheduleTask(task)
    /**
     * What it is:
     * Execution routine for [cancel].
     *
     * What it does:
     * Coordinates cancel operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AlarmScheduler]
     */
    fun cancel(taskId: Long) = cancelTask(taskId)

    /**
     * What it is:
     * Purges and cancels all pending AlarmManager alarms and system clock alarms for all tasks
     * that are DONE, FAILED, CANCELLED, or PAUSED, or whose trigger timestamp has already expired.
     */
    fun cleanOrphanedAndCompletedAlarms() {
        cleanOrphanedAndCompletedAlarms(context)
    }

    /**
     * What it is:
     * Schedules a task for exact or windowed execution using Android's [AlarmManager].
     *
     * What it does:
     * Checks [AlarmManager.canScheduleExactAlarms] on Android 12+ (API 31+) and falls back to `setAndAllowWhileIdle` if exact alarm scheduling is not granted by the user.
     *
     * Linked with:
     * - Architecture: [AlarmScheduler], [scheduleTask]
     * @param task [ScheduledTask] configuration to schedule.
     */
    fun scheduleTask(task: ScheduledTask) {
        if (task.status == TaskStatus.CANCELLED || task.status == TaskStatus.PAUSED) {
            Log.w(TAG, "Refusing to schedule ${task.status.name} Task #${task.id}")
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

        val now = System.currentTimeMillis()
        val triggerTime = if (task.triggerTimeMillis <= now) {
            val calculated = calculateNextFutureTrigger(task, now)
            Log.i(TAG, "Task #${task.id} scheduled time had elapsed (${task.triggerTimeMillis} <= now). Auto-rescheduled to future trigger: $calculated")
            try {
                val repo = AppRepository(context)
                repo.updateTask(task.copy(triggerTimeMillis = calculated, status = TaskStatus.SCHEDULED))
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist auto-rescheduled trigger timestamp in repository: ${e.message}")
            }
            calculated
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
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission missing, fallback to setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            AppFileLogger.w(context, TAG, "Exact alarm permission missing for Task #${task.id}, used setAndAllowWhileIdle fallback")
        }
    }

    /**
     * Cancels any pending [AlarmManager] alarms associated with the specified task ID.
     *
     * What it does:
     * Executes [cancelTask] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [AlarmScheduler], [cancelTask]
     * @param taskId Unique identifier of the task to cancel.
     * @param task Optional pre-loaded [ScheduledTask] instance (if available).
     */
    fun cancelTask(taskId: Long, task: ScheduledTask? = null) {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_TASK
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Cancelled alarm for task $taskId")
        AppFileLogger.action(context, TAG, "Cancelled alarm for Task #$taskId")

        try {
            com.jmd.supersms.engine.SequentialTaskEngine.getInstance(context).cancelTask(taskId)
        } catch (t: Throwable) {
            AppFileLogger.w(context, TAG, "Failed to cancel task #$taskId in SequentialTaskEngine: ${t.message}")
        }
    }

    /**
     * What it is:
     * Cancels all scheduled alarms for all tasks currently registered in the database.
     *
     * What it does:
     * Executes [cancelAll] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [AlarmScheduler], [cancelAll]
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
     * What it is:
     * Scans the database for all tasks, re-registers alarms with the [AlarmManager] for pending/scheduled tasks,
     * and guarantees that all completed, failed, paused, or cancelled tasks have their alarms cleared from Android AlarmManager.
     *
     * What it does:
     * Invoked after device boot, timezone alterations, or app upgrades.
     *
     * Linked with:
     * - Architecture: [AlarmScheduler], [rescheduleAllPending]
     */
    fun rescheduleAllPending() {
        val repository = AppRepository(context)
        repository.recoverInterruptedRunningTasks()
        cleanOrphanedAndCompletedAlarms(context)
        val allTasks = repository.getAllTasks()
        var scheduledCount = 0
        var cleanedCount = 0
        for (task in allTasks) {
            if (task.status.isPendingOrScheduled) {
                scheduleTask(task)
                scheduledCount++
            } else {
                cancelTask(task.id, task)
                cleanedCount++
            }
        }
        Log.d(TAG, "Rescheduled $scheduledCount pending tasks, cleaned $cleanedCount inactive tasks across system event")
        AppFileLogger.action(context, TAG, "Rescheduled $scheduledCount pending tasks, cleaned $cleanedCount inactive tasks across system event")
    }
    /**
     * What it is:
     * Companion object constant and factory provider for [AlarmScheduler].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [AlarmScheduler]
     */

    companion object {
        private const val TAG = "AlarmScheduler"
        const val ACTION_FIRE_TASK = "com.jmd.supersms.ACTION_FIRE_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_MANUAL_RUN = "com.jmd.supersms.EXTRA_MANUAL_RUN"

        private const val MAX_DAY_ADVANCE = 400

        /**
         * What it is:
         * Scans the database and proactively purges and cancels all pending AlarmManager alarms
         * and system clock alarms for all tasks that are DONE, FAILED, CANCELLED, or PAUSED, or whose
         * trigger timestamp has expired without recurrence.
         *
         * What it does:
         * Guarantees zero residual alarms clutter device settings or clock managers.
         */
        @JvmStatic
        fun cleanOrphanedAndCompletedAlarms(context: Context) {
            val repository = AppRepository(context)
            val allTasks = repository.getAllTasks()
            val scheduler = AlarmScheduler(context)
            val now = System.currentTimeMillis()
            var cleanedCount = 0
            for (task in allTasks) {
                val isCompletedOrInactive = !task.status.isPendingOrScheduled
                val isExpiredOnce = task.recurrence == RecurrenceType.ONCE && task.triggerTimeMillis < now - 60_000L
                if (isCompletedOrInactive || isExpiredOnce) {
                    scheduler.cancelTask(task.id, task)
                    cleanedCount++
                }
            }
            Log.d(TAG, "cleanOrphanedAndCompletedAlarms: Purged and cancelled alarms for $cleanedCount inactive/expired tasks")
            AppFileLogger.action(context, TAG, "Purged and cancelled alarms for $cleanedCount inactive/expired tasks from AlarmManager")
        }


        /**
         * What it is:
         * Computes the epoch millisecond timestamp for the next trigger of a [ScheduledTask] strictly in the future relative to [now], preserving the user's scheduled time of day for recurring tasks based on its recurrence type, interval, and selected days of week.
         *
         * What it does:
         * Executes [calculateNextFutureTrigger] logic in accordance with system invariants.
         *
         * Linked with:
         * - Architecture: [AlarmScheduler], [calculateNextFutureTrigger]
         * @param task Target [ScheduledTask].
         * @param now Reference millisecond timestamp (defaults to current time).
         * @return Next trigger timestamp in epoch milliseconds.
         */
        fun calculateNextFutureTrigger(task: ScheduledTask, now: Long = System.currentTimeMillis()): Long {
            if (task.triggerTimeMillis > now) {
                return task.triggerTimeMillis
            }

            if (task.recurrence == RecurrenceType.ONCE) {
                val origCal = Calendar.getInstance().apply {
                    timeInMillis = if (task.triggerTimeMillis > 0L) task.triggerTimeMillis else now
                }
                val targetHour = origCal.get(Calendar.HOUR_OF_DAY)
                val targetMinute = origCal.get(Calendar.MINUTE)
                val targetSecond = origCal.get(Calendar.SECOND)

                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMinute)
                    set(Calendar.SECOND, targetSecond)
                    set(Calendar.MILLISECOND, 0)
                }

                if (cal.timeInMillis <= now) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }

                if (task.selectedDaysOfWeek.isNotEmpty()) {
                    var guard = 0
                    while (cal.get(Calendar.DAY_OF_WEEK) !in task.selectedDaysOfWeek && guard < 14) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                        guard++
                    }
                }
                return cal.timeInMillis
            }

            val cal = Calendar.getInstance().apply {
                timeInMillis = if (task.triggerTimeMillis > 0L) task.triggerTimeMillis else now
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

        /**
         * What it is:
         * Operational routine [calculateNextTrigger].
         *
         * What it does:
         * Executes calculateNextTrigger actions according to system invariants.
         *
         * Linked with:
         * - Component: [AlarmScheduler]
         */
        @JvmStatic
        fun calculateNextTrigger(task: ScheduledTask, fromTime: Long = System.currentTimeMillis()): Long =
            calculateNextTrigger(task.recurrence, task.customIntervalMinutes, fromTime, task.selectedDaysOfWeek)

        /**
         * What it is:
         * Core recurrence calculation resolving the next trigger time.
         *
         * What it does:
         * Handles ONCE, DAILY, WEEKDAYS, WEEKENDS, WEEKLY, MONTHLY, YEARLY, and CUSTOM_INTERVAL.
         *
         * Linked with:
         * - Architecture: [AlarmScheduler], [calculateNextTrigger]
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
        /**
         * What it is:
         * Operational routine [advanceUntil].
         *
         * What it does:
         * Executes advanceUntil actions according to system invariants.
         *
         * Linked with:
         * - Component: [AlarmScheduler]
         */

        private fun advanceUntil(cal: Calendar, predicate: (Calendar) -> Boolean) {
            var guard = 0
            do {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                guard++
            } while (!predicate(cal) && guard < MAX_DAY_ADVANCE)
        }
    }
}

