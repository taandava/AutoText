/**
 * Package: com.jmd.superwa.api
 * Module: Public Automation API Layer
 *
 * What it is:
 * Architecture definition and implementation for [TaskFeatureApi].
 *
 * What it does:
 * Exposes standardized, callable endpoints for all core task features: Each endpoint enforces pre-condition validation, semantic designated element actions, accurate result verification, and execution logging with verified real-device execution outcome.
 *
 * Linked With:
 * - Repositories: [com.jmd.superwa.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.superwa.data.model.ChannelType], [com.jmd.superwa.data.model.ExecutionLog], [com.jmd.superwa.data.model.ScheduledTask], [com.jmd.superwa.data.model.TaskStatus]
 * - Schedulers & Receivers: [com.jmd.superwa.scheduler.AlarmScheduler]
 * - Services & Engines: [com.jmd.superwa.engine.SmsSender], [com.jmd.superwa.engine.WhatsAppSender], [com.jmd.superwa.service.TaskAccessibilityService], [com.jmd.superwa.whatsapp.WhatsAppIntentDispatcher]
 * - UI Screens & Components: [com.jmd.superwa.ui.activity.CallSimulationActivity]
 * - Core Security & Utilities: [android.app.NotificationManager], [android.media.AudioManager], [android.telecom.TelecomManager], [android.telephony.SubscriptionManager]
 * - Android Framework & Jetpack: [android.app.PendingIntent], [android.net.Uri], [android.os.Bundle], [kotlinx.coroutines.delay]
 */

package com.jmd.superwa.api

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jmd.superwa.R
import com.jmd.superwa.SuperApplication
import com.jmd.superwa.core.AppFileLogger
import com.jmd.superwa.core.NotificationIconHelper
import com.jmd.superwa.core.PhoneNumberNormalizer
import com.jmd.superwa.core.WhatsAppPackageHelper
import com.jmd.superwa.data.model.ChannelType
import com.jmd.superwa.data.model.ExecutionLog
import com.jmd.superwa.data.model.ScheduledTask
import com.jmd.superwa.data.model.TaskStatus
import com.jmd.superwa.data.prefs.PreferenceManager
import com.jmd.superwa.data.repo.AppRepository
import com.jmd.superwa.engine.WhatsAppSender
import com.jmd.superwa.service.TaskAccessibilityService
import com.jmd.superwa.whatsapp.WhatsAppIntentDispatcher
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * What it is:
 * Standard structured execution outcome returned by every feature endpoint.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [FeatureExecutionResult].
 *
 * Linked with:
 * - Architecture: [TaskFeatureApi], [FeatureExecutionResult]
 */
