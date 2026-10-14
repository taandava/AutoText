@file:Suppress("FunctionName")

package com.jmd.autotext.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.DailyCampaignConfig
import com.jmd.autotext.model.DynamicPoolStats
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import com.jmd.autotext.util.SmsQuotaManager

@Composable
fun DashboardSmsCampaignCard(
    smsDailyConfig: DailyCampaignConfig,
    smsQuota: SmsQuotaManager.QuotaStatus,
    onNavigateToDailyCampaign: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onSendBatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = BentoGlassTokens.GlassSurfaceDefault,
        borderColor = if (smsDailyConfig.isActive) BentoGlassTokens.NeonCyan.copy(alpha = 0.35f) else BentoGlassTokens.GlassBorderSubtle,
        onClick = onNavigateToDailyCampaign
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Default.Sms,
                    contentDescription = null,
                    tint = BentoGlassTokens.NeonCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Daily SMS Campaign",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = BentoGlassTokens.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        if (smsDailyConfig.isActive) {
                            BentoBadge(text = "LIVE", color = BentoGlassTokens.NeonCyan)
                        }
                    }
                    Text(
                        if (smsDailyConfig.isActive)
                            "${smsDailyConfig.dailyLimit} SMS/day • %02d:%02d".format(smsDailyConfig.scheduledHour, smsDailyConfig.scheduledMinute)
                        else "SMS Automation Paused",
                        fontSize = 12.5.sp,
                        color = if (smsDailyConfig.isActive) BentoGlassTokens.NeonCyan else BentoGlassTokens.TextMuted
                    )
                }
            }

            Switch(
                checked = smsDailyConfig.isActive,
                onCheckedChange = onToggleActive,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = BentoGlassTokens.NeonCyan,
                    uncheckedThumbColor = BentoGlassTokens.TextMuted,
                    uncheckedTrackColor = BentoGlassTokens.GlassSurfaceSubtle
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Message preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(BentoGlassTokens.ShapeXSmall)
                .background(BentoGlassTokens.GlassSurfaceSubtle)
                .border(1.dp, BentoGlassTokens.GlassBorderHairline, BentoGlassTokens.ShapeXSmall)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = "\"${smsDailyConfig.messageTemplate.take(70)}\"",
                fontSize = 12.5.sp,
                color = BentoGlassTokens.TextSecondary,
                maxLines = 2
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Regulatory TRAI Meter
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "App Safeguard Cap (100 SMS)",
                    fontSize = 11.5.sp,
                    color = BentoGlassTokens.TextMuted,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (smsQuota.isLimitReached) "Limit Reached (${smsQuota.totalSentToday}/100)"
                    else "${smsQuota.totalSentToday} / 100 sent (${smsQuota.remaining} left)",
                    fontSize = 11.5.sp,
                    color = if (smsQuota.isLimitReached) BentoGlassTokens.CyberRose else BentoGlassTokens.NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { (smsQuota.totalSentToday / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = if (smsQuota.isLimitReached) BentoGlassTokens.CyberRose else BentoGlassTokens.NeonCyan,
                trackColor = Color(0x33334155),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Action: Send Batch Button
        GlassButton(
            text = "Send Batch",
            icon = Icons.Default.PlayArrow,
            variant = GlassButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            onClick = onSendBatch
        )
    }
}

@Composable
fun DashboardWhatsAppCampaignCard(
    waDailyConfig: DailyCampaignConfig,
    waPoolStats: DynamicPoolStats,
    isA11yActive: Boolean,
    onNavigateToDailyCampaign: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onSendBatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = BentoGlassTokens.GlassSurfaceDefault,
        borderColor = if (waDailyConfig.isActive) BentoGlassTokens.VividEmerald.copy(alpha = 0.35f) else BentoGlassTokens.GlassBorderSubtle,
        onClick = onNavigateToDailyCampaign
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = BentoGlassTokens.VividEmerald,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Daily WhatsApp Campaign",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = BentoGlassTokens.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        if (waDailyConfig.isActive) {
                            BentoBadge(text = "LIVE", color = BentoGlassTokens.VividEmerald)
                        }
                    }
                    Text(
                        if (waDailyConfig.isActive)
                            "${waDailyConfig.dailyLimit} WA/day • %02d:%02d".format(waDailyConfig.scheduledHour, waDailyConfig.scheduledMinute)
                        else "WhatsApp Automation Paused",
                        fontSize = 12.5.sp,
                        color = if (waDailyConfig.isActive) BentoGlassTokens.VividEmerald else BentoGlassTokens.TextMuted
                    )
                }
            }

            Switch(
                checked = waDailyConfig.isActive,
                onCheckedChange = onToggleActive,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = BentoGlassTokens.VividEmerald,
                    uncheckedThumbColor = BentoGlassTokens.TextMuted,
                    uncheckedTrackColor = BentoGlassTokens.GlassSurfaceSubtle
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Message preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(BentoGlassTokens.ShapeXSmall)
                .background(BentoGlassTokens.GlassSurfaceSubtle)
                .border(1.dp, BentoGlassTokens.GlassBorderHairline, BentoGlassTokens.ShapeXSmall)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = "\"${waDailyConfig.messageTemplate.take(70)}\"",
                fontSize = 12.5.sp,
                color = BentoGlassTokens.TextSecondary,
                maxLines = 2
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Service & Pool Status Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BentoBadge(
                text = if (isA11yActive) "A11y Active" else "A11y Setup Needed",
                color = if (isA11yActive) BentoGlassTokens.VividEmerald else BentoGlassTokens.RadiantAmber
            )

            Text(
                "Contacted: ${waPoolStats.sentCount} / Pool: ${waPoolStats.totalCount}",
                fontSize = 12.sp,
                color = BentoGlassTokens.TextMuted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Action: Send Batch Button
        GlassButton(
            text = "Send Batch",
            icon = Icons.Default.PlayArrow,
            variant = GlassButtonVariant.SUCCESS,
            modifier = Modifier.fillMaxWidth(),
            onClick = onSendBatch
        )
    }
}
