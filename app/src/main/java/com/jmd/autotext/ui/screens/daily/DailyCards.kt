package com.jmd.autotext.ui.screens.daily

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.DynamicContactItem
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun BentoLedgerItemCard(
    contact: DynamicContactItem,
    isWhatsApp: Boolean,
    onRequeue: () -> Unit
) {
    val sentAt = if (isWhatsApp) contact.waLastSentAt else contact.lastSentAt
    val sendCount = if (isWhatsApp) contact.waSendCount else contact.sendCount
    val status = if (isWhatsApp) contact.waStatus else contact.smsStatus
    val currentLocale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val dateStr = if (sentAt > 0) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", currentLocale).format(Date(sentAt))
    } else "Dispatched"

    val statusColor = when (status.uppercase()) {
        "FAILED" -> BentoGlassTokens.CyberRose
        "QUEUED" -> BentoGlassTokens.RadiantAmber
        else -> BentoGlassTokens.VividEmerald
    }

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeMedium,
        backgroundColor = BentoGlassTokens.GlassSurfaceElevated
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BentoAvatar(
                name = contact.name.ifBlank { contact.phone },
                photoUri = contact.photoUri,
                size = 36.dp,
                badgeIcon = if (isWhatsApp) Icons.Default.PhoneAndroid else Icons.Default.Sms,
                badgeColor = if (isWhatsApp) BentoGlassTokens.VividEmerald else BentoGlassTokens.NeonCyan
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = contact.name.ifEmpty { "Unsaved Contact" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = BentoGlassTokens.TextPrimary,
                        maxLines = 1
                    )
                    BentoBadge(
                        text = status.ifEmpty { "SENT" },
                        color = statusColor,
                        showDot = true
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = contact.phone,
                        fontSize = 11.sp,
                        color = BentoGlassTokens.NeonCyan
                    )
                    if (sendCount > 1) {
                        Text(
                            text = "• ${sendCount}x sent",
                            fontSize = 10.sp,
                            color = BentoGlassTokens.TextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "Dispatched: $dateStr",
                    fontSize = 9.5.sp,
                    color = BentoGlassTokens.TextMuted
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            GlassButton(
                text = "Re-queue",
                onClick = onRequeue,
                variant = GlassButtonVariant.SECONDARY,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                shape = BentoGlassTokens.ShapePill
            )
        }
    }
}

@Composable
fun BentoContactItemCard(
    contact: DynamicContactItem,
    isWhatsApp: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeMedium,
        backgroundColor = BentoGlassTokens.GlassSurfaceElevated
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sourceIcon = when (contact.source) {
                "CONTACT" -> Icons.Default.Person
                "SMS" -> Icons.Default.ChatBubble
                else -> Icons.Default.Call
            }
            val sourceColor = when (contact.source) {
                "CONTACT" -> BentoGlassTokens.ElectricIndigo
                "SMS" -> BentoGlassTokens.VividEmerald
                else -> BentoGlassTokens.NeonCyan
            }

            BentoAvatar(
                name = contact.name.ifBlank { contact.phone },
                photoUri = contact.photoUri,
                size = 36.dp,
                badgeIcon = sourceIcon,
                badgeColor = sourceColor
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name.ifEmpty { "Unsaved Contact" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = BentoGlassTokens.TextPrimary
                )
                Text(
                    text = contact.phone,
                    fontSize = 11.5.sp,
                    color = BentoGlassTokens.NeonCyan
                )
                val isSent = if (isWhatsApp) contact.waLastSentAt > 0 else contact.lastSentAt > 0
                if (isSent) {
                    Text(
                        text = "Already contacted in cycle",
                        fontSize = 9.5.sp,
                        color = BentoGlassTokens.VividEmerald
                    )
                }
            }

            Switch(
                checked = contact.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BentoGlassTokens.VividEmerald,
                    uncheckedThumbColor = BentoGlassTokens.TextMuted,
                    uncheckedTrackColor = BentoGlassTokens.GlassSurfaceDefault
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = BentoGlassTokens.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
