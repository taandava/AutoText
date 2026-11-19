package com.jmd.supersms.api

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jmd.supersms.R
import com.jmd.supersms.SuperApplication
import com.jmd.supersms.core.AppFileLogger
import com.jmd.supersms.core.NotificationIconHelper
import com.jmd.supersms.core.PhoneNumberNormalizer
import com.jmd.supersms.data.model.ChannelType
import com.jmd.supersms.data.model.ExecutionLog
import com.jmd.supersms.data.model.ScheduledTask
import com.jmd.supersms.data.model.TaskStatus
import com.jmd.supersms.data.prefs.PreferenceManager
import com.jmd.supersms.data.repo.AppRepository
import com.jmd.supersms.engine.SmsSender
import com.jmd.supersms.ui.activity.CallSimulationActivity
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

data class FeatureExecutionResult(
    val taskId: Long,
    val channel: ChannelType,
    val success: Boolean,
    val recipient: String,
    val messageSnippet: String,
    val details: String = "",
    val error: String? = null
)

data class SmsRequest(
    val taskId: Long = System.currentTimeMillis(),
    val recipient: String,
    val message: String,
    val simSlot: Int = 0
)

data class CallRequest(
    val taskId: Long = System.currentTimeMillis(),
    val recipient: String,
    val simSlot: Int = 0,
    val callDurationSeconds: Int = 0,
    val includeDialTime: Boolean = true
)

data class CallSimulationRequest(
    val taskId: Long = System.currentTimeMillis(),
    val callerName: String = "Boss / Office",
    val callerNumber: String = "+91 98765 43210",
    val screenSkin: String = "PIXEL",
    val ringingTimeoutSeconds: Int = 30
)

data class DailyCampaignRequest(
    val channel: ChannelType = ChannelType.SMS,
    val batchLimit: Int = 20,
    val simSlot: Int = 0
)

object TaskFeatureApi {

    private const val TAG = "TaskFeatureApi"

    suspend fun sendSms(
        context: Context,
        repository: AppRepository,
        request: SmsRequest
    ): FeatureExecutionResult {
        val cleanPhone = PhoneNumberNormalizer.normalize(request.recipient)
        val smsSender = SmsSender(context)
        val (success, segments, reason, _) = smsSender.sendSmsWithResult(
            recipientPhone = cleanPhone,
            messageText = request.message,
            simSlot = request.simSlot,
            enforceQuota = PreferenceManager(context).isSmsQuotaLimitEnabled,
            taskId = request.taskId
        )
        val snippet = if (request.message.length > 50) request.message.take(50) + "..." else request.message
        val result = FeatureExecutionResult(
            taskId = request.taskId,
            channel = ChannelType.SMS,
            success = success,
            recipient = cleanPhone,
            messageSnippet = snippet,
            details = if (success) "Dispatched via SIM ${request.simSlot} (parts: $segments)" else reason
        )
        logResult(repository, result)
        return result
    }

    suspend fun placePhoneCall(
        context: Context,
        repository: AppRepository,
        request: CallRequest
    ): FeatureExecutionResult {
        val cleanPhone = PhoneNumberNormalizer.normalize(request.recipient)
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            val err = "CALL_PHONE permission not granted"
            AppFileLogger.e(context, TAG, err)
            val result = FeatureExecutionResult(request.taskId, ChannelType.CALL, false, cleanPhone, "Call to $cleanPhone", err)
            logResult(repository, result)
            return result
        }

