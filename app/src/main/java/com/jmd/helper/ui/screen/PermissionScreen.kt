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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.jmd.helper.core.*
import com.jmd.helper.receiver.HelperDeviceAdminReceiver
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.theme.AppDesignTokens
import com.jmd.helper.ui.theme.AppLayoutGrid
import com.jmd.helper.ui.theme.AppSemanticColors
import com.jmd.helper.ui.theme.isAppDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Universal ADB Privilege Grant Command.
 */
private const val UNIVERSAL_ADB_COMMAND =
    "adb shell \"pm grant com.jmd.helper android.permission.WRITE_SECURE_SETTINGS; " +
    "pm grant com.jmd.helper android.permission.READ_PHONE_STATE; " +
    "pm grant com.jmd.helper android.permission.CALL_PHONE; " +
    "pm grant com.jmd.helper android.permission.ANSWER_PHONE_CALLS; " +
    "pm grant com.jmd.helper android.permission.READ_CALL_LOG; " +
    "pm grant com.jmd.helper android.permission.READ_CONTACTS; " +
    "pm grant com.jmd.helper android.permission.POST_NOTIFICATIONS; " +
    "pm grant com.jmd.helper android.permission.READ_SMS; " +
    "pm grant com.jmd.helper android.permission.SEND_SMS; " +
    "pm grant com.jmd.helper android.permission.READ_MEDIA_IMAGES; " +
    "pm grant com.jmd.helper android.permission.READ_MEDIA_VIDEO; " +
    "appops set com.jmd.helper SYSTEM_ALERT_WINDOW allow; " +
    "appops set com.jmd.helper TURN_SCREEN_ON allow; " +
    "appops set com.jmd.helper SCHEDULE_EXACT_ALARM allow; " +
    "appops set com.jmd.helper START_ACTIVITIES_FROM_BACKGROUND allow; " +
    "appops set com.jmd.helper ACCESS_RESTRICTED_SETTINGS allow; " +
    "appops set com.jmd.helper AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore; " +
    "dumpsys deviceidle whitelist +com.jmd.helper; " +
    "dpm set-active-admin com.jmd.helper/.receiver.HelperDeviceAdminReceiver\""

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

    // Section metrics
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
                .padding(horizontal = AppLayoutGrid.SpaceStandard),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp
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
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
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
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "OEM Autostart ($brandSummary)",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Hardware settings to prevent background kills",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = if (oemExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(18.dp)
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
                                    .padding(top = 10.dp, start = 50.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                brandGuidance.forEach { step ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "• ",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = step,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
 * Modern Material 3 Hero System Health Card with dynamic luminance contrast.
 */
@Composable
private fun PermissionHeroCard(
    context: Context,
    grantedCount: Int,
    totalCount: Int,
    isA11yActive: Boolean,
    canExactAlarm: Boolean,
    hasNotifListener: Boolean,
    isIgnoringBattery: Boolean,
    onOpenAdbDialog: () -> Unit,
    onHealDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isHealing by remember { mutableStateOf(false) }
    val isAllGranted = grantedCount >= totalCount

    val healthPercentage = (grantedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = healthPercentage, label = "HealthProgress")

    val successAccent = AppSemanticColors.success
    val primaryAccent = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppLayoutGrid.CardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = AppSemanticColors.heroContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            1.dp,
            if (isAllGranted) AppSemanticColors.successBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (isAllGranted) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(AppLayoutGrid.SquircleCornerRadius)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAllGranted) Icons.Default.Shield else Icons.Default.SecurityUpdateWarning,
                            contentDescription = null,
                            tint = if (isAllGranted) successAccent else primaryAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (isAllGranted) "System Protected & Ready" else "System Health Check",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isAllGranted) {
                                "All $totalCount core privileges authorized & healthy."
                            } else {
                                "$grantedCount of $totalCount privileges authorized."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progress Percentage Badge
                Surface(
                    shape = CircleShape,
                    color = if (isAllGranted) AppSemanticColors.successContainer else primaryAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.5.dp, if (isAllGranted) successAccent else primaryAccent),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${(healthPercentage * 100).toInt()}%",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isAllGranted) successAccent else primaryAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isAllGranted) successAccent else primaryAccent,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Mini Indicators Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HealthMiniChip(name = "Engine", isGranted = isA11yActive)
                HealthMiniChip(name = "Alarms", isGranted = canExactAlarm)
                HealthMiniChip(name = "Notifs", isGranted = hasNotifListener)
                HealthMiniChip(name = "Battery", isGranted = isIgnoringBattery)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    text = if (isHealing) "Healing..." else "Auto-Heal Engine",
                    onClick = {
                        if (isHealing) return@GlassButton
                        isHealing = true
                        scope.launch {
                            val healed = withContext(Dispatchers.IO) {
                                AccessibilityHealthManager.ensureAllServicesActive(context)
                                AccessibilityHealthManager.ensureServiceAlive(context)
                            }
                            isHealing = false
                            onHealDone()
                            if (healed) {
                                context.showAppToast("Services verified and operational")
                            } else {
                                context.showAppToast("Auto-heal executed. Review individual items below.", isLong = true)
                            }
                        }
                    },
                    icon = if (isHealing) Icons.Default.HourglassTop else Icons.Default.Sync,
                    variant = GlassButtonVariant.SUCCESS,
                    modifier = Modifier.weight(1.1f)
                )

                GlassButton(
                    text = "ADB Setup",
                    onClick = onOpenAdbDialog,
                    icon = Icons.Default.Terminal,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(0.9f)
                )
            }
        }
    }
}

