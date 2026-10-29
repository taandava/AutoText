

/**
 * Package: com.jmd.autotext.ui.screen
 * Module: UI WhatsApp Automation Onboarding Layer
 *
 * Guided setup wizard for WhatsApp and WhatsApp Business automation.
 * Steps through Accessibility service enablement, battery optimization disablement,
 * test ping message verification, lockscreen PIN setup, and permission status checks.
 *
 * Linked With:
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Health: [com.jmd.autotext.core.AccessibilityHealthManager]
 * - Components: `GlassComponentsKt`
 */
package com.jmd.autotext.ui.screen

import android.content.*
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.core.net.toUri
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

import com.jmd.autotext.core.*
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.prefs.*
import com.jmd.autotext.engine.*
import com.jmd.autotext.service.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*


/**
 * Guided onboarding and troubleshooting view for WhatsApp automation.
 *
 * Configures accessibility services, device lockscreen auto-unlock PINs, post-dispatch
 * auto-relock policies, battery optimization whitelists, and live end-to-end ping verification.
 *
 * @param context Active Android [Context].
 * @param prefs Storage manager for user preferences.
 * @param onBack Callback invoked to exit setup view.
 */
@Composable
fun whatsAppSetupAndAutoUnlockView(
    context: Context,
    prefs: PreferenceManager,
    onBack: () -> Unit,
) {
    var isAutoUnlock by remember { mutableStateOf(prefs.isAutoUnlockEnabled) }
    var unlockType by remember { mutableStateOf(prefs.autoUnlockType) }
    var pinCode by remember { mutableStateOf(prefs.deviceUnlockPin) }
    var isAutoLockAfterSend by remember { mutableStateOf(prefs.isAutoLockAfterSendEnabled) }
    var countryCode by remember { mutableStateOf(prefs.defaultCountryCode) }

    var testPhone by remember { mutableStateOf("") }
    var testMessage by remember { mutableStateOf("") }

    val isA11yActive = AccessibilityHealthManager.isServiceOperational()
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = "WhatsApp & Screen Auto-Unlock",
                subtitle = "Accessibility & automation setup",
                onBack = onBack
            )
        },
        containerColor = AppDesignTokens.VoidBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Accessibility Service Live Status Enterprise Card
            item {
                whatsAppA11yStatusCard(context = context, isA11yActive = isA11yActive)
            }

            // 2. Android 13/14 "Restricted Settings" Guide Enterprise Card
            item {
                whatsAppRestrictedSettingsCard(context = context)
            }

            // 2B. Self-Healing & Never-Stop Watchdog Card
            item {
                superAutoHealAndPermissionFixerCard(context)
            }

            // 2C. Keep Open in Recent Apps Card
            item {
                keepOpenRecentAppsCard()
            }

            // 3. Screen Auto-Unlock & Auto-Lock Settings Enterprise Card
            item {
                appCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockClock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Screen Auto-Unlock & Auto-Lock",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Allows AutoText to wake your screen, enter your device PIN when locked, send scheduled messages, and automatically lock the screen back.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle Auto-Unlock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Unlock When Locked", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp)
                            Text("Wakes screen and unlocks device automatically", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isAutoUnlock,
                            onCheckedChange = {
                                isAutoUnlock = it
                                prefs.isAutoUnlockEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            ),
                        )
                    }

                    if (isAutoUnlock) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Screen Lock Type:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
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
                                glassChip(
                                    selected = unlockType == type,
                                    onClick = {
                                        unlockType = type
                                        prefs.autoUnlockType = type
                                    },
                                    label = label,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if ((unlockType == "PIN") || (unlockType == "PASSWORD")) {
                            Spacer(modifier = Modifier.height(12.dp))

                            val isPinMode = unlockType == "PIN"
                            glassInputField(
                                value = pinCode,
                                onValueChange = {
                                    pinCode = if (isPinMode) it.filter { ch -> ch.isDigit() } else it
                                    prefs.deviceUnlockPin = pinCode
                                },
                                label = if (isPinMode) "Device Number Lock PIN" else "Device Lock Password",
                                placeholder = if (isPinMode) "e.g. 1234" else "Enter lock password",
                                isPassword = true,
                                leadingIcon = Icons.Default.Lock,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (isPinMode) KeyboardType.NumberPassword else KeyboardType.Password
                                ),
                                accentColor = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                "Fingerprint / Face Auto-Switch: If your phone uses fingerprint, AutoText automatically swipes up to dismiss fingerprint and switch directly to number lock to enter your password.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Auto Lock After Send Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Lock Phone After Send", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp)
                                Text("Re-locks the screen 2s after message is sent", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Screen Lock & Auto-Unlock Testing Hub
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        glassButton(
                            text = "Test Screen Lock",
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AccessibilityHealthManager.ensureServiceAlive(context)
                                    }
                                    context.showAppToast("Locking screen now...")
                                    AutoTextAccessibilityService.lockScreenNow()
                                }
                            },
                            icon = Icons.Default.Lock,
                            variant = GlassButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                        glassButton(
                            text = "Test Screen Unlock",
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AccessibilityHealthManager.ensureServiceAlive(context)
                                    }
                                    val currentPin = pinCode.ifBlank { prefs.deviceUnlockPin }
                                    AutoTextAccessibilityService.testLockAndAutoUnlock(context, currentPin)
                                }
                            },
                            icon = Icons.Default.LockOpen,
                            variant = GlassButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    glassButton(
                        text = "Emergency Stop All Automation",
                        onClick = {
                            AutoTextAccessibilityService.clearPending()
                            context.showAppToast("Automation stopped")
                        },
                        icon = Icons.Default.StopCircle,
                        variant = GlassButtonVariant.DANGER,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 4. Default Country Code Enterprise Card
            item {
                appCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Default Country Code", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "WhatsApp requires international dial codes. If phone numbers are entered without country code (e.g. 10 digits), AutoText automatically prefixes this country code.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    glassInputField(
                        value = countryCode,
                        onValueChange = {
                            countryCode = it.filter { ch -> ch.isDigit() }
                            prefs.defaultCountryCode = countryCode
                        },
                        label = "Country Code (e.g. 91 for India, 1 for US)",
                        placeholder = "91",
                        leadingIcon = Icons.Default.Public,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 5. Battery Optimization & Background Guide Enterprise Card
            item {
                whatsAppBatteryOptimizationCard(context = context)
            }

            // 6. Live Test WhatsApp Automation Enterprise Card
            item {
                whatsAppLiveTestCard(
                    context = context,
                    testPhone = testPhone,
                    onTestPhoneChange = { testPhone = it },
                    testMessage = testMessage
                ) { newTestMessage -> testMessage = newTestMessage }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// ============================================================================
// ACCESSIBILITY SETUP GUIDE CARDS
// ============================================================================

/**
 * Diagnostic card presenting current Accessibility service status with direct action intent.
 *
 * @param context Active Android [Context].
 * @param isA11yActive True if service is currently running and verified via heartbeat.
 */
@Composable
fun whatsAppA11yStatusCard(context: Context, isA11yActive: Boolean) {
    appCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (isA11yActive) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        borderColor = if (isA11yActive) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isA11yActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isA11yActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isA11yActive) "Accessibility Service Active" else "Accessibility Service Inactive",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isA11yActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (isA11yActive) "Ready to auto-send WhatsApp and auto-unlock" else "Required for WhatsApp sending and PIN auto-unlock",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        glassButton(
            text = if (isA11yActive) "Accessibility Settings" else "Turn ON Accessibility Service",
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            },
            icon = Icons.Default.AccessibilityNew,
            variant = if (isA11yActive) GlassButtonVariant.SUCCESS else GlassButtonVariant.DANGER,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


/**
 * Guidance card for Android 13+ sideloaded restricted settings resolution.
 *
 * Directs users on how to allow restricted settings in App Info before enabling Accessibility.
 *
 * @param context Active Android [Context].
 */
@Composable
fun whatsAppRestrictedSettingsCard(context: Context) {
    appCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Android 13 / 14: Sideloaded App Fix",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "If turning on Accessibility says \"Restricted setting - For your security, this setting is unavailable\":\n\n" +
                    "1. Tap 'Open App Info' button below.\n" +
                    "2. In the top-right corner, tap the 3 dots (⋮).\n" +
                    "3. Tap 'Allow restricted settings'.\n" +
                    "4. Enter your phone PIN/fingerprint.\n" +
                    "5. Return here and enable Accessibility!",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        glassButton(
            text = "Open App Info (Tap 3 Dots)",
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            },
            icon = Icons.Default.Settings,
            variant = GlassButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


/**
 * Battery optimization guidance card helping users exempt AutoText from background process freezing.
 *
 * @param context Active Android [Context].
 */
@Composable
fun whatsAppBatteryOptimizationCard(context: Context) {
    appCard(modifier = Modifier.fillMaxWidth()) {
        Text("Battery & Background Restrictions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "To ensure tasks trigger on time when phone is locked:\n" +
                    "• Exempt AutoText from battery optimization.\n" +
                    "• On Samsung: Device Care -> Battery -> Background usage limits -> Never sleeping apps -> Add AutoText.\n" +
                    "• On Xiaomi / Vivo / Oppo: Enable 'Autostart' in App settings.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        glassButton(
            text = "Exempt From Battery Optimization",
            onClick = {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            },
            icon = Icons.Default.BatteryChargingFull,
            variant = GlassButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


/**
 * Interactive end-to-end testing card enabling instant test dispatch to any phone number.
 *
 * @param context Active Android [Context].
 * @param testPhone Destination phone number input.
 * @param onTestPhoneChange Callback invoked as test phone number changes.
 * @param testMessage Test message payload string.
 * @param onTestMessageChange Callback invoked as test message content changes.
 */
@Composable
fun whatsAppLiveTestCard(
    context: Context,
    testPhone: String,
    onTestPhoneChange: (String) -> Unit,
    testMessage: String,
    onTestMessageChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    appCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Live WhatsApp Send Test", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Test sending a WhatsApp message right now. Lock your screen right after tapping if you want to test auto-unlock!", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(10.dp))

        glassInputField(
            value = testPhone,
            onValueChange = onTestPhoneChange,
            label = "Phone Number",
            placeholder = "e.g. 9876543210",
            leadingIcon = Icons.Default.Phone,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            accentColor = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(8.dp))

        glassInputField(
            value = testMessage,
            onValueChange = onTestMessageChange,
            label = "Message Text",
            placeholder = "Type test message...",
            leadingIcon = Icons.Default.EditNote,
            accentColor = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(12.dp))

        glassButton(
            text = "Dispatch Test WhatsApp Now",
            onClick = {
                if (!WhatsAppPackageHelper.isWhatsAppInstalled(context)) {
                    context.showAppToast("WhatsApp is not installed on this device")
                    return@glassButton
                }
                if (testPhone.trim().isEmpty()) {
                    context.showAppToast("Please enter a phone number")
                    return@glassButton
                }
                scope.launch {
                    val mutex = SequentialTaskEngine.getUiAutomationMutex(context)
                    val success = mutex.withLock {
                        withContext(Dispatchers.IO) {
                            val sender = WhatsAppAutomationSender(context)
                            sender.send(
                                taskId = 9999L,
                                channel = ChannelType.WHATSAPP,
                                recipientPhone = testPhone.trim(),
                                messageText = testMessage.trim()
                            )
                        }
                    }
                    if (success) {
                        context.showAppToast("WhatsApp dispatched")
                    } else {
                        context.showAppToast("Failed to launch WhatsApp.")
                    }
                }
            },
            icon = Icons.Default.PlayArrow,
            variant = GlassButtonVariant.SUCCESS,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

