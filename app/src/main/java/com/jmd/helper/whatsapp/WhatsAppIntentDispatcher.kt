

/**
 * Package: com.jmd.helper.whatsapp
 * Module: WhatsApp Task Subsystem
 *
 * Formulates and fires explicit deep link intents (`api.whatsapp.com/send?phone=...` and package-targeted actions)
 * launching WhatsApp or WhatsApp Business chats, groups, and status updates while attaching security metadata.
 *
 * Linked With:
 * - Service: [com.jmd.helper.service.TaskAccessibilityService]
 * - Sender: [com.jmd.helper.engine.WhatsAppSender]
 * - Package Helper: [com.jmd.helper.core.WhatsAppPackageHelper]
 * - Diagnostics: [com.jmd.helper.core.AppFileLogger]
 */
package com.jmd.helper.whatsapp

import android.content.*
import androidx.core.net.toUri
import android.util.Log
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.core.PhoneNumberNormalizer
import com.jmd.helper.core.WhatsAppPackageHelper
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.service.TaskAccessibilityService
import java.net.URLEncoder

/**
 * High-reliability intent launcher for personal and business WhatsApp endpoints.
 */
object WhatsAppIntentDispatcher {
    private const val TAG = "WhatsAppIntentDisp"

    /**
     * Prepares the accessibility task state and launches WhatsApp/WhatsApp Business
     * targeting a recipient phone number, group name, or status feed.
     *
     * Handles text messages, media attachment sharing via `ACTION_SEND`, deep link schemes
     * (`whatsapp://send` and `https://api.whatsapp.com/send`), and flags for foreground task execution.
     *
     * @param context Application or activity context used to start activities.
     * @param taskId Unique identifier of the task being processed.
     * @param channel The delivery channel ([ChannelType.WHATSAPP], [ChannelType.WHATSAPP_BUSINESS], [ChannelType.WHATSAPP_GROUP], or status).
     * @param recipient Target phone number or group name string.
     * @param message Text payload to compose or attach.
     * @param shouldRelock Whether the keyguard should be relocked after completion.
     * @param isBatch Indicates if this invocation is part of a bulk campaign run.
     * @param mediaUri Optional content URI pointing to an image or video to dispatch.
     */
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
        val isGroup = channel == ChannelType.WHATSAPP_GROUP || recipient.startsWith("group:", ignoreCase = true)
        val targetPkg = when (channel) {
            ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_BUSINESS_STATUS -> "com.whatsapp.w4b"
            ChannelType.WHATSAPP_STATUS -> if (recipient.contains("w4b", ignoreCase = true) || recipient.contains("business", ignoreCase = true)) "com.whatsapp.w4b" else (WhatsAppPackageHelper.getInstalledWhatsAppPackage(context) ?: "com.whatsapp")
            else -> WhatsAppPackageHelper.getInstalledWhatsAppPackage(context) ?: "com.whatsapp"
        }

