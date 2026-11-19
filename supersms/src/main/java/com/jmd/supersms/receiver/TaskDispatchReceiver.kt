package com.jmd.supersms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmd.supersms.api.CallRequest
import com.jmd.supersms.api.CallSimulationRequest
import com.jmd.supersms.api.SmsRequest
import com.jmd.supersms.api.TaskFeatureApi
import com.jmd.supersms.core.AppFileLogger
import com.jmd.supersms.core.AppVariantHelper
import com.jmd.supersms.data.prefs.PreferenceManager
import com.jmd.supersms.data.repo.AppRepository
import com.jmd.supersms.telephony.TelephonyCallEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TaskDispatchReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskDispatchReceiver"

        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_PHONE = "phone"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SIM_SLOT = "sim_slot"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_INCLUDE_DIAL_TIME = "include_dial_time"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_NUMBER = "caller_number"
        const val EXTRA_SKIN = "skin"
        const val EXTRA_TIMEOUT_SECONDS = "timeout_seconds"
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Task intent received: $action")
        AppFileLogger.action(context, TAG, "Task intent received: $action")

        val repository = AppRepository(context.applicationContext)

        when {
            // SYNC POOL
            action.endsWith("ACTION_SYNC_POOL") -> {
                receiverScope.launch {
                    val summary = repository.syncWhatsAppPool()
                    val msg = "Pool synced: +${summary.newAdded} new entries (${summary.totalPoolCount} total)"
                    Log.i(TAG, msg)
                    AppFileLogger.i(context, TAG, msg)
                    setResult(1, msg, null)
                }
            }

            // DISPATCH TASK
            action.endsWith("ACTION_DISPATCH_TASK") -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId <= 0L) {
                    Log.w(TAG, "Invalid task ID: $taskId")
                    setResult(0, "Invalid task ID: $taskId", null)
                    return
                }

                receiverScope.launch {
                    val task = repository.getTaskById(taskId)
                    if (task == null) {
                        Log.w(TAG, "Task #$taskId not found")
                        setResult(0, "Task not found: $taskId", null)
                        return@launch
                    }
                    TaskFeatureApi.runTaskImmediately(context, repository, task)
                    setResult(1, "Task #${task.id} triggered", null)
                }
            }

            // SEND SMS (SuperSMS)
            action.endsWith("ACTION_SEND_SMS") -> {
                val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty().trim()
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                val simSlot = intent.getIntExtra(EXTRA_SIM_SLOT, 0)

                if (phone.isBlank() || message.isBlank()) {
                    setResult(0, "Missing phone or message text", null)
                    return
                }

                receiverScope.launch {
                    val request = SmsRequest(
                        taskId = System.currentTimeMillis(),
                        recipient = phone,
                        message = message,
                        simSlot = simSlot
                    )
                    val result = TaskFeatureApi.sendSms(context, repository, request)
                    setResult(if (result.success) 1 else 0, result.messageSnippet, null)
                }
            }

            // MAKE CALL (SuperSMS)
            action.endsWith("ACTION_MAKE_CALL") -> {
                val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty().trim()
                val duration = intent.getIntExtra(EXTRA_DURATION, 10)
                val simSlot = intent.getIntExtra(EXTRA_SIM_SLOT, 0)
                val includeDialTime = intent.getBooleanExtra(EXTRA_INCLUDE_DIAL_TIME, true)

                if (phone.isBlank()) {
                    setResult(0, "Missing phone number for call", null)
                    return
                }

                receiverScope.launch {
                    val request = CallRequest(
                        taskId = System.currentTimeMillis(),
                        recipient = phone,
                        simSlot = simSlot,
                        callDurationSeconds = duration,
                        includeDialTime = includeDialTime
                    )
                    val result = TaskFeatureApi.placePhoneCall(context, repository, request)
                    setResult(if (result.success) 1 else 0, result.messageSnippet, null)
                }
            }

            // SIMULATE CALL (SuperSMS)
            action.endsWith("ACTION_SIMULATE_CALL") -> {
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Office / Boss"
                val callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: "+91 98765 43210"
                val skin = intent.getStringExtra(EXTRA_SKIN) ?: "PIXEL"
                val timeoutSeconds = intent.getIntExtra(EXTRA_TIMEOUT_SECONDS, 30)

                val request = CallSimulationRequest(
                    taskId = System.currentTimeMillis(),
                    callerName = callerName,
                    callerNumber = callerNumber,
                    screenSkin = skin,
                    ringingTimeoutSeconds = timeoutSeconds
                )
                val result = TaskFeatureApi.simulateCall(context, repository, request)
                setResult(if (result.success) 1 else 0, result.messageSnippet, null)
            }

            // GET STATUS
            action.endsWith("ACTION_GET_STATUS") -> {
                receiverScope.launch {
                    val variant = AppVariantHelper.currentVariant
                    val poolCount = repository.countDynamicPool()
                    val diag = TaskFeatureApi.getDiagnosticStatus(context)
                    val statusMsg = "App: $variant | poolCount=$poolCount | diag=$diag"
                    Log.i(TAG, statusMsg)
                    setResult(1, statusMsg, null)
                }
            }
        }
    }
}
