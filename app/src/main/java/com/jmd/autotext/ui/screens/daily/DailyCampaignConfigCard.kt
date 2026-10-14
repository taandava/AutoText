package com.jmd.autotext.ui.screens.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.DailyCampaignConfig
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Suppress("FunctionName")
@Composable
fun DailyCampaignConfigCard(
    config: DailyCampaignConfig,
    isWhatsApp: Boolean,
    campaignThemeColor: Color,
    onConfigChange: (DailyCampaignConfig) -> Unit,
    onSave: (DailyCampaignConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isTimePickerExpanded by remember { mutableStateOf(false) }
    var dailyLimitText by remember(config.dailyLimit) { mutableStateOf(config.dailyLimit.toString()) }
    var intervalSecondsText by remember(config.intervalSeconds) { mutableStateOf(config.intervalSeconds.toString()) }

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeLarge
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = campaignThemeColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${if (isWhatsApp) "WhatsApp" else "SMS"} Campaign Settings",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoGlassTokens.TextPrimary
                )
            }
            BentoBadge(text = "Settings", color = campaignThemeColor, showDot = false)
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Daily Batch Limit (PROPER ENTRY FIELD + STEPPERS + CHIPS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassInputField(
                value = dailyLimitText,
                onValueChange = { newStr ->
                    val digits = newStr.filter { it.isDigit() }.take(4)
                    dailyLimitText = digits
                    val num = digits.toIntOrNull()
                    if (num != null && num > 0) {
                        onConfigChange(config.copy(dailyLimit = num.coerceIn(1, 1000)))
                    }
                },
                label = "Daily Batch Limit (Messages / Day)",
                placeholder = "e.g. 50",
                leadingIcon = Icons.Default.Pin,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = campaignThemeColor,
                modifier = Modifier.weight(1f)
            )

            // Stepper controls [-] [+]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(BentoGlassTokens.GlassSurfaceElevated)
                        .border(1.dp, campaignThemeColor.copy(alpha = 0.4f), CircleShape)
                        .clickable {
                            val next = (config.dailyLimit - 5).coerceAtLeast(1)
                            dailyLimitText = next.toString()
                            onConfigChange(config.copy(dailyLimit = next))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "-", color = campaignThemeColor, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(BentoGlassTokens.GlassSurfaceElevated)
                        .border(1.dp, campaignThemeColor.copy(alpha = 0.4f), CircleShape)
                        .clickable {
                            val next = (config.dailyLimit + 5).coerceAtMost(1000)
                            dailyLimitText = next.toString()
                            onConfigChange(config.copy(dailyLimit = next))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+", color = campaignThemeColor, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Preset Chips for Batch Limit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(10, 25, 50, 100, 200).forEach { limitPreset ->
                val isSel = config.dailyLimit == limitPreset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(BentoGlassTokens.ShapePill)
                        .background(if (isSel) campaignThemeColor.copy(alpha = 0.25f) else BentoGlassTokens.GlassSurfaceElevated)
                        .border(1.dp, if (isSel) campaignThemeColor else BentoGlassTokens.GlassBorderSubtle, BentoGlassTokens.ShapePill)
                        .clip(BentoGlassTokens.ShapePill)
                        .clickable {
                            dailyLimitText = limitPreset.toString()
                            onConfigChange(config.copy(dailyLimit = limitPreset))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$limitPreset",
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) campaignThemeColor else BentoGlassTokens.TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Scheduled Time & Carrier Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassButton(
                text = "Time: %02d:%02d".format(config.scheduledHour, config.scheduledMinute),
                onClick = { isTimePickerExpanded = !isTimePickerExpanded },
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Default.AccessTime,
                modifier = Modifier.weight(1f)
            )

            if (!isWhatsApp) {
                Box(modifier = Modifier.weight(1f)) {
                    GlassSegmentedControl(
                        items = listOf("SIM 1", "SIM 2"),
                        selectedIndex = if (config.simSlot == 0) 0 else 1,
                        onItemSelected = {
                            onConfigChange(config.copy(simSlot = it))
                        },
                        activeColor = BentoGlassTokens.NeonCyan
                    )
                }
            } else {
                val isWaBusiness = config.channel.contains("BUSINESS", ignoreCase = true)
                Box(modifier = Modifier.weight(1f)) {
                    GlassSegmentedControl(
                        items = listOf("WhatsApp", "WA Business"),
                        selectedIndex = if (isWaBusiness) 1 else 0,
                        onItemSelected = {
                            onConfigChange(config.copy(channel = if (it == 1) "WHATSAPP_BUSINESS" else "WHATSAPP"))
                        },
                        activeColor = BentoGlassTokens.VividEmerald
                    )
                }
            }
        }

        // Native Interactive Clock Picker Frame
        if (isTimePickerExpanded) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = BentoGlassTokens.ShapeMedium,
                color = BentoGlassTokens.GlassSurfaceDefault,
                border = BorderStroke(1.dp, BentoGlassTokens.GlassBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BentoGlassTokens.ShapeMedium)
                    .clickable {
                        android.app.TimePickerDialog(
                            context,
                            { _, h, m ->
                                val updated = config.copy(scheduledHour = h, scheduledMinute = m)
                                onConfigChange(updated)
                                onSave(updated)
                            },
                            config.scheduledHour,
                            config.scheduledMinute,
                            false
                        ).show()
                    },
                ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = campaignThemeColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scheduled Execution Time", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 13.sp)
                        }
                        BentoBadge(
                            text = "%02d:%02d".format(config.scheduledHour, config.scheduledMinute),
                            color = campaignThemeColor,
                            showDot = false
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap to open the clock picker and set exact delivery time.",
                        fontSize = 11.5.sp,
                        color = BentoGlassTokens.TextMuted,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GlassButton(
                        text = "Set Exact Time (Clock)",
                        onClick = {
                            android.app.TimePickerDialog(
                                context,
                                { _, h, m ->
                                    val updated = config.copy(scheduledHour = h, scheduledMinute = m)
                                    onConfigChange(updated)
                                    onSave(updated)
                                },
                                config.scheduledHour,
                                config.scheduledMinute,
                                false
                            ).show()
                        },
                        icon = Icons.Default.AccessTime,
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Send Delay Interval (PROPER ENTRY FIELD + STEPPERS + CHIPS)
        val minSec = if (isWhatsApp) 5 else 2
        val maxSec = 120

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassInputField(
                value = intervalSecondsText,
                onValueChange = { newStr ->
                    val digits = newStr.filter { it.isDigit() }.take(3)
                    intervalSecondsText = digits
                    val num = digits.toIntOrNull()
                    if (num != null && num > 0) {
                        onConfigChange(config.copy(intervalSeconds = num.coerceIn(minSec, maxSec)))
                    }
                },
                label = "Send Delay Interval (Seconds between messages)",
                placeholder = "e.g. 8",
                leadingIcon = Icons.Default.Timer,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = BentoGlassTokens.VividEmerald,
                modifier = Modifier.weight(1f)
            )

            // Stepper controls [-] [+]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(BentoGlassTokens.GlassSurfaceElevated)
                        .border(1.dp, BentoGlassTokens.VividEmerald.copy(alpha = 0.4f), CircleShape)
                        .clickable {
                            val next = (config.intervalSeconds - 2).coerceAtLeast(minSec)
                            intervalSecondsText = next.toString()
                            onConfigChange(config.copy(intervalSeconds = next))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "-", color = BentoGlassTokens.VividEmerald, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(BentoGlassTokens.GlassSurfaceElevated)
                        .border(1.dp, BentoGlassTokens.VividEmerald.copy(alpha = 0.4f), CircleShape)
                        .clickable {
                            val next = (config.intervalSeconds + 2).coerceAtMost(maxSec)
                            intervalSecondsText = next.toString()
                            onConfigChange(config.copy(intervalSeconds = next))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+", color = BentoGlassTokens.VividEmerald, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Preset Chips for Delay Interval
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(5, 8, 12, 15, 20, 30).forEach { secPreset ->
                val isSel = config.intervalSeconds == secPreset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(BentoGlassTokens.ShapePill)
                        .background(if (isSel) BentoGlassTokens.VividEmerald.copy(alpha = 0.25f) else BentoGlassTokens.GlassSurfaceElevated)
                        .border(1.dp, if (isSel) BentoGlassTokens.VividEmerald else BentoGlassTokens.GlassBorderSubtle, BentoGlassTokens.ShapePill)
                        .clickable {
                            intervalSecondsText = secPreset.toString()
                            onConfigChange(config.copy(intervalSeconds = secPreset))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${secPreset}s",
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) BentoGlassTokens.VividEmerald else BentoGlassTokens.TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Auto-scan Toggle
        GlassToggleRow(
            title = "Auto-scan before batch",
            subtitle = "Discovers newly received contacts, SMS and calls automatically",
            checked = config.autoScanEnabled,
            onCheckedChange = { onConfigChange(config.copy(autoScanEnabled = it)) },
            icon = Icons.Default.Sync,
            iconTint = BentoGlassTokens.ElectricIndigo,
            activeTrackColor = BentoGlassTokens.ElectricIndigo
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Unified Glass Input Field for Message Template
        GlassInputField(
            value = config.messageTemplate,
            onValueChange = { onConfigChange(config.copy(messageTemplate = it)) },
            label = "Campaign Message Template",
            placeholder = "Type your personalized batch message...",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            accentColor = campaignThemeColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Dynamic Tag Chips
        Text("Dynamic Variables:", fontSize = 11.sp, color = BentoGlassTokens.TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("{name}", "{first_name}", "{phone}", "{date}").forEach { tag ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(BentoGlassTokens.ShapePill)
                        .background(campaignThemeColor.copy(alpha = 0.12f))
                        .border(1.dp, campaignThemeColor.copy(alpha = 0.35f), BentoGlassTokens.ShapePill)
                        .clickable {
                            onConfigChange(config.copy(messageTemplate = config.messageTemplate + " $tag"))
                        }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tag, fontSize = 11.sp, color = campaignThemeColor, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        GlassButton(
            text = "Save Campaign Changes",
            onClick = { onSave(config) },
            variant = GlassButtonVariant.PRIMARY,
            icon = Icons.Default.Save,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
