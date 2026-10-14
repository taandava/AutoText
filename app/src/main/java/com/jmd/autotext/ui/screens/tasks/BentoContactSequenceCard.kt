@file:Suppress("FunctionName")

package com.jmd.autotext.ui.screens.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ContactSequence
import com.jmd.autotext.ui.components.BentoCard
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun BentoContactSequenceCard(
    sequence: ContactSequence,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        borderColor = if (sequence.isEnabled) BentoGlassTokens.NeonCyan.copy(alpha = 0.5f) else BentoGlassTokens.GlassBorderSubtle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = BentoGlassTokens.NeonCyan.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, BentoGlassTokens.NeonCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = BentoGlassTokens.NeonCyan, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        sequence.contactName.ifBlank { sequence.contactPhone },
                        fontWeight = FontWeight.Bold,
                        color = BentoGlassTokens.TextPrimary,
                        fontSize = 15.sp
                    )
                    Text(
                        sequence.contactPhone,
                        color = BentoGlassTokens.TextMuted,
                        fontSize = 12.5.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = sequence.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BentoGlassTokens.NeonCyan,
                        uncheckedThumbColor = BentoGlassTokens.TextMuted,
                        uncheckedTrackColor = BentoGlassTokens.GlassSurfaceElevated
                    )
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = BentoGlassTokens.CyberRose, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (sequence.title.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(sequence.title, color = BentoGlassTokens.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Steps timeline flow preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sequence.steps.take(4).forEachIndexed { idx, step ->
                val chColor = when (step.channel) {
                    ChannelType.WHATSAPP -> BentoGlassTokens.VividEmerald
                    ChannelType.WHATSAPP_BUSINESS -> BentoGlassTokens.NeonCyan
                    else -> BentoGlassTokens.ElectricIndigo
                }
                val delayText = when {
                    idx == 0 -> "Start"
                    step.delayMinutesFromPrevious < 60 -> "+${step.delayMinutesFromPrevious}m"
                    step.delayMinutesFromPrevious < 1440 -> "+${step.delayMinutesFromPrevious / 60}h"
                    else -> "+${step.delayMinutesFromPrevious / 1440}d"
                }

                Surface(
                    shape = BentoGlassTokens.ShapePill,
                    color = chColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, chColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        "#${idx + 1} ${step.channel.name.take(3)} ($delayText)",
                        color = chColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (idx < sequence.steps.size - 1 && idx < 3) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = BentoGlassTokens.TextMuted, modifier = Modifier.size(10.dp))
                }
            }
            if (sequence.steps.size > 4) {
                Text("+${sequence.steps.size - 4} more", color = BentoGlassTokens.TextMuted, fontSize = 11.sp)
            }
        }
    }
}

// Material 3 Canonical Contact Sequence Card Alias
@Composable
fun ContactSequenceCard(
    sequence: ContactSequence,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) = BentoContactSequenceCard(sequence, onEdit, onToggle, onDelete)

