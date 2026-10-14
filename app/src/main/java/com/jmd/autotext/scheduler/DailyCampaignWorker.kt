package com.jmd.autotext.scheduler

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ExecutionLog
import com.jmd.autotext.scanner.ContactIngestionEngine
import com.jmd.autotext.sender.SmsSender
import com.jmd.autotext.sender.WhatsAppAutomationSender
import com.jmd.autotext.service.AccessibilityHealthManager
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.util.AppFileLogger
import com.jmd.autotext.util.SmsQuotaManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyCampaignWorker(
    appContext: Context,
    params: WorkerParameters
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
        val isManual = inputData.getBoolean(KEY_MANUAL_TRIGGER, false)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = dateFormat.format(Date())

        AppFileLogger.i(
            applicationContext,
            TAG,
            "Starting DailyCampaignWorker for id=$campaignId (${config.title}), manual=$isManual, active=${config.isActive}"
        )

        if (!isManual && !config.isActive) {
            AppFileLogger.i(applicationContext, TAG, "Campaign is inactive and not manual. Skipping.")
            return Result.success()
        }

        // Prevent periodic scheduler from running twice in the same day
        if (!isManual && config.lastRunDate == todayStr) {
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

        // 1. Permission and service health check
        if (isWhatsApp) {
            val isA11yActive = AccessibilityHealthManager.ensureServiceAlive(applicationContext)
            if (!isA11yActive) {
                val errorMsg = "Accessibility permission is required for automated WhatsApp message delivery."
                repo.insertLog(
                    ExecutionLog(
                        taskId = config.id,
                        channel = channelDisplayName,
                        recipient = "N/A",
                        messageSnippet = "Accessibility Service is disabled or binder dropped",
                        status = "FAILED",
                        details = errorMsg
                    )
                )
                AppFileLogger.e(applicationContext, TAG, errorMsg)
                return Result.failure()
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

        // 2. Campaign Daily Limit & application-level 100 SMS safeguard check
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

            // Estimate segments per message to avoid fetching excess recipients
            val estimatedSegments = SmsQuotaManager.calculateSmsSegments(config.messageTemplate)
            val maxContactsForCampaign = maxOf(1, campaignRemaining / maxOf(1, estimatedSegments))
            val maxContactsForQuota = maxOf(1, quotaStatus.remaining / maxOf(1, estimatedSegments))

            // Cap batch to minimum of campaign remaining contacts and app-safeguard remaining quota
            allowedBatchSize = minOf(maxContactsForCampaign, maxContactsForQuota)
        }

        // 3. Auto-scan contacts, SMS, and Call Log if enabled
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

        // 4. Fetch recipients: priority order delivers failed/queued contacts FIRST, then uncontacted numbers
        val recipients = repo.getUnsentRecipientsForDailyBatch(limit = allowedBatchSize, whatsApp = isWhatsApp)
        if (recipients.isEmpty()) {
            AppFileLogger.i(applicationContext, TAG, "No recipients in pool to send daily campaign to.")
            return Result.success()
        }

        val prefs = PreferenceManager(applicationContext)
        val smsSender = if (!isWhatsApp) SmsSender(applicationContext) else null
        val waSender = if (isWhatsApp) WhatsAppAutomationSender(applicationContext) else null
        val dateDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        val timeDisplay = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        var successCount = 0
        var failCount = 0
        var queuedCount = 0

        val minInterval = if (isWhatsApp) 10 else 4
        val intervalMs = (config.intervalSeconds.coerceAtLeast(minInterval)) * 1000L
        val totalCount = recipients.size

        AppFileLogger.i(
            applicationContext,
            TAG,
            "Commencing dispatch of $totalCount recipients for $channelDisplayName. Interval: ${intervalMs}ms"
        )

        for ((index, item) in recipients.withIndex()) {
            if (isStopped) {
                AppFileLogger.w(applicationContext, TAG, "DailyCampaignWorker was stopped by system")
                break
            }

            val displayName = item.name.ifBlank { "Friend" }
            val message = config.messageTemplate
                .replace("{name}", displayName, ignoreCase = true)
                .replace("{first_name}", displayName.split(" ").firstOrNull() ?: displayName, ignoreCase = true)
                .replace("{phone}", item.phone, ignoreCase = true)
                .replace("{date}", dateDisplay, ignoreCase = true)
                .replace("{time}", timeDisplay, ignoreCase = true)

            val isLast = index == totalCount - 1

            if (isWhatsApp) {
                val waChannel = if (config.channel.contains("BUSINESS", ignoreCase = true)) {
                    ChannelType.WHATSAPP_BUSINESS
                } else {
                    ChannelType.WHATSAPP
                }

                val shouldRelockThisItem = isLast && prefs.isAutoLockAfterSendEnabled

                AutoTextAccessibilityService.clearPending()
                AutoTextAccessibilityService.markDispatchPending()

                val dispatched = waSender?.send(
                    taskId = config.id,
                    channel = waChannel,
                    recipientPhone = item.phone,
                    messageText = message,
                    shouldRelock = shouldRelockThisItem,
                    isBatchCampaign = true
                ) ?: false

                if (!dispatched) {
                    failCount++
                    repo.markContactPendingRetry(item.id, whatsApp = true, status = "FAILED")
                    repo.insertLog(
                        ExecutionLog(
                            taskId = config.id,
                            channel = channelDisplayName,
                            recipient = "${item.phone} ($displayName)",
                            messageSnippet = message.take(60),
                            status = "FAILED",
                            details = "Failed to launch WhatsApp automation intent (kept in retry queue)"
                        )
                    )
                    AppFileLogger.w(applicationContext, TAG, "Failed launching WhatsApp automation for ${item.phone}")
                    AppFileLogger.campaignLedger(
                        context = applicationContext,
                        campaignId = config.id,
                        channel = channelDisplayName,
                        recipientName = displayName,
                        recipientPhone = item.phone,
                        status = "FAILED",
                        messageSnippet = message.take(80),
                        attempt = item.waSendCount + 1,
                        details = "Failed to launch WhatsApp automation intent (kept in retry queue)"
                    )
                } else {
                    // Synchronously wait for accessibility service outcome (up to 16 seconds)
                    val maxWaitMs = 16000L
                    var waitedMs = 0L
                    while (AutoTextAccessibilityService.lastSendResult == AutoTextAccessibilityService.RESULT_PENDING && waitedMs < maxWaitMs) {
                        if (isStopped) break
                        delay(500L)
                        waitedMs += 500L
                    }

                    when (AutoTextAccessibilityService.lastSendResult) {
                        AutoTextAccessibilityService.RESULT_SUCCESS -> {
                            successCount++
                            repo.markContactSent(item.id, whatsApp = true)
                            AppFileLogger.i(applicationContext, TAG, "Delivered WhatsApp message to ${item.phone} ($displayName)")
                            AppFileLogger.campaignLedger(
                                context = applicationContext,
                                campaignId = config.id,
                                channel = channelDisplayName,
                                recipientName = displayName,
                                recipientPhone = item.phone,
                                status = "SUCCESS",
                                messageSnippet = message.take(80),
                                attempt = item.waSendCount + 1,
                                details = "Batch campaign dispatch delivered via AutoText Service"
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
                                attempt = item.waSendCount + 1,
                                details = "Recipient not registered on WhatsApp or invalid phone number"
                            )
                        }
                        else -> {
                            // Timeout or failure: Keep in queue to retry first on next cycle!
                            failCount++
                            repo.markContactPendingRetry(item.id, whatsApp = true, status = "FAILED")
                            repo.insertLog(
                                ExecutionLog(
                                    taskId = config.id,
                                    channel = channelDisplayName,
                                    recipient = "${item.phone} ($displayName)",
                                    messageSnippet = message.take(60),
                                    status = "FAILED",
                                    details = "WhatsApp automation timed out (contact kept in queue to retry first next cycle)"
                                )
                            )
                            AppFileLogger.w(applicationContext, TAG, "WhatsApp automation timed out for ${item.phone}. Added to priority retry.")
                            AppFileLogger.campaignLedger(
                                context = applicationContext,
                                campaignId = config.id,
                                channel = channelDisplayName,
                                recipientName = displayName,
                                recipientPhone = item.phone,
                                status = "TIMEOUT",
                                messageSnippet = message.take(80),
                                attempt = item.waSendCount + 1,
                                details = "WhatsApp automation timed out (contact kept in priority queue to retry)"
                            )
                        }
                    }

                    // Reset WhatsApp to conversation list for the next recipient in batch mode
                    if (!isLast) {
                        delay(450L)
                        AutoTextAccessibilityService.navigateBack()
                        delay(350L)
                    }
                }
            } else {
                // SMS Mode: strictly verify remaining quota before every individual message
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
                    // Stop further SMS dispatches for today
                    break
                }

                val sendResult = smsSender?.sendSmsWithResult(item.phone, message, config.simSlot, enforceQuota = true)
                if (sendResult != null && sendResult.success) {
                    successCount++
                    repo.markContactSent(item.id, whatsApp = false)
                    repo.insertLog(
                        ExecutionLog(
                            taskId = config.id,
                            channel = channelDisplayName,
                            recipient = "${item.phone} ($displayName)",
                            messageSnippet = message.take(160),
                            status = "SUCCESS",
                            details = "Source: ${item.source}, SIM: ${config.simSlot}, parts: ${sendResult.segments}"
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

            // Interval delay between messages
            if (!isLast) {
                delay(intervalMs)
            }
        }

        if (isWhatsApp && !prefs.isAutoLockAfterSendEnabled && successCount > 0) {
            delay(1200L)
            try {
                val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_HOME)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                applicationContext.startActivity(homeIntent)
            } catch (ignored: Exception) {}
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
