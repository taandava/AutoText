/**
 * Package: com.jmd.superapp.receiver
 * Module: Task Dispatch & Headless CLI Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [TaskDispatchReceiver].
 *
 * What it does:
 * Exposes system-level endpoints callable via ADB CLI, scripts, and intent broadcasts:
 * - ACTION_HARVEST_WHATSAPP / ACTION_SYNC_CHATS: Triggers node-based WhatsApp / WA Business chat and group discovery.
 * - ACTION_SYNC_POOL: Triggers multi-source contact pool synchronization (Contacts, WhatsApp, WA Business).
 * - ACTION_DISPATCH_TASK: Executes a scheduled task immediately by ID.
 * - ACTION_SEND_WHATSAPP: Dispatches a WhatsApp direct or group message via TaskFeatureApi.
 * - ACTION_WHATSAPP_CALL: Dispatches a WhatsApp voice or video call via TaskFeatureApi.
 * - ACTION_POST_STATUS: Posts text or media status to WhatsApp / WA Business.
 * - ACTION_SEND_SMS: Dispatches a direct SMS via TaskFeatureApi.
 * - ACTION_MAKE_CALL: Dispatches a direct cellular phone call with duration enforcement.
 * - ACTION_SIMULATE_CALL: Dispatches simulated incoming call alert.
 * - ACTION_SET_BANKING_SHIELD: Dynamically toggles protection for sensitive applications.
 * - ACTION_GET_STATUS: Returns and logs real-time service and engine health metrics.
 * - ACTION_DEVICE_DIAGNOSTICS: Returns diagnostic JSON payload.
 *
 * Linked With:
 * - Repositories: [com.jmd.superapp.data.repo.AppRepository]
 * - Services: [com.jmd.superapp.service.TaskAccessibilityService]
 * - API: [com.jmd.superapp.api.TaskFeatureApi]
 * - Core Security & Utilities: [com.jmd.superapp.core.ProtectedApplicationManager], [com.jmd.superapp.core.AppFileLogger]
 */

