package com.jmd.autotext.ui.screens.daily

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScheduleSend
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.DailyCampaignConfig
import com.jmd.autotext.model.DynamicPoolStats
import com.jmd.autotext.model.IngestionSummary
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun DailyCampaignBannerCard(
    isWhatsApp: Boolean,
    config: DailyCampaignConfig,
    stats: DynamicPoolStats,
    isA11yActive: Boolean,
    isScanning: Boolean,
    campaignThemeColor: Color,
    lastScanSummary: IngestionSummary?,
    onToggleActive: (Boolean) -> Unit,
    onAutoHeal: () -> Unit,
    onScan: () -> Unit,
    onSendBatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCardElevated(
        modifier = modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeMedium,
        accentGlow = campaignThemeColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isWhatsApp) Icons.Default.PhoneAndroid else Icons.AutoMirrored.Filled.ScheduleSend,
                    contentDescription = null,
                    tint = campaignThemeColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        if (isWhatsApp) "Daily WhatsApp Campaign" else "Daily Contact SMS",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoGlassTokens.TextPrimary
                    )
                    Text(
                        if (config.isActive) "Active • Sends ${config.dailyLimit}/day at %02d:%02d".format(config.scheduledHour, config.scheduledMinute)
                        else "${if (isWhatsApp) "WhatsApp" else "SMS"} Automation Paused",
                        fontSize = 10.5.sp,
                        color = if (config.isActive) campaignThemeColor else BentoGlassTokens.TextMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = config.isActive,
                    onCheckedChange = onToggleActive,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = campaignThemeColor,
                        uncheckedThumbColor = BentoGlassTokens.TextMuted,
                        uncheckedTrackColor = BentoGlassTokens.GlassSurfaceElevated
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BentoStatTile(
                modifier = Modifier.weight(1f),
                title = "Total Pool",
                value = "${stats.totalCount}",
                icon = Icons.Default.People,
                iconTint = BentoGlassTokens.NeonCyan
            )
            BentoStatTile(
                modifier = Modifier.weight(1f),
                title = "Pending",
                value = "${stats.unsentCount}",
                icon = Icons.Default.HourglassTop,
                iconTint = BentoGlassTokens.RadiantAmber
            )
            BentoStatTile(
                modifier = Modifier.weight(1f),
                title = "Contacted",
                value = "${stats.sentCount}",
                icon = Icons.Default.CheckCircle,
                iconTint = BentoGlassTokens.VividEmerald
            )
        }

        // WhatsApp Accessibility Health & Readiness
        if (isWhatsApp) {
            Spacer(modifier = Modifier.height(6.dp))
            WhatsAppA11yHealthBar(
                isA11yActive = isA11yActive,
                onAutoHeal = onAutoHeal
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons: Scan Contacts, Edit Campaign & Send Now
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GlassButton(
                text = if (isScanning) "Scanning..." else "Scan",
                onClick = onScan,
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Default.Sync,
                isLoading = isScanning,
                modifier = Modifier.weight(1f)
            )

            GlassButton(
                text = "Send Batch",
                onClick = onSendBatch,
                variant = GlassButtonVariant.PRIMARY,
                icon = Icons.AutoMirrored.Filled.Send,
                modifier = Modifier.weight(1.3f)
            )
        }

        // Last scan summary indicator
        lastScanSummary?.let { summary ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Last scan: ${summary.contactsScanned} contacts, ${summary.smsScanned} SMS, ${summary.callLogsScanned} calls • +${summary.newAdded} new unique numbers added.",
                fontSize = 10.sp,
                color = BentoGlassTokens.NeonCyan
            )
        }
    }
}
