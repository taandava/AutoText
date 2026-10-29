

/**
 * Package: com.jmd.autotext.engine
 * Module: Automation Engine
 *
 * Centralized sequential execution orchestrator for scheduled tasks across SMS,
 * WhatsApp chats, WhatsApp Status stories, Phone Calls, Reminders, and Sound Mode events.
 *
 * Linked With:
 * - Schedulers: [com.jmd.autotext.scheduler.AlarmScheduler], [com.jmd.autotext.receiver.TaskAlarmReceiver]
 * - Services: [com.jmd.autotext.service.TaskForegroundService], [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Dispatchers: [com.jmd.autotext.engine.SmsSender], [com.jmd.autotext.engine.WhatsAppAutomationSender]
 * - Repositories: [com.jmd.autotext.data.repo.AppRepository], [com.jmd.autotext.data.repo.TaskRepository]
 * - Safety & Quota: [com.jmd.autotext.core.AutomationSafetyManager], [com.jmd.autotext.core.SmsQuotaManager]
 */
package com.jmd.autotext.engine

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jmd.autotext.AutoTextApplication
import com.jmd.autotext.ui.activity.FakeCallActivity
import com.jmd.autotext.ui.activity.AutomationLauncherActivity
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.core.AutomationSafetyManager
import com.jmd.autotext.core.MessageTemplateResolver
import com.jmd.autotext.core.PhoneNumberNormalizer
import com.jmd.autotext.core.SmsQuotaManager
import com.jmd.autotext.core.WhatsAppPackageHelper
import com.jmd.autotext.receiver.AutoTextDeviceAdminReceiver
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.ExecutionLog
import com.jmd.autotext.data.model.RecurrenceType
import com.jmd.autotext.data.model.ScheduledTask
import com.jmd.autotext.data.model.TaskStatus
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.scheduler.AlarmScheduler
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.whatsapp.WhatsAppIntentDispatcher
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Upper bound for waiting on a WhatsApp send to complete before the task is retried. */
private const val WHATSAPP_SEND_TIMEOUT_MS = 25000L

/**
 * Singleton sequential task execution pipeline.
 *
 * Guarantees serial execution of UI automation tasks via [uiAutomationMutex] and safe
 * concurrency for background cellular SMS dispatches via [smsMutex].
 */
class SequentialTaskEngine private constructor(private val appContext: Context) {

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val taskChannel = Channel<Long>(Channel.UNLIMITED)
    private val activeTaskIds = ConcurrentHashMap.newKeySet<Long>()

    private val uiAutomationMutex = Mutex()
    private val smsMutex = Mutex()

    private var currentTaskJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var currentTaskId: Long = -1L

    init {
        startSequentialProcessor()
    }

    private fun startSequentialProcessor() {
        engineScope.launch {
            val repository = AppRepository(appContext)
            val prefs = PreferenceManager(appContext)
            for (taskId in taskChannel) {
                try {
                    if (!prefs.isTasksMasterEnabled) {
                        AppFileLogger.w(appContext, TAG, "Task Scheduler Engine is disabled by master switch. Dropping task #$taskId.")
                        continue
                    }

                    if (AutomationSafetyManager.isEmergencyStopActive(appContext)) {
                        AppFileLogger.w(appContext, TAG, "Emergency Stop is active. Dropping queued task #$taskId.")
                        continue
                    }

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
                        AppFileLogger.w(appContext, TAG, "Task #$taskId is Awaiting Confirmation. Suppressing automatic redispatch to prevent duplicate delivery.")
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
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error processing taskId=$taskId in sequential queue", e)
                    AppFileLogger.e(appContext, TAG, "Failed sequential execution of Task #$taskId: ${e.message}", e)
                } finally {
                    currentTaskId = -1L
                    currentTaskJob = null
                    activeTaskIds.remove(taskId)
                }
            }
        }
    }

    /**
     * Cooperatively cancels a specific task if currently executing and removes it from active tracking.
     */
    fun cancelTask(taskId: Long) {
        Log.i(TAG, "cancelTask requested for Task #$taskId")
        AppFileLogger.action(appContext, TAG, "cancelTask requested for Task #$taskId")
        activeTaskIds.remove(taskId)
        if (currentTaskId == taskId) {
            AppFileLogger.w(appContext, TAG, "Task #$taskId is currently active in engine. Cancelling coroutine job immediately!")
            currentTaskJob?.cancel(kotlinx.coroutines.CancellationException("Task #$taskId cancelled by user"))
            AutoTextAccessibilityService.clearPending()
        }
    }

