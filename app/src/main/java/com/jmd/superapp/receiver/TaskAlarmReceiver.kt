/**
 * Package: com.jmd.superapp.receiver
 * Module: Broadcast Receivers Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [TaskAlarmReceiver].
 *
 * What it does:
 * Listens for exact RTC_WAKEUP alarms triggered by Android AlarmManager for scheduled task tasks. Wakes the device, logs alarm activation, updates task execution state, and enqueues the task ID into [com.jmd.superapp.engine.SequentialTaskEngine].
 *
 * Linked With:
 * - Repositories: [com.jmd.superapp.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.superapp.data.model.ChannelType], [com.jmd.superapp.data.model.RecurrenceType], [com.jmd.superapp.data.model.ScheduledTask], [com.jmd.superapp.data.model.TaskStatus]
 * - Schedulers & Receivers: [com.jmd.superapp.scheduler.AlarmScheduler]
 * - Services & Engines: [com.jmd.superapp.engine.SequentialTaskEngine]
 * - Core Security & Utilities: [android.util.Log], [com.jmd.superapp.core.AppFileLogger], [com.jmd.superapp.core.TaskSafetyManager], [com.jmd.superapp.data.prefs.PreferenceManager]
 * - Android Framework & Jetpack: [android.content.BroadcastReceiver]
 */

package com.jmd.superapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmd.superapp.SuperApplication
import com.jmd.superapp.core.AppFileLogger
import com.jmd.superapp.core.TaskSafetyManager
import com.jmd.superapp.data.model.ChannelType
import com.jmd.superapp.data.model.RecurrenceType
import com.jmd.superapp.data.model.ScheduledTask
import com.jmd.superapp.data.model.TaskStatus
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.data.repo.AppRepository
import com.jmd.superapp.engine.SequentialTaskEngine
import com.jmd.superapp.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What it is:
 * High-priority wakeful receiver dispatched by Android AlarmManager when a scheduled task is due.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [TaskAlarmReceiver].
 *
 * Linked with:
 * - Architecture: [TaskAlarmReceiver], [TaskAlarmReceiver]
 */
class TaskAlarmReceiver : BroadcastReceiver() {
    /**
     * What it is:
     * Companion object constant and factory provider for [TaskAlarmReceiver].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [TaskAlarmReceiver]
     */

    companion object {
        private const val TAG = "TaskAlarmReceiver"
        const val EXTRA_CANCELLED = "extra_cancelled"
        const val ACTION_COUNTDOWN_TIMEOUT = "com.jmd.superapp.ACTION_COUNTDOWN_TIMEOUT"
        const val ACTION_FIRE_CAMPAIGN = "com.jmd.superapp.ACTION_FIRE_CAMPAIGN"

        /**
         * What it is:
         * Dumps complete diagnostics, active tasks, execution logs, and system error traces to a file in external storage (super_execution_dump.txt) for IDE inspection and support.
         *
         * What it does:
         * Executes [dumpExecutionLogsToFile] logic in accordance with system invariants.
         *
         * Linked with:
         * - Architecture: [TaskAlarmReceiver], [dumpExecutionLogsToFile]
         * @param context Active Android [Context].
         * @return Dump file if created successfully, null otherwise.
         */
        fun dumpExecutionLogsToFile(context: Context): java.io.File? {
            val repository = AppRepository(context)
            val logs = repository.getAllLogs()
            val tasks = repository.getAllTasks()
            val traceTail = AppFileLogger.readLogFile(context, 1000)
            val errTail = AppFileLogger.readErrorLog(context, 100)
            val isIdle = TaskSafetyManager.isDeviceIdle(context)
            val lastInteraction = TaskSafetyManager.lastHumanInteractionTimestamp.get()
            val now = System.currentTimeMillis()

            Log.i(TAG, "========== [START EXECUTION LOG DUMP (count=${logs.size})] ==========")
            logs.take(60).forEach { log ->
                Log.i(TAG, "EXEC_LOG: id=${log.id} taskId=${log.taskId} channel=${log.channel} recipient=${log.recipient} status=${log.status} details=${log.details}")
            }
            Log.i(TAG, "========== [END EXECUTION LOG DUMP] ==========")

            return try {
                val outDir = context.getExternalFilesDir(null) ?: context.filesDir
                val outFile = java.io.File(outDir, "super_execution_dump.txt")
                val content = buildString {
                    appendLine("=== SYSTEM IDLE & SAFETY STATE ===")
                    appendLine("isDeviceIdle = $isIdle")
                    appendLine("lastHumanInteractionTimestamp = $lastInteraction (${(now - lastInteraction) / 1000}s ago)")
                    appendLine("currentTimestamp = $now")
                    appendLine("\n=== SCHEDULED TASKS IN DATABASE (Total: ${tasks.size}) ===")
                    tasks.forEach { t ->
                        val triggerDiffSec = (t.triggerTimeMillis - now) / 1000
                        appendLine("[ID: ${t.id}] ${t.title} | Channel: ${t.channel.displayName} | Status: ${t.status} | Trigger: in ${triggerDiffSec}s | Recurrence: ${t.recurrence.displayName} (custom: ${t.customIntervalMinutes}) | Recipients: ${t.recipients}")
                    }
                    appendLine("\n=== EXECUTION LOGS (Total: ${logs.size}) ===")
                    logs.take(100).forEach { log ->
                        appendLine("[${log.timestampMillis}] [taskId=${log.taskId}] [${log.channel}] [${log.recipient}] status=${log.status} details=${log.details} snippet=${log.messageSnippet}")
                    }
                    appendLine("\n=== SYSTEM TRACE LOGS TAIL ===")
                    appendLine(traceTail)
                    appendLine("\n=== ERROR LOGS TAIL ===")
                    appendLine(errTail)
                }
                outFile.writeText(content)
                Log.i(TAG, "Dumped logs to ${outFile.absolutePath}")
                outFile
            } catch (e: Exception) {
                Log.w(TAG, "Failed dumping to external files dir: ${e.message}")
                null
            }
        }
    }

