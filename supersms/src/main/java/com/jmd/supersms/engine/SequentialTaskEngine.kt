/**
 * Package: com.jmd.supersms.engine
 * Module: Task Execution & Dispatch Engines
 *
 * What it is:
 * Architecture definition and implementation for [SequentialTaskEngine].
 *
 * What it does:
 * Centralized sequential execution orchestrator for scheduled tasks across SMS, Phone Calls, and Call Simulations.
 *
 * Linked With:
 * - Repositories: [com.jmd.supersms.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.supersms.data.model.*]
 * - Schedulers & Receivers: [com.jmd.supersms.scheduler.AlarmScheduler]
 * - Services & Engines: [com.jmd.supersms.engine.SmsSender], [com.jmd.supersms.telephony.TelephonyCallEngine]
 * - UI Screens & Components: [com.jmd.supersms.ui.activity.CallSimulationActivity], [com.jmd.supersms.ui.activity.TaskLauncherActivity]
 * - Core Security & Utilities: [android.app.KeyguardManager], [android.app.NotificationManager], [android.content.pm.PackageManager], [android.media.AudioManager]
 * - Android Framework & Jetpack: [android.app.PendingIntent], [android.net.Uri], [kotlinx.coroutines.channels.Channel], [kotlinx.coroutines.sync.Mutex]
 */

