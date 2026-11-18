/**
 * Package: com.jmd.superapp.ui.screen.settings
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [SettingsScreenAssistCard].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for SettingsScreenAssistCard.
 *
 * Linked With:
 * - Services & Engines: [com.jmd.superapp.service.TaskAccessibilityService]
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.text.font.FontWeight], [androidx.compose.ui.text.input.KeyboardType]
 * - Core Security & Utilities: [com.jmd.superapp.core.showAppToast], [com.jmd.superapp.data.prefs.PreferenceManager]
 * - Android Framework & Jetpack: [androidx.compose.foundation.text.KeyboardOptions], [androidx.compose.material.icons.Icons], [androidx.compose.material.icons.filled.Lock], [androidx.compose.material.icons.filled.LockOpen]
 */

package com.jmd.superapp.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.superapp.core.AccessibilityHealthManager
import com.jmd.superapp.core.showAppToast
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.service.TaskAccessibilityService
import com.jmd.superapp.ui.components.*

/**
 * What it is:
 * Configuration card managing screen wake, keyguard PIN input, and post-dispatch hardware lock.
 *
 * What it does:
 * Renders interactive Material 3 UI for [SettingsScreenAssistCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [SettingsScreenAssistCard], [SettingsScreenAssistCard]
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
    var showPinPromptDialog by remember { mutableStateOf(false) }
    var promptPinInput by remember { mutableStateOf("") }

    if (showPinPromptDialog) {
        AlertDialog(
            onDismissRequest = { showPinPromptDialog = false },
            title = {
                Text(
                    text = "Configure Unlock",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Enter your phone lock PIN to test auto-unlock, or select Swipe if your device has no password.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AppInputField(
                        value = promptPinInput,
                        onValueChange = { promptPinInput = it.filter { ch -> ch.isDigit() } },
                        label = "Lock PIN",
                        placeholder = "e.g. 1234",
                        isPassword = true,
                        leadingIcon = Icons.Default.Lock,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppButton(
                        text = "Use Swipe",
                        onClick = {
                            assistType = "SWIPE"
                            prefs.screenAssistType = "SWIPE"
                            prefs.isScreenAssistEnabled = true
                            isScreenAssistEnabled = true
                            showPinPromptDialog = false
                            TaskAccessibilityService.testScreenAssist(context, "")
                        },
                        variant = AppButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AppButton(
                        text = "Test PIN",
                        onClick = {
                            if (promptPinInput.isNotBlank()) {
                                assistPin = promptPinInput
                                assistType = "PIN"
                                prefs.screenAssistPin = promptPinInput
                                prefs.screenAssistType = "PIN"
                                prefs.isScreenAssistEnabled = true
                                isScreenAssistEnabled = true
                                showPinPromptDialog = false
                                TaskAccessibilityService.testScreenAssist(context, promptPinInput)
                            } else {
                                context.showAppToast("Enter at least 4 digits")
                            }
                        },
                        variant = AppButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinPromptDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Text("Screen Assist", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Super can wake the device, assist with lockscreen entry during dispatches, and securely re-lock the device safely.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Locked Assist", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

            // Live readiness check: unlock cannot work unless the task service is bound.
            val isServiceBound = AccessibilityHealthManager.isServiceRunning()
            val canSelfEnable = AccessibilityHealthManager.hasWriteSecureSettings(context)
            if (!isServiceBound) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "Unlock is not active: Super Service is off",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            if (canSelfEnable) {
                                "It reconnects automatically on the next scheduled task. Open accessibility settings if you want it always on."
                            } else {
                                "Enable Super under Accessibility settings. Without the ADB secure-settings grant, Android will not let Super reconnect on its own."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure { context.showAppToast("Could not open accessibility settings") }
                            }
                        ) {
                            Text("Open Accessibility", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Text("Lock Type:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                    AppChip(
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
                AppInputField(
                    value = assistPin,
                    onValueChange = {
                        assistPin = if (isPinMode) it.filter { ch -> ch.isDigit() } else it
                        prefs.screenAssistPin = assistPin
                    },
                    label = if (isPinMode) "Lock PIN" else "Lock Password",
                    placeholder = "Enter your phone's PIN or Password",
                    isPassword = true,
                    leadingIcon = Icons.Default.Lock,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPinMode) KeyboardType.NumberPassword else KeyboardType.Password
                    ),
                    accentColor = MaterialTheme.colorScheme.primary
                )

                Text(
                    "Biometric Transition: If your device uses fingerprint or face unlock, Super swipes up to switch to PIN/Password entry for the scheduled task.",
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
                    Text("Auto Lock", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Action: Test Screen Wake & Unlock
            AppButton(
                text = "Test Unlock",
                onClick = {
                    if (!isScreenAssistEnabled) {
                        isScreenAssistEnabled = true
                        prefs.isScreenAssistEnabled = true
                    }
                    val currentPin = assistPin.ifBlank { prefs.screenAssistPin }.trim()
                    if (currentPin.isBlank() && assistType != "SWIPE") {
                        promptPinInput = ""
                        showPinPromptDialog = true
                        return@AppButton
                    }
                    TaskAccessibilityService.testScreenAssist(context, currentPin)
                },
                icon = Icons.Filled.LockOpen,
                variant = AppButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Locks phone now, wakes display in 4 seconds, and executes automated keyguard unlock.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Secondary Action: Test Screen Lock Only
            AppButton(
                text = "Test Lock",
                onClick = {
                    context.showAppToast("Locking phone now...")
                    TaskAccessibilityService.lockScreenNow()
                },
                icon = Icons.Filled.Lock,
                variant = AppButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
