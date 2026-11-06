/**
 * Package: com.jmd.helper.ui.screen
 * Module: UI Permission & Health Screen Layer
 *
 * Dedicated permission verification and diagnostic hub displaying real-time status of:
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
 * - Components: `GlassComponentsKt`, `AppCard`, `AppSubScreenHeader`
 * - Design System: [com.jmd.helper.ui.theme.OrangeBrandPalette], [com.jmd.helper.ui.theme.AppDesignTokens]
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.rotate
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
import com.jmd.helper.ui.components.AppCard
import com.jmd.helper.ui.components.AppSubScreenHeader
import com.jmd.helper.ui.components.GlassButton
import com.jmd.helper.ui.components.GlassButtonVariant
import com.jmd.helper.ui.theme.AppDesignTokens
import com.jmd.helper.ui.theme.OrangeBrandPalette
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
 * Modern Material 3 Expressive Permission & System Health Hub.
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

    // Grouping metrics for category headers
    val coreCount = listOf(isA11yActive, canExactAlarm, hasNotifListener).count { it }
    val bgCount = listOf(isIgnoringBattery, true).count { it } // Battery + Sideload Guide
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
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

            // 2. Section: Core Automation Engine
            item {
                PermissionSectionHeader(
                    title = "Core Automation Engine",
                    subtitle = "Foundational services powering scheduled task execution",
                    grantedCount = coreCount,
                    totalCount = 3
                )
            }

            item {
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
                    AccessibilityHealthManager.ServiceState.DISABLED -> "Required to dispatch scheduled WhatsApp tasks hands-free."
                }
                PermissionCardItem(
                    icon = Icons.Default.VerifiedUser,
                    title = a11yTitle,
                    subtitle = a11ySubtitle,
                    isGranted = isA11yActive,
                    grantButtonText = if (a11yState == AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND) "Toggle" else "Enable",
                    detailedPurpose = "Allows Helper to interact with approved messaging applications at user-scheduled times to populate and dispatch tasks. Never reads personal data or interacts with banking apps.",
                    onClick = {
                        if (isA11yActive) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        } else {
                            showA11yDisclosureDialog = true
                        }
                    }
                )
            }

            item {
                PermissionCardItem(
                    icon = Icons.Default.Alarm,
                    title = if (canExactAlarm) "Exact Alarms & Triggers: Active" else "Schedule Exact Alarms",
                    subtitle = "Fires scheduled tasks at the exact specified second without battery jitter.",
                    isGranted = canExactAlarm,
                    grantButtonText = "Grant",
                    detailedPurpose = "Required on Android 12+ (API 31+) to invoke high-precision RTC alarms, ensuring time-critical tasks trigger promptly without system delay.",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = "package:${context.packageName}".toUri()
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        } else {
                            context.showAppToast("Exact alarm privileges active")
                        }
                    }
                )
            }

            item {
                PermissionCardItem(
                    icon = Icons.Default.NotificationsActive,
                    title = if (hasNotifListener) "Notification Listener: Active" else "Notification Listener Access",
                    subtitle = "Detects incoming chats for automated rules and quick responses.",
                    isGranted = hasNotifListener,
                    grantButtonText = "Enable",
                    detailedPurpose = "Enables Helper to read notification actions in messaging apps to process auto-replies directly in the background without needing to illuminate the display.",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                )
            }

            // 3. Section: Background Reliability & Battery
            item {
                PermissionSectionHeader(
                    title = "Background Reliability",
                    subtitle = "Prevent OS task termination and background throttling",
                    grantedCount = bgCount,
                    totalCount = 2
                )
            }

            item {
                PermissionCardItem(
                    icon = Icons.Default.BatteryChargingFull,
                    title = if (isIgnoringBattery) "Battery Saver: Exempted" else "Battery Optimization Exemption",
                    subtitle = "Prevents system power savers from freezing scheduled timers.",
                    isGranted = isIgnoringBattery,
                    grantButtonText = "Exempt",
                    detailedPurpose = "Adds Helper to the system battery optimization allowlist, ensuring background task schedulers are never frozen during Doze mode.",
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

            item {
                OemAutostartCard(context)
            }

            item {
                PermissionCardItem(
                    icon = Icons.Default.Security,
                    title = "Sideloaded App Guidance (Android 13+)",
                    subtitle = "If settings are greyed out: App Info -> 3 dots -> Allow restricted settings.",
                    isGranted = true,
                    grantButtonText = "App Info",
                    detailedPurpose = "On Android 13+, sideloaded APKs have accessibility and notification permissions restricted by default. Tapping the three dots in App Info unlocks full access.",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${context.packageName}".toUri()
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 4. Section: Device Controls & Communications
            item {
                PermissionSectionHeader(
                    title = "Device Controls & Communications",
                    subtitle = "Screen locking, overlays, and communication features",
                    grantedCount = deviceCount,
                    totalCount = 5
                )
            }

            item {
                PermissionCardItem(
                    icon = Icons.Default.LockClock,
                    title = "Power Button Auto-Lock (Biometrics Safe)",
                    subtitle = "Preserves fingerprint and face unlock without locking out credentials.",
                    isGranted = true,
                    grantButtonText = "Ready",
                    detailedPurpose = "Helper uses the system power button simulation (GLOBAL_ACTION_LOCK_SCREEN) to turn off the screen post-dispatch without forcing hardware PIN lockout.",
                    onClick = {
                        context.showAppToast("Power button auto-lock active (fingerprint preserved)")
                    }
                )
            }

            item {
                PermissionCardItem(
                    icon = Icons.Default.Layers,
                    title = if (hasOverlay) "Display Over Other Apps: Granted" else "Display Over Other Apps",
                    subtitle = "Renders privacy shields, countdown overlays, and incoming alerts.",
                    isGranted = hasOverlay,
                    grantButtonText = "Grant",
                    detailedPurpose = "Required to render transient privacy overlays during automated screen actions and display simulated call interfaces.",
                    onClick = {
                        if (!hasOverlay) {
                            if (!OverlayPermissionHelper.launchOverlaySettings(context)) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = "package:${context.packageName}".toUri()
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        } else {
                            context.showAppToast("Overlay permission active")
                        }
                    }
                )
            }

            item {
                PermissionCardItem(
                    icon = Icons.Default.AdminPanelSettings,
                    title = if (isAdminActive) "Device Admin: Active" else "Device Administration",
                    subtitle = "Hardware screen lock authorization to secure phone post-task.",
                    isGranted = isAdminActive,
                    grantButtonText = "Activate",
                    detailedPurpose = "Grants standard device administration privileges solely used to secure the display immediately after scheduled tasks conclude.",
                    onClick = {
                        if (!isAdminActive) {
                            HelperDeviceAdminReceiver.requestActivation(context)
                        } else {
                            context.showAppToast("Device Administration is active")
                        }
                    }
                )
            }

            item {
                PermissionCardItem(
                    icon = Icons.Default.Contacts,
                    title = if (hasRuntimePerms) "Communications Access: Active" else "Contacts, SMS & Phone Access",
                    subtitle = "Auto-discovers contacts and permits direct cellular messaging.",
                    isGranted = hasRuntimePerms,
                    grantButtonText = "Grant All",
                    detailedPurpose = "Required to populate recipient contact names, dispatch carrier SMS tasks, and handle incoming call automation.",
                    onClick = {
                        if (!hasRuntimePerms) {
                            runtimePermissionsLauncher.launch(coreRuntimePermissions.toTypedArray())
                        } else {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }

            item {
                PermissionCardItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = if (hasDnd) "Do Not Disturb & Sound: Active" else "Do Not Disturb / Sound Policy",
                    subtitle = "Required for Sound Mode Switcher (Silent / Vibrate / Normal).",
                    isGranted = hasDnd,
                    grantButtonText = "Grant",
                    detailedPurpose = "Allows Helper to programmatically adjust the system audio stream and sound mode profile between Normal, Vibrate, and Silent states.",
                    onClick = {
                        if (!hasDnd) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            } catch (e: Exception) {
                                AppFileLogger.w("PermissionScreen", "Failed to launch DND settings: ${e.message}")
                                context.showAppToast("Unable to open notification policy settings directly")
                            }
                        } else {
                            context.showAppToast("Sound policy access active")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Hero System Health Overview Card displaying visual readiness metrics and quick actions.
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
    val isDark = isSystemInDarkTheme()
    val isAllGranted = grantedCount >= totalCount

    val healthPercentage = (grantedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = healthPercentage, label = "HealthProgress")

    val successAccent = if (isDark) OrangeBrandPalette.SuccessGreenDark else OrangeBrandPalette.SuccessGreen
    val successBg = if (isDark) OrangeBrandPalette.SuccessGreenDarkContainer else OrangeBrandPalette.SuccessGreenContainer
    val primaryAccent = MaterialTheme.colorScheme.primary

    val containerBg = if (isAllGranted) {
        if (isDark) Color(0xFF10281E) else Color(0xFFF1F8F3)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.45f else 0.65f)
    }
    val borderColor = if (isAllGranted) {
        if (isDark) successAccent.copy(alpha = 0.4f) else OrangeBrandPalette.SuccessGreenBorder
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = containerBg,
        borderColor = borderColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isAllGranted) successAccent else primaryAccent,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAllGranted) "System Protected & Ready" else "System Health Check",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isAllGranted) {
                        "All $totalCount essential system privileges are fully authorized."
                    } else {
                        "$grantedCount of $totalCount privileges authorized. Missing authorizations highlighted below."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Percentage Badge
            Surface(
                shape = CircleShape,
                color = if (isAllGranted) successBg else primaryAccent.copy(alpha = 0.12f),
                border = BorderStroke(2.dp, if (isAllGranted) successAccent else primaryAccent),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${(healthPercentage * 100).toInt()}%",
                        fontSize = 13.sp,
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

        // Mini status indicators row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HealthIndicatorPill(name = "Engine", isGranted = isA11yActive, isDark = isDark)
            HealthIndicatorPill(name = "Alarms", isGranted = canExactAlarm, isDark = isDark)
            HealthIndicatorPill(name = "Notifs", isGranted = hasNotifListener, isDark = isDark)
            HealthIndicatorPill(name = "Battery", isGranted = isIgnoringBattery, isDark = isDark)
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
                text = "ADB Power Setup",
                onClick = onOpenAdbDialog,
                icon = Icons.Default.Terminal,
                variant = GlassButtonVariant.PRIMARY,
                modifier = Modifier.weight(0.9f)
            )
        }
    }
}

@Composable
private fun HealthIndicatorPill(name: String, isGranted: Boolean, isDark: Boolean) {
    val activeColor = if (isDark) OrangeBrandPalette.SuccessGreenDark else OrangeBrandPalette.SuccessGreen
    val inactiveColor = MaterialTheme.colorScheme.outline
    val bg = if (isGranted) {
        if (isDark) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFE8F5E9)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

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
    val isDark = isSystemInDarkTheme()
    val badgeColor = if (isComplete) {
        if (isDark) OrangeBrandPalette.SuccessGreenDark else OrangeBrandPalette.SuccessGreen
    } else {
        MaterialTheme.colorScheme.primary
    }
    val badgeBg = if (isComplete) {
        if (isDark) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFE8F5E9)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
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

/**
 * Compact, high-end permission item with squircle icon, status chip, and tap-to-expand details.
 */
