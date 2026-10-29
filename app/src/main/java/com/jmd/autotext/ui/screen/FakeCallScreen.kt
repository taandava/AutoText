

/**
 * Package: com.jmd.autotext.ui.screen
 * Module: UI Fake Call Fullscreen Screen Layer
 *
 * Incoming simulated phone call user interface rendering caller photo/avatar, contact name,
 * and animated accept/decline action triggers.
 *
 * Linked With:
 * - Activity: [com.jmd.autotext.ui.activity.FakeCallActivity]
 */
package com.jmd.autotext.ui.screen

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.util.*
import kotlin.time.Duration.Companion.seconds

import com.jmd.autotext.ui.theme.*

/**
 * Incoming phone call simulation screen rendering caller identity, animated answer/decline
 * controls, active call timer, and in-call options (Mute, Keypad, Speaker).
 *
 * @param name Custom caller display name (e.g. "Work / Boss").
 * @param number Simulated phone number string.
 * @param style Visual skin style (e.g. "PIXEL", "SAMSUNG", "IOS").
 * @param onAnswer Invoked when call is answered to stop ringtones.
 * @param onDecline Invoked when call is rejected or hung up to finish the activity.
 */
@Composable
fun fakeCallScreen(
    name: String,
    number: String,
    style: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    var isCallActive by remember { mutableStateOf(value = false) }
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }

    LaunchedEffect(isCallActive) {
        if (isCallActive) {
            while (true) {
                delay(1.seconds)
                secondsElapsed++
            }
        }
    }

    val formattedTimer = remember(secondsElapsed) {
        val mins = secondsElapsed / 60
        val secs = secondsElapsed % 60
        String.format(Locale.ROOT, "%02d:%02d", mins, secs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDesignTokens.GlassBackground),
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
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = name,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isCallActive) formattedTimer else number,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCallActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )

                if (!isCallActive) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Incoming Simulation • $style Style",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            .background(if (isMuted) Color.White else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
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
                            .background(if (isSpeakerOn) Color.White else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
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
                                .background(AppDesignTokens.RoseGradient)
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
                        Text(text = "Decline", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                                .background(AppDesignTokens.EmeraldGradient)
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
                        Text(text = "Answer", color = MaterialTheme.colorScheme.tertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                        .background(AppDesignTokens.RoseGradient)
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
