/**
 * Package: com.jmd.superapp.ui.screen.settings
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [SettingsAutomationChannelsCardComponents].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for SettingsAutomationChannelsCardComponents.
 *
 * Linked With:
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.text.font.FontWeight], [androidx.compose.ui.text.input.KeyboardType]
 * - Core Security & Utilities: [java.util.Locale], [com.jmd.superapp.data.prefs.PreferenceManager]
 * - Android Framework & Jetpack: [android.speech.tts.TextToSpeech], [androidx.compose.foundation.text.KeyboardOptions], [androidx.compose.material.icons.Icons]
 */

package com.jmd.superapp.ui.screen.settings

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.ui.components.*

/**
 * What it is:
 * Operational controls for automation channels: WhatsApp pacing, SMS quotas, multi-SIM preferences, call simulation defaults, TTS voice engine, and auto-reply.
 *
 * What it does:
 * Renders interactive Material 3 UI for [SettingsAutomationChannelsCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [SettingsAutomationChannelsCardComponents], [SettingsAutomationChannelsCard]
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
    var systemClockAlarmSync by remember { mutableStateOf(prefs.isSystemClockAlarmSyncEnabled) }

    // TTS Engine for live testing
    var isTtsReady by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            isTtsReady = (status == TextToSpeech.SUCCESS)
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
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("WhatsApp Dispatch", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = "${waDelay}s Pace", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Adjust organic delay between dispatches and realistic typing simulation.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Dispatch Delay:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val delays = listOf(1, 2, 3, 5, 8, 10)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                delays.forEach { s ->
                    AppChip(
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
            Text("Typing Delay:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val typingDelays = listOf(
                0 to "Instant (0ms)",
                400 to "Natural (400ms)",
                800 to "Human (800ms)",
                1500 to "Deliberate (1.5s)"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                typingDelays.forEach { (ms, label) ->
                    AppChip(
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
            AppToggleRow(
                title = "SMS Fallback",
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
                    Text("SIM Routing", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = if (simSlot == -1) "Default" else "SIM ${simSlot + 1}", color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Select preferred outbound SIM and configure TRAI daily safety quotas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("SIM Slot:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val slots = listOf(
                0 to "SIM 1",
                1 to "SIM 2",
                -1 to "System Default"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                slots.forEach { (slot, label) ->
                    AppChip(
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
            AppToggleRow(
                title = "SMS Quota",
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
                        AppChip(
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
                    Icon(Icons.AutoMirrored.Filled.PhoneForwarded, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Call Simulation", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
            Text("Screen Skin:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val styles = listOf(
                "PIXEL" to "Google Pixel",
                "SAMSUNG" to "Samsung OneUI",
                "WHATSAPP" to "WhatsApp Call"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                styles.forEach { (st, label) ->
                    AppChip(
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
            Text("Ringing Timeout:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val durations = listOf(15, 30, 45, 60)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                durations.forEach { d ->
                    AppChip(
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
                    Text("TTS Voice", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
            AppButton(
                text = "Test Voice",
                onClick = {
                    ttsEngine?.let { engine ->
                        engine.setSpeechRate(ttsRate)
                        engine.setPitch(ttsPitch)
                        engine.speak(
                            "Namaste! This is your voice reminder test with Super.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "test_utterance"
                        )
                    }
                },
                icon = Icons.AutoMirrored.Filled.VolumeUp,
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
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Pool Sync", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = "${autoReplyCooldown}s Throttle", color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Prevent auto-reply message flooding and set periodic address book harvest frequency.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Reply Throttle:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val cooldowns = listOf(10, 30, 60, 120, 300)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                cooldowns.forEach { c ->
                    AppChip(
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
            Text("Harvest Interval:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val syncIntervals = listOf(2, 6, 12, 24)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                syncIntervals.forEach { h ->
                    AppChip(
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

        // 6. Reminder & System Clock Sync
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Clock Sync", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = if (systemClockAlarmSync) "OEM Synced" else "Clean AlarmManager", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Controls whether Reminder tasks are mirrored into the phone's built-in Clock app.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Sync to External Clock App",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (systemClockAlarmSync)
                            "On: Reminders also create an alarm in your device's Clock app."
                            else
                            "Off (Recommended): Reminders trigger exact alarms internally with fullscreen alerts and voice, leaving your device's Clock app clean and unpolluted.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = systemClockAlarmSync,
                    onCheckedChange = { enable ->
                        systemClockAlarmSync = enable
                        prefs.isSystemClockAlarmSyncEnabled = enable
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                )
            }
        }
    }
}
