/**
 * Package: com.jmd.autotext.receiver
 * Module: Broadcast Receivers Layer
 *
 * Monitors telephony call state transitions (RINGING, OFFHOOK, IDLE) to trigger automated SMS
 * replies for missed calls, answered and ended incoming calls, or connected and ended outgoing calls.
 *
 * Accurately distinguishes between:
 * 1. Missed Incoming Calls (rings but not answered) -> Triggers MISSED_CALL auto-reply.
 * 2. Answered & Disconnected Incoming Calls (conversation occurred) -> Triggers CALL_ENDED auto-reply.
 * 3. Answered & Disconnected Outgoing Calls (conversation occurred, duration > 0) -> Triggers CALL_ENDED auto-reply.
 * 4. Unconnected Outgoing Calls (dialed by user but not answered, rejected, or cancelled, duration == 0) -> Strictly IGNORED (no auto-reply).
 *
 * Linked With:
 * - Compat: [com.jmd.autotext.compat.TelephonyManagerCompat]
 * - Repositories: [com.jmd.autotext.data.repo.AppRepository], [com.jmd.autotext.data.repo.AutoReplyRepository]
 * - Senders: [com.jmd.autotext.engine.SmsSender]
 * - Templates: [com.jmd.autotext.core.MessageTemplateResolver]
 * - Quotas: [com.jmd.autotext.core.SmsQuotaManager]
 */
package com.jmd.autotext.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import com.jmd.autotext.core.AutomationSafetyManager
import com.jmd.autotext.core.MessageTemplateResolver
import com.jmd.autotext.core.PhoneNumberNormalizer
import com.jmd.autotext.core.SmsQuotaManager
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.ExecutionLog
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.engine.SmsSender
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Broadcast receiver monitoring telephony calls to execute configured auto-replies.
 */
class PhoneCallReceiver : BroadcastReceiver() {

    data class CallRecord(
        val number: String,
        val type: Int,
        val durationSeconds: Long,
        val timestamp: Long
    )

    companion object {
        private const val TAG = "PhoneCallReceiver"
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var savedIncomingNumber: String? = null
        private var wasRinging = false
        private var wasAnswered = false
    }

