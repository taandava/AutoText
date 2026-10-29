

/**
 * Package: com.jmd.autotext.scheduler
 * Module: Schedulers Layer
 *
 * Background WorkManager [CoroutineWorker] executing automated daily outreach batch campaigns.
 * Coordinates dynamic contact ingestion, per-contact cooldowns, gap delays (pacing), SMS quota tracking,
 * and dispatching via [SmsSender] or [WhatsAppAutomationSender].
 *
 * Linked With:
 * - Scheduler: [com.jmd.autotext.scheduler.DailyCampaignScheduler]
 * - Ingestion Engine: [com.jmd.autotext.engine.ContactIngestionEngine]
 * - Senders: [com.jmd.autotext.engine.SmsSender], [com.jmd.autotext.engine.WhatsAppAutomationSender]
 * - Repositories: [com.jmd.autotext.data.repo.AppRepository], [com.jmd.autotext.data.repo.DailyCampaignRepository]
 * - Quotas: [com.jmd.autotext.core.SmsQuotaManager]
 * - Diagnostics: [com.jmd.autotext.core.AppFileLogger]
 */
package com.jmd.autotext.scheduler

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.core.MessageTemplateResolver
import com.jmd.autotext.core.SmsQuotaManager
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.ExecutionLog
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.engine.ContactIngestionEngine
import com.jmd.autotext.engine.SequentialTaskEngine
import com.jmd.autotext.engine.SmsSender
import com.jmd.autotext.engine.WhatsAppAutomationSender
import com.jmd.autotext.service.AutoTextAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background worker executing paced daily batch outreach campaigns.
 */
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

    /**
     * Executes the background campaign workflow:
     * 1. Validates campaign status, schedule date, and permission readiness (Accessibility for WhatsApp, SEND_SMS for cellular).
     * 2. Checks India TRAI regulatory SMS quotas (100 SMS/day ceiling) and campaign daily allowance limits.
     * 3. Triggers automated contact ingestion if auto-scan is enabled.
     * 4. Queries pending recipient pool contacts and paces sequential dispatch according to configured throttle delays.
     * 5. Logs detailed execution results and updates campaign progress ledgers.
     *
     * @return [Result.success] on clean completion/pause, or [Result.failure] on fatal missing permissions.
     */
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

        if (!prefs.isTasksMasterEnabled) {
            AppFileLogger.i(applicationContext, TAG, "Task Scheduler Master Switch is OFF. Skipping campaign #$campaignId.")
            return Result.success()
        }

        if (com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive(applicationContext)) {
            AppFileLogger.i(applicationContext, TAG, "Emergency Stop is ACTIVE. Skipping campaign #$campaignId.")
            return Result.success()
        }

        if ((!isManual) && (config.lastRunDate == todayStr)) {
            AppFileLogger.i(applicationContext, TAG, "Campaign already executed today ($todayStr). Skipping duplicate run.")
            return Result.success()
        }

        val isWhatsApp = config.channel.equals("WHATSAPP", ignoreCase = true) ||
                config.channel.equals("WHATSAPP_BUSINESS", ignoreCase = true)

        val channelDisplayName = when {
            config.channel.contains("BUSINESS", ignoreCase = true) -> "Daily WhatsApp Business Campaign"
            isWhatsApp -> "Daily WhatsApp Campaign"
            else -> "Daily SMS Campaign"
        }

        if (isWhatsApp) {
            val isA11yActive = AccessibilityHealthManager.waitForServiceReadiness(applicationContext, 4000L)
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
                return Result.retry()
            }
        } else {
            val hasSmsPerm = ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasSmsPerm) {
                val errorMsg = "SEND_SMS permission not granted."
                repo.insertLog(
                    ExecutionLog(
                        taskId = config.id,
                        channel = channelDisplayName,
                        recipient = "N/A",
                        messageSnippet = "SEND_SMS permission not granted",
                        status = "FAILED",
                        details = errorMsg
                    )
                )
                AppFileLogger.e(applicationContext, TAG, errorMsg)
                return Result.failure()
            }
        }

        val alreadySentCampaign = if (isWhatsApp) {
            repo.getSentCountForCampaignToday(config.id)
        } else {
            repo.getSentSegmentsForCampaignToday(config.id)
        }
        val campaignRemaining = maxOf(0, config.dailyLimit - alreadySentCampaign)

        if (campaignRemaining <= 0) {
            val limitMsg = "Daily limit of ${config.dailyLimit} reached today for campaign '${config.title}' ($alreadySentCampaign already sent today). Pausing batch."
            AppFileLogger.i(applicationContext, TAG, limitMsg)
            return Result.success()
        }

        var allowedBatchSize = campaignRemaining

        if (!isWhatsApp) {
            val quotaStatus = SmsQuotaManager.getQuotaStatus(applicationContext)
            AppFileLogger.i(
                applicationContext,
                TAG,
                "India SMS Quota check: ${quotaStatus.totalSentToday}/${quotaStatus.limit} sent today across device. Remaining: ${quotaStatus.remaining}. Campaign remaining allowance: $campaignRemaining segments."
            )

            if (quotaStatus.isLimitReached) {
                val limitMsg = "App daily safeguard of 100 SMS reached across device (${quotaStatus.totalSentToday} sent today). Batch queued for tomorrow to prevent carrier penalty."
                repo.insertLog(
                    ExecutionLog(
                        taskId = config.id,
                        channel = channelDisplayName,
                        recipient = "All Queued Contacts",
                        messageSnippet = "Daily India SMS Quota Exceeded",
                        status = "QUEUED",
                        details = limitMsg
                    )
                )
                AppFileLogger.w(applicationContext, TAG, limitMsg)
                return Result.success()
            }

            val estimatedSegments = maxOf(1, SmsQuotaManager.calculateSmsSegments(config.messageTemplate))
            val maxContactsForCampaign = campaignRemaining / estimatedSegments
            val maxContactsForQuota = quotaStatus.remaining / estimatedSegments

            if (maxContactsForCampaign <= 0 || maxContactsForQuota <= 0) {
                val limitMsg = "Campaign daily limit reached or estimated segments exceed remaining capacity (campaign remaining: $campaignRemaining, quota remaining: ${quotaStatus.remaining}, required: $estimatedSegments)."
                AppFileLogger.i(applicationContext, TAG, limitMsg)
                return Result.success()
            }

            allowedBatchSize = minOf(maxContactsForCampaign, maxContactsForQuota)
        }

        if (config.autoScanEnabled) {
            try {
                val scanner = ContactIngestionEngine(applicationContext, repo)
                val summary = scanner.scanAndIngestAll()
                AppFileLogger.i(
                    applicationContext,
                    TAG,
                    "Auto-scanned: Contacts=${summary.contactsScanned}, SMS=${summary.smsScanned}, Calls=${summary.callLogsScanned}, New=${summary.newAdded}"
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppFileLogger.w(applicationContext, TAG, "Contact ingestion encountered an issue: ${e.message}", e)
            }
        }

        val recipients = repo.getPendingPoolContactsForCampaign(campaignId = config.id, isWhatsApp = isWhatsApp, limit = allowedBatchSize)
        if (recipients.isEmpty()) {
            AppFileLogger.i(applicationContext, TAG, "No recipients in pool to send daily campaign to.")
            return Result.success()
        }

        val smsSender = if (!isWhatsApp) SmsSender(applicationContext) else null
        val waSender = if (isWhatsApp) WhatsAppAutomationSender(applicationContext) else null

        var successCount = 0
        var failCount = 0
        var queuedCount = 0

        val configuredWaDelay = prefs.whatsappDispatchDelaySeconds
        val minInterval = if (isWhatsApp) configuredWaDelay else 4
        val intervalMs = (config.intervalSeconds.coerceAtLeast(minInterval)) * 1000L
        val totalCount = recipients.size

        AppFileLogger.i(
            applicationContext,
            TAG,
            "Commencing dispatch of $totalCount recipients for $channelDisplayName. Interval: ${intervalMs}ms"
        )

        for ((index, item) in recipients.withIndex()) {
            val liveConfig = repo.getDailyCampaignConfig(config.id)
            if (!liveConfig.isActive) {
                AppFileLogger.w(applicationContext, TAG, "Daily Campaign #${config.id} was PAUSED/DEACTIVATED by user mid-batch! Halting immediately.")
                AutoTextAccessibilityService.clearPending()
                return Result.success()
            }
            if (!prefs.isTasksMasterEnabled) {
                AppFileLogger.w(applicationContext, TAG, "Task Scheduler Master Switch was disabled mid-batch! Halting campaign #${config.id}.")
                AutoTextAccessibilityService.clearPending()
                return Result.success()
            }
            if (com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive(applicationContext)) {
                AppFileLogger.w(applicationContext, TAG, "Emergency Stop was triggered mid-batch! Halting campaign #${config.id}.")
                AutoTextAccessibilityService.clearPending()
                return Result.success()
            }
            if (isStopped) {
                AppFileLogger.w(applicationContext, TAG, "DailyCampaignWorker was stopped by system. Halting immediately.")
                AutoTextAccessibilityService.clearPending()
                return Result.success()
            }

            val displayName = item.name.ifBlank { "Friend" }
            val message = MessageTemplateResolver.resolve(
                template = config.messageTemplate,
                name = displayName,
                phone = item.phone,
                targetTimestamp = System.currentTimeMillis()
            )

            val isLast = index == totalCount - 1

            if (isWhatsApp) {
                val waChannel = if (config.channel.contains("BUSINESS", ignoreCase = true)) {
                    ChannelType.WHATSAPP_BUSINESS
                } else {
                    ChannelType.WHATSAPP
                }

                val shouldRelockThisItem = isLast && prefs.isAutoLockAfterSendEnabled

                val automationMutex = SequentialTaskEngine.getUiAutomationMutex(applicationContext)
                automationMutex.withLock {
                    var sendOutcome = AutoTextAccessibilityService.RESULT_NONE
                    var attemptsMade = 0
                    val maxImmediateAttempts = 3 // 1 initial + up to 2 immediate retries

                    while (attemptsMade < maxImmediateAttempts && sendOutcome != AutoTextAccessibilityService.RESULT_SUCCESS && sendOutcome != AutoTextAccessibilityService.RESULT_INVALID) {
                        attemptsMade++
                        if (attemptsMade > 1) {
                            AppFileLogger.i(applicationContext, TAG, "Retrying WhatsApp message to ${item.phone} (attempt $attemptsMade/$maxImmediateAttempts)")
                            delay(1200.milliseconds)
                        }
                        AutoTextAccessibilityService.clearPending()
                        AutoTextAccessibilityService.markDispatchPending()

                        SequentialTaskEngine.getInstance(applicationContext).ensureDeviceAwakeAndUnlocked(
                            taskId = config.id,
                            channel = waChannel,
                            targetRecipient = item.phone
                        )

                        val dispatched = waSender?.send(
                            taskId = config.id,
                            channel = waChannel,
                            recipientPhone = item.phone,
                            messageText = message,
                            shouldRelock = shouldRelockThisItem,
                            isBatchCampaign = true
                        ) ?: false

                        if (!dispatched) {
                            sendOutcome = AutoTextAccessibilityService.RESULT_NONE
                            if (attemptsMade < maxImmediateAttempts) continue
                        } else {
                            val maxWaitMs = 16000L
                            var waitedMs = 0L
                            while (AutoTextAccessibilityService.lastSendResult == AutoTextAccessibilityService.RESULT_PENDING && waitedMs < maxWaitMs) {
                                if (isStopped || !repo.getDailyCampaignConfig(config.id).isActive || !prefs.isTasksMasterEnabled || com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive(applicationContext)) {
                                    AutoTextAccessibilityService.clearPending()
                                    return Result.success()
                                }
                                delay(500.milliseconds)
                                waitedMs += 500L
                            }
                            sendOutcome = AutoTextAccessibilityService.lastSendResult
                        }

                        if (sendOutcome != AutoTextAccessibilityService.RESULT_SUCCESS && !isLast) {
                            AutoTextAccessibilityService.navigateBack()
                            delay(400.milliseconds)
                        }
                    }

                    when (sendOutcome) {
                        AutoTextAccessibilityService.RESULT_SUCCESS -> {
                            successCount++
                            repo.markContactSent(item.id, whatsApp = true)
                            repo.markCampaignContactSuccess(config.id, item, config.channel)
                            AppFileLogger.i(applicationContext, TAG, "Delivered WhatsApp message to ${item.phone} ($displayName) on attempt $attemptsMade")
                            AppFileLogger.campaignLedger(
                                context = applicationContext,
                                campaignId = config.id,
                                channel = channelDisplayName,
                                recipientName = displayName,
                                recipientPhone = item.phone,
                                status = "SUCCESS",
                                messageSnippet = message.take(80),
                                attempt = item.waSendCount + attemptsMade,
                                details = "Batch campaign dispatch delivered via AutoText Service (attempt $attemptsMade)"
                            )
                        }
                        AutoTextAccessibilityService.RESULT_INVALID -> {
                            failCount++
                            repo.markContactInvalid(item.id, whatsApp = true)
                            AppFileLogger.w(applicationContext, TAG, "Recipient ${item.phone} is invalid or not registered on WhatsApp")
                            AppFileLogger.campaignLedger(
                                context = applicationContext,
                                campaignId = config.id,
                                channel = channelDisplayName,
                                recipientName = displayName,
                                recipientPhone = item.phone,
                                status = "INVALID",
                                messageSnippet = message.take(80),
                                attempt = item.waSendCount + attemptsMade,
                                details = "Recipient not registered on WhatsApp or invalid phone number"
                            )
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
                                    details = "WhatsApp automation timed out after $attemptsMade attempts (contact kept in priority retry queue)"
                                )
                            )
                            AppFileLogger.w(applicationContext, TAG, "WhatsApp automation failed after $attemptsMade attempts for ${item.phone}. Added to priority retry queue.")
                            AppFileLogger.campaignLedger(
                                context = applicationContext,
                                campaignId = config.id,
                                channel = channelDisplayName,
                                recipientName = displayName,
                                recipientPhone = item.phone,
                                status = "TIMEOUT",
                                messageSnippet = message.take(80),
                                attempt = item.waSendCount + attemptsMade,
                                details = "WhatsApp automation timed out after $attemptsMade attempts (kept in queue for next cycle)"
                            )
                        }
                    }

                    if (!isLast && sendOutcome == AutoTextAccessibilityService.RESULT_SUCCESS) {
                        delay(450.milliseconds)
                        AutoTextAccessibilityService.navigateBack()
                        delay(350.milliseconds)
                    }
                }
            } else {
                val exactMessageSegments = SmsQuotaManager.calculateSmsSegments(message)
                val sentSoFarToday = repo.getSentSegmentsForCampaignToday(config.id)
                if (sentSoFarToday + exactMessageSegments > config.dailyLimit) {
                    queuedCount++
                    val stopReason = "Campaign daily limit reached ($sentSoFarToday/${config.dailyLimit} segments sent). Next contact requires $exactMessageSegments segments."
                    AppFileLogger.i(applicationContext, TAG, stopReason)
                    repo.insertLog(
                        ExecutionLog(
                            taskId = config.id,
                            channel = channelDisplayName,
                            recipient = "${item.phone} ($displayName)",
                            messageSnippet = message.take(160),
                            status = "QUEUED",
                            details = stopReason
                        )
                    )
                    break
                }

                val (canSend, quotaReason) = SmsQuotaManager.canDispatchSms(applicationContext, message)
                if (!canSend) {
                    queuedCount++
                    repo.markContactPendingRetry(item.id, whatsApp = false, status = "QUEUED")
                    repo.insertLog(
                        ExecutionLog(
                            taskId = config.id,
                            channel = channelDisplayName,
                            recipient = "${item.phone} ($displayName)",
                            messageSnippet = message.take(160),
                            status = "QUEUED",
                            details = quotaReason
                        )
                    )
                    AppFileLogger.w(applicationContext, TAG, "Stopping SMS batch at ${item.phone}: $quotaReason")
                    AppFileLogger.campaignLedger(
                        context = applicationContext,
                        campaignId = config.id,
                        channel = channelDisplayName,
                        recipientName = displayName,
                        recipientPhone = item.phone,
                        status = "QUEUED",
                        messageSnippet = message.take(80),
                        attempt = item.sendCount + 1,
                        details = quotaReason
                    )
                    break
                }

                val sendResult = smsSender?.sendSmsWithResult(item.phone, message, config.simSlot, enforceQuota = true)
                if (sendResult != null && sendResult.success) {
                    SmsQuotaManager.commitSmsQuotaReservation(sendResult.segments)
                    successCount++
                    repo.markContactSent(item.id, whatsApp = false)
                    repo.markCampaignContactSuccess(config.id, item, config.channel, sendResult.segments)
                    repo.insertLog(
                        ExecutionLog(
                            taskId = config.id,
                            channel = channelDisplayName,
                            recipient = "${item.phone} ($displayName)",
                            messageSnippet = message.take(160),
                            status = "SUCCESS",
                            details = "Source: ${item.source}, SIM: ${config.simSlot}, parts: ${sendResult.segments}",
                            segments = sendResult.segments
                        )
                    )
                    AppFileLogger.i(applicationContext, TAG, "Sent SMS to ${item.phone} ($displayName) [${sendResult.segments} parts]")
                    AppFileLogger.campaignLedger(
                        context = applicationContext,
                        campaignId = config.id,
                        channel = channelDisplayName,
                        recipientName = displayName,
                        recipientPhone = item.phone,
                        status = "SUCCESS",
                        messageSnippet = message.take(80),
                        attempt = item.sendCount + 1,
                        details = "Source: ${item.source}, SIM: ${config.simSlot}, parts: ${sendResult.segments}"
                    )
                } else if (sendResult != null && (sendResult.deliveryStatus == SmsSender.DeliveryStatus.PENDING_TIMEOUT || sendResult.deliveryStatus == SmsSender.DeliveryStatus.PARTIAL_DISPATCH)) {
                    // Cellular modem accepted dispatch (or partially dispatched) but full carrier ACK is pending.
                    // Do NOT mark as FAILED to prevent duplicate re-sends, but accurately mark as DISPATCHED in campaign ledger.
                    repo.markContactSent(item.id, whatsApp = false)
                    repo.markCampaignContactDispatched(config.id, item, config.channel, sendResult.segments)
                    repo.insertLog(
                        ExecutionLog(
                            taskId = config.id,
                            channel = channelDisplayName,
                            recipient = "${item.phone} ($displayName)",
                            messageSnippet = message.take(160),
                            status = "DISPATCHED",
                            details = if (sendResult.deliveryStatus == SmsSender.DeliveryStatus.PARTIAL_DISPATCH) sendResult.reason else "Dispatched to cellular modem via SIM ${config.simSlot} (${sendResult.segments} parts); carrier acknowledgement pending",
                            segments = sendResult.segments
                        )
                    )
                    AppFileLogger.i(applicationContext, TAG, "SMS dispatched to modem for ${item.phone} ($displayName) [${sendResult.segments} parts], carrier ack pending")
                    AppFileLogger.campaignLedger(
                        context = applicationContext,
                        campaignId = config.id,
                        channel = channelDisplayName,
                        recipientName = displayName,
                        recipientPhone = item.phone,
                        status = "DISPATCHED",
                        messageSnippet = message.take(80),
                        attempt = item.sendCount + 1,
                        details = if (sendResult.deliveryStatus == SmsSender.DeliveryStatus.PARTIAL_DISPATCH) sendResult.reason else "Dispatched to cellular modem via SIM ${config.simSlot} (${sendResult.segments} parts); carrier acknowledgement pending"
                    )
                } else {
                    failCount++
                    val reason = sendResult?.reason ?: "Failed to transmit SMS"
                    val isQuota = reason.contains("limit", ignoreCase = true) || reason.contains("quota", ignoreCase = true)
                    val status = if (isQuota) "QUEUED" else "FAILED"
                    repo.markContactPendingRetry(item.id, whatsApp = false, status = status)
                    repo.insertLog(
                        ExecutionLog(
                            taskId = config.id,
                            channel = channelDisplayName,
                            recipient = "${item.phone} ($displayName)",
                            messageSnippet = message.take(160),
                            status = status,
                            details = reason
                        )
                    )
                    AppFileLogger.w(applicationContext, TAG, "SMS outcome for ${item.phone}: $status ($reason)")
                    AppFileLogger.campaignLedger(
                        context = applicationContext,
                        campaignId = config.id,
                        channel = channelDisplayName,
                        recipientName = displayName,
                        recipientPhone = item.phone,
                        status = status,
                        messageSnippet = message.take(80),
                        attempt = item.sendCount + 1,
                        details = reason
                    )
                    if (isQuota) {
                        queuedCount++
                        break
                    }
                }
            }

            if (!isLast) {
                var elapsed = 0L
                val step = 250L
                while (elapsed < intervalMs) {
                    if (isStopped || !repo.getDailyCampaignConfig(config.id).isActive || !prefs.isTasksMasterEnabled || com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive(applicationContext)) {
                        AppFileLogger.w(applicationContext, TAG, "Daily Campaign #${config.id} interval delay aborted: Campaign paused or master switch disabled.")
                        AutoTextAccessibilityService.clearPending()
                        return Result.success()
                    }
                    val chunk = minOf(step, intervalMs - elapsed)
                    delay(chunk.milliseconds)
                    elapsed += chunk
                }
            }
        }

        if (isWhatsApp && !prefs.isAutoLockAfterSendEnabled && successCount > 0) {
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

        // If the worker was stopped or interrupted by system, do not retry! Terminate cleanly.
        if (isStopped) {
            AppFileLogger.w(applicationContext, TAG, "Daily campaign #${config.id} was stopped prematurely. Halting without retry.")
            return Result.success()
        }

        val finalConfig = repo.getDailyCampaignConfig(config.id)
        if (successCount == 0 && failCount > 0) {
            if (!finalConfig.isActive || !prefs.isTasksMasterEnabled || com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive(applicationContext)) {
                AppFileLogger.w(applicationContext, TAG, "Daily campaign #${config.id} had failures, but campaign is inactive or stopped. Will NOT retry.")
                return Result.success()
            }
            AppFileLogger.w(applicationContext, TAG, "Daily campaign #${config.id} had 0 successes and $failCount failures. Leaving lastRunDate open for retry.")
            return Result.retry()
        }

        repo.updateDailyCampaignLastRunDate(config.id, todayStr)
        AppFileLogger.action(
            applicationContext,
            TAG,
            "Daily campaign #${config.id} (${config.title}) finished. Sent=$successCount, Failed=$failCount, Queued=$queuedCount"
        )

        return Result.success()
    }
}
