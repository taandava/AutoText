package com.jmd.autotext.ui.screens.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.RecurrenceType
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Suppress("FunctionName")
@Composable
fun ComposeScheduleOptionsCard(
    selectedChannel: ChannelType,
    triggerTimeMillis: Long,
    onTriggerTimeChanged: (Long) -> Unit,
    selectedRecurrence: RecurrenceType,
    onRecurrenceChanged: (RecurrenceType) -> Unit,
    customIntervalMinutes: Int,
    onCustomIntervalMinutesChanged: (Int) -> Unit,
    simSlot: Int,
    onSimSlotChanged: (Int) -> Unit,
    askBeforeSend: Boolean,
    onAskBeforeSendChanged: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 1. Date & Time Picker (ZERO POPUPS / Inline Bento Glass)
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            shape = BentoGlassTokens.ShapeLarge
        ) {
            Text(
                "Execution Schedule Time",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = BentoGlassTokens.TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            InlineBentoDateTimePicker(
                timestampMillis = triggerTimeMillis,
                onTimestampChanged = onTriggerTimeChanged,
                accentColor = BentoGlassTokens.NeonCyan
            )
        }

        // 2. Recurrence Selector Bento Card
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            shape = BentoGlassTokens.ShapeLarge
        ) {
            Text(
                "Repetition Cycle",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = BentoGlassTokens.TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            val recurrenceOptions = RecurrenceType.entries.toList()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recurrenceOptions.chunked(3).forEach { rowRec ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowRec.forEach { rec ->
                            GlassChip(
                                selected = selectedRecurrence == rec,
                                onClick = { onRecurrenceChanged(rec) },
                                label = rec.displayName,
                                activeColor = BentoGlassTokens.VividEmerald,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (selectedRecurrence == RecurrenceType.CUSTOM_INTERVAL) {
                Spacer(modifier = Modifier.height(10.dp))
                var intervalText by androidx.compose.runtime.remember(customIntervalMinutes) {
                    androidx.compose.runtime.mutableStateOf(if (customIntervalMinutes > 0) customIntervalMinutes.toString() else "60")
                }

                GlassInputField(
                    value = intervalText,
                    onValueChange = { newStr ->
                        val digits = newStr.filter { it.isDigit() }.take(5)
                        intervalText = digits
                        val num = digits.toIntOrNull()
                        if (num != null && num > 0) {
                            onCustomIntervalMinutesChanged(num)
                        }
                    },
                    label = "Repeat interval in minutes",
                    placeholder = "60",
                    leadingIcon = Icons.Default.Timer,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    accentColor = BentoGlassTokens.VividEmerald
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf(15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h", 1440 to "1d").forEach { (mins, label) ->
                        val isSel = customIntervalMinutes == mins
                        GlassChip(
                            selected = isSel,
                            onClick = {
                                intervalText = mins.toString()
                                onCustomIntervalMinutesChanged(mins)
                            },
                            label = label,
                            activeColor = BentoGlassTokens.VividEmerald,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. SIM Card Selection for SMS
        if (selectedChannel == ChannelType.SMS) {
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                shape = BentoGlassTokens.ShapeLarge
            ) {
                Text(
                    "Carrier SIM Slot",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = BentoGlassTokens.TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Default SIM" to 0, "SIM 1" to 1, "SIM 2" to 2).forEach { (label, slot) ->
                        GlassChip(
                            selected = simSlot == slot,
                            onClick = { onSimSlotChanged(slot) },
                            label = label,
                            icon = Icons.Default.SimCard,
                            activeColor = BentoGlassTokens.NeonCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 4. Execution Safety Bento Card
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            shape = BentoGlassTokens.ShapeLarge
        ) {
            GlassToggleRow(
                title = "Ask Before Sending",
                subtitle = "Show approval prompt with Send & Skip actions when alarm triggers",
                checked = askBeforeSend,
                onCheckedChange = onAskBeforeSendChanged,
                icon = Icons.Default.NotificationsActive,
                iconTint = BentoGlassTokens.RadiantAmber,
                activeTrackColor = BentoGlassTokens.RadiantAmber
            )
        }
    }
}
