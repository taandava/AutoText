/**
 * Package: com.jmd.supersms.ui.theme
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [MaterialIconMapper].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for MaterialIconMapper.
 *
 * Linked With:
 * - Models & Entities: [com.jmd.supersms.data.model.ChannelType], [com.jmd.supersms.data.model.TaskStatus]
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.clip], [androidx.compose.ui.graphics.Color]
 * - Android Framework & Jetpack: [androidx.compose.foundation.background], [androidx.compose.foundation.border], [androidx.compose.foundation.layout.Box], [androidx.compose.foundation.layout.size]
 */

package com.jmd.supersms.ui.theme

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
import com.jmd.supersms.data.model.ChannelType
import com.jmd.supersms.data.model.TaskStatus

/**
 * What it is:
 * Maps communication channels and task statuses to expressive, colorful Material 3 vector icons.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [MaterialIconMapper].
 *
 * Linked with:
 * - Architecture: [MaterialIconMapper], [MaterialIconMapper]
 */
object MaterialIconMapper {
    /**
     * What it is:
     * Execution routine for [channelIcon].
     *
     * What it does:
     * Coordinates channelIcon operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [MaterialIconMapper]
     */

    fun channelIcon(channel: ChannelType): ImageVector = when (channel) {
        ChannelType.SMS -> Icons.AutoMirrored.Filled.Message
        ChannelType.CALL -> Icons.Filled.Phone
        ChannelType.CALL_SIMULATION -> Icons.AutoMirrored.Filled.PhoneForwarded
    }
    /**
     * What it is:
     * Execution routine for [channelColor].
     *
     * What it does:
     * Coordinates channelColor operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [MaterialIconMapper]
     */

    fun channelColor(channel: ChannelType): Color = when (channel) {
        ChannelType.SMS -> Color(0xFF2563EB) // Vibrant Blue
        ChannelType.CALL -> Color(0xFF10B981) // Green Phone
        ChannelType.CALL_SIMULATION -> Color(0xFFD97706) // Amber Simulation
    }
    /**
     * What it is:
     * Execution routine for [statusIcon].
     *
     * What it does:
     * Coordinates statusIcon operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [MaterialIconMapper]
     */

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
    /**
     * What it is:
     * Execution routine for [statusColor].
     *
     * What it does:
     * Coordinates statusColor operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [MaterialIconMapper]
     */

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
 * What it is:
 * Renders a colorful Material 3 icon badge with tonal background container.
 *
 * What it does:
 * Renders interactive Material 3 UI for [MaterialIconBadge] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [MaterialIconMapper], [MaterialIconBadge]
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
