package com.jmd.autotext.engine

import android.content.Context
import android.util.Log
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ExecutionLog
import com.jmd.autotext.model.ScheduledTask
import com.jmd.autotext.model.TaskStatus
import com.jmd.autotext.sender.SmsSender
import com.jmd.autotext.sender.WhatsAppAutomationSender
import com.jmd.autotext.service.AccessibilityHealthManager
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.util.AppFileLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Intelligent Sequential Task Engine & Queue Coordinator.
 *
 * Responsibilities:
 * 1. Queue Management: Enqueues incoming tasks, deduplicates redundant requests,
 *    and serializes tasks by conflicting resource domains (e.g., WhatsApp UI automation, SMS radio).
 * 2. Conflict Resolution: Prevents concurrent WhatsApp accessibility flows from stepping
 *    on each other, sequencing them cleanly with intelligent backoff and retry.
 * 3. Health & Auto-Recovery: Ensures accessibility service and device wake state before dispatch.
 */
class SequentialTaskEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "SequentialTaskEngine"

        @Volatile
        private var INSTANCE: SequentialTaskEngine? = null

        fun getInstance(context: Context): SequentialTaskEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SequentialTaskEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val taskQueue = ConcurrentLinkedQueue<Long>()
    private val activeTaskIds = ConcurrentHashMap.newKeySet<Long>()
    
    // Resource locks to guarantee serial UI execution for WhatsApp/UI automation
    private val uiAutomationMutex = Mutex()
    private val smsMutex = Mutex()
    private val isProcessorRunning = AtomicBoolean(false)

    /**
     * Enqueues a task for sequential, collision-free execution.
     */
    fun enqueue(taskId: Long): Boolean {
        if (taskId <= 0L) return false
        if (activeTaskIds.contains(taskId)) {
            AppFileLogger.w(appContext, TAG, "Task #$taskId is already in the execution pipeline. Ignoring duplicate enqueue.")
            return false
        }
        activeTaskIds.add(taskId)
        taskQueue.offer(taskId)
        AppFileLogger.action(appContext, TAG, "Enqueued Task #$taskId. Total pending in queue: ${taskQueue.size}")
        triggerQueueProcessor()
        return true
    }

    /**
     * Triggers the sequential background consumer loop if not already running.
     */
    private fun triggerQueueProcessor() {
        if (isProcessorRunning.compareAndSet(false, true)) {
            engineScope.launch {
                try {
                    processQueue()
                } finally {
                    isProcessorRunning.set(false)
                    // If items arrived right as we were finishing, trigger again
                    if (taskQueue.isNotEmpty()) {
                        triggerQueueProcessor()
                    }
                }
            }
        }
    }

    private suspend fun processQueue() {
        val repository = AppRepository(appContext)

        while (taskQueue.isNotEmpty()) {
            val taskId = taskQueue.poll() ?: break
            try {
                val task = repository.getTaskById(taskId)
                if (task == null) {
                    AppFileLogger.w(appContext, TAG, "Task #$taskId no longer exists in DB. Skipping.")
                    continue
                }

                if (task.status == TaskStatus.DONE) {
                    AppFileLogger.i(appContext, TAG, "Task #$taskId is already marked DONE. Skipping.")
                    continue
                }

                executeTaskSafely(repository, task)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error processing taskId=$taskId", e)
                AppFileLogger.e(appContext, TAG, "Failed sequential execution of Task #$taskId: ${e.message}", e)
            } finally {
                activeTaskIds.remove(taskId)
            }
        }
    }

    /**
     * Dispatches task through dedicated channel locks to guarantee no concurrent UI conflicts.
     */
    suspend fun executeTaskSafely(repository: AppRepository, task: ScheduledTask): Boolean {
        AppFileLogger.action(appContext, TAG, "Starting execution for Task #${task.id} (${task.title}) on [${task.channel.displayName}]")

        return when (task.channel) {
            ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS,
            ChannelType.WHATSAPP_GROUP, ChannelType.WHATSAPP_STATUS -> {
                // WhatsApp automation demands exclusive foreground accessibility lock
                uiAutomationMutex.withLock {
                    executeWhatsAppTaskWithRetries(repository, task)
                }
            }
            ChannelType.SMS -> {
                smsMutex.withLock {
                    executeSmsTask(repository, task)
                }
            }
            ChannelType.CALL, ChannelType.REMINDER, ChannelType.FAKE_CALL, ChannelType.EMAIL, ChannelType.SOUND_MODE -> {
                executeDirectTask(repository, task)
            }
        }
    }

    private suspend fun executeWhatsAppTaskWithRetries(
        repository: AppRepository,
        task: ScheduledTask,
        maxRetries: Int = 2
    ): Boolean {
        var attempts = 0
        var success = false

        while (attempts <= maxRetries && !success) {
            attempts++
            try {
                // Ensure accessibility service is active and responsive
                AccessibilityHealthManager.ensureServiceAlive(appContext)
                delay(300.milliseconds)

                val waSender = WhatsAppAutomationSender(appContext)
                
                if (task.channel == ChannelType.WHATSAPP_STATUS) {
                    AutoTextAccessibilityService.clearPending()
                    AutoTextAccessibilityService.markDispatchPending()
                    val statusTarget = task.recipients.firstOrNull() ?: "STATUS"
                    val dispatched = waSender.send(
                        taskId = task.id,
                        channel = task.channel,
                        recipientPhone = statusTarget,
                        messageText = task.messageText,
                        shouldRelock = false,
                        mediaUri = task.mediaUri
                    )
                    if (dispatched) {
                        success = waitForWhatsAppCompletion(25000L)
                    }
                } else {
                    var allRecipientsSuccess = true
                    for (i in task.recipients.indices) {
                        val phone = task.recipients[i]
                        val name = if (i in task.recipientNames.indices) task.recipientNames[i] else "Friend"
                        val personalizedMsg = personalize(task.messageText, name, phone)

                        AutoTextAccessibilityService.clearPending()
                        AutoTextAccessibilityService.markDispatchPending()

                        val dispatched = waSender.send(
                            taskId = task.id,
                            channel = task.channel,
                            recipientPhone = phone,
                            messageText = personalizedMsg,
                            shouldRelock = false,
                            mediaUri = task.mediaUri
                        )

                        if (!dispatched || !waitForWhatsAppCompletion(25000L)) {
                            allRecipientsSuccess = false
                            AppFileLogger.w(appContext, TAG, "Failed sending WhatsApp message to $phone (Attempt $attempts)")
                        }
                    }
                    success = allRecipientsSuccess
                }

                if (!success && attempts <= maxRetries) {
                    AppFileLogger.action(appContext, TAG, "Retrying WhatsApp task #${task.id} in 3 seconds (Attempt $attempts of $maxRetries)...")
                    delay(3000.milliseconds)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing WhatsApp task #${task.id}", e)
                AppFileLogger.e(appContext, TAG, "WhatsApp execution exception: ${e.message}", e)
                if (attempts <= maxRetries) delay(2000.milliseconds)
            }
        }

        val newStatus = if (success) TaskStatus.DONE else TaskStatus.FAILED
        repository.updateTaskStatus(task.id, newStatus)
        return success
    }

    private suspend fun waitForWhatsAppCompletion(maxWaitMs: Long): Boolean {
        var waitedMs = 0L
        while (AutoTextAccessibilityService.lastSendResult == AutoTextAccessibilityService.RESULT_PENDING && waitedMs < maxWaitMs) {
            delay(500.milliseconds)
            waitedMs += 500L
        }
        return AutoTextAccessibilityService.lastSendResult == AutoTextAccessibilityService.RESULT_SUCCESS
    }

    private suspend fun executeSmsTask(repository: AppRepository, task: ScheduledTask): Boolean {
        val smsSender = SmsSender(appContext)
        var allSuccess = true

        for (i in task.recipients.indices) {
            val phone = task.recipients[i]
            val name = if (i in task.recipientNames.indices) task.recipientNames[i] else "Friend"
            val personalizedMsg = personalize(task.messageText, name, phone)

            val sendResult = smsSender.sendSmsWithResult(phone, personalizedMsg, task.simSlot, enforceQuota = true)
            val isQuota = sendResult.reason.contains("limit", ignoreCase = true) || sendResult.reason.contains("quota", ignoreCase = true)
            val logStatus = if (sendResult.success) "SUCCESS" else if (isQuota) "QUEUED" else "FAILED"
            val logDetails = if (sendResult.success) "Sent via SIM slot ${task.simSlot}, parts: ${sendResult.segments}" else sendResult.reason

            repository.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = "SMS",
                    recipient = phone,
                    messageSnippet = personalizedMsg.take(160),
                    status = logStatus,
                    details = logDetails
                )
            )

            if (!sendResult.success) allSuccess = false
            if (isQuota) {
                AppFileLogger.w(appContext, TAG, "Task #${task.id} paused due to SMS quota: ${sendResult.reason}")
                break
            }
            delay(100.milliseconds)
        }

        val newStatus = if (allSuccess) TaskStatus.DONE else TaskStatus.FAILED
        repository.updateTaskStatus(task.id, newStatus)
        return allSuccess
    }

    private fun executeDirectTask(repository: AppRepository, task: ScheduledTask): Boolean {
        // Direct intent or alarm triggers
        repository.updateTaskStatus(task.id, TaskStatus.DONE)
        return true
    }

    private fun personalize(template: String, name: String, phone: String): String {
        return template
            .replace("{name}", name, ignoreCase = true)
            .replace("{phone}", phone, ignoreCase = true)
    }
}