@Composable
private fun HealthMiniChip(name: String, isGranted: Boolean) {
    val activeColor = AppSemanticColors.success
    val inactiveColor = MaterialTheme.colorScheme.outline
    val bg = if (isGranted) AppSemanticColors.successContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (isGranted) activeColor else inactiveColor,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isGranted) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Section title header for categorized permission groups with status counter badge.
 */
@Composable
private fun PermissionSectionHeader(
    title: String,
    subtitle: String,
    grantedCount: Int,
    totalCount: Int
) {
    val isComplete = grantedCount >= totalCount
    val badgeColor = if (isComplete) AppSemanticColors.success else MaterialTheme.colorScheme.primary
    val badgeBg = if (isComplete) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = AppDesignTokens.ShapePill,
            color = badgeBg,
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
        ) {
            Text(
                text = "$grantedCount/$totalCount Ready",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun PermissionStatusActiveChip() {
    Surface(
        shape = AppDesignTokens.ShapePill,
        color = AppSemanticColors.successContainer,
        border = BorderStroke(1.dp, AppSemanticColors.successBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = AppSemanticColors.success,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Active",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppSemanticColors.success
            )
        }
    }
}

@Composable
private fun PermissionGrantActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = AppDesignTokens.ShapePill,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/**
 * Universal ADB Privilege Grant Dialog providing one-click command copying.
 */
@Composable
private fun AdbSetupModalDialog(context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Universal ADB Setup",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Grants all required background execution privileges (secure settings, runtime permissions, and battery whitelist) with zero interactive prompts.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = UNIVERSAL_ADB_COMMAND,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    com.jmd.helper.compat.ClipboardCompat.setPrimaryClip(
                        cm,
                        ClipData.newPlainText("Universal ADB Command", UNIVERSAL_ADB_COMMAND)
                    )
                    context.showAppToast("ADB command copied to clipboard")
                    onDismiss()
                }
            ) {
                Text("Copy Command", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Accessibility Service Policy & User Disclosure Dialog.
 */
@Composable
private fun A11yDisclosureDialog(context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Task Service Disclosure",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Helper uses Android's Accessibility Service API exclusively for user-directed automated task dispatch:",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• Hands-free scheduled message preparation and delivery in user-approved messaging applications (WhatsApp and WhatsApp Business).\n" +
                           "• Unattended device wake and user-configured PIN entry assistance when scheduled tasks trigger while locked.\n" +
                           "• Power button simulation (GLOBAL_ACTION_LOCK_SCREEN) post-dispatch to preserve biometric authentication.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Privacy & Safety Guarantees:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• Helper does NOT collect, store, or transmit your private chat messages, keystrokes, personal passwords, or screen contents.\n" +
                           "• Helper strictly isolates itself from all banking, payment, and UPI applications.\n" +
                           "• 100% on-device execution with zero cloud telemetry.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            ) {
                Text("Agree & Open Settings", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
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
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "OEM Autostart ($brandSummary)",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Hardware settings to prevent background kills",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                brandGuidance.forEach { step ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "• ",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = step,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
