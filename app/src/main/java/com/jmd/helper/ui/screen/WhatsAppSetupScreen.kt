/**
 * Package: com.jmd.helper.ui.screen
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [WhatsAppSetupScreen].
 *
 * What it does:
 * Guided setup wizard for WhatsApp and WhatsApp Business dispatch. Steps through Accessibility service enablement, battery optimization disablement, test ping message verification, lockscreen PIN setup, and permission status checks.
 *
 * Linked With:
 * - Models & Entities: [com.jmd.helper.data.model.*]
 * - Services & Engines: [com.jmd.helper.engine.*], [com.jmd.helper.service.*]
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.text.font.FontWeight], [androidx.compose.ui.text.input.KeyboardType]
 * - Core Security & Utilities: [androidx.core.net.toUri], [com.jmd.helper.core.*]
 * - Android Framework & Jetpack: [android.provider.Settings], [androidx.compose.foundation.lazy.LazyColumn], [androidx.compose.foundation.text.KeyboardOptions], [androidx.compose.material.icons.Icons]
 */

package com.jmd.helper.ui.screen

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

import com.jmd.helper.core.*
import com.jmd.helper.data.model.*
import com.jmd.helper.data.prefs.*
import com.jmd.helper.engine.*
import com.jmd.helper.service.*
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.screen.permission.AutoHealWatchdogCard
import com.jmd.helper.ui.screen.permission.OemAutostartCard
import com.jmd.helper.ui.theme.*


/**
 * What it is:
 * Guided onboarding and troubleshooting view for WhatsApp tasks.
 *
 * What it does:
 * Configures accessibility services, device lockscreen screen assistance PINs, post-dispatch auto-relock policies, battery optimization allowlists, and live end-to-end ping verification.
 *
 * Linked with:
 * - Architecture: [WhatsAppSetupScreen], [WhatsAppSetupAndScreenAssistView]
 * @param context Active Android [Context].
 * @param prefs Storage manager for user preferences.
 * @param onBack Callback invoked to exit setup view.
 */
