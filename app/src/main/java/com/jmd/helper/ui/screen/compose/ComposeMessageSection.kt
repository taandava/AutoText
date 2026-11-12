/**
 * Package: com.jmd.helper.ui.screen.compose
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [ComposeMessageSection].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for ComposeMessageSection.
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.helper.data.model.*], [com.jmd.helper.ui.model.*]
 * - Services & Engines: [com.jmd.helper.service.TaskAccessibilityService]
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.clip], [androidx.compose.ui.text.style.TextOverflow]
 * - Core Security & Utilities: [com.jmd.helper.core.*]
 * - Android Framework & Jetpack: [androidx.compose.foundation.shape.CircleShape], [androidx.compose.foundation.shape.RoundedCornerShape], [androidx.compose.foundation.text.KeyboardOptions], [androidx.compose.material.icons.Icons]
 */

package com.jmd.helper.ui.screen.compose

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.helper.core.*
import com.jmd.helper.service.TaskAccessibilityService
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.data.model.*
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.model.*
import com.jmd.helper.ui.theme.*

/**
 * What it is:
 * Card managing message recipients, message drafting, and attachments.
 *
 * What it does:
 * Renders interactive Material 3 UI for [ComposeRecipientsAndMessageCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [ComposeMessageSection], [ComposeRecipientsAndMessageCard]
 * @param messageText Text draft content.
 * @param onMessageTextChange Callback when text is edited.
 * @param onOpenTemplates Callback to open template picker drawer.
 * @param smsPartCount Segment count calculated for SMS text length.
 * @param isUnicode True if message contains multi-byte or unicode characters.
 * @param mediaUri File URI of attached photo, video, or audio media.
 * @param onMediaUriChange Callback when attached media URI is modified.
 * @param onLaunchMediaPicker Callback to launch system photo/media selector.
 */
