package com.jmd.autotext.util

import android.content.Context
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import com.jmd.autotext.data.AppRepository
import java.util.Calendar

/**
 * Manages and enforces an application-level SMS safeguard.
 *
 * Checks device-wide sent messages across all apps (Google Messages, Truecaller,
 * Samsung Messages, AutoText) by querying Android's telephony content provider
 * (content://sms/sent) as well as the app's local logs.
 *
 * Accurately measures cellular PDU segments (concatenated SMS):
 * - 160 GSM-7 characters = 1 segment
 * - Emojis / Hindi / Unicode (UCS-2) = 70 characters (single) or 67 characters
 *   (multipart) per segment.
 *
 * Deduplicates messages between content://sms/sent and AutoText logs to prevent
 * double-counting.
 *
 * IMPORTANT — scope of the limit:
 * [INDIA_DAILY_SMS_LIMIT] is an **application-level safeguard**, not a verified
 * regulatory limit. Operator caps, TRAI DND/DLT rules and promotional-versus-
 * transactional distinctions vary by subscription, sender ID and message class,
 * and are not queryable from the device. This constant is therefore a
 * conservative, configurable ceiling intended to reduce the risk of carrier
 * blocking. It must not be presented to users as a legally verified limit, and
 * it does not by itself guarantee compliance with any regulation.
 *
 * When the safeguard is reached:
 * - Outgoing SMS automation is halted.
 * - Remaining messages are queued for the next cycle.
 */
object SmsQuotaManager {

    private const val TAG = "SmsQuotaManager"

    /**
     * Application-level daily SMS ceiling, measured in PDU segments.
     *
     * This is a safeguard, NOT a verified regulatory limit. See the class KDoc
     * for the full caveat.
     */
    const val INDIA_DAILY_SMS_LIMIT = 100

    data class QuotaStatus(
        val totalSentToday: Int,
        val limit: Int = INDIA_DAILY_SMS_LIMIT,
        val remaining: Int,
        val isLimitReached: Boolean,
        val details: String
    )

    /**
     * Calculates the millisecond timestamp for the start of today (00:00:00.000).
     */
    fun getStartOfTodayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Calculates the number of cellular SMS PDU segments required to transmit a text message.
     */
    fun calculateSmsSegments(messageText: String): Int {
        if (messageText.isEmpty()) return 1
        return try {
            @Suppress("DEPRECATION")
            val defaultSmsManager = SmsManager.getDefault()
            val parts = defaultSmsManager.divideMessage(messageText)
            maxOf(1, parts.size)
        } catch (_: Exception) {
            // Fallback estimation if telephony service is unavailable
            val hasUnicode = messageText.any { it.code > 127 }
            if (hasUnicode) {
                if (messageText.length <= 70) 1 else ((messageText.length + 66) / 67)
            } else {
                if (messageText.length <= 160) 1 else ((messageText.length + 152) / 153)
            }
        }
    }

