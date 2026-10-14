package com.jmd.autotext.ui.screens.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import com.jmd.autotext.util.SmsQuotaManager

@Composable
fun DailySmsQuotaCard(
    smsQuota: SmsQuotaManager.QuotaStatus,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeMedium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = BentoGlassTokens.NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "TRAI India SMS Quota",
                    fontWeight = FontWeight.Bold,
                    color = BentoGlassTokens.TextPrimary,
                    fontSize = 12.5.sp
                )
            }

            BentoBadge(
                text = if (smsQuota.isLimitReached) "100/100 LIMIT REACHED" else "${smsQuota.totalSentToday} / 100 Sent",
                color = if (smsQuota.isLimitReached) BentoGlassTokens.CyberRose else BentoGlassTokens.NeonCyan
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (smsQuota.totalSentToday / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(BentoGlassTokens.ShapePill),
            color = if (smsQuota.isLimitReached) BentoGlassTokens.CyberRose else BentoGlassTokens.NeonCyan,
            trackColor = BentoGlassTokens.GlassBorderSubtle
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            smsQuota.details,
            fontSize = 10.sp,
            color = BentoGlassTokens.TextMuted,
            lineHeight = 13.5.sp
        )
    }
}

@Composable
fun WhatsAppA11yHealthBar(
    isA11yActive: Boolean,
    onAutoHeal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = BentoGlassTokens.ShapeSmall,
        color = if (isA11yActive) BentoGlassTokens.VividEmerald.copy(alpha = 0.12f) else BentoGlassTokens.RadiantAmber.copy(alpha = 0.15f),
        border = BorderStroke(
            1.dp,
            if (isA11yActive) BentoGlassTokens.VividEmerald.copy(alpha = 0.35f) else BentoGlassTokens.RadiantAmber.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isA11yActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isA11yActive) BentoGlassTokens.VividEmerald else BentoGlassTokens.RadiantAmber,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isA11yActive) "Automation Service Ready" else "Service dropped / inactive",
                    fontSize = 10.5.sp,
                    color = BentoGlassTokens.TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!isA11yActive) {
                GlassButton(
                    text = "Auto-Heal",
                    onClick = onAutoHeal,
                    variant = GlassButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = BentoGlassTokens.ShapePill
                )
            }
        }
    }
}
