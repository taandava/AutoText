@file:Suppress("FunctionName")

package com.jmd.autotext.ui.screens.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun BentoTaskCard(
    task: ScheduledTask,
    onRunNow: () -> Unit,
    onToggle: (Boolean) -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    val currentLocale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val dateStr = SimpleDateFormat("EEE, dd MMM • hh:mm a", currentLocale).format(Date(task.triggerTimeMillis))

    val (channelColor, _) = when (task.channel) {
        ChannelType.SMS -> BentoGlassTokens.NeonCyan to Icons.AutoMirrored.Filled.Message
        ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_GROUP, ChannelType.WHATSAPP_STATUS -> BentoGlassTokens.VividEmerald to Icons.AutoMirrored.Filled.Send
        ChannelType.EMAIL -> BentoGlassTokens.ElectricIndigo to Icons.Default.Email
        ChannelType.CALL -> BentoGlassTokens.RadiantAmber to Icons.Default.Call
        ChannelType.FAKE_CALL -> BentoGlassTokens.CyberRose to Icons.AutoMirrored.Filled.PhoneCallback
        ChannelType.REMINDER -> BentoGlassTokens.RadiantAmber to Icons.Default.Alarm
        ChannelType.SOUND_MODE -> BentoGlassTokens.NeonCyan to Icons.AutoMirrored.Filled.VolumeUp
    }

    val (statusColor, statusDot) = when (task.status) {
        TaskStatus.PENDING -> BentoGlassTokens.NeonCyan to true
        TaskStatus.DONE -> BentoGlassTokens.VividEmerald to true
        TaskStatus.FAILED -> BentoGlassTokens.CyberRose to true
        TaskStatus.CANCELLED -> BentoGlassTokens.TextMuted to false
    }

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeMedium,
        backgroundColor = BentoGlassTokens.GlassSurfaceElevated,
        onClick = onEdit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Header Row: Channel Badge, Status Badge & Timestamp + Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BentoBadge(
                        text = task.channel.displayName,
                        color = channelColor,
                        showDot = false
                    )
                    BentoBadge(
                        text = task.status.displayName,
                        color = statusColor,
                        showDot = statusDot
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateStr,
                        fontSize = 12.5.sp,
                        color = BentoGlassTokens.TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CompactSwitch(
                        checked = task.status == TaskStatus.PENDING,
                        onCheckedChange = onToggle,
                        activeColor = channelColor
                    )
                }
            }

            // Task Title
            Text(
                text = task.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                color = BentoGlassTokens.TextPrimary
            )

            // Media attachment badge if exists
            if (task.mediaUri.isNotEmpty()) {
                val isVid = task.mediaUri.contains("mp4", ignoreCase = true) || task.mediaUri.contains("video", ignoreCase = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        if (isVid) Icons.Default.Videocam else Icons.Default.Image,
                        contentDescription = null,
                        tint = BentoGlassTokens.NeonCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isVid) "Video Media Attached" else "Photo Media Attached",
                        fontSize = 12.sp,
                        color = BentoGlassTokens.NeonCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Message Bubble if exists
            if (task.messageText.isNotEmpty()) {
                Surface(
                    shape = BentoGlassTokens.ShapeSmall,
                    color = BentoGlassTokens.GlassSurfaceDefault,
                    border = BorderStroke(1.dp, BentoGlassTokens.GlassBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = task.messageText,
                        fontSize = 13.5.sp,
                        color = BentoGlassTokens.TextSecondary,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        lineHeight = 17.5.sp
                    )
                }
            }

            // Target info: Alarm badge for REMINDER, or Recipients Pill for messaging channels
            if (task.channel == ChannelType.REMINDER) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = BentoGlassTokens.RadiantAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Local Device Alarm • Text-to-Speech",
                        fontSize = 12.sp,
                        color = BentoGlassTokens.RadiantAmber,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            } else if (task.recipients.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Contacts,
                        contentDescription = null,
                        tint = BentoGlassTokens.NeonCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = task.recipients.joinToString(", "),
                        fontSize = 13.sp,
                        color = BentoGlassTokens.NeonCyan,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // Footer: Recurrence & Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = BentoGlassTokens.TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = task.recurrence.displayName,
                        fontSize = 11.sp,
                        color = BentoGlassTokens.TextMuted
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.status == TaskStatus.PENDING) {
                        GlassButton(
                            text = "Send Now",
                            onClick = onRunNow,
                            variant = GlassButtonVariant.SUCCESS,
                            icon = Icons.Default.PlayArrow,
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                            shape = BentoGlassTokens.ShapePill
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = BentoGlassTokens.CyberRose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// Material 3 Canonical Task Card Alias
@Composable
fun TaskCard(
    task: ScheduledTask,
    onRunNow: () -> Unit,
    onToggle: (Boolean) -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) = BentoTaskCard(task, onRunNow, onToggle, onEdit, onDelete)