    /**
     * Queries Android's telephony content provider (content://sms/sent)
     * and AutoText execution logs to accurately calculate the total cellular SMS segments sent today.
     * Deduplicates any rows that exist in both content://sms/sent and local logs.
     */
    fun getTotalSmsSentToday(context: Context): Int {
        val startOfToday = getStartOfTodayMillis()
        var externalSegments = 0

        // 1. Fetch AutoText's sent SMS logs for today
        val appLogs = try {
            val repo = AppRepository(context)
            repo.getLogsForToday().filter {
                it.status == "SUCCESS" &&
                it.channel.contains("SMS", ignoreCase = true)
            }
        } catch (e: Exception) {
            emptyList()
        }

        var appSegments = 0
        for (log in appLogs) {
            // Check if parts count was explicitly recorded in details (e.g. "parts: 2")
            val partsMatch = Regex("parts:\\s*(\\d+)").find(log.details)
            val segments = partsMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: calculateSmsSegments(log.messageSnippet)
            appSegments += segments
        }

        // 2. Query device-wide sent SMS via content://sms/sent
        try {
            val sentUri = Uri.parse("content://sms/sent")
            val projection = arrayOf("_id", "date", "address", "body")
            val selection = "date >= ?"
            val selectionArgs = arrayOf(startOfToday.toString())

            val cursor = context.contentResolver.query(
                sentUri,
                projection,
                selection,
                selectionArgs,
                "date ASC"
            )

            cursor?.use { c ->
                val dateIdx = c.getColumnIndex("date")
                val addrIdx = c.getColumnIndex("address")
                val bodyIdx = c.getColumnIndex("body")

                while (c.moveToNext()) {
                    val date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                    val address = if (addrIdx >= 0) c.getString(addrIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) c.getString(bodyIdx) ?: "" else ""

                    val normAddr = PhoneNumberNormalizer.normalize(address)

                    // Check if this message matches an AutoText log (sent within 20s with matching address)
                    val matchesAppLog = appLogs.any { log ->
                        val logNorm = PhoneNumberNormalizer.normalize(log.recipient)
                        Math.abs(log.timestampMillis - date) < 20_000L &&
                                (logNorm.isNotEmpty() && normAddr.isNotEmpty() && (logNorm == normAddr || logNorm.endsWith(normAddr) || normAddr.endsWith(logNorm)))
                    }

                    if (!matchesAppLog) {
                        // This message was sent by an external app (Google Messages, Truecaller, system, etc.)
                        val segments = calculateSmsSegments(body)
                        externalSegments += segments
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_SMS permission not available; using app logs exclusively: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Error querying content://sms/sent: ${e.message}", e)
        }

        val total = externalSegments + appSegments
        Log.d(TAG, "SMS Quota calculation today: external=$externalSegments, app=$appSegments, total=$total")
        return total
    }

    /**
     * Returns the remaining SMS quota for today under the app-level 100 SMS/day safeguard.
     */
    fun getRemainingSmsQuota(context: Context): Int {
        val sent = getTotalSmsSentToday(context)
        return maxOf(0, INDIA_DAILY_SMS_LIMIT - sent)
    }

    /**
     * Returns the complete quota status report.
     */
    fun getQuotaStatus(context: Context): QuotaStatus {
        val sent = getTotalSmsSentToday(context)
        val remaining = maxOf(0, INDIA_DAILY_SMS_LIMIT - sent)
        val isReached = remaining <= 0

        val details = if (isReached) {
            "Daily limit of $INDIA_DAILY_SMS_LIMIT SMS reached across device ($sent sent today). Automated SMS is paused to prevent carrier blocking."
        } else {
            "$sent / $INDIA_DAILY_SMS_LIMIT SMS sent today across device. $remaining SMS remaining in quota."
        }

        return QuotaStatus(
            totalSentToday = sent,
            limit = INDIA_DAILY_SMS_LIMIT,
            remaining = remaining,
            isLimitReached = isReached,
            details = details
        )
    }

    /**
     * Evaluates if an SMS message of given text can be sent right now.
     * Factors message segment count (e.g. Hindi / Unicode / 160+ chars split into 2+ parts).
     *
     * @return Pair(canSend, reason)
     */
    fun canDispatchSms(context: Context, messageText: String): Pair<Boolean, String> {
        val status = getQuotaStatus(context)
        if (status.isLimitReached) {
            return Pair(
                false,
                "App daily safeguard reached ($INDIA_DAILY_SMS_LIMIT/100 SMS sent today across device). Queued for next cycle."
            )
        }

        val partsCount = calculateSmsSegments(messageText)

        if (status.remaining < partsCount) {
            return Pair(
                false,
                "Message requires $partsCount SMS parts, but only ${status.remaining} remaining today. Queued to prevent carrier penalty."
            )
        }

        return Pair(true, "OK ($partsCount parts, ${status.remaining} quota remaining)")
    }
}
