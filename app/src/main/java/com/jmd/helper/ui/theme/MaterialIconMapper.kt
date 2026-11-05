package com.jmd.helper.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.TaskStatus

/**
 * Maps communication channels and task statuses to expressive, colorful Material 3 vector icons.
 */
object MaterialIconMapper {

    fun channelIcon(channel: ChannelType): ImageVector = when (channel) {
        ChannelType.SMS -> Icons.AutoMirrored.Filled.Message
        ChannelType.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
        ChannelType.WHATSAPP_BUSINESS -> Icons.Filled.BusinessCenter
        ChannelType.WHATSAPP_GROUP -> Icons.Filled.Groups
        ChannelType.WHATSAPP_STATUS -> Icons.Filled.PhotoCamera
        ChannelType.WHATSAPP_BUSINESS_STATUS -> Icons.Filled.Camera
        ChannelType.CALL -> Icons.Filled.Phone
        ChannelType.WHATSAPP_CALL -> Icons.Filled.PhoneInTalk
        ChannelType.WHATSAPP_BUSINESS_CALL -> Icons.Filled.ContactPhone
        ChannelType.CALL_SIMULATION -> Icons.AutoMirrored.Filled.PhoneForwarded
        ChannelType.REMINDER -> Icons.Filled.Alarm
        ChannelType.SOUND_MODE -> Icons.AutoMirrored.Filled.VolumeUp
    }

    fun channelColor(channel: ChannelType): Color = when (channel) {
        ChannelType.SMS -> Color(0xFF2563EB) // Vibrant Blue
        ChannelType.WHATSAPP -> Color(0xFF16A34A) // WhatsApp Green
        ChannelType.WHATSAPP_BUSINESS -> Color(0xFF0D9488) // Business Teal
        ChannelType.WHATSAPP_GROUP -> Color(0xFF059669) // Emerald Group
        ChannelType.WHATSAPP_STATUS -> Color(0xFF7C3AED) // Vibrant Purple
        ChannelType.WHATSAPP_BUSINESS_STATUS -> Color(0xFF4F46E5) // Indigo
        ChannelType.CALL -> Color(0xFF10B981) // Green Phone
        ChannelType.WHATSAPP_CALL -> Color(0xFF06B6D4) // Cyan VoIP
        ChannelType.WHATSAPP_BUSINESS_CALL -> Color(0xFF0284C7) // Sky Blue
        ChannelType.CALL_SIMULATION -> Color(0xFFD97706) // Amber Simulation
        ChannelType.REMINDER -> Color(0xFFE11D48) // Rose Alarm
        ChannelType.SOUND_MODE -> Color(0xFFEA580C) // Orange Sound Mode
    }

    fun statusIcon(status: TaskStatus): ImageVector = when (status) {
        TaskStatus.DRAFT -> Icons.Filled.EditNote
        TaskStatus.VALIDATING -> Icons.Filled.Search
        TaskStatus.READY -> Icons.Filled.CheckCircleOutline
        TaskStatus.SCHEDULED, TaskStatus.PENDING -> Icons.Filled.Schedule
        TaskStatus.RUNNING -> Icons.Filled.Bolt
        TaskStatus.SUCCESS, TaskStatus.DONE -> Icons.Filled.CheckCircle
        TaskStatus.FAILED -> Icons.Filled.ErrorOutline
        TaskStatus.PAUSED, TaskStatus.CANCELLED -> Icons.Filled.PauseCircle
        TaskStatus.PENDING_CONFIRMATION -> Icons.Filled.HourglassTop
    }

    fun statusColor(status: TaskStatus): Color = when (status) {
        TaskStatus.DRAFT -> Color(0xFF6B7280) // Gray
        TaskStatus.VALIDATING -> Color(0xFF6366F1) // Indigo
        TaskStatus.READY -> Color(0xFF3B82F6) // Blue
        TaskStatus.SCHEDULED, TaskStatus.PENDING -> Color(0xFF0284C7) // Sky
        TaskStatus.RUNNING -> Color(0xFFF59E0B) // Amber
        TaskStatus.SUCCESS, TaskStatus.DONE -> Color(0xFF10B981) // Emerald Green
        TaskStatus.FAILED -> Color(0xFFEF4444) // Red Error
        TaskStatus.PAUSED, TaskStatus.CANCELLED -> Color(0xFF9CA3AF) // Muted Gray
        TaskStatus.PENDING_CONFIRMATION -> Color(0xFFF97316) // Orange
    }
}

/**
 * Renders a colorful Material 3 icon badge with tonal background container.
 */
@Composable
fun MaterialIconBadge(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(iconSize)
        )
    }
}