    /**
     * Intercepts `ACTION_PHONE_STATE_CHANGED` broadcasts to detect state transitions
     * between RINGING, OFFHOOK, and IDLE.
     *
     * Identifies missed calls and completed incoming/outgoing calls, dispatches background coroutines
     * using [goAsync], and evaluates enabled auto-reply rules against time windows and contact filters.
     *
     * @param context Active application context.
     * @param intent Incoming telephony broadcast intent.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val numberFromIntent = intent.getStringExtra("incoming_number")

        if (!numberFromIntent.isNullOrEmpty()) {
            savedIncomingNumber = numberFromIntent
        }

        Log.d(TAG, "Phone State Change: $lastState -> $state, IncomingNumber: $savedIncomingNumber")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                wasRinging = true
                wasAnswered = false
                Log.d(TAG, "Incoming phone ringing from: $savedIncomingNumber")
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (wasRinging) {
                    wasAnswered = true
                }
                Log.d(TAG, "Phone offhook. wasRinging: $wasRinging, wasAnswered: $wasAnswered")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val cachedNumber = savedIncomingNumber
                val capturedLastState = lastState
                val capturedWasRinging = wasRinging
                val capturedWasAnswered = wasAnswered

                wasRinging = false
                wasAnswered = false
                savedIncomingNumber = null

                // Either phone was ringing before going idle (missed/declined call),
                // or phone was offhook before going idle (call ended or outgoing call aborted).
                val isPotentialEvent = (capturedLastState == TelephonyManager.EXTRA_STATE_RINGING) ||
                    (capturedLastState == TelephonyManager.EXTRA_STATE_OFFHOOK)

                if (isPotentialEvent) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            handleCallTransition(
                                context = context,
                                cachedNumber = cachedNumber,
                                capturedLastState = capturedLastState,
                                capturedWasRinging = capturedWasRinging,
                                capturedWasAnswered = capturedWasAnswered
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling call auto-reply in background", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                    lastState = state
                    return
                }
            }
        }
        lastState = state
    }

    private suspend fun handleCallTransition(
        context: Context,
        cachedNumber: String?,
        capturedLastState: String,
        capturedWasRinging: Boolean,
        capturedWasAnswered: Boolean
    ) {
        val idleTimestamp = System.currentTimeMillis()
        var callRecord: CallRecord? = null

        // Poll CallLog with up to 3 retries (total ~750ms) to allow Android telephony provider
        // to insert the final call record with accurate duration
        for (attempt in 0..2) {
            val latest = queryLatestCallLog(context)
            if (latest != null) {
                val ageMs = idleTimestamp - latest.timestamp
                if (ageMs in -5000L..30_000L) {
                    callRecord = latest
                    break
                }
            }
            if (attempt < 2) {
                delay(250L.milliseconds)
            }
        }

        val targetNumber = callRecord?.number?.ifBlank { null } ?: cachedNumber
        if (targetNumber.isNullOrEmpty()) {
            Log.d(TAG, "No valid phone number resolved for call event. Skipping.")
            return
        }

        val isMissedCall: Boolean
        val isCallEnded: Boolean

        if (callRecord != null && (idleTimestamp - callRecord.timestamp in -5000L..30_000L)) {
            when (callRecord.type) {
                CallLog.Calls.OUTGOING_TYPE -> {
                    if (callRecord.durationSeconds > 0) {
                        // Outgoing call was answered and connected! Now ended.
                        Log.i(TAG, "Outgoing call to $targetNumber was connected (duration: ${callRecord.durationSeconds}s) and ended.")
                        isMissedCall = false
                        isCallEnded = true
                    } else {
                        // User dialed an outgoing call, but it was NOT connected (duration = 0).
                        // Recipient didn't pick up, busy, rejected, or user hung up during ringing.
                        Log.i(TAG, "Outgoing call to $targetNumber was NOT connected (duration: 0s). Skipping auto-reply.")
                        isMissedCall = false
                        isCallEnded = false
                    }
                }
                CallLog.Calls.INCOMING_TYPE -> {
                    if (callRecord.durationSeconds > 0 || capturedWasAnswered) {
                        Log.i(TAG, "Incoming call from $targetNumber was answered (duration: ${callRecord.durationSeconds}s) and ended.")
                        isMissedCall = false
                        isCallEnded = true
                    } else {
                        Log.i(TAG, "Incoming call from $targetNumber was not answered (duration: 0s). Treating as missed call.")
                        isMissedCall = true
                        isCallEnded = false
                    }
                }
                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> {
                    Log.i(TAG, "Incoming call from $targetNumber was missed or rejected (type: ${callRecord.type}).")
                    isMissedCall = true
                    isCallEnded = false
                }
                else -> {
                    if (callRecord.durationSeconds > 0) {
                        isMissedCall = false
                        isCallEnded = true
                    } else if (capturedWasRinging && !capturedWasAnswered) {
                        isMissedCall = true
                        isCallEnded = false
                    } else {
                        isMissedCall = false
                        isCallEnded = false
                    }
                }
            }
        } else {
            // Fallback when CallLog entry is unavailable or unreadable
            if (capturedWasRinging && !capturedWasAnswered) {
                Log.i(TAG, "Fallback: Inbound ringing without answer detected -> Missed Call.")
                isMissedCall = true
                isCallEnded = false
            } else if (capturedWasRinging && capturedWasAnswered) {
                Log.i(TAG, "Fallback: Inbound ringing with answer detected -> Call Ended.")
                isMissedCall = false
                isCallEnded = true
            } else {
                // Outgoing call without CallLog duration confirmation:
                // DEFENSIVELY IGNORE to prevent false auto-replies on unanswered dials!
                Log.w(TAG, "Fallback: Outgoing call without CallLog duration verification. Skipping to avoid false trigger on unanswered dial.")
                isMissedCall = false
                isCallEnded = false
            }
        }

        if (!isMissedCall && !isCallEnded) {
            Log.d(TAG, "Call event on $targetNumber is neither missed incoming nor connected ended. No auto-reply needed.")
            return
        }

        handleCallAutoReply(context, targetNumber, isMissedCall = isMissedCall, isCallEnded = isCallEnded)
    }

    private fun queryLatestCallLog(context: Context): CallRecord? {
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val number = it.getString(0) ?: ""
                    val type = it.getInt(1)
                    val duration = it.getLong(2)
                    val date = it.getLong(3)
                    CallRecord(number, type, duration, date)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch CallLog: ${e.message}")
            null
        }
    }

    private fun handleCallAutoReply(
        context: Context,
        callerNumber: String,
        isMissedCall: Boolean,
        isCallEnded: Boolean
    ) {
        val eventName = if (isMissedCall) "Missed Call" else "Call Ended"
        val channelLabel = "$eventName Auto-Reply"
        val repository = AppRepository(context)
        val prefs = PreferenceManager(context)

        if (!prefs.isAutoReplyMasterEnabled) {
            Log.d(TAG, "Skipping call auto-reply: Auto-Reply Responder is disabled by master switch")
            return
        }
        if (AutomationSafetyManager.isEmergencyStopActive(context)) {
            Log.d(TAG, "Skipping call auto-reply: Automation Emergency Stop is active")
            return
        }

        if (!PhoneNumberNormalizer.isMobileNumber(callerNumber)) {
            Log.i(TAG, "Skipped Auto-Reply: $callerNumber is not a valid mobile number (landline, shortcode, or toll-free)")
            repository.insertLog(
                ExecutionLog(
                    channel = channelLabel,
                    recipient = callerNumber,
                    messageSnippet = "Filtered out non-mobile number",
                    status = "SKIPPED",
                    details = "Skipped non-mobile number ($callerNumber). Auto-reply is restricted to mobile numbers only."
                )
            )
            return
        }

        val smsSender = SmsSender(context)
        val replyRules = repository.getEnabledAutoReplyRules(ChannelType.CALL)
        if (replyRules.isEmpty()) {
            Log.d(TAG, "No enabled call auto-reply rules found.")
            return
        }

        val contactName = getContactName(context, callerNumber)
        val isContact = contactName != null
        val displayName = contactName ?: "there"

        val currentCal = Calendar.getInstance()
        val currentMinutes = currentCal.get(Calendar.HOUR_OF_DAY) * 60 + currentCal.get(Calendar.MINUTE)

        for (rule in replyRules) {
            val matchesEvent = when (rule.callEventType.uppercase()) {
                "MISSED_CALL" -> isMissedCall
                "CALL_ENDED" -> isCallEnded
                "BOTH" -> isMissedCall || isCallEnded
                else -> isMissedCall || isCallEnded
            }
            if (!matchesEvent) continue

            val startMinutes = rule.startTimeHour * 60 + rule.startTimeMinute
            val endMinutes = rule.endTimeHour * 60 + rule.endTimeMinute
            val inTimeWindow = startMinutes == endMinutes || if (startMinutes < endMinutes) {
                currentMinutes in startMinutes..endMinutes
            } else {
                currentMinutes in startMinutes..1439 || currentMinutes in 0..endMinutes
            }
            if (!inTimeWindow) continue

            if (rule.replyToContactsOnly && !isContact) continue
            if (rule.replyToNonContactsOnly && isContact) continue

            if (!repository.canReplyToNumber(rule, callerNumber)) {
                Log.d(TAG, "Rate or daily limit (${rule.maxDailyReplies}/day) reached for rule '${rule.name}' on $callerNumber")
                repository.insertLog(
                    ExecutionLog(
                        channel = channelLabel,
                        recipient = callerNumber,
                        messageSnippet = rule.replyText.take(50),
                        status = "SKIPPED",
                        details = "Daily frequency limit (${rule.maxDailyReplies}x/day) or cooldown reached for $callerNumber"
                    )
                )
                continue
            }

            val formattedMessage = MessageTemplateResolver.resolve(
                template = rule.replyText,
                name = displayName,
                phone = callerNumber,
                event = if (isMissedCall) "missed call" else "call",
                targetTimestamp = System.currentTimeMillis()
            )

            val targetSimSlot = prefs.defaultSimSlot
            val result = smsSender.sendSmsWithResult(callerNumber, formattedMessage, simSlot = targetSimSlot, enforceQuota = true)
            val isQuota = result.reason.contains("limit", ignoreCase = true) || result.reason.contains("quota", ignoreCase = true)
            val isDispatchedTimeout = result.deliveryStatus == SmsSender.DeliveryStatus.PENDING_TIMEOUT
            val isPartial = result.deliveryStatus == SmsSender.DeliveryStatus.PARTIAL_DISPATCH
            val logStatus = if (result.success) "SUCCESS" else if (isDispatchedTimeout) "DISPATCHED" else if (isPartial) "PARTIAL" else if (isQuota) "QUEUED" else "FAILED"
            val logDetails = if (result.success) "Auto-replied via SMS to mobile number $callerNumber ($eventName) via SIM $targetSimSlot, parts: ${result.segments}"
                else if (isDispatchedTimeout) "Auto-reply dispatched to cellular modem for $callerNumber via SIM $targetSimSlot (${result.segments} parts); carrier acknowledgement pending"
                else if (isPartial) "Auto-reply partially delivered (${result.successfulParts}/${result.segments} parts). Anti-duplicate protection active."
                else result.reason

            if (result.success || isDispatchedTimeout || isPartial) {
                repository.recordReplySent(rule.id, callerNumber)
                if (result.success) {
                    SmsQuotaManager.commitSmsQuotaReservation(result.segments)
                }
                repository.insertLog(
                    ExecutionLog(
                        channel = channelLabel,
                        recipient = callerNumber,
                        messageSnippet = formattedMessage.take(160),
                        status = logStatus,
                        details = logDetails,
                        segments = result.segments
                    )
                )
                Log.i(TAG, "Sent Auto-Reply SMS to mobile number $callerNumber for $eventName [${result.segments} parts] ($logStatus)")
            } else {
                repository.insertLog(
                    ExecutionLog(
                        channel = channelLabel,
                        recipient = callerNumber,
                        messageSnippet = formattedMessage.take(160),
                        status = logStatus,
                        details = logDetails,
                        segments = result.segments
                    )
                )
                Log.w(TAG, "Auto-reply SMS blocked/failed for $callerNumber: ${result.reason}")
            }
            break
        }
    }

    private fun getContactName(context: Context, number: String): String? {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
