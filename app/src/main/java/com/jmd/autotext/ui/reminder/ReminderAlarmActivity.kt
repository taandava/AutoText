package com.jmd.autotext.ui.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class ReminderAlarmActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var reminderText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val title = intent.getStringExtra("reminder_title") ?: "Reminder"
        reminderText = intent.getStringExtra("reminder_text") ?: "Scheduled Alert"

        tts = TextToSpeech(this, this)

        setContent {
            ReminderScreen(
                title = title,
                text = reminderText,
                onDismiss = {
                    finish()
                },
                onSnooze = { minutes ->
                    snooze(minutes, title, reminderText)
                    finish()
                }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.speak(reminderText, TextToSpeech.QUEUE_FLUSH, null, "reminder_speech")
        }
    }

    private fun snooze(minutes: Int, title: String, text: String) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderAlarmActivity::class.java).apply {
            putExtra("reminder_title", title)
            putExtra("reminder_text", text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val trigger = System.currentTimeMillis() + (minutes * 60 * 1000L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("ReminderAlarmActivity", "Exact alarm permission missing on snooze, falling back", e)
            am.set(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Suppress("FunctionName")
@Composable
fun ReminderScreen(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.jmd.autotext.ui.theme.BentoGlassTokens.GlassBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(com.jmd.autotext.ui.theme.BentoGlassTokens.RadiantAmber.copy(alpha = 0.18f))
                    .border(1.5.dp, com.jmd.autotext.ui.theme.BentoGlassTokens.RadiantAmber.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = com.jmd.autotext.ui.theme.BentoGlassTokens.RadiantAmber,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = com.jmd.autotext.ui.theme.BentoGlassTokens.TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            com.jmd.autotext.ui.components.BentoCardElevated(
                modifier = Modifier.fillMaxWidth(),
                accentGlow = com.jmd.autotext.ui.theme.BentoGlassTokens.RadiantAmber
            ) {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    color = com.jmd.autotext.ui.theme.BentoGlassTokens.TextPrimary,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Bento Glass Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                com.jmd.autotext.ui.components.GlassButton(
                    text = "Snooze 5m",
                    onClick = { onSnooze(5) },
                    variant = com.jmd.autotext.ui.components.GlassButtonVariant.SECONDARY,
                    icon = Icons.Default.Snooze,
                    modifier = Modifier.weight(1f)
                )

                com.jmd.autotext.ui.components.GlassButton(
                    text = "Dismiss",
                    onClick = onDismiss,
                    variant = com.jmd.autotext.ui.components.GlassButtonVariant.SUCCESS,
                    icon = Icons.Default.Check,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