        when {
            channel == ChannelType.WHATSAPP_STATUS || channel == ChannelType.WHATSAPP_BUSINESS_STATUS -> {
                TaskAccessibilityService.prepareForStatus(
                    taskId = taskId,
                    packageName = targetPkg,
                    messageText = message,
                    shouldRelock = shouldRelock,
                    mediaUri = mediaUri
                )
                if (mediaUri.isNotBlank()) {
                    try {
                        val uri = mediaUri.toUri()
                        val detectedMime = context.contentResolver.getType(uri).orEmpty().lowercase()
                        val isVideo = detectedMime.startsWith("video/") || mediaUri.substringBefore('?').endsWith(".mp4", ignoreCase = true) || mediaUri.substringBefore('?').endsWith(".3gp", ignoreCase = true)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = detectedMime.takeIf { it.startsWith("image/") || it.startsWith("video/") } ?: if (isVideo) "video/*" else "image/*"
                            setPackage(targetPkg)
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("media", uri)
                            if (message.isNotBlank()) putExtra(Intent.EXTRA_TEXT, message)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        try { context.grantUriPermission(targetPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { Log.d(TAG, "grantUriPermission skipped: ${e.message}") }
                        context.startActivity(shareIntent)
                        Log.d(TAG, "launchWhatsApp: Launched ACTION_SEND for WhatsApp Status with media: $mediaUri")
                        AppFileLogger.action(context.applicationContext, TAG, "Launched ACTION_SEND for WhatsApp Status with media: $mediaUri")
                    } catch (e: Exception) {
                        Log.e(TAG, "launchWhatsApp: Failed launching ACTION_SEND: ${e.message}")
                    }
                } else {
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
            isGroup -> {
                val cleanGroupName = recipient.replaceFirst(Regex("^group\\s*:", RegexOption.IGNORE_CASE), "").trim()
                TaskAccessibilityService.prepareForGroup(
                    taskId = taskId,
                    packageName = targetPkg,
                    groupName = cleanGroupName,
                    messageText = message,
                    shouldRelock = shouldRelock,
                    isBatchCampaign = isBatch,
                    mediaUri = mediaUri
                )
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    context.startActivity(launchIntent)
                    Log.d(TAG, "launchWhatsApp: Launched $targetPkg for group search: $cleanGroupName")
                } else {
                    Log.e(TAG, "WhatsApp launch intent unavailable for group: $cleanGroupName")
                }
            }
            else -> {
                val cleanPhone = PhoneNumberNormalizer.formatForWhatsApp(recipient, PreferenceManager(context).defaultCountryCode)
                TaskAccessibilityService.prepareForSend(
                    taskId = taskId,
                    packageName = targetPkg,
                    recipient = cleanPhone,
                    messageText = message,
                    shouldRelock = shouldRelock,
                    isBatchCampaign = isBatch
                )

                    if (mediaUri.isNotBlank()) {
                        try {
                            val uri = mediaUri.toUri()
                            val detectedMime = context.contentResolver.getType(uri).orEmpty().lowercase()
                            val isVideo = detectedMime.startsWith("video/") || mediaUri.substringBefore('?').endsWith(".mp4", ignoreCase = true) || mediaUri.substringBefore('?').endsWith(".3gp", ignoreCase = true)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = detectedMime.takeIf { it.startsWith("image/") || it.startsWith("video/") } ?: if (isVideo) "video/*" else "image/*"
                                setPackage(targetPkg)
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newRawUri("media", uri)
                                if (message.isNotBlank()) putExtra(Intent.EXTRA_TEXT, message)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            try {
                                context.grantUriPermission(targetPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (e: Exception) {
                                Log.d(TAG, "grantUriPermission skipped: ${e.message}")
                            }
                            context.startActivity(shareIntent)
                            Log.d(TAG, "launchWhatsApp: Launched ACTION_SEND for Direct Chat with media: $mediaUri")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed launching media ACTION_SEND for direct chat: ${e.message}")
                        }
                    } else {
                        try {
                            val encoded = URLEncoder.encode(message, "UTF-8")
                            val uri = "whatsapp://send?phone=$cleanPhone&text=$encoded".toUri()
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
                                val fallbackUri = "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encoded".toUri()
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

    /**
     * Dispatches an intent to open a WhatsApp direct conversation and prepares the
     * accessibility engine to initiate a voice call.
     *
     * @param context Context used to start activities.
     * @param taskId Unique identifier for the calling task.
     * @param channel The WhatsApp channel to target.
     * @param recipient Target phone number string.
     * @param callDurationSeconds Desired duration in seconds before the call is hung up (0 for manual termination).
     * @param shouldRelock Whether the device should be relocked after concluding the call.
     * @return `true` if the activity launch intent succeeded; `false` on failure.
     */
    fun launchWhatsAppCall(
        context: Context,
        taskId: Long,
        channel: ChannelType,
        recipient: String,
        callDurationSeconds: Int = 0,
        shouldRelock: Boolean = false
    ): Boolean {
        val targetPkg = when (channel) {
            ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_BUSINESS_CALL -> "com.whatsapp.w4b"
            ChannelType.WHATSAPP, ChannelType.WHATSAPP_CALL -> "com.whatsapp"
            else -> WhatsAppPackageHelper.getInstalledWhatsAppPackage(context) ?: "com.whatsapp"
        }
        val cleanPhone = PhoneNumberNormalizer.formatForWhatsApp(recipient, PreferenceManager(context).defaultCountryCode)

        TaskAccessibilityService.clearPending()
        TaskAccessibilityService.markDispatchPending()
        TaskAccessibilityService.prepareForWhatsAppCall(
            taskId = taskId,
            packageName = targetPkg,
            recipient = cleanPhone,
            callDurationSeconds = callDurationSeconds,
            shouldRelock = shouldRelock
        )

        return try {
            try {
                val directUri = "whatsapp://send?phone=$cleanPhone".toUri()
                val directIntent = Intent(Intent.ACTION_VIEW, directUri).apply {
                    setPackage(targetPkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(directIntent)
                Log.d(TAG, "launchWhatsAppCall: Dispatched direct WhatsApp URI for $cleanPhone")
                true
            } catch (ex: Exception) {
                Log.w(TAG, "launchWhatsAppCall: whatsapp:// scheme failed, falling back to api.whatsapp.com: ${ex.message}")
                val callUri = "https://api.whatsapp.com/send?phone=$cleanPhone".toUri()
                val callIntent = Intent(Intent.ACTION_VIEW, callUri).apply {
                    setPackage(targetPkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(callIntent)
                Log.d(TAG, "launchWhatsAppCall: Dispatched https fallback for $cleanPhone")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "launchWhatsAppCall failed: ${e.message}", e)
            false
        }
    }
}
