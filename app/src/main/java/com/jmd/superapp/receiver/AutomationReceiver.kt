/**
 * Package: com.jmd.superapp.receiver
 * Module: Automation & CLI Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [AutomationReceiver].
 *
 * What it does:
 * Exposes system-level automation endpoints callable via ADB CLI, automation scripts, and intent broadcasts:
 * - ACTION_HARVEST_WHATSAPP: Triggers node-based WhatsApp / WA Business chat and group discovery.
 * - ACTION_SYNC_POOL: Triggers multi-source contact pool synchronization (Contacts, SMS, Calls).
 * - ACTION_DISPATCH_TASK: Executes a scheduled task immediately by ID.
 * - ACTION_SEND_WHATSAPP: Dispatches a WhatsApp direct message via TaskFeatureApi.
 * - ACTION_SEND_SMS: Dispatches a direct SMS via SmsSender.
 * - ACTION_SET_BANKING_SHIELD: Dynamically toggles protection for sensitive applications.
 * - ACTION_GET_STATUS: Returns and logs real-time service and engine health metrics.
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
import android.util.Log
import com.jmd.superapp.api.SmsRequest
import com.jmd.superapp.api.TaskFeatureApi
import com.jmd.superapp.api.WhatsAppMessageRequest
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

class AutomationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AutomationReceiver"

        const val ACTION_HARVEST_WHATSAPP = "com.jmd.superapp.ACTION_HARVEST_WHATSAPP"
        const val ACTION_SYNC_POOL = "com.jmd.superapp.ACTION_SYNC_POOL"
        const val ACTION_DISPATCH_TASK = "com.jmd.superapp.ACTION_DISPATCH_TASK"
        const val ACTION_SEND_WHATSAPP = "com.jmd.superapp.ACTION_SEND_WHATSAPP"
        const val ACTION_SEND_SMS = "com.jmd.superapp.ACTION_SEND_SMS"
        const val ACTION_SET_BANKING_SHIELD = "com.jmd.superapp.ACTION_SET_BANKING_SHIELD"
        const val ACTION_GET_STATUS = "com.jmd.superapp.ACTION_GET_STATUS"

        // Extra keys
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_TARGET = "target"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_PHONE = "phone"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SIM_SLOT = "sim_slot"
        const val EXTRA_IS_BUSINESS = "is_business"
        const val EXTRA_ENABLED = "enabled"
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Automation intent received: $action")
        AppFileLogger.action(context, TAG, "Automation intent received: $action")

        val repository = AppRepository(context.applicationContext)
        val prefs = PreferenceManager(context.applicationContext)

        when (action) {
            ACTION_HARVEST_WHATSAPP -> {
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
                setResult(1, "WhatsApp harvest initiated ($scanTarget)", null)
            }

            ACTION_SYNC_POOL -> {
                receiverScope.launch {
                    val summary = repository.syncWhatsAppPool()
                    val msg = "Pool synced: +${summary.newAdded} new contacts (${summary.totalPoolCount} total)"
                    Log.i(TAG, msg)
                    AppFileLogger.i(context, TAG, msg)
                    setResult(1, msg, null)
                }
            }

            ACTION_DISPATCH_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                if (taskId <= 0L) {
                    Log.w(TAG, "Invalid task ID provided: $taskId")
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

            ACTION_SEND_WHATSAPP -> {
                val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty().trim()
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                val isBusiness = intent.getBooleanExtra(EXTRA_IS_BUSINESS, false)

                if (phone.isBlank()) {
                    Log.w(TAG, "Missing phone recipient for WhatsApp message")
                    setResult(0, "Missing recipient phone", null)
                    return
                }

                val ch = if (isBusiness) ChannelType.WHATSAPP_BUSINESS else ChannelType.WHATSAPP
                receiverScope.launch {
                    val request = WhatsAppMessageRequest(
                        taskId = System.currentTimeMillis(),
                        channel = ch,
                        recipient = phone,
                        message = message
                    )
                    val result = TaskFeatureApi.sendWhatsAppMessage(context, repository, request)
                    setResult(if (result.success) 1 else 0, result.messageSnippet, null)
                }
            }

            ACTION_SEND_SMS -> {
                val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty().trim()
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                val simSlot = intent.getIntExtra(EXTRA_SIM_SLOT, 0)

                if (phone.isBlank() || message.isBlank()) {
                    Log.w(TAG, "Missing phone or message text for SMS")
                    setResult(0, "Missing phone or message text", null)
                    return
                }

                val request = SmsRequest(
                    taskId = System.currentTimeMillis(),
                    recipient = phone,
                    message = message,
                    simSlot = simSlot
                )
                val result = TaskFeatureApi.sendSms(context, repository, request)
                setResult(if (result.success) 1 else 0, result.messageSnippet, null)
            }

            ACTION_SET_BANKING_SHIELD -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty().trim()
                val enable = intent.getBooleanExtra(EXTRA_ENABLED, true)

                if (pkg.isBlank()) {
                    Log.w(TAG, "Missing package parameter for banking shield toggle")
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

            ACTION_GET_STATUS -> {
                receiverScope.launch {
                    val isA11y = AccessibilityHealthManager.isAccessibilityEnabledInSystem(context)
                    val poolCount = repository.countDynamicPool()
                    val protectedCount = ProtectedApplicationManager.getUserProtectedPackages().size
                    val statusMsg = "SuperApp Status: a11y=$isA11y, poolCount=$poolCount, protectedApps=$protectedCount"
                    Log.i(TAG, statusMsg)
                    setResult(1, statusMsg, null)
                }
            }
        }
    }
}
