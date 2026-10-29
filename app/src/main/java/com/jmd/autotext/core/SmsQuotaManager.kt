/**
 * Package: com.jmd.autotext.core
 * Module: Core Automation Utilities
 *
 * Provides cellular SMS quota tracking, TRAI/carrier daily limit enforcement (100 SMS/day safeguard),
 * multi-part PDU segment calculation, provider reconciliation, and in-flight reservation management.
 */
package com.jmd.autotext.core

import android.content.Context
import androidx.core.net.toUri
import android.util.Log
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.data.prefs.PreferenceManager
import java.util.Calendar
import kotlin.math.abs

/**
 * Enterprise manager governing cellular SMS quota consumption.
 *
 * Integrates internal execution logs with device-wide `content://sms/sent` records
 * to track both automated and user-sent SMS segments against carrier thresholds.
 */
object SmsQuotaManager {
    private const val TAG = "SmsQuotaManager"

    /** Standard regulatory safety ceiling (TRAI India default: 100 SMS per SIM per day). */
    const val INDIA_DAILY_SMS_LIMIT = 100

    /**
     * Snapshot model of current SMS quota consumption.
     *
     * @property totalSentToday Total SMS segments dispatched today across device.
     * @property limit Configured daily maximum SMS threshold.
     * @property remaining Unused SMS quota balance available today.
     * @property isLimitReached True if remaining balance is zero or exhausted.
     * @property details Diagnostic description string for user display.
     */
    data class QuotaStatus(
        val totalSentToday: Int,
        val limit: Int = INDIA_DAILY_SMS_LIMIT,
        val remaining: Int,
        val isLimitReached: Boolean,
        val details: String,
    )

    /**
     * Computes epoch millisecond timestamp representing midnight (00:00:00.000) of the current day.
     *
     * @return Milliseconds timestamp at start of today.
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
     * Calculates the number of 160-character (GSM 7-bit) or 70-character (UCS-2 Unicode) segments
     * required to transmit the given text message payload.
     *
     * @param messageText Draft message string.
     * @param context Active Android [Context] for SmsManager calculation.
     * @return Number of PDU segments (minimum 1).
     */
    fun calculateSmsSegments(messageText: String, context: Context? = null): Int {
        if (messageText.isEmpty()) return 1
        return try {
            val smsManager = com.jmd.autotext.compat.SmsManagerCompat.getDefault(context)
            val parts = smsManager.divideMessage(messageText) ?: emptyList()
            maxOf(1, parts.size)
        } catch (_: Exception) {
            val hasUnicode = messageText.any { it.code > 127 }
            if (hasUnicode) {
                if (messageText.length <= 70) 1 else ((messageText.length + 66) / 67)
            } else {
                if (messageText.length <= 160) 1 else ((messageText.length + 152) / 153)
            }
        }
    }

