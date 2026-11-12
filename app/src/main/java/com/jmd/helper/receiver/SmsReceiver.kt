/**
 * Package: com.jmd.helper.receiver
 * Module: Broadcast Receivers Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [SmsReceiver].
 *
 * What it does:
 * Catches incoming cellular SMS messages via [Telephony.Sms.Intents.SMS_RECEIVED_ACTION], inspects message body patterns against configured AutoReply rules, and dispatches responses.
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.helper.data.model.AutoReplyRule], [com.jmd.helper.data.model.ChannelType], [com.jmd.helper.data.model.ExecutionLog]
 * - Services & Engines: [com.jmd.helper.engine.SmsSender]
 * - Core Security & Utilities: [android.util.Log], [com.jmd.helper.core.AppFileLogger], [com.jmd.helper.core.MessageTemplateResolver], [com.jmd.helper.core.PhoneNumberNormalizer]
 * - Android Framework & Jetpack: [android.content.BroadcastReceiver], [android.provider.Telephony], [kotlinx.coroutines.CoroutineScope], [kotlinx.coroutines.Dispatchers]
 */

package com.jmd.helper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.core.MessageTemplateResolver
import com.jmd.helper.core.PhoneNumberNormalizer
import com.jmd.helper.core.SmsQuotaManager
import com.jmd.helper.data.model.AutoReplyRule
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.ExecutionLog
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.engine.SmsSender
import java.util.Calendar
import com.jmd.helper.HelperApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What it is:
 * Incoming SMS broadcast receiver executing intelligent auto-reply rules.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [SmsReceiver].
 *
 * Linked with:
 * - Architecture: [SmsReceiver], [SmsReceiver]
 */
class SmsReceiver : BroadcastReceiver() {
    /**
     * What it is:
     * Companion object constant and factory provider for [SmsReceiver].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [SmsReceiver]
     */

    companion object {
        private const val TAG = "SmsReceiver"
    }
    /**
     * What it is:
     * Execution routine for [onReceive].
     *
     * What it does:
     * Coordinates onReceive operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SmsReceiver]
     */

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

            val messages = try {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to parse SMS messages from broadcast intent", t)
                return
            }
            if (messages.isNullOrEmpty()) return

            val senderPhone = messages[0]?.displayOriginatingAddress ?: return
            val messageBody = messages.joinToString("") { it?.displayMessageBody ?: "" }

            val maskedPhone = if (senderPhone.length > 4) "${senderPhone.take(3)}****${senderPhone.takeLast(2)}" else "****"
            Log.d(TAG, "Incoming SMS from $maskedPhone (length=${messageBody.length})")
            AppFileLogger.action(context, TAG, "Incoming SMS from $maskedPhone (${messageBody.length} chars)")

            val pendingResult = goAsync()
            val appScope = (context.applicationContext as? HelperApplication)?.applicationScope
                ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
            appScope.launch(Dispatchers.IO) {
                try {
                    processSmsAsync(context, senderPhone, messageBody)
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected error in SmsReceiver onReceive", t)
        }
    }
    /**
     * What it is:
     * Execution routine for [processSmsAsync].
     *
     * What it does:
     * Coordinates processSmsAsync operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SmsReceiver]
     */

    private fun processSmsAsync(context: Context, senderPhone: String, messageBody: String) {
        val repository = AppRepository(context)
        val smsSender = SmsSender(context)
        val prefs = PreferenceManager(context)

        if (prefs.isAutoReplyMasterEnabled && PhoneNumberNormalizer.isMobileNumber(senderPhone)) {
            val isContact = PhoneNumberNormalizer.isContact(context, senderPhone)
            val contactName = PhoneNumberNormalizer.getContactName(context, senderPhone) ?: "there"

            val replyRules = repository.getEnabledAutoReplyRules(ChannelType.SMS)
            for (rule in replyRules) {
                if (rule.replyToContactsOnly && !isContact) continue
                if (rule.replyToNonContactsOnly && isContact) continue

                if (matchesRule(rule, messageBody)) {
                    if (repository.canReplyToNumber(rule, senderPhone)) {
                        val formattedReply = MessageTemplateResolver.resolve(
                            template = rule.replyText,
                            name = contactName,
                            phone = senderPhone,
                            targetTimestamp = System.currentTimeMillis()
                        )

                        val targetSimSlot = prefs.defaultSimSlot
                        val result = smsSender.sendSmsWithResult(senderPhone, formattedReply, simSlot = targetSimSlot, enforceQuota = true)
                        val isQuota = result.reason.contains("limit", ignoreCase = true) || result.reason.contains("quota", ignoreCase = true)
                        val isDispatchedTimeout = result.deliveryStatus == SmsSender.DeliveryStatus.PENDING_TIMEOUT
                        val isPartial = result.deliveryStatus == SmsSender.DeliveryStatus.PARTIAL_DISPATCH
                        val logStatus = if (result.success) "SUCCESS" else if (isDispatchedTimeout) "DISPATCHED" else if (isPartial) "PARTIAL" else if (isQuota) "QUEUED" else "FAILED"
                        val logDetails = if (result.success) "Auto-replied to incoming SMS via SIM $targetSimSlot, parts: ${result.segments}"
                            else if (isDispatchedTimeout) "Auto-reply dispatched to cellular modem via SIM $targetSimSlot (${result.segments} parts); carrier acknowledgement pending"
                            else if (isPartial) "Auto-reply partially delivered (${result.successfulParts}/${result.segments} parts). Anti-duplicate protection active."
                            else result.reason

                        if (result.success || isDispatchedTimeout || isPartial) {
                            repository.recordReplySent(rule.id, senderPhone)
                        }
                        if (result.success) {
                            SmsQuotaManager.commitSmsQuotaReservation(result.segments)
                        }
                        repository.insertLog(
                            ExecutionLog(
                                channel = "SMS Auto-Reply",
                                recipient = senderPhone,
                                messageSnippet = formattedReply.take(160),
                                status = logStatus,
                                details = logDetails,
                                segments = result.segments
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
    }
    /**
     * What it is:
     * Execution routine for [matchesRule].
     *
     * What it does:
     * Coordinates matchesRule operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SmsReceiver]
     */

    private fun matchesRule(rule: AutoReplyRule, messageText: String): Boolean {
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = rule.startTimeHour * 60 + rule.startTimeMinute
        val endMinutes = rule.endTimeHour * 60 + rule.endTimeMinute

        val inTimeWindow = startMinutes == endMinutes || if (startMinutes < endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes in startMinutes..1439 || currentMinutes in 0..endMinutes
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