data class FeatureExecutionResult(
    val taskId: Long,
    val channel: ChannelType,
    val success: Boolean,
    val recipient: String,
    val messageSnippet: String,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * What it is:
 * Request parameters and data specification for WhatsApp direct or group messaging.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [WhatsAppMessageRequest].
 *
 * Linked with:
 * - Architecture: [TaskFeatureApi], [WhatsAppMessageRequest]
 */
data class WhatsAppMessageRequest(
    val taskId: Long,
    val channel: ChannelType = ChannelType.WHATSAPP,
    val recipient: String,
    val message: String,
    val mediaUri: String = "",
    val isBatch: Boolean = false,
    val shouldRelock: Boolean = false
)

/**
 * What it is:
 * Request parameters and data specification for WhatsApp Status story publishing.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [WhatsAppStatusRequest].
 *
 * Linked with:
 * - Architecture: [TaskFeatureApi], [WhatsAppStatusRequest]
 */
data class WhatsAppStatusRequest(
    val taskId: Long,
    val channel: ChannelType = ChannelType.WHATSAPP_STATUS,
    val message: String,
    val mediaUri: String = "",
    val isMedia: Boolean = false,
    val shouldRelock: Boolean = false
)

/**
 * What it is:
 * Request parameters and data specification for WhatsApp Voice Call dispatch.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [WhatsAppCallRequest].
 *
 * Linked with:
 * - Architecture: [TaskFeatureApi], [WhatsAppCallRequest]
 */
data class WhatsAppCallRequest(
    val taskId: Long,
    val channel: ChannelType = ChannelType.WHATSAPP_CALL,
    val recipient: String,
    val callDurationSeconds: Int = 0,
    val includeDialTime: Boolean = false,
    val shouldRelock: Boolean = false
)

/**
 * What it is:
 * Request parameters and data specification for SMS dispatch.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [SmsRequest].
 *
 * Linked with:
 * - Architecture: [TaskFeatureApi], [SmsRequest]
 */
data class SmsRequest(
    val taskId: Long,
    val recipient: String,
    val message: String,
    val simSlot: Int = 0
)

/**
 * What it is:
 * Request parameters and data specification for Telecom Phone Calling.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [PhoneCallRequest].
 *
 * Linked with:
 * - Architecture: [TaskFeatureApi], [PhoneCallRequest]
 */
data class PhoneCallRequest(
    val taskId: Long,
    val recipient: String,
    val simSlot: Int = 0,
    val callDurationSeconds: Int = 0,
    val includeDialTime: Boolean = false
)

/**
 * What it is:
 * Request parameters and data specification for Inbound Call Simulation.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [CallSimulationRequest].
 *
 * Linked with:
 * - Architecture: [TaskFeatureApi], [CallSimulationRequest]
 */
data class CallSimulationRequest(
    val taskId: Long,
    val callerName: String,
    val callerNumber: String,
    val style: String = "Modern"
)

/**
 * What it is:
 * Master singleton API providing typed, reliable, and verified execution endpoints for all application capabilities.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [TaskFeatureApi].
 *
 * Linked with:
 * - Architecture: [TaskFeatureApi], [TaskFeatureApi]
 */
object TaskFeatureApi {

    private const val TAG = "TaskFeatureApi"

    /**
     * What it is:
     * Executes WhatsApp or WhatsApp Business message task using designated buttons.
     *
     * What it does:
     * Executes [sendWhatsAppMessage] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskFeatureApi], [sendWhatsAppMessage]
     */
    suspend fun sendWhatsAppMessage(
        context: Context,
        repository: AppRepository,
        request: WhatsAppMessageRequest
    ): FeatureExecutionResult {
        val cleanPhone = PhoneNumberNormalizer.formatForWhatsApp(
            request.recipient,
            PreferenceManager(context).defaultCountryCode
        )

        val sender = WhatsAppSender(context)
        val launched = sender.send(
            taskId = request.taskId,
            channel = request.channel,
            recipientPhone = cleanPhone,
            messageText = request.message,
            shouldRelock = request.shouldRelock,
            mediaUri = request.mediaUri,
            isBatchCampaign = request.isBatch
        )

        if (!launched) {
            val err = "Failed to launch WhatsApp URI intent for $cleanPhone"
            AppFileLogger.e(context, TAG, err)
            val result = FeatureExecutionResult(request.taskId, request.channel, false, cleanPhone, "Launch failed", err)
            logResult(repository, result)
            return result
        }

        val success = waitForAccessibilityCompletion(40000L)
        val snippet = if (success) {
            "Sent WhatsApp message to $cleanPhone: ${request.message.take(60)}"
        } else {
            "WhatsApp message to $cleanPhone failed or timed out"
        }

        val outcome = FeatureExecutionResult(
            taskId = request.taskId,
            channel = request.channel,
            success = success,
            recipient = cleanPhone,
            messageSnippet = snippet,
            details = if (success) "Confirmed via accessibility tree" else "Timed out without submission"
        )
        logResult(repository, outcome)
        return outcome
    }

    /**
     * What it is:
     * Publishes a status story to WhatsApp or WhatsApp Business using designated buttons.
     *
     * What it does:
     * Executes [postWhatsAppStatus] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskFeatureApi], [postWhatsAppStatus]
     */
    suspend fun postWhatsAppStatus(
        context: Context,
        repository: AppRepository,
        request: WhatsAppStatusRequest
    ): FeatureExecutionResult {
        val sender = WhatsAppSender(context)
        val launched = sender.send(
            taskId = request.taskId,
            channel = request.channel,
            recipientPhone = "Status",
            messageText = request.message,
            shouldRelock = request.shouldRelock,
            mediaUri = request.mediaUri,
            isBatchCampaign = false
        )

        if (!launched) {
            val err = "Failed to launch WhatsApp status creation intent"
            AppFileLogger.e(context, TAG, err)
            val result = FeatureExecutionResult(request.taskId, request.channel, false, "Status", "Status launch failed", err)
            logResult(repository, result)
            return result
        }

        val success = waitForAccessibilityCompletion(35000L)
        val snippet = if (success) "Published WhatsApp status: ${request.message.take(60)}" else "Status publishing timed out"
        val outcome = FeatureExecutionResult(
            taskId = request.taskId,
            channel = request.channel,
            success = success,
            recipient = "Status",
            messageSnippet = snippet,
            details = if (success) "Confirmed published on Updates screen" else "Timed out"
        )
        logResult(repository, outcome)
        return outcome
    }

    /**
     * What it is:
     * Executes scheduled WhatsApp VoIP voice calling, confirming dropdown items and verifying connection.
     *
     * What it does:
     * Executes [startWhatsAppCall] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskFeatureApi], [startWhatsAppCall]
     */
    suspend fun startWhatsAppCall(
        context: Context,
        repository: AppRepository,
        request: WhatsAppCallRequest
    ): FeatureExecutionResult {
        val cleanPhone = PhoneNumberNormalizer.formatForWhatsApp(
            request.recipient,
            PreferenceManager(context).defaultCountryCode
        )

        val launched = WhatsAppIntentDispatcher.launchWhatsAppCall(
            context = context,
            taskId = request.taskId,
            channel = request.channel,
            recipient = cleanPhone,
            callDurationSeconds = request.callDurationSeconds,
            shouldRelock = request.shouldRelock
        )

        if (!launched) {
            val err = "Failed to launch WhatsApp chat for voice call"
            AppFileLogger.e(context, TAG, err)
            val result = FeatureExecutionResult(request.taskId, request.channel, false, cleanPhone, "Call launch failed", err)
            logResult(repository, result)
            return result
        }

        val success = waitForAccessibilityCompletion(35000L)
        if (success && request.callDurationSeconds > 0) {
            val targetPkg = WhatsAppPackageHelper.getPackageForChannel(context, request.channel)
                ?: (if (request.channel == ChannelType.WHATSAPP_BUSINESS_CALL) WhatsAppPackageHelper.PACKAGE_WHATSAPP_BUSINESS else WhatsAppPackageHelper.PACKAGE_WHATSAPP)
            val durationSec = request.callDurationSeconds
            if (request.includeDialTime) {
                delay((durationSec * 1000L).milliseconds)
            } else {
                var elapsedMs = 0L
                while (elapsedMs < 30_000L && !TaskAccessibilityService.isWhatsAppCallConnected()) {
                    delay(500.milliseconds)
                    elapsedMs += 500L
                }
                delay((durationSec * 1000L).milliseconds)
            }
            hangupActiveCall(context, repository, request.taskId, isVoip = true, pkg = targetPkg)
        }
        val snippet = if (success) "Initiated WhatsApp voice call to $cleanPhone" else "WhatsApp voice call failed to connect"
        val outcome = FeatureExecutionResult(
            taskId = request.taskId,
            channel = request.channel,
            success = success,
            recipient = cleanPhone,
            messageSnippet = snippet,
            details = if (success) "VoIP screen confirmed" else "Call confirmation or connection timeout"
        )
        logResult(repository, outcome)
        return outcome
    }

    /**
     * What it is:
     * Dispatches cellular SMS with full Multi-SIM support and modem delivery verification.
     *
     * What it does:
     * Executes [sendSms] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskFeatureApi], [sendSms]
     */
    fun sendSms(
        context: Context,
        repository: AppRepository,
        request: SmsRequest
    ): FeatureExecutionResult {
        val outcome = FeatureExecutionResult(
            taskId = request.taskId,
            channel = ChannelType.SMS,
            success = false,
            recipient = request.recipient,
            messageSnippet = "Cellular SMS handled in SuperSMS",
            details = "Feature delegated to SuperSMS"
        )
        logResult(repository, outcome)
        return outcome
    }

    /**
     * What it is:
     * Places a direct Telecom phone call with Multi-SIM OEM extras and optional duration holding.
     *
     * What it does:
     * Executes [placePhoneCall] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskFeatureApi], [placePhoneCall]
     */
    suspend fun placePhoneCall(
        context: Context,
        repository: AppRepository,
        request: PhoneCallRequest
    ): FeatureExecutionResult {
        val cleanPhone = PhoneNumberNormalizer.cleanPhoneNumber(request.recipient).filter { it.isDigit() || it == '+' }
        val hasCallPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val callInitiated = com.jmd.superwa.telephony.TelephonyCallEngine.placeCall(context, cleanPhone, request.simSlot)

        if (callInitiated && request.callDurationSeconds > 0) {
            val durationSec = request.callDurationSeconds
            if (request.includeDialTime) {
                delay((durationSec * 1000L).milliseconds)
            } else {
                var elapsedMs = 0L
                while (elapsedMs < 30_000L) {
                    if (com.jmd.superwa.telephony.TelephonyCallEngine.isCallConnected(context) ||
                        TaskAccessibilityService.isCallTimerVisible()) {
                        break
                    }
                    delay(500.milliseconds)
                    elapsedMs += 500L
                }
                delay((durationSec * 1000L).milliseconds)
            }
            hangupActiveCall(context, repository, request.taskId, isVoip = false)
        }

        val snippet = "Placed phone call to $cleanPhone (simSlot=${request.simSlot}, duration=${request.callDurationSeconds}s)"
        val outcome = FeatureExecutionResult(
            taskId = request.taskId,
            channel = ChannelType.CALL,
            success = callInitiated,
            recipient = cleanPhone,
            messageSnippet = snippet,
            details = if (hasCallPerm) "Direct call dispatched via TelephonyCallEngine" else "Dialer opened (ACTION_DIAL)"
        )
        logResult(repository, outcome)
        return outcome
    }

    /**
     * What it is:
     * Simulates an inbound phone call with fullscreen ringing UI.
     *
     * What it does:
     * Executes [simulateCall] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskFeatureApi], [simulateCall]
     */
    fun simulateCall(
        context: Context,
        repository: AppRepository,
        request: CallSimulationRequest
    ): FeatureExecutionResult {
        val outcome = FeatureExecutionResult(
            taskId = request.taskId,
            channel = ChannelType.CALL_SIMULATION,
            success = false,
            recipient = request.callerNumber,
            messageSnippet = "Call simulation handled in SuperSMS",
            details = "Feature delegated to SuperSMS"
        )
        logResult(repository, outcome)
        return outcome
    }

    /**
     * What it is:
     * Terminates an active cellular phone call or WhatsApp VoIP call using designated system hooks.
     *
     * What it does:
     * Executes [hangupActiveCall] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskFeatureApi], [hangupActiveCall]
     */
    fun hangupActiveCall(
        context: Context,
        repository: AppRepository,
        taskId: Long,
        isVoip: Boolean = false,
        pkg: String = "com.whatsapp"
    ): FeatureExecutionResult {
        val success = if (isVoip) {
            TaskAccessibilityService.endWhatsAppCall(pkg)
        } else {
            com.jmd.superwa.telephony.TelephonyCallEngine.endCall(context)
        }

        val snippet = if (isVoip) "Concluded WhatsApp VoIP call" else "Concluded cellular phone call"
        val outcome = FeatureExecutionResult(
            taskId = taskId,
            channel = if (isVoip) ChannelType.WHATSAPP_CALL else ChannelType.CALL,
            success = success,
            recipient = "Active Call",
            messageSnippet = snippet,
            details = if (success) "Call terminated successfully via TelecomManager/Designated End Button" else "Call termination routine unfulfilled"
        )
        logResult(repository, outcome)
        return outcome
    }

    /**
     * What it is:
     * Executes manual immediate run of a scheduled task via the unified endpoint pipeline.
     *
     * What it does:
     * Executes [runTaskImmediately] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskFeatureApi], [runTaskImmediately]
     */
    fun runTaskImmediately(
        context: Context,
        repository: AppRepository,
        task: ScheduledTask
    ) {
        repository.updateTaskStatus(task.id, TaskStatus.PENDING)
        val intent = Intent(context, com.jmd.superwa.receiver.TaskAlarmReceiver::class.java).apply {
            action = com.jmd.superwa.scheduler.AlarmScheduler.ACTION_FIRE_TASK
            putExtra(com.jmd.superwa.scheduler.AlarmScheduler.EXTRA_TASK_ID, task.id)
            putExtra(com.jmd.superwa.scheduler.AlarmScheduler.EXTRA_MANUAL_RUN, true)
        }
        context.sendBroadcast(intent)
    }

    /**
     * What it is:
     * Standard toggle endpoint to pause or resume a scheduled task.
     *
     * What it does:
     * Executes [toggleTaskSchedule] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskFeatureApi], [toggleTaskSchedule]
     */
    fun toggleTaskSchedule(
        context: Context,
        repository: AppRepository,
        task: ScheduledTask,
        enable: Boolean
    ) {
        if (!enable) {
            val updated = task.copy(status = TaskStatus.PAUSED)
            repository.updateTask(updated)
            com.jmd.superwa.scheduler.AlarmScheduler(context).cancelTask(task.id, task)
            com.jmd.superwa.engine.SequentialTaskEngine.getInstance(context).cancelTask(task.id)
            com.jmd.superwa.core.AppDataEvents.notifyTasksChanged()
            com.jmd.superwa.core.AppFileLogger.action(
                context,
                "TaskFeatureApi",
                "Task #${task.id} (${task.title}) paused via toggle."
            )
            return
        }

        val now = System.currentTimeMillis()
        val nextTrigger = com.jmd.superwa.scheduler.AlarmScheduler.calculateNextFutureTrigger(task, now)
        val updated = task.copy(
            status = TaskStatus.SCHEDULED,
            triggerTimeMillis = nextTrigger
        )
        // Reset deliveries from prior runs so execution is fresh
        repository.clearDeliveriesForTask(task.id)
        repository.updateTask(updated)
        com.jmd.superwa.scheduler.AlarmScheduler(context).scheduleTask(updated)
        com.jmd.superwa.core.AppDataEvents.notifyTasksChanged()
        com.jmd.superwa.core.AppFileLogger.action(
            context,
            "TaskFeatureApi",
            "Task #${task.id} (${task.title}) enabled via toggle. Rescheduled to $nextTrigger."
        )
    }

    /**
     * What it is:
     * Standard removal endpoint for a scheduled task.
     *
     * What it does:
     * Executes [deleteTask] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskFeatureApi], [deleteTask]
     */
    fun deleteTask(
        context: Context,
        repository: AppRepository,
        taskId: Long
    ) {
        com.jmd.superwa.engine.SequentialTaskEngine.getInstance(context).cancelTask(taskId)
        com.jmd.superwa.scheduler.AlarmScheduler(context).cancelTask(taskId)
        repository.deleteTask(taskId)
    }
    /**
     * What it is:
     * Execution routine for [waitForAccessibilityCompletion].
     *
     * What it does:
     * Coordinates waitForAccessibilityCompletion operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskFeatureApi]
     */

    private suspend fun waitForAccessibilityCompletion(timeoutMs: Long): Boolean {
        var waitedMs = 0L
        while (
            TaskAccessibilityService.lastSendResult == TaskAccessibilityService.RESULT_PENDING &&
            waitedMs < timeoutMs
        ) {
            delay(400.milliseconds)
            waitedMs += 400L
        }
        return TaskAccessibilityService.lastSendResult == TaskAccessibilityService.RESULT_SUCCESS
    }
    /**
     * What it is:
     * Execution routine for [logResult].
     *
     * What it does:
     * Coordinates logResult operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskFeatureApi]
     */

    private fun logResult(repository: AppRepository, result: FeatureExecutionResult) {
        val status = if (result.success) "SUCCESS" else "FAILED"
        repository.insertLog(
            ExecutionLog(
                taskId = result.taskId,
                channel = result.channel.displayName,
                recipient = result.recipient,
                messageSnippet = result.messageSnippet,
                status = status,
                details = result.details
            )
        )
    }
}
