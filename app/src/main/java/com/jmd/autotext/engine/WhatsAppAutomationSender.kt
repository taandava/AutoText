/**
 * Package: com.jmd.autotext.engine
 * Module: Automation Engine
 *
 * Coordinates intent generation and accessibility service dispatch for WhatsApp
 * (Personal & Business) direct chats, group messages, and status stories.
 *
 * Linked With:
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Dispatcher: [com.jmd.autotext.whatsapp.WhatsAppIntentDispatcher]
 * - Status Engine: [com.jmd.autotext.whatsapp.WhatsAppStatusEngine]
 * - Direct Engine: [com.jmd.autotext.whatsapp.WhatsAppDirectSendEngine]
 * - Package Helper: [com.jmd.autotext.core.WhatsAppPackageHelper]
 * - Engine: [com.jmd.autotext.engine.SequentialTaskEngine]
 */
package com.jmd.autotext.engine

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.util.Log
import java.net.URLEncoder
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.core.PhoneNumberNormalizer
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.prefs.PreferenceManager

/**
 * Dispatch engine for WhatsApp messaging, media sharing, and status publishing.
 *
 * Interfaces with [AutoTextAccessibilityService] to execute automated taps, node inputs,
 * and send button clicks across consumer WhatsApp and WhatsApp Business editions.
 */
class WhatsAppAutomationSender(private val context: Context) {

    private val tag = "WhatsAppAutomationSender"

    /**
     * Dispatches an automated WhatsApp task across appropriate sub-flow (Status, Group, or Direct Chat).
     *
     * @param taskId Unique identifier of the scheduled task.
     * @param channel The target [ChannelType].
     * @param recipientPhone Destination phone number, JID, or group name.
     * @param messageText Message body or media caption.
     * @param shouldRelock True if device should be secured/locked following delivery.
     * @param mediaUri Local media content or file URI string (optional).
     * @param isBatchCampaign True if executed as part of high-volume daily campaign.
     * @return True if intent launched successfully, false otherwise.
     */
    fun send(
        taskId: Long,
        channel: ChannelType,
        recipientPhone: String,
        messageText: String,
        shouldRelock: Boolean = false,
        mediaUri: String = "",
        isBatchCampaign: Boolean = false
    ): Boolean {
        val isBusiness = (channel == ChannelType.WHATSAPP_BUSINESS || channel == ChannelType.WHATSAPP_BUSINESS_STATUS)
        val targetPkg = if (isBusiness) "com.whatsapp.w4b" else "com.whatsapp"

        val isStatus = (channel == ChannelType.WHATSAPP_STATUS || channel == ChannelType.WHATSAPP_BUSINESS_STATUS)
        val isGroup = recipientPhone.contains("-") || recipientPhone.length > 15 || !recipientPhone.all { it.isDigit() || it == '+' }

        return when {
            isStatus -> sendWhatsAppStatusMessage(targetPkg, taskId, mediaUri, shouldRelock, messageText)
            isGroup -> sendWhatsAppGroupMessage(targetPkg, taskId, recipientPhone, messageText, shouldRelock, isBatchCampaign)
            else -> sendWhatsAppDirectChatMessage(targetPkg, taskId, mediaUri, shouldRelock, isBatchCampaign, recipientPhone, messageText)
        }
    }

