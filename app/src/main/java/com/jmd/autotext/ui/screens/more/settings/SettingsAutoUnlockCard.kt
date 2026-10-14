package com.jmd.autotext.ui.screens.more.settings

import android.content.Context
import android.widget.Toast
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
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun SettingsAutoUnlockCard(
    context: Context,
    prefs: PreferenceManager,
    modifier: Modifier = Modifier
) {
    var isAutoUnlock by remember { mutableStateOf(prefs.isAutoUnlockEnabled) }
    var unlockType by remember { mutableStateOf(prefs.autoUnlockType) }
    var unlockPin by remember { mutableStateOf(prefs.deviceUnlockPin) }
    var isAutoLockAfterSend by remember { mutableStateOf(prefs.isAutoLockAfterSendEnabled) }

    BentoCard(modifier = modifier.fillMaxWidth()) {
        Text("Screen Auto-Unlock & Auto-Lock", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("AutoText can wake the device, enter your screen PIN, dispatch messages, and re-lock the device automatically.", color = BentoGlassTokens.TextMuted, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-Unlock When Locked", color = BentoGlassTokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Bypasses lock screen when scheduled messages fire", color = BentoGlassTokens.TextMuted, fontSize = 11.sp)
            }
            Switch(
                checked = isAutoUnlock,
                onCheckedChange = {
                    isAutoUnlock = it
                    prefs.isAutoUnlockEnabled = it
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BentoGlassTokens.NeonCyan,
                    checkedTrackColor = BentoGlassTokens.NeonCyan.copy(alpha = 0.35f)
                )
            )
        }

        if (isAutoUnlock) {
            Spacer(modifier = Modifier.height(10.dp))

            Text("Screen Lock Type:", color = BentoGlassTokens.TextMuted, fontSize = 12.sp)
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
                        activeColor = BentoGlassTokens.NeonCyan,
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    accentColor = BentoGlassTokens.NeonCyan
                )

                Text(
                    "Fingerprint Auto-Switch: If your device uses fingerprint, AutoText automatically swipes up to switch to number lock and unlocks using this entered PIN/Password.",
                    fontSize = 11.sp,
                    color = BentoGlassTokens.TextMuted,
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
                    Text("Auto-Lock Phone After Send", color = BentoGlassTokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Locks the screen 2 seconds after message is delivered", color = BentoGlassTokens.TextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = isAutoLockAfterSend,
                    onCheckedChange = {
                        isAutoLockAfterSend = it
                        prefs.isAutoLockAfterSendEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BentoGlassTokens.NeonCyan,
                        checkedTrackColor = BentoGlassTokens.NeonCyan.copy(alpha = 0.35f)
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
                        if (AutoTextAccessibilityService.isServiceRunning) {
                            Toast.makeText(context, "Locking phone now...", Toast.LENGTH_SHORT).show()
                            AutoTextAccessibilityService.lockScreenNow()
                        } else {
                            Toast.makeText(context, "Enable Accessibility Service first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    icon = Icons.Default.Lock,
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "Test Screen Unlock",
                    onClick = {
                        val currentPin = unlockPin.ifBlank { prefs.deviceUnlockPin }
                        AutoTextAccessibilityService.testLockAndAutoUnlock(context, currentPin)
                    },
                    icon = Icons.Default.LockOpen,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            GlassButton(
                text = "Emergency Stop All Automation",
                onClick = {
                    AutoTextAccessibilityService.clearPending()
                    Toast.makeText(context, "🛑 All background automation & auto-unlock stopped cleanly", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Default.StopCircle,
                variant = GlassButtonVariant.DANGER,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
