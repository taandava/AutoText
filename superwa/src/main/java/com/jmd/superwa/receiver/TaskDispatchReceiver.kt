/**
 * Package: com.jmd.superwa.receiver
 * Module: Task Dispatch & Headless CLI Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [TaskDispatchReceiver].
 *
 * What it does:
 * Exposes system-level endpoints callable via ADB CLI, scripts, and intent broadcasts:
 * - SMS & Call dispatch for SuperSMS
 * - WhatsApp chat & group sync for SuperWA
 * - Banking shield toggles
 * - Status diagnostics & pool inspection
 */

package com.jmd.superwa.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmd.superwa.api.TaskFeatureApi
import com.jmd.superwa.api.WhatsAppMessageRequest
import com.jmd.superwa.core.AccessibilityHealthManager
import com.jmd.superwa.core.AppFileLogger
import com.jmd.superwa.core.AppVariantHelper
import com.jmd.superwa.core.ProtectedApplicationManager
import com.jmd.superwa.core.WhatsAppPackageHelper
import com.jmd.superwa.data.model.ChannelType
import com.jmd.superwa.data.prefs.PreferenceManager
import com.jmd.superwa.data.repo.AppRepository
import com.jmd.superwa.service.TaskAccessibilityService
import com.jmd.superwa.whatsapp.WhatsAppChatScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TaskDispatchReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskDispatchReceiver"

        // Extra keys
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_TARGET = "target"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_PHONE = "phone"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_DURATION = "duration"
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
            // SYNC CHATS / HARVEST (SuperWA)
            action.endsWith("ACTION_SYNC_CHATS") || action.endsWith("ACTION_HARVEST_WHATSAPP") -> {
                if (AppVariantHelper.isSuperSms) {
                    setResult(0, "Not supported in SuperSMS variant", null)
                    return
                }
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

            // SEND WHATSAPP (SuperWA)
            action.endsWith("ACTION_SEND_WHATSAPP") -> {
                if (AppVariantHelper.isSuperSms) {
                    setResult(0, "Not supported in SuperSMS variant", null)
                    return
                }
                val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty().trim()
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                val isBusiness = intent.getBooleanExtra(EXTRA_IS_BUSINESS, false)

                if (phone.isBlank()) {
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

            // WHATSAPP CALL (SuperWA)
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
                receiverScope.launch {
                    val request = com.jmd.superwa.api.WhatsAppCallRequest(
                        taskId = System.currentTimeMillis(),
                        channel = ch,
                        recipient = phone,
                        callDurationSeconds = duration,
                        includeDialTime = includeDialTime
                    )
                    val result = TaskFeatureApi.startWhatsAppCall(context, repository, request)
                    setResult(if (result.success) 1 else 0, result.messageSnippet, null)
                }
            }

            // POST STATUS (SuperWA)
            action.endsWith("ACTION_POST_STATUS") -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                val mediaUri = intent.getStringExtra("media_uri").orEmpty()
                val isMedia = intent.getBooleanExtra("is_media", mediaUri.isNotBlank())

                if (message.isBlank() && mediaUri.isBlank()) {
                    setResult(0, "Missing status text or media URI", null)
                    return
                }

                receiverScope.launch {
                    val request = com.jmd.superwa.api.WhatsAppStatusRequest(
                        taskId = System.currentTimeMillis(),
                        message = message,
                        mediaUri = mediaUri,
                        isMedia = isMedia
                    )
                    val result = TaskFeatureApi.postWhatsAppStatus(context, repository, request)
                    setResult(if (result.success) 1 else 0, result.messageSnippet, null)
                }
            }

            // SET BANKING SHIELD (SuperWA)
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

            // GET STATUS
            action.endsWith("ACTION_GET_STATUS") -> {
                receiverScope.launch {
                    val variant = AppVariantHelper.currentVariant
                    val poolCount = repository.countDynamicPool()
                    val protectedCount = ProtectedApplicationManager.getUserProtectedPackages().size
                    val isA11y = if (AppVariantHelper.isSuperSms) {
                        "N/A (SuperSMS - Zero Accessibility Required)"
                    } else {
                        AccessibilityHealthManager.isAccessibilityEnabledInSystem(context).toString()
                    }
                    val statusMsg = "App: $variant | a11y=$isA11y | poolCount=$poolCount | protectedApps=$protectedCount"
                    Log.i(TAG, statusMsg)
                    setResult(1, statusMsg, null)
                }
            }

            // DUMP GROUPS (SuperWA)
            action.endsWith("ACTION_DUMP_GROUPS") -> {
                receiverScope.launch {
                    val db = com.jmd.superwa.data.db.DatabaseHelper.getInstance(context).readableDatabase
                    val dumpLines = mutableListOf<String>()
                    val count = try {
                        db.rawQuery(
                            "SELECT id, name, phone, normalized_number, source FROM ${com.jmd.superwa.data.db.DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE enabled = 1 AND (source IN ('WA_GROUP', 'WA_BUSINESS_GROUP') OR phone LIKE 'group:%' OR normalized_number LIKE 'group:%') ORDER BY name ASC",
                            null
                        ).use { c ->
                            var cIdx = 0
                            while (c.moveToNext()) {
                                cIdx++
                                val id = c.getLong(0)
                                val name = c.getString(1).orEmpty()
                                val phone = c.getString(2).orEmpty()
                                val norm = c.getString(3).orEmpty()
                                val src = c.getString(4).orEmpty()
                                val line = "[$id] name='$name' | phone='$phone' | norm='$norm' | src='$src'"
                                dumpLines.add(line)
                                Log.w(TAG, "DUMP_GROUP: $line")
                            }
                            cIdx
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "DUMP_GROUPS error: ${e.message}")
                        0
                    }
                    try {
                        val dumpFile = java.io.File("/sdcard/Download/groups_dump.txt")
                        dumpFile.writeText(dumpLines.joinToString("\n"))
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not write /sdcard/Download/groups_dump.txt: ${e.message}")
                    }
                    val msg = "Dumped $count group entries"
                    Log.i(TAG, msg)
                    setResult(1, msg, null)
                }
            }

            // PURGE CORRUPTED GROUPS (SuperWA)
            action.endsWith("ACTION_PURGE_CORRUPTED_GROUPS") -> {
                receiverScope.launch {
                    val purged = repository.purgeCorruptedGroupsFromPool()
                    val msg = "Purged and repaired $purged corrupted group entries"
                    Log.i(TAG, msg)
                    AppFileLogger.i(context, TAG, msg)
                    setResult(1, msg, null)
                }
            }
        }
    }
}