package com.jmd.superapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.jmd.superapp.api.CallSimulationRequest
import com.jmd.superapp.api.PhoneCallRequest
import com.jmd.superapp.api.SmsRequest
import com.jmd.superapp.api.TaskFeatureApi
import com.jmd.superapp.api.WhatsAppCallRequest
import com.jmd.superapp.api.WhatsAppMessageRequest
import com.jmd.superapp.api.WhatsAppStatusRequest
import com.jmd.superapp.core.AccessibilityHealthManager
import com.jmd.superapp.core.AppFileLogger
import com.jmd.superapp.core.ProtectedApplicationManager
import com.jmd.superapp.core.WhatsAppPackageHelper
import com.jmd.superapp.data.model.ChannelType
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.data.repo.AppRepository
import com.jmd.superapp.service.TaskAccessibilityService
import com.jmd.superapp.whatsapp.WhatsAppChatScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class TaskDispatchReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskDispatchReceiver"

        const val ACTION_HARVEST_WHATSAPP = "com.jmd.superapp.ACTION_HARVEST_WHATSAPP"
        const val ACTION_SYNC_CHATS = "com.jmd.superapp.ACTION_SYNC_CHATS"
        const val ACTION_SYNC_POOL = "com.jmd.superapp.ACTION_SYNC_POOL"
        const val ACTION_DISPATCH_TASK = "com.jmd.superapp.ACTION_DISPATCH_TASK"
        const val ACTION_SEND_WHATSAPP = "com.jmd.superapp.ACTION_SEND_WHATSAPP"
        const val ACTION_WHATSAPP_CALL = "com.jmd.superapp.ACTION_WHATSAPP_CALL"
        const val ACTION_POST_STATUS = "com.jmd.superapp.ACTION_POST_STATUS"
        const val ACTION_SEND_SMS = "com.jmd.superapp.ACTION_SEND_SMS"
        const val ACTION_MAKE_CALL = "com.jmd.superapp.ACTION_MAKE_CALL"
        const val ACTION_SIMULATE_CALL = "com.jmd.superapp.ACTION_SIMULATE_CALL"
        const val ACTION_SET_BANKING_SHIELD = "com.jmd.superapp.ACTION_SET_BANKING_SHIELD"
        const val ACTION_GET_STATUS = "com.jmd.superapp.ACTION_GET_STATUS"
        const val ACTION_DEVICE_DIAGNOSTICS = "com.jmd.superapp.ACTION_DEVICE_DIAGNOSTICS"

        // Extra keys
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_TARGET = "target"
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
        const val EXTRA_IS_BUSINESS = "is_business"
        const val EXTRA_ENABLED = "enabled"
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Task intent received: $action")
        AppFileLogger.action(context, TAG, "Task intent received: $action")

        val repository = AppRepository(context.applicationContext)
        val prefs = PreferenceManager(context.applicationContext)

        when {
            action.endsWith("ACTION_HARVEST_WHATSAPP") || action.endsWith("ACTION_SYNC_CHATS") -> {
                val targetPkg = intent.getStringExtra(EXTRA_PACKAGE)
                    ?: WhatsAppPackageHelper.PACKAGE_WHATSAPP
                val channelStr = intent.getStringExtra(EXTRA_CHANNEL) ?: "WHATSAPP"
                val channel = try {
                    ChannelType.valueOf(channelStr)
                } catch (e: Exception) {
                    ChannelType.WHATSAPP
                }
                val targetStr = intent.getStringExtra(EXTRA_TARGET) ?: "ALL_CHATS"
                val scanTarget = when (targetStr.uppercase()) {
                    "GROUPS_ONLY" -> WhatsAppChatScanner.ScanTarget.GROUPS_ONLY
                    "DIRECT_CHATS_ONLY" -> WhatsAppChatScanner.ScanTarget.DIRECT_CHATS_ONLY
                    else -> WhatsAppChatScanner.ScanTarget.ALL_CHATS
                }

                TaskAccessibilityService.startWhatsAppChatScan(
                    context = context,
                    targetPackage = targetPkg,
                    channel = channel,
                    scanTarget = scanTarget
                )
                setResult(1, "WhatsApp sync initiated ($scanTarget)", null)
            }

            action.endsWith("ACTION_SYNC_POOL") -> {
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val summary = repository.syncWhatsAppPool()
                        val json = JSONObject().apply {
                            put("success", true)
                            put("new_added", summary.newAdded)
                            put("total_pool", summary.totalPoolCount)
                            put("contacts_scanned", summary.contactsScanned)
                            put("wa_chats_scanned", summary.waChatsScanned)
                            put("wa_biz_scanned", summary.waBusinessScanned)
                        }
                        val msg = json.toString()
                        Log.i(TAG, "Pool synced: $msg")
                        AppFileLogger.i(context, TAG, msg)
                        pendingResult.setResult(1, msg, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Pool sync error: ${e.message}")
                        pendingResult.setResult(0, e.message ?: "Failed", null)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

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

            action.endsWith("ACTION_SEND_WHATSAPP") -> {
                val rawPhone = intent.getStringExtra(EXTRA_PHONE).orEmpty().trim()
                val rawGroup = intent.getStringExtra("group").orEmpty().trim()
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                val isBusiness = intent.getBooleanExtra(EXTRA_IS_BUSINESS, false)

                val isGroup = rawGroup.isNotBlank() || rawPhone.startsWith("group:", ignoreCase = true)
                val recipient = when {
                    rawGroup.isNotBlank() -> "group:$rawGroup"
                    else -> rawPhone
                }

                if (recipient.isBlank()) {
                    setResult(0, "Missing recipient phone or group", null)
                    return
                }

                val ch = when {
                    isGroup && isBusiness -> ChannelType.WHATSAPP_BUSINESS_GROUP
                    isGroup -> ChannelType.WHATSAPP_GROUP
                    isBusiness -> ChannelType.WHATSAPP_BUSINESS
                    else -> ChannelType.WHATSAPP
                }

                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val request = WhatsAppMessageRequest(
                            taskId = System.currentTimeMillis(),
                            channel = ch,
                            recipient = recipient,
                            message = message
                        )
                        val result = TaskFeatureApi.sendWhatsAppMessage(context, repository, request)
                        pendingResult.setResult(if (result.success) 1 else 0, result.messageSnippet, null)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            action.endsWith("ACTION_WHATSAPP_CALL") -> {
                val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty().trim()
                val isBusiness = intent.getBooleanExtra(EXTRA_IS_BUSINESS, false)
                val duration = intent.getIntExtra(EXTRA_DURATION, 0)
                val includeDialTime = intent.getBooleanExtra("include_dial_time", true)

                if (phone.isBlank()) {
                    setResult(0, "Missing recipient phone", null)
                    return
                }

                val ch = if (isBusiness) ChannelType.WHATSAPP_BUSINESS_CALL else ChannelType.WHATSAPP_CALL
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val request = WhatsAppCallRequest(
                            taskId = System.currentTimeMillis(),
                            channel = ch,
                            recipient = phone,
                            callDurationSeconds = duration,
                            includeDialTime = includeDialTime
                        )
                        val result = TaskFeatureApi.startWhatsAppCall(context, repository, request)
                        pendingResult.setResult(if (result.success) 1 else 0, result.messageSnippet, null)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            action.endsWith("ACTION_POST_STATUS") -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE)
                    ?: intent.getStringExtra("status_text")
                    ?: intent.getStringExtra("text")
                    ?: ""
                val mediaUri = intent.getStringExtra("media_uri").orEmpty()
                val isMedia = intent.getBooleanExtra("is_media", mediaUri.isNotBlank())
                val targetPkg = intent.getStringExtra(EXTRA_PACKAGE) ?: intent.getStringExtra("package").orEmpty()
                val appExtra = intent.getStringExtra("app").orEmpty().lowercase()
                val isBiz = targetPkg == WhatsAppPackageHelper.PACKAGE_WHATSAPP_BUSINESS ||
                    intent.getBooleanExtra("is_business", false) ||
                    appExtra == "w4b" || appExtra.contains("business")

                val ch = if (isBiz) ChannelType.WHATSAPP_BUSINESS_STATUS else ChannelType.WHATSAPP_STATUS
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val request = WhatsAppStatusRequest(
                            taskId = System.currentTimeMillis(),
                            channel = ch,
                            message = message,
                            mediaUri = mediaUri,
                            isMedia = isMedia
                        )
                        val result = TaskFeatureApi.postWhatsAppStatus(context, repository, request)
                        pendingResult.setResult(if (result.success) 1 else 0, result.messageSnippet, null)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            action.endsWith("ACTION_SEND_SMS") -> {
                val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty().trim()
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                val simSlot = intent.getIntExtra(EXTRA_SIM_SLOT, 0)

                if (phone.isBlank() || message.isBlank()) {
                    setResult(0, "Missing phone or message text", null)
                    return
                }

                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val request = SmsRequest(
                            taskId = System.currentTimeMillis(),
                            recipient = phone,
                            message = message,
                            simSlot = simSlot
                        )
                        val result = TaskFeatureApi.sendSms(context.applicationContext, repository, request)
                        pendingResult.setResult(if (result.success) 1 else 0, result.messageSnippet, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "ACTION_SEND_SMS error: ${e.message}")
                        pendingResult.setResult(0, e.message ?: "Failed", null)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            action.endsWith("ACTION_MAKE_CALL") -> {
                val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty().trim()
                val duration = intent.getIntExtra(EXTRA_DURATION, 10)
                val simSlot = intent.getIntExtra(EXTRA_SIM_SLOT, 0)
                val includeDialTime = intent.getBooleanExtra(EXTRA_INCLUDE_DIAL_TIME, true)

                if (phone.isBlank()) {
                    setResult(0, "Missing phone number for call", null)
                    return
                }

                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val request = PhoneCallRequest(
                            taskId = System.currentTimeMillis(),
                            recipient = phone,
                            simSlot = simSlot,
                            callDurationSeconds = duration,
                            includeDialTime = includeDialTime
                        )
                        val result = TaskFeatureApi.placePhoneCall(context, repository, request)
                        pendingResult.setResult(if (result.success) 1 else 0, result.messageSnippet, null)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            action.endsWith("ACTION_SIMULATE_CALL") -> {
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Office / Boss"
                val callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: "+91 98765 43210"
                val skin = intent.getStringExtra(EXTRA_SKIN) ?: "PIXEL"

                val request = CallSimulationRequest(
                    taskId = System.currentTimeMillis(),
                    callerName = callerName,
                    callerNumber = callerNumber,
                    style = skin
                )
                val result = TaskFeatureApi.simulateCall(context, repository, request)
                setResult(if (result.success) 1 else 0, result.messageSnippet, null)
            }

            action.endsWith("ACTION_SET_BANKING_SHIELD") -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty().trim()
                val enable = intent.getBooleanExtra(EXTRA_ENABLED, true)

                if (pkg.isBlank()) {
                    setResult(0, "Missing package parameter", null)
                    return
                }

                val currentList = prefs.protectedPackages.toMutableSet()
                if (enable) {
                    currentList.add(pkg)
                } else {
                    currentList.remove(pkg)
                }
                prefs.protectedPackages = currentList
                ProtectedApplicationManager.setUserProtectedPackages(currentList)

                val msg = "Banking shield ${if (enable) "enabled" else "disabled"} for $pkg (${currentList.size} total protected)"
                Log.i(TAG, msg)
                AppFileLogger.action(context, TAG, msg)
                setResult(1, msg, null)
            }

            action.endsWith("ACTION_GET_STATUS") -> {
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val isA11y = AccessibilityHealthManager.isAccessibilityEnabledInSystem(context)
                        val a11yHealthy = AccessibilityHealthManager.isServiceOperational()
                        val poolCount = repository.countDynamicPool()
                        val waGroupCount = repository.countDynamicGroupPool(channelFilter = "WA_GROUP")
                        val w4bGroupCount = repository.countDynamicGroupPool(channelFilter = "WA_BUSINESS_GROUP")
                        val totalGroupCount = repository.countDynamicGroupPool()
                        val waChatCount = repository.countDynamicPool(channelFilter = "wa")
                        val w4bChatCount = repository.countDynamicPool(channelFilter = "w4b")
                        val protectedCount = ProtectedApplicationManager.getUserProtectedPackages().size

                        val json = JSONObject().apply {
                            put("app", "SuperApp")
                            put("package", context.packageName)
                            put("a11y_enabled", isA11y)
                            put("a11y_healthy", a11yHealthy)
                            put("pool_count", poolCount)
                            put("wa_groups", waGroupCount)
                            put("w4b_groups", w4bGroupCount)
                            put("total_groups", totalGroupCount)
                            put("wa_chats", waChatCount)
                            put("w4b_chats", w4bChatCount)
                            put("protected_apps", protectedCount)
                        }
                        val statusMsg = json.toString()
                        Log.i(TAG, "SuperApp Status: $statusMsg")
                        pendingResult.setResult(1, statusMsg, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "ACTION_GET_STATUS error: ${e.message}")
                        pendingResult.setResult(0, e.message ?: "Failed", null)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            action.endsWith("ACTION_DEVICE_DIAGNOSTICS") -> {
                try {
                    val pm = context.getSystemService(PowerManager::class.java)
                    val isBatteryIgnoring = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                    val notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    val isDefaultSms = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

                    val json = JSONObject().apply {
                        put("app", "SuperApp")
                        put("package", context.packageName)
                        put("battery_unrestricted", isBatteryIgnoring)
                        put("notifications_enabled", notifEnabled)
                        put("is_default_sms", isDefaultSms)
                        put("a11y_healthy", AccessibilityHealthManager.isServiceOperational())
                    }
                    setResult(1, json.toString(), null)
                } catch (e: Exception) {
                    setResult(0, e.message ?: "Failed", null)
                }
            }
        }
    }
}
