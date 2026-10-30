package com.jmd.autotext.ui.screen.campaign

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jmd.autotext.core.*
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.repo.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun createDailyCampaignSheet(
    initialChannel: String = "SMS",
    onDismiss: () -> Unit,
    onSave: (DailyCampaignConfig) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var selectedChannel by remember(initialChannel) { mutableStateOf(initialChannel) }
    var messageTemplate by remember { mutableStateOf("") }
    var dailyLimitText by remember { mutableStateOf("30") }
    var intervalSecondsText by remember { mutableStateOf("10") }
    var scheduledHour by remember { mutableIntStateOf(9) }
    var scheduledMinute by remember { mutableIntStateOf(0) }
    var simSlot by remember { mutableIntStateOf(0) }

    val accentColor = when (selectedChannel) {
        "WHATSAPP_BUSINESS" -> MaterialTheme.colorScheme.primary
        "WHATSAPP" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(AppDesignTokens.ShapePill)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (selectedChannel) {
                            "WHATSAPP_BUSINESS" -> "Create WhatsApp Business Campaign"
                            "WHATSAPP" -> "Create WhatsApp Daily Campaign"
                            else -> "Create SMS Daily Campaign"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                appBadge(text = "NEW", color = accentColor)
            }

            Surface(
                shape = AppDesignTokens.ShapeMedium,
                color = accentColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (selectedChannel) {
                            "WHATSAPP_BUSINESS" -> "💼"
                            "WHATSAPP" -> "💬"
                            else -> "📩"
                        },
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when (selectedChannel) {
                                "WHATSAPP_BUSINESS" -> "Channel: WhatsApp Business (W4B)"
                                "WHATSAPP" -> "Channel: WhatsApp (Personal)"
                                else -> "Channel: SMS (Carrier Direct)"
                            },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (selectedChannel) {
                                "WHATSAPP_BUSINESS" -> "Outreach via WhatsApp Business app with paced delivery"
                                "WHATSAPP" -> "1-on-1 personal outreach with anti-ban delay pacing"
                                else -> "Direct cellular SMS outreach via device SIM card"
                            },
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Campaign Title
            glassInputField(
                value = title,
                onValueChange = { title = it },
                label = "Campaign Title / Name",
                placeholder = "e.g. Daily VIP Clients Follow-up",
                leadingIcon = Icons.AutoMirrored.Filled.Label,
                accentColor = accentColor
            )

            // Daily Limit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                glassInputField(
                    value = dailyLimitText,
                    onValueChange = { dailyLimitText = it.filter { c -> c.isDigit() }.take(4) },
                    label = "Batch Limit (Daily)",
                    placeholder = "30",
                    leadingIcon = Icons.Default.Pin,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )

                glassInputField(
                    value = intervalSecondsText,
                    onValueChange = { intervalSecondsText = it.filter { c -> c.isDigit() }.take(3) },
                    label = "Interval (Seconds)",
                    placeholder = "10",
                    leadingIcon = Icons.Default.Timer,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f)
                )
            }

            // Time Picker Button
            glassButton(
                text = "Dispatch Time: %02d:%02d".format(scheduledHour, scheduledMinute),
                onClick = {
                    showAppTimePicker(context, scheduledHour, scheduledMinute) { h, m ->
                        scheduledHour = h
                        scheduledMinute = m
                    }
                },
                icon = Icons.Default.AccessTime,
                variant = GlassButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )

            // SIM Slot if SMS
            if (selectedChannel == "SMS") {
                glassSegmentedControl(
                    items = listOf("Default SIM", "SIM 1", "SIM 2"),
                    selectedIndex = simSlot.coerceIn(0, 2),
                    onItemSelected = { simSlot = it },
                    activeColor = MaterialTheme.colorScheme.primary
                )
            }

            // Message Template
            glassInputField(
                value = messageTemplate,
                onValueChange = { messageTemplate = it },
                label = "Message Template",
                placeholder = "Enter message with {name}, {first_name}, {date}...",
                leadingIcon = Icons.Default.EditNote,
                singleLine = false,
                minLines = 3,
                maxLines = 5,
                accentColor = accentColor
            )

            // Variable helper chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("{name}", "{first_name}", "{phone}", "{date}").forEach { tag ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AppDesignTokens.ShapePill)
                            .background(accentColor.copy(alpha = 0.12f))
                            .border(1.dp, accentColor.copy(alpha = 0.35f), AppDesignTokens.ShapePill)
                            .clickable { messageTemplate += " $tag" }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tag, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Save Action
            glassButton(
                text = "Create & Activate Campaign",
                onClick = {
                    val finalTitle = title.trim().ifBlank {
                        when (selectedChannel) {
                            "WHATSAPP_BUSINESS" -> "Daily WhatsApp Business Campaign"
                            "WHATSAPP" -> "Daily WhatsApp Campaign"
                            else -> "Daily SMS Campaign"
                        }
                    }
                    val limit = dailyLimitText.toIntOrNull()?.coerceIn(1, 1000) ?: 30
                    val interval = intervalSecondsText.toIntOrNull()?.coerceIn(2, 120) ?: 10
                    val newCfg = DailyCampaignConfig(
                        channel = selectedChannel,
                        title = finalTitle,
                        messageTemplate = messageTemplate.trim(),
                        dailyLimit = limit,
                        intervalSeconds = interval,
                        scheduledHour = scheduledHour,
                        scheduledMinute = scheduledMinute,
                        simSlot = simSlot,
                        autoScanEnabled = true,
                        isActive = true
                    )
                    onSave(newCfg)
                },
                variant = GlassButtonVariant.PRIMARY,
                emoji = "🚀",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Dialog enabling user to pick between SMS, WhatsApp, and WhatsApp Business channels for a new campaign.
 *
 * @param onDismiss Callback to dismiss channel selection dialog.
 * @param onSelectChannel Callback receiving channel key identifier (SMS, WHATSAPP, WHATSAPP_BUSINESS).
 */
@Composable
fun dailyCampaignChannelSelectionDialog(
    onDismiss: () -> Unit,
    onSelectChannel: (String) -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = AppDesignTokens.ShapeLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .systemBarsPadding()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Choose Daily Auto Channel",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Select communication channel for daily outreach",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 1. SMS
                dailyChannelOptionCard(
                    title = "SMS Daily Campaign",
                    badge = "Carrier Direct",
                    description = "Cellular SMS outreach via SIM card with dual SIM and India daily quota protection (100 SMS/day)",
                    emoji = "📩",
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onSelectChannel("SMS") }
                )

                // 2. WhatsApp
                dailyChannelOptionCard(
                    title = "WhatsApp Daily Campaign",
                    badge = "Personal 1-on-1",
                    description = "Automate 1-on-1 personal messages to unique contacts with anti-ban delay pacing",
                    emoji = "💬",
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = { onSelectChannel("WHATSAPP") }
                )

                // 3. WhatsApp Business
                dailyChannelOptionCard(
                    title = "WhatsApp Business (W4B) Campaign",
                    badge = "Client Outreach",
                    description = "Client follow-ups via WhatsApp Business app (com.whatsapp.w4b) with paced delivery safeguards",
                    emoji = "💼",
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onSelectChannel("WHATSAPP_BUSINESS") }
                )
            }
        }
    }
}

@Composable
private fun dailyChannelOptionCard(
    title: String,
    badge: String,
    description: String,
    emoji: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = AppDesignTokens.ShapeMedium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
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
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape),
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
                        color = accentColor.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
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
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

