package com.jmd.autotext.ui.screens.autoreply

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.AutoReplyRule
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.ui.components.BentoBadge
import com.jmd.autotext.ui.components.BentoCard
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun BentoAutoReplyCard(
    rule: AutoReplyRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeLarge,
        backgroundColor = BentoGlassTokens.GlassSurfaceElevated,
        onClick = onEdit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Header: Platform Badge, Rule Name, Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    BentoBadge(
                        text = if (rule.channel == ChannelType.CALL) {
                            when (rule.callEventType.uppercase()) {
                                "MISSED_CALL" -> "Missed Call"
                                "CALL_ENDED" -> "Call Ended"
                                "CALL_REJECTED" -> "Call Rejected"
                                else -> "Missed & Ended"
                            }
                        } else rule.channel.displayName,
                        color = BentoGlassTokens.VividEmerald,
                        showDot = false
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = rule.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = BentoGlassTokens.TextPrimary,
                        maxLines = 1
                    )
                }

                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BentoGlassTokens.VividEmerald,
                        uncheckedThumbColor = BentoGlassTokens.TextMuted,
                        uncheckedTrackColor = BentoGlassTokens.GlassSurfaceDefault
                    )
                )
            }

            // Badges row: Mobile Only, Daily Limit, Cooldown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BentoBadge(text = "Mobile Only", color = BentoGlassTokens.NeonCyan, showDot = false)
                BentoBadge(
                    text = if (rule.maxDailyReplies <= 0) "Unlimited" else "Max ${rule.maxDailyReplies}x/day",
                    color = BentoGlassTokens.RadiantAmber,
                    showDot = false
                )
                Text(
                    text = "${rule.cooldownMinutes}m cooldown",
                    fontSize = 12.5.sp,
                    color = BentoGlassTokens.TextMuted
                )
            }

            // Message Snippet
            Surface(
                shape = BentoGlassTokens.ShapeSmall,
                color = BentoGlassTokens.GlassSurfaceDefault,
                border = BorderStroke(1.dp, BentoGlassTokens.GlassBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = rule.replyText,
                    fontSize = 13.5.sp,
                    color = BentoGlassTokens.TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    lineHeight = 17.5.sp
                )
            }

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (rule.channel == ChannelType.CALL) "Auto-replies via SMS to mobile numbers"
                    else "Trigger: ${if (rule.triggerPattern == "*") "All Incoming" else "\"${rule.triggerPattern}\""}",
                    fontSize = 12.sp,
                    color = BentoGlassTokens.TextMuted
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = BentoGlassTokens.CyberRose, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
