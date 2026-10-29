

/**
 * Package: com.jmd.autotext.ui.screen
 * Module: UI Reminder Fullscreen Alert Layer
 *
 * Fullscreen alarm and reminder interaction screen showing note content with snooze and dismiss actions.
 *
 * Linked With:
 * - Activity: [com.jmd.autotext.ui.activity.ReminderAlarmActivity]
 */
package com.jmd.autotext.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*

/**
 * Fullscreen alarm and reminder interaction screen rendering custom note content,
 * prominent alarm iconography, and intuitive snooze / dismiss action controls.
 *
 * @param title Header title of the triggered reminder (e.g., "Meeting Reminder").
 * @param text Detailed descriptive body message.
 * @param onDismiss Callback invoked when user acknowledges and cancels the alarm.
 * @param onSnooze Callback receiving snooze duration in minutes (e.g., 5, 10, or 15 mins).
 */
@Composable
fun reminderScreen(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesignTokens.GlassBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            appCardElevated(
                modifier = Modifier.fillMaxWidth(),
                accentGlow = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Enterprise Modern Glass Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                glassButton(
                    text = "Snooze 5m",
                    onClick = { onSnooze(5) },
                    variant = GlassButtonVariant.SECONDARY,
                    icon = Icons.Default.Snooze,
                    modifier = Modifier.weight(1f)
                )

                glassButton(
                    text = "Dismiss",
                    onClick = onDismiss,
                    variant = GlassButtonVariant.SUCCESS,
                    icon = Icons.Default.Check,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ============================================================================
// AUTO-REPLY RULES STUDIO SCREEN
// ============================================================================


@Composable
fun reminderComposerCard(
    reminderTitle: String,
    onReminderTitleChange: (String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    appCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: On-Device Alarm Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = "On-Device Alarm",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Local Device Alarm Reminder",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Rings on this phone with full-screen wake & Voice (TTS). No phone number needed.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
                    )
                }
            }

            // Reminder Title / Subject
            glassInputField(
                value = reminderTitle,
                onValueChange = onReminderTitleChange,
                label = "Reminder Title",
                placeholder = "e.g. Take Blood Pressure Tablet, Water Plants",
                leadingIcon = Icons.Default.Title,
                accentColor = MaterialTheme.colorScheme.primary
            )

            // Voice Speech & Display Note
            glassInputField(
                value = messageText,
                onValueChange = onMessageTextChange,
                label = "Voice Speech Note (TTS)",
                placeholder = "What this phone should announce aloud when alarm rings...",
                leadingIcon = Icons.Default.RecordVoiceOver,
                accentColor = MaterialTheme.colorScheme.primary,
                singleLine = false,
                maxLines = 4
            )

            // Dynamic Variable Chips for Reminder Voice Note
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("{time}", "{date}", "{short_date}", "{day}", "{short_day}", "{day_num}").forEach { tag ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(AppDesignTokens.ShapePill)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), AppDesignTokens.ShapePill)
                            .clickable {
                                onMessageTextChange(if (messageText.isBlank()) tag else "$messageText $tag")
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tag,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}


