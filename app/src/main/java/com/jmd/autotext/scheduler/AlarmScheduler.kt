package com.jmd.autotext.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.RecurrenceType
import com.jmd.autotext.model.ScheduledTask
import com.jmd.autotext.model.TaskStatus
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleTask(task: ScheduledTask) {
        val triggerTime = if (task.triggerTimeMillis <= System.currentTimeMillis()) {
            calculateNextTrigger(task, System.currentTimeMillis())
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d(TAG, "Scheduled task ${task.id} (${task.title}) for trigger at $triggerTime")
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission missing, fallback to setWindow", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelTask(taskId: Long) {
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
        }
    }

    fun rescheduleAllPending() {
        val repository = AppRepository(context)
        val pendingTasks = repository.getPendingTasks()
        Log.d(TAG, "Rescheduling ${pendingTasks.size} pending tasks")
        for (task in pendingTasks) {
            scheduleTask(task)
        }
    }

    /**
     * Computes the next fire time for a recurring task.
     *
     * Extracted into the companion object (rather than the instance) so the
     * recurrence rules are pure and unit-testable without an Android [Context].
     * All arithmetic uses the device's local [Calendar] so that daylight-saving
     * transitions are handled by the platform rather than by manual offsets.
     *
     * Note: this helper advances strictly forward from [fromTime]; callers that
     * need "skip a missed window" semantics must handle that separately.
     */
    fun calculateNextTrigger(task: ScheduledTask, fromTime: Long): Long =
        calculateNextTrigger(task.recurrence, task.customIntervalMinutes, fromTime)

    companion object {
        private const val TAG = "AlarmScheduler"
        const val ACTION_FIRE_TASK = "com.jmd.autotext.ACTION_FIRE_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"

        /** Safety bound so a malformed rule can never spin forever. */
        private const val MAX_DAY_ADVANCE = 400

        /**
         * Pure recurrence calculation — no Android dependencies.
         *
         * @param recurrence the recurrence rule to advance by
         * @param customIntervalMinutes interval used only by [RecurrenceType.CUSTOM_INTERVAL]
         * @param fromTime the reference instant, in epoch millis
         * @return the next trigger instant, strictly after [fromTime] for every
         *   recurring rule
         */
        fun calculateNextTrigger(recurrence: RecurrenceType, customIntervalMinutes: Int, fromTime: Long): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = fromTime }

            return when (recurrence) {
                RecurrenceType.ONCE -> fromTime

                RecurrenceType.DAILY -> {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    cal.timeInMillis
                }

                RecurrenceType.WEEKDAYS -> {
                    advanceUntil(cal) {
                        val dow = it.get(Calendar.DAY_OF_WEEK)
                        dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
                    }
                    cal.timeInMillis
                }

                RecurrenceType.WEEKENDS -> {
                    advanceUntil(cal) {
                        val dow = it.get(Calendar.DAY_OF_WEEK)
                        dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
                    }
                    cal.timeInMillis
                }

                RecurrenceType.WEEKLY -> {
                    cal.add(Calendar.WEEK_OF_YEAR, 1)
                    cal.timeInMillis
                }

                RecurrenceType.MONTHLY -> {
                    // Calendar.add already clamps the day-of-month for short
                    // months (31 Jan -> 28/29 Feb, 31 Aug -> 30 Sep), so no
                    // manual correction is required. This behaviour is pinned
                    // by RecurrenceEngineTest.monthlyFromJanThirtyOne*.
                    cal.add(Calendar.MONTH, 1)
                    cal.timeInMillis
                }

                RecurrenceType.YEARLY -> {
                    // 29 Feb degrades to 28 Feb on non-leap years via the same
                    // clamping behaviour; pinned by RecurrenceEngineTest.
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
         * Steps [cal] forward one day at a time until [predicate] accepts it.
         * Stops after [MAX_DAY_ADVANCE] iterations as a guard against a
         * non-terminating predicate.
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