@Composable
fun WhatsAppSetupAndScreenAssistView(
    context: Context,
    prefs: PreferenceManager,
    onBack: () -> Unit,
) {
    var isScreenAssistEnabled by remember { mutableStateOf(prefs.isScreenAssistEnabled) }
    var unlockType by remember { mutableStateOf(prefs.screenAssistType) }
    var pinCode by remember { mutableStateOf(prefs.screenAssistPin) }
    var isAutoLockAfterSend by remember { mutableStateOf(prefs.isAutoLockAfterSendEnabled) }
    var countryCode by remember { mutableStateOf(prefs.defaultCountryCode) }

    var testPhone by remember { mutableStateOf("") }
    var testMessage by remember { mutableStateOf("") }
    var showPinPromptDialog by remember { mutableStateOf(false) }
    var promptPinInput by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    if (showPinPromptDialog) {
        AlertDialog(
            onDismissRequest = { showPinPromptDialog = false },
            title = {
                Text(
                    text = "Configure Unlock to Test",
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
                    GlassInputField(
                        value = promptPinInput,
                        onValueChange = { promptPinInput = it.filter { ch -> ch.isDigit() } },
                        label = "Screen Lock PIN",
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
                    GlassButton(
                        text = "Use Swipe",
                        onClick = {
                            unlockType = "SWIPE"
                            prefs.screenAssistType = "SWIPE"
                            prefs.isScreenAssistEnabled = true
                            isScreenAssistEnabled = true
                            showPinPromptDialog = false
                            TaskAccessibilityService.testScreenAssist(context, "")
                        },
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Test PIN",
                        onClick = {
                            if (promptPinInput.isNotBlank()) {
                                pinCode = promptPinInput
                                unlockType = "PIN"
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
                        variant = GlassButtonVariant.PRIMARY,
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppSubScreenHeader(
                title = "WhatsApp & Screen Task Assistance",
                subtitle = "Accessibility & task assistance setup",
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
                WhatsAppA11yStatusCard(context = context, prefs = prefs)
            }

            // 2. Android 13/14 "Restricted Settings" Guide Enterprise Card
            item {
                WhatsAppRestrictedSettingsCard(context = context)
            }

            // 2B. Self-Healing & Never-Stop Watchdog Card
            item {
                AutoHealWatchdogCard(context = context)
            }

            // 2C. OEM Background Execution & Autostart Card
            item {
                OemAutostartCard(context = context)
            }

            // 3. Screen Auto-Unlock & Auto-Lock Settings Enterprise Card
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockClock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Screen Task Assistance & Auto-Lock",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Allows Helper to wake your screen, assist with PIN entry when locked, dispatch scheduled tasks, and securely re-lock the screen.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle Screen Assistance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Screen Assistance When Locked", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp)
                            Text("Wakes screen and assists entry for scheduled tasks", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isScreenAssistEnabled,
                            onCheckedChange = {
                                isScreenAssistEnabled = it
                                prefs.isScreenAssistEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            ),
                        )
                    }

                    if (isScreenAssistEnabled) {
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
                                GlassChip(
                                    selected = unlockType == type,
                                    onClick = {
                                        unlockType = type
                                        prefs.screenAssistType = type
                                    },
                                    label = label,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if ((unlockType == "PIN") || (unlockType == "PASSWORD")) {
                            Spacer(modifier = Modifier.height(12.dp))

                            val isPinMode = unlockType == "PIN"
                            GlassInputField(
                                value = pinCode,
                                onValueChange = {
                                    pinCode = if (isPinMode) it.filter { ch -> ch.isDigit() } else it
                                    prefs.screenAssistPin = pinCode
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
                                "Fingerprint / Face Auto-Switch: If your phone uses fingerprint, Helper swipes up to dismiss fingerprint and switch directly to number lock to enter your password.",
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
                    GlassButton(
                        text = "Test Screen Wake & Unlock",
                        onClick = {
                            if (!isScreenAssistEnabled) {
                                isScreenAssistEnabled = true
                                prefs.isScreenAssistEnabled = true
                            }
                            val currentPin = pinCode.ifBlank { prefs.screenAssistPin }.trim()
                            if (currentPin.isBlank() && prefs.screenAssistType != "SWIPE") {
                                promptPinInput = pinCode
                                showPinPromptDialog = true
                                return@GlassButton
                            }
                            TaskAccessibilityService.testScreenAssist(context, currentPin)
                        },
                        icon = Icons.Default.LockOpen,
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Locks phone now, wakes display in 4 seconds, and executes automated keyguard unlock.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    GlassButton(
                        text = "Test Screen Lock Only",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AccessibilityHealthManager.ensureServiceAlive(context)
                                }
                                context.showAppToast("Locking screen now...")
                                TaskAccessibilityService.lockScreenNow()
                            }
                        },
                        icon = Icons.Default.Lock,
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 4. Default Country Code Enterprise Card
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Default Country Code", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "WhatsApp requires international dial codes. If phone numbers are entered without country code (e.g. 10 digits), Helper directly prefixes this country code.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    GlassInputField(
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
                WhatsAppBatteryOptimizationCard(context = context)
            }

            // 6. Live Test WhatsApp Task Enterprise Card
            item {
                WhatsAppLiveTestCard(
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
 * What it is:
 * Diagnostic card presenting current Accessibility service status with direct action intent.
 *
 * What it does:
 * Renders interactive Material 3 UI for [WhatsAppA11yStatusCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [WhatsAppSetupScreen], [WhatsAppA11yStatusCard]
 * @param context Active Android [Context].
 * @param prefs Application [PreferenceManager].
 */
@Composable
fun WhatsAppA11yStatusCard(context: Context, prefs: PreferenceManager) {
    val isRunning = AccessibilityHealthManager.isServiceOperational()
    val isSettingOn = AccessibilityHealthManager.isAccessibilitySettingEnabled(context)
    val hasWriteSecure = AccessibilityHealthManager.hasWriteSecureSettings(context)
    val isDynamicStandby = hasWriteSecure && prefs.isDynamicA11yOnDemandEnabled

    var statusState by remember {
        mutableStateOf(
            Triple(
                isRunning || isSettingOn || isDynamicStandby,
                isRunning,
                isDynamicStandby
            )
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            val r = AccessibilityHealthManager.isServiceOperational()
            val s = AccessibilityHealthManager.isAccessibilitySettingEnabled(context)
            val ws = AccessibilityHealthManager.hasWriteSecureSettings(context)
            val ds = ws && prefs.isDynamicA11yOnDemandEnabled
            statusState = Triple(r || s || ds, r, ds)
            delay(1500L)
        }
    }

    val (isOperational, isCurrentlyRunning, isStandby) = statusState

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (isOperational) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        borderColor = if (isOperational) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isOperational) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isOperational) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = when {
                        isCurrentlyRunning -> "Helper Assistant: Connected (Bank-Shield Active)"
                        isStandby -> "Helper Assistant: Dynamic Standby (Bank-Safe)"
                        isOperational -> "Helper Assistant: Enabled in System"
                        else -> "Helper Assistant: Setup Required"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isOperational) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = when {
                        isCurrentlyRunning -> "Active in background. Banking and payment apps are strictly isolated."
                        isStandby -> "Connects on demand when tasks run. 100% banking and payment app compatibility."
                        isOperational -> "Accessibility enabled. Ready for scheduled message delivery."
                        else -> "Enable Helper Assistant in Settings > Accessibility > Installed apps."
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassButton(
            text = if (isOperational) "Open Accessibility Settings" else "Turn ON in Accessibility Settings",
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            },
            icon = Icons.Default.AccessibilityNew,
            variant = if (isOperational) GlassButtonVariant.SUCCESS else GlassButtonVariant.DANGER,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


/**
 * What it is:
 * Guidance card for Android 13+ sideloaded restricted settings resolution.
 *
 * What it does:
 * Directs users on how to allow restricted settings in App Info before enabling Accessibility.
 *
 * Linked with:
 * - Architecture: [WhatsAppSetupScreen], [WhatsAppRestrictedSettingsCard]
 * @param context Active Android [Context].
 */
@Composable
fun WhatsAppRestrictedSettingsCard(context: Context) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
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
                    "5. Return here and enable Accessibility!\n\n" +
                    "• Samsung tip: In Settings > Accessibility, tap 'Installed apps' to locate Helper Assistant.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassButton(
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
 * What it is:
 * Battery optimization guidance card helping users exempt Helper from background process freezing.
 *
 * What it does:
 * Renders interactive Material 3 UI for [WhatsAppBatteryOptimizationCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [WhatsAppSetupScreen], [WhatsAppBatteryOptimizationCard]
 * @param context Active Android [Context].
 */
@Composable
fun WhatsAppBatteryOptimizationCard(context: Context) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text("Battery & Background Restrictions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "To ensure tasks trigger on time when phone is locked:\n" +
                    "• Exempt Helper from battery optimization.\n" +
                    "• On Samsung: Device Care -> Battery -> Background usage limits -> Never sleeping apps -> Add Helper.\n" +
                    "• On Xiaomi / Vivo / Oppo: Enable 'Autostart' in App settings.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlassButton(
            text = "Exempt From Battery Optimization",
            onClick = {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    com.jmd.helper.core.AppFileLogger.w("WhatsAppSetupScreen", "Could not open battery optimization settings: ${e.message}")
                    context.showAppToast("Unable to open battery optimization settings directly. Please check device Settings.")
                }
            },
            icon = Icons.Default.BatteryChargingFull,
            variant = GlassButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


/**
 * What it is:
 * Interactive end-to-end testing card enabling instant test dispatch to any phone number.
 *
 * What it does:
 * Renders interactive Material 3 UI for [WhatsAppLiveTestCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [WhatsAppSetupScreen], [WhatsAppLiveTestCard]
 * @param context Active Android [Context].
 * @param testPhone Destination phone number input.
 * @param onTestPhoneChange Callback invoked as test phone number changes.
 * @param testMessage Test message payload string.
 * @param onTestMessageChange Callback invoked as test message content changes.
 */
@Composable
fun WhatsAppLiveTestCard(
    context: Context,
    testPhone: String,
    onTestPhoneChange: (String) -> Unit,
    testMessage: String,
    onTestMessageChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Live WhatsApp Send Test", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Test sending a WhatsApp message right now. Lock your screen right after tapping if you want to test screen assistance!", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(10.dp))

        GlassInputField(
            value = testPhone,
            onValueChange = onTestPhoneChange,
            label = "Phone Number",
            placeholder = "e.g. 9876543210",
            leadingIcon = Icons.Default.Phone,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            accentColor = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(8.dp))

        GlassInputField(
            value = testMessage,
            onValueChange = onTestMessageChange,
            label = "Message Text",
            placeholder = "Type test message...",
            leadingIcon = Icons.Default.EditNote,
            accentColor = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(12.dp))

        GlassButton(
            text = "Dispatch Test WhatsApp Now",
            onClick = {
                if (!WhatsAppPackageHelper.isWhatsAppInstalled(context)) {
                    context.showAppToast("WhatsApp is not installed on this device")
                    return@GlassButton
                }
                if (testPhone.trim().isEmpty()) {
                    context.showAppToast("Please enter a phone number")
                    return@GlassButton
                }
                val messageToSend = testMessage.trim().ifEmpty { "Helper Test Ping: Task is active!" }
                scope.launch {
                    val mutex = SequentialTaskEngine.getUiTaskMutex(context)
                    val success = mutex.withLock {
                        withContext(Dispatchers.IO) {
                            val sender = WhatsAppSender(context)
                            sender.send(
                                taskId = 9999L,
                                channel = ChannelType.WHATSAPP,
                                recipientPhone = testPhone.trim(),
                                messageText = messageToSend
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
