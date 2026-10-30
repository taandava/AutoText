package com.jmd.autotext.ui.screen.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.jmd.autotext.data.model.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*

/**
 * Card managing schedule date, time, repetition, and carrier SIM selection.
 * @param onSimSlotChanged Callback when selected SIM slot is updated.
 * @param selectedDaysOfWeek Days of week selected for weekly repetition schedule.
 * @param onSelectedDaysOfWeekChanged Callback when selected days of week are toggled.
 */
@Composable
fun composeScheduleOptionsCard(
    selectedChannel: ChannelType,
    triggerTimeMillis: Long,
    onTriggerTimeChanged: (Long) -> Unit,
    selectedRecurrence: RecurrenceType,
    onRecurrenceChanged: (RecurrenceType) -> Unit,
    customIntervalMinutes: Int,
    onCustomIntervalMinutesChanged: (Int) -> Unit,
    simSlot: Int,
    onSimSlotChanged: (Int) -> Unit,
    selectedDaysOfWeek: List<Int> = emptyList(),
    onSelectedDaysOfWeekChanged: (List<Int>) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 1. Date & Time Picker (ZERO POPUPS / Inline Enterprise Modern Glass)
        appCard(
            modifier = Modifier.fillMaxWidth(),
            shape = AppDesignTokens.ShapeLarge
        ) {
            Text(
                "Execution Schedule Time",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            appDateTimePicker(
                timestampMillis = triggerTimeMillis,
                onTimestampChanged = onTriggerTimeChanged,
                accentColor = MaterialTheme.colorScheme.primary
            )
        }

        // 2. Repetition & Recurrence Selector (Once vs Granular Repeat with Dropdown)
        val isRepeat = selectedRecurrence != RecurrenceType.ONCE
        var selectedUnit by remember(selectedRecurrence) {
            mutableStateOf(
                when (selectedRecurrence) {
                    RecurrenceType.REPEAT_SECONDS -> RepeatUnit.SECONDS
                    RecurrenceType.REPEAT_HOURS -> RepeatUnit.HOURS
                    RecurrenceType.REPEAT_DAYS, RecurrenceType.DAILY -> RepeatUnit.DAYS
                    RecurrenceType.REPEAT_WEEKS, RecurrenceType.WEEKLY -> RepeatUnit.WEEKS
                    RecurrenceType.REPEAT_MONTHS, RecurrenceType.MONTHLY -> RepeatUnit.MONTHS
                    else -> RepeatUnit.MINUTES
                }
            )
        }
        var intervalCountText by remember(customIntervalMinutes) {
            mutableStateOf(
                if (customIntervalMinutes > 0) customIntervalMinutes.toString() else "1"
            )
        }

        appCard(
            modifier = Modifier.fillMaxWidth(),
            shape = AppDesignTokens.ShapeLarge
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Repetition Schedule",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                appBadge(
                    text = if (isRepeat) "Repeating" else "Single Run",
                    color = if (isRepeat) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Segmented Selection: [ Once ] [ Repeat ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Option: Once
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isRepeat) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onRecurrenceChanged(RecurrenceType.ONCE) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🔘 Run Once",
                        fontSize = 12.sp,
                        fontWeight = if (!isRepeat) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isRepeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Option: Repeat
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isRepeat) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            val count = intervalCountText.toIntOrNull() ?: 1
                            onCustomIntervalMinutesChanged(count)
                            val targetRec = when (selectedUnit) {
                                RepeatUnit.SECONDS -> RecurrenceType.REPEAT_SECONDS
                                RepeatUnit.MINUTES -> RecurrenceType.REPEAT_MINUTES
                                RepeatUnit.HOURS -> RecurrenceType.REPEAT_HOURS
                                RepeatUnit.DAYS -> RecurrenceType.REPEAT_DAYS
                                RepeatUnit.WEEKS -> RecurrenceType.REPEAT_WEEKS
                                RepeatUnit.MONTHS -> RecurrenceType.REPEAT_MONTHS
                            }
                            onRecurrenceChanged(targetRec)
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🔁 Repeat",
                        fontSize = 12.sp,
                        fontWeight = if (isRepeat) FontWeight.Bold else FontWeight.Normal,
                        color = if (isRepeat) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isRepeat) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Task will execute once at the selected date & time.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Repeat Interval Configuration",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    glassInputField(
                        value = intervalCountText,
                        onValueChange = { newStr ->
                            val digits = newStr.filter { it.isDigit() }.take(4)
                            intervalCountText = digits
                            val count = digits.toIntOrNull() ?: 1
                            onCustomIntervalMinutesChanged(count)
                            val targetRec = when (selectedUnit) {
                                RepeatUnit.SECONDS -> RecurrenceType.REPEAT_SECONDS
                                RepeatUnit.MINUTES -> RecurrenceType.REPEAT_MINUTES
                                RepeatUnit.HOURS -> RecurrenceType.REPEAT_HOURS
                                RepeatUnit.DAYS -> RecurrenceType.REPEAT_DAYS
                                RepeatUnit.WEEKS -> RecurrenceType.REPEAT_WEEKS
                                RepeatUnit.MONTHS -> RecurrenceType.REPEAT_MONTHS
                            }
                            onRecurrenceChanged(targetRec)
                        },
                        label = "Interval",
                        placeholder = "1",
                        leadingIcon = Icons.Default.Repeat,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )

                    glassDropdownField(
                        selectedValue = selectedUnit,
                        onValueChange = { unit ->
                            selectedUnit = unit
                            val count = intervalCountText.toIntOrNull() ?: 1
                            onCustomIntervalMinutesChanged(count)
                            val targetRec = when (unit) {
                                RepeatUnit.SECONDS -> RecurrenceType.REPEAT_SECONDS
                                RepeatUnit.MINUTES -> RecurrenceType.REPEAT_MINUTES
                                RepeatUnit.HOURS -> RecurrenceType.REPEAT_HOURS
                                RepeatUnit.DAYS -> RecurrenceType.REPEAT_DAYS
                                RepeatUnit.WEEKS -> RecurrenceType.REPEAT_WEEKS
                                RepeatUnit.MONTHS -> RecurrenceType.REPEAT_MONTHS
                            }
                            onRecurrenceChanged(targetRec)
                        },
                        options = RepeatUnit.entries,
                        label = "Time Unit",
                        displayValue = { it.displayName },
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                val countNum = intervalCountText.toIntOrNull() ?: 1
                val unitLabel = if (countNum == 1) selectedUnit.singularName else selectedUnit.displayName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ Next run repeats every $countNum $unitLabel after completion.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // 3. SIM Card Selection for SMS
        if (selectedChannel == ChannelType.SMS) {
            appCard(
                modifier = Modifier.fillMaxWidth(),
                shape = AppDesignTokens.ShapeLarge
            ) {
                Text(
                    "Carrier SIM Slot",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf("Default SIM" to 0, "SIM 1" to 1, "SIM 2" to 2).forEach { (label, slot) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onSimSlotChanged(slot) }
                        ) {
                            RadioButton(
                                selected = simSlot == slot,
                                onClick = { onSimSlotChanged(slot) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

    }
}
