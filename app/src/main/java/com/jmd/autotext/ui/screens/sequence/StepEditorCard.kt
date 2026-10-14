package com.jmd.autotext.ui.screens.sequence

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ContactSequenceStep
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("NonObservableLocale")
@Composable
fun StepEditorCard(
    stepNumber: Int,
    isFirst: Boolean,
    step: ContactSequenceStep,
    onStepChanged: (ContactSequenceStep) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val channelColor = when (step.channel) {
        ChannelType.WHATSAPP -> BentoGlassTokens.VividEmerald
        ChannelType.WHATSAPP_BUSINESS -> BentoGlassTokens.NeonCyan
        else -> BentoGlassTokens.ElectricIndigo
    }

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = channelColor.copy(alpha = 0.5f)
    ) {
        // Step Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = channelColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, channelColor),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$stepNumber", color = channelColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Step $stepNumber Message", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 13.5.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = BentoGlassTokens.TextMuted, modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = BentoGlassTokens.TextMuted, modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = BentoGlassTokens.CyberRose, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Channel Selector
        Text("Dispatch Channel:", fontSize = 11.sp, color = BentoGlassTokens.TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                Triple(ChannelType.SMS, "SMS", Icons.Default.Sms),
                Triple(ChannelType.WHATSAPP, "WhatsApp", Icons.AutoMirrored.Filled.Chat),
                Triple(ChannelType.WHATSAPP_BUSINESS, "WA Business", Icons.Default.Business)
            ).forEach { (ch, label, icon) ->
                val isSelected = step.channel == ch
                val btnColor = if (ch == ChannelType.SMS) BentoGlassTokens.ElectricIndigo else BentoGlassTokens.VividEmerald
                Surface(
                    shape = BentoGlassTokens.ShapePill,
                    color = if (isSelected) btnColor.copy(alpha = 0.25f) else BentoGlassTokens.GlassSurfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) btnColor else BentoGlassTokens.GlassBorderSubtle
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(BentoGlassTokens.ShapePill)
                        .clickable { onStepChanged(step.copy(channel = ch)) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = if (isSelected) btnColor else BentoGlassTokens.TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label, color = if (isSelected) btnColor else BentoGlassTokens.TextMuted, fontSize = 10.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }

        if (step.channel == ChannelType.SMS) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Carrier SIM: ", fontSize = 11.sp, color = BentoGlassTokens.TextMuted)
                Spacer(modifier = Modifier.width(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "SIM 1", 1 to "SIM 2").forEach { (sim, label) ->
                        val isSelected = step.simSlot == sim
                        Surface(
                            shape = BentoGlassTokens.ShapePill,
                            color = if (isSelected) BentoGlassTokens.ElectricIndigo.copy(alpha = 0.2f) else BentoGlassTokens.GlassSurfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) BentoGlassTokens.ElectricIndigo else BentoGlassTokens.GlassBorderSubtle
                            ),
                            modifier = Modifier
                                .clip(BentoGlassTokens.ShapePill)
                                .clickable { onStepChanged(step.copy(simSlot = sim)) }
                        ) {
                            Text(
                                label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BentoGlassTokens.ElectricIndigo else BentoGlassTokens.TextMuted,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Step Timing: Relative Interval or Exact Target Date/Time
        Spacer(modifier = Modifier.height(8.dp))
        val isExactTargetTime = step.specificTriggerTime > 0L

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Step Dispatch Timing:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = BentoGlassTokens.TextPrimary)
            val timingBadgeText = remember(step.specificTriggerTime, step.delayMinutesFromPrevious, isFirst) {
                if (step.specificTriggerTime > 0L) {
                    SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault()).format(Date(step.specificTriggerTime))
                } else if (isFirst && step.delayMinutesFromPrevious == 0) {
                    "Immediate upon start"
                } else if (step.delayMinutesFromPrevious < 60) {
                    "+${step.delayMinutesFromPrevious}m after prev"
                } else if (step.delayMinutesFromPrevious < 1440) {
                    "+${step.delayMinutesFromPrevious / 60}h after prev"
                } else {
                    "+${step.delayMinutesFromPrevious / 1440}d after prev"
                }
            }
            Surface(
                shape = BentoGlassTokens.ShapePill,
                color = channelColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, channelColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "⚡ $timingBadgeText",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = channelColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        GlassSegmentedControl(
            items = listOf("Interval Delay", "Target Date & Time"),
            selectedIndex = if (isExactTargetTime) 1 else 0,
            onItemSelected = { selectedIdx ->
                if (selectedIdx == 0) {
                    onStepChanged(step.copy(specificTriggerTime = 0L))
                } else {
                    val defaultTime = if (step.specificTriggerTime > 0L) step.specificTriggerTime else System.currentTimeMillis() + (stepNumber * 3600_000L)
                    onStepChanged(step.copy(specificTriggerTime = defaultTime))
                }
            },
            activeColor = channelColor
        )

        if (!isExactTargetTime) {
            Spacer(modifier = Modifier.height(6.dp))
            val delayPresets = if (isFirst) {
                listOf(
                    0 to "Immediate",
                    15 to "+15m",
                    30 to "+30m",
                    60 to "+1h",
                    120 to "+2h",
                    1440 to "+1d"
                )
            } else {
                listOf(
                    15 to "+15m",
                    30 to "+30m",
                    60 to "+1h",
                    120 to "+2h",
                    360 to "+6h",
                    1440 to "+1d",
                    2880 to "+2d",
                    4320 to "+3d"
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                delayPresets.chunked(4).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowPresets.forEach { (minutes, label) ->
                            val isSel = step.delayMinutesFromPrevious == minutes
                            Surface(
                                shape = BentoGlassTokens.ShapePill,
                                color = if (isSel) channelColor.copy(alpha = 0.25f) else BentoGlassTokens.GlassSurfaceElevated,
                                border = BorderStroke(1.dp, if (isSel) channelColor else BentoGlassTokens.GlassBorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(BentoGlassTokens.ShapePill)
                                    .clickable { onStepChanged(step.copy(delayMinutesFromPrevious = minutes)) }
                            ) {
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) channelColor else BentoGlassTokens.TextMuted,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            InlineBentoDateTimePicker(
                timestampMillis = step.specificTriggerTime,
                onTimestampChanged = { newTs ->
                    onStepChanged(step.copy(specificTriggerTime = newTs))
                },
                accentColor = channelColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message text input
        GlassInputField(
            value = step.messageText,
            onValueChange = { onStepChanged(step.copy(messageText = it)) },
            label = "Step $stepNumber Message Text",
            placeholder = "Type message for this stage...",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 2,
            maxLines = 5,
            accentColor = channelColor
        )

        // Dynamic Variable Chips
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("{name}", "{first_name}", "{phone}", "{date}").forEach { tag ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(BentoGlassTokens.ShapePill)
                        .background(channelColor.copy(alpha = 0.12f))
                        .border(1.dp, channelColor.copy(alpha = 0.35f), BentoGlassTokens.ShapePill)
                        .clickable {
                            onStepChanged(step.copy(messageText = step.messageText + " $tag"))
                        }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tag, fontSize = 10.sp, color = channelColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
