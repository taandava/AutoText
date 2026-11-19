/**
 * Package: com.jmd.supersms.scheduler
 * Module: Schedulers & Background Workers
 *
 * What it is:
 * Background WorkManager [CoroutineWorker] executing scheduled daily SMS outreach batch campaigns.
 */

package com.jmd.supersms.scheduler

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jmd.supersms.core.AppFileLogger
import com.jmd.supersms.core.MessageTemplateResolver
import com.jmd.supersms.core.SmsQuotaManager
import com.jmd.supersms.data.model.ExecutionLog
import com.jmd.supersms.data.prefs.PreferenceManager
import com.jmd.supersms.data.repo.AppRepository
import com.jmd.supersms.engine.ContactIngestionEngine
import com.jmd.supersms.engine.SmsSender
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

        val channelDisplayName = "SMS Campaign"

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

        val alreadySentCampaign = repo.getSentSegmentsForCampaignToday(config.id)
        val campaignRemaining = maxOf(0, config.dailyLimit - alreadySentCampaign)

        if (campaignRemaining <= 0) {
            val limitMsg = "Daily limit of ${config.dailyLimit} reached today for campaign '${config.title}' ($alreadySentCampaign already sent today). Pausing batch."
            AppFileLogger.i(applicationContext, TAG, limitMsg)
            return Result.success()
        }

        val quotaStatus = SmsQuotaManager.getQuotaStatus(applicationContext)
        AppFileLogger.i(
            applicationContext,
            TAG,
            "SMS Quota check: ${quotaStatus.totalSentToday}/${quotaStatus.limit} sent today. Remaining: ${quotaStatus.remaining}. Campaign remaining allowance: $campaignRemaining segments."
        )

        if (quotaStatus.isLimitReached) {
            val limitMsg = "Daily safeguard reached (${quotaStatus.totalSentToday} sent today). Batch queued for tomorrow."
            repo.insertLog(
                ExecutionLog(
                    taskId = config.id,
                    channel = channelDisplayName,
                    recipient = "All Queued Contacts",
                    messageSnippet = "Daily SMS Quota Exceeded",
                    status = "QUEUED",
                    details = limitMsg
                )
            )
            AppFileLogger.w(applicationContext, TAG, limitMsg)
            return Result.success()
        }

        val estimatedSegments = maxOf(1, SmsQuotaManager.calculateSmsSegments(config.message))
        val maxContactsForCampaign = campaignRemaining / estimatedSegments
        val maxContactsForQuota = quotaStatus.remaining / estimatedSegments

        if (maxContactsForCampaign <= 0 || maxContactsForQuota <= 0) {
            val limitMsg = "Campaign daily limit reached or estimated segments exceed remaining capacity."
            AppFileLogger.i(applicationContext, TAG, limitMsg)
            return Result.success()
        }

        val allowedBatchSize = minOf(maxContactsForCampaign, maxContactsForQuota)

        if (config.autoScanEnabled) {
            try {
                val scanner = ContactIngestionEngine(applicationContext, repo)
                val summary = scanner.runFullIngestion()
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

        val recipients = repo.getPendingPoolContactsForCampaign(campaignId = config.id, isWhatsApp = false, limit = allowedBatchSize)
        if (recipients.isEmpty()) {
            AppFileLogger.i(applicationContext, TAG, "No recipients in pool to send daily campaign to.")
            return Result.success()
        }

        val smsSender = SmsSender(applicationContext)
        var successCount = 0
        var failCount = 0
        var queuedCount = 0

        val intervalMs = (config.intervalSeconds.coerceAtLeast(4)) * 1000L
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
                return Result.success()
            }
            if (isStopped) {
                AppFileLogger.w(applicationContext, TAG, "DailyCampaignWorker stopped by system.")
                return Result.success()
            }

            if (com.jmd.supersms.telephony.TelephonyCallEngine.isCallActive(applicationContext)) {
                AppFileLogger.i(applicationContext, TAG, "Daily Campaign paused: Active phone call in progress.")
                var callWaitElapsedMs = 0L
                val maxCallWaitMs = 180_000L
                while (com.jmd.supersms.telephony.TelephonyCallEngine.isCallActive(applicationContext) && callWaitElapsedMs < maxCallWaitMs) {
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
                phone = item.phone,
                targetTimestamp = System.currentTimeMillis()
            )

            val isLast = index == totalCount - 1

            val exactMessageSegments = SmsQuotaManager.calculateSmsSegments(message)
            val sentSoFarToday = repo.getSentSegmentsForCampaignToday(config.id)
            if (sentSoFarToday + exactMessageSegments > config.dailyLimit) {
                queuedCount++
                val stopReason = "Campaign daily limit reached ($sentSoFarToday/${config.dailyLimit} segments sent)."
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
                break
            }

            val sendResult = smsSender.sendSmsWithResult(item.phone, message, config.simSlot, enforceQuota = true)
            if (sendResult.success) {
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
            } else if (sendResult.deliveryStatus == SmsSender.DeliveryStatus.PENDING_TIMEOUT || sendResult.deliveryStatus == SmsSender.DeliveryStatus.PARTIAL_DISPATCH) {
                repo.markContactSent(item.id, whatsApp = false)
                repo.markCampaignContactDispatched(config.id, item, config.channel, sendResult.segments)
                repo.insertLog(
                    ExecutionLog(
                        taskId = config.id,
                        channel = channelDisplayName,
                        recipient = "${item.phone} ($displayName)",
                        messageSnippet = message.take(160),
                        status = "DISPATCHED",
                        details = sendResult.reason,
                        segments = sendResult.segments
                    )
                )
                AppFileLogger.i(applicationContext, TAG, "SMS dispatched to modem for ${item.phone} ($displayName)")
            } else {
                failCount++
                val reason = sendResult.reason
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
                if (isQuota) {
                    queuedCount++
                    break
                }
            }

            if (!isLast) {
                var elapsed = 0L
                val step = 250L
                while (elapsed < intervalMs) {
                    if (isStopped || !repo.getDailyCampaignConfig(config.id).isActive) {
                        return Result.success()
                    }
                    val chunk = minOf(step, intervalMs - elapsed)
                    delay(chunk.milliseconds)
                    elapsed += chunk
                }
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
            "Daily campaign #${config.id} (${config.title}) finished. Sent=$successCount, Failed=$failCount, Queued=$queuedCount"
        )

        Result.success()
    }
}