@Composable
fun ComposeRecipientsAndMessageCard(
    selectedChannel: ChannelType,
    selectedRecipients: List<SelectedRecipient>,
    duplicateWarning: String?,
    onDismissDuplicateWarning: () -> Unit,
    onClearAllRecipients: () -> Unit,
    onRemoveRecipient: (Int) -> Unit,
    recipientsInput: String,
    onRecipientsInputChange: (String) -> Unit,
    onAddManualRecipient: () -> Unit,
    onOpenWhatsAppContacts: () -> Unit,
    onAddDirectRecipient: ((name: String, phone: String) -> Unit)? = null,
    onOpenPoolPicker: () -> Unit,
    onOpenContactsPicker: () -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onOpenTemplates: (() -> Unit)? = null,
    smsPartCount: Int,
    isUnicode: Boolean,
    mediaUri: String = "",
    onMediaUriChange: (String) -> Unit = {},
    onLaunchMediaPicker: () -> Unit = {}
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge
    ) {
        val isWa = selectedChannel == ChannelType.WHATSAPP || selectedChannel == ChannelType.WHATSAPP_BUSINESS ||
            selectedChannel == ChannelType.WHATSAPP_GROUP || selectedChannel == ChannelType.WHATSAPP_BUSINESS_GROUP
        val isBusiness = selectedChannel == ChannelType.WHATSAPP_BUSINESS || selectedChannel == ChannelType.WHATSAPP_BUSINESS_GROUP

        // Recipients Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recipients",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (selectedRecipients.isNotEmpty()) {
                Text(
                    "Clear All (${selectedRecipients.size})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onClearAllRecipients() }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Pickers Flow
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isWa) {
                val localCtx = LocalContext.current
                val targetPkg = if (isBusiness) WhatsAppPackageHelper.PACKAGE_WHATSAPP_BUSINESS else WhatsAppPackageHelper.PACKAGE_WHATSAPP
                val labelPick = if (isBusiness) "Pick in W4B" else "Pick in WA"
                val labelContacts = if (isBusiness) "W4B Contacts" else "WA Contacts"
                GlassButton(
                    text = labelPick,
                    onClick = {
                        TaskAccessibilityService.startManualChatPicker(localCtx, targetPackage = targetPkg, channel = selectedChannel) { chatInfo ->
                            when (chatInfo.type) {
                                PickedChatType.STATUS -> {
                                    localCtx.showAppToast("Status updates cannot be added as recipients")
                                }
                                PickedChatType.CHANNEL -> {
                                    localCtx.showAppToast("Channels cannot receive direct messages")
                                }
                                PickedChatType.BROADCAST -> {
                                    onAddDirectRecipient?.invoke(chatInfo.title, "broadcast:${chatInfo.title}") ?: run {
                                        onAddManualRecipient()
                                    }
                                }
                                PickedChatType.GROUP -> {
                                    onAddDirectRecipient?.invoke(chatInfo.title, "group:${chatInfo.title}") ?: run {
                                        onAddManualRecipient()
                                    }
                                }
                                PickedChatType.INDIVIDUAL -> {
                                    val clean = chatInfo.title
                                    val repo = AppRepository(localCtx)
                                    val phoneFromRepo = repo.findPhoneForContactName(clean)
                                    val resolvedPhone = chatInfo.phoneOrId ?: phoneFromRepo ?: clean
                                    onAddDirectRecipient?.invoke(clean, resolvedPhone) ?: run {
                                        onAddManualRecipient()
                                    }
                                }
                            }
                        }
                    },
                    variant = GlassButtonVariant.PRIMARY,
                    icon = Icons.Default.Add,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
                GlassButton(
                    text = labelContacts,
                    onClick = onOpenWhatsAppContacts,
                    variant = GlassButtonVariant.SECONDARY,
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }
            GlassButton(
                text = "From Pool",
                onClick = onOpenPoolPicker,
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Default.Group,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = AppDesignTokens.ShapePill
            )
            GlassButton(
                text = "Device Contacts",
                onClick = onOpenContactsPicker,
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Default.Contacts,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                shape = AppDesignTokens.ShapePill
            )
        }

        // Duplicate Warning Banner
        if (duplicateWarning != null) {
            Surface(
                shape = AppDesignTokens.ShapeSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = duplicateWarning,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissDuplicateWarning,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Selected Recipients Chips / Cards
        if (selectedRecipients.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Selected Recipients (${selectedRecipients.size}):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Clear All",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onClearAllRecipients() }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selectedRecipients.forEachIndexed { idx, item ->
                    Surface(
                        shape = AppDesignTokens.ShapeSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    if (item.name.isNotBlank()) {
                                        Text(
                                            text = item.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = item.phone,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onRemoveRecipient(idx) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Manual Recipient Entry with Add Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            GlassInputField(
                value = recipientsInput,
                onValueChange = onRecipientsInputChange,
                label = "Add Recipient Manually",
                placeholder = "Enter phone number...",
                leadingIcon = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassButton(
                text = "Add",
                onClick = onAddManualRecipient,
                variant = GlassButtonVariant.PRIMARY,
                contentPadding = PaddingValues(horizontal = 14.dp),
                shape = AppDesignTokens.ShapeSmall,
                modifier = Modifier.height(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Message Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Message Content",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "1-tap insert tokens below",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Media Attachment Selector for WhatsApp, WA Business & Groups
        if (isWa) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Media Attachment (Optional)",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                if (mediaUri.isNotBlank()) {
                    Text(
                        "Remove",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onMediaUriChange("") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (mediaUri.isNotBlank()) {
                val isVideo = mediaUri.contains("mp4", ignoreCase = true) || mediaUri.contains("video", ignoreCase = true)
                Surface(
                    shape = AppDesignTokens.ShapeSmall,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isVideo) "Video Attached" else "Image / File Attached",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = mediaUri.substringAfterLast("/").take(32),
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        IconButton(onClick = { onMediaUriChange("") }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassButton(
                        text = "Attach Image",
                        onClick = onLaunchMediaPicker,
                        variant = GlassButtonVariant.SECONDARY,
                        icon = Icons.Default.AddPhotoAlternate,
                        modifier = Modifier.weight(1f),
                        shape = AppDesignTokens.ShapeSmall
                    )
                    GlassButton(
                        text = "Attach Video",
                        onClick = onLaunchMediaPicker,
                        variant = GlassButtonVariant.SECONDARY,
                        icon = Icons.Default.Videocam,
                        modifier = Modifier.weight(1f),
                        shape = AppDesignTokens.ShapeSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        GlassInputField(
            value = messageText,
            onValueChange = onMessageTextChange,
            label = "Message Text",
            placeholder = "Type your message...",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 3,
            maxLines = 4,
            accentColor = MaterialTheme.colorScheme.primary
        )

        // SMS Char & Counter
        if (selectedChannel == ChannelType.SMS) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${messageText.length} chars • $smsPartCount SMS part${if (smsPartCount > 1) "s" else ""} (${if (isUnicode) "UCS-2 Unicode" else "GSM-7"})",
                    fontSize = 11.sp,
                    color = if (smsPartCount > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                AppBadge(
                    text = "App cap: 100/day",
                    color = MaterialTheme.colorScheme.primary,
                    showDot = false
                )
            }
        }

        // Variable Insertion Chips
        Spacer(modifier = Modifier.height(10.dp))
        Text("Dynamic Variables:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        val variables = listOf(
            "{name}", "{first_name}", "{phone}",
            "{date}", "{short_date}", "{time}",
            "{date_dd_mm_yyyy}", "{date_dd_mm_yy}", "{date_words}",
            "{time_24h}", "{today}", "{ist_time}",
            "{day}", "{greeting}", "{namaste}",
            "{rupee}", "{month_name}", "{year}"
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            variables.chunked(3).forEach { rowVars ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowVars.forEach { v ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(AppDesignTokens.ShapePill)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), AppDesignTokens.ShapePill)
                                .clickable { onMessageTextChange("$messageText $v") }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(v, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (rowVars.size < 3) {
                        repeat(3 - rowVars.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
