package com.jmd.helper.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.helper.core.showAppToast
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.service.TaskAccessibilityService
import com.jmd.helper.ui.components.*

/**
 * Configuration card managing screen wake, keyguard PIN input, and post-dispatch hardware lock.
 *
 * @param context Local context for keyguard and device admin validation.
 * @param prefs Preferences manager storing unlock credentials and auto-lock state.
 * @param modifier Optional layout modifier.
 */
@Composable
fun SettingsScreenAssistCard(
    context: Context,
    prefs: PreferenceManager,
    modifier: Modifier = Modifier
) {
    var isScreenAssistEnabled by remember { mutableStateOf(prefs.isScreenAssistEnabled) }
    var assistType by remember { mutableStateOf(prefs.screenAssistType) }
    var assistPin by remember { mutableStateOf(prefs.screenAssistPin) }
    var isAutoLockAfterSend by remember { mutableStateOf(prefs.isAutoLockAfterSendEnabled) }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Text("Screen Task Assistance & Auto-Lock", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Helper can wake the device, assist with lockscreen entry during dispatches, and securely re-lock the device safely.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Screen Assistance When Locked", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Wakes screen and assists entry when scheduled tasks fire", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Switch(
                checked = isScreenAssistEnabled,
                onCheckedChange = {
                    isScreenAssistEnabled = it
                    prefs.isScreenAssistEnabled = it
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            )
        }

        if (isScreenAssistEnabled) {
            Spacer(modifier = Modifier.height(10.dp))

            Text("Screen Lock Type:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "PIN" to "PIN",
                    "PASSWORD" to "Password",
                    "SWIPE" to "Swipe"
                ).forEach { (type, label) ->
                    GlassChip(
                        selected = assistType == type,
                        onClick = {
                            assistType = type
                            prefs.screenAssistType = type
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (assistType == "PIN" || assistType == "PASSWORD") {
                Spacer(modifier = Modifier.height(10.dp))
                val isPinMode = assistType == "PIN"
                GlassInputField(
                    value = assistPin,
                    onValueChange = {
                        assistPin = if (isPinMode) it.filter { ch -> ch.isDigit() } else it
                        prefs.screenAssistPin = assistPin
                    },
                    label = if (isPinMode) "Device Number Lock PIN" else "Device Lock Password",
                    placeholder = "Enter your phone's PIN or Password",
                    isPassword = true,
                    leadingIcon = Icons.Default.Lock,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPinMode) KeyboardType.NumberPassword else KeyboardType.Password
                    ),
                    accentColor = MaterialTheme.colorScheme.primary
                )

                Text(
                    "Biometric Transition: If your device uses fingerprint or face unlock, Helper swipes up to switch to PIN/Password entry for the scheduled task.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Lock Phone After Send", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Locks the screen 2 seconds after message is delivered", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Switch(
                    checked = isAutoLockAfterSend,
                    onCheckedChange = {
                        isAutoLockAfterSend = it
                        prefs.isAutoLockAfterSendEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = "Test Screen Lock",
                    onClick = {
                        context.showAppToast("Locking phone...")
                        TaskAccessibilityService.lockScreenNow()
                    },
                    icon = Icons.Filled.Lock,
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "Test Screen Wake & Unlock",
                    onClick = {
                        val currentPin = assistPin.ifBlank { prefs.screenAssistPin }
                        if (!isScreenAssistEnabled) {
                            isScreenAssistEnabled = true
                            prefs.isScreenAssistEnabled = true
                        }
                        TaskAccessibilityService.testScreenAssist(context, currentPin)
                    },
                    icon = Icons.Filled.LockOpen,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            GlassButton(
                text = "Emergency Stop All Scheduled Tasks",
                onClick = {
                    com.jmd.helper.core.TaskSafetyManager.triggerEmergencyStop(context, "User clicked Emergency Stop in Settings")
                },
                icon = Icons.Filled.StopCircle,
                variant = GlassButtonVariant.DANGER,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Backward-compatible alias for [SettingsScreenAssistCard].
 */
@Composable
fun SettingsAutoUnlockCard(
    context: Context,
    prefs: PreferenceManager,
    modifier: Modifier = Modifier
) {
    SettingsScreenAssistCard(context, prefs, modifier)
}

