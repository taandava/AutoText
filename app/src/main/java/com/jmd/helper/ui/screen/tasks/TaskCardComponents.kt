/**
 * Package: com.jmd.helper.ui.screen.tasks
 * Module: UI Scheduled Task Card Item
 *
 * Interactive presentation card displaying an individual scheduled task with channel badge,
 * schedule timestamp, recipient summary, recurrence pill, and action buttons (Run Now, Pause/Resume, Delete).
 */
package com.jmd.helper.ui.screen.tasks

import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
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

import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.RecurrenceType
import com.jmd.helper.data.model.ScheduledTask
import com.jmd.helper.data.model.TaskStatus
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.theme.AppDesignTokens
import com.jmd.helper.ui.theme.MaterialIconMapper

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
fun TaskCard(
    task: ScheduledTask,
    onRunNow: () -> Unit,
    onToggle: (Boolean) -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    val currentLocale = LocalLocale.current.platformLocale
    val dateStr = SimpleDateFormat("EEE, dd MMM • hh:mm a", currentLocale).format(Date(task.triggerTimeMillis))

    val channelColor = MaterialIconMapper.channelColor(task.channel)
    val channelIcon = MaterialIconMapper.channelIcon(task.channel)
    val statusColor = MaterialIconMapper.statusColor(task.status)
    val statusIcon = MaterialIconMapper.statusIcon(task.status)

    AppCard(
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
                    AppBadge(
                        text = task.channel.displayName,
                        icon = channelIcon,
                        color = channelColor,
                        showDot = false
                    )
                    AppBadge(
                        text = task.status.displayName,
                        icon = statusIcon,
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
                    CompactSwitch(
                        checked = task.status.isPendingOrScheduled || task.status == TaskStatus.PENDING_CONFIRMATION,
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
                    Icon(
                        imageVector = if (isVid) Icons.Filled.Videocam else Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
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
                    Icon(
                        imageVector = Icons.Filled.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
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
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
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
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
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
                        TaskStatus.SCHEDULED, TaskStatus.PENDING, TaskStatus.READY -> {
                            GlassButton(
                                text = "Send Now",
                                onClick = onRunNow,
                                variant = GlassButtonVariant.SUCCESS,
                                icon = Icons.AutoMirrored.Filled.Send,
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                shape = AppDesignTokens.ShapePill
                            )
                        }
                        TaskStatus.PENDING_CONFIRMATION -> {
                            GlassButton(
                                text = "Resolve",
                                onClick = onRunNow,
                                variant = GlassButtonVariant.SECONDARY,
                                icon = Icons.Filled.HourglassTop,
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                shape = AppDesignTokens.ShapePill
                            )
                        }
                        TaskStatus.FAILED -> {
                            GlassButton(
                                text = "Retry",
                                onClick = onRunNow,
                                variant = GlassButtonVariant.PRIMARY,
                                icon = Icons.Filled.Refresh,
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
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
