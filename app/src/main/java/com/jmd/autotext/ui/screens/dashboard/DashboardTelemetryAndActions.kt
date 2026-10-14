@file:Suppress("FunctionName")

package com.jmd.autotext.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ScheduledTask
import com.jmd.autotext.ui.components.BentoBadge
import com.jmd.autotext.ui.components.BentoCard
import com.jmd.autotext.ui.theme.BentoGlassTokens
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun BentoStatItem(label: String, count: String, accentColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(count, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold, color = accentColor)
        Spacer(modifier = Modifier.height(1.dp))
        Text(label, fontSize = 11.5.sp, color = BentoGlassTokens.TextMuted, maxLines = 1)
    }
}

@Composable
fun BentoActionTile(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    BentoCard(
        modifier = modifier,
        shape = BentoGlassTokens.ShapeMedium,
        backgroundColor = BentoGlassTokens.GlassSurfaceElevated,
        elevation = BentoGlassTokens.ElevationLevel2,
        onClick = onClick
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                color = BentoGlassTokens.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DashboardTelemetryBar(
    pendingCount: Int,
    doneCount: Int,
    failedCount: Int,
    activeRepliesCount: Int,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeLarge,
        backgroundColor = BentoGlassTokens.GlassSurfaceSubtle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BentoStatItem("Pending", pendingCount.toString(), BentoGlassTokens.ElectricIndigo, Modifier.weight(1f))
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(BentoGlassTokens.GlassBorderSubtle))
            BentoStatItem("Sent", doneCount.toString(), BentoGlassTokens.VividEmerald, Modifier.weight(1f))
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(BentoGlassTokens.GlassBorderSubtle))
            BentoStatItem("Failed", failedCount.toString(), BentoGlassTokens.CyberRose, Modifier.weight(1f))
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(BentoGlassTokens.GlassBorderSubtle))
            BentoStatItem("Auto-Reply", activeRepliesCount.toString(), BentoGlassTokens.NeonCyan, Modifier.weight(1f))
        }
    }
}

@Composable
fun DashboardQuickActionsGrid(
    onNavigateToCompose: (ChannelType) -> Unit,
    onNavigateToSequences: () -> Unit,
    onNavigateToDailyCampaign: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val actionsRow1 = listOf(
        Triple("SMS", Icons.AutoMirrored.Filled.Message, BentoGlassTokens.ElectricIndigo) to { onNavigateToCompose(ChannelType.SMS) },
        Triple("WhatsApp", Icons.AutoMirrored.Filled.Send, BentoGlassTokens.VividEmerald) to { onNavigateToCompose(ChannelType.WHATSAPP) },
        Triple("WA Business", Icons.Default.Business, Color(0xFF14B8A6)) to { onNavigateToCompose(ChannelType.WHATSAPP_BUSINESS) },
        Triple("Sequences", Icons.AutoMirrored.Filled.AltRoute, BentoGlassTokens.RadiantAmber) to { onNavigateToSequences() }
    )
    val actionsRow2 = listOf(
        Triple("Call", Icons.Default.Call, BentoGlassTokens.RadiantAmber) to { onNavigateToCompose(ChannelType.CALL) },
        Triple("Fake Call", Icons.AutoMirrored.Filled.PhoneCallback, Color(0xFFEC4899)) to { onNavigateToCompose(ChannelType.FAKE_CALL) },
        Triple("Reminder", Icons.Default.Alarm, Color(0xFFA855F7)) to { onNavigateToCompose(ChannelType.REMINDER) },
        Triple("Daily Auto", Icons.Default.Schedule, BentoGlassTokens.NeonCyan) to { onNavigateToDailyCampaign(1L) }
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            actionsRow1.forEach { (actionInfo, clickAction) ->
                BentoActionTile(
                    title = actionInfo.first,
                    icon = actionInfo.second,
                    color = actionInfo.third,
                    modifier = Modifier.weight(1f),
                    onClick = clickAction
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            actionsRow2.forEach { (actionInfo, clickAction) ->
                BentoActionTile(
                    title = actionInfo.first,
                    icon = actionInfo.second,
                    color = actionInfo.third,
                    modifier = Modifier.weight(1f),
                    onClick = clickAction
                )
            }
        }
    }
}

@Composable
fun DashboardTaskSummaryCard(
    task: ScheduledTask,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {}
) {
    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", LocalLocale.current.platformLocale).format(Date(task.triggerTimeMillis))

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeMedium,
        backgroundColor = BentoGlassTokens.GlassSurfaceDefault,
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BentoBadge(
                text = task.channel.displayName,
                color = when (task.channel) {
                    ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS -> BentoGlassTokens.VividEmerald
                    ChannelType.CALL -> BentoGlassTokens.RadiantAmber
                    else -> BentoGlassTokens.ElectricIndigo
                }
            )

            Text(dateStr, fontSize = 13.sp, color = BentoGlassTokens.NeonCyan, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(task.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BentoGlassTokens.TextPrimary)

        if (task.messageText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(task.messageText, fontSize = 13.sp, color = BentoGlassTokens.TextMuted, maxLines = 2)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recipients: ${task.recipients.size} • Repeat: ${task.recurrence.displayName}",
                fontSize = 12.sp,
                color = BentoGlassTokens.TextDisabled
            )

            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = BentoGlassTokens.CyberRose, modifier = Modifier.size(15.dp))
            }
        }
    }
}
