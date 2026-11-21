/**
 * Package: com.jmd.superapp.engine
 * Module: Task Execution & Dispatch Engines
 *
 * What it is:
 * Architecture definition and implementation for [SequentialTaskEngine].
 *
 * What it does:
 * Centralized sequential execution orchestrator for scheduled tasks across SMS, WhatsApp chats, WhatsApp Status stories, Phone Calls, Reminders, and Sound Mode events.
 *
 * Linked With:
 * - Repositories: [com.jmd.superapp.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.superapp.data.model.*]
 * - Schedulers & Receivers: [com.jmd.superapp.scheduler.AlarmScheduler], [com.jmd.superapp.ui.activity.ReminderAlarmActivity]
 * - Services & Engines: [com.jmd.superapp.service.TaskAccessibilityService], [com.jmd.superapp.whatsapp.WhatsAppIntentDispatcher]
 * - UI Screens & Components: [com.jmd.superapp.ui.activity.CallSimulationActivity], [com.jmd.superapp.ui.activity.TaskLauncherActivity]
 * - Core Security & Utilities: [android.app.KeyguardManager], [android.app.NotificationManager], [android.content.pm.PackageManager], [android.media.AudioManager]
 * - Android Framework & Jetpack: [android.app.PendingIntent], [android.net.Uri], [kotlinx.coroutines.channels.Channel], [kotlinx.coroutines.sync.Mutex]
 */

package com.jmd.superapp.engine

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jmd.superapp.R
import com.jmd.superapp.SuperApplication
import com.jmd.superapp.core.*
import com.jmd.superapp.data.model.*
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.data.repo.AppRepository
import com.jmd.superapp.scheduler.AlarmScheduler
import com.jmd.superapp.service.TaskAccessibilityService
import com.jmd.superapp.ui.activity.CallSimulationActivity
import com.jmd.superapp.ui.activity.TaskLauncherActivity
import com.jmd.superapp.whatsapp.WhatsAppIntentDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/** Upper bound for waiting on a WhatsApp send to complete before the task is retried. */
private const val WHATSAPP_SEND_TIMEOUT_MS = 35000L
private const val PROTECTED_WINDOW_MAX_WAIT_MS = 10 * 60_000L

/**
 * What it is:
 * Singleton sequential task execution pipeline.
 *
 * What it does:
 * Guarantees serial execution of UI task tasks via [uiTaskMutex] and safe concurrency for background cellular SMS dispatches via [smsMutex].
 *
 * Linked with:
 * - Architecture: [SequentialTaskEngine], [SequentialTaskEngine]
 */
class SequentialTaskEngine private constructor(private val appContext: Context) {

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val taskChannel = Channel<Long>(Channel.UNLIMITED)
    private val activeTaskIds = ConcurrentHashMap.newKeySet<Long>()

    private val uiTaskMutex = Mutex()
    private val smsMutex = Mutex()
    private val repository = AppRepository(appContext)

    private var currentTaskJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var currentTaskId: Long = -1L

    init {
        startSequentialProcessor()
    }
    /**
     * What it is:
     * Execution routine for [startSequentialProcessor].
     *
     * What it does:
     * Coordinates startSequentialProcessor operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SequentialTaskEngine]
     */