    /**
     * What it is:
     * Receives wakeful alarm broadcasts from AlarmManager, validates task existence and state, checks cancellation flags, and enqueues the task ID into [SequentialTaskEngine].
     *
     * What it does:
     * Executes [onReceive] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskAlarmReceiver], [onReceive]
     * @param context Active Context.
     * @param intent Alarm trigger intent carrying task ID and flags.
     */
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == ACTION_FIRE_CAMPAIGN) {
            val campId = intent.getLongExtra("campaign_id", 1L)
            val repo = AppRepository(context)
            val cfg = repo.getDailyCampaignConfig(campId)
            if (!cfg.isActive) {
                AppFileLogger.w(context, TAG, "ACTION_FIRE_CAMPAIGN suppressed: Campaign #$campId is inactive.")
                com.jmd.superapp.scheduler.DailyCampaignScheduler.cancel(context, campId)
                return
            }
            AppFileLogger.action(context, TAG, "ACTION_FIRE_CAMPAIGN received for Campaign #$campId. Triggering batch execution.")
            com.jmd.superapp.scheduler.DailyCampaignScheduler.triggerNow(context, campId)
            com.jmd.superapp.scheduler.DailyCampaignScheduler.scheduleOrUpdate(context, campId)
            return
        }

        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1L).let {
            if (it == -1L) intent.getIntExtra(AlarmScheduler.EXTRA_TASK_ID, -1).toLong() else it
        }.let {
            if (it == -1L) intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID)?.toLongOrNull() ?: -1L else it
        }.let {
            if (it == -1L) intent.getLongExtra("task_id", -1L) else it
        }.let {
            if (it == -1L) intent.getIntExtra("task_id", -1).toLong() else it
        }

        AppFileLogger.action(context, TAG, "TaskAlarmReceiver received action=${intent.action}, taskId=$taskId")
        if (intent.action == ACTION_COUNTDOWN_TIMEOUT) {
            AppFileLogger.action(context, TAG, "Countdown timer elapsed for Task #$taskId. Enqueuing for execution.")
        }

        if (taskId == -1L) {
            AppFileLogger.w(context, TAG, "TaskAlarmReceiver: taskId missing or invalid (-1)")
            return
        }
        val isCancelled = intent.getBooleanExtra(EXTRA_CANCELLED, false)

        val repository = AppRepository(context)
        val task = repository.getTaskById(taskId) ?: run {
            AppFileLogger.w(context, TAG, "Task #$taskId not found in database")
            Log.w(TAG, "Task $taskId not found in database")
            return
        }

        if (isCancelled) {
            Log.i(TAG, "Task #$taskId execution cancelled via intent flag. Updating DB and cancelling alarms.")
            AppFileLogger.action(context, TAG, "Task #$taskId execution cancelled via intent flag.")
            repository.updateTaskStatus(taskId, TaskStatus.CANCELLED)
            AlarmScheduler(context).cancelTask(taskId, task)
            return
        }

        val isManualRun = intent.getBooleanExtra(AlarmScheduler.EXTRA_MANUAL_RUN, false)
        if (isManualRun) {
            AppFileLogger.action(context, TAG, "Manual run requested for Task #$taskId (current status: ${task.status}). Resetting to PENDING.")
            if (task.status != TaskStatus.PENDING) {
                repository.updateTaskStatus(taskId, TaskStatus.PENDING)
            }
        } else {
            if (task.status == TaskStatus.CANCELLED) {
                Log.w(TAG, "Task #$taskId is marked CANCELLED in DB. Skipping execution.")
                AppFileLogger.w(context, TAG, "Alarm triggered for CANCELLED Task #$taskId. Suppressed.")
                return
            }

            if (task.status == TaskStatus.PENDING_CONFIRMATION && task.recurrence == RecurrenceType.ONCE) {
                Log.w(TAG, "Task #$taskId is Awaiting Confirmation. Skipping stale alarm trigger without manual retry.")
                AppFileLogger.w(context, TAG, "Alarm triggered for PENDING_CONFIRMATION Task #$taskId. Suppressed.")
                return
            }

            if (task.status == TaskStatus.FAILED && task.recurrence == RecurrenceType.ONCE) {
                Log.w(TAG, "Task #$taskId is marked FAILED in DB. Skipping stale alarm trigger without manual retry.")
                AppFileLogger.w(context, TAG, "Alarm triggered for FAILED Task #$taskId. Suppressed.")
                AlarmScheduler(context).cancelTask(taskId, task)
                return
            }

            if (task.status == TaskStatus.DONE && task.recurrence == RecurrenceType.ONCE) {
                Log.d(TAG, "Task #$taskId is already marked DONE. Skipping alarm trigger.")
                AlarmScheduler(context).cancelTask(taskId, task)
                return
            }
        }

        // Pre-flight Idle Check: Only trigger and complete task when idle (unless manually triggered from UI)
        if (!isManualRun && !TaskSafetyManager.isDeviceIdle(context)) {
            val idleSince = (System.currentTimeMillis() - TaskSafetyManager.lastHumanInteractionTimestamp.get()) / 1000
            val reason = "Alarm fired while device was in active human use (${idleSince}s since last interaction)"
            Log.i(TAG, "Task #$taskId postponed by 30s: $reason")
            AppFileLogger.action(context, TAG, "Auto-postponing Task #$taskId: $reason. Will trigger once device is idle.")
            TaskSafetyManager.postponeTask(context, taskId, reason = reason)
            return
        }

        Log.d(TAG, "Alarm triggered for Task ID: $taskId. Routing to SequentialTaskEngine.")
        AppFileLogger.action(context, TAG, "Alarm triggered for Task #${task.id} (${task.title}) [${task.channel.displayName}] -> SequentialTaskEngine")
        val enqueued = SequentialTaskEngine.getInstance(context).enqueue(taskId)
        if (!enqueued) {
            Log.w(TAG, "Task #$taskId is already active or queued in SequentialTaskEngine. Suppressing duplicate alarm dispatch.")
            AppFileLogger.w(context, TAG, "Task #$taskId already active in SequentialTaskEngine pipeline. Duplicate alarm trigger suppressed.")
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Unexpected error in TaskAlarmReceiver onReceive", t)
    }
}

    private fun parseStringListExtra(intent: Intent, key: String): List<String> {
        val stringVal = intent.getStringExtra(key)
        if (!stringVal.isNullOrBlank()) {
            return stringVal.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        val arrayVal = intent.getStringArrayExtra(key)
        if (!arrayVal.isNullOrEmpty()) {
            return arrayVal.toList()
        }
        val listVal = intent.getStringArrayListExtra(key)
        if (!listVal.isNullOrEmpty()) {
            return listVal
        }
        return emptyList()
    }
}

