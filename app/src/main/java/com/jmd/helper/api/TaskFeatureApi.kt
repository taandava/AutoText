/**
 * Package: com.jmd.helper.api
 * Module: Public Automation API Layer
 *
 * What it is:
 * Architecture definition and implementation for [TaskFeatureApi].
 *
 * What it does:
 * Exposes standardized, callable endpoints for all core task features: Each endpoint enforces pre-condition validation, semantic designated element actions, accurate result verification, and room execution logging with verified real-device execution outcome.
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.helper.data.model.ChannelType], [com.jmd.helper.data.model.ExecutionLog], [com.jmd.helper.data.model.ScheduledTask], [com.jmd.helper.data.model.TaskStatus]
 * - Schedulers & Receivers: [com.jmd.helper.ui.activity.ReminderAlarmActivity]
 * - Services & Engines: [com.jmd.helper.engine.SmsSender], [com.jmd.helper.engine.WhatsAppSender], [com.jmd.helper.service.TaskAccessibilityService], [com.jmd.helper.whatsapp.WhatsAppIntentDispatcher]
 * - UI Screens & Components: [com.jmd.helper.ui.activity.CallSimulationActivity]
 * - Core Security & Utilities: [android.app.NotificationManager], [android.media.AudioManager], [android.telecom.TelecomManager], [android.telephony.SubscriptionManager]
 * - Android Framework & Jetpack: [android.app.PendingIntent], [android.net.Uri], [android.os.Bundle], [kotlinx.coroutines.delay]
 */

package com.jmd.helper.api

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
import com.jmd.helper.HelperApplication
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.core.OemBrandHelper
import com.jmd.helper.core.PhoneNumberNormalizer
import com.jmd.helper.core.WhatsAppPackageHelper
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.ExecutionLog
import com.jmd.helper.data.model.ScheduledTask
import com.jmd.helper.data.model.TaskStatus
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.engine.SmsSender
import com.jmd.helper.engine.WhatsAppSender
import com.jmd.helper.service.TaskAccessibilityService
import com.jmd.helper.ui.activity.CallSimulationActivity
import com.jmd.helper.ui.activity.ReminderAlarmActivity
import com.jmd.helper.whatsapp.WhatsAppIntentDispatcher
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
 * Request payload for WhatsApp direct or group messaging.
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
 * Request payload for WhatsApp Status story publishing.
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
 * Request payload for WhatsApp Voice Call dispatch.
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
 * Request payload for SMS dispatch.
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
 * Request payload for Telecom Phone Calling.
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
 * Request payload for Interactive Reminder Alarms.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [ReminderRequest].
 *
 * Linked with:
 * - Architecture: [TaskFeatureApi], [ReminderRequest]
 */
data class ReminderRequest(
    val taskId: Long,
    val title: String,
    val message: String,
    val ttsEnabled: Boolean = true
)