    private fun startSequentialProcessor() {
        engineScope.launch {
            for (taskId in taskChannel) {
                try {
                    val task = repository.getTaskById(taskId)
                    if (task == null) {
                        AppFileLogger.w(appContext, TAG, "Task #$taskId no longer exists in DB. Skipping.")
                        continue
                    }

                    if (task.status == TaskStatus.CANCELLED) {
                        AppFileLogger.w(appContext, TAG, "Task #$taskId is marked CANCELLED. Suppressing stale execution.")
                        continue
                    }

                    if ((task.status == TaskStatus.PENDING_CONFIRMATION) && (task.recurrence == RecurrenceType.ONCE)) {
                        AppFileLogger.w(appContext, TAG, "Task #$taskId is Awaiting Confirmation. Suppressing redispatch to prevent duplicate delivery.")
                        continue
                    }

                    if ((task.status == TaskStatus.FAILED) && (task.recurrence == RecurrenceType.ONCE)) {
                        AppFileLogger.w(appContext, TAG, "Task #$taskId is marked FAILED. Suppressing stale alarm execution without manual retry.")
                        continue
                    }

                    if ((task.status == TaskStatus.DONE) && (task.recurrence == RecurrenceType.ONCE)) {
                        AppFileLogger.i(appContext, TAG, "Task #$taskId is already marked DONE. Skipping.")
                        continue
                    }

                    currentTaskId = taskId
                    val job = launch {
                        executeTaskSafely(repository, task)
                    }
                    currentTaskJob = job
                    job.join()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    AppFileLogger.w(appContext, TAG, "Task #$taskId execution cancelled.")
                    val fresh = repository.getTaskById(taskId)
                    if (fresh != null && fresh.status == TaskStatus.RUNNING) {
                        val targetStatus = if (fresh.recurrence != RecurrenceType.ONCE) TaskStatus.PENDING else TaskStatus.CANCELLED
                        repository.updateTaskStatus(taskId, targetStatus)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error processing taskId=$taskId in sequential queue", e)
                    AppFileLogger.e(appContext, TAG, "Failed sequential execution of Task #$taskId: ${e.message}", e)
                    val fresh = repository.getTaskById(taskId)
                    if (fresh != null && fresh.status == TaskStatus.RUNNING) {
                        completeOrRescheduleTask(appContext, repository, fresh, success = false)
                    }
                } finally {
                    val fresh = repository.getTaskById(taskId)
                    if (fresh != null && fresh.status == TaskStatus.RUNNING) {
                        AppFileLogger.w(appContext, TAG, "Task #$taskId was still marked RUNNING upon exiting processor. Reconciling status.")
                        val targetStatus = if (fresh.recurrence != RecurrenceType.ONCE) TaskStatus.PENDING else TaskStatus.FAILED
                        repository.updateTaskStatus(taskId, targetStatus)
                    }
                    currentTaskId = -1L
                    currentTaskJob = null
                    activeTaskIds.remove(taskId)
                }
            }
        }
    }

    /**
     * What it is:
     * Cooperatively cancels a specific task if currently executing and removes it from active tracking.
     *
     * What it does:
     * Executes [cancelTask] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [SequentialTaskEngine], [cancelTask]
     */
    fun cancelTask(taskId: Long) {
        Log.i(TAG, "cancelTask requested for Task #$taskId")
        AppFileLogger.action(appContext, TAG, "cancelTask requested for Task #$taskId")
        activeTaskIds.remove(taskId)
        if (currentTaskId == taskId) {
            AppFileLogger.w(appContext, TAG, "Task #$taskId is currently active in engine. Cancelling coroutine job immediately!")
            currentTaskJob?.cancel(kotlinx.coroutines.CancellationException("Task #$taskId cancelled by user"))
            TaskAccessibilityService.clearPending()
        }
        val fresh = repository.getTaskById(taskId)
        if (fresh != null && fresh.status == TaskStatus.RUNNING) {
            val targetStatus = if (fresh.recurrence != RecurrenceType.ONCE) TaskStatus.PENDING else TaskStatus.CANCELLED
            repository.updateTaskStatus(taskId, targetStatus)
        }
    }

    /**
     * What it is:
     * Cooperatively aborts the currently running task, drains the sequential queue, and resets engine state.
     *
     * What it does:
     * Executes [cancelAll] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [SequentialTaskEngine], [cancelAll]
     */
    fun cancelAll() {
        Log.i(TAG, "cancelAll requested for SequentialTaskEngine")
        AppFileLogger.action(appContext, TAG, "cancelAll requested: Aborting active task and draining channel queue")
        val activeId = currentTaskId
        currentTaskJob?.cancel(kotlinx.coroutines.CancellationException("All tasks cancelled by user"))
        TaskAccessibilityService.clearPending()
        while (taskChannel.tryReceive().isSuccess) {
            // Drain channel
        }
        activeTaskIds.clear()
        if (activeId > 0) {
            val fresh = repository.getTaskById(activeId)
            if (fresh != null && fresh.status == TaskStatus.RUNNING) {
                val targetStatus = if (fresh.recurrence != RecurrenceType.ONCE) TaskStatus.PENDING else TaskStatus.CANCELLED
                repository.updateTaskStatus(activeId, targetStatus)
            }
        }
        currentTaskId = -1L
    }

    /**
     * What it is:
     * Returns true if this engine instance is currently executing or holding any scheduled task.
     *
     * What it does:
     * Checks currentTaskId and active queue state.
     *
     * Linked with:
     * - Architecture: [SequentialTaskEngine], [isEngineActive]
     */
    fun isEngineActive(): Boolean {
        return currentTaskId != -1L || activeTaskIds.isNotEmpty()
    }
    /**
     * What it is:
     * Companion object constant and factory provider for [SequentialTaskEngine].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [SequentialTaskEngine]
     */

    companion object {
        private const val TAG = "SequentialTaskEngine"

        @Volatile
        private var INSTANCE: SequentialTaskEngine? = null

        /**
         * What it is:
         * Static inquiry whether any task is actively executing in SequentialTaskEngine.
         *
         * What it does:
         * Queries the singleton instance state safely.
         *
         * Linked with:
         * - Architecture: [SequentialTaskEngine], [isEngineActive]
         */
        fun isEngineActive(): Boolean {
            return INSTANCE?.isEngineActive() ?: false
        }
        /**
         * What it is:
         * Operational routine [getInstance].
         *
         * What it does:
         * Executes getInstance actions according to system invariants.
         *
         * Linked with:
         * - Component: [SequentialTaskEngine]
         */

        fun getInstance(context: Context): SequentialTaskEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SequentialTaskEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
        /**
         * What it is:
         * Operational routine [getUiTaskMutex].
         *
         * What it does:
         * Executes getUiTaskMutex actions according to system invariants.
         *
         * Linked with:
         * - Component: [SequentialTaskEngine]
         */

        fun getUiTaskMutex(context: Context): Mutex {
            return getInstance(context).uiTaskMutex
        }
        /**
         * What it is:
         * Operational routine [completeOrRescheduleTask].
         *
         * What it does:
         * Executes completeOrRescheduleTask actions according to system invariants.
         *
         * Linked with:
         * - Component: [SequentialTaskEngine]
         */

        fun completeOrRescheduleTask(
            context: Context,
            repository: AppRepository,
            task: ScheduledTask,
            success: Boolean,
            pendingConfirmation: Boolean = false,
        ) {
            val now = System.currentTimeMillis()
            val freshTask = repository.getTaskById(task.id)
            if (freshTask == null) {
                Log.i(TAG, "Task #${task.id} no longer exists in DB. Suppressing reschedule.")
                AlarmScheduler(context).cancelTask(task.id, task)
                return
            }
            if (freshTask.status == TaskStatus.CANCELLED) {
                Log.i(TAG, "Task #${task.id} was marked CANCELLED by user. Preserving CANCELLED state and canceling alarms.")
                AlarmScheduler(context).cancelTask(task.id, freshTask)
                return
            }

            val isRecurring = freshTask.recurrence != RecurrenceType.ONCE
            if (isRecurring) {
                val effectiveRecurrence = freshTask.recurrence
                val effectiveInterval = if (freshTask.customIntervalMinutes > 0) freshTask.customIntervalMinutes else 60

                val scheduler = AlarmScheduler(context)
                val baseAnchor = if (freshTask.triggerTimeMillis > 0L) freshTask.triggerTimeMillis else now
                var nextTrigger = AlarmScheduler.calculateNextTrigger(
                    recurrence = effectiveRecurrence,
                    customIntervalMinutes = effectiveInterval,
                    fromTime = baseAnchor,
                    selectedDaysOfWeek = freshTask.selectedDaysOfWeek,
                )
                var safetyGuard = 0
                while ((nextTrigger <= now) && (safetyGuard < 500)) {
                    nextTrigger = AlarmScheduler.calculateNextTrigger(
                        recurrence = effectiveRecurrence,
                        customIntervalMinutes = effectiveInterval,
                        fromTime = nextTrigger,
                        selectedDaysOfWeek = freshTask.selectedDaysOfWeek,
                    )
                    safetyGuard++
                }
                if (nextTrigger <= now) {
                    nextTrigger = AlarmScheduler.calculateNextTrigger(
                        recurrence = effectiveRecurrence,
                        customIntervalMinutes = effectiveInterval,
                        fromTime = now,
                        selectedDaysOfWeek = freshTask.selectedDaysOfWeek,
                    )
                }

                val updatedTask = freshTask.copy(
                    recurrence = effectiveRecurrence,
                    customIntervalMinutes = effectiveInterval,
                    triggerTimeMillis = nextTrigger,
                    lastExecutedMillis = now,
                    executionCount = freshTask.executionCount + 1,
                    status = TaskStatus.PENDING,
                )
                repository.updateTask(updatedTask)
                repository.clearDeliveriesForTask(task.id)
                scheduler.scheduleTask(updatedTask)
                val unitSuffix = when (effectiveRecurrence) {
                    RecurrenceType.REPEAT_SECONDS -> "s"
                    RecurrenceType.REPEAT_HOURS -> "h"
                    RecurrenceType.REPEAT_DAYS, RecurrenceType.DAILY -> "d"
                    RecurrenceType.REPEAT_WEEKS, RecurrenceType.WEEKLY -> "w"
                    RecurrenceType.REPEAT_MONTHS, RecurrenceType.MONTHLY -> "mo"
                    else -> "m"
                }
                AppFileLogger.action(
                    context,
                    TAG,
                    "Task #${task.id} (${task.title}): Recurrence [${effectiveRecurrence.displayName}, interval=${effectiveInterval}$unitSuffix] rescheduled for trigger timestamp: $nextTrigger (execution count: ${updatedTask.executionCount})",
                )
            } else {
                val finalStatus = if (pendingConfirmation) {
                    TaskStatus.PENDING_CONFIRMATION
                } else if (success) {
                    TaskStatus.DONE
                } else {
                    TaskStatus.FAILED
                }
                repository.updateTaskStatus(
                    id = task.id,
                    status = finalStatus,
                    lastExecuted = now,
                )
                val updatedTask = freshTask.copy(
                    executionCount = freshTask.executionCount + 1,
                    lastExecutedMillis = now,
                    status = finalStatus,
                )
                repository.updateTask(updatedTask)
                AppFileLogger.action(
                    context,
                    TAG,
                    "Task #${task.id} (${task.title}) marked as ${finalStatus.name}",
                )
                // Guarantee complete auto-deregistration of all alarms for this completed/failed task
                AlarmScheduler(context).cancelTask(task.id, updatedTask)
            }
        }
    }

    /**
     * What it is:
     * Execution routine for [queue].
     *
     * What it does:
     * Coordinates queue operations conforming to application constraints.
     *
     * Linked with:
     * - Architecture: [SequentialTaskEngine], [queue]
     */
    fun queue(taskId: Long): Boolean = enqueue(taskId)

    /**
     * What it is:
     * Submits a scheduled task ID into the sequential dispatch pipeline queue.
     *
     * What it does:
     * Tasks are queued into an asynchronous coroutine channel and executed one-by-one. If the task is already being processed or enqueued, duplicate submissions are rejected.
     *
     * Linked with:
     * - Architecture: [SequentialTaskEngine], [enqueue]
     * @param taskId Unique identifier of the task.
     * @return `true` if enqueued successfully; `false` if ID <= 0 or already queued.
     */
    fun enqueue(taskId: Long): Boolean {
        if (taskId <= 0L) return false
        if (!activeTaskIds.add(taskId)) {
            AppFileLogger.w(appContext, TAG, "Task #$taskId is already in the execution pipeline. Ignoring duplicate enqueue.")
            return false
        }
        val sent = taskChannel.trySend(taskId).isSuccess
        if (sent) {
            AppFileLogger.action(appContext, TAG, "Enqueued Task #$taskId into Channel pipeline.")
        } else {
            activeTaskIds.remove(taskId)
            AppFileLogger.e(appContext, TAG, "Failed to offer Task #$taskId to Channel.")
        }
        return sent
    }

    /**
     * What it is:
     * Top-level safe execution handler routing tasks to appropriate channel engines (SMS, WhatsApp, Calls, Reminders, Call Simulations, Sound Modes) with circuit-breaker protection.
     *
     * What it does:
     * Executes [executeTaskSafely] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [SequentialTaskEngine], [executeTaskSafely]
     * @param repository The central application repository.
     * @param task The scheduled task to execute.
     * @return `true` if task completed successfully; `false` on failure.
     */
    suspend fun executeTaskSafely(repository: AppRepository, task: ScheduledTask): Boolean {
        val fresh = repository.getTaskById(task.id)
        if (fresh == null || fresh.status == TaskStatus.CANCELLED) {
            AppFileLogger.w(appContext, TAG, "Task #${task.id} is cancelled or deleted in DB. Refusing execution.")
            return false
        }

        AppFileLogger.action(appContext, TAG, "Starting execution for Task #${task.id} (${task.title}) on [${task.channel.displayName}]")

        // Bank-safe window: a protected/banking app in the foreground suppresses this dispatch.
        // The task waits (state and journal untouched) and runs once that app is closed.
        awaitProtectedWindowClearance(task)

        // Universal Popup & Notification Dismissal: Whenever ANY task activates, immediately clear
        // all on-screen popups, floating heads-up notifications (HUNs), caller ID overlays (Truecaller etc.), and dialogs
        TaskAccessibilityService.getInstance()?.let { service ->
            try {
                com.jmd.superapp.whatsapp.PopupShield.clearOverlaysAndNotificationShade(service)
            } catch (t: Throwable) {
                Log.w(TAG, "Task activation popup shield error: ${t.message}")
            }
        }

        if (!TaskSafetyManager.canExecute(task.channel)) {
            AppFileLogger.w(appContext, TAG, "TaskSafetyManager refused execution for ${task.channel.displayName} (circuit breaker tripped).")
            return false
        }

        val privacyMgr = com.jmd.superapp.core.PrivacyManager.getInstance(appContext)
        val targetPkg = when (task.channel) {
            ChannelType.WHATSAPP, ChannelType.WHATSAPP_GROUP, ChannelType.WHATSAPP_STATUS, ChannelType.WHATSAPP_CALL -> "com.whatsapp"
            ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_BUSINESS_GROUP, ChannelType.WHATSAPP_BUSINESS_STATUS, ChannelType.WHATSAPP_BUSINESS_CALL -> "com.whatsapp.w4b"
            else -> null
        }
        if (targetPkg != null && privacyMgr.isTaskBlockedForPackage(targetPkg)) {
            AppFileLogger.w(appContext, TAG, "Task #${task.id} (${task.channel.displayName}) aborted: Target package $targetPkg is excluded by Privacy & App Visibility Policy.")
            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = task.channel.displayName,
                    recipient = task.recipients.joinToString(),
                    messageSnippet = "[BLOCKED BY PRIVACY POLICY]",
                    status = TaskStatus.FAILED.name,
                    details = "Execution blocked: Package $targetPkg is excluded by Privacy Policy"
                )
            )
            return false
        }