    /**
     * Reconciles total SMS segments sent today across both the application's internal database
     * and external messages recorded in system `content://sms/sent` content provider.
     *
     * @param context Active Android [Context].
     * @return Total integer segments dispatched today across the device.
     */
    fun getTotalSmsSentToday(context: Context): Int {
        val startOfToday = getStartOfTodayMillis()
        var externalSegments = 0

        val appLogs = try {
            val repo = AppRepository(context)
            repo.getLogsForToday().filter { log ->
                ((log.status == "SUCCESS") || (log.status == "DISPATCHED")) &&
                    log.channel.contains("SMS", ignoreCase = true)
            }
        } catch (_: Exception) {
            emptyList()
        }

        var appSegments = 0
        for ((_, _, _, _, messageSnippet, _, details, segmentsFromLog) in appLogs) {
            val segments = if (segmentsFromLog > 0) {
                segmentsFromLog
            } else {
                val partsMatch = Regex("(?:parts|segments):\\s*(\\d+)").find(details)
                partsMatch?.groupValues?.get(1)?.toIntOrNull() ?: calculateSmsSegments(messageSnippet)
            }
            appSegments += segments
        }

        try {
            val sentUri = "content://sms/sent".toUri()
            val projection = arrayOf("_id", "date", "address", "body")
            val selection = "date >= ?"
            val selectionArgs = arrayOf(startOfToday.toString())

            val cursor = context.contentResolver.query(
                sentUri,
                projection,
                selection,
                selectionArgs,
                "date ASC",
            )

            val availableAppLogs = appLogs.toMutableList()

            cursor?.use { c ->
                val dateIdx = c.getColumnIndex("date")
                val addrIdx = c.getColumnIndex("address")
                val bodyIdx = c.getColumnIndex("body")

                while (c.moveToNext()) {
                    val date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                    val address = if (addrIdx >= 0) c.getString(addrIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) c.getString(bodyIdx) ?: "" else ""

                    val normAddr = PhoneNumberNormalizer.normalize(address)

                    val matchedLog = availableAppLogs.firstOrNull { log ->
                        val logNorm = PhoneNumberNormalizer.normalize(log.recipient)
                        abs(log.timestampMillis - date) < 20_000L &&
                                (logNorm.isNotEmpty() && normAddr.isNotEmpty() && (logNorm == normAddr || logNorm.endsWith(normAddr) || normAddr.endsWith(logNorm)))
                    }

                    if (matchedLog != null) {
                        availableAppLogs.remove(matchedLog)
                    } else {
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

    /** Short alias returning remaining SMS quota balance. */
    fun quota(context: Context): Int = getRemainingSmsQuota(context)

    /** Short alias calculating required SMS segments for a text string. */
    fun segments(text: String): Int = calculateSmsSegments(text)

    /** Short alias returning complete [QuotaStatus]. */
    fun status(context: Context): QuotaStatus = getQuotaStatus(context)

    /**
     * Checks if cellular SMS regulatory quota enforcement is actively enabled.
     * When false, enables unlimited personal carrier plan dispatch.
     */
    fun isQuotaEnforcementEnabled(context: Context): Boolean {
        return try {
            PreferenceManager(context).isSmsQuotaLimitEnabled
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Resolves configured daily limit from shared preferences, falling back to 100.
     *
     * @param context Active Android [Context].
     * @return Daily limit integer.
     */
    fun getQuotaLimit(context: Context): Int {
        if (!isQuotaEnforcementEnabled(context)) return Int.MAX_VALUE
        return try {
            PreferenceManager(context).dailySmsSafetyLimit
        } catch (_: Exception) {
            INDIA_DAILY_SMS_LIMIT
        }
    }

    /**
     * Calculates unused SMS quota remaining for today.
     *
     * @param context Active Android [Context].
     * @return Remaining quota integer (clamped to 0).
     */
    fun getRemainingSmsQuota(context: Context): Int {
        if (!isQuotaEnforcementEnabled(context)) return Int.MAX_VALUE
        val sent = getTotalSmsSentToday(context)
        val limit = getQuotaLimit(context)
        return maxOf(0, limit - sent)
    }

    /**
     * Obtains a detailed [QuotaStatus] descriptor.
     *
     * @param context Active Android [Context].
     * @return [QuotaStatus] detailing sent counts, limit, remaining balance, and warnings.
     */
    fun getQuotaStatus(context: Context): QuotaStatus {
        val sent = getTotalSmsSentToday(context)
        if (!isQuotaEnforcementEnabled(context)) {
            return QuotaStatus(
                totalSentToday = sent,
                limit = Int.MAX_VALUE,
                remaining = Int.MAX_VALUE,
                isLimitReached = false,
                details = "$sent SMS sent today across device. Unlimited personal carrier quota mode active.",
            )
        }

        val limit = getQuotaLimit(context)
        val remaining = maxOf(0, limit - sent)
        val isReached = remaining <= 0

        val details = if (isReached) {
            "Daily limit of $limit SMS reached across device ($sent sent today). Automated SMS is paused to prevent carrier blocking."
        } else {
            "$sent / $limit SMS sent today across device. $remaining SMS remaining in quota."
        }

        return QuotaStatus(
            totalSentToday = sent,
            limit = limit,
            remaining = remaining,
            isLimitReached = isReached,
            details = details,
        )
    }

    @Volatile
    private var inFlightReservedSegments: Int = 0
    @Volatile
    private var lastQuotaDayStart: Long = getStartOfTodayMillis()

    @Synchronized
    private fun checkDateRollover() {
        val currentDayStart = getStartOfTodayMillis()
        if (currentDayStart != lastQuotaDayStart) {
            inFlightReservedSegments = 0
            lastQuotaDayStart = currentDayStart
        }
    }

    /**
     * Atomically reserves a block of SMS segments for an in-flight dispatch pipeline.
     * Prevents race conditions during high-volume batch triggers.
     *
     * @param context Active Android [Context].
     * @param partsCount Number of segments to hold.
     * @return Pair of (True if reservation granted, diagnostic message).
     */
    @Synchronized
    fun reserveSmsQuota(context: Context, partsCount: Int): Pair<Boolean, String> {
        checkDateRollover()
        if (!isQuotaEnforcementEnabled(context)) {
            return Pair(true, "Unlimited SMS mode active (reserved $partsCount segments)")
        }
        val status = getQuotaStatus(context)
        val available = maxOf(0, status.remaining - inFlightReservedSegments)
        if (status.isLimitReached || available < partsCount) {
            val msg = if (status.isLimitReached) {
                "App daily safeguard reached (${status.limit}/${status.limit} SMS sent today across device). Queued for next cycle."
            } else {
                "Message requires $partsCount SMS parts, but only $available remaining today (in-flight: $inFlightReservedSegments). Queued to prevent carrier penalty."
            }
            return Pair(false, msg)
        }
        inFlightReservedSegments += partsCount
        return Pair(true, "Reserved $partsCount segments")
    }

    /**
     * Releases an in-flight quota reservation if dispatch was aborted or failed before carrier handoff.
     *
     * @param partsCount Segments to return to available balance.
     */
    @Synchronized
    fun releaseSmsQuotaReservation(partsCount: Int) {
        checkDateRollover()
        inFlightReservedSegments = maxOf(0, inFlightReservedSegments - partsCount)
    }

    /**
     * Commits an in-flight reservation upon successful carrier submission.
     *
     * @param partsCount Segments committed.
     */
    @Synchronized
    fun commitSmsQuotaReservation(partsCount: Int) {
        checkDateRollover()
        inFlightReservedSegments = maxOf(0, inFlightReservedSegments - partsCount)
    }

    /**
     * Quick verification check determining whether a message can be dispatched without exceeding quota.
     *
     * @param context Active Android [Context].
     * @param messageText Text payload to evaluate.
     * @return Pair of (Boolean eligibility, status message).
     */
    @Synchronized
    fun canDispatchSms(context: Context, messageText: String): Pair<Boolean, String> {
        val partsCount = calculateSmsSegments(messageText)
        if (!isQuotaEnforcementEnabled(context)) {
            return Pair(true, "OK ($partsCount parts, Unlimited quota mode)")
        }
        val status = getQuotaStatus(context)
        val available = maxOf(0, status.remaining - inFlightReservedSegments)

        if (available < partsCount) {
            return Pair(
                false,
                "Message requires $partsCount SMS parts, but only $available remaining today. Queued to prevent carrier penalty."
            )
        }

        return Pair(true, "OK ($partsCount parts, $available quota remaining)")
    }
}
