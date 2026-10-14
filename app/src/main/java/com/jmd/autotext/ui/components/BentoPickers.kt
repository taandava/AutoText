@file:Suppress("FunctionName")

package com.jmd.autotext.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import java.text.SimpleDateFormat
import java.util.*

/**
 * Inline Bento Glass Date & Time Picker
 * ZERO POPUPS / ZERO MODALS. Directly interactive inside the page / card.
 */
@Composable
fun InlineBentoDateTimePicker(
    timestampMillis: Long,
    onTimestampChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = BentoGlassTokens.NeonCyan,
    showDate: Boolean = true,
    showTime: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cal = remember(timestampMillis) {
        Calendar.getInstance().apply { timeInMillis = if (timestampMillis > 0) timestampMillis else System.currentTimeMillis() }
    }
    var year by remember(timestampMillis) { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var month by remember(timestampMillis) { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    var day by remember(timestampMillis) { mutableIntStateOf(cal.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember(timestampMillis) { mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember(timestampMillis) { mutableIntStateOf(cal.get(Calendar.MINUTE)) }

    fun updateTimestamp(newYear: Int = year, newMonth: Int = month, newDay: Int = day, newHour: Int = hour, newMinute: Int = minute) {
        val c = Calendar.getInstance().apply {
            set(newYear, newMonth, newDay, newHour, newMinute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        year = newYear
        month = newMonth
        day = newDay
        hour = newHour
        minute = newMinute
        onTimestampChanged(c.timeInMillis)
    }

    val displayDateStr = remember(year, month, day) {
        val c = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(c.time)
    }

    val displayTimeStr = remember(hour, minute) {
        val amPm = if (hour >= 12) "PM" else "AM"
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        "%02d:%02d %s".format(h12, minute, amPm)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(BentoGlassTokens.ShapeMedium)
            .background(BentoGlassTokens.GlassSurfaceDefault)
            .border(1.dp, accentColor.copy(alpha = 0.25f), BentoGlassTokens.ShapeMedium)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Summary Header Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EventAvailable,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Target Schedule",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoGlassTokens.TextPrimary
                )
            }

            Surface(
                shape = BentoGlassTokens.ShapePill,
                color = accentColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "$displayDateStr • $displayTimeStr",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        if (showDate) {
            // Quick Date Preset Chips
            Text("Select Date (Quick Presets):", fontSize = 12.sp, color = BentoGlassTokens.TextMuted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "Today" to 0,
                    "Tomorrow" to 1,
                    "+2 Days" to 2,
                    "+1 Week" to 7
                ).forEach { (label, offsetDays) ->
                    val targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
                    val isSelected = (day == targetCal.get(Calendar.DAY_OF_MONTH) &&
                                      month == targetCal.get(Calendar.MONTH) &&
                                      year == targetCal.get(Calendar.YEAR))
                    Surface(
                        shape = BentoGlassTokens.ShapePill,
                        color = if (isSelected) accentColor.copy(alpha = 0.25f) else BentoGlassTokens.GlassSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) accentColor else BentoGlassTokens.GlassBorderSubtle
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(BentoGlassTokens.ShapePill)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = accentColor.copy(alpha = 0.25f)),
                                onClick = {
                                    updateTimestamp(
                                        newYear = targetCal.get(Calendar.YEAR),
                                        newMonth = targetCal.get(Calendar.MONTH),
                                        newDay = targetCal.get(Calendar.DAY_OF_MONTH)
                                    )
                                }
                            )
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accentColor else BentoGlassTokens.TextPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // Proper Native Date Picker Dialog Button (NO SLIDERS)
            Surface(
                shape = BentoGlassTokens.ShapePill,
                color = BentoGlassTokens.GlassSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BentoGlassTokens.ShapePill)
                    .clickable {
                        android.app.DatePickerDialog(
                            context,
                            { _, y, m, d -> updateTimestamp(newYear = y, newMonth = m, newDay = d) },
                            year, month, day
                        ).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Date from Calendar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
                }
            }
        }

        if (showTime) {
            Spacer(modifier = Modifier.height(2.dp))
            // Quick Time Preset Chips
            Text("Select Time (Quick Presets):", fontSize = 12.sp, color = BentoGlassTokens.TextMuted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "09:00 AM" to (9 to 0),
                    "12:00 PM" to (12 to 0),
                    "03:00 PM" to (15 to 0),
                    "06:00 PM" to (18 to 0),
                    "09:00 PM" to (21 to 0)
                ).forEach { (lbl, timePair) ->
                    val isSel = (hour == timePair.first && minute == timePair.second)
                    Surface(
                        shape = BentoGlassTokens.ShapePill,
                        color = if (isSel) accentColor.copy(alpha = 0.25f) else BentoGlassTokens.GlassSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSel) accentColor else BentoGlassTokens.GlassBorderSubtle
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(BentoGlassTokens.ShapePill)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = accentColor.copy(alpha = 0.25f)),
                                onClick = {
                                    updateTimestamp(newHour = timePair.first, newMinute = timePair.second)
                                }
                            )
                    ) {
                        Text(
                            text = lbl.take(5),
                            fontSize = 11.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) accentColor else BentoGlassTokens.TextPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // Proper Native Time Picker Dialog Button (NO SLIDERS)
            Surface(
                shape = BentoGlassTokens.ShapeMedium,
                color = BentoGlassTokens.GlassSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BentoGlassTokens.ShapeMedium)
                    .clickable {
                        android.app.TimePickerDialog(
                            context,
                            { _, h, m -> updateTimestamp(newHour = h, newMinute = m) },
                            hour, minute, false
                        ).show()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Scheduled Time", fontSize = 11.sp, color = BentoGlassTokens.TextMuted)
                            Text(displayTimeStr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary)
                        }
                    }
                    Surface(
                        shape = BentoGlassTokens.ShapePill,
                        color = accentColor.copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            "Tap to Change Clock",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// Material 3 Canonical Aliases
@Composable
fun Material3DateTimePicker(
    timestampMillis: Long,
    onTimestampChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = BentoGlassTokens.NeonCyan,
    showDate: Boolean = true,
    showTime: Boolean = true
) = InlineBentoDateTimePicker(timestampMillis, onTimestampChanged, modifier, accentColor, showDate, showTime)



