package com.jmd.autotext.ui.screens.autoreply

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.ui.components.BentoCard
import com.jmd.autotext.ui.components.GlassChip
import com.jmd.autotext.ui.components.GlassInputField
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Suppress("FunctionName")
@Composable
fun AutoReplyLimitsCard(
    dailyLimitMode: String,
    onDailyLimitModeChanged: (String) -> Unit,
    customDailyLimitText: String,
    onCustomDailyLimitTextChanged: (String) -> Unit,
    cooldownMinutes: Int,
    onCooldownMinutesChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var cooldownText by remember(cooldownMinutes) { mutableStateOf(cooldownMinutes.toString()) }

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeLarge
    ) {
        Text("Anti-Spam & Contact Daily Quota", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Safeguards against messaging the same caller repeatedly within a 24-hour cycle.",
            fontSize = 11.5.sp,
            color = BentoGlassTokens.TextMuted,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("1" to "1x / day", "3" to "3x / day", "5" to "5x / day").forEach { (mode, label) ->
                GlassChip(
                    selected = dailyLimitMode == mode,
                    onClick = { onDailyLimitModeChanged(mode) },
                    label = label,
                    activeColor = BentoGlassTokens.NeonCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("CUSTOM" to "Custom", "UNLIMITED" to "Unlimited").forEach { (mode, label) ->
                GlassChip(
                    selected = dailyLimitMode == mode,
                    onClick = { onDailyLimitModeChanged(mode) },
                    label = label,
                    activeColor = BentoGlassTokens.NeonCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (dailyLimitMode == "CUSTOM") {
            Spacer(modifier = Modifier.height(10.dp))
            GlassInputField(
                value = customDailyLimitText,
                onValueChange = onCustomDailyLimitTextChanged,
                label = "Custom Times Per Day",
                placeholder = "e.g. 2, 4, 10",
                leadingIcon = Icons.Default.Pin,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = BentoGlassTokens.NeonCyan
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Repeat Delay Interval Entry (NO SLIDERS - Proper Material 3 Entry)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassInputField(
                value = cooldownText,
                onValueChange = { newStr ->
                    val digits = newStr.filter { it.isDigit() }.take(4)
                    cooldownText = digits
                    val num = digits.toIntOrNull()
                    if (num != null && num > 0) {
                        onCooldownMinutesChanged(num.coerceIn(1, 1440))
                    }
                },
                label = "Repeat Delay Interval (Minutes / Contact)",
                placeholder = "e.g. 10",
                leadingIcon = Icons.Default.Timer,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = BentoGlassTokens.VividEmerald,
                modifier = Modifier.weight(1f)
            )

            // Stepper controls: [-] [+]
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
                            val next = (cooldownMinutes - 5).coerceAtLeast(1)
                            cooldownText = next.toString()
                            onCooldownMinutesChanged(next)
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
                            val next = (cooldownMinutes + 5).coerceAtMost(1440)
                            cooldownText = next.toString()
                            onCooldownMinutesChanged(next)
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

        // Quick Preset Chips for Cooldown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(1 to "1m", 5 to "5m", 10 to "10m", 15 to "15m", 30 to "30m", 60 to "1h").forEach { (presetMin, presetLabel) ->
                val isSel = cooldownMinutes == presetMin
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(BentoGlassTokens.ShapePill)
                        .background(if (isSel) BentoGlassTokens.VividEmerald.copy(alpha = 0.25f) else BentoGlassTokens.GlassSurfaceElevated)
                        .border(1.dp, if (isSel) BentoGlassTokens.VividEmerald else BentoGlassTokens.GlassBorderSubtle, BentoGlassTokens.ShapePill)
                        .clickable {
                            cooldownText = presetMin.toString()
                            onCooldownMinutesChanged(presetMin)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = presetLabel,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) BentoGlassTokens.VividEmerald else BentoGlassTokens.TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
