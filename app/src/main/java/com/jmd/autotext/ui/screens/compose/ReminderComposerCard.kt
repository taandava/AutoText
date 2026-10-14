package com.jmd.autotext.ui.screens.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.ui.components.BentoCard
import com.jmd.autotext.ui.components.GlassInputField
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun ReminderComposerCard(
    reminderTitle: String,
    onReminderTitleChange: (String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val presetReminders = listOf(
        Triple("💊 Medicine", "Take Medicine", "Time to take your medicine and drink a glass of water."),
        Triple("💧 Water", "Drink Water", "Hydration reminder: please drink a glass of fresh water."),
        Triple("⏰ Wake / Alert", "Alarm Alert", "Wake up! Your scheduled reminder is active."),
        Triple("💳 Pay Bill", "Pay Bills", "Reminder to check pending bill payments or dues today."),
        Triple("📞 Follow Up", "Follow Up Call", "Reminder: Call your contact or follow up on your task.")
    )

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = BentoGlassTokens.RadiantAmber.copy(alpha = 0.45f)
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
                        BentoGlassTokens.RadiantAmber.copy(alpha = 0.12f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = "On-Device Alarm",
                    tint = BentoGlassTokens.RadiantAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Local Device Alarm Reminder",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = BentoGlassTokens.TextPrimary
                    )
                    Text(
                        text = "Rings on this phone with full-screen wake & Voice (TTS). No phone number needed.",
                        fontSize = 11.sp,
                        color = BentoGlassTokens.TextMuted,
                        lineHeight = 14.sp
                    )
                }
            }

            // Quick Preset Chips
            Text(
                text = "Quick Presets:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = BentoGlassTokens.TextMuted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presetReminders.take(3).forEach { (label, defaultTitle, defaultSpeech) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BentoGlassTokens.GlassSurfaceElevated)
                            .border(1.dp, BentoGlassTokens.GlassBorderSubtle, RoundedCornerShape(6.dp))
                            .clickable {
                                onReminderTitleChange(defaultTitle)
                                onMessageTextChange(defaultSpeech)
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = BentoGlassTokens.TextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }

            // Reminder Title / Subject
            GlassInputField(
                value = reminderTitle,
                onValueChange = onReminderTitleChange,
                label = "Reminder Title",
                placeholder = "e.g. Take Blood Pressure Tablet, Water Plants",
                leadingIcon = Icons.Default.Title,
                accentColor = BentoGlassTokens.RadiantAmber
            )

            // Voice Speech & Display Note
            GlassInputField(
                value = messageText,
                onValueChange = onMessageTextChange,
                label = "Voice Speech Note (TTS)",
                placeholder = "What this phone should announce aloud when alarm rings...",
                leadingIcon = Icons.Default.RecordVoiceOver,
                accentColor = BentoGlassTokens.RadiantAmber,
                singleLine = false,
                maxLines = 4
            )
        }
    }
}