package com.jmd.supersms.engine

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
import com.jmd.supersms.R
import com.jmd.supersms.SuperApplication
import com.jmd.supersms.compat.TelephonyManagerCompat
import com.jmd.supersms.core.*
import com.jmd.supersms.data.model.*
import com.jmd.supersms.data.prefs.PreferenceManager
import com.jmd.supersms.data.repo.AppRepository
import com.jmd.supersms.scheduler.AlarmScheduler
import com.jmd.supersms.telephony.TelephonyCallEngine
import com.jmd.supersms.ui.activity.CallSimulationActivity
import com.jmd.supersms.ui.activity.TaskLauncherActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

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
     * Top-level safe execution handler routing tasks to appropriate channel engines (SMS, Phone Calls, Call Simulations) with circuit-breaker protection.
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

        // Exactly-once guard: one durable claim per scheduled occurrence.
        val dispatchOccurrence = DispatchJournal.occurrenceKey(task.id, task.triggerTimeMillis)
        if (!DispatchJournal.claim(appContext, task.id, dispatchOccurrence)) {
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
            ChannelType.SMS -> {
                smsMutex.withLock {
                    executeSmsTask(repository, task)
                }
            }
            ChannelType.CALL -> {
                executeCallTask(repository, task)
            }
            ChannelType.CALL_SIMULATION -> {
                executeCallSimulationTask(repository, task)
            }
            else -> {
                AppFileLogger.w(appContext, TAG, "Unsupported channel for SuperSMS: ${task.channel.name}")
                false
            }
        }

        val freshAfter = repository.getTaskById(task.id)
        val isCancelledOrInterrupted = freshAfter?.status == TaskStatus.CANCELLED || TaskSafetyManager.isInterruptedByHuman.get()

        if (success) {
            TaskSafetyManager.recordSuccess(task.channel)
        } else if (!isCancelledOrInterrupted) {
            TaskSafetyManager.recordFailure(task.channel, appContext)
        }

        if (success) {
            DispatchJournal.complete(appContext, task.id, dispatchOccurrence)
        } else {
            DispatchJournal.fail(appContext, task.id, dispatchOccurrence)
        }

        return success
    }

    private suspend fun executeSmsTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val smsSender = SmsSender(appContext)
        var allSuccess = true
        var hasPendingConfirmation = false
        val occurrenceId = "${task.triggerTimeMillis}_${task.executionCount}"

        for (i in task.recipients.indices) {
            val currentTaskState = repository.getTaskById(task.id)
            if (currentTaskState == null || currentTaskState.status == TaskStatus.CANCELLED) {
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

            val personalizedMsg = personalize(task.messageText, phone)

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
                    recipientPhone = phone,
                    messageText = personalizedMsg,
                    simSlot = task.simSlot,
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
            } else if (sendDeliveryStatus == SmsSender.DeliveryStatus.PENDING_TIMEOUT || sendDeliveryStatus == SmsSender.DeliveryStatus.PARTIAL_DISPATCH) {
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
        return allSuccess
    }

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
                                    }
                                }
                            }
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Missing permission to query telecom accounts", e)
                            explicitSimUnresolved = true
                        } catch (e: Throwable) {
                            Log.w(TAG, "Exception resolving call-capable account for SIM ${task.simSlot}: ${e.message}")
                            explicitSimUnresolved = true
                        }
                        OemBrandHelper.populateTelecomSimExtras(this, targetPhysicalSlot, matchedSubId)
                    }
                }

                if (explicitSimUnresolved) {
                    val errorMsg = "Cannot place call: Selected SIM ${task.simSlot} is not available or has no active cellular subscription"
                    AppFileLogger.e(appContext, TAG, errorMsg)
                    repository.insertLog(
                        ExecutionLog(
                            taskId = task.id,
                            channel = "Phone Call",
                            recipient = cleanPhone,
                            messageSnippet = errorMsg,
                            status = "FAILED",
                            details = "Explicit SIM ${task.simSlot} failed resolution",
                        ),
                    )
                    allCallsSuccess = false
                    continue
                }

                TelephonyCallEngine.trackDispatchedCall(cleanPhone)
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

                if (isDirectCall && task.callDurationSeconds > 0) {
                    val durationSec = task.callDurationSeconds
                    val telephonyManager = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    val hasPhoneStatePerm = ContextCompat.checkSelfPermission(
                        appContext,
                        android.Manifest.permission.READ_PHONE_STATE,
                    ) == PackageManager.PERMISSION_GRANTED

                    val isCellularCallOngoing: () -> Boolean = {
                        TelephonyManagerCompat.isCallActiveOrRinging(appContext, telephonyManager)
                    }

                    val waitForCallDurationWithEarlyCheck: suspend (Long, Long, Boolean) -> Boolean = { totalMs, graceMs, allowEarlyExit ->
                        var elapsed = 0L
                        val step = 250L
                        var completedNormally = true
                        var idleConsecutiveMs = 0L
                        while (elapsed < totalMs) {
                            if (allowEarlyExit && elapsed >= graceMs) {
                                if (!isCellularCallOngoing()) {
                                    idleConsecutiveMs += step
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
                        AppFileLogger.action(appContext, TAG, "Call duration limit of ${durationSec}s (including dialing time) holding for $cleanPhone...")
                        val allowEarly = durationSec > 25
                        waitForCallDurationWithEarlyCheck(durationSec * 1000L, 15000L, allowEarly)
                    } else if (telephonyManager != null && hasPhoneStatePerm) {
                        AppFileLogger.action(appContext, TAG, "Awaiting call connection for $cleanPhone before starting ${durationSec}s call duration...")
                        val dialTimeoutMs = 45_000L
                        val pollIntervalMs = 500L
                        var dialElapsedMs = 0L
                        var connected = false

                        while (dialElapsedMs < dialTimeoutMs) {
                            val isCallAlive = isCellularCallOngoing()
                            if (dialElapsedMs >= 4000L && !isCallAlive) {
                                AppFileLogger.action(appContext, TAG, "Call to $cleanPhone ended before answering (busy/declined/dropped).")
                                connected = false
                                break
                            }

                            val isOffhook = TelephonyManagerCompat.isCallConnected(appContext, telephonyManager)
                            if (isOffhook && dialElapsedMs >= 3000L) {
                                connected = true
                                AppFileLogger.action(appContext, TAG, "Call to $cleanPhone connected! Starting configured ${durationSec}s call duration timer...")
                                break
                            }

                            delay(pollIntervalMs.milliseconds)
                            dialElapsedMs += pollIntervalMs
                        }

                        if (connected) {
                            waitForCallDurationWithEarlyCheck(durationSec * 1000L, 2000L, true)
                        } else {
                            AppFileLogger.action(appContext, TAG, "Call to $cleanPhone did not connect within timeout ($dialElapsedMs ms). Attempting hangup...")
                            TelephonyCallEngine.endCall(appContext)
                            false
                        }
                    } else {
                        waitForCallDurationWithEarlyCheck(durationSec * 1000L, 10000L, durationSec > 20)
                    }

                    if (durationCriteriaSatisfied) {
                        AppFileLogger.action(appContext, TAG, "Configured call duration (${durationSec}s) elapsed for $cleanPhone. Hanging up call now...")
                        val hungUp = TelephonyCallEngine.endCall(appContext)
                        AppFileLogger.action(appContext, TAG, "Hangup request for $cleanPhone: result=$hungUp")

                        try {
                            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            val endNotif = NotificationCompat.Builder(appContext, SuperApplication.CHANNEL_TASKS)
                                .setSmallIcon(R.drawable.ic_notification)
                                .apply { NotificationIconHelper.getLargeIcon(appContext)?.let { setLargeIcon(it) } }
                                .setColor(0xFFFFD600.toInt())
                                .setContentTitle("Call Ended")
                                .setContentText("Scheduled call with $cleanPhone concluded (${durationSec}s).")
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setAutoCancel(true)
                                .build()
                            nm?.notify((task.id + index + 50000).toInt(), endNotif)
                        } catch (e: Exception) {
                            Log.d(TAG, "Call duration notification error: ${e.message}")
                        }
                    } else {
                        AppFileLogger.action(appContext, TAG, "Call to $cleanPhone ended naturally or did not trigger auto-hangup.")
                    }

                    delay(2000L.milliseconds)
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
        return allCallsSuccess
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
    ): Boolean = true

    suspend fun ensureDeviceAwakeAndUnlocked(
        taskId: Long,
        channel: ChannelType,
        targetRecipient: String = ""
    ): Boolean = true
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
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val callNotif = NotificationCompat.Builder(appContext, SuperApplication.CHANNEL_TASKS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .apply { NotificationIconHelper.getLargeIcon(appContext)?.let { setLargeIcon(it) } }
                    .setColor(0xFFFFD600.toInt())
                    .setContentTitle(task.simulatedCallerName.ifBlank { "Incoming Call" })
                    .setContentText("Incoming call from ${task.simulatedCallerNumber.ifBlank { "Unknown Caller" }}")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setAutoCancel(true)
                    .build()
                nm?.notify(notifId, callNotif)
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

    private fun personalize(template: String, phone: String): String {
        return MessageTemplateResolver.resolve(
            template = template,
            phone = phone,
            targetTimestamp = System.currentTimeMillis(),
        )
    }
}


