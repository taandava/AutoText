/**
 * Package: com.jmd.helper.ui.screen
 * Module: UI Permission & Health Screen Layer
 *
 * Material 3 Expressive System Health Hub presenting real-time privilege states:
 * - Accessibility Service (Task Engine)
 * - Notification Listener Service
 * - Exact Alarm privileges
 * - Battery optimization exemption
 * - Overlay permission
 * - Device Admin privileges
 * - Runtime permissions (SMS, Phone, Contacts, Media)
 * - Sound & DND Policy Access
 *
 * Linked With:
 * - Health Manager: [com.jmd.helper.core.AccessibilityHealthManager]
 * - Overlay Helper: [com.jmd.helper.core.OverlayPermissionHelper]
 * - Components: [com.jmd.helper.ui.components.AppGroupCard], [com.jmd.helper.ui.components.AppGroupRow], [com.jmd.helper.ui.components.GlassButton]
 * - Design System: [com.jmd.helper.ui.theme.AppSemanticColors], [com.jmd.helper.ui.theme.AppLayoutGrid]
 */
package com.jmd.helper.ui.screen

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.jmd.helper.core.*
import com.jmd.helper.receiver.HelperDeviceAdminReceiver
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.screen.permission.*
import com.jmd.helper.ui.theme.AppLayoutGrid
import com.jmd.helper.ui.theme.AppSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Material 3 Expressive System Health & Permission Center.
 *
 * @param context Android [Context] for system managers and settings intent dispatch.
 * @param onBack Callback invoked when user navigates back.
 */
