package com.jmd.helper.ui.screen.settings

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.ui.components.*

/**
 * Operational controls for automation channels: WhatsApp pacing, SMS quotas,
 * multi-SIM preferences, call simulation defaults, TTS voice engine, and auto-reply.
 */
@Composable
fun SettingsAutomationChannelsCard(
    context: Context,
    prefs: PreferenceManager,
    modifier: Modifier = Modifier
) {
    var waDelay by remember { mutableIntStateOf(prefs.whatsappDispatchDelaySeconds) }
    var waTypingDelay by remember { mutableIntStateOf(prefs.whatsappTypingDelayMs) }
    var autoSmsFallback by remember { mutableStateOf(prefs.isAutoSmsFallbackEnabled) }
    var dailySmsLimit by remember { mutableIntStateOf(prefs.dailySmsSafetyLimit) }
    var smsQuotaEnabled by remember { mutableStateOf(prefs.isSmsQuotaLimitEnabled) }
    var simSlot by remember { mutableIntStateOf(prefs.defaultSimSlot) }
    var callSimDuration by remember { mutableIntStateOf(prefs.callSimulationRingDurationSeconds) }
    var callSimName by remember { mutableStateOf(prefs.callSimulationDefaultName) }
    var callSimNumber by remember { mutableStateOf(prefs.callSimulationDefaultNumber) }
    var callSimStyle by remember { mutableStateOf(prefs.callSimulationDefaultStyle) }
    var ttsRate by remember { mutableFloatStateOf(prefs.ttsSpeechRate) }
    var ttsPitch by remember { mutableFloatStateOf(prefs.ttsSpeechPitch) }
    var autoReplyCooldown by remember { mutableIntStateOf(prefs.autoReplyCooldownSeconds) }
    var contactSyncHours by remember { mutableIntStateOf(prefs.contactSyncIntervalHours) }

    // TTS Engine for live testing
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialized successfully
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. WhatsApp Automation & Pacing
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("WhatsApp Dispatch & Human Cadence", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = "${waDelay}s Pace", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Adjust organic delay between dispatches and realistic typing simulation.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Inter-Task Dispatch Delay:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val delays = listOf(1, 2, 3, 5, 8, 10)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                delays.forEach { s ->
                    GlassChip(
                        selected = waDelay == s,
                        onClick = {
                            waDelay = s
                            prefs.whatsappDispatchDelaySeconds = s
                        },
                        label = "${s}s",
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Typing Indicator Simulation Delay:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val typingDelays = listOf(
                0 to "Instant (0ms)",
                400 to "Natural (400ms)",
                800 to "Human (800ms)",
                1500 to "Deliberate (1.5s)"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                typingDelays.forEach { (ms, label) ->
                    GlassChip(
                        selected = waTypingDelay == ms,
                        onClick = {
                            waTypingDelay = ms
                            prefs.whatsappTypingDelayMs = ms
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            GlassToggleRow(
                title = "Automatic Cellular SMS Fallback",
                subtitle = "Attempt carrier SMS delivery if WhatsApp contact is unregistered or unavailable",
                checked = autoSmsFallback,
                onCheckedChange = { checked ->
                    autoSmsFallback = checked
                    prefs.isAutoSmsFallbackEnabled = checked
                },
                icon = Icons.Filled.SmsFailed
            )
        }

        // 2. Carrier SMS & Multi-SIM Routing
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Carrier SMS & Multi-SIM Routing", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = if (simSlot == -1) "Default" else "SIM ${simSlot + 1}", color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Select preferred outbound SIM and configure TRAI daily safety quotas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Default Cellular SIM Slot:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val slots = listOf(
                0 to "SIM 1",
                1 to "SIM 2",
                -1 to "System Default"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                slots.forEach { (slot, label) ->
                    GlassChip(
                        selected = simSlot == slot,
                        onClick = {
                            simSlot = slot
                            prefs.defaultSimSlot = slot
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            GlassToggleRow(
                title = "TRAI 100 SMS/Day Safety Quota Guard",
                subtitle = "Prevent telecom carrier blacklisting by capping daily outgoing SMS dispatches",
                checked = smsQuotaEnabled,
                onCheckedChange = { checked ->
                    smsQuotaEnabled = checked
                    prefs.isSmsQuotaLimitEnabled = checked
                },
                icon = Icons.Filled.Security
            )

            if (smsQuotaEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Safety Cap Threshold: $dailySmsLimit SMS/Day", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))

                val limits = listOf(20, 50, 80, 100)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    limits.forEach { lim ->
                        GlassChip(
                            selected = dailySmsLimit == lim,
                            onClick = {
                                dailySmsLimit = lim
                                prefs.dailySmsSafetyLimit = lim
                            },
                            label = "$lim/day",
                            activeColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. Simulated Incoming Call Settings
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.PhoneForwarded, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Call Simulation Customizations", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = callSimStyle, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Configure default identity, ring duration, and visual skin for incoming call simulation.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = callSimName,
                    onValueChange = {
                        callSimName = it
                        prefs.callSimulationDefaultName = it
                    },
                    label = { Text("Caller Name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = callSimNumber,
                    onValueChange = {
                        callSimNumber = it
                        prefs.callSimulationDefaultNumber = it
                    },
                    label = { Text("Phone Number") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Visual Screen Skin:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val styles = listOf(
                "PIXEL" to "Google Pixel",
                "SAMSUNG" to "Samsung OneUI",
                "WHATSAPP" to "WhatsApp Call"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                styles.forEach { (st, label) ->
                    GlassChip(
                        selected = callSimStyle == st,
                        onClick = {
                            callSimStyle = st
                            prefs.callSimulationDefaultStyle = st
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Ringing Timeout Duration:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val durations = listOf(15, 30, 45, 60)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                durations.forEach { d ->
                    GlassChip(
                        selected = callSimDuration == d,
                        onClick = {
                            callSimDuration = d
                            prefs.callSimulationRingDurationSeconds = d
                        },
                        label = "${d}s",
                        activeColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Text-To-Speech (TTS) Voice Engine
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Text-To-Speech (TTS) Voice Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = "${"%.1f".format(ttsRate)}x", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Fine-tune speech rate and pitch for voice reminders and audible alarms.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Speech Rate: ${"%.2f".format(ttsRate)}x", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Slider(
                value = ttsRate,
                onValueChange = {
                    ttsRate = it
                    prefs.ttsSpeechRate = it
                },
                valueRange = 0.5f..2.0f,
                steps = 5,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Text("Speech Pitch: ${"%.2f".format(ttsPitch)}x", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Slider(
                value = ttsPitch,
                onValueChange = {
                    ttsPitch = it
                    prefs.ttsSpeechPitch = it
                },
                valueRange = 0.5f..1.8f,
                steps = 5,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary
                )
            )

            Spacer(modifier = Modifier.height(4.dp))
            GlassButton(
                text = "Test Voice Speech Output",
                onClick = {
                    ttsEngine?.let { engine ->
                        engine.setSpeechRate(ttsRate)
                        engine.setPitch(ttsPitch)
                        engine.speak(
                            "Namaste! This is your voice reminder test with Helper.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "test_utterance"
                        )
                    }
                },
                icon = Icons.Filled.VolumeUp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 5. Auto-Reply & Contact Harvester
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Auto-Reply & Contact Pool Sync", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = "${autoReplyCooldown}s Throttle", color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Prevent auto-reply message flooding and set periodic address book harvest frequency.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Auto-Reply Cooldown Throttle per Sender:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val cooldowns = listOf(10, 30, 60, 120, 300)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                cooldowns.forEach { c ->
                    GlassChip(
                        selected = autoReplyCooldown == c,
                        onClick = {
                            autoReplyCooldown = c
                            prefs.autoReplyCooldownSeconds = c
                        },
                        label = "${c}s",
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Address Book & Group Harvest Interval:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val syncIntervals = listOf(2, 6, 12, 24)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                syncIntervals.forEach { h ->
                    GlassChip(
                        selected = contactSyncHours == h,
                        onClick = {
                            contactSyncHours = h
                            prefs.contactSyncIntervalHours = h
                        },
                        label = "Every ${h}h",
                        activeColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
