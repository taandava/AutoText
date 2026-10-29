/**
 * Package: com.jmd.autotext.ui.screen
 * Module: UI Task Type Selection Layer
 *
 * Modal task picker displaying clear, categorized task types:
 * SMS, WhatsApp, WhatsApp Business, WhatsApp Status (sub-flow for Personal vs Business W4B),
 * Phone Calls, WhatsApp Voice Calls, Fake Calls, Contact Sequences, Daily Campaigns, Alarms, and Sound Modes.
 *
 * Linked With:
 * - Screens: `TasksScreen`, `DailyCampaignScreen`,
 *   `MainAppHost`
 * - Models: [com.jmd.autotext.data.model.ChannelType], [com.jmd.autotext.ui.model.TaskTypeItem]
 */
package com.jmd.autotext.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

import com.jmd.autotext.data.model.*
import com.jmd.autotext.ui.model.*
import com.jmd.autotext.ui.theme.*

/**
 * Modal selection dialog providing categorized creation options for all supported automation types.
 *
 * Supported Dispatch Pathways:
 * - Direct Messaging: SMS via SIM, WhatsApp Chat, WhatsApp Business Chat, WhatsApp Group dispatch.
 * - Calling Automation: Cellular Phone Calls, WhatsApp VoIP Voice Calls, Incoming Fake Call simulation.
 * - Media Updates: Personal WhatsApp Status, WhatsApp Business Status updates with captions.
 * - Advanced Outreach: Multi-step Contact Sequences, Automated Unique Contact Daily Campaigns.
 * - Device Controls: Timed Alarms and Scheduled Device Sound Mode toggles.
 *
 * @param onDismiss Invoked when user taps outside or cancels selection.
 * @param onSelectChannel Callback receiving selected single-shot [ChannelType].
 * @param onSelectSequence Callback invoked when creating a multi-step sequence for a channel.
 * @param onSelectDailyCampaign Callback invoked when launching the daily campaign builder.
 */
