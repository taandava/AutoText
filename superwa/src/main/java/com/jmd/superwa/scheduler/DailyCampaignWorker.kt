/**
 * Package: com.jmd.superwa.scheduler
 * Module: Schedulers & Background Workers
 *
 * What it is:
 * Background WorkManager [CoroutineWorker] executing scheduled daily WhatsApp batch outreach campaigns.
 */

package com.jmd.superwa.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jmd.superwa.core.AccessibilityHealthManager
import com.jmd.superwa.core.AppFileLogger
import com.jmd.superwa.core.MessageTemplateResolver
import com.jmd.superwa.data.model.ChannelType
import com.jmd.superwa.data.model.ExecutionLog
import com.jmd.superwa.data.prefs.PreferenceManager
import com.jmd.superwa.data.repo.AppRepository
import com.jmd.superwa.engine.ContactIngestionEngine
import com.jmd.superwa.engine.SequentialTaskEngine
import com.jmd.superwa.engine.WhatsAppSender
import com.jmd.superwa.service.TaskAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DailyCampaignWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "DailyCampaignWorker"
        const val KEY_MANUAL_TRIGGER = "key_manual_trigger"
        const val KEY_CAMPAIGN_ID = "key_campaign_id"

        private val workerMutex = Mutex()
    }

    override suspend fun doWork(): Result = workerMutex.withLock {
        val repo = AppRepository(applicationContext)
        val campaignId = inputData.getLong(KEY_CAMPAIGN_ID, 1L)
        val config = repo.getDailyCampaignConfig(campaignId)
        val isManual = inputData.getBoolean(KEY_MANUAL_TRIGGER, defaultValue = false)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = dateFormat.format(Date())

        AppFileLogger.i(
            applicationContext,
            TAG,
            "Starting DailyCampaignWorker for id=$campaignId (${config.title}), manual=$isManual, active=${config.isActive}"
        )

        val prefs = PreferenceManager(applicationContext)

        if (!config.isActive) {
            AppFileLogger.i(applicationContext, TAG, "Campaign #$campaignId is inactive. Skipping.")
            return Result.success()
        }

        if (!isManual) {
            val nowCal = java.util.Calendar.getInstance()
            val targetCal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, config.scheduledHour)
                set(java.util.Calendar.MINUTE, config.scheduledMinute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            if (nowCal.timeInMillis < targetCal.timeInMillis - 60_000L) {
                AppFileLogger.w(
                    applicationContext,
                    TAG,
                    "Premature trigger intercepted: Current time (${nowCal.time}) is before scheduled dispatch window (${targetCal.time}) for Campaign #${config.id}."
                )
                return Result.success()
            }

            if (config.lastRunDate == todayStr) {
                AppFileLogger.i(applicationContext, TAG, "Campaign already executed today ($todayStr). Skipping duplicate run.")
                return Result.success()
            }
        }

        val channelDisplayName = if (config.channel.contains("BUSINESS", ignoreCase = true)) {
            "Business Campaign"
        } else {
            "WhatsApp Campaign"
        }

        val privacyMgr = com.jmd.superwa.core.PrivacyManager.getInstance(applicationContext)
        privacyMgr.prepareAccessibilityForTask()

        val isA11yActive = AccessibilityHealthManager.awaitServiceReadiness(applicationContext, 4000L)
        if (!isA11yActive) {
            val errorMsg = "Accessibility Service is not operational. Waiting for service reconnection before dispatching WhatsApp campaign."
            repo.insertLog(
                ExecutionLog(
                    taskId = config.id,
                    channel = channelDisplayName,
                    recipient = "N/A",
                    messageSnippet = "Accessibility Service disconnected or paused",
                    status = "RETRY",
                    details = errorMsg
                )
            )
            AppFileLogger.w(applicationContext, TAG, errorMsg)
            privacyMgr.releaseAccessibilityAfterTask()
            return Result.retry()
        }

        try {
            val alreadySentCampaign = repo.getSentCountForCampaignToday(config.id)
            val campaignRemaining = maxOf(0, config.dailyLimit - alreadySentCampaign)

            if (campaignRemaining <= 0) {
                val limitMsg = "Daily limit of ${config.dailyLimit} reached today for campaign '${config.title}' ($alreadySentCampaign already sent today). Pausing batch."
                AppFileLogger.i(applicationContext, TAG, limitMsg)
                return Result.success()
            }

            if (config.autoScanEnabled) {
                try {
                    val scanner = ContactIngestionEngine(applicationContext, repo)
                    val summary = scanner.scanAndIngestAll()
                    AppFileLogger.i(
                        applicationContext,
                        TAG,
                        "Auto-scanned: Contacts=${summary.contactsScanned}, WhatsApp=${summary.waChatsScanned}, Business=${summary.waBusinessScanned}, New=${summary.newAdded}"
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    AppFileLogger.w(applicationContext, TAG, "Contact ingestion encountered an issue: ${e.message}", e)
                }
            }

            val recipients = repo.getPendingPoolContactsForCampaign(campaignId = config.id, isWhatsApp = true, limit = campaignRemaining)
            if (recipients.isEmpty()) {
                AppFileLogger.i(applicationContext, TAG, "No recipients in pool to send daily campaign to.")
                return Result.success()
            }

            val waSender = WhatsAppSender(applicationContext)
            var successCount = 0
            var failCount = 0

            val configuredWaDelay = prefs.whatsappDispatchDelaySeconds
            val intervalMs = (config.intervalSeconds.coerceAtLeast(configuredWaDelay)) * 1000L
            val totalCount = recipients.size

            AppFileLogger.i(
                applicationContext,
                TAG,
                "Commencing dispatch of $totalCount recipients for $channelDisplayName. Interval: ${intervalMs}ms"
            )

            for ((index, item) in recipients.withIndex()) {
                val liveConfig = repo.getDailyCampaignConfig(config.id)
                if (!liveConfig.isActive) {
                    AppFileLogger.w(applicationContext, TAG, "Daily Campaign #${config.id} paused mid-batch.")
                    TaskAccessibilityService.clearPending()
                    return Result.success()
                }
                if (isStopped) {
                    AppFileLogger.w(applicationContext, TAG, "DailyCampaignWorker was stopped by system.")
                    TaskAccessibilityService.clearPending()
                    return Result.success()
                }

                if (com.jmd.superwa.telephony.TelephonyCallEngine.isCallActive(applicationContext)) {
                    AppFileLogger.i(applicationContext, TAG, "Daily Campaign #${config.id} paused: Active phone call in progress.")
                    var callWaitElapsedMs = 0L
                    val maxCallWaitMs = 180_000L
                    while (com.jmd.superwa.telephony.TelephonyCallEngine.isCallActive(applicationContext) && callWaitElapsedMs < maxCallWaitMs) {
                        if (isStopped || !repo.getDailyCampaignConfig(config.id).isActive) {
                            return Result.success()
                        }
                        delay(2000L.milliseconds)
                        callWaitElapsedMs += 2000L
                    }
                }

                val displayName = item.name.ifBlank { "Friend" }
                val message = MessageTemplateResolver.resolve(
                    template = config.message,
                    name = item.name,
                    phone = item.phone,
                    targetTimestamp = System.currentTimeMillis()
                )

                val isLast = index == totalCount - 1
                val waChannel = if (config.channel.contains("BUSINESS", ignoreCase = true)) {
                    ChannelType.WHATSAPP_BUSINESS
                } else {
                    ChannelType.WHATSAPP
                }

                val shouldRelockThisItem = isLast && prefs.isAutoLockAfterSendEnabled
                val taskDispatchMutex = SequentialTaskEngine.getUiTaskMutex(applicationContext)

                taskDispatchMutex.withLock {
                    var sendOutcome = TaskAccessibilityService.RESULT_NONE
                    var attemptsMade = 0
                    val maxImmediateAttempts = 3

                    while (attemptsMade < maxImmediateAttempts && sendOutcome != TaskAccessibilityService.RESULT_SUCCESS && sendOutcome != TaskAccessibilityService.RESULT_INVALID) {
                        attemptsMade++
                        if (attemptsMade > 1) {
                            AppFileLogger.i(applicationContext, TAG, "Retrying WhatsApp message to ${item.phone} (attempt $attemptsMade/$maxImmediateAttempts)")
                            delay(1200.milliseconds)
                        }
                        TaskAccessibilityService.clearPending()
                        TaskAccessibilityService.markDispatchPending()

                        SequentialTaskEngine.getInstance(applicationContext).ensureDeviceAwakeAndUnlocked(
                            taskId = config.id,
                            channel = waChannel,
                            targetRecipient = item.phone
                        )

                        val dispatched = waSender.send(
                            taskId = config.id,
                            channel = waChannel,
                            recipientPhone = item.phone,
                            messageText = message,
                            shouldRelock = shouldRelockThisItem,
                            isBatchCampaign = true
                        )

                        if (!dispatched) {
                            sendOutcome = TaskAccessibilityService.RESULT_NONE
                            if (attemptsMade < maxImmediateAttempts) continue
                        } else {
                            val maxWaitMs = 16000L
                            var waitedMs = 0L
                            while (TaskAccessibilityService.lastSendResult == TaskAccessibilityService.RESULT_PENDING && waitedMs < maxWaitMs) {
                                if (isStopped || !repo.getDailyCampaignConfig(config.id).isActive) {
                                    TaskAccessibilityService.clearPending()
                                    return Result.success()
                                }
                                delay(500.milliseconds)
                                waitedMs += 500L
                            }
                            sendOutcome = TaskAccessibilityService.lastSendResult
                        }

                        if (sendOutcome != TaskAccessibilityService.RESULT_SUCCESS && !isLast) {
                            TaskAccessibilityService.navigateBack()
                            delay(400.milliseconds)
                        }
                    }

                    when (sendOutcome) {
                        TaskAccessibilityService.RESULT_SUCCESS -> {
                            successCount++
                            repo.markContactSent(item.id, whatsApp = true)
                            repo.markCampaignContactSuccess(config.id, item, config.channel)
                            AppFileLogger.i(applicationContext, TAG, "Delivered WhatsApp message to ${item.phone} ($displayName) on attempt $attemptsMade")
                        }
                        TaskAccessibilityService.RESULT_INVALID -> {
                            failCount++
                            repo.markContactInvalid(item.id, whatsApp = true)
                            AppFileLogger.w(applicationContext, TAG, "Recipient ${item.phone} is invalid or not registered on WhatsApp")
                        }
                        else -> {
                            failCount++
                            repo.markContactPendingRetry(item.id, whatsApp = true, status = "FAILED")
                            repo.insertLog(
                                ExecutionLog(
                                    taskId = config.id,
                                    channel = channelDisplayName,
                                    recipient = "${item.phone} ($displayName)",
                                    messageSnippet = message.take(60),
                                    status = "FAILED",
                                    details = "WhatsApp dispatch timed out after $attemptsMade attempts"
                                )
                            )
                        }
                    }

                    if (!isLast && sendOutcome == TaskAccessibilityService.RESULT_SUCCESS) {
                        delay(450.milliseconds)
                        TaskAccessibilityService.navigateBack()
                        delay(350.milliseconds)
                    }
                }

                if (!isLast) {
                    var elapsed = 0L
                    val step = 250L
                    while (elapsed < intervalMs) {
                        if (isStopped || !repo.getDailyCampaignConfig(config.id).isActive) {
                            TaskAccessibilityService.clearPending()
                            return Result.success()
                        }
                        val chunk = minOf(step, intervalMs - elapsed)
                        delay(chunk.milliseconds)
                        elapsed += chunk
                    }
                }
            }

            if (!prefs.isAutoLockAfterSendEnabled && successCount > 0) {
                delay(1200.milliseconds)
                try {
                    val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                        addCategory(android.content.Intent.CATEGORY_HOME)
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    applicationContext.startActivity(homeIntent)
                } catch (e: Exception) {
                    AppFileLogger.w(applicationContext, TAG, "Failed to navigate HOME after campaign: ${e.message}")
                }
            }

            if (isStopped) {
                return Result.success()
            }

            val finalConfig = repo.getDailyCampaignConfig(config.id)
            if (successCount == 0 && failCount > 0) {
                if (!finalConfig.isActive) {
                    return Result.success()
                }
                return Result.retry()
            }

            repo.updateDailyCampaignLastRunDate(config.id, todayStr)
            AppFileLogger.action(
                applicationContext,
                TAG,
                "Daily campaign #${config.id} (${config.title}) finished. Sent=$successCount, Failed=$failCount"
            )

            return Result.success()
        } finally {
            privacyMgr.releaseAccessibilityAfterTask()
        }
    }
}
