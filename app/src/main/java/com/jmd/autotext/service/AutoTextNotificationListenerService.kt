

/**
 * Package: com.jmd.autotext.service
 * Module: Notification Listener Service Layer
 *
 * Intercepts incoming notifications across messaging apps (WhatsApp, WhatsApp Business, SMS, Telegram, Messenger)
 * and dispatches automated replies via inline [RemoteInput] actions without launching UI on screen.
 *
 * Linked With:
 * - Repositories: [com.jmd.autotext.data.repo.AppRepository], [com.jmd.autotext.data.repo.AutoReplyRepository]
 * - Templates: [com.jmd.autotext.core.MessageTemplateResolver]
 * - Preferences: [com.jmd.autotext.data.prefs.PreferenceManager]
 * - Diagnostics: [com.jmd.autotext.core.AppFileLogger]
 */
package com.jmd.autotext.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jmd.autotext.core.MessageTemplateResolver
import com.jmd.autotext.core.PhoneNumberNormalizer
import com.jmd.autotext.data.model.AutoReplyRule
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.ExecutionLog
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.data.repo.AppRepository
import java.util.Calendar
import kotlinx.coroutines.*

/**
 * System NotificationListenerService intercepting incoming messages to perform automated replies via RemoteInput.
 */
class AutoTextNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "AutoReplyNL"
        var isListenerConnected = false
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
        Log.d(TAG, "Notification Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isListenerConnected = false
        Log.d(TAG, "Notification Listener disconnected")
    }

    private val serviceScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    )

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * Intercepts posted status bar notifications, inspects text or call events, evaluates matching rules, and triggers replies.
     *
     * @param sbn Notification container posted by the Android notification subsystem.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        val channel = when (pkg) {
            "com.whatsapp" -> ChannelType.WHATSAPP
            "com.whatsapp.w4b" -> ChannelType.WHATSAPP_BUSINESS
            else -> return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val lowerText = text.lowercase()
        val lowerTitle = title.lowercase()
        val isMissedCall = lowerText.contains("missed call") || lowerText.contains("missed voice call") || lowerText.contains("missed video call") || lowerTitle.contains("missed call")
        val isCallEnded = (lowerText.contains("call ended") || lowerText.contains("video call ended") || lowerText.contains("voice call ended")) &&
            !lowerText.contains("missed") &&
            !lowerText.contains("declined") &&
            !lowerText.contains("no answer") &&
            !lowerText.contains("unavailable") &&
            !lowerText.contains("cancelled") &&
            !lowerText.contains("canceled")
        val isCallEvent = isMissedCall || isCallEnded

        serviceScope.launch {
            try {
                val prefs = PreferenceManager(applicationContext)
                if (!prefs.isAutoReplyMasterEnabled) return@launch
                if (!prefs.isTasksMasterEnabled) return@launch
                if (com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive(applicationContext)) return@launch

                val repository = AppRepository(applicationContext)
                val enabledRules = repository.getEnabledAutoReplyRules(channel)
                if (enabledRules.isEmpty()) return@launch

                for (rule in enabledRules) {
                    // Check Call Event Type filter if rule specifies callEventType
                    if (isCallEvent) {
                        val matchesCallEvent = when (rule.callEventType.uppercase()) {
                            "MISSED_CALL" -> isMissedCall
                            "CALL_ENDED" -> isCallEnded
                            "BOTH" -> true
                            else -> false // Rules configured for "MESSAGE" do not trigger on voice/video calls
                        }
                        if (!matchesCallEvent) continue
                    } else {
                        // Incoming text message notification: skip rules configured strictly for calls
                        if (rule.callEventType.uppercase() in listOf("MISSED_CALL", "CALL_ENDED")) {
                            continue
                        }
                    }

                    // Apply contact / non-contact filters
                    val isKnownContact = try {
                        PhoneNumberNormalizer.isContact(applicationContext, title)
                    } catch (_: Exception) { false }
                    if (rule.replyToContactsOnly && !isKnownContact) continue
                    if (rule.replyToNonContactsOnly && isKnownContact) continue

                    if (matchesRule(rule, text, isCallEvent)) {
                        if (!repository.canReplyToNumber(rule, title)) {
                            Log.d(TAG, "Skipping auto-reply to $title due to cooldown (${rule.cooldownSeconds}s) or daily limit (${rule.maxDailyReplies}/day)")
                            continue
                        }

                        val formattedReply = MessageTemplateResolver.resolve(
                            template = rule.replyText,
                            name = title,
                            phone = title,
                            event = if (isMissedCall) "missed call" else if (isCallEnded) "call" else "message",
                            targetTimestamp = System.currentTimeMillis()
                        )

                        val replied = sendInlineReply(notification, formattedReply)
                        if (replied) {
                            val eventDesc = if (isMissedCall) "WhatsApp Missed Call" else if (isCallEnded) "WhatsApp Call Ended" else "WhatsApp message"
                            repository.recordReplySent(rule.id, title)
                            repository.insertLog(
                                ExecutionLog(
                                    channel = channel.displayName,
                                    recipient = title,
                                    messageSnippet = formattedReply.take(60),
                                    status = "SUCCESS",
                                    details = "Auto-replied to $eventDesc: \"$text\""
                                )
                            )
                            Log.d(TAG, "Auto-replied to $title on $pkg ($eventDesc)")
                            break
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error processing incoming notification auto-reply in background", t)
            }
        }
    }

    /**
     * Checks if notification message content and current time window satisfy rule filter criteria.
     *
     * @param rule Auto reply rule configuration.
     * @param messageText Incoming text string.
     * @param isCallEvent True if triggered by a missed or ended phone call.
     * @return True if rule conditions are satisfied.
     */
    private fun matchesRule(rule: AutoReplyRule, messageText: String, isCallEvent: Boolean = false): Boolean {
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

        if (isCallEvent) {
            // For call events (missed call or call ended), the trigger is the event itself
            return true
        }

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

    /**
     * Injects text directly into the notification's [RemoteInput] reply action without waking screen.
     *
     * @param notification Target notification object containing reply action.
     * @param replyText Generated message text to dispatch.
     * @return True if remote input reply intent was sent.
     */
    private fun sendInlineReply(notification: Notification, replyText: String): Boolean {
        val actions = notification.actions ?: return false

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.allowFreeFormInput) {
                    val intent = Intent()
                    val bundle = Bundle().apply {
                        putCharSequence(remoteInput.resultKey, replyText)
                    }
                    RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

                    return try {
                        action.actionIntent.send(this, 0, intent)
                        true
                    } catch (e: PendingIntent.CanceledException) {
                        Log.e(TAG, "Failed to send inline reply intent: ${e.message}")
                        false
                    }
                }
            }
        }
        return false
    }
}