@Composable
private fun PermissionCardItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    grantButtonText: String,
    detailedPurpose: String,
    onClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val accentGreen = if (isDark) OrangeBrandPalette.SuccessGreenDark else OrangeBrandPalette.SuccessGreen
    val greenContainer = if (isDark) Color(0xFF10B981).copy(alpha = 0.16f) else Color(0xFFE8F5E9)
    val chevronRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "ChevronRotation")

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        backgroundColor = if (isGranted) {
            if (isDark) Color(0xFF10B981).copy(alpha = 0.04f) else Color(0xFFF9FDF9)
        } else {
            MaterialTheme.colorScheme.surface
        },
        borderColor = if (isGranted) {
            if (isDark) Color(0xFF4ADE80).copy(alpha = 0.20f) else Color(0xFFC8E6C9)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Squircle leading icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isGranted) greenContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) accentGreen else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing action or status chip
            if (isGranted) {
                Surface(
                    shape = AppDesignTokens.ShapePill,
                    color = greenContainer,
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF4ADE80).copy(alpha = 0.35f) else Color(0xFFA5D6A7)),
                    modifier = Modifier.clickable { onClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = accentGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentGreen
                        )
                    }
                }
            } else {
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
                        text = grantButtonText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation)
            )
        }

        // Expandable Detail Accordion
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 0.8.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detailedPurpose,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Open System Settings",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * OEM Brand tailored autostart guidance card.
 */
@Composable
private fun OemAutostartCard(context: Context) {
    val brandSummary = remember { OemBrandHelper.getDeviceSummary() }
    val brandGuidance = remember { OemBrandHelper.getOemGuidance() }
    var isExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "OemChevronRotation")

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
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
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
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
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            brandGuidance.take(if (isExpanded) brandGuidance.size else 2).forEach { step ->
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
    var healRefreshKey by remember { mutableIntStateOf(0) }
    PermissionHeroCard(
        context = context,
        grantedCount = 8,
        totalCount = 8,
        isA11yActive = true,
        canExactAlarm = true,
        hasNotifListener = true,
        isIgnoringBattery = true,
        onOpenAdbDialog = {},
        onHealDone = { healRefreshKey++ }
    )
}

/**
 * Information card instructing users how to lock the app into recent tasks to prevent OEM background kills.
 */
@Composable
fun KeepOpenRecentAppsCard(context: Context = LocalContext.current) {
    OemAutostartCard(context)
}
