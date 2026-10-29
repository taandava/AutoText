package com.jmd.autotext.ui.screen.sequence

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.ContactSequence
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Overview card displaying an active or paused drip sequence with step counts and edit trigger.
 *
 * @param sequence Data model of contact communication sequence.
 * @param onEdit Callback when sequence card is clicked for editing.
 * @param onToggle Callback when enable/disable switch is flipped.
 * @param onDelete Callback when sequence is deleted.
 */
@Composable
fun contactSequenceCard(
    sequence: ContactSequence,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    appCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        borderColor = if (sequence.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (sequence.contactName.isNotBlank()) sequence.contactName.take(1).uppercase() else "👤",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = sequence.contactName.ifBlank { sequence.contactPhone },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (sequence.contactName.isNotBlank() && sequence.contactPhone.isNotBlank()) {
                        Text(
                            text = sequence.contactPhone,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = sequence.title.ifBlank { "Sequence #${sequence.id}" },
                        fontSize = 11.5.sp,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = sequence.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Sequence",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val stepChannel = sequence.steps.firstOrNull()?.channel ?: ChannelType.SMS
            val channelName = when (stepChannel) {
                ChannelType.WHATSAPP -> "WhatsApp"
                ChannelType.WHATSAPP_BUSINESS -> "WA Business"
                else -> "SMS"
            }
            val channelColor = when (stepChannel) {
                ChannelType.WHATSAPP -> MaterialTheme.colorScheme.tertiary
                ChannelType.WHATSAPP_BUSINESS -> Color(0xFF25D366)
                else -> MaterialTheme.colorScheme.primary
            }

            Surface(
                shape = CircleShape,
                color = channelColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, channelColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "$channelName Drip",
                    color = channelColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Text(
                text = "${sequence.steps.size} automated steps",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
