/**
 * Package: com.jmd.superwa.ui.screen.tasks
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [TaskCardComponents].
 *
 * What it does:
 * Interactive presentation card displaying an individual scheduled task with channel badge, schedule timestamp,
 * recipient summary, recurrence pill, and action buttons in a comfortable, readable 2-tier structure.
 *
 * Linked With:
 * - Models & Entities: [com.jmd.superwa.data.model.ChannelType], [com.jmd.superwa.data.model.RecurrenceType], [com.jmd.superwa.data.model.ScheduledTask], [com.jmd.superwa.data.model.TaskStatus]
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.platform.LocalConfiguration], [androidx.compose.ui.text.font.FontWeight]
 * - Core Security & Utilities: [java.util.Date]
 * - Android Framework & Jetpack: [androidx.compose.foundation.BorderStroke], [androidx.compose.material.icons.Icons], [androidx.compose.material.icons.outlined.Delete], [androidx.compose.runtime.Composable]
 */

package com.jmd.superwa.ui.screen.tasks

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.superwa.data.model.ChannelType
import com.jmd.superwa.data.model.RecurrenceType
import com.jmd.superwa.data.model.ScheduledTask
import com.jmd.superwa.data.model.TaskStatus
import com.jmd.superwa.ui.components.AppCard
import com.jmd.superwa.ui.theme.AppDesignTokens
import com.jmd.superwa.ui.theme.MaterialIconMapper
import com.jmd.superwa.ui.theme.isAppDarkTheme
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Comfortable animated toggle switch designed for accessible touch targets.
 * Provides smooth animation with zero layout clipping.
 */
@Composable
private fun ComfortableSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = updateTransition(checked, label = "switchAnim")
    val thumbOffset by transition.animateDp(label = "thumbOffset") { isChecked ->
        if (isChecked) 17.dp else 2.dp
    }
    val trackColor by transition.animateColor(label = "trackColor") { isChecked ->
        if (isChecked) activeColor else MaterialTheme.colorScheme.surfaceContainerHighest
    }

    Box(
        modifier = modifier
            .size(width = 36.dp, height = 21.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(thumbOffset.roundToPx(), 0) }
                .size(17.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * Comfortable circular action button for single-action icon controls.
 */
@Composable
private fun ComfortableIconButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
    }
}

/**
 * Comfortable pill action button with 1-word label.
 */
@Composable
private fun ComfortableActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = AppDesignTokens.ShapePill,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(0.75.dp, color.copy(alpha = 0.35f)),
        modifier = modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

/**
 * What it is:
 * Interactive presentation card displaying an individual scheduled task with channel badge, schedule timestamp,
 * recipient summary, recurrence pill, and action buttons.
 *
 * What it does:
 * Renders a comfortable, highly readable, structured card layout with optimal typography and touch targets.
 *
 * Linked with:
 * - Architecture: [TaskCardComponents], [TaskCard]
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
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val currentLocale = LocalConfiguration.current.locales[0]
    val channelColor = MaterialIconMapper.channelColor(task.channel)
    val channelIcon = MaterialIconMapper.channelIcon(task.channel)
    val statusColor = MaterialIconMapper.statusColor(task.status)
    val isDark = isAppDarkTheme()
    val successColor = if (isDark) Color(0xFF22C55E) else Color(0xFF16A34A)

    val channelDisplayName = when (task.channel) {
        ChannelType.WHATSAPP_BUSINESS,
        ChannelType.WHATSAPP_BUSINESS_GROUP,
        ChannelType.WHATSAPP_BUSINESS_STATUS,
        ChannelType.WHATSAPP_BUSINESS_CALL -> "WA Biz"
        ChannelType.WHATSAPP,
        ChannelType.WHATSAPP_GROUP,
        ChannelType.WHATSAPP_STATUS,
        ChannelType.WHATSAPP_CALL -> "WhatsApp"
        ChannelType.SMS -> "SMS"
        ChannelType.CALL,
        ChannelType.CALL_SIMULATION -> "Call"
    }

    val dateFormatted = SimpleDateFormat("dd MMM • hh:mm a", currentLocale).format(Date(task.triggerTimeMillis))

    val recurrenceLabel = when (task.recurrence) {
        RecurrenceType.ONCE -> "Once"
        RecurrenceType.REPEAT_SECONDS -> "${if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 30}s"
        RecurrenceType.REPEAT_MINUTES -> "${if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 15}m"
        RecurrenceType.REPEAT_HOURS -> "${if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 1}h"
        RecurrenceType.REPEAT_DAYS -> "Daily"
        RecurrenceType.REPEAT_WEEKS -> "Weekly"
        RecurrenceType.REPEAT_MONTHS -> "Monthly"
        RecurrenceType.CUSTOM_INTERVAL -> "${if (task.customIntervalMinutes > 0) task.customIntervalMinutes else 60}m"
        else -> task.recurrence.displayName
    }

    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeMedium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        onClick = onEdit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Channel Badge + Title + Subtitle | Status Dot + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Branded channel icon box
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(channelColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = channelIcon,
                        contentDescription = null,
                        tint = channelColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title and Timestamp Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = task.title.ifBlank { channelDisplayName },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$channelDisplayName • $dateFormatted",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Dot & Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    ComfortableSwitch(
                        checked = task.status.isPendingOrScheduled || task.status == TaskStatus.PENDING_CONFIRMATION,
                        onCheckedChange = onToggle,
                        activeColor = channelColor
                    )
                }
            }

            // Row 2: Target / Recipient details + Recurrence pill | Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Recipient info & Recurrence pill
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (task.recipients.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        val recText = if (task.recipients.size > 1) {
                            "${task.recipients.first()} +${task.recipients.size - 1}"
                        } else {
                            task.recipients.first()
                        }
                        Text(
                            text = recText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else if (task.messageText.isNotEmpty()) {
                        Text(
                            text = task.messageText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    // Recurrence Pill
                    Surface(
                        shape = AppDesignTokens.ShapePill,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.height(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Repeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = recurrenceLabel,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (task.mediaUri.isNotEmpty()) {
                        val isVid = task.mediaUri.contains("mp4", ignoreCase = true) || task.mediaUri.contains("video", ignoreCase = true)
                        Icon(
                            imageVector = if (isVid) Icons.Filled.Videocam else Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Action Buttons (Send/Retry/Resolve + Delete)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    when (task.status) {
                        TaskStatus.SCHEDULED, TaskStatus.PENDING, TaskStatus.READY -> {
                            ComfortableActionButton(
                                icon = Icons.AutoMirrored.Filled.Send,
                                label = "Send",
                                color = successColor,
                                onClick = onRunNow
                            )
                        }
                        TaskStatus.PENDING_CONFIRMATION -> {
                            ComfortableActionButton(
                                icon = Icons.Filled.HourglassTop,
                                label = "Resolve",
                                color = MaterialTheme.colorScheme.tertiary,
                                onClick = onRunNow
                            )
                        }
                        TaskStatus.FAILED -> {
                            ComfortableActionButton(
                                icon = Icons.Filled.Refresh,
                                label = "Retry",
                                color = MaterialTheme.colorScheme.primary,
                                onClick = onRunNow
                            )
                        }
                        else -> {}
                    }

                    ComfortableIconButton(
                        icon = Icons.Outlined.Delete,
                        tint = MaterialTheme.colorScheme.error,
                        contentDescription = "Delete",
                        onClick = onDelete
                    )
                }
            }
        }
    }
}
