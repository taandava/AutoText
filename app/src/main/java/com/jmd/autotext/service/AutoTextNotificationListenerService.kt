package com.jmd.autotext.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.AutoReplyRule
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ExecutionLog
import java.util.Calendar

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
        val isCallEnded = lowerText.contains("call ended") || lowerText.contains("video call ended") || lowerText.contains("voice call ended")
        val isCallEvent = isMissedCall || isCallEnded

        val prefs = com.jmd.autotext.data.PreferenceManager(applicationContext)
        if (!prefs.isAutoReplyMasterEnabled) return

        val repository = AppRepository(applicationContext)
        val enabledRules = repository.getEnabledAutoReplyRules(channel)
        if (enabledRules.isEmpty()) return

        for (rule in enabledRules) {
            // Check Call Event Type filter if rule specifies callEventType
            if (isCallEvent) {
                val matchesCallEvent = when (rule.callEventType.uppercase()) {
                    "MISSED_CALL" -> isMissedCall
                    "CALL_ENDED" -> isCallEnded
                    else -> true // "BOTH"
                }
                if (!matchesCallEvent) continue
            }

            if (matchesRule(rule, title, text, isCallEvent)) {
                if (!repository.canReplyToNumber(rule, title)) {
                    Log.d(TAG, "Skipping auto-reply to $title due to cooldown (${rule.cooldownMinutes}m) or daily limit (${rule.maxDailyReplies}/day)")
                    continue
                }

                val timeDisplay = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                val formattedReply = rule.replyText
                    .replace("{name}", title, ignoreCase = true)
                    .replace("{first_name}", title.split(" ").firstOrNull() ?: title, ignoreCase = true)
                    .replace("{time}", timeDisplay, ignoreCase = true)
                    .replace("{event}", if (isMissedCall) "missed call" else if (isCallEnded) "call" else "message", ignoreCase = true)

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
    }

    private fun matchesRule(rule: AutoReplyRule, sender: String, messageText: String, isCallEvent: Boolean = false): Boolean {
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
