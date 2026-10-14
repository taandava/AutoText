package com.jmd.autotext.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ExecutionLog
import com.jmd.autotext.sender.SmsSender
import com.jmd.autotext.util.PhoneNumberNormalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PhoneCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneCallReceiver"
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var savedIncomingNumber: String? = null
        private var wasRinging = false
        private var wasAnswered = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val numberFromIntent = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

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
                val callNumber = resolveCallNumber(context, savedIncomingNumber)

                if (lastState == TelephonyManager.EXTRA_STATE_RINGING && wasRinging && !wasAnswered) {
                    // Event 1: MISSED CALL (phone was ringing, never answered, returned to idle)
                    Log.d(TAG, "Missed call detected from: $callNumber")
                    if (!callNumber.isNullOrEmpty()) {
                        handleCallAutoReply(context, callNumber, isMissedCall = true)
                    }
                } else if (lastState == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    // Event 2: CALL ENDED (phone was active/off-hook, now finished)
                    Log.d(TAG, "Call ended detected with: $callNumber")
                    if (!callNumber.isNullOrEmpty()) {
                        handleCallAutoReply(context, callNumber, isMissedCall = false)
                    }
                }

                // Reset state
                wasRinging = false
                wasAnswered = false
                savedIncomingNumber = null
            }
        }
        lastState = state
    }

    private fun resolveCallNumber(context: Context, cachedNumber: String?): String? {
        if (!cachedNumber.isNullOrEmpty()) return cachedNumber

        // If intent did not provide caller number, retrieve the most recent call record from system CallLog
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch number from CallLog: ${e.message}")
            null
        }
    }

    private fun handleCallAutoReply(context: Context, callerNumber: String, isMissedCall: Boolean) {
        val eventName = if (isMissedCall) "Missed Call" else "Call Ended"
        val channelLabel = "$eventName Auto-Reply"
        val repository = AppRepository(context)
        val prefs = com.jmd.autotext.data.PreferenceManager(context)

        if (!prefs.isAutoReplyMasterEnabled) {
            Log.d(TAG, "Skipping call auto-reply: Auto-Reply Responder is disabled by master switch")
            return
        }

        // 1. STRICT MOBILE NUMBER VALIDATION: Filter out landlines, shortcodes, toll-free numbers
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
        val dateDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        val timeDisplay = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        for (rule in replyRules) {
            // A. Call Event Filter: BOTH, MISSED_CALL, or CALL_ENDED
            val matchesEvent = when (rule.callEventType.uppercase()) {
                "MISSED_CALL" -> isMissedCall
                "CALL_ENDED" -> !isMissedCall
                else -> true // "BOTH" matches both missed call and ended call
            }
            if (!matchesEvent) continue

            // B. Time Window Filter
            val startMinutes = rule.startTimeHour * 60 + rule.startTimeMinute
            val endMinutes = rule.endTimeHour * 60 + rule.endTimeMinute
            val inTimeWindow = if (startMinutes <= endMinutes) {
                currentMinutes in startMinutes..endMinutes
            } else {
                currentMinutes >= startMinutes || currentMinutes <= endMinutes
            }
            if (!inTimeWindow) continue

            // C. Contact Filter
            if (rule.replyToContactsOnly && !isContact) continue
            if (rule.replyToNonContactsOnly && isContact) continue

            // D. Rate Limit & Daily Frequency Limit (Once, 3x, 5x, custom N times/day)
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

            // E. Format Custom SMS
            val formattedMessage = rule.replyText
                .replace("{name}", displayName, ignoreCase = true)
                .replace("{first_name}", displayName.split(" ").firstOrNull() ?: displayName, ignoreCase = true)
                .replace("{phone}", callerNumber, ignoreCase = true)
                .replace("{time}", timeDisplay, ignoreCase = true)
                .replace("{date}", dateDisplay, ignoreCase = true)
                .replace("{event}", if (isMissedCall) "missed call" else "call", ignoreCase = true)

            val result = smsSender.sendSmsWithResult(callerNumber, formattedMessage, enforceQuota = true)
            val isQuota = result.reason.contains("limit", ignoreCase = true) || result.reason.contains("quota", ignoreCase = true)
            val logStatus = if (result.success) "SUCCESS" else if (isQuota) "QUEUED" else "FAILED"
            val logDetails = if (result.success) "Auto-replied via SMS to mobile number $callerNumber ($eventName), parts: ${result.segments}" else result.reason

            if (result.success) {
                repository.recordReplySent(rule.id, callerNumber)
                repository.insertLog(
                    ExecutionLog(
                        channel = channelLabel,
                        recipient = callerNumber,
                        messageSnippet = formattedMessage.take(160),
                        status = "SUCCESS",
                        details = logDetails
                    )
                )
                Log.i(TAG, "Sent Auto-Reply SMS to mobile number $callerNumber for $eventName [${result.segments} parts]")
            } else {
                repository.insertLog(
                    ExecutionLog(
                        channel = channelLabel,
                        recipient = callerNumber,
                        messageSnippet = formattedMessage.take(160),
                        status = logStatus,
                        details = logDetails
                    )
                )
                Log.w(TAG, "Auto-reply SMS blocked/failed for $callerNumber: ${result.reason}")
            }
            break
        }

        // 2. Missed Call Auto-Forward (if it was a missed call)
        if (isMissedCall && prefs.isAutoForwardMasterEnabled) {
            val forwardRules = repository.getEnabledForwardRules("MISSED_CALL")
            for (fRule in forwardRules) {
                val forwardText = "Alert: Missed call received from $callerNumber at $timeDisplay"
                val fResult = smsSender.sendSmsWithResult(fRule.forwardToNumber, forwardText, enforceQuota = true)
                val isQuota = fResult.reason.contains("limit", ignoreCase = true) || fResult.reason.contains("quota", ignoreCase = true)
                val fStatus = if (fResult.success) "SUCCESS" else if (isQuota) "QUEUED" else "FAILED"
                val fDetails = if (fResult.success) "Forwarded missed call alert for $callerNumber, parts: ${fResult.segments}" else fResult.reason

                repository.insertLog(
                    ExecutionLog(
                        channel = "Missed Call Alert Forward",
                        recipient = fRule.forwardToNumber,
                        messageSnippet = forwardText.take(160),
                        status = fStatus,
                        details = fDetails
                    )
                )
            }
        }
    }

    private fun getContactName(context: Context, number: String): String? {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
