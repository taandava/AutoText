/**
 * Package: com.jmd.supersms.ui.screen
 * Module: Application Core Subsystem
 *
 * What it is:
 * Modal task picker displaying categorized task types for SuperSMS:
 * SMS Message, Phone Call, Call Simulation, and Daily Campaign.
 */

package com.jmd.supersms.ui.screen

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
import com.jmd.supersms.data.model.ChannelType
import com.jmd.supersms.ui.model.TaskTypeItem
import com.jmd.supersms.ui.theme.AppDesignTokens

/**
 * Modal selection dialog providing categorized creation options for SuperSMS task types.
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
    val taskTypes = listOf(
        TaskTypeItem(
            title = "SMS Message",
            description = "Schedule direct SMS via device SIM card",
            icon = Icons.AutoMirrored.Filled.Message,
            color = MaterialTheme.colorScheme.primary,
            channel = ChannelType.SMS
        ),
        TaskTypeItem(
            title = "Phone Call",
            description = "Schedule direct phone call via device SIM card",
            icon = Icons.Filled.Phone,
            color = Color(0xFF10B981),
            channel = ChannelType.CALL
        ),
        TaskTypeItem(
            title = "Call Simulation",
            description = "Simulate incoming call alert with custom caller name",
            icon = Icons.AutoMirrored.Filled.PhoneForwarded,
            color = Color(0xFFD97706),
            channel = ChannelType.CALL_SIMULATION
        ),
        TaskTypeItem(
            title = "Daily Campaign",
            description = "Recurrent daily outreach batch with multi-SIM rotation",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "New Task",
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
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(taskTypes.size) { index ->
                        val item = taskTypes[index]
                        TaskTypeRow(
                            item = item,
                            onClick = {
                                if (item.isDailyCampaign) {
                                    onDismiss()
                                    onSelectDailyCampaign()
                                } else if (item.channel != null) {
                                    onDismiss()
                                    onSelectChannel(item.channel)
                                }
                            }
                        )
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
                        text = item.emoji.ifBlank { "📱" },
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
