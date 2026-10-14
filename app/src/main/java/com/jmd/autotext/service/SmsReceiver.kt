package com.jmd.autotext.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ExecutionLog
import com.jmd.autotext.sender.SmsSender
import com.jmd.autotext.util.AppFileLogger
import java.util.Calendar

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val senderPhone = messages[0].displayOriginatingAddress ?: return
        val messageBody = messages.joinToString("") { it.displayMessageBody ?: "" }

        Log.d(TAG, "Incoming SMS from $senderPhone: $messageBody")
        AppFileLogger.action(context, TAG, "Incoming SMS from $senderPhone (${messageBody.length} chars)")

        val repository = AppRepository(context)
        val smsSender = SmsSender(context)
        val prefs = com.jmd.autotext.data.PreferenceManager(context)

        // 1. Check Auto-Reply Rules for SMS (Mobile Numbers Only)
        if (prefs.isAutoReplyMasterEnabled && com.jmd.autotext.util.PhoneNumberNormalizer.isMobileNumber(senderPhone)) {
            val isContact = com.jmd.autotext.util.PhoneNumberNormalizer.isContact(context, senderPhone)
            val contactName = com.jmd.autotext.util.PhoneNumberNormalizer.getContactName(context, senderPhone) ?: "there"
            val timeDisplay = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            val dateDisplay = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())

            val replyRules = repository.getEnabledAutoReplyRules(ChannelType.SMS)
            for (rule in replyRules) {
                // Contact / Non-Contact Filter
                if (rule.replyToContactsOnly && !isContact) continue
                if (rule.replyToNonContactsOnly && isContact) continue

                if (matchesRule(rule, messageBody)) {
                    if (repository.canReplyToNumber(rule, senderPhone)) {
                        val formattedReply = rule.replyText
                            .replace("{name}", contactName, ignoreCase = true)
                            .replace("{time}", timeDisplay, ignoreCase = true)
                            .replace("{date}", dateDisplay, ignoreCase = true)

                        val result = smsSender.sendSmsWithResult(senderPhone, formattedReply, enforceQuota = true)
                        val isQuota = result.reason.contains("limit", ignoreCase = true) || result.reason.contains("quota", ignoreCase = true)
                        val logStatus = if (result.success) "SUCCESS" else if (isQuota) "QUEUED" else "FAILED"
                        val logDetails = if (result.success) "Auto-replied to incoming SMS, parts: ${result.segments}" else result.reason

                        if (result.success) {
                            repository.recordReplySent(rule.id, senderPhone)
                        }
                        repository.insertLog(
                            ExecutionLog(
                                channel = "SMS Auto-Reply",
                                recipient = senderPhone,
                                messageSnippet = formattedReply.take(160),
                                status = logStatus,
                                details = logDetails
                            )
                        )
                        AppFileLogger.action(context, TAG, "Auto-replying to SMS from $senderPhone: $logStatus ($logDetails)")
                        AppFileLogger.campaignLedger(
                            context = context,
                            campaignId = rule.id,
                            channel = "SMS Auto-Reply",
                            recipientName = contactName,
                            recipientPhone = senderPhone,
                            status = logStatus,
                            messageSnippet = formattedReply.take(80),
                            attempt = 1,
                            details = logDetails
                        )
                    } else {
                        Log.d(TAG, "Daily frequency limit (${rule.maxDailyReplies}/day) or cooldown reached for $senderPhone")
                        AppFileLogger.i(context, TAG, "Daily limit or cooldown reached for auto-replying to $senderPhone")
                    }
                    break
                }
            }
        } else if (!prefs.isAutoReplyMasterEnabled) {
            Log.d(TAG, "Skipping SMS auto-reply: Auto-Reply Responder is disabled by master switch")
        } else {
            Log.d(TAG, "Skipping SMS auto-reply: $senderPhone is not a mobile number (shortcode/alphanumeric header)")
        }

        // 2. Check Auto-Forward Rules for SMS
        if (prefs.isAutoForwardMasterEnabled) {
            val forwardRules = repository.getEnabledForwardRules("SMS")
            for (fRule in forwardRules) {
                val matchesFilter = (fRule.filterSender.isEmpty() || senderPhone.contains(fRule.filterSender)) &&
                        (fRule.filterKeyword.isEmpty() || messageBody.contains(fRule.filterKeyword, ignoreCase = true))

                if (matchesFilter) {
                    val forwardText = "Fwd from $senderPhone:\n$messageBody"
                    val fResult = smsSender.sendSmsWithResult(fRule.forwardToNumber, forwardText, enforceQuota = true)
                    val isQuota = fResult.reason.contains("limit", ignoreCase = true) || fResult.reason.contains("quota", ignoreCase = true)
                    val fStatus = if (fResult.success) "SUCCESS" else if (isQuota) "QUEUED" else "FAILED"
                    val fDetails = if (fResult.success) "Forwarded SMS originally from $senderPhone, parts: ${fResult.segments}" else fResult.reason

                    repository.insertLog(
                        ExecutionLog(
                            channel = "SMS Auto-Forward",
                            recipient = fRule.forwardToNumber,
                            messageSnippet = forwardText.take(160),
                            status = fStatus,
                            details = fDetails
                        )
                    )
                    AppFileLogger.action(context, TAG, "Auto-forwarding SMS from $senderPhone to ${fRule.forwardToNumber}: $fStatus ($fDetails)")
                    AppFileLogger.campaignLedger(
                        context = context,
                        campaignId = fRule.id,
                        channel = "SMS Auto-Forward",
                        recipientName = "Forward Target",
                        recipientPhone = fRule.forwardToNumber,
                        status = fStatus,
                        messageSnippet = forwardText.take(80),
                        attempt = 1,
                        details = fDetails
                    )
                }
            }
        }
    }

    private fun matchesRule(rule: com.jmd.autotext.model.AutoReplyRule, messageText: String): Boolean {
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = rule.startTimeHour * 60 + rule.startTimeMinute
        val endMinutes = rule.endTimeHour * 60 + rule.endTimeMinute

        val inTimeWindow = if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
        if (!inTimeWindow) return false

        val pattern = rule.triggerPattern.trim()
        if (pattern == "*" || pattern.isEmpty()) return true

        val keywords = pattern.split(",").map { it.trim().lowercase() }
        val msgLower = messageText.lowercase()

        return if (rule.isExactMatch) {
            keywords.any { it == msgLower }
        } else {
            keywords.any { msgLower.contains(it) }
        }
    }
}
