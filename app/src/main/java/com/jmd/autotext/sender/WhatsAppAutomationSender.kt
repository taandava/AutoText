package com.jmd.autotext.sender

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.ui.automation.AutomationLauncherActivity
import com.jmd.autotext.util.AppFileLogger
import com.jmd.autotext.util.PhoneNumberNormalizer
import java.net.URLEncoder

class WhatsAppAutomationSender(private val context: Context) {

    companion object {
        private const val TAG = "WASender"
    }

    fun send(
        taskId: Long,
        channel: ChannelType,
        recipientPhone: String,
        messageText: String,
        shouldRelock: Boolean = PreferenceManager(context).isAutoLockAfterSendEnabled,
        isBatchCampaign: Boolean = false,
        mediaUri: String = ""
    ): Boolean {
        return try {
            // Proactively ensure Accessibility Service is alive and connected before dispatching WhatsApp
            com.jmd.autotext.service.AccessibilityHealthManager.ensureServiceAlive(context)

            val prefs = PreferenceManager(context)
            val isStatus = channel == ChannelType.WHATSAPP_STATUS
            val isGroup = channel == ChannelType.WHATSAPP_GROUP || recipientPhone.startsWith("group:", ignoreCase = true)

            val effectiveRecipient = when {
                isStatus -> "STATUS"
                isGroup -> if (recipientPhone.startsWith("group:", ignoreCase = true)) recipientPhone.substringAfter(':').trim() else recipientPhone.trim()
                else -> PhoneNumberNormalizer.formatForWhatsApp(recipientPhone, prefs.defaultCountryCode)
            }

            if (!isStatus && effectiveRecipient.isEmpty()) {
                Log.e(TAG, "Recipient is empty after normalization: raw='$recipientPhone'")
                AppFileLogger.e(context, TAG, "Recipient is empty after normalization: raw='$recipientPhone'")
                return false
            }

            val targetPkg = when {
                channel == ChannelType.WHATSAPP_BUSINESS -> "com.whatsapp.w4b"
                isStatus && (recipientPhone.contains("w4b", ignoreCase = true) || recipientPhone.contains("business", ignoreCase = true)) -> "com.whatsapp.w4b"
                else -> com.jmd.autotext.util.WhatsAppPackageHelper.getInstalledWhatsAppPackage(context) ?: "com.whatsapp"
            }

            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val isScreenOff = !pm.isInteractive
            val isLocked = km.isKeyguardLocked || km.isDeviceLocked ||
                    AutoTextAccessibilityService.isKeyguardWindowShowing()

            Log.d(TAG, "Dispatching WhatsApp message to $effectiveRecipient. isScreenOff: $isScreenOff, isLocked: $isLocked, pkg: $targetPkg, isGroup: $isGroup, isStatus: $isStatus, media: $mediaUri")
            AppFileLogger.action(context, TAG, "Dispatching WhatsApp to $effectiveRecipient (locked=$isLocked, screenOff=$isScreenOff, pkg=$targetPkg, isGroup=$isGroup, isStatus=$isStatus, media=$mediaUri)")

            if (isScreenOff || isLocked) {
                // If screen is off or device is locked, launch via AutomationLauncherActivity
                // which wakes the device, displays over keyguard, and triggers auto-unlock
                val launchIntent = Intent(context, AutomationLauncherActivity::class.java).apply {
                    putExtra(AutomationLauncherActivity.EXTRA_TASK_ID, taskId)
                    putExtra(AutomationLauncherActivity.EXTRA_CHANNEL, channel.name)
                    putExtra(AutomationLauncherActivity.EXTRA_RECIPIENT, effectiveRecipient)
                    putExtra(AutomationLauncherActivity.EXTRA_MESSAGE, messageText)
                    putExtra(AutomationLauncherActivity.EXTRA_RELOCK, shouldRelock)
                    putExtra(AutomationLauncherActivity.EXTRA_IS_BATCH, isBatchCampaign)
                    putExtra(AutomationLauncherActivity.EXTRA_MEDIA_URI, mediaUri)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                AutoTextAccessibilityService.markDispatchPending()

                var startedDirectly = false
                try {
                    context.startActivity(launchIntent)
                    startedDirectly = true
                    Log.d(TAG, "Launched AutomationLauncherActivity over lock screen")
                    AppFileLogger.action(context, TAG, "Launched AutomationLauncherActivity over lock screen for $effectiveRecipient")
                } catch (e: Exception) {
                    Log.w(TAG, "Direct activity start error: ${e.message}")
                    AppFileLogger.w(context, TAG, "Direct activity start error: ${e.message}", e)
                }

                // Fallback to FullScreenIntent notification if direct startActivity failed
                if (!startedDirectly) {
                    try {
                        val fullScreenPending = android.app.PendingIntent.getActivity(
                            context,
                            (taskId % 100000).toInt(),
                            launchIntent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        val notif = androidx.core.app.NotificationCompat.Builder(context, com.jmd.autotext.AutoTextApplication.CHANNEL_TASKS)
                            .setSmallIcon(android.R.drawable.stat_notify_sync)
                            .setContentTitle("AutoText Automation")
                            .setContentText("Sending WhatsApp message...")
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                            .setFullScreenIntent(fullScreenPending, true)
                            .setAutoCancel(true)
                            .setTimeoutAfter(8000L)
                            .build()

                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        nm.notify(9988, notif)
                    } catch (e: Exception) {
                        Log.w(TAG, "FullScreenIntent notification error: ${e.message}")
                    }
                }

                true
            } else {
                // Device is already awake and unlocked: direct dispatch
                if (isStatus) {
                    AutoTextAccessibilityService.prepareForStatus(
                        taskId = taskId,
                        packageName = targetPkg,
                        messageText = messageText,
                        shouldRelock = shouldRelock,
                        mediaUri = mediaUri
                    )
                    if (mediaUri.isNotBlank()) {
                        try {
                            val uri = Uri.parse(mediaUri)
                            val detectedMime = context.contentResolver.getType(uri).orEmpty().lowercase()
                            val isVideo = detectedMime.startsWith("video/") || mediaUri.substringBefore('?').endsWith(".mp4", ignoreCase = true) || mediaUri.substringBefore('?').endsWith(".3gp", ignoreCase = true)
                            val mimeType = detectedMime.takeIf { it.startsWith("image/") || it.startsWith("video/") } ?: if (isVideo) "video/*" else "image/*"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                setPackage(targetPkg)
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = android.content.ClipData.newRawUri("media", uri)
                                if (messageText.isNotBlank()) putExtra(Intent.EXTRA_TEXT, messageText)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            try {
                                context.grantUriPermission(targetPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (_: Exception) {}
                            context.startActivity(shareIntent)
                            Log.d(TAG, "Directly launched ACTION_SEND for WhatsApp Status with media: $mediaUri")
                            AppFileLogger.action(context, TAG, "Directly launched ACTION_SEND for WhatsApp Status with media: $mediaUri")
                            true
                        } catch (e: Exception) {
                            Log.e(TAG, "Direct launch ACTION_SEND failed: ${e.message}")
                            false
                        }
                    } else {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                        if (launchIntent != null) {
                            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            context.startActivity(launchIntent)
                            Log.d(TAG, "Launched WhatsApp home for text-status composer navigation")
                            AppFileLogger.action(context, TAG, "Launched WhatsApp home for text-status composer navigation")
                            true
                        } else {
                            Log.e(TAG, "WhatsApp launch intent unavailable for text status")
                            false
                        }
                    }
                } else if (isGroup) {
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
                        Log.d(TAG, "Directly launched WhatsApp for group search: $effectiveRecipient")
                        AppFileLogger.action(context, TAG, "Directly launched WhatsApp for group search: $effectiveRecipient")
                        true
                    } else false
                } else {
                    val encodedMessage = URLEncoder.encode(messageText, "UTF-8")

                    // Arm the accessibility service to click Send
                    AutoTextAccessibilityService.prepareForSend(
                        taskId = taskId,
                        packageName = targetPkg,
                        recipient = effectiveRecipient,
                        messageText = messageText,
                        shouldRelock = shouldRelock,
                        isBatchCampaign = isBatchCampaign
                    )

                    if (mediaUri.isNotBlank()) {
                        try {
                            val uri = Uri.parse(mediaUri)
                            val detectedMime = context.contentResolver.getType(uri).orEmpty().lowercase()
                            val isVideo = detectedMime.startsWith("video/") || mediaUri.substringBefore('?').endsWith(".mp4", ignoreCase = true) || mediaUri.substringBefore('?').endsWith(".3gp", ignoreCase = true)
                            val mimeType = detectedMime.takeIf { it.startsWith("image/") || it.startsWith("video/") } ?: if (isVideo) "video/*" else "image/*"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                setPackage(targetPkg)
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = android.content.ClipData.newRawUri("media", uri)
                                if (messageText.isNotBlank()) putExtra(Intent.EXTRA_TEXT, messageText)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            try {
                                context.grantUriPermission(targetPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (_: Exception) {}
                            context.startActivity(shareIntent)
                            Log.d(TAG, "Directly launched $targetPkg with media ACTION_SEND for $effectiveRecipient")
                            AppFileLogger.action(context, TAG, "Directly launched $targetPkg with media ACTION_SEND for $effectiveRecipient")
                            true
                        } catch (e: Exception) {
                            Log.e(TAG, "Direct media launch failed: ${e.message}")
                            false
                        }
                    } else {
                        try {
                            val directUri = Uri.parse("whatsapp://send?phone=$effectiveRecipient&text=$encodedMessage")
                            val intent = Intent(Intent.ACTION_VIEW, directUri).apply {
                                setPackage(targetPkg)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            context.startActivity(intent)
                            Log.d(TAG, "Directly launched $targetPkg via whatsapp:// for $effectiveRecipient")
                            AppFileLogger.action(context, TAG, "Directly launched $targetPkg via whatsapp:// for $effectiveRecipient")
                            true
                        } catch (e: Exception) {
                            Log.w(TAG, "whatsapp:// failed, trying api.whatsapp.com fallback", e)
                            val fallbackUri = Uri.parse("https://api.whatsapp.com/send?phone=$effectiveRecipient&text=$encodedMessage")
                            val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                                setPackage(targetPkg)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            context.startActivity(fallbackIntent)
                            AppFileLogger.action(context, TAG, "Launched $targetPkg via https fallback for $effectiveRecipient")
                            true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch WhatsApp automation: ${e.message}", e)
            AppFileLogger.e(context, TAG, "Failed to launch WhatsApp automation: ${e.message}", e)
            AutoTextAccessibilityService.clearPending()
            false
        }
    }
}
