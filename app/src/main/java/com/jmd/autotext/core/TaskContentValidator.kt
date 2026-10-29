/**
 * Package: com.jmd.autotext.core
 * Module: Core Automation Utilities
 *
 * Provides pre-flight media URI accessibility inspection, MIME resolution,
 * and channel-specific payload validation (SMS text-only vs WhatsApp multimedia).
 */
package com.jmd.autotext.core

import android.content.Context
import androidx.core.net.toUri
import com.jmd.autotext.data.model.ChannelType
import java.io.File

/**
 * Result abstraction for attachment accessibility and format validation.
 */
sealed class AttachmentValidationResult {
    /** Indicates no media URI was supplied. */
    object Empty : AttachmentValidationResult()

    /**
     * Indicates attachment was verified as accessible and compatible.
     *
     * @property mimeType Resolved MIME type string (e.g. "image/jpeg", "video/mp4").
     * @property isVideo True if media represents a video file; false for images.
     */
    data class Valid(val mimeType: String, val isVideo: Boolean) : AttachmentValidationResult()

    /**
     * Indicates attachment access failed or file format is unsupported.
     *
     * @property reason Human-readable diagnostic explanation.
     */
    data class Invalid(val reason: String) : AttachmentValidationResult()
}

/**
 * Result abstraction for complete task payload validation across channels.
 */
sealed class ContentValidationResult {
    /** Plain text message payload accepted without media. */
    object AcceptTextOnly : ContentValidationResult()

    /** Media-only payload accepted without caption (valid for WhatsApp/W4B photo/video statuses and chats). */
    data class AcceptMediaOnly(val mimeType: String, val isVideo: Boolean) : ContentValidationResult()

    /** Media payload with associated text caption accepted. */
    data class AcceptMediaWithCaption(val mimeType: String, val isVideo: Boolean, val caption: String) : ContentValidationResult()

    /** Payload rejected due to missing requirements or inaccessible attachments. */
    data class Reject(val reason: String) : ContentValidationResult()
}

/**
 * Centralized validator ensuring tasks have valid text, accessible media URIs, and valid channel rules.
 */
object TaskContentValidator {

    /**
     * Validates that an attachment URI is readable via ContentResolver or File System,
     * resolving its MIME type and image/video classification.
     *
     * @param context Active Android [Context] (nullable for test environments).
     * @param uriString URI string (content:// or file://) to inspect.
     * @return [AttachmentValidationResult] detailing accessibility and MIME attributes.
     */
    fun validateAttachmentAccess(context: Context?, uriString: String?): AttachmentValidationResult {
        val clean = uriString?.trim().orEmpty()
        if (clean.isBlank()) return AttachmentValidationResult.Empty

        val scheme = try {
            clean.toUri().scheme?.lowercase()
        } catch (_: Throwable) {
            clean.substringBefore("://", "").lowercase().ifEmpty { null }
        }

        if (scheme != null && scheme != "content" && scheme != "file") {
            return AttachmentValidationResult.Invalid("Unsupported attachment URI scheme: '$scheme'")
        }

        fun resolveFile(rawUri: String): File {
            val withoutScheme = if (rawUri.startsWith("file://", ignoreCase = true)) {
                rawUri.substring(7)
            } else rawUri
            return File(withoutScheme)
        }

        var detectedMime = ""
        var isAccessible = false

        if (context != null) {
            try {
                val parsedUri = clean.toUri()
                detectedMime = context.contentResolver.getType(parsedUri).orEmpty().lowercase()
                context.contentResolver.openInputStream(parsedUri)?.use {
                    isAccessible = true
                }
            } catch (_: Exception) {
                // If content resolver fails or file scheme direct check
                if (scheme == "file" || scheme == null) {
                    val f = resolveFile(clean)
                    if (f.exists() && f.canRead()) {
                        isAccessible = true
                    }
                }
            }
        } else {
            // Context-free fallback (e.g. unit tests): check file system or assume valid if content URI
            if (scheme == "content") {
                isAccessible = true
            } else {
                val f = resolveFile(clean)
                isAccessible = f.exists() && f.canRead()
            }
        }

        if (!isAccessible) {
            return AttachmentValidationResult.Invalid("Attachment is inaccessible or file no longer exists at: $clean")
        }

        // Determine if image or video
        val uriPathLower = clean.substringBefore('?').lowercase()
        val isVideo = detectedMime.startsWith("video/") ||
                uriPathLower.endsWith(".mp4") ||
                uriPathLower.endsWith(".3gp") ||
                uriPathLower.endsWith(".mkv") ||
                uriPathLower.endsWith(".webm")

        val isImage = detectedMime.startsWith("image/") ||
                uriPathLower.endsWith(".jpg") ||
                uriPathLower.endsWith(".jpeg") ||
                uriPathLower.endsWith(".png") ||
                uriPathLower.endsWith(".webp") ||
                uriPathLower.endsWith(".gif") ||
                uriPathLower.endsWith(".bmp")

        if (!isVideo && !isImage && detectedMime.isNotBlank()) {
            return AttachmentValidationResult.Invalid("Unsupported attachment type ($detectedMime). Only images and videos are supported.")
        }

        val effectiveMime = when {
            detectedMime.isNotBlank() -> detectedMime
            isVideo -> "video/*"
            else -> "image/*"
        }

        return AttachmentValidationResult.Valid(effectiveMime, isVideo)
    }

    /**
     * Validates combined message text and attachment URI according to channel constraints.
     *
     * @param context Active Android [Context].
     * @param channel Target communication [ChannelType].
     * @param messageText Draft message text.
     * @param mediaUri Media attachment URI string.
     * @return [ContentValidationResult] accepting or rejecting payload.
     */
    fun validateContent(
        context: Context?,
        channel: ChannelType,
        messageText: String,
        mediaUri: String?
    ): ContentValidationResult {
        val trimmedText = messageText.trim()
        val hasText = trimmedText.isNotEmpty()
        return when (val attachmentResult = validateAttachmentAccess(context, mediaUri)) {
            is AttachmentValidationResult.Invalid -> {
                ContentValidationResult.Reject(attachmentResult.reason)
            }
            is AttachmentValidationResult.Empty -> {
                if (hasText) {
                    ContentValidationResult.AcceptTextOnly
                } else {
                    if (channel in listOf(ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_GROUP, ChannelType.WHATSAPP_STATUS, ChannelType.WHATSAPP_BUSINESS_STATUS)) {
                        ContentValidationResult.Reject("Enter message text or attach an image/video")
                    } else {
                        ContentValidationResult.Reject("Enter message text")
                    }
                }
            }
            is AttachmentValidationResult.Valid -> {
                if (channel == ChannelType.SMS) {
                    if (!hasText) {
                        ContentValidationResult.Reject("SMS requires message text (MMS is not supported)")
                    } else {
                        ContentValidationResult.AcceptTextOnly
                    }
                } else if (hasText) {
                    ContentValidationResult.AcceptMediaWithCaption(
                        mimeType = attachmentResult.mimeType,
                        isVideo = attachmentResult.isVideo,
                        caption = trimmedText
                    )
                } else {
                    ContentValidationResult.AcceptMediaOnly(
                        mimeType = attachmentResult.mimeType,
                        isVideo = attachmentResult.isVideo,
                    )
                }
            }
        }
    }
}
