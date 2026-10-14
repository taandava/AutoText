package com.jmd.autotext.ui.fakecall

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class FakeCallActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and show over lock screen
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

        val callerName = intent.getStringExtra("caller_name") ?: "Incoming Call"
        val callerNumber = intent.getStringExtra("caller_number") ?: "+1 (555) 019-2834"
        val callerStyle = intent.getStringExtra("caller_style") ?: "PIXEL"

        startRinging()

        setContent {
            FakeCallScreen(
                name = callerName,
                number = callerNumber,
                style = callerStyle,
                onAnswer = { stopRinging() },
                onDecline = {
                    stopRinging()
                    finish()
                }
            )
        }
    }

    private fun startRinging() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer.create(this, ringtoneUri).apply {
                isLooping = true
                start()
            }

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRinging() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}

@Composable
fun FakeCallScreen(
    name: String,
    number: String,
    style: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    var isCallActive by remember { mutableStateOf(false) }
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }

    LaunchedEffect(isCallActive) {
        if (isCallActive) {
            while (true) {
                delay(1000L)
                secondsElapsed++
            }
        }
    }

    val formattedTimer = remember(secondsElapsed) {
        val mins = secondsElapsed / 60
        val secs = secondsElapsed % 60
        java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d", mins, secs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.jmd.autotext.ui.theme.BentoGlassTokens.GlassBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Caller Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(com.jmd.autotext.ui.theme.BentoGlassTokens.ElectricIndigo.copy(alpha = 0.18f))
                        .border(
                            1.5.dp,
                            com.jmd.autotext.ui.theme.BentoGlassTokens.ElectricIndigo.copy(alpha = 0.45f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = com.jmd.autotext.ui.theme.BentoGlassTokens.NeonCyan,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = name,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.jmd.autotext.ui.theme.BentoGlassTokens.TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isCallActive) formattedTimer else number,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCallActive) com.jmd.autotext.ui.theme.BentoGlassTokens.VividEmerald else com.jmd.autotext.ui.theme.BentoGlassTokens.NeonCyan
                )

                if (!isCallActive) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Incoming Simulation • $style Style",
                        fontSize = 12.sp,
                        color = com.jmd.autotext.ui.theme.BentoGlassTokens.TextMuted
                    )
                }
            }

            // Middle Section: In-Call Controls (if answered)
            if (isCallActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color.White else com.jmd.autotext.ui.theme.BentoGlassTokens.GlassSurfaceElevated)
                            .border(1.dp, com.jmd.autotext.ui.theme.BentoGlassTokens.GlassBorderSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = if (isMuted) Color.Black else Color.White
                        )
                    }

                    IconButton(
                        onClick = { isSpeakerOn = !isSpeakerOn },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isSpeakerOn) Color.White else com.jmd.autotext.ui.theme.BentoGlassTokens.GlassSurfaceElevated)
                            .border(1.dp, com.jmd.autotext.ui.theme.BentoGlassTokens.GlassBorderSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = "Speaker",
                            tint = if (isSpeakerOn) Color.Black else Color.White
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(30.dp))
            }

            // Bottom Section: Actions
            if (!isCallActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Decline Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onDecline,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(com.jmd.autotext.ui.theme.BentoGlassTokens.RoseGradient)
                                .border(1.dp, Color(0x66FFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Decline", color = com.jmd.autotext.ui.theme.BentoGlassTokens.CyberRose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Answer Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                isCallActive = true
                                onAnswer()
                            },
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(com.jmd.autotext.ui.theme.BentoGlassTokens.EmeraldGradient)
                                .border(1.dp, Color(0x66FFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Answer",
                                tint = Color.Black,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Answer", color = com.jmd.autotext.ui.theme.BentoGlassTokens.VividEmerald, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // End Call Button
                IconButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .padding(bottom = 36.dp)
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(com.jmd.autotext.ui.theme.BentoGlassTokens.RoseGradient)
                        .border(1.dp, Color(0x66FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}
