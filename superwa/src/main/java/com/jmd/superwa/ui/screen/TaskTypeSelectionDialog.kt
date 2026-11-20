/**
 * Package: com.jmd.superwa.ui.screen
 * Module: Application Core Subsystem
 *
 * What it is:
 * Modal task picker displaying categorized task types for SuperWA:
 * WhatsApp Chat, WA Business, WA Group, Status Story, Voice Call, and Daily Campaign.
 */

package com.jmd.superwa.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jmd.superwa.data.model.ChannelType
import com.jmd.superwa.ui.model.TaskTypeItem
import com.jmd.superwa.ui.theme.AppDesignTokens

/**
 * Modal selection dialog providing categorized creation options for SuperWA task types.
 *
 * @param onDismiss Invoked when user cancels selection.
 * @param onSelectChannel Callback receiving selected [ChannelType].
 * @param onSelectDailyCampaign Callback invoked when launching the daily campaign builder.
 */
@Composable
fun TaskTypeSelectionDialog(
    onDismiss: () -> Unit,
    onSelectChannel: (ChannelType) -> Unit,
    onSelectDailyCampaign: () -> Unit = {}
) {
    var selectedSubFlow by remember { mutableStateOf<String?>(null) }

    val taskTypes = listOf(
        TaskTypeItem(
            title = "WhatsApp Chat",
            description = "Send WhatsApp messages with optional media attachments",
            icon = Icons.AutoMirrored.Filled.Chat,
            color = Color(0xFF16A34A),
            channel = ChannelType.WHATSAPP
        ),
        TaskTypeItem(
            title = "WA Business",
            description = "Send WhatsApp Business direct messages with media",
            icon = Icons.Filled.BusinessCenter,
            color = Color(0xFF0D9488),
            channel = ChannelType.WHATSAPP_BUSINESS
        ),
        TaskTypeItem(
            title = "WA Group",
            description = "Send message to a WhatsApp or Business group chat",
            icon = Icons.Filled.Groups,
            color = Color(0xFF15803D),
            channel = null,
            isGroup = true
        ),
        TaskTypeItem(
            title = "Status Story",
            description = "Post text or photo/video stories to Status",
            icon = Icons.Filled.PhotoCamera,
            color = Color(0xFF7C3AED),
            channel = null,
            isStatus = true
        ),
        TaskTypeItem(
            title = "Voice Call",
            description = "Schedule WhatsApp voice call to contact",
            icon = Icons.Filled.PhoneInTalk,
            color = Color(0xFF06B6D4),
            channel = null,
            isWaCall = true
        ),
        TaskTypeItem(
            title = "Daily Campaign",
            description = "High-speed paced outreach batch with cooldown pacing",
            icon = Icons.Filled.RocketLaunch,
            color = Color(0xFFEF4444),
            channel = null,
            isDailyCampaign = true
        )
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AppDesignTokens.ShapeLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Back if inside sub-flow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedSubFlow != null) {
                            IconButton(onClick = { selectedSubFlow = null }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Column {
                            Text(
                                text = when (selectedSubFlow) {
                                    "STATUS" -> "Status Type"
                                    "WA_CALL" -> "Call Type"
                                    "GROUP" -> "Group Type"
                                    else -> "New Task"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select task type",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (selectedSubFlow) {
                    "STATUS" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubFlowOptionRow(
                                title = "Personal Status",
                                description = "Post update to WhatsApp Personal Status",
                                icon = Icons.Filled.PhotoCamera,
                                color = Color(0xFF7C3AED),
                                onClick = {
                                    onDismiss()
                                    onSelectChannel(ChannelType.WHATSAPP_STATUS)
                                }
                            )
                            SubFlowOptionRow(
                                title = "Business Status",
                                description = "Post update to WhatsApp Business Status",
                                icon = Icons.Filled.BusinessCenter,
                                color = Color(0xFF0D9488),
                                onClick = {
                                    onDismiss()
                                    onSelectChannel(ChannelType.WHATSAPP_BUSINESS_STATUS)
                                }
                            )
                        }
                    }
                    "WA_CALL" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubFlowOptionRow(
                                title = "Personal Call",
                                description = "Place voice call via personal WhatsApp",
                                icon = Icons.Filled.PhoneInTalk,
                                color = Color(0xFF06B6D4),
                                onClick = {
                                    onDismiss()
                                    onSelectChannel(ChannelType.WHATSAPP_CALL)
                                }
                            )
                            SubFlowOptionRow(
                                title = "Business Call",
                                description = "Place voice call via WhatsApp Business",
                                icon = Icons.Filled.BusinessCenter,
                                color = Color(0xFF0D9488),
                                onClick = {
                                    onDismiss()
                                    onSelectChannel(ChannelType.WHATSAPP_BUSINESS_CALL)
                                }
                            )
                        }
                    }
                    "GROUP" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubFlowOptionRow(
                                title = "Personal Group",
                                description = "Send message to personal WhatsApp group",
                                icon = Icons.Filled.Groups,
                                color = Color(0xFF15803D),
                                onClick = {
                                    onDismiss()
                                    onSelectChannel(ChannelType.WHATSAPP_GROUP)
                                }
                            )
                            SubFlowOptionRow(
                                title = "Business Group",
                                description = "Send message to WhatsApp Business group",
                                icon = Icons.Filled.BusinessCenter,
                                color = Color(0xFF0D9488),
                                onClick = {
                                    onDismiss()
                                    onSelectChannel(ChannelType.WHATSAPP_BUSINESS_GROUP)
                                }
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(taskTypes.size) { index ->
                                val item = taskTypes[index]
                                TaskTypeRow(
                                    item = item,
                                    onClick = {
                                        when {
                                            item.isDailyCampaign -> {
                                                onDismiss()
                                                onSelectDailyCampaign()
                                            }
                                            item.isStatus -> selectedSubFlow = "STATUS"
                                            item.isWaCall -> selectedSubFlow = "WA_CALL"
                                            item.isGroup -> selectedSubFlow = "GROUP"
                                            item.channel != null -> {
                                                onDismiss()
                                                onSelectChannel(item.channel)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskTypeRow(
    item: TaskTypeItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = AppDesignTokens.ShapeMedium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(item.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.icon != null) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = item.color,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = item.emoji.ifBlank { "💬" },
                        fontSize = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SubFlowOptionRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = AppDesignTokens.ShapeMedium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
