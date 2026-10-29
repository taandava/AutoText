/**
 * Package: com.jmd.autotext.ui.screen.tasks
 * Module: UI Scheduled Task Card Item
 *
 * Interactive presentation card displaying an individual scheduled task with channel badge,
 * schedule timestamp, recipient summary, recurrence pill, and action buttons (Run Now, Pause/Resume, Delete).
 */
package com.jmd.autotext.ui.screen.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date

import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.RecurrenceType
import com.jmd.autotext.data.model.ScheduledTask
import com.jmd.autotext.data.model.TaskStatus
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Interactive card displaying an individual scheduled task with channel badge, schedule timestamp,
 * recipient summary, recurrence pill, and action buttons (Run Now, Pause/Resume, Delete).
 *
 * @param task The [ScheduledTask] domain entity to render.
 * @param onRunNow Callback to trigger immediate manual dispatch.
 * @param onToggle Callback to activate or pause the task.
 * @param onEdit Callback to edit task parameters in composer.
 * @param onDelete Callback to remove the task from database and alarm scheduler.
 */
@Composable
fun taskCard(
    task: ScheduledTask,
    onRunNow: () -> Unit,
    onToggle: (Boolean) -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    val currentLocale = LocalLocale.current.platformLocale
    val dateStr = SimpleDateFormat("EEE, dd MMM • hh:mm a", currentLocale).format(Date(task.triggerTimeMillis))

    val (channelColor, channelEmoji) = when (task.channel) {
        ChannelType.SMS -> MaterialTheme.colorScheme.primary to "📩"
        ChannelType.WHATSAPP -> MaterialTheme.colorScheme.tertiary to "💬"
        ChannelType.WHATSAPP_BUSINESS -> MaterialTheme.colorScheme.secondary to "💼"
        ChannelType.WHATSAPP_GROUP -> MaterialTheme.colorScheme.tertiary to "👥"
        ChannelType.WHATSAPP_STATUS -> MaterialTheme.colorScheme.primary to "📸"
        ChannelType.WHATSAPP_BUSINESS_STATUS -> MaterialTheme.colorScheme.primary to "📸💼"
        ChannelType.CALL -> MaterialTheme.colorScheme.primary to "📞"
        ChannelType.WHATSAPP_CALL -> MaterialTheme.colorScheme.tertiary to "📞"
        ChannelType.WHATSAPP_BUSINESS_CALL -> MaterialTheme.colorScheme.secondary to "📞💼"
        ChannelType.FAKE_CALL -> MaterialTheme.colorScheme.error to "🎭"
        ChannelType.REMINDER -> MaterialTheme.colorScheme.primary to "⏰"
        ChannelType.SOUND_MODE -> MaterialTheme.colorScheme.secondary to "🔊"
    }

    val (statusColor, statusEmoji) = when (task.status) {
        TaskStatus.PENDING -> MaterialTheme.colorScheme.primary to "⏱️"
        TaskStatus.PENDING_CONFIRMATION -> MaterialTheme.colorScheme.secondary to "⏳"
        TaskStatus.DONE -> MaterialTheme.colorScheme.tertiary to "✅"
        TaskStatus.FAILED -> MaterialTheme.colorScheme.error to "❌"
        TaskStatus.CANCELLED -> MaterialTheme.colorScheme.outline to "⏸️"
    }

    appCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge,
        onClick = onEdit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header Row: Channel Badge, Status Badge & Timestamp + Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    appBadge(
                        text = task.channel.displayName,
                        emoji = channelEmoji,
                        color = channelColor,
                        showDot = false
                    )
                    appBadge(
                        text = task.status.displayName,
                        emoji = statusEmoji,
                        color = statusColor,
                        showDot = false
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateStr,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    compactSwitch(
                        checked = task.status == TaskStatus.PENDING || task.status == TaskStatus.PENDING_CONFIRMATION,
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
                color = MaterialTheme.colorScheme.onSurface
            )

            // Media attachment badge if exists
            if (task.mediaUri.isNotEmpty()) {
                val isVid = task.mediaUri.contains("mp4", ignoreCase = true) || task.mediaUri.contains("video", ignoreCase = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(if (isVid) "🎥" else "📸", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        if (isVid) "Video Media Attached" else "Photo Media Attached",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Message Bubble if exists
            if (task.messageText.isNotEmpty()) {
                Surface(
                    shape = AppDesignTokens.ShapeSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = task.messageText,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        lineHeight = 17.5.sp
                    )
                }
            } else if (task.mediaUri.isNotEmpty()) {
                Surface(
                    shape = AppDesignTokens.ShapeSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Media Message (No Caption)",
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Target info: Alarm badge for REMINDER, or Recipients Pill for messaging channels
            if (task.channel == ChannelType.REMINDER) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text("⏰", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Local Device Alarm • Text-to-Speech",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            } else if (task.recipients.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text("👤", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = task.recipients.joinToString(", "),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
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
                    Text("🔁", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    val recurrenceLabel = when (task.recurrence) {
                        RecurrenceType.ONCE -> "Once"
                        RecurrenceType.REPEAT_SECONDS -> "Every ${if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 30} sec"
                        RecurrenceType.REPEAT_MINUTES -> "Every ${if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 15} min"
                        RecurrenceType.REPEAT_HOURS -> "Every ${if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 1} hr"
                        RecurrenceType.REPEAT_DAYS -> "Every ${if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 1} day"
                        RecurrenceType.REPEAT_WEEKS -> "Every ${if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 1} week"
                        RecurrenceType.REPEAT_MONTHS -> "Every ${if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 1} mo"
                        RecurrenceType.CUSTOM_INTERVAL -> "Every ${if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 60} min"
                        else -> task.recurrence.displayName
                    }
                    Text(
                        text = recurrenceLabel,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (task.status) {
                        TaskStatus.PENDING -> {
                            glassButton(
                                text = "Send Now",
                                onClick = onRunNow,
                                variant = GlassButtonVariant.SUCCESS,
                                emoji = "🚀",
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                shape = AppDesignTokens.ShapePill
                            )
                        }
                        TaskStatus.PENDING_CONFIRMATION -> {
                            glassButton(
                                text = "Resolve",
                                onClick = onRunNow,
                                variant = GlassButtonVariant.SECONDARY,
                                emoji = "⏳",
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                shape = AppDesignTokens.ShapePill
                            )
                        }
                        TaskStatus.FAILED -> {
                            glassButton(
                                text = "Retry",
                                onClick = onRunNow,
                                variant = GlassButtonVariant.PRIMARY,
                                emoji = "🔄",
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                shape = AppDesignTokens.ShapePill
                            )
                        }
                        else -> {}
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("🗑️", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