@Composable
fun taskTypeSelectionDialog(
    onDismiss: () -> Unit,
    onSelectChannel: (ChannelType) -> Unit,
    onSelectSequence: (ChannelType) -> Unit = {},
    onSelectDailyCampaign: () -> Unit = {},
) {
    // Sub-flow state: null = root list, "STATUS" = choose WhatsApp vs W4B status, "SEQUENCE" = choose SMS vs WA vs W4B sequence
    var selectedSubFlow by remember { mutableStateOf<String?>(null) }

    val taskTypes = listOf(
        TaskTypeItem(
            title = "SMS Message",
            description = "Schedule direct automated SMS via device SIM card",
            emoji = "📩",
            color = MaterialTheme.colorScheme.primary,
            channel = ChannelType.SMS,
        ),
        TaskTypeItem(
            title = "WhatsApp Chat",
            description = "Automated WhatsApp messages with optional media attachments",
            emoji = "💬",
            color = MaterialTheme.colorScheme.tertiary,
            channel = ChannelType.WHATSAPP,
        ),
        TaskTypeItem(
            title = "WhatsApp Business",
            description = "Send automated WhatsApp Business direct messages with media",
            emoji = "💼",
            color = MaterialTheme.colorScheme.secondary,
            channel = ChannelType.WHATSAPP_BUSINESS,
        ),
        TaskTypeItem(
            title = "Status Story",
            description = "Schedule auto-posting text or photo/video stories to Status",
            emoji = "📸",
            color = MaterialTheme.colorScheme.primary,
            channel = null,
            isStatus = true,
        ),
        TaskTypeItem(
            title = "Phone Call",
            description = "Schedule direct phone call via device SIM card to specified destinations",
            emoji = "📞",
            color = MaterialTheme.colorScheme.primary,
            channel = ChannelType.CALL,
        ),
        TaskTypeItem(
            title = "WhatsApp Call",
            description = "Schedule automated WhatsApp or WA Business voice call to selected contact",
            emoji = "📞",
            color = MaterialTheme.colorScheme.tertiary,
            channel = null,
            isWaCall = true,
        ),
        TaskTypeItem(
            title = "Fake Call Simulator",
            description = "Simulate incoming call alert with custom name and ring style",
            emoji = "🎭",
            color = MaterialTheme.colorScheme.error,
            channel = ChannelType.FAKE_CALL,
        ),
        TaskTypeItem(
            title = "Contact Sequence (Multi-Task)",
            description = "Step-by-step drip messages for a contact across SMS & WhatsApp",
            emoji = "🔗",
            color = MaterialTheme.colorScheme.primary,
            channel = null,
            isSequence = true,
        ),
        TaskTypeItem(
            title = "Daily Auto Campaign",
            description = "High-speed paced automated outreach batch with 10-second intervals",
            emoji = "⚡",
            color = MaterialTheme.colorScheme.primary,
            channel = null,
            isDailyCampaign = true,
        ),
        TaskTypeItem(
            title = "Alarm / Reminder",
            description = "Local on-device audio alarm and reminder notification",
            emoji = "⏰",
            color = MaterialTheme.colorScheme.primary,
            channel = ChannelType.REMINDER,
        ),
        TaskTypeItem(
            title = "Sound Mode Switcher",
            description = "Automatically switch between Silent, Vibrate, and Normal sound",
            emoji = "🔊",
            color = MaterialTheme.colorScheme.secondary,
            channel = ChannelType.SOUND_MODE,
        ),
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = AppDesignTokens.ShapeLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .systemBarsPadding()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header with dynamic back button if inside a sub-flow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (selectedSubFlow != null) {
                            IconButton(
                                onClick = { selectedSubFlow = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Column {
                            Text(
                                text = when (selectedSubFlow) {
                                    "STATUS" -> "Choose Status Platform"
                                    "SEQUENCE" -> "Choose Sequence Channel"
                                    "WA_CALL" -> "Choose WhatsApp Calling App"
                                    else -> "Choose Task Type"
                                },
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (selectedSubFlow) {
                                    "STATUS" -> "Select which app to post your Status story to"
                                    "SEQUENCE" -> "Select messaging channel for this multi-step sequence"
                                    "WA_CALL" -> "Select WhatsApp or WhatsApp Business for this voice call"
                                    else -> "Select what automated action you want to schedule"
                                },
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedSubFlow) {
                    "STATUS" -> {
                        // Sub-selection: WhatsApp Status vs WhatsApp Business Status
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            dialogOptionTile(
                                title = "WhatsApp Status",
                                description = "Auto-post text or photo/video stories to personal WhatsApp",
                                badge = "Personal",
                                emoji = "📸",
                                color = MaterialTheme.colorScheme.tertiary,
                                onClick = {
                                    onSelectChannel(ChannelType.WHATSAPP_STATUS)
                                }
                            )

                            dialogOptionTile(
                                title = "WhatsApp Business Status",
                                description = "Auto-post text or media updates to WhatsApp Business (W4B)",
                                badge = "Business",
                                emoji = "💼",
                                color = MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    onSelectChannel(ChannelType.WHATSAPP_BUSINESS_STATUS)
                                }
                            )
                        }
                    }
                    "SEQUENCE" -> {
                        // Sub-selection: SMS vs WhatsApp vs WhatsApp Business Sequence
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            dialogOptionTile(
                                title = "SMS Message Sequence",
                                description = "Step-by-step drip messages via device SIM card (Carrier Direct)",
                                badge = "SIM Direct",
                                emoji = "📩",
                                color = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    onSelectSequence(ChannelType.SMS)
                                }
                            )

                            dialogOptionTile(
                                title = "WhatsApp Sequence",
                                description = "Multi-step scheduled messages via personal WhatsApp",
                                badge = "Personal WA",
                                emoji = "💬",
                                color = MaterialTheme.colorScheme.tertiary,
                                onClick = {
                                    onSelectSequence(ChannelType.WHATSAPP)
                                }
                            )

                            dialogOptionTile(
                                title = "WhatsApp Business (W4B) Sequence",
                                description = "Client outreach and paced follow-up sequence via WhatsApp Business",
                                badge = "Business W4B",
                                emoji = "💼",
                                color = MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    onSelectSequence(ChannelType.WHATSAPP_BUSINESS)
                                }
                            )
                        }
                    }
                    "WA_CALL" -> {
                        // Sub-selection: WhatsApp Call vs WhatsApp Business Call
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            dialogOptionTile(
                                title = "WhatsApp Voice Call",
                                description = "Automated VoIP voice call via personal WhatsApp Messenger",
                                badge = "Personal",
                                emoji = "📞",
                                color = MaterialTheme.colorScheme.tertiary,
                                onClick = {
                                    onSelectChannel(ChannelType.WHATSAPP_CALL)
                                }
                            )

                            dialogOptionTile(
                                title = "WhatsApp Business Voice Call",
                                description = "Automated VoIP voice call via WhatsApp Business (W4B)",
                                badge = "Business",
                                emoji = "💼",
                                color = MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    onSelectChannel(ChannelType.WHATSAPP_BUSINESS_CALL)
                                }
                            )
                        }
                    }
                    else -> {
                        // Main Task Type List
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxHeight(0.78f)
                        ) {
                            items(taskTypes.size) { index ->
                                val item = taskTypes[index]
                                Surface(
                                    shape = AppDesignTokens.ShapeMedium,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(AppDesignTokens.ShapeMedium)
                                        .clickable {
                                            when {
                                                item.isStatus -> {
                                                    selectedSubFlow = "STATUS"
                                                }
                                                item.isSequence -> {
                                                    selectedSubFlow = "SEQUENCE"
                                                }
                                                item.isWaCall -> {
                                                    selectedSubFlow = "WA_CALL"
                                                }
                                                item.isDailyCampaign -> {
                                                    onSelectDailyCampaign()
                                                }
                                                else -> {
                                                    item.channel?.let { onSelectChannel(it) }
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(item.color.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.emoji,
                                                fontSize = 18.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = item.description,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 14.sp
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun dialogOptionTile(
    title: String,
    description: String,
    badge: String,
    emoji: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = AppDesignTokens.ShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppDesignTokens.ShapeMedium)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = AppDesignTokens.ShapePill,
                        color = color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