/**
 * What it is:
 * Request payload for Inbound Call Simulation.
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
 * Master singleton API providing typed, reliable, and audited execution endpoints for all application capabilities.
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
        val smsSender = SmsSender(context)
        val sendResult = smsSender.sendSmsWithResult(
            recipientPhone = request.recipient,
            messageText = request.message,
            simSlot = request.simSlot,
            enforceQuota = true
        )

        val snippet = if (sendResult.success) {
            "Sent SMS to ${request.recipient}: ${request.message.take(50)}"
        } else {
            "Failed SMS to ${request.recipient}: ${sendResult.reason}"
        }

        val outcome = FeatureExecutionResult(
            taskId = request.taskId,
            channel = ChannelType.SMS,
            success = sendResult.success,
            recipient = request.recipient,
            messageSnippet = snippet,
            details = "Modem status: ${sendResult.deliveryStatus}, parts=${sendResult.segments}"
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
        val uri = Uri.fromParts("tel", cleanPhone, null)
        val hasCallPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

        val extras = Bundle()
        if (request.simSlot == 1 || request.simSlot == 2) {
            val targetPhysicalSlot = request.simSlot - 1
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val targetSub = runCatching {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    subManager?.activeSubscriptionInfoList?.find { it.simSlotIndex == targetPhysicalSlot }
                } else null
            }.getOrNull()

            OemBrandHelper.populateTelecomSimExtras(extras, targetPhysicalSlot, targetSub?.subscriptionId)
        }

        val callInitiated = if (hasCallPerm && telecomManager != null) {
            val placed = runCatching {
                telecomManager.placeCall(uri, extras)
                true
            }.onFailure { e ->
                Log.w(TAG, "telecomManager.placeCall failed, falling back to Intent: ${e.message}")
            }.getOrDefault(false)

            if (!placed) {
                val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtras(extras)
                }
                runCatching {
                    context.startActivity(callIntent)
                    true
                }.getOrDefault(false)
            } else {
                true
            }
        } else {
            val callIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            runCatching {
                context.startActivity(callIntent)
                true
            }.getOrDefault(false)
        }

        if (callInitiated && request.callDurationSeconds > 0) {
            val durationSec = request.callDurationSeconds
            if (request.includeDialTime) {
                delay((durationSec * 1000L).milliseconds)
            } else {
                var elapsedMs = 0L
                while (elapsedMs < 30_000L && !TaskAccessibilityService.isPhoneCallUiActive()) {
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
            details = if (hasCallPerm) "Direct call dispatched via TelecomManager/ACTION_CALL" else "Dialer opened (ACTION_DIAL)"
        )
        logResult(repository, outcome)
        return outcome
    }

    /**
     * What it is:
     * Triggers high-priority full-screen interactive reminder alarm.
     *
     * What it does:
     * Executes [triggerReminder] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskFeatureApi], [triggerReminder]
     */
    fun triggerReminder(
        context: Context,
        repository: AppRepository,
        request: ReminderRequest
    ): FeatureExecutionResult {
        val success = try {
            val notifId = (request.taskId + 60000).toInt()
            val reminderIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("title", request.title.ifBlank { "Helper Reminder" })
                putExtra("message", request.message)
                putExtra("task_id", request.taskId)
                putExtra("notif_id", notifId)
                putExtra("tts_enabled", request.ttsEnabled)
            }

            runCatching {
                val fullScreenPendingIntent = PendingIntent.getActivity(
                    context,
                    notifId,
                    reminderIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val notif = NotificationCompat.Builder(context, HelperApplication.CHANNEL_REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(request.title.ifBlank { "Reminder" })
                    .setContentText(request.message)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setAutoCancel(true)
                    .build()
                nm?.notify(notifId, notif)
            }

            context.startActivity(reminderIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "triggerReminder failed: ${e.message}", e)
            false
        }

        val snippet = if (success) "Triggered reminder: ${request.title}" else "Failed triggering reminder"
        val outcome = FeatureExecutionResult(
            taskId = request.taskId,
            channel = ChannelType.REMINDER,
            success = success,
            recipient = "Local Device",
            messageSnippet = snippet
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
        val success = try {
            val notifId = (request.taskId + 70000).toInt()
            val simulationIntent = Intent(context, CallSimulationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("caller_name", request.callerName.ifBlank { "Incoming Call" })
                putExtra("caller_number", request.callerNumber.ifBlank { "+91 98765 43210" })
                putExtra("caller_style", request.style.ifBlank { "Modern" })
                putExtra("task_id", request.taskId)
                putExtra("notif_id", notifId)
            }

            runCatching {
                val fullScreenPendingIntent = PendingIntent.getActivity(
                    context,
                    notifId,
                    simulationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val callNotif = NotificationCompat.Builder(context, HelperApplication.CHANNEL_REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_menu_call)
                    .setContentTitle(request.callerName.ifBlank { "Incoming Call" })
                    .setContentText("Incoming call from ${request.callerNumber.ifBlank { "Unknown Caller" }}")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setAutoCancel(true)
                    .build()
                nm?.notify(notifId, callNotif)
            }

            context.startActivity(simulationIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "simulateCall failed: ${e.message}", e)
            false
        }

        val snippet = if (success) "Incoming simulated call from ${request.callerName}" else "Call simulation failed"
        val outcome = FeatureExecutionResult(
            taskId = request.taskId,
            channel = ChannelType.CALL_SIMULATION,
            success = success,
            recipient = request.callerNumber,
            messageSnippet = snippet
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
            TaskAccessibilityService.endPhoneCall(context)
        }

        val snippet = if (isVoip) "Concluded WhatsApp VoIP call" else "Concluded cellular phone call"
        val outcome = FeatureExecutionResult(
            taskId = taskId,
            channel = if (isVoip) ChannelType.WHATSAPP_CALL else ChannelType.CALL,
            success = success,
            recipient = "Active Call",
            messageSnippet = snippet,
            details = if (success) "Call terminated successfully via TelecomManager/Designated End Button" else "Call termination hook executed"
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
        val intent = Intent(context, com.jmd.helper.receiver.TaskAlarmReceiver::class.java).apply {
            action = com.jmd.helper.scheduler.AlarmScheduler.ACTION_FIRE_TASK
            putExtra(com.jmd.helper.scheduler.AlarmScheduler.EXTRA_TASK_ID, task.id)
            putExtra(com.jmd.helper.scheduler.AlarmScheduler.EXTRA_MANUAL_RUN, true)
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
        val newStatus = if (enable) TaskStatus.SCHEDULED else TaskStatus.PAUSED
        val updated = task.copy(status = newStatus)
        repository.updateTask(updated)
        if (enable) {
            com.jmd.helper.scheduler.AlarmScheduler(context).scheduleTask(updated)
        } else {
            com.jmd.helper.scheduler.AlarmScheduler(context).cancelTask(task.id)
            com.jmd.helper.engine.SequentialTaskEngine.getInstance(context).cancelTask(task.id)
        }
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
        com.jmd.helper.engine.SequentialTaskEngine.getInstance(context).cancelTask(taskId)
        com.jmd.helper.scheduler.AlarmScheduler(context).cancelTask(taskId)
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
