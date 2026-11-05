package com.jmd.autotext.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Configuration card managing automated screen wake, keyguard PIN input, and post-dispatch hardware lock.
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
    var isAutoUnlock by remember { mutableStateOf(prefs.isScreenAssistEnabled) }
    var unlockType by remember { mutableStateOf(prefs.screenAssistType) }
    var unlockPin by remember { mutableStateOf(prefs.screenAssistPin) }
    var isAutoLockAfterSend by remember { mutableStateOf(prefs.isAutoLockAfterSendEnabled) }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Text("Screen Task Assistance & Auto-Lock", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("AutoText can wake the device, assist with lockscreen entry during dispatches, and securely re-lock the device automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

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
                checked = isAutoUnlock,
                onCheckedChange = {
                    isAutoUnlock = it
                    prefs.isScreenAssistEnabled = it
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            )
        }

        if (isAutoUnlock) {
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
                        selected = unlockType == type,
                        onClick = {
                            unlockType = type
                            prefs.autoUnlockType = type
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (unlockType == "PIN" || unlockType == "PASSWORD") {
                Spacer(modifier = Modifier.height(10.dp))
                val isPinMode = unlockType == "PIN"
                GlassInputField(
                    value = unlockPin,
                    onValueChange = {
                        unlockPin = if (isPinMode) it.filter { ch -> ch.isDigit() } else it
                        prefs.deviceUnlockPin = unlockPin
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
                    "Biometric Transition: If your device uses fingerprint or face unlock, AutoText automatically swipes up to switch to PIN/Password entry for the scheduled task.",
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
                        AutoTextAccessibilityService.lockScreenNow()
                    },
                    emoji = "🔒",
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "Test Screen Wake & Unlock",
                    onClick = {
                        val currentPin = unlockPin.ifBlank { prefs.deviceUnlockPin }
                        if (!isAutoUnlock) {
                            isAutoUnlock = true
                            prefs.isAutoUnlockEnabled = true
                        }
                        AutoTextAccessibilityService.testLockAndAutoUnlock(context, currentPin)
                    },
                    emoji = "🔓",
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            GlassButton(
                text = "Emergency Stop All Scheduled Tasks",
                onClick = {
                    AutoTextAccessibilityService.clearPending()
                    context.showAppToast("Tasks stopped")
                },
                emoji = "🛑",
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