    /**
     * Cooperatively aborts the currently running task, drains the sequential queue, and resets engine state.
     */
    fun cancelAll() {
        Log.i(TAG, "cancelAll requested for SequentialTaskEngine")
        AppFileLogger.action(appContext, TAG, "cancelAll requested: Aborting active task and draining channel queue")
        currentTaskJob?.cancel(kotlinx.coroutines.CancellationException("All tasks cancelled by user"))
        AutoTextAccessibilityService.clearPending()
        while (taskChannel.tryReceive().isSuccess) {
            // Drain channel
        }
        activeTaskIds.clear()
        currentTaskId = -1L
    }

    companion object {
        private const val TAG = "SequentialTaskEngine"

        @Volatile
        private var INSTANCE: SequentialTaskEngine? = null

        fun getInstance(context: Context): SequentialTaskEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SequentialTaskEngine(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getUiAutomationMutex(context: Context): Mutex {
            return getInstance(context).uiAutomationMutex
        }

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
                AlarmScheduler(context).cancelTask(task.id)
                return
            }
            if (freshTask.status == TaskStatus.CANCELLED) {
                Log.i(TAG, "Task #${task.id} was marked CANCELLED by user. Preserving CANCELLED state and canceling alarms.")
                AlarmScheduler(context).cancelTask(task.id)
                return
            }
            val prefs = PreferenceManager(context)
            if (!prefs.isTasksMasterEnabled) {
                Log.i(TAG, "Task Scheduler Engine is disabled. Suppressing recurrence for Task #${task.id}.")
                AlarmScheduler(context).cancelTask(task.id)
                repository.updateTaskStatus(task.id, if (success) TaskStatus.DONE else TaskStatus.FAILED, now)
                return
            }
            if (AutomationSafetyManager.isEmergencyStopActive(context)) {
                Log.i(TAG, "Emergency Stop is active. Suppressing recurrence for Task #${task.id}.")
                AlarmScheduler(context).cancelTask(task.id)
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
            }
        }
    }

    /** Shortened convenience alias for [enqueue]. */
    fun queue(taskId: Long): Boolean = enqueue(taskId)

    /**
     * Submits a scheduled task ID into the sequential dispatch pipeline queue.
     *
     * Tasks are queued into an asynchronous coroutine channel and executed one-by-one.
     * If the task is already being processed or enqueued, duplicate submissions are rejected.
     *
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
     * Top-level safe execution handler routing tasks to appropriate channel engines
     * (SMS, WhatsApp, Calls, Reminders, Fake Calls, Sound Modes) with circuit-breaker protection.
     *
     * @param repository The central application repository.
     * @param task The scheduled task to execute.
     * @return `true` if task completed successfully; `false` on failure.
     */
    suspend fun executeTaskSafely(repository: AppRepository, task: ScheduledTask): Boolean {
        val prefs = PreferenceManager(appContext)
        if (!prefs.isTasksMasterEnabled) {
            AppFileLogger.w(appContext, TAG, "Task Scheduler Engine is disabled by master switch. Refusing execution for Task #${task.id}.")
            return false
        }
        if (AutomationSafetyManager.isEmergencyStopActive(appContext)) {
            AppFileLogger.w(appContext, TAG, "Emergency Stop is active. Refusing execution for Task #${task.id}.")
            return false
        }
        val fresh = repository.getTaskById(task.id)
        if (fresh == null || fresh.status == TaskStatus.CANCELLED) {
            AppFileLogger.w(appContext, TAG, "Task #${task.id} is cancelled or deleted in DB. Refusing execution.")
            return false
        }

        AppFileLogger.action(appContext, TAG, "Starting execution for Task #${task.id} (${task.title}) on [${task.channel.displayName}]")

        if (!AutomationSafetyManager.canExecute(task.channel)) {
            AppFileLogger.w(appContext, TAG, "AutomationSafetyManager refused execution for ${task.channel.displayName} (circuit breaker tripped or emergency stop active).")
            return false
        }

        // 0. Pre-flight Idle Check: Only trigger and complete task when idle!
        if (!AutomationSafetyManager.isDeviceIdle(appContext)) {
            val idleSince = (System.currentTimeMillis() - AutomationSafetyManager.lastHumanInteractionTimestamp.get()) / 1000
            val msg = "Postponed Task #${task.id} (${task.title}) by 30s: Device is in active human use (${idleSince}s since last interaction). Will execute once device becomes idle."
            AppFileLogger.action(appContext, TAG, msg)
            AutomationSafetyManager.postponeTask(appContext, task.id, reason = msg)
            return false
        }

        val success = when (task.channel) {
            ChannelType.WHATSAPP,
            ChannelType.WHATSAPP_BUSINESS,
            ChannelType.WHATSAPP_GROUP,
            ChannelType.WHATSAPP_STATUS,
            ChannelType.WHATSAPP_BUSINESS_STATUS,
            -> {
                uiAutomationMutex.withLock {
                    executeWhatsAppTaskWithRetries(repository, task)
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
                uiAutomationMutex.withLock {
                    executeWhatsAppCallTask(repository, task)
                }
            }
            ChannelType.REMINDER -> {
                executeReminderTask(repository, task)
            }
            ChannelType.FAKE_CALL -> {
                executeFakeCallTask(repository, task)
            }
            ChannelType.SOUND_MODE -> {
                executeSoundModeTask(repository, task)
            }
        }

        if (success) {
            AutomationSafetyManager.recordSuccess(task.channel)
        } else {
            AutomationSafetyManager.recordFailure(task.channel, appContext)
        }

        if (task.channel != ChannelType.FAKE_CALL && prefs.isAutoLockAfterSendEnabled) {
            executeDeviceLockPostcondition(appContext, task.title)
        }
        AccessibilityHealthManager.enforceAutoRotateInvariant(appContext)
        return success
    }

    suspend fun ensureDeviceAwakeAndUnlocked(
        task: ScheduledTask,
        targetRecipient: String = ""
    ): Boolean = ensureDeviceAwakeAndUnlocked(task.id, task.channel, targetRecipient)

    /**
     * Verifies whether the screen is interactive and keyguard is unlocked.
     * If the screen is off or keyguard is locked, turns on the display and initiates
     * keyguard dismiss or automated PIN entry via [AutomationLauncherActivity].
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
            val wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AutoText:ExecutionWakeLock",
            )
            wakeLock?.acquire(45000L)
        } catch (e: Exception) {
            Log.w(TAG, "ExecutionWakeLock acquire error: ${e.message}")
        }

        val prefs = PreferenceManager(appContext)
        val launchIntent = Intent(appContext, AutomationLauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AutomationLauncherActivity.EXTRA_TASK_ID, taskId)
            putExtra(AutomationLauncherActivity.EXTRA_CHANNEL, channel.name)
            putExtra(AutomationLauncherActivity.EXTRA_RECIPIENT, targetRecipient)
            putExtra(AutomationLauncherActivity.EXTRA_WAKE_ONLY, true)
        }

        if (prefs.isAutoUnlockEnabled && km?.isKeyguardLocked == true) {
            val pin = if (prefs.autoUnlockType != "SWIPE") prefs.deviceUnlockPin else ""
            AutoTextAccessibilityService.armAutoUnlock(
                pin = pin,
                shouldRelockAfterSend = prefs.isAutoLockAfterSendEnabled
            )
            appContext.startActivity(launchIntent)

            var waited = 0L
            while (waited < 10000L) {
                delay(500.milliseconds)
                waited += 500L
                if (km.isKeyguardLocked == false) {
                    AppFileLogger.action(
                        appContext,
                        TAG,
                        "Device unlocked successfully before executing ${channel.displayName} for Task #$taskId."
                    )
                    break
                }
            }
        } else {
            appContext.startActivity(launchIntent)
            delay(1200.milliseconds)
        }
        return true
    }

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
            val ready = AccessibilityHealthManager.waitForServiceReadiness(appContext, 3000L)
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
                ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_BUSINESS_STATUS, ChannelType.WHATSAPP_BUSINESS_CALL -> "WhatsApp Business (com.whatsapp.w4b)"
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
                val prefs = PreferenceManager(appContext)
                if (!prefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) {
                    AppFileLogger.w(appContext, TAG, "WhatsApp task execution halted: Master scheduler disabled or emergency stop active.")
                    return false
                }

                // Cooperative cancellation check before attempt
                val currentTaskState = repository.getTaskById(task.id)
                if ((currentTaskState == null) || (currentTaskState.status == TaskStatus.CANCELLED)) {
                    AppFileLogger.w(appContext, TAG, "Task #${task.id} was cancelled before or during WhatsApp attempt. Aborting.")
                    return false
                }

                // Interruption and Idle check:
                if (AutomationSafetyManager.isInterruptedByHuman.get() || !AutomationSafetyManager.isDeviceIdle(appContext)) {
                    val msg = "Task #${task.id} (${task.title}) was interrupted by human interaction. Auto-postponing task to complete when idle."
                    AppFileLogger.action(appContext, TAG, msg)
                    AutomationSafetyManager.isInterruptedByHuman.set(false)
                    AutomationSafetyManager.postponeTask(appContext, task.id, reason = msg)
                    return false
                }

                AccessibilityHealthManager.ensureServiceAlive(appContext)
                ensureDeviceAwakeAndUnlocked(task, task.recipients.firstOrNull() ?: "")
                delay(300.milliseconds)

                val waSender = WhatsAppAutomationSender(appContext)

                val shouldRelockPref = PreferenceManager(appContext).isAutoLockAfterSendEnabled
                if (task.channel == ChannelType.WHATSAPP_STATUS || task.channel == ChannelType.WHATSAPP_BUSINESS_STATUS) {
                    AutoTextAccessibilityService.clearPending()
                    AutoTextAccessibilityService.markDispatchPending()
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
                            val livePrefs = PreferenceManager(appContext)
                            if (!livePrefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) {
                                AppFileLogger.w(appContext, TAG, "Task #${task.id} halted during multi-recipient loop: Master scheduler disabled or emergency stop active.")
                                return false
                            }

                            // In-flight cooperative cancellation check between recipients
                            val taskCheck = repository.getTaskById(task.id)
                            if ((taskCheck == null) || (taskCheck.status == TaskStatus.CANCELLED)) {
                                AppFileLogger.w(appContext, TAG, "Task #${task.id} cancelled during multi-recipient loop. Stopping remaining dispatches.")
                                return false
                            }

                            if (AutomationSafetyManager.isInterruptedByHuman.get() || !AutomationSafetyManager.isDeviceIdle(appContext)) {
                                val msg = "Task #${task.id} (${task.title}) interrupted by human interaction between recipients. Auto-postponing task to complete when idle."
                                AppFileLogger.action(appContext, TAG, msg)
                                AutomationSafetyManager.isInterruptedByHuman.set(false)
                                AutomationSafetyManager.postponeTask(appContext, task.id, reason = msg)
                                return false
                            }

                            val phone = task.recipients[i]
                            if (phone in successfulRecipients) continue

                            val name = if (i in task.recipientNames.indices) task.recipientNames[i] else "Friend"
                            val personalizedMsg = personalize(task.messageText, name, phone)
                            val isLast = (i + 1) == totalRecipients

                            AutoTextAccessibilityService.clearPending()
                            AutoTextAccessibilityService.markDispatchPending()

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
                            } else {
                                currentBatchSuccess = false
                                AppFileLogger.w(appContext, TAG, "Failed sending WhatsApp message to $phone (Attempt $attempts)")
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
                        details = "WhatsApp automation timed out after $attempts attempt(s) or Accessibility Service not active.",
                    )
                )
            }
        }

        completeOrRescheduleTask(appContext, repository, task, success)
        return success
    }

    private suspend fun waitForWhatsAppCompletion(): Boolean {
        var waitedMs = 0L
        while (
            (AutoTextAccessibilityService.lastSendResult == AutoTextAccessibilityService.RESULT_PENDING) &&
                (waitedMs < WHATSAPP_SEND_TIMEOUT_MS)
        ) {
            delay(500.milliseconds)
            waitedMs += 500L
        }
        return AutoTextAccessibilityService.lastSendResult == AutoTextAccessibilityService.RESULT_SUCCESS
    }

    private suspend fun executeSmsTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val smsSender = SmsSender(appContext)
        var allSuccess = true
        var hasPendingConfirmation = false
        val occurrenceId = "${task.triggerTimeMillis}_${task.executionCount}"

        for (i in task.recipients.indices) {
            val livePrefs = PreferenceManager(appContext)
            if (!livePrefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) {
                AppFileLogger.w(appContext, TAG, "SMS task #${task.id} halted during recipient loop: Master scheduler disabled or emergency stop active.")
                return false
            }

            // Cooperative cancellation check between SMS recipients
            val currentTaskState = repository.getTaskById(task.id)
            if ((currentTaskState == null) || (currentTaskState.status == TaskStatus.CANCELLED)) {
                AppFileLogger.w(appContext, TAG, "SMS task #${task.id} cancelled during recipient loop. Aborting.")
                return false
            }

            if (AutomationSafetyManager.isInterruptedByHuman.get() || !AutomationSafetyManager.isDeviceIdle(appContext)) {
                val msg = "SMS Task #${task.id} (${task.title}) interrupted by human interaction. Auto-postponing task to complete when idle."
                AppFileLogger.action(appContext, TAG, msg)
                AutomationSafetyManager.isInterruptedByHuman.set(false)
                AutomationSafetyManager.postponeTask(appContext, task.id, reason = msg)
                return false
            }

            val phone = task.recipients[i]
            if (repository.isRecipientDeliveredForTask(task.id, phone, occurrenceId)) {
                AppFileLogger.i(appContext, TAG, "SMS recipient $phone already delivered for task #${task.id} (occurrence $occurrenceId). Skipping.")
                continue
            }

            val name = if (i in task.recipientNames.indices) task.recipientNames[i] else "Friend"
            val personalizedMsg = personalize(task.messageText, name, phone)

            val (sendSuccess, sendSegments, sendReason, sendDeliveryStatus) =
                smsSender.sendSmsWithResult(phone, personalizedMsg, task.simSlot, enforceQuota = true)
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
                repository.recordRecipientDelivery(task.id, phone, "SMS", "SUCCESS", occurrenceId)
            } else if (sendDeliveryStatus == SmsSender.DeliveryStatus.PENDING_TIMEOUT) {
                // Modem accepted message payload into cellular transmit buffer but carrier radio ACK timed out.
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
     * Executes post-task device lock if enabled in preferences.
     * Minimizes to home, attempts accessibility global lock, falls back to Device Admin lockNow,
     * and verifies screen interactive state.
     */
    private suspend fun executeDeviceLockPostcondition(context: Context, taskTitle: String): Boolean {
        val prefs = PreferenceManager(context)
        if (!prefs.isAutoLockAfterSendEnabled) {
            AppFileLogger.i(context, TAG, "Device auto-lock disabled in settings; skipping postcondition for '$taskTitle'")
            return true
        }

        AppFileLogger.action(context, TAG, "Executing device auto-lock postcondition for '$taskTitle'...")
        // 1. Return to home launcher first so private conversation/dialer is cleared
        AutoTextAccessibilityService.minimizeToHome(context)
        AccessibilityHealthManager.enforceAutoRotateInvariant(context)
        delay(400.milliseconds)

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == false) {
            AppFileLogger.action(context, TAG, "Screen is already off; auto-lock postcondition satisfied.")
            return true
        }

        // 2. Attempt lock via Accessibility Service global action
        var locked = AutoTextAccessibilityService.lockScreenNow()
        delay(400.milliseconds)

        // 3. Fallback to Device Admin lockNow if screen remains interactive
        if (pm?.isInteractive != false) {
            val adminLocked = AutoTextDeviceAdminReceiver.lockNow(context)
            locked = locked || adminLocked
            delay(400.milliseconds)
        }

        val screenIsOn = pm?.isInteractive != false
        val success = !screenIsOn || locked
        if (success) {
            AppFileLogger.action(context, TAG, "Device auto-lock postcondition succeeded for '$taskTitle' (screenInteractive=$screenIsOn)")
        } else {
            AppFileLogger.w(context, TAG, "Device auto-lock postcondition failed for '$taskTitle'; screen remains active")
        }
        return success
    }

    private suspend fun executeCallTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val prefs = PreferenceManager(appContext)
        if (!prefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) {
            AppFileLogger.w(appContext, TAG, "Emergency stop or master switch disabled! Halting phone call task #${task.id}.")
            return false
        }

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
            val livePrefs = PreferenceManager(appContext)
            if (!livePrefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) {
                AppFileLogger.w(appContext, TAG, "Phone call sequence halted: Master scheduler disabled or emergency stop active.")
                return false
            }

            val taskState = repository.getTaskById(task.id)
            if (taskState == null || taskState.status == TaskStatus.CANCELLED) {
                AppFileLogger.w(appContext, TAG, "Task #${task.id} cancelled during multi-call loop. Halting remaining calls.")
                return false
            }

            if (AutomationSafetyManager.isInterruptedByHuman.get() || !AutomationSafetyManager.isDeviceIdle(appContext)) {
                val msg = "Phone Call Task #${task.id} (${task.title}) interrupted by human interaction. Auto-postponing task to complete when idle."
                AppFileLogger.action(appContext, TAG, msg)
                AutomationSafetyManager.isInterruptedByHuman.set(false)
                AutomationSafetyManager.postponeTask(appContext, task.id, reason = msg)
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
                        try {
                            val subManager = appContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                            val activeSubs = subManager?.activeSubscriptionInfoList
                            val targetSub = activeSubs?.find { it.simSlotIndex == targetPhysicalSlot }
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
                            Log.w(TAG, "Missing permission to query telecom accounts", e)
                            explicitSimUnresolved = true
                        } catch (e: Throwable) {
                            Log.w(TAG, "Exception resolving call-capable account for SIM ${task.simSlot}: ${e.message}")
                            explicitSimUnresolved = true
                        }
                        putInt("com.android.phone.extra.slot", targetPhysicalSlot)
                        putInt("simSlot", targetPhysicalSlot)
                        putInt("android.telecom.extra.PHONE_ACCOUNT_HANDLE_INDEX", targetPhysicalSlot)
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

                var callInitiated = false
                if ((hasCallPerm) && (telecomManager != null)) {
                    try {
                        telecomManager.placeCall(uri, extras)
                        callInitiated = true
                        AppFileLogger.action(appContext, TAG, "Placed direct telecom call to $cleanPhone via SIM ${task.simSlot}")
                    } catch (e: Exception) {
                        Log.w(TAG, "telecomManager.placeCall failed, falling back to Intent: ${e.message}")
                    }
                }

                val isDirectCall = callInitiated || hasCallPerm
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
                    appContext.startActivity(callIntent)
                }

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

                // 2. Call Duration Holding and Automatic Hangup Execution
                if (isDirectCall && task.callDurationSeconds > 0) {
                    val durationSec = task.callDurationSeconds
                    val telephonyManager = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    val hasPhoneStatePerm = ContextCompat.checkSelfPermission(
                        appContext,
                        android.Manifest.permission.READ_PHONE_STATE,
                    ) == PackageManager.PERMISSION_GRANTED

                    val waitForCallDuration: suspend (Long) -> Unit = { totalMs ->
                        var elapsed = 0L
                        val step = 250L
                        while (elapsed < totalMs) {
                            val livePrefs = PreferenceManager(appContext)
                            if (!livePrefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) {
                                AppFileLogger.w(appContext, TAG, "Call duration holding interrupted: Master switch disabled or emergency stop active.")
                                break
                            }
                            val chunk = minOf(step, totalMs - elapsed)
                            delay(chunk.milliseconds)
                            elapsed += chunk
                        }
                    }

                    if (task.includeDialTimeInCallDuration) {
                        // Timer starts immediately when call placement initiates
                        AppFileLogger.action(appContext, TAG, "Call duration limit of ${durationSec}s (including dialing time) holding for $cleanPhone...")
                        waitForCallDuration(durationSec * 1000L)
                    } else if (telephonyManager != null && hasPhoneStatePerm) {
                        // Timer starts ONLY once the call is confirmed answered/connected (OFFHOOK)
                        AppFileLogger.action(appContext, TAG, "Awaiting call connection (OFFHOOK) for $cleanPhone before starting ${durationSec}s call duration...")
                        val dialTimeoutMs = 40_000L
                        val pollIntervalMs = 600L
                        var dialElapsedMs = 0L
                        var connected = false

                        while (dialElapsedMs < dialTimeoutMs) {
                            val livePrefs = PreferenceManager(appContext)
                            if (!livePrefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) break
                            val state = com.jmd.autotext.compat.TelephonyManagerCompat.getCallState(appContext, telephonyManager)
                            if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                                connected = true
                                AppFileLogger.action(appContext, TAG, "Call to $cleanPhone connected (OFFHOOK)! Holding for configured ${durationSec}s duration...")
                                break
                            } else if (dialElapsedMs >= 4000L && state == TelephonyManager.CALL_STATE_IDLE) {
                                AppFileLogger.action(appContext, TAG, "Call to $cleanPhone disconnected or rejected before connect.")
                                break
                            }
                            delay(pollIntervalMs.milliseconds)
                            dialElapsedMs += pollIntervalMs
                        }

                        if (connected) {
                            waitForCallDuration(durationSec * 1000L)
                        }
                    } else {
                        // Fallback: wait configured duration
                        waitForCallDuration(durationSec * 1000L)
                    }

                    // Duration elapsed! Terminate the active call immediately
                    AppFileLogger.action(appContext, TAG, "Configured call duration (${durationSec}s) elapsed for $cleanPhone. Hanging up call now...")
                    val hungUp = AutoTextAccessibilityService.endPhoneCall(appContext)
                    AppFileLogger.action(appContext, TAG, "Hangup request for $cleanPhone: result=$hungUp")

                    try {
                        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        val endNotif = NotificationCompat.Builder(appContext, AutoTextApplication.CHANNEL_TASKS)
                            .setSmallIcon(android.R.drawable.ic_menu_call)
                            .setContentTitle("Call Duration Elapsed (${durationSec}s)")
                            .setContentText("Scheduled call with $cleanPhone concluded.")
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .build()
                        nm?.notify((task.id + index + 50000).toInt(), endNotif)
                    } catch (e: Exception) {
                        Log.d(TAG, "Call duration notification error: ${e.message}")
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
                            val livePrefs = PreferenceManager(appContext)
                            if (!livePrefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) break
                            val state = com.jmd.autotext.compat.TelephonyManagerCompat.getCallState(appContext, telephonyManager)
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
                    val livePrefs = PreferenceManager(appContext)
                    if (!livePrefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) {
                        AppFileLogger.w(appContext, TAG, "Inter-call delay cancelled: Master switch disabled or emergency stop active.")
                        break
                    }
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

    private suspend fun executeWhatsAppCallTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val prefs = PreferenceManager(appContext)
        if (!prefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) {
            AppFileLogger.w(appContext, TAG, "WhatsApp call task aborted: Master switch disabled or emergency stop active.")
            return false
        }
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
            val ready = AccessibilityHealthManager.waitForServiceReadiness(appContext, 3000L)
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
            val livePrefs = PreferenceManager(appContext)
            if (!livePrefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) {
                AppFileLogger.w(appContext, TAG, "Emergency stop or master switch disabled during WhatsApp call sequence. Halting.")
                return false
            }

            if (AutomationSafetyManager.isInterruptedByHuman.get() || !AutomationSafetyManager.isDeviceIdle(appContext)) {
                val msg = "WhatsApp Call Task #${task.id} (${task.title}) interrupted by human interaction. Auto-postponing task to complete when idle."
                AppFileLogger.action(appContext, TAG, msg)
                AutomationSafetyManager.isInterruptedByHuman.set(false)
                AutomationSafetyManager.postponeTask(appContext, task.id, reason = msg)
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
                ensureDeviceAwakeAndUnlocked(task, cleanPhone)

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

                    // If duration is specified, hold for the configured call duration and then hang up
                    if (task.callDurationSeconds > 0) {
                        val durationSec = task.callDurationSeconds
                        AppFileLogger.action(appContext, TAG, "Holding WhatsApp voice call for configured duration of ${durationSec}s...")
                        delay((durationSec * 1000L).milliseconds)

                        val targetPkg = when (task.channel) {
                            ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_BUSINESS_CALL -> "com.whatsapp.w4b"
                            else -> "com.whatsapp"
                        }
                        var hungUp = AutoTextAccessibilityService.endWhatsAppCall(targetPkg)
                        if (!hungUp) {
                            for (r in 1..3) {
                                delay(600L.milliseconds)
                                hungUp = AutoTextAccessibilityService.endWhatsAppCall(targetPkg)
                                if (hungUp) break
                            }
                        }
                        AppFileLogger.action(appContext, TAG, "Auto-hangup WhatsApp VoIP call after ${durationSec}s: result=$hungUp")
                        delay(1000L.milliseconds)
                    } else {
                        // Indefinite call: await VoIP call conclusion or maximum 120s timeout
                        val maxWaitMs = 120_000L
                        var elapsedMs = 0L
                        var sawInCall = false
                        delay(3000L.milliseconds)
                        elapsedMs += 3000L
                        while (elapsedMs < maxWaitMs) {
                            val livePrefs = PreferenceManager(appContext)
                            if (!livePrefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) break
                            val inCall = AutoTextAccessibilityService.isInWhatsAppCall()
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
                    val livePrefs = PreferenceManager(appContext)
                    if (!livePrefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(appContext)) {
                        AppFileLogger.w(appContext, TAG, "WhatsApp inter-call delay cancelled: Master switch disabled or emergency stop active.")
                        break
                    }
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

    private fun executeReminderTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val success = try {
            val label = AlarmScheduler.getSystemAlarmLabel(task)

            // 1. Ensure native System Alarm is registered and active in Android's Clock app
            AlarmScheduler.setDeviceSystemAlarm(appContext, task)

            // 2. Open System Alarm/Clock if available to display alarms
            runCatching {
                val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(clockIntent)
            }

            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = "Reminder",
                    recipient = "System Clock",
                    messageSnippet = "System Alarm: $label",
                    status = "SUCCESS",
                ),
            )
            AppFileLogger.action(appContext, TAG, "Task #${task.id}: Triggered System Alarm Clock ($label)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger system reminder for task #${task.id}: ${e.message}", e)
            AppFileLogger.e(appContext, TAG, "Failed to trigger system reminder: ${e.message}", e)
            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = "Reminder",
                    recipient = "System Clock",
                    messageSnippet = "Reminder failed: ${e.message}",
                    status = "FAILED",
                ),
            )
            false
        }

        completeOrRescheduleTask(appContext, repository, task, success)
        return success
    }

    private fun executeFakeCallTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val success = try {
            val fakeIntent = Intent(appContext, FakeCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("caller_name", task.fakeCallerName.ifBlank { "Unknown Caller" })
                putExtra("caller_number", task.fakeCallerNumber.ifBlank { "+1 555-0199" })
                putExtra("caller_style", task.fakeCallStyle.ifBlank { "Modern" })
            }
            appContext.startActivity(fakeIntent)
            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = "Fake Call",
                    recipient = task.fakeCallerNumber.ifBlank { "Simulated" },
                    messageSnippet = "Incoming simulated call from ${task.fakeCallerName}",
                    status = "SUCCESS",
                ),
            )
            AppFileLogger.action(appContext, TAG, "Task #${task.id}: Triggered Fake Call Simulation")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Fake Call: ${e.message}", e)
            AppFileLogger.e(appContext, TAG, "Failed to launch Fake Call: ${e.message}", e)
            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = "Fake Call",
                    recipient = task.fakeCallerNumber.ifBlank { "Simulated" },
                    messageSnippet = "Fake call simulation failed: ${e.message}",
                    status = "FAILED",
                ),
            )
            false
        }

        completeOrRescheduleTask(appContext, repository, task, success)
        return success
    }

    private fun executeSoundModeTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val success = try {
            val audioManager = appContext.getSystemService(AudioManager::class.java)
                ?: (appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?: return false
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val modeSetting = task.soundModeSetting.lowercase()

            val currentRinger = audioManager.ringerMode
            val targetRinger = when (modeSetting) {
                "silent" -> AudioManager.RINGER_MODE_SILENT
                "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            val hasPolicyAccess = notificationManager?.isNotificationPolicyAccessGranted == true
            val isDndTransition = (currentRinger != targetRinger) && (
                currentRinger == AudioManager.RINGER_MODE_SILENT ||
                currentRinger == AudioManager.RINGER_MODE_VIBRATE ||
                targetRinger == AudioManager.RINGER_MODE_SILENT ||
                targetRinger == AudioManager.RINGER_MODE_VIBRATE
            )

            if (isDndTransition && !hasPolicyAccess) {
                AppFileLogger.w(appContext, TAG, "Task #${task.id}: Cannot change sound mode from $currentRinger to $targetRinger without Do Not Disturb (Notification Policy) access.")
                repository.insertLog(
                    ExecutionLog(
                        taskId = task.id,
                        channel = "Sound Mode",
                        recipient = "System",
                        messageSnippet = "Permission needed: Do Not Disturb access required to set $modeSetting mode",
                        status = "ACTION_REQUIRED",
                        details = "Do Not Disturb access is required to change ringer mode across silent/vibrate/normal states. Please grant in Settings."
                    ),
                )
                false
            } else {
                if (currentRinger != targetRinger) {
                    audioManager.ringerMode = targetRinger
                }
                if (modeSetting == "normal" || modeSetting == "ring") {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, (maxVol * 0.7).toInt(), 0)
                }
                repository.insertLog(
                    ExecutionLog(
                        taskId = task.id,
                        channel = "Sound Mode",
                        recipient = "System",
                        messageSnippet = "Sound profile set to ${task.soundModeSetting}",
                        status = "SUCCESS",
                        details = "Ringer mode set to $targetRinger"
                    ),
                )
                AppFileLogger.action(appContext, TAG, "Task #${task.id}: Applied sound mode profile: ${task.soundModeSetting}")
                true
            }
        } catch (e: SecurityException) {
            AppFileLogger.w(appContext, TAG, "SecurityException changing sound mode: ${e.message}")
            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = "Sound Mode",
                    recipient = "System",
                    messageSnippet = "Permission needed: Notification Policy access required",
                    status = "ACTION_REQUIRED",
                    details = e.message ?: "Not allowed to change Do Not Disturb state"
                )
            )
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set sound mode: ${e.message}", e)
            AppFileLogger.e(appContext, TAG, "Failed to set sound mode: ${e.message}", e)
            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = "Sound Mode",
                    recipient = "System",
                    messageSnippet = "Failed to set sound mode: ${e.message}",
                    status = "FAILED",
                    details = e.message ?: "Unknown audio setting error"
                ),
            )
            false
        }

        completeOrRescheduleTask(appContext, repository, task, success)
        return success
    }

    private fun personalize(template: String, name: String, phone: String): String {
        return MessageTemplateResolver.resolve(
            template = template,
            name = name,
            phone = phone,
            targetTimestamp = System.currentTimeMillis(),
        )
    }
}
