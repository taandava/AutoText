package com.jmd.autotext.service.accessibility

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.util.AppFileLogger
import com.jmd.autotext.util.PhoneNumberNormalizer
import com.jmd.autotext.util.WhatsAppPackageHelper
import java.net.URLEncoder

/**
 * Handles intent dispatching for WhatsApp, WhatsApp Business, Groups, and Status updates.
 */
object WhatsAppIntentDispatcher {
    private const val TAG = "WhatsAppIntentDisp"

    fun launchWhatsApp(
        context: Context,
        taskId: Long,
        channel: ChannelType,
        recipient: String,
        message: String,
        shouldRelock: Boolean,
        isBatch: Boolean,
        mediaUri: String = ""
    ) {
        val targetPkg = when {
            channel == ChannelType.WHATSAPP_BUSINESS -> "com.whatsapp.w4b"
            channel == ChannelType.WHATSAPP_STATUS && (recipient.contains("w4b", ignoreCase = true) || recipient.contains("business", ignoreCase = true)) -> "com.whatsapp.w4b"
            else -> WhatsAppPackageHelper.getInstalledWhatsAppPackage(context) ?: "com.whatsapp"
        }

        when (channel) {
            ChannelType.WHATSAPP_STATUS -> {
                AutoTextAccessibilityService.prepareForStatus(
                    taskId = taskId,
                    packageName = targetPkg,
                    messageText = message,
                    shouldRelock = shouldRelock,
                    mediaUri = mediaUri
                )
                if (mediaUri.isNotBlank()) {
                    try {
                        val uri = Uri.parse(mediaUri)
                        val detectedMime = context.contentResolver.getType(uri).orEmpty().lowercase()
                        val isVideo = detectedMime.startsWith("video/") || mediaUri.substringBefore('?').endsWith(".mp4", ignoreCase = true) || mediaUri.substringBefore('?').endsWith(".3gp", ignoreCase = true)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = detectedMime.takeIf { it.startsWith("image/") || it.startsWith("video/") } ?: if (isVideo) "video/*" else "image/*"
                            setPackage(targetPkg)
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = android.content.ClipData.newRawUri("media", uri)
                            if (message.isNotBlank()) putExtra(Intent.EXTRA_TEXT, message)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        try { context.grantUriPermission(targetPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                        context.startActivity(shareIntent)
                        Log.d(TAG, "launchWhatsApp: Launched ACTION_SEND for WhatsApp Status with media: $mediaUri")
                        AppFileLogger.action(context.applicationContext, TAG, "Launched ACTION_SEND for WhatsApp Status with media: $mediaUri")
                    } catch (e: Exception) {
                        Log.e(TAG, "launchWhatsApp: Failed launching ACTION_SEND: ${e.message}")
                    }
                } else {
                    // WhatsApp's ACTION_SEND text flow opens a recipient/share picker, not the text-status
                    // composer. Let the accessibility state machine navigate Home -> Updates -> text FAB.
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        context.startActivity(launchIntent)
                        AppFileLogger.action(context.applicationContext, TAG, "Launched WhatsApp home for text-status composer navigation")
                    } else {
                        Log.e(TAG, "WhatsApp launch intent unavailable for text status")
                    }
                }
            }
            ChannelType.WHATSAPP_GROUP -> {
                AutoTextAccessibilityService.prepareForGroup(
                    taskId = taskId,
                    packageName = targetPkg,
                    groupName = recipient,
                    messageText = message,
                    shouldRelock = shouldRelock,
                    isBatchCampaign = isBatch
                )
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    context.startActivity(launchIntent)
                    Log.d(TAG, "launchWhatsApp: Launched WhatsApp for Group search: $recipient")
                }
            }
            else -> {
                val isGroup = recipient.startsWith("group:", ignoreCase = true)
                if (isGroup) {
                    val groupName = recipient.substringAfter(':').trim()
                    AutoTextAccessibilityService.prepareForGroup(
                        taskId = taskId,
                        packageName = targetPkg,
                        groupName = groupName,
                        messageText = message,
                        shouldRelock = shouldRelock,
                        isBatchCampaign = isBatch
                    )
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        context.startActivity(launchIntent)
                        Log.d(TAG, "launchWhatsApp: Launched WhatsApp for Group search: $recipient")
                    }
                } else {
                    val cleanPhone = PhoneNumberNormalizer.formatForWhatsApp(recipient, PreferenceManager(context).defaultCountryCode)
                    AutoTextAccessibilityService.prepareForSend(
                        taskId = taskId,
                        packageName = targetPkg,
                        recipient = cleanPhone,
                        messageText = message,
                        shouldRelock = shouldRelock,
                        isBatchCampaign = isBatch
                    )

                    if (mediaUri.isNotBlank()) {
                        try {
                            val uri = Uri.parse(mediaUri)
                            val detectedMime = context.contentResolver.getType(uri).orEmpty().lowercase()
                            val isVideo = detectedMime.startsWith("video/") || mediaUri.substringBefore('?').endsWith(".mp4", ignoreCase = true) || mediaUri.substringBefore('?').endsWith(".3gp", ignoreCase = true)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = detectedMime.takeIf { it.startsWith("image/") || it.startsWith("video/") } ?: if (isVideo) "video/*" else "image/*"
                                setPackage(targetPkg)
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = android.content.ClipData.newRawUri("media", uri)
                                if (message.isNotBlank()) putExtra(Intent.EXTRA_TEXT, message)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            try {
                                context.grantUriPermission(targetPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (_: Exception) {}
                            context.startActivity(shareIntent)
                            Log.d(TAG, "launchWhatsApp: Launched ACTION_SEND for Direct Chat with media: $mediaUri")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed launching media ACTION_SEND for direct chat: ${e.message}")
                        }
                    } else {
                        try {
                            val encoded = URLEncoder.encode(message, "UTF-8")
                            val uri = Uri.parse("whatsapp://send?phone=$cleanPhone&text=$encoded")
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                setPackage(targetPkg)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            context.startActivity(intent)
                            Log.d(TAG, "launchWhatsApp: Dispatched WhatsApp URI for $cleanPhone")
                        } catch (e: Exception) {
                            Log.e(TAG, "Primary direct WhatsApp intent failed: ${e.message}")
                            try {
                                val encoded = URLEncoder.encode(message, "UTF-8")
                                val fallbackUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encoded")
                                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                                    setPackage(targetPkg)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                context.startActivity(fallbackIntent)
                            } catch (e2: Exception) {
                                Log.e(TAG, "All WhatsApp intent launches failed: ${e2.message}")
                            }
                        }
                    }
                }
            }
        }
    }
}