        if (!com.jmd.superapp.core.ProtectedApplicationManager.isTaskTargetAuthorized(task)) {
            AppFileLogger.w(appContext, TAG, "Task #${task.id} (${task.channel.displayName}) aborted: Channel target is not authorized under Protected Application Policy.")
            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = task.channel.displayName,
                    recipient = task.recipients.joinToString(),
                    messageSnippet = "[BLOCKED BY PROTECTED APP POLICY]",
                    status = TaskStatus.FAILED.name,
                    details = "Target channel ${task.channel.displayName} refused by ProtectedApplicationManager"
                )
            )
            return false
        }

        // 0. Pre-flight Idle Check: Only trigger and complete task when idle!
        if (!TaskSafetyManager.isDeviceIdle(appContext)) {
            val idleSince = (System.currentTimeMillis() - TaskSafetyManager.lastHumanInteractionTimestamp.get()) / 1000
            val msg = "Postponed Task #${task.id} (${task.title}) by 30s: Device is in active human use (${idleSince}s since last interaction). Will execute once device becomes idle."
            AppFileLogger.action(appContext, TAG, msg)
            TaskSafetyManager.postponeTask(appContext, task.id, reason = msg)
            return false
        }

        // 0b. Pre-flight Telephony Mutual Gate: If a cellular call is active, hold non-call UI tasks to prevent clashes
        if (task.channel != ChannelType.CALL && task.channel != ChannelType.SMS &&
            com.jmd.superapp.telephony.TelephonyCallEngine.isCallActive(appContext)) {
            val msg = "Postponed Task #${task.id} (${task.title}) by 30s: Active cellular call in progress. Will execute once call concludes."
            AppFileLogger.action(appContext, TAG, msg)
            TaskSafetyManager.postponeTask(appContext, task.id, reason = msg)
            return false
        }

        // Exactly-once guard: one durable claim per scheduled occurrence. Duplicate alarms, queue
        // replays after a restart and concurrent triggers are suppressed here.
        val dispatchOccurrence = DispatchJournal.occurrenceKey(task.id, task.triggerTimeMillis)
        val isManualRun = (task.status == TaskStatus.PENDING || task.status == TaskStatus.RUNNING)
        if (!DispatchJournal.claim(appContext, task.id, dispatchOccurrence, isManual = isManualRun)) {
            AppFileLogger.w(
                appContext,
                TAG,
                "Task #${task.id} occurrence ${task.triggerTimeMillis} already dispatched (journal). Suppressing duplicate execution.",
            )
            return false
        }

        val prefs = PreferenceManager(appContext)
        repository.updateTaskStatus(task.id, TaskStatus.RUNNING)

        val success = when (task.channel) {
            ChannelType.WHATSAPP,
            ChannelType.WHATSAPP_BUSINESS,
            ChannelType.WHATSAPP_GROUP,
            ChannelType.WHATSAPP_BUSINESS_GROUP,
            ChannelType.WHATSAPP_STATUS,
            ChannelType.WHATSAPP_BUSINESS_STATUS,
            -> {
                uiTaskMutex.withLock {
                    privacyMgr.prepareAccessibilityForTask()
                    try {
                        executeWhatsAppTaskWithRetries(repository, task)
                    } finally {
                        if (prefs.isAutoLockAfterSendEnabled) {
                            executeDeviceLockPostcondition(appContext, task.title)
                        }
                        privacyMgr.releaseAccessibilityAfterTask()
                    }
                }
            }
            ChannelType.SMS -> {
                smsMutex.withLock {
                    executeSmsTask(repository, task)
                }
            }
            ChannelType.CALL -> {
                executeCallTask(repository, task)
            }
            ChannelType.WHATSAPP_CALL,
            ChannelType.WHATSAPP_BUSINESS_CALL,
            -> {
                uiTaskMutex.withLock {
                    privacyMgr.prepareAccessibilityForTask()
                    try {
                        executeWhatsAppCallTask(repository, task)
                    } finally {
                        if (prefs.isAutoLockAfterSendEnabled) {
                            executeDeviceLockPostcondition(appContext, task.title)
                        }
                        privacyMgr.releaseAccessibilityAfterTask()
                    }
                }
            }
            ChannelType.CALL_SIMULATION -> {
                executeCallSimulationTask(repository, task)
            }
        }

        val freshAfter = repository.getTaskById(task.id)
        val isCancelledOrInterrupted = freshAfter?.status == TaskStatus.CANCELLED || TaskSafetyManager.isInterruptedByHuman.get()

        if (success) {
            TaskSafetyManager.recordSuccess(task.channel)
        } else if (!isCancelledOrInterrupted) {
            TaskSafetyManager.recordFailure(task.channel, appContext)
        }

        val isUiTaskChannel = when (task.channel) {
            ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_GROUP, ChannelType.WHATSAPP_BUSINESS_GROUP,
            ChannelType.WHATSAPP_STATUS, ChannelType.WHATSAPP_BUSINESS_STATUS,
            ChannelType.WHATSAPP_CALL, ChannelType.WHATSAPP_BUSINESS_CALL -> true
            else -> false
        }
        if (!isUiTaskChannel && task.channel != ChannelType.CALL_SIMULATION && prefs.isAutoLockAfterSendEnabled) {
            executeDeviceLockPostcondition(appContext, task.title)
        }
        if (success) {
            DispatchJournal.complete(appContext, task.id, dispatchOccurrence)
        } else {
            DispatchJournal.fail(appContext, task.id, dispatchOccurrence)
        }

        return success
    }
    /**
     * What it is:
     * Execution routine for [ensureDeviceAwakeAndUnlocked].
     *
     * What it does:
     * Coordinates ensureDeviceAwakeAndUnlocked operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SequentialTaskEngine]
     */

    suspend fun ensureDeviceAwakeAndUnlocked(
        task: ScheduledTask,
        targetRecipient: String = ""
    ): Boolean = ensureDeviceAwakeAndUnlocked(task.id, task.channel, targetRecipient)

    /**
     * What it is:
     * Verifies whether the screen is interactive and keyguard is unlocked.
     *
     * What it does:
     * If the screen is off or keyguard is locked, turns on the display and initiates keyguard dismiss or scheduled PIN entry via [TaskLauncherActivity].
     *
     * Linked with:
     * - Architecture: [SequentialTaskEngine], [ensureDeviceAwakeAndUnlocked]
     */
    suspend fun ensureDeviceAwakeAndUnlocked(
        taskId: Long,
        channel: ChannelType,
        targetRecipient: String = ""
    ): Boolean {
        val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenOn = pm?.isInteractive == true
        val isLocked = (km?.isKeyguardLocked == true) || (km?.isDeviceLocked == true) || (!isScreenOn)

        if (!isLocked) return true

        AppFileLogger.action(
            appContext,
            TAG,
            "Device is locked/screen off before executing ${channel.displayName} for Task #$taskId. Initiating screen wake & keyguard unlock..."
        )

        try {
            @Suppress("DEPRECATION")
            val wakeLock = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Super:ExecutionWakeLock",
            )
            wakeLock?.acquire(45000L)
        } catch (e: Exception) {
            Log.w(TAG, "ExecutionWakeLock acquire error: ${e.message}")
        }

        val prefs = PreferenceManager(appContext)
        val launchIntent = Intent(appContext, TaskLauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TaskLauncherActivity.EXTRA_TASK_ID, taskId)
            putExtra(TaskLauncherActivity.EXTRA_CHANNEL, channel.name)
            putExtra(TaskLauncherActivity.EXTRA_RECIPIENT, targetRecipient)
            putExtra(TaskLauncherActivity.EXTRA_WAKE_ONLY, true)
        }

        val pin = if (prefs.screenAssistType != "SWIPE") prefs.screenAssistPin else ""
        if (pin.isNotBlank() && km?.isKeyguardLocked == true) {
            TaskAccessibilityService.armScreenAssist(
                pin = pin,
                shouldRelockAfterSend = prefs.isAutoLockAfterSendEnabled
            )
            appContext.startActivity(launchIntent)

            var waited = 0L
            while (waited < 10000L) {
                delay(500.milliseconds)
                waited += 500L
                if (!km.isKeyguardLocked) {
                    AppFileLogger.action(
                        appContext,
                        TAG,
                        "Device unlocked successfully before executing ${channel.displayName} for Task #$taskId."
                    )
                    TaskAccessibilityService.disarmScreenAssist()
                    break
                }
            }
        } else {
            appContext.startActivity(launchIntent)
            delay(1200.milliseconds)
        }
        return true
    }
    /**
     * What it is:
     * Execution routine for [executeWhatsAppTaskWithRetries].
     *
     * What it does:
     * Coordinates executeWhatsAppTaskWithRetries operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SequentialTaskEngine]
     */

    private suspend fun executeWhatsAppTaskWithRetries(
        repository: AppRepository,
        task: ScheduledTask,
        maxRetries: Int = 1,
    ): Boolean {
        var attempts = 0
        var success = false
        val occurrenceId = "${task.triggerTimeMillis}_${task.executionCount}"
        val successfulRecipients = mutableSetOf<String>()
        val effectiveMaxRetries = if (task.recurrence == RecurrenceType.REPEAT_SECONDS) 0 else maxRetries

        if (!AccessibilityHealthManager.isServiceOperational()) {
            val ready = AccessibilityHealthManager.awaitServiceReadiness(appContext, 5000L)
            if (!ready) {
                val err = "Accessibility Service is not operational. Cannot execute WhatsApp task #${task.id}."
                AppFileLogger.e(appContext, TAG, err)
                repository.insertLog(
                    ExecutionLog(
                        taskId = task.id,
                        channel = task.channel.displayName,
                        recipient = task.recipients.firstOrNull() ?: "Unknown",
                        messageSnippet = "Accessibility service unavailable",
                        status = "FAILED",
                        details = err,
                    ),
                )
                completeOrRescheduleTask(appContext, repository, task, success = false)
                return false
            }
        }

        val requiredPackage = WhatsAppPackageHelper.getPackageForChannel(appContext, task.channel)
        if (requiredPackage == null) {
            val missingPkgName = when (task.channel) {
                ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_BUSINESS_GROUP, ChannelType.WHATSAPP_BUSINESS_STATUS, ChannelType.WHATSAPP_BUSINESS_CALL -> "WhatsApp Business (com.whatsapp.w4b)"
                else -> "WhatsApp (com.whatsapp)"
            }
            AppFileLogger.w(appContext, TAG, "Cannot execute Task #${task.id}: $missingPkgName is not installed on this device.")
            val targets = if (task.channel == ChannelType.WHATSAPP_STATUS || task.channel == ChannelType.WHATSAPP_BUSINESS_STATUS) {
                listOf(task.recipients.firstOrNull() ?: "STATUS")
            } else {
                task.recipients
            }
            for (recipient in targets) {
                repository.insertLog(
                    ExecutionLog(
                        taskId = task.id,
                        channel = task.channel.displayName,
                        recipient = recipient,
                        messageSnippet = MessageTemplateResolver.resolve(task.messageText).take(160),
                        status = "FAILED",
                        details = "$missingPkgName is not installed on this device",
                    ),
                )
            }
            completeOrRescheduleTask(appContext, repository, task, success = false)
            return false
        }

        // Seed with previously recorded durable deliveries from database for this specific occurrence
        for (recipient in task.recipients) {
            if (repository.isRecipientDeliveredForTask(task.id, recipient, occurrenceId)) {
                successfulRecipients.add(recipient)
            }
        }

        while ((attempts <= effectiveMaxRetries) && !success) {
            attempts++
            try {
                // Cooperative cancellation check before attempt
                val currentTaskState = repository.getTaskById(task.id)
                if ((currentTaskState == null) || (currentTaskState.status == TaskStatus.CANCELLED)) {
                    AppFileLogger.w(appContext, TAG, "Task #${task.id} was cancelled before or during WhatsApp attempt. Aborting.")
                    return false
                }

                // Interruption and Idle check:
                if (TaskSafetyManager.isInterruptedByHuman.get() || !TaskSafetyManager.isDeviceIdle(appContext)) {
                    val msg = "Task #${task.id} (${task.title}) was interrupted by human interaction. Auto-postponing task to complete when idle."
                    AppFileLogger.action(appContext, TAG, msg)
                    TaskSafetyManager.isInterruptedByHuman.set(false)
                    TaskSafetyManager.postponeTask(appContext, task.id, reason = msg)
                    return false
                }

                PrivacyManager.getInstance(appContext).prepareAccessibilityForTask()
                AccessibilityHealthManager.ensureServiceAlive(appContext)
                if (!AccessibilityHealthManager.isServiceOperational()) {
                    val ready = AccessibilityHealthManager.awaitServiceReadiness(appContext, 3000L)
                    if (!ready) {
                        AppFileLogger.w(appContext, TAG, "Accessibility Service is not operational for Task #${task.id}. Retrying after delay...")
                        if (attempts <= effectiveMaxRetries) {
                            delay(2000.milliseconds)
                            continue
                        }
                    }
                }
                ensureDeviceAwakeAndUnlocked(task, task.recipients.firstOrNull() ?: "")
                delay(300.milliseconds)

                val waSender = WhatsAppSender(appContext)

                val shouldRelockPref = PreferenceManager(appContext).isAutoLockAfterSendEnabled
                if (task.channel == ChannelType.WHATSAPP_STATUS || task.channel == ChannelType.WHATSAPP_BUSINESS_STATUS) {
                    TaskAccessibilityService.clearPending()
                    TaskAccessibilityService.markDispatchPending()
                    val statusTarget = task.recipients.firstOrNull() ?: "STATUS"
                    val resolvedStatusText = MessageTemplateResolver.resolve(
                        template = task.messageText,
                        targetTimestamp = System.currentTimeMillis()
                    )
                    val dispatched = waSender.send(
                        taskId = task.id,
                        channel = task.channel,
                        recipientPhone = statusTarget,
                        messageText = resolvedStatusText,
                        shouldRelock = shouldRelockPref,
                        mediaUri = task.mediaUri,
                    )
                    if (dispatched) {
                        success = waitForWhatsAppCompletion()
                        if (success) {
                            repository.recordRecipientDelivery(task.id, statusTarget, task.channel.name, "SUCCESS", occurrenceId)
                        }
                    }
                } else {
                    val pendingRecipients = task.recipients.filter { it !in successfulRecipients }
                    if (pendingRecipients.isEmpty()) {
                        success = true
                    } else {
                        var currentBatchSuccess = true
                        val totalRecipients = task.recipients.size
                        for (i in task.recipients.indices) {
                            // In-flight cooperative cancellation check between recipients
                            val taskCheck = repository.getTaskById(task.id)
                            if ((taskCheck == null) || (taskCheck.status == TaskStatus.CANCELLED)) {
                                AppFileLogger.w(appContext, TAG, "Task #${task.id} cancelled during multi-recipient loop. Stopping remaining dispatches.")
                                return false
                            }

                            if (TaskSafetyManager.isInterruptedByHuman.get() || !TaskSafetyManager.isDeviceIdle(appContext)) {
                                val msg = "Task #${task.id} (${task.title}) interrupted by human interaction between recipients. Auto-postponing task to complete when idle."
                                AppFileLogger.action(appContext, TAG, msg)
                                TaskSafetyManager.isInterruptedByHuman.set(false)
                                TaskSafetyManager.postponeTask(appContext, task.id, reason = msg)
                                return false
                            }

                            val phone = task.recipients[i]
                            if (phone in successfulRecipients) continue

                            val recipientName = task.recipientNames.getOrNull(i).orEmpty()
                            val personalizedMsg = personalize(task.messageText, phone, recipientName)
                            val isLast = (i + 1) == totalRecipients

                            // Accessibility pre-flight per recipient: verify service alive before launching intent
                            if (!AccessibilityHealthManager.isServiceOperational() || TaskAccessibilityService.getInstance() == null) {
                                AppFileLogger.w(appContext, TAG, "Accessibility Service inactive before sending to $phone. Attempting recovery...")
                                AccessibilityHealthManager.ensureServiceAlive(appContext)
                                val ready = AccessibilityHealthManager.awaitServiceReadiness(appContext, 2500L)
                                if (!ready || TaskAccessibilityService.getInstance() == null) {
                                    AppFileLogger.e(appContext, TAG, "Accessibility Service could not be re-bound. Aborting remaining dispatches for Task #${task.id}.")
                                    currentBatchSuccess = false
                                    break
                                }
                            }

                            TaskAccessibilityService.clearPending()
                            TaskAccessibilityService.markDispatchPending()

                            val dispatched = waSender.send(
                                taskId = task.id,
                                channel = task.channel,
                                recipientPhone = phone,
                                messageText = personalizedMsg,
                                shouldRelock = isLast && shouldRelockPref,
                                mediaUri = task.mediaUri,
                            )

                            if (dispatched && waitForWhatsAppCompletion()) {
                                successfulRecipients.add(phone)
                                repository.recordRecipientDelivery(task.id, phone, task.channel.name, "SUCCESS", occurrenceId)
                                if (!isLast) {
                                    delay(1200.milliseconds)
                                }
                            } else {
                                currentBatchSuccess = false
                                AppFileLogger.w(appContext, TAG, "Failed sending WhatsApp message to $phone (Attempt $attempts)")
                                if (!AccessibilityHealthManager.isServiceOperational() || TaskAccessibilityService.getInstance() == null) {
                                    AppFileLogger.e(appContext, TAG, "Accessibility Service disconnected during dispatch to $phone. Halting batch.")
                                    break
                                }
                            }
                        }
                        success = currentBatchSuccess && (successfulRecipients.size == task.recipients.size)
                    }
                }

                if (!success && (attempts <= effectiveMaxRetries)) {
                    AppFileLogger.action(appContext, TAG, "Retrying WhatsApp task #${task.id} in 3 seconds (Attempt $attempts of $effectiveMaxRetries)...")
                    delay(3000.milliseconds)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Error executing WhatsApp task #${task.id}", e)
                AppFileLogger.e(appContext, TAG, "WhatsApp execution exception: ${e.message}", e)
                if (attempts <= effectiveMaxRetries) delay(2000.milliseconds)
            }
        }

        if (!success) {
            val failedRecipients = if (task.channel == ChannelType.WHATSAPP_STATUS || task.channel == ChannelType.WHATSAPP_BUSINESS_STATUS) {
                val statusTarget = task.recipients.firstOrNull() ?: "STATUS"
                if (statusTarget !in successfulRecipients) listOf(statusTarget) else emptyList()
            } else {
                task.recipients.filter { it !in successfulRecipients }
            }
            for (recipient in failedRecipients) {
                val snippet = MessageTemplateResolver.resolve(task.messageText).take(160)
                repository.insertLog(
                    ExecutionLog(
                        taskId = task.id,
                        channel = task.channel.displayName,
                        recipient = recipient,
                        messageSnippet = snippet,
                        status = "FAILED",
                        details = "WhatsApp dispatch timed out after $attempts attempt(s) or Accessibility Service not active.",
                    )
                )
            }
        }

        completeOrRescheduleTask(appContext, repository, task, success)
        return success
    }
    /**
     * What it is:
     * Execution routine for [waitForWhatsAppCompletion].
     *
     * What it does:
     * Coordinates waitForWhatsAppCompletion operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SequentialTaskEngine]
     */

    private suspend fun waitForWhatsAppCompletion(): Boolean {
        var waitedMs = 0L
        while (
            (TaskAccessibilityService.lastSendResult == TaskAccessibilityService.RESULT_PENDING) &&
                (waitedMs < WHATSAPP_SEND_TIMEOUT_MS)
        ) {
            // Early exit if the Accessibility Service becomes unbound by OS power management
            if (!AccessibilityHealthManager.isServiceOperational() || TaskAccessibilityService.getInstance() == null) {
                AccessibilityHealthManager.ensureServiceAlive(appContext)
                val recovered = AccessibilityHealthManager.awaitServiceReadiness(appContext, 2000L)
                if (!recovered || TaskAccessibilityService.getInstance() == null) {
                    AppFileLogger.w(appContext, TAG, "Accessibility Service unbound mid-wait after ${waitedMs}ms. Aborting send wait early.")
                    return false
                }
            }
            delay(500.milliseconds)
            waitedMs += 500L
        }
        return TaskAccessibilityService.lastSendResult == TaskAccessibilityService.RESULT_SUCCESS
    }
    /**
     * What it is:
     * Execution routine for [executeSmsTask].
     *
     * What it does:
     * Coordinates executeSmsTask operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SequentialTaskEngine]
     */

    private suspend fun executeSmsTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val smsSender = SmsSender(appContext)
        var allSuccess = true
        var hasPendingConfirmation = false
        val occurrenceId = "${task.triggerTimeMillis}_${task.executionCount}"

        for (i in task.recipients.indices) {
            // Cooperative cancellation check between SMS recipients
            val currentTaskState = repository.getTaskById(task.id)
            if ((currentTaskState == null) || (currentTaskState.status == TaskStatus.CANCELLED)) {
                AppFileLogger.w(appContext, TAG, "SMS task #${task.id} cancelled during recipient loop. Aborting.")
                return false
            }

            if (TaskSafetyManager.isInterruptedByHuman.get() || !TaskSafetyManager.isDeviceIdle(appContext)) {
                val msg = "SMS Task #${task.id} (${task.title}) interrupted by human interaction. Auto-postponing task to complete when idle."
                AppFileLogger.action(appContext, TAG, msg)
                TaskSafetyManager.isInterruptedByHuman.set(false)
                TaskSafetyManager.postponeTask(appContext, task.id, reason = msg)
                return false
            }

            val phone = task.recipients[i]
            if (repository.isRecipientDeliveredForTask(task.id, phone, occurrenceId)) {
                AppFileLogger.i(appContext, TAG, "SMS recipient $phone already delivered for task #${task.id} (occurrence $occurrenceId). Skipping.")
                continue
            }

            val recipientName = task.recipientNames.getOrNull(i).orEmpty()
            val personalizedMsg = personalize(task.messageText, phone, recipientName)

            val shouldEnforceQuota = PreferenceManager(appContext).isSmsQuotaLimitEnabled
            if (shouldEnforceQuota) {
                val slotQuota = SmsQuotaManager.getSlotQuotaStatus(appContext, task.simSlot)
                if (slotQuota.isLimitReached) {
                    AppFileLogger.w(appContext, TAG, "Task #${task.id} paused on per-SIM quota: ${slotQuota.details}.")
                    allSuccess = false
                    break
                }
            }
            val (sendSuccess, sendSegments, sendReason, sendDeliveryStatus) =
                smsSender.sendSmsWithResult(
                    phone,
                    personalizedMsg,
                    task.simSlot,
                    enforceQuota = shouldEnforceQuota,
                    taskId = task.id,
                    occurrenceId = occurrenceId
                )
            val isQuota = sendReason.contains("limit", ignoreCase = true) || sendReason.contains("quota", ignoreCase = true)
            val logStatus = when (sendDeliveryStatus) {
                SmsSender.DeliveryStatus.CONFIRMED -> "SUCCESS"
                SmsSender.DeliveryStatus.PENDING_TIMEOUT -> "DISPATCHED"
                SmsSender.DeliveryStatus.PARTIAL_DISPATCH -> "PARTIAL"
                SmsSender.DeliveryStatus.FAILED -> if (isQuota) "QUEUED" else "FAILED"
            }
            val logDetails = if (sendSuccess) "Dispatched to cellular modem via SIM slot ${task.simSlot}, parts: $sendSegments" else sendReason

            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = "SMS",
                    recipient = phone,
                    messageSnippet = personalizedMsg.take(160),
                    status = logStatus,
                    details = logDetails,
                    segments = sendSegments,
                ),
            )

            if (sendSuccess) {
                SmsQuotaManager.commitSmsQuotaReservation(sendSegments)
                SmsQuotaManager.recordSegmentsForSlot(appContext, task.simSlot, sendSegments)
                repository.recordRecipientDelivery(task.id, phone, "SMS", "SUCCESS", occurrenceId)
            } else if (sendDeliveryStatus == SmsSender.DeliveryStatus.PENDING_TIMEOUT) {
                // Modem accepted message content into cellular transmit buffer but carrier radio ACK timed out.
                // Record DISPATCHED delivery status for this occurrence so duplicate send attempts are blocked.
                hasPendingConfirmation = true
                allSuccess = false
                repository.recordRecipientDelivery(task.id, phone, "SMS", "DISPATCHED", occurrenceId)
            } else if (sendDeliveryStatus == SmsSender.DeliveryStatus.PARTIAL_DISPATCH) {
                hasPendingConfirmation = true
                allSuccess = false
                repository.recordRecipientDelivery(task.id, phone, "SMS", "DISPATCHED", occurrenceId)
            } else {
                allSuccess = false
            }

            if (isQuota) {
                AppFileLogger.w(appContext, TAG, "Task #${task.id} paused due to SMS quota: $sendReason")
                break
            }
            delay(100.milliseconds)
        }

        completeOrRescheduleTask(
            context = appContext,
            repository = repository,
            task = task,
            success = allSuccess,
            pendingConfirmation = hasPendingConfirmation,
        )
        if (PreferenceManager(appContext).isAutoLockAfterSendEnabled) {
            executeDeviceLockPostcondition(appContext, task.title)
        }
        return allSuccess
    }

    /**
     * What it is:
     * Executes post-task device lock if enabled in preferences.
     *
     * What it does:
     * Minimizes to home and executes Power button auto-lock via [TaskAccessibilityService.lockScreenNow] (using Android Pie+ GLOBAL_ACTION_LOCK_SCREEN) so biometrics and fingerprint are 100% preserved. Never calls DevicePolicyManager.lockNow().
     *
     * Linked with:
     * - Architecture: [SequentialTaskEngine], [executeDeviceLockPostcondition]
     */
    private suspend fun executeDeviceLockPostcondition(context: Context, taskTitle: String): Boolean {
        val prefs = PreferenceManager(context)
        if (!prefs.isAutoLockAfterSendEnabled) {
            AppFileLogger.i(context, TAG, "Device auto-lock disabled in settings; skipping postcondition for '$taskTitle'")
            return true
        }

        AppFileLogger.action(context, TAG, "Executing device auto-lock postcondition for '$taskTitle' (Power button simulation, Fingerprint safe)...")
        // 1. Return to home launcher first so private conversation/dialer is cleared
        TaskAccessibilityService.minimizeToHome(context)
        delay(400.milliseconds)

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == false) {
            AppFileLogger.action(context, TAG, "Screen is already off; auto-lock postcondition satisfied.")
            return true
        }

        // 2. Lock screen mimicking hardware Power button via Accessibility Service global action
        val locked = TaskAccessibilityService.lockScreenNow()
        delay(400.milliseconds)

        val screenIsOn = pm?.isInteractive != false
        val success = !screenIsOn || locked
        if (success) {
            AppFileLogger.action(context, TAG, "Device auto-lock postcondition succeeded for '$taskTitle' (screenInteractive=$screenIsOn, Biometrics Safe)")
        } else {
            AppFileLogger.w(context, TAG, "Device auto-lock postcondition finished for '$taskTitle'; home launcher minimized.")
        }
        return success
    }
    /**
     * What it is:
     * Execution routine for [executeCallTask].
     *
     * What it does:
     * Coordinates executeCallTask operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SequentialTaskEngine]
     */

    private suspend fun executeCallTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val baseRecipients = task.recipients.filter { it.isNotBlank() }
        if (baseRecipients.isEmpty()) {
            AppFileLogger.e(appContext, TAG, "Task #${task.id}: No recipient phone number provided for call")
            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = "Phone Call",
                    recipient = "Unknown",
                    messageSnippet = "No phone number specified",
                    status = "FAILED",
                ),
            )
            completeOrRescheduleTask(appContext, repository, task, success = false)
            return false
        }

        // Apply call sequence mode: GIVEN preserves order, RANDOM shuffles deterministically
        val rawRecipients = if (task.callSequenceMode.equals("RANDOM", ignoreCase = true)) {
            val randomSeed = task.id xor task.triggerTimeMillis
            baseRecipients.shuffled(java.util.Random(randomSeed))
        } else {
            baseRecipients
        }

        val hasCallPerm = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED

        var allCallsSuccess = true
        var anyDirectCall = false
        val interCallDelayMs = maxOf(1000L, task.delayBetweenCallsSeconds * 1000L)

        for ((index, rawRecipient) in rawRecipients.withIndex()) {
            val taskState = repository.getTaskById(task.id)
            if (taskState == null || taskState.status == TaskStatus.CANCELLED) {
                AppFileLogger.w(appContext, TAG, "Task #${task.id} cancelled during multi-call loop. Halting remaining calls.")
                return false
            }

            if (TaskSafetyManager.isInterruptedByHuman.get() || !TaskSafetyManager.isDeviceIdle(appContext)) {
                val msg = "Phone Call Task #${task.id} (${task.title}) interrupted by human interaction. Auto-postponing task to complete when idle."
                AppFileLogger.action(appContext, TAG, msg)
                TaskSafetyManager.isInterruptedByHuman.set(false)
                TaskSafetyManager.postponeTask(appContext, task.id, reason = msg)
                return false
            }

            val candidateName = rawRecipient.removePrefix("group:").removePrefix("broadcast:").trim()
            val phoneFromRepo = repository.findPhoneForContactName(candidateName)
            val effectiveTarget = phoneFromRepo ?: candidateName
            val cleanPhone = PhoneNumberNormalizer.cleanPhoneNumber(effectiveTarget)
            if (cleanPhone.length < 3) {
                AppFileLogger.w(appContext, TAG, "Task #${task.id}: Skipping invalid phone number '$rawRecipient'")
                continue
            }

            val success = try {
                // 1. Lockscreen Wake & Keyguard Dismiss Guard
                ensureDeviceAwakeAndUnlocked(task, cleanPhone)

                val telecomManager = appContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val uri = Uri.fromParts("tel", cleanPhone, null)
                var explicitSimUnresolved = false
                val extras = android.os.Bundle().apply {
                    val hasExplicitSimSelection = (task.simSlot == 1) || (task.simSlot == 2)
                    if ((telecomManager != null) && (hasExplicitSimSelection)) {
                        val targetPhysicalSlot = task.simSlot - 1
                        var matchedSubId: Int? = null
                        try {
                            val subManager = appContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                            val activeSubs = subManager?.activeSubscriptionInfoList
                            val targetSub = activeSubs?.find { it.simSlotIndex == targetPhysicalSlot }
                            matchedSubId = targetSub?.subscriptionId
                            val accounts = telecomManager.callCapablePhoneAccounts

                            val matchedAccount = accounts.find { handle ->
                                if (targetSub != null) {
                                    val handleId = handle.id ?: ""
                                    (handleId == targetSub.subscriptionId.toString()) ||
                                            ((targetSub.iccId != null) && (handleId.contains(targetSub.iccId)))
                                } else {
                                    false
                                }
                            }

                            if (matchedAccount != null) {
                                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, matchedAccount)
                            } else {
                                val candidate = accounts.getOrNull(targetPhysicalSlot)
                                if ((targetSub != null) && (candidate != null)) {
                                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, candidate)
                                } else {
                                    val fallbackAccount = accounts.firstOrNull()
                                    if (fallbackAccount != null) {
                                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, fallbackAccount)
                                        AppFileLogger.w(appContext, TAG, "Selected SIM ${task.simSlot} unavailable; falling back to default active SIM account")
                                    } else {
                                        explicitSimUnresolved = true
                                        Log.w(TAG, "Selected SIM ${task.simSlot} (slotIndex=$targetPhysicalSlot) could not be resolved and no fallback accounts exist")
                                    }
                                }
                            }
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Missing permission to query telecom accounts: ${e.message}")
                        } catch (e: Throwable) {
                            Log.w(TAG, "Exception resolving call-capable account for SIM ${task.simSlot}: ${e.message}")
                        }
                        com.jmd.superapp.core.OemBrandHelper.populateTelecomSimExtras(this, targetPhysicalSlot, matchedSubId)
                    }
                }

                if (explicitSimUnresolved) {
                    AppFileLogger.w(appContext, TAG, "Explicit SIM ${task.simSlot} could not be resolved; routing call via default telephony...")
                    extras.clear()
                }

                com.jmd.superapp.telephony.TelephonyCallEngine.trackDispatchedCall(cleanPhone)
                var callInitiated = false
                if (hasCallPerm && telecomManager != null) {
                    try {
                        telecomManager.placeCall(uri, extras)
                        callInitiated = true
                        AppFileLogger.action(appContext, TAG, "Placed direct telecom call to $cleanPhone via SIM ${task.simSlot}")
                    } catch (e: Exception) {
                        Log.w(TAG, "telecomManager.placeCall failed, falling back to Intent: ${e.message}")
                    }
                }

                if (!callInitiated) {
                    val callIntent = if (hasCallPerm) {
                        Intent(Intent.ACTION_CALL, uri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtras(extras)
                        }
                    } else {
                        Intent(Intent.ACTION_DIAL, uri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    }
                    val intentStarted = runCatching {
                        appContext.startActivity(callIntent)
                        true
                    }.getOrDefault(false)
                    if (intentStarted) {
                        callInitiated = true
                    }
                }

                val isDirectCall = callInitiated || hasCallPerm

                val logStatus = if (isDirectCall) "SUCCESS" else "DIALER_OPENED"
                val logSnippet = if (isDirectCall) "Placed call to $cleanPhone (${index + 1}/${rawRecipients.size}, simSlot=${task.simSlot}, duration=${task.callDurationSeconds}s, dialTimeIncluded=${task.includeDialTimeInCallDuration})" else "Opened dialer for $cleanPhone (Action Required)"
                repository.insertLog(
                    ExecutionLog(
                        taskId = task.id,
                        channel = "Phone Call",
                        recipient = cleanPhone,
                        messageSnippet = logSnippet,
                        status = logStatus,
                    ),
                )
                AppFileLogger.action(appContext, TAG, "Task #${task.id}: $logSnippet")
                if (isDirectCall) anyDirectCall = true

                // 2. Call Duration Holding and Quick Hangup Execution
                if (isDirectCall && task.callDurationSeconds > 0) {
                    val durationSec = task.callDurationSeconds
                    val telephonyManager = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    val hasPhoneStatePerm = ContextCompat.checkSelfPermission(
                        appContext,
                        android.Manifest.permission.READ_PHONE_STATE,
                    ) == PackageManager.PERMISSION_GRANTED

                    // Super to check if cellular call is still active on the device
                    val isCellularCallOngoing: () -> Boolean = {
                        val stateActive = com.jmd.superapp.compat.TelephonyManagerCompat.isCallActiveOrRinging(appContext, telephonyManager)
                        val uiActive = TaskAccessibilityService.isPhoneCallUiActive()
                        stateActive || uiActive
                    }

                    // Sliced delay polling checking master pause and early call termination
                    // Returns true if duration fully elapsed; false if stopped via master switch / task cancellation
                    val waitForCallDurationWithEarlyCheck: suspend (Long, Long, Boolean) -> Boolean = { totalMs, graceMs, allowEarlyExit ->
                        var elapsed = 0L
                        val step = 250L
                        var completedNormally = true
                        var idleConsecutiveMs = 0L
                        while (elapsed < totalMs) {
                            // After grace period, if early exit is allowed (e.g. connected call was hung up by remote party), verify continuous idle state
                            if (allowEarlyExit && elapsed >= graceMs) {
                                if (!isCellularCallOngoing()) {
                                    idleConsecutiveMs += step
                                    // Require at least 2.5s of confirmed idle to avoid carrier radio handover glitches
                                    if (idleConsecutiveMs >= 2500L) {
                                        AppFileLogger.action(appContext, TAG, "Call for $cleanPhone ended early by remote party (${elapsed / 1000}s into ${durationSec}s limit). Exiting wait loop.")
                                        completedNormally = false
                                        break
                                    }
                                } else {
                                    idleConsecutiveMs = 0L
                                }
                            }
                            val chunk = minOf(step, totalMs - elapsed)
                            delay(chunk.milliseconds)
                            elapsed += chunk
                        }
                        completedNormally
                    }

                    val durationCriteriaSatisfied = if (task.includeDialTimeInCallDuration) {
                        // Timer starts immediately when call placement initiates - hold full duration without dropping early
                        AppFileLogger.action(appContext, TAG, "Call duration limit of ${durationSec}s (including dialing time) holding for $cleanPhone...")
                        val allowEarly = durationSec > 25
                        waitForCallDurationWithEarlyCheck(durationSec * 1000L, 15000L, allowEarly)
                    } else if (telephonyManager != null && hasPhoneStatePerm) {
                        // Timer starts ONLY once the call is confirmed answered/connected (OFFHOOK)
                        AppFileLogger.action(appContext, TAG, "Awaiting call connection for $cleanPhone before starting ${durationSec}s call duration...")
                        val dialTimeoutMs = 45_000L
                        val pollIntervalMs = 500L
                        var dialElapsedMs = 0L
                        var connected = false

                        while (dialElapsedMs < dialTimeoutMs) {
                            val isCallAlive = isCellularCallOngoing()
                            // If phone call terminated/dropped while still dialing (after initial 4s placement grace)
                            if (dialElapsedMs >= 4000L && !isCallAlive) {
                                AppFileLogger.action(appContext, TAG, "Call to $cleanPhone ended before answering (busy/declined/dropped).")
                                connected = false
                                break
                            }

                            val a11yTimerActive = TaskAccessibilityService.isCallTimerVisible()
                            val isOffhook = com.jmd.superapp.compat.TelephonyManagerCompat.isCallConnected(appContext, telephonyManager)

                            // Detect connection:
                            // 1. Accessibility call timer is actively running (00:01, etc.)
                            // 2. Or offhook state sustained past typical dialing phase (> 12s)
                            if (a11yTimerActive || (isOffhook && dialElapsedMs >= 12000L)) {
                                connected = true
                                AppFileLogger.action(appContext, TAG, "Call to $cleanPhone connected (timerActive=$a11yTimerActive, elapsed=${dialElapsedMs}ms)! Starting configured ${durationSec}s call duration timer...")
                                break
                            }

                            delay(pollIntervalMs.milliseconds)
                            dialElapsedMs += pollIntervalMs
                        }

                        if (connected) {
                            waitForCallDurationWithEarlyCheck(durationSec * 1000L, 2000L, true)
                        } else {
                            AppFileLogger.action(appContext, TAG, "Call to $cleanPhone did not connect within timeout ($dialElapsedMs ms). Attempting hangup...")
                            com.jmd.superapp.telephony.TelephonyCallEngine.endCall(appContext)
                            false
                        }
                    } else {
                        // Fallback when phone state perm is absent: wait configured duration
                        waitForCallDurationWithEarlyCheck(durationSec * 1000L, 10000L, durationSec > 20)
                    }

                    // Terminate active call once duration criteria has elapsed
                    if (durationCriteriaSatisfied) {
                        AppFileLogger.action(appContext, TAG, "Configured call duration (${durationSec}s) elapsed for $cleanPhone. Hanging up call now...")
                        val hungUp = com.jmd.superapp.telephony.TelephonyCallEngine.endCall(appContext)
                        AppFileLogger.action(appContext, TAG, "Hangup request for $cleanPhone: result=$hungUp")

                        try {
                            com.jmd.superapp.core.SuperNotificationManager.postTaskNotification(
                                appContext,
                                (task.id + index + 50000).toInt(),
                                "Call Ended",
                                "Scheduled call with $cleanPhone concluded (${durationSec}s)."
                            )
                        } catch (e: Exception) {
                            Log.d(TAG, "Call duration notification error: ${e.message}")
                        }
                    } else {
                        AppFileLogger.action(appContext, TAG, "Call to $cleanPhone ended naturally or did not trigger auto-hangup.")
                    }

                    // Allow 2 seconds for audio/telephony channel to disconnect cleanly
                    delay(2000L.milliseconds)
                } else if (isDirectCall && index < (rawRecipients.size - 1)) {
                    // Manual/indefinite duration mode: wait for current call to hang up before dialing next number
                    val telephonyManager = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    val hasPhoneStatePerm = ContextCompat.checkSelfPermission(
                        appContext,
                        android.Manifest.permission.READ_PHONE_STATE,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (telephonyManager != null && hasPhoneStatePerm) {
                        AppFileLogger.action(appContext, TAG, "Awaiting termination of call for $cleanPhone before dialing next recipient...")
                        val maxWaitMs = 120_000L
                        val pollIntervalMs = 800L
                        var elapsedMs = 0L
                        var sawOffHook = false
                        delay(3000L.milliseconds)
                        elapsedMs += 3000L
                        while (elapsedMs < maxWaitMs) {
                            val state = com.jmd.superapp.compat.TelephonyManagerCompat.getCallState(appContext, telephonyManager)
                            if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                                sawOffHook = true
                            } else if (state == TelephonyManager.CALL_STATE_IDLE && (sawOffHook || elapsedMs >= 15000L)) {
                                AppFileLogger.action(appContext, TAG, "Call for $cleanPhone concluded (state=IDLE).")
                                break
                            }
                            delay(pollIntervalMs.milliseconds)
                            elapsedMs += pollIntervalMs
                        }
                    } else {
                        delay(6000L.milliseconds)
                    }
                }

                isDirectCall
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to place call to $cleanPhone: ${e.message}", e)
                AppFileLogger.e(appContext, TAG, "Failed to place call to $cleanPhone: ${e.message}", e)
                repository.insertLog(
                    ExecutionLog(
                        taskId = task.id,
                        channel = "Phone Call",
                        recipient = cleanPhone,
                        messageSnippet = "Call failed: ${e.message}",
                        status = "FAILED",
                    ),
                )
                false
            }

            if (!success) {
                allCallsSuccess = false
                if (task.callFailurePolicy.equals("STOP_ON_FAILURE", ignoreCase = true)) {
                    AppFileLogger.w(appContext, TAG, "Halting remaining calls in sequence due to failure policy STOP_ON_FAILURE for recipient $cleanPhone.")
                    break
                }
            }

            if (index < (rawRecipients.size - 1)) {
                // Explicit configured inter-call delay between calls
                AppFileLogger.action(appContext, TAG, "Applying configured inter-call delay of ${task.delayBetweenCallsSeconds}s before dialing next recipient...")
                var elapsed = 0L
                val step = 250L
                while (elapsed < interCallDelayMs) {
                    val chunk = minOf(step, interCallDelayMs - elapsed)
                    delay(chunk.milliseconds)
                    elapsed += chunk
                }
            }
        }

        completeOrRescheduleTask(appContext, repository, task, allCallsSuccess)
        if (anyDirectCall && PreferenceManager(appContext).isAutoLockAfterSendEnabled) {
            executeDeviceLockPostcondition(appContext, task.title)
        }
        return allCallsSuccess
    }
    /**
     * What it is:
     * Execution routine for [executeWhatsAppCallTask].
     *
     * What it does:
     * Coordinates executeWhatsAppCallTask operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SequentialTaskEngine]
     */

    private suspend fun executeWhatsAppCallTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val baseRecipients = task.recipients.filter { it.isNotBlank() }
        if (baseRecipients.isEmpty()) {
            AppFileLogger.e(appContext, TAG, "Task #${task.id}: No recipient phone number provided for WhatsApp call")
            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = "WhatsApp Call",
                    recipient = "Unknown",
                    messageSnippet = "No phone number specified",
                    status = "FAILED",
                ),
            )
            completeOrRescheduleTask(appContext, repository, task, success = false)
            return false
        }

        if (!AccessibilityHealthManager.isServiceOperational()) {
            val ready = AccessibilityHealthManager.awaitServiceReadiness(appContext, 5000L)
            if (!ready) {
                val err = "Accessibility Service is not operational. Cannot execute WhatsApp call task #${task.id}."
                AppFileLogger.e(appContext, TAG, err)
                repository.insertLog(
                    ExecutionLog(
                        taskId = task.id,
                        channel = task.channel.displayName,
                        recipient = baseRecipients.firstOrNull() ?: "Unknown",
                        messageSnippet = "Accessibility service unavailable",
                        status = "FAILED",
                        details = err,
                    ),
                )
                completeOrRescheduleTask(appContext, repository, task, success = false)
                return false
            }
        }

        val requiredPackage = WhatsAppPackageHelper.getPackageForChannel(appContext, task.channel)
        if (requiredPackage == null) {
            val missingPkgName = if (task.channel == ChannelType.WHATSAPP_BUSINESS_CALL) "WhatsApp Business (com.whatsapp.w4b)" else "WhatsApp (com.whatsapp)"
            AppFileLogger.w(appContext, TAG, "Cannot execute Task #${task.id}: $missingPkgName is not installed on this device.")
            for (recipient in baseRecipients) {
                repository.insertLog(
                    ExecutionLog(
                        taskId = task.id,
                        channel = task.channel.displayName,
                        recipient = recipient,
                        messageSnippet = "WhatsApp call failed: $missingPkgName not installed",
                        status = "FAILED",
                        details = "$missingPkgName is not installed on this device",
                    ),
                )
            }
            completeOrRescheduleTask(appContext, repository, task, success = false)
            return false
        }

        val rawRecipients = if (task.callSequenceMode.equals("RANDOM", ignoreCase = true)) {
            val randomSeed = task.id xor task.triggerTimeMillis
            baseRecipients.shuffled(java.util.Random(randomSeed))
        } else {
            baseRecipients
        }

        var allCallsSuccess = true
        var anyCallPlaced = false
        val shouldRelockPref = PreferenceManager(appContext).isAutoLockAfterSendEnabled

        for ((index, rawRecipient) in rawRecipients.withIndex()) {
            if (TaskSafetyManager.isInterruptedByHuman.get() || !TaskSafetyManager.isDeviceIdle(appContext)) {
                val msg = "WhatsApp Call Task #${task.id} (${task.title}) interrupted by human interaction. Auto-postponing task to complete when idle."
                AppFileLogger.action(appContext, TAG, msg)
                TaskSafetyManager.isInterruptedByHuman.set(false)
                TaskSafetyManager.postponeTask(appContext, task.id, reason = msg)
                return false
            }

            val candidateName = rawRecipient.removePrefix("group:").removePrefix("broadcast:").trim()
            val phoneFromRepo = repository.findPhoneForContactName(candidateName)
            val effectiveTarget = phoneFromRepo ?: candidateName
            val cleanPhone = PhoneNumberNormalizer.cleanPhoneNumber(effectiveTarget).filter { it.isDigit() }
            if (cleanPhone.length < 7) {
                AppFileLogger.w(appContext, TAG, "Task #${task.id}: Skipping invalid phone number '$rawRecipient'")
                continue
            }

            val isLast = (index + 1) == rawRecipients.size
            val success = try {
                // Verify and prepare Accessibility Service before placing call
                PrivacyManager.getInstance(appContext).prepareAccessibilityForTask()
                if (!AccessibilityHealthManager.isServiceOperational() || TaskAccessibilityService.getInstance() == null) {
                    AppFileLogger.w(appContext, TAG, "Accessibility Service inactive for call to $cleanPhone. Attempting recovery...")
                    AccessibilityHealthManager.ensureServiceAlive(appContext)
                    val ready = AccessibilityHealthManager.awaitServiceReadiness(appContext, 3000L)
                    if (!ready || TaskAccessibilityService.getInstance() == null) {
                        AppFileLogger.e(appContext, TAG, "Accessibility Service could not be restored for VoIP call to $cleanPhone. Aborting.")
                        break
                    }
                }

                ensureDeviceAwakeAndUnlocked(task, cleanPhone)
                delay(300.milliseconds)

                if (!AccessibilityHealthManager.isServiceOperational() || TaskAccessibilityService.getInstance() == null) {
                    AccessibilityHealthManager.ensureServiceAlive(appContext)
                    AccessibilityHealthManager.awaitServiceReadiness(appContext, 3000L)
                }

                val dispatched = WhatsAppIntentDispatcher.launchWhatsAppCall(
                    context = appContext,
                    taskId = task.id,
                    channel = task.channel,
                    recipient = cleanPhone,
                    callDurationSeconds = task.callDurationSeconds,
                    shouldRelock = isLast && shouldRelockPref
                )

                if (dispatched && waitForWhatsAppCompletion()) {
                    anyCallPlaced = true
                    AppFileLogger.action(appContext, TAG, "WhatsApp voice call #${index + 1} initiated successfully to $cleanPhone")

                    // If duration is specified, hold for configured call duration and then hang up
                    if (task.callDurationSeconds > 0) {
                        val durationSec = task.callDurationSeconds
                        val targetPkg = when (task.channel) {
                            ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_BUSINESS_CALL -> "com.whatsapp.w4b"
                            else -> "com.whatsapp"
                        }

                        // Sliced delay polling checking master pause and early VoIP call termination
                        val waitForVoipDurationWithEarlyCheck: suspend (Long, Long, Boolean) -> Boolean = { totalMs, graceMs, allowEarlyExit ->
                            var elapsed = 0L
                            val step = 250L
                            var completedNormally = true
                            var idleConsecutiveMs = 0L
                            while (elapsed < totalMs) {
                                if (allowEarlyExit && elapsed >= graceMs) {
                                    if (!TaskAccessibilityService.isInWhatsAppCall()) {
                                        idleConsecutiveMs += step
                                        if (idleConsecutiveMs >= 2500L) {
                                            AppFileLogger.action(appContext, TAG, "WhatsApp VoIP call for $cleanPhone ended early (${elapsed / 1000}s into ${durationSec}s limit). Exiting wait loop.")
                                            completedNormally = false
                                            break
                                        }
                                    } else {
                                        idleConsecutiveMs = 0L
                                    }
                                }
                                val chunk = minOf(step, totalMs - elapsed)
                                delay(chunk.milliseconds)
                                elapsed += chunk
                            }
                            completedNormally
                        }

                        val durationCriteriaSatisfied = if (task.includeDialTimeInCallDuration) {
                            // Timer starts immediately when VoIP call screen is displayed
                            AppFileLogger.action(appContext, TAG, "Holding WhatsApp voice call for configured duration of ${durationSec}s (including dialing time)...")
                            waitForVoipDurationWithEarlyCheck(durationSec * 1000L, 15000L, durationSec > 25)
                        } else {
                            // Timer starts ONLY once the VoIP call is confirmed connected (answered)
                            AppFileLogger.action(appContext, TAG, "Awaiting WhatsApp VoIP call connection for $cleanPhone before starting ${durationSec}s duration...")
                            val dialTimeoutMs = 45_000L
                            val pollIntervalMs = 500L
                            var dialElapsedMs = 0L
                            var connected = false

                            while (dialElapsedMs < dialTimeoutMs) {
                                if (TaskAccessibilityService.isWhatsAppCallConnected()) {
                                    connected = true
                                    AppFileLogger.action(appContext, TAG, "WhatsApp VoIP call to $cleanPhone connected! Starting configured ${durationSec}s duration timer...")
                                    break
                                }
                                delay(pollIntervalMs.milliseconds)
                                dialElapsedMs += pollIntervalMs
                            }

                            if (connected) {
                                waitForVoipDurationWithEarlyCheck(durationSec * 1000L, 2000L, true)
                            } else {
                                AppFileLogger.action(appContext, TAG, "WhatsApp VoIP call to $cleanPhone did not connect within timeout. Hanging up...")
                                TaskAccessibilityService.endWhatsAppCall(targetPkg)
                                false
                            }
                        }

                        // Auto-hangup once configured duration has elapsed
                        if (durationCriteriaSatisfied) {
                            var hungUp = TaskAccessibilityService.endWhatsAppCall(targetPkg)
                            if (!hungUp) {
                                for (r in 1..4) {
                                    delay(500L.milliseconds)
                                    hungUp = TaskAccessibilityService.endWhatsAppCall(targetPkg)
                                    if (hungUp) break
                                }
                            }
                            if (!hungUp) {
                                AccessibilityHealthManager.ensureServiceAlive(appContext)
                                delay(400L.milliseconds)
                                hungUp = TaskAccessibilityService.endWhatsAppCall(targetPkg)
                            }
                            AppFileLogger.action(appContext, TAG, "Auto-hangup WhatsApp VoIP call after ${durationSec}s: result=$hungUp")

                            try {
                                com.jmd.superapp.core.SuperNotificationManager.postTaskNotification(
                                    appContext,
                                    (task.id + index + 60000).toInt(),
                                    "Call Ended",
                                    "Scheduled WhatsApp call with $cleanPhone concluded (${durationSec}s)."
                                )
                            } catch (e: Exception) {
                                Log.d(TAG, "WhatsApp call duration notification error: ${e.message}")
                            }
                            delay(1000L.milliseconds)
                        } else {
                            AppFileLogger.action(appContext, TAG, "WhatsApp call to $cleanPhone ended naturally before configured ${durationSec}s limit.")
                        }
                    } else {
                        // Indefinite call: await VoIP call conclusion or maximum 120s timeout
                        val maxWaitMs = 120_000L
                        var elapsedMs = 0L
                        var sawInCall = false
                        delay(3000L.milliseconds)
                        elapsedMs += 3000L
                        while (elapsedMs < maxWaitMs) {
                            val inCall = TaskAccessibilityService.isInWhatsAppCall()
                            if (inCall) {
                                sawInCall = true
                            } else if (sawInCall) {
                                AppFileLogger.action(appContext, TAG, "WhatsApp VoIP call for $cleanPhone concluded.")
                                break
                            }
                            delay(1000L.milliseconds)
                            elapsedMs += 1000L
                        }
                    }
                    true
                } else {
                    AppFileLogger.w(appContext, TAG, "WhatsApp voice call dispatch or completion timed out for $cleanPhone")
                    repository.insertLog(
                        ExecutionLog(
                            taskId = task.id,
                            channel = task.channel.displayName,
                            recipient = cleanPhone,
                            messageSnippet = "WhatsApp voice call dispatch timed out",
                            status = "FAILED",
                            details = "WhatsApp VoIP dispatch timed out or accessibility service unavailable",
                        ),
                    )
                    false
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to initiate WhatsApp call to $cleanPhone: ${e.message}", e)
                AppFileLogger.e(appContext, TAG, "Failed to initiate WhatsApp call to $cleanPhone: ${e.message}", e)
                repository.insertLog(
                    ExecutionLog(
                        taskId = task.id,
                        channel = "WhatsApp Call",
                        recipient = cleanPhone,
                        messageSnippet = "WhatsApp call failed: ${e.message}",
                        status = "FAILED",
                    ),
                )
                false
            }

            if (!success) {
                allCallsSuccess = false
                if (task.callFailurePolicy.equals("STOP_ON_FAILURE", ignoreCase = true)) {
                    AppFileLogger.w(appContext, TAG, "Halting remaining calls in sequence due to failure policy STOP_ON_FAILURE for recipient $cleanPhone.")
                    break
                }
            }

            if (index < (rawRecipients.size - 1)) {
                val interCallDelayMs = maxOf(2000L, task.delayBetweenCallsSeconds * 1000L)
                AppFileLogger.action(appContext, TAG, "WhatsApp Sequential Call #${index + 1} completed. Pacing next call in ${interCallDelayMs}ms.")
                var elapsed = 0L
                val step = 250L
                while (elapsed < interCallDelayMs) {
                    val chunk = minOf(step, interCallDelayMs - elapsed)
                    delay(chunk.milliseconds)
                    elapsed += chunk
                }
            }
        }

        completeOrRescheduleTask(appContext, repository, task, allCallsSuccess)
        if (anyCallPlaced && PreferenceManager(appContext).isAutoLockAfterSendEnabled) {
            executeDeviceLockPostcondition(appContext, task.title)
        }
        return allCallsSuccess
    }
    /**
     * What it is:
     * Execution routine for [executeCallSimulationTask].
     *
     * What it does:
     * Coordinates executeCallSimulationTask operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SequentialTaskEngine]
     */

    private fun executeCallSimulationTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val success = try {
            val notifId = (task.id + 70000).toInt()
            val simulationIntent = Intent(appContext, CallSimulationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("caller_name", task.simulatedCallerName.ifBlank { "Unknown Caller" })
                putExtra("caller_number", task.simulatedCallerNumber.ifBlank { "+91 98765 43210" })
                putExtra("caller_style", task.simulatedCallStyle.ifBlank { "Modern" })
                putExtra("task_id", task.id)
                putExtra("notif_id", notifId)
            }

            // Post high-priority full-screen intent notification ensuring display on locked screens across Android 10-16
            runCatching {
                val fullScreenPendingIntent = PendingIntent.getActivity(
                    appContext,
                    notifId,
                    simulationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                com.jmd.superapp.core.SuperNotificationManager.postSimulatedCallNotification(
                    appContext,
                    notifId,
                    task.simulatedCallerName,
                    task.simulatedCallerNumber,
                    fullScreenPendingIntent
                )
            }

            appContext.startActivity(simulationIntent)
            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = task.channel.displayName,
                    recipient = task.simulatedCallerNumber.ifBlank { "Simulated" },
                    messageSnippet = "Incoming simulated call from ${task.simulatedCallerName}",
                    status = "SUCCESS",
                ),
            )
            AppFileLogger.action(appContext, TAG, "Task #${task.id}: Triggered Call Simulation")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Call Simulation: ${e.message}", e)
            AppFileLogger.e(appContext, TAG, "Failed to launch Call Simulation: ${e.message}", e)
            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = task.channel.displayName,
                    recipient = task.simulatedCallerNumber.ifBlank { "Simulated" },
                    messageSnippet = "Call simulation failed: ${e.message}",
                    status = "FAILED",
                ),
            )
            false
        }

        completeOrRescheduleTask(appContext, repository, task, success)
        return success
    }
    /**
     * What it is:
     * Execution routine for [personalize].
     *
     * What it does:
     * Coordinates personalize operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SequentialTaskEngine]
     */

    private fun personalize(template: String, phone: String, name: String = ""): String {
        return MessageTemplateResolver.resolve(
            template = template,
            phone = phone,
            name = name,
            targetTimestamp = System.currentTimeMillis(),
        )
    }

    /**
     * What it is:
     * Bank-window deferral gate for every scheduled dispatch.
     *
     * What it does:
     * While a package on the owner's protected list is in the foreground, logs the deferral and
     * waits (bounded) for it to close, then lets the normal dispatch pipeline continue. The task is
     * never marked failed or dropped by this gate.
     */
    private suspend fun awaitProtectedWindowClearance(task: ScheduledTask) {
        val manager = com.jmd.superapp.core.ProtectedApplicationManager

        fun blockingPackage(): String? {
            val fg = com.jmd.superapp.service.TaskAccessibilityService.currentForegroundPackage
            return if (!fg.isNullOrBlank() && manager.isProtectedPackage(fg)) fg else null
        }

        val first = blockingPackage() ?: return
        AppFileLogger.i(
            appContext,
            TAG,
            "Task #${task.id} (${task.title}) DEFERRED: protected app $first is foreground"
        )
        val deadline = System.currentTimeMillis() + PROTECTED_WINDOW_MAX_WAIT_MS
        var previous = first
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(1000L)
            val current = blockingPackage()
            if (current == null) {
                AppFileLogger.i(appContext, TAG, "Task #${task.id} protected window closed; resuming dispatch")
                return
            }
            if (current != previous) {
                previous = current
                AppFileLogger.i(appContext, TAG, "Task #${task.id} still deferred by protected app $current")
            }
        }
        AppFileLogger.w(
            appContext,
            TAG,
            "Task #${task.id} protected-window wait expired after ${PROTECTED_WINDOW_MAX_WAIT_MS / 1000}s; proceeding"
        )
    }
}