    /**
     * Configures accessibility service and launches intent to publish a text or media Status story.
     */
    private fun sendWhatsAppStatusMessage(
        targetPkg: String,
        taskId: Long,
        mediaUri: String,
        shouldRelock: Boolean,
        messageText: String
    ): Boolean {
        AutoTextAccessibilityService.prepareForStatus(
            taskId = taskId,
            packageName = targetPkg,
            messageText = messageText,
            shouldRelock = shouldRelock,
            mediaUri = mediaUri
        )
        if (mediaUri.isNotBlank()) {
            return launchActionSend(targetPkg, mediaUri, messageText)
        } else {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                context.startActivity(launchIntent)
                Log.d(tag, "Launched WhatsApp home for text-status composer navigation")
                AppFileLogger.action(context, tag, "Launched WhatsApp home for text-status composer navigation")
                return true
            } else {
                Log.e(tag, "WhatsApp launch intent unavailable for text status")
                return false
            }
        }
    }

    /**
     * Configures accessibility service and launches WhatsApp for searching and messaging a group.
     */
    private fun sendWhatsAppGroupMessage(
        targetPkg: String,
        taskId: Long,
        effectiveRecipient: String,
        messageText: String,
        shouldRelock: Boolean,
        isBatchCampaign: Boolean
    ): Boolean {
        AutoTextAccessibilityService.prepareForGroup(
            taskId = taskId,
            packageName = targetPkg,
            groupName = effectiveRecipient,
            messageText = messageText,
            shouldRelock = shouldRelock,
            isBatchCampaign = isBatchCampaign
        )
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
        if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            context.startActivity(launchIntent)
            Log.d(tag, "Directly launched WhatsApp for group search: $effectiveRecipient")
            AppFileLogger.action(context, tag, "Directly launched WhatsApp for group search: $effectiveRecipient")
            return true
        }
        return false
    }

    /**
     * Configures accessibility service and launches deep-link intent (`whatsapp://send?phone=...`) for a direct conversation.
     */
    private fun sendWhatsAppDirectChatMessage(
        targetPkg: String,
        taskId: Long,
        mediaUri: String,
        shouldRelock: Boolean,
        isBatchCampaign: Boolean,
        effectiveRecipient: String,
        messageText: String
    ): Boolean {
        val cleanPhone = PhoneNumberNormalizer.formatForWhatsApp(
            effectiveRecipient,
            PreferenceManager(context).defaultCountryCode
        ).ifBlank {
            effectiveRecipient.replace("+", "").trim()
        }
        AutoTextAccessibilityService.prepareForSend(
            taskId = taskId,
            packageName = targetPkg,
            recipient = cleanPhone,
            messageText = messageText,
            shouldRelock = shouldRelock,
            isBatchCampaign = isBatchCampaign
        )

        if (mediaUri.isNotBlank()) {
            return launchActionSend(targetPkg, mediaUri, messageText)
        } else {
            val encodedMessage = URLEncoder.encode(messageText, "UTF-8")
            try {
                val directUri = "whatsapp://send?phone=$cleanPhone&text=$encodedMessage".toUri()
                val intent = Intent(Intent.ACTION_VIEW, directUri).apply {
                    setPackage(targetPkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(intent)
                Log.d(tag, "Directly launched $targetPkg via whatsapp:// for $cleanPhone")
                AppFileLogger.action(context, tag, "Directly launched $targetPkg via whatsapp:// for $cleanPhone")
                return true
            } catch (e: Exception) {
                Log.w(tag, "whatsapp:// failed, trying api.whatsapp.com fallback", e)
                try {
                    val fallbackUri = "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage".toUri()
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                        setPackage(targetPkg)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(fallbackIntent)
                    return true
                } catch (ex: Exception) {
                    Log.e(tag, "Fallback also failed: ${ex.message}")
                    return false
                }
            }
        }
    }

    /**
     * Dispatches native [Intent.ACTION_SEND] with URI permissions for media attachment sharing.
     */
    private fun launchActionSend(targetPkg: String, mediaUri: String, messageText: String): Boolean {
        try {
            val uri = mediaUri.toUri()
            val detectedMime = context.contentResolver.getType(uri).orEmpty().lowercase()
            val isVideo = detectedMime.startsWith("video/") || mediaUri.substringBefore('?').endsWith(".mp4", ignoreCase = true) || mediaUri.substringBefore('?').endsWith(".3gp", ignoreCase = true)
            val mimeType = detectedMime.takeIf { it.startsWith("image/") || it.startsWith("video/") } ?: if (isVideo) "video/*" else "image/*"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                setPackage(targetPkg)
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("media", uri)
                if (messageText.isNotBlank()) putExtra(Intent.EXTRA_TEXT, messageText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            try {
                context.grantUriPermission(targetPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.d(tag, "grantUriPermission skipped: ${e.message}")
            }
            context.startActivity(shareIntent)
            Log.d(tag, "Directly launched ACTION_SEND with media: $mediaUri")
            AppFileLogger.action(context, tag, "Directly launched ACTION_SEND with media: $mediaUri")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Direct media launch failed: ${e.message}")
            return false
        }
    }
}