        return try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanPhone")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.simSlot > 0) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subInfoList = subManager?.activeSubscriptionInfoList
                val targetSub = subInfoList?.firstOrNull { it.simSlotIndex == request.simSlot - 1 }
                if (targetSub != null && telecomManager != null) {
                    val accounts = telecomManager.callCapablePhoneAccounts
                    val phoneAccountHandle = accounts.firstOrNull { it.id.contains(targetSub.subscriptionId.toString()) }
                    if (phoneAccountHandle != null) {
                        callIntent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                    }
                }
            }

            context.startActivity(callIntent)

            if (request.callDurationSeconds > 0) {
                val durationSec = request.callDurationSeconds
                if (request.includeDialTime) {
                    delay((durationSec * 1000L).milliseconds)
                } else {
                    var elapsedMs = 0L
                    while (elapsedMs < 30_000L && !isCallActive(context)) {
                        delay(500.milliseconds)
                        elapsedMs += 500L
                    }
                    delay((durationSec * 1000L).milliseconds)
                }
                hangupActiveCall(context, repository, request.taskId, isVoip = false)
            }

            val outcome = FeatureExecutionResult(
                taskId = request.taskId,
                channel = ChannelType.CALL,
                success = true,
                recipient = cleanPhone,
                messageSnippet = "Initiated cellular voice call to $cleanPhone",
                details = "Dialed via SIM ${request.simSlot}"
            )
            logResult(repository, outcome)
            outcome
        } catch (e: Exception) {
            val err = "Dialer launch exception: ${e.message}"
            AppFileLogger.e(context, TAG, err, e)
            val result = FeatureExecutionResult(request.taskId, ChannelType.CALL, false, cleanPhone, "Call to $cleanPhone", err)
            logResult(repository, result)
            result
        }
    }

    fun simulateCall(
        context: Context,
        repository: AppRepository,
        request: CallSimulationRequest
    ): FeatureExecutionResult {
        return try {
            val intent = Intent(context, CallSimulationActivity::class.java).apply {
                putExtra(CallSimulationActivity.EXTRA_CALLER_NAME, request.callerName)
                putExtra(CallSimulationActivity.EXTRA_CALLER_NUMBER, request.callerNumber)
                putExtra(CallSimulationActivity.EXTRA_SKIN, request.screenSkin)
                putExtra(CallSimulationActivity.EXTRA_TIMEOUT_SECONDS, request.ringingTimeoutSeconds)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                request.taskId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nm = context.getSystemService(NotificationManager::class.java)
            val notif = NotificationCompat.Builder(context, SuperApplication.CHANNEL_TASKS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Incoming call: ${request.callerName}")
                .setContentText(request.callerNumber)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build()

            nm?.notify(request.taskId.toInt(), notif)
            context.startActivity(intent)

            val outcome = FeatureExecutionResult(
                taskId = request.taskId,
                channel = ChannelType.CALL_SIMULATION,
                success = true,
                recipient = request.callerNumber,
                messageSnippet = "Call Simulation: ${request.callerName}",
                details = "Triggered ${request.screenSkin} skin (${request.ringingTimeoutSeconds}s timeout)"
            )
            logResult(repository, outcome)
            outcome
        } catch (e: Exception) {
            val err = "Call simulation failed: ${e.message}"
            AppFileLogger.e(context, TAG, err, e)
            val result = FeatureExecutionResult(request.taskId, ChannelType.CALL_SIMULATION, false, request.callerNumber, "Call simulation failed", err)
            logResult(repository, result)
            result
        }
    }

    suspend fun triggerDailyCampaign(
        context: Context,
        repository: AppRepository,
        request: DailyCampaignRequest
    ): FeatureExecutionResult {
        return try {
            val eligible = repository.getDynamicPool(limit = request.batchLimit)
            var dispatched = 0

            for (contact in eligible) {
                if (contact.phone.isNotBlank()) {
                    val res = sendSms(
                        context,
                        repository,
                        SmsRequest(
                            recipient = contact.phone,
                            message = "Hello ${contact.name}, this is an outreach message.",
                            simSlot = request.simSlot
                        )
                    )
                    if (res.success) dispatched++
                    delay(500.milliseconds)
                }
            }

            FeatureExecutionResult(
                taskId = System.currentTimeMillis(),
                channel = ChannelType.SMS,
                success = true,
                recipient = "Batch (${eligible.size})",
                messageSnippet = "Dispatched $dispatched/${eligible.size} campaign messages",
                details = "Completed outreach batch"
            )
        } catch (e: Exception) {
            FeatureExecutionResult(
                taskId = System.currentTimeMillis(),
                channel = ChannelType.SMS,
                success = false,
                recipient = "Batch",
                messageSnippet = "Campaign failed",
                details = e.message ?: "Unknown error"
            )
        }
    }

    fun runTaskImmediately(
        context: Context,
        repository: AppRepository,
        task: ScheduledTask
    ) {
        repository.updateTaskStatus(task.id, TaskStatus.PENDING)
        val intent = Intent(context, com.jmd.supersms.receiver.TaskAlarmReceiver::class.java).apply {
            action = com.jmd.supersms.scheduler.AlarmScheduler.ACTION_FIRE_TASK
            putExtra(com.jmd.supersms.scheduler.AlarmScheduler.EXTRA_TASK_ID, task.id)
            putExtra(com.jmd.supersms.scheduler.AlarmScheduler.EXTRA_MANUAL_RUN, true)
        }
        context.sendBroadcast(intent)
    }

    fun toggleTaskSchedule(
        context: Context,
        repository: AppRepository,
        task: ScheduledTask,
        enable: Boolean
    ) {
        if (!enable) {
            val updated = task.copy(status = TaskStatus.PAUSED)
            repository.updateTask(updated)
            com.jmd.supersms.scheduler.AlarmScheduler(context).cancelTask(task.id, task)
            com.jmd.supersms.engine.SequentialTaskEngine.getInstance(context).cancelTask(task.id)
            com.jmd.supersms.core.AppDataEvents.notifyTasksChanged()
            com.jmd.supersms.core.AppFileLogger.action(
                context,
                "TaskFeatureApi",
                "Task #${task.id} (${task.title}) paused via toggle."
            )
            return
        }

        val now = System.currentTimeMillis()
        val nextTrigger = com.jmd.supersms.scheduler.AlarmScheduler.calculateNextFutureTrigger(task, now)
        val updated = task.copy(
            status = TaskStatus.SCHEDULED,
            triggerTimeMillis = nextTrigger
        )
        repository.clearDeliveriesForTask(task.id)
        repository.updateTask(updated)
        com.jmd.supersms.scheduler.AlarmScheduler(context).scheduleTask(updated)
        com.jmd.supersms.core.AppDataEvents.notifyTasksChanged()
        com.jmd.supersms.core.AppFileLogger.action(
            context,
            "TaskFeatureApi",
            "Task #${task.id} (${task.title}) enabled via toggle. Rescheduled to $nextTrigger."
        )
    }

    fun deleteTask(
        context: Context,
        repository: AppRepository,
        taskId: Long
    ) {
        com.jmd.supersms.engine.SequentialTaskEngine.getInstance(context).cancelTask(taskId)
        com.jmd.supersms.scheduler.AlarmScheduler(context).cancelTask(taskId)
        repository.deleteTask(taskId)
    }

    fun getDiagnosticStatus(context: Context): Map<String, Any> {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        return mapOf(
            "hasSmsCapability" to (telephonyManager?.isSmsCapable ?: false),
            "simState" to (telephonyManager?.simState ?: -1),
            "networkOperatorName" to (telephonyManager?.networkOperatorName ?: "Unknown")
        )
    }

    private fun isCallActive(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        return tm?.callState == android.telephony.TelephonyManager.CALL_STATE_OFFHOOK
    }

    private fun hangupActiveCall(
        context: Context,
        repository: AppRepository,
        taskId: Long,
        isVoip: Boolean = false
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.endCall()
            }
        } catch (e: Exception) {
            Log.w(TAG, "End call attempt note: ${e.message}")
        }
    }

    private fun logResult(repository: AppRepository, result: FeatureExecutionResult) {
        try {
            repository.insertLog(
                ExecutionLog(
                    taskId = result.taskId,
                    channel = result.channel.name,
                    recipient = result.recipient,
                    messageSnippet = result.messageSnippet,
                    status = if (result.success) "SUCCESS" else "FAILED",
                    details = result.details
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log result: ${e.message}")
        }
    }
}