@Composable
fun PermissionCenterView(context: Context, onBack: () -> Unit) {
    var showA11yDisclosureDialog by remember { mutableStateOf(false) }
    var showAdbSetupDialog by remember { mutableStateOf(false) }
    var permissionsRefreshKey by remember { mutableIntStateOf(0) }

    val coreRuntimePermissions = remember {
        buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            addAll(PermissionManager.getRequiredMediaPermissions())
            add(Manifest.permission.ANSWER_PHONE_CALLS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionsRefreshKey++
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionsRefreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Live permission states
    val a11yState = remember(permissionsRefreshKey) { AccessibilityHealthManager.getServiceState(context) }
    val isA11yActive = a11yState == AccessibilityHealthManager.ServiceState.OPERATIONAL ||
            a11yState == AccessibilityHealthManager.ServiceState.STANDBY

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val canExactAlarm = remember(permissionsRefreshKey) {
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) || (alarmManager?.canScheduleExactAlarms() == true)
    }

    val hasNotifListener = remember(permissionsRefreshKey) {
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isIgnoringBattery = remember(permissionsRefreshKey) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    val hasOverlay = remember(permissionsRefreshKey) { OverlayPermissionHelper.canDrawOverlays(context) }
    val isAdminActive = remember(permissionsRefreshKey) { HelperDeviceAdminReceiver.isAdminActive(context) }
    val hasDnd = remember(permissionsRefreshKey) { PermissionManager.hasNotificationPolicyAccess(context) }

    val hasRuntimePerms = remember(permissionsRefreshKey) {
        PermissionManager.hasContactsPermission(context) &&
            PermissionManager.hasSmsSendPermission(context) &&
            PermissionManager.hasCallPhonePermission(context) &&
            PermissionManager.hasPhoneStatePermission(context) &&
            PermissionManager.hasCallLogPermission(context) &&
            PermissionManager.hasMediaPermission(context) &&
            PermissionManager.hasPostNotificationsPermission(context)
    }

    val totalPermissions = 8
    val grantedCount = listOf(
        isA11yActive,
        canExactAlarm,
        hasNotifListener,
        isIgnoringBattery,
        hasOverlay,
        isAdminActive,
        hasDnd,
        hasRuntimePerms
    ).count { it }

    val coreCount = listOf(isA11yActive, canExactAlarm, hasNotifListener).count { it }
    val bgCount = listOf(isIgnoringBattery, true).count { it }
    val deviceCount = listOf(true, hasOverlay, isAdminActive, hasRuntimePerms, hasDnd).count { it }

    // Dialogs
    if (showA11yDisclosureDialog) {
        A11yDisclosureDialog(
            context = context,
            onDismiss = { showA11yDisclosureDialog = false }
        )
    }

    if (showAdbSetupDialog) {
        AdbSetupModalDialog(
            context = context,
            onDismiss = { showAdbSetupDialog = false }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppSubScreenHeader(
                title = "Permission Center",
                subtitle = "System access & automated health",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppLayoutGrid.SpaceMedium),
            contentPadding = PaddingValues(
                top = AppLayoutGrid.SpaceSmall,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + AppLayoutGrid.SpaceSection
            ),
            verticalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceMedium),
        ) {
            // 1. Hero Health Dashboard Card
            item {
                PermissionHeroCard(
                    context = context,
                    grantedCount = grantedCount,
                    totalCount = totalPermissions,
                    isA11yActive = isA11yActive,
                    canExactAlarm = canExactAlarm,
                    hasNotifListener = hasNotifListener,
                    isIgnoringBattery = isIgnoringBattery,
                    onOpenAdbDialog = { showAdbSetupDialog = true },
                    onHealDone = { permissionsRefreshKey++ }
                )
            }

            // 2. Group 1: Core Automation Engine
            item {
                PermissionSectionHeader(
                    title = "Core Automation Engine",
                    subtitle = "Foundational services powering scheduled execution",
                    grantedCount = coreCount,
                    totalCount = 3
                )
            }

            item {
                AppGroupCard {
                    // Row 1: Accessibility Task Service
                    val a11yTitle = when (a11yState) {
                        AccessibilityHealthManager.ServiceState.OPERATIONAL -> "Task Service: Active"
                        AccessibilityHealthManager.ServiceState.STANDBY -> "Task Service: Bank-Safe Standby"
                        AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND -> "Task Service: Connecting"
                        AccessibilityHealthManager.ServiceState.DISABLED -> "Task Accessibility Service"
                    }
                    val a11ySubtitle = when (a11yState) {
                        AccessibilityHealthManager.ServiceState.OPERATIONAL -> "Active in background to dispatch scheduled messaging tasks."
                        AccessibilityHealthManager.ServiceState.STANDBY -> "Bank-Safe Standby: Operates on-demand for tasks only."
                        AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND -> "Enabled in settings, connecting background binder."
                        AccessibilityHealthManager.ServiceState.DISABLED -> "Required to dispatch scheduled messaging tasks hands-free."
                    }
                    AppGroupRow(
                        title = a11yTitle,
                        subtitle = a11ySubtitle,
                        icon = Icons.Default.VerifiedUser,
                        iconTint = if (isA11yActive) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                        iconContainerColor = if (isA11yActive) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        trailingContent = {
                            if (isA11yActive) {
                                PermissionStatusActiveChip()
                            } else {
                                PermissionGrantActionButton(
                                    text = if (a11yState == AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND) "Toggle" else "Enable",
                                    onClick = { showA11yDisclosureDialog = true }
                                )
                            }
                        },
                        detailedPurpose = "Allows Helper to interact with approved messaging applications at user-scheduled times to populate and dispatch tasks. Never reads personal data or interacts with banking apps.",
                        onSettingsClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    )

                    AppGroupDivider()

                    // Row 2: Exact Alarms
                    AppGroupRow(
                        title = if (canExactAlarm) "Exact Alarms & Triggers: Active" else "Schedule Exact Alarms",
                        subtitle = "Fires scheduled tasks at the exact specified second without battery jitter.",
                        icon = Icons.Default.Alarm,
                        iconTint = if (canExactAlarm) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                        iconContainerColor = if (canExactAlarm) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        trailingContent = {
                            if (canExactAlarm) {
                                PermissionStatusActiveChip()
                            } else {
                                PermissionGrantActionButton(
                                    text = "Grant",
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                data = "package:${context.packageName}".toUri()
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            })
                                        }
                                    }
                                )
                            }
                        },
                        detailedPurpose = "Required on Android 12+ (API 31+) to invoke high-precision RTC alarms, ensuring time-critical tasks trigger promptly without system delay.",
                        onSettingsClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = "package:${context.packageName}".toUri()
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            }
                        }
                    )

                    AppGroupDivider()

                    // Row 3: Notification Listener
                    AppGroupRow(
                        title = if (hasNotifListener) "Notification Listener: Active" else "Notification Listener Access",
                        subtitle = "Detects incoming chats for automated rules and quick responses.",
                        icon = Icons.Default.NotificationsActive,
                        iconTint = if (hasNotifListener) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                        iconContainerColor = if (hasNotifListener) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        trailingContent = {
                            if (hasNotifListener) {
                                PermissionStatusActiveChip()
                            } else {
                                PermissionGrantActionButton(
                                    text = "Enable",
                                    onClick = {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        })
                                    }
                                )
                            }
                        },
                        detailedPurpose = "Enables Helper to read notification actions in messaging apps to process auto-replies directly in the background without needing to illuminate the display.",
                        onSettingsClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    )
                }
            }

            // 3. Group 2: Background Reliability & Battery
            item {
                PermissionSectionHeader(
                    title = "Background Reliability",
                    subtitle = "Prevent OS task termination and background throttling",
                    grantedCount = bgCount,
                    totalCount = 2
                )
            }

            item {
                AppGroupCard {
                    // Row 1: Battery Saver Exemption
                    AppGroupRow(
                        title = if (isIgnoringBattery) "Battery Saver: Exempted" else "Battery Saver Exemption",
                        subtitle = "Prevents system power savers from freezing scheduled timers.",
                        icon = Icons.Default.BatteryChargingFull,
                        iconTint = if (isIgnoringBattery) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                        iconContainerColor = if (isIgnoringBattery) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        trailingContent = {
                            if (isIgnoringBattery) {
                                PermissionStatusActiveChip()
                            } else {
                                PermissionGrantActionButton(
                                    text = "Exempt",
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            })
                                        } catch (e: Exception) {
                                            AppFileLogger.w("PermissionScreen", "Failed to launch battery settings: ${e.message}")
                                            context.showAppToast("Unable to open battery optimization settings directly")
                                        }
                                    }
                                )
                            }
                        },
                        detailedPurpose = "Adds Helper to the system battery optimization allowlist, ensuring background task schedulers are never frozen during Doze mode.",
                        onSettingsClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            } catch (e: Exception) {
                                context.showAppToast("Unable to open battery optimization settings directly")
                            }
                        }
                    )

                    AppGroupDivider()

                    // Row 2: OEM Autostart Guidance
                    val brandSummary = remember { OemBrandHelper.getDeviceSummary() }
                    val brandGuidance = remember { OemBrandHelper.getOemGuidance() }
                    var oemExpanded by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { oemExpanded = !oemExpanded }
                            .padding(horizontal = AppLayoutGrid.SpaceMedium, vertical = AppLayoutGrid.SpaceStandard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AppLayoutGrid.MinTouchTarget),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(AppLayoutGrid.SquircleCornerRadius)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(AppLayoutGrid.SpaceStandard))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "OEM Autostart ($brandSummary)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceMicro))
                                Text(
                                    text = "Hardware settings to prevent background kills",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (oemExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .scale(1f, if (oemExpanded) -1f else 1f)
                            )
                        }

                        // Guidance steps
                        AnimatedVisibility(
                            visible = oemExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = AppLayoutGrid.SpaceSmall, start = 52.dp),
                                verticalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceXS)
                            ) {
                                brandGuidance.forEach { step ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "• ",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = step,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceSmall))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceSmall)
                                ) {
                                    GlassButton(
                                        text = "Open Auto-Start",
                                        onClick = {
                                            if (!OemBrandHelper.openOemBackgroundSettings(context)) {
                                                context.showAppToast("Unable to open OEM autostart settings directly")
                                            }
                                        },
                                        icon = Icons.Default.Settings,
                                        variant = GlassButtonVariant.PRIMARY,
                                        modifier = Modifier.weight(1f)
                                    )
                                    GlassButton(
                                        text = "Special Overlays",
                                        onClick = {
                                            if (!OverlayPermissionHelper.launchOverlaySettings(context)) {
                                                context.showAppToast("Unable to open overlay settings directly")
                                            }
                                        },
                                        icon = Icons.Default.Layers,
                                        variant = GlassButtonVariant.SECONDARY,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    AppGroupDivider()

                    // Row 3: Sideloaded App Guidance
                    AppGroupRow(
                        title = "Sideloaded App Guidance (Android 13+)",
                        subtitle = "If settings are greyed out: App Info -> 3 dots -> Allow restricted settings.",
                        icon = Icons.Default.Security,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        trailingContent = {
                            PermissionGrantActionButton(
                                text = "App Info",
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = "package:${context.packageName}".toUri()
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        },
                        detailedPurpose = "On Android 13+, sideloaded APKs have accessibility and notification permissions restricted by default. Tapping the three dots in App Info unlocks full access.",
                        onSettingsClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // 4. Group 3: Device Controls & Communications
            item {
                PermissionSectionHeader(
                    title = "Device Controls & Communications",
                    subtitle = "Screen locking, overlays, and communication features",
                    grantedCount = deviceCount,
                    totalCount = 5
                )
            }

            item {
                AppGroupCard {
                    // Row 1: Biometric Safe Power Button Lock
                    AppGroupRow(
                        title = "Power Button Auto-Lock",
                        subtitle = "Hardware lock simulation preserving biometric credentials.",
                        icon = Icons.Default.LockClock,
                        iconTint = AppSemanticColors.success,
                        iconContainerColor = AppSemanticColors.successContainer,
                        trailingContent = { PermissionStatusActiveChip() },
                        detailedPurpose = "Helper uses the system power button simulation (GLOBAL_ACTION_LOCK_SCREEN) to turn off the screen post-dispatch without forcing hardware PIN lockout."
                    )

                    AppGroupDivider()

                    // Row 2: Display Over Other Apps
                    AppGroupRow(
                        title = if (hasOverlay) "Display Over Other Apps: Granted" else "Display Over Other Apps",
                        subtitle = "Renders privacy shields, countdown overlays, and incoming alerts.",
                        icon = Icons.Default.Layers,
                        iconTint = if (hasOverlay) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                        iconContainerColor = if (hasOverlay) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        trailingContent = {
                            if (hasOverlay) {
                                PermissionStatusActiveChip()
                            } else {
                                PermissionGrantActionButton(
                                    text = "Grant",
                                    onClick = {
                                        if (!OverlayPermissionHelper.launchOverlaySettings(context)) {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = "package:${context.packageName}".toUri()
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                            }
                        },
                        detailedPurpose = "Required to render transient privacy overlays during automated screen actions and display simulated call interfaces.",
                        onSettingsClick = {
                            OverlayPermissionHelper.launchOverlaySettings(context)
                        }
                    )

                    AppGroupDivider()

                    // Row 3: Device Administration
                    AppGroupRow(
                        title = if (isAdminActive) "Device Administration: Active" else "Device Administration",
                        subtitle = "Hardware screen lock authorization to secure phone post-task.",
                        icon = Icons.Default.AdminPanelSettings,
                        iconTint = if (isAdminActive) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                        iconContainerColor = if (isAdminActive) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        trailingContent = {
                            if (isAdminActive) {
                                PermissionStatusActiveChip()
                            } else {
                                PermissionGrantActionButton(
                                    text = "Activate",
                                    onClick = { HelperDeviceAdminReceiver.requestActivation(context) }
                                )
                            }
                        },
                        detailedPurpose = "Grants standard device administration privileges solely used to secure the display immediately after scheduled tasks conclude.",
                        onSettingsClick = {
                            HelperDeviceAdminReceiver.requestActivation(context)
                        }
                    )

                    AppGroupDivider()

                    // Row 4: Contacts, SMS & Phone Access
                    AppGroupRow(
                        title = if (hasRuntimePerms) "Communications Access: Active" else "Contacts, SMS & Phone Access",
                        subtitle = "Auto-discovers contacts and permits direct cellular messaging.",
                        icon = Icons.Default.Contacts,
                        iconTint = if (hasRuntimePerms) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                        iconContainerColor = if (hasRuntimePerms) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        trailingContent = {
                            if (hasRuntimePerms) {
                                PermissionStatusActiveChip()
                            } else {
                                PermissionGrantActionButton(
                                    text = "Grant All",
                                    onClick = { runtimePermissionsLauncher.launch(coreRuntimePermissions.toTypedArray()) }
                                )
                            }
                        },
                        detailedPurpose = "Required to populate recipient contact names, dispatch carrier SMS tasks, and handle incoming call automation.",
                        onSettingsClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    )

                    AppGroupDivider()

                    // Row 5: Do Not Disturb & Sound Policy
                    AppGroupRow(
                        title = if (hasDnd) "Do Not Disturb & Sound: Active" else "Do Not Disturb / Sound Policy",
                        subtitle = "Required for Sound Mode Switcher (Silent / Vibrate / Normal).",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        iconTint = if (hasDnd) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                        iconContainerColor = if (hasDnd) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        trailingContent = {
                            if (hasDnd) {
                                PermissionStatusActiveChip()
                            } else {
                                PermissionGrantActionButton(
                                    text = "Grant",
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            })
                                        } catch (e: Exception) {
                                            AppFileLogger.w("PermissionScreen", "Failed to launch DND settings: ${e.message}")
                                            context.showAppToast("Unable to open notification policy settings directly")
                                        }
                                    }
                                )
                            }
                        },
                        detailedPurpose = "Allows Helper to programmatically adjust the system audio stream and sound mode profile between Normal, Vibrate, and Silent states.",
                        onSettingsClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            } catch (e: Exception) {
                                context.showAppToast("Unable to open notification policy settings directly")
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Self-healing and watchdog quick fix card.
 */
@Composable
fun SuperAutoHealAndPermissionFixerCard(context: Context) {
    var isHealing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AppGroupCard {
        AppGroupRow(
            title = "Auto-Heal Services & Watchdog",
            subtitle = "Restores and binds any dropped background services or alarms.",
            icon = Icons.Default.Sync,
            iconTint = AppSemanticColors.success,
            iconContainerColor = AppSemanticColors.successContainer,
            trailingContent = {
                GlassButton(
                    text = if (isHealing) "Healing..." else "Auto-Heal",
                    onClick = {
                        if (isHealing) return@GlassButton
                        isHealing = true
                        scope.launch {
                            val healed = withContext(Dispatchers.IO) {
                                AccessibilityHealthManager.ensureAllServicesActive(context)
                                AccessibilityHealthManager.ensureServiceAlive(context)
                            }
                            isHealing = false
                            if (healed) {
                                context.showAppToast("Services verified and operational")
                            } else {
                                context.showAppToast("Auto-heal executed. Review individual items in Permission Center.")
                            }
                        }
                    },
                    icon = if (isHealing) Icons.Default.HourglassTop else Icons.Default.Sync,
                    variant = GlassButtonVariant.SUCCESS
                )
            }
        )
    }
}

/**
 * Information card instructing users how to lock the app into recent tasks to prevent OEM background kills.
 */
@Composable
fun KeepOpenRecentAppsCard(context: Context = LocalContext.current) {
    val brandSummary = remember { OemBrandHelper.getDeviceSummary() }
    val brandGuidance = remember { OemBrandHelper.getOemGuidance() }

    AppGroupCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(AppLayoutGrid.SpaceMedium)) {
            Row(
                modifier = Modifier.defaultMinSize(minHeight = AppLayoutGrid.MinTouchTarget),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(AppLayoutGrid.SquircleCornerRadius)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(AppLayoutGrid.SpaceStandard))
                Column {
                    Text(
                        text = "OEM Autostart ($brandSummary)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Hardware settings to prevent background kills",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceSmall))

            Column(verticalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceXS)) {
                brandGuidance.forEach { step ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "• ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = step,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceStandard))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceSmall)
            ) {
                GlassButton(
                    text = "Open Auto-Start",
                    onClick = {
                        if (!OemBrandHelper.openOemBackgroundSettings(context)) {
                            context.showAppToast("Unable to open OEM autostart settings directly")
                        }
                    },
                    icon = Icons.Default.Settings,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "Special Overlays",
                    onClick = {
                        if (!OverlayPermissionHelper.launchOverlaySettings(context)) {
                            context.showAppToast("Unable to open overlay settings directly")
                        }
                    },
                    icon = Icons.Default.Layers,
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
