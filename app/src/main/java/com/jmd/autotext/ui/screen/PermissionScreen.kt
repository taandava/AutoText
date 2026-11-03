

/**
 * Package: com.jmd.autotext.ui.screen
 * Module: UI Permission & Health Screen Layer
 *
 * Dedicated permission verification and diagnostic hub displaying real-time status of:
 * - Accessibility Service
 * - Notification Listener Service
 * - Exact Alarm privileges
 * - Battery optimization exemption (allowlist)
 * - Overlay permission
 * - Device Admin privileges
 *
 * Linked With:
 * - Health Manager: [com.jmd.autotext.core.AccessibilityHealthManager]
 * - Overlay Helper: [com.jmd.autotext.core.OverlayPermissionHelper]
 * - Components: `GlassComponentsKt`
 */
package com.jmd.autotext.ui.screen

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.OverlayPermissionHelper
import com.jmd.autotext.core.PermissionManager
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.receiver.AutoTextDeviceAdminReceiver
import com.jmd.autotext.ui.components.GlassButtonVariant
import com.jmd.autotext.ui.components.appCard
import com.jmd.autotext.ui.components.appSubScreenHeader
import com.jmd.autotext.ui.components.glassButton
import com.jmd.autotext.ui.theme.AppDesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Main diagnostic and permission resolution center rendering automated heal utilities,
 * ADB synchronization commands, and granular system permission toggles.
 *
 * @param context Local Android context used to query system managers and dispatch settings intents.
 * @param onBack Callback invoked when user taps the top bar back navigation arrow.
 */
@Composable
fun permissionCenterView(context: Context, onBack: () -> Unit) {
    var showA11yDisclosureDialog by remember { mutableStateOf(false) }
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

    if (showA11yDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showA11yDisclosureDialog = false },
            title = {
                Text(
                    text = "Accessibility Service Prominent Disclosure",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "AutoText uses Android's AccessibilityService API solely for the following explicit user-directed capabilities:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Hands-free automated message preparation and delivery in user-authorized messaging apps (WhatsApp and WhatsApp Business) at user-scheduled times.\n" +
                               "• Unattended device wake and user-configured PIN assistance when scheduled tasks trigger while locked.\n" +
                               "• Automatic return to home screen and screen lock upon task completion.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Privacy & Safety Commitments:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• AutoText does NOT collect, store, or transmit your private chat messages, keystrokes, personal passwords, or screen contents.\n" +
                               "• AutoText strictly isolates itself from and NEVER inspects, reads, or interacts with banking, payment, or UPI apps (such as Google Pay, PhonePe, Paytm, or net banking apps).\n" +
                               "• 100% on-device operation: Zero cloud analytics or third-party tracking.\n" +
                               "• You retain full control and may pause or revoke this service at any time.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showA11yDisclosureDialog = false
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                ) {
                    Text("Agree & Open Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showA11yDisclosureDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = "Permission Center",
                subtitle = "System access & automated self-healing",
                onBack = onBack
            )
        },
        containerColor = AppDesignTokens.VoidBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. Super Auto-Heal & Universal Fixer
            item {
                superAutoHealAndPermissionFixerCard(context)
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Individual Permissions & System Access", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Ensure these are granted so WhatsApp screen assistance, alarms, and background sending run smoothly.", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 2. Device Administration (Auto-Lock phone after task completes)
            item {
                val isAdminActive = AutoTextDeviceAdminReceiver.isAdminActive(context)
                permissionItem(
                    title = if (isAdminActive) "Device Administration: Active" else "Device Administration (Hardware Auto-Lock)",
                    desc = "Allows AutoText to instantly and securely lock the device (via lockNow) after unattended scheduled WhatsApp/SMS tasks finish.",
                    btnText = if (isAdminActive) "Device Admin Active" else "Activate Device Admin",
                    isGranted = isAdminActive,
                ) {
                    if (!isAdminActive) {
                        if (!AutoTextDeviceAdminReceiver.requestActivation(context)) {
                            context.showAppToast(
                                "Could not open Device Admin screen directly. Please enable AutoText in system Settings > Security > Device admin apps.",
                                isLong = true,
                            )
                        }
                    } else {
                        context.showAppToast("Admin already enabled")
                    }
                }
            }

            // 3. Display Over Other Apps (Overlay Permission)
            item {
                val hasOverlay = OverlayPermissionHelper.canDrawOverlays(context)
                permissionItem(
                    title = if (hasOverlay) "Display Over Other Apps: Granted" else "Display Over Other Apps (Overlay)",
                    desc = "Allows AutoText to display privacy shields, countdown overlays, and incoming call simulation alerts over other applications.",
                    btnText = if (hasOverlay) "Overlay Permission Active" else "Grant Overlay Permission",
                    isGranted = hasOverlay,
                ) {
                    if (!hasOverlay) {
                        try {
                            context.startActivity(OverlayPermissionHelper.getManageOverlayIntent(context))
                        } catch (e: Exception) {
                            com.jmd.autotext.core.AppFileLogger.w("PermissionScreen", "Failed to launch overlay settings: ${e.message}")
                            context.showAppToast("Unable to open overlay settings directly.")
                        }
                    } else {
                        context.showAppToast("Overlay permission already granted")
                    }
                }
            }



            // 4. Exact Alarms & Reminders Permission
            item {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val canExactAlarm = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                        (alarmManager?.canScheduleExactAlarms() == true)
                permissionItem(
                    title = if (canExactAlarm) "Exact Alarms & Reminders: Granted" else "Schedule Exact Alarms & Reminders",
                    desc = "Required by Android 12+ to fire scheduled messages and reminders at the precise exact second without power-saver jitter.",
                    btnText = if (canExactAlarm) "Exact Alarms Granted" else "Grant Exact Alarm Permission",
                    isGranted = canExactAlarm,
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = "package:${context.packageName}".toUri()
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            },
                        )
                    } else {
                        context.showAppToast("Exact alarms permitted")
                    }
                }
            }

            // 5. Accessibility Assistant Service
            item {
                val a11yState = AccessibilityHealthManager.getServiceState(context)
                val isA11y = a11yState == AccessibilityHealthManager.ServiceState.OPERATIONAL || a11yState == AccessibilityHealthManager.ServiceState.STANDBY
                val a11yTitle = when (a11yState) {
                    AccessibilityHealthManager.ServiceState.OPERATIONAL -> "AutoText Assistant: Active"
                    AccessibilityHealthManager.ServiceState.STANDBY -> "AutoText Assistant: Bank-Safe Standby"
                    AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND -> "AutoText Assistant: Enabled (Binder Paused)"
                    AccessibilityHealthManager.ServiceState.DISABLED -> "AutoText Assistant Service"
                }
                val a11yDesc = when (a11yState) {
                    AccessibilityHealthManager.ServiceState.OPERATIONAL -> "Active in background to auto-send scheduled WhatsApp messages and unlock screen."
                    AccessibilityHealthManager.ServiceState.STANDBY -> "Bank-Safe Standby: Connects on demand for ~2s during tasks only. Banking and payment apps detect 0 third-party services."
                    AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND -> "Accessibility is enabled in Settings, but the background binder is connecting. Tap to toggle in Settings if needed."
                    AccessibilityHealthManager.ServiceState.DISABLED -> "Required to auto-send scheduled WhatsApp messages and assist with screen unlock."
                }
                val a11yBtnText = when (a11yState) {
                    AccessibilityHealthManager.ServiceState.OPERATIONAL -> "Assistant Active"
                    AccessibilityHealthManager.ServiceState.STANDBY -> "Bank-Safe Ready"
                    AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND -> "Toggle in Settings"
                    AccessibilityHealthManager.ServiceState.DISABLED -> "Accessibility Settings"
                }
                permissionItem(
                    title = a11yTitle,
                    desc = a11yDesc,
                    btnText = a11yBtnText,
                    isGranted = isA11y,
                ) {
                    if (isA11y) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    } else {
                        showA11yDisclosureDialog = true
                    }
                }
            }

            // 6. Android 13/14 Sideloaded App Fix (Restricted Settings)
            item {
                permissionItem(
                    title = "Android 13 / 14 Sideloaded App Fix",
                    desc = "If Accessibility is greyed out: Go to App Info -> Tap 3 dots (top right) -> 'Allow restricted settings' -> then turn Accessibility on.",
                    btnText = "Open App Info (Tap 3 Dots)",
                ) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            }

            // 7. Notification Listener Access
            item {
                val hasNotif = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                permissionItem(
                    title = if (hasNotif) "Notification Listener: Active" else "Notification Listener Access",
                    desc = "Required for WhatsApp auto-replies directly from background without waking screen.",
                    btnText = if (hasNotif) "Notification Access Active" else "Notification Access Settings",
                    isGranted = hasNotif,
                ) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                }
            }

            // 8. Battery Optimization Exemption
            item {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                permissionItem(
                    title = if (isIgnoringBattery) "Battery Optimization: Exempted" else "Battery Optimization Exemption",
                    desc = "Prevents Android from freezing timers, ensuring scheduled messages trigger on the exact minute.",
                    btnText = if (isIgnoringBattery) "Battery Exemption Active" else "Exempt from Battery Saver",
                    isGranted = isIgnoringBattery,
                ) {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        com.jmd.autotext.core.AppFileLogger.w("PermissionScreen", "Failed to launch battery optimization settings: ${e.message}")
                        context.showAppToast("Unable to open battery optimization settings directly.")
                    }
                }
            }

            // 9. Contacts, SMS, Phone & Media Access
            item {
                val hasRuntimePerms = remember(permissionsRefreshKey) {
                    PermissionManager.hasContactsPermission(context) &&
                        PermissionManager.hasSmsSendPermission(context) &&
                        PermissionManager.hasCallPhonePermission(context) &&
                        PermissionManager.hasPhoneStatePermission(context) &&
                        PermissionManager.hasCallLogPermission(context) &&
                        PermissionManager.hasMediaPermission(context) &&
                        PermissionManager.hasPostNotificationsPermission(context)
                }

                permissionItem(
                    title = if (hasRuntimePerms) "Contacts, SMS, Phone & Media Access: Active" else "Contacts, SMS, Phone & Media Access",
                    desc = "Required to auto-discover mobile contacts into the internal dynamic pool for daily SMS campaigns, direct SMS dispatch, call automation, missed-call auto-replies, and photo/video attachments.",
                    btnText = if (hasRuntimePerms) "Runtime Permissions Active" else "Grant Runtime Permissions",
                    isGranted = hasRuntimePerms,
                ) {
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
            }

            // 10. Do Not Disturb / Notification Policy Access (Sound Mode Switcher)
            item {
                val hasDnd = PermissionManager.hasNotificationPolicyAccess(context)
                permissionItem(
                    title = if (hasDnd) "Do Not Disturb & Sound Policy: Active" else "Do Not Disturb / Notification Policy Access",
                    desc = "Required by Android for the Sound Mode Switcher to toggle between Silent, Vibrate, and Normal sound profiles without interruption.",
                    btnText = if (hasDnd) "Sound Policy Granted" else "Grant Sound Policy Access",
                    isGranted = hasDnd,
                ) {
                    if (!hasDnd) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        } catch (e: Exception) {
                            com.jmd.autotext.core.AppFileLogger.w("PermissionScreen", "Failed to launch notification policy settings: ${e.message}")
                            context.showAppToast("Unable to open notification policy settings directly.")
                        }
                    } else {
                        context.showAppToast("Notification policy access active")
                    }
                }
            }

            item {
                keepOpenRecentAppsCard(context)
            }
        }
    }
}


/**
 * Master repair and auto-heal card providing one-touch reactivation of dead accessibility binders
 * and single-click copying of the comprehensive universal ADB privilege grant script.
 *
 * @param context Local Android context for system service invocation and clipboard operations.
 */
@Composable
fun superAutoHealAndPermissionFixerCard(context: Context) {
    val scope = rememberCoroutineScope()
    var hasSecurePerm by remember { mutableStateOf(AccessibilityHealthManager.hasWriteSecureSettings(context)) }
    val isAdminActive = AutoTextDeviceAdminReceiver.isAdminActive(context)
    val isA11yActive = AccessibilityHealthManager.isServiceOperational()
    var isHealing by remember { mutableStateOf(value = false) }

    // Safe ADB command: grants WRITE_SECURE_SETTINGS, runtime permissions, and key appops without destructively overwriting existing third-party accessibility services
    val universalAdbCommand = "adb shell \"pm grant com.jmd.autotext android.permission.WRITE_SECURE_SETTINGS; pm grant com.jmd.autotext android.permission.READ_PHONE_STATE; pm grant com.jmd.autotext android.permission.CALL_PHONE; pm grant com.jmd.autotext android.permission.ANSWER_PHONE_CALLS; pm grant com.jmd.autotext android.permission.READ_CALL_LOG; pm grant com.jmd.autotext android.permission.READ_CONTACTS; pm grant com.jmd.autotext android.permission.POST_NOTIFICATIONS; pm grant com.jmd.autotext android.permission.READ_SMS; pm grant com.jmd.autotext android.permission.SEND_SMS; pm grant com.jmd.autotext android.permission.READ_MEDIA_IMAGES; pm grant com.jmd.autotext android.permission.READ_MEDIA_VIDEO; appops set com.jmd.autotext TURN_SCREEN_ON allow; appops set com.jmd.autotext SCHEDULE_EXACT_ALARM allow; appops set com.jmd.autotext START_ACTIVITIES_FROM_BACKGROUND allow; appops set com.jmd.autotext ACCESS_RESTRICTED_SETTINGS allow; appops set com.jmd.autotext AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore; dumpsys deviceidle whitelist +com.jmd.autotext; dpm set-active-admin com.jmd.autotext/.AutoTextDeviceAdminReceiver\""

    appCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (hasSecurePerm && isA11yActive) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        borderColor = if (hasSecurePerm && isA11yActive) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (hasSecurePerm && isA11yActive) Icons.Default.VerifiedUser else Icons.Default.AutoFixHigh,
                contentDescription = null,
                tint = if (hasSecurePerm && isA11yActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "⚡ Master Auto-Heal & Universal Fixer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "One-click automated recovery & permission synchronization",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live Health Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val canExactAlarm = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
                    (alarmManager?.canScheduleExactAlarms() == true)
            statusPill("A11y", isA11yActive, Modifier.weight(1f))
            statusPill("Admin", isAdminActive, Modifier.weight(1f))
            statusPill("Alarms", canExactAlarm, Modifier.weight(1f))
            statusPill("Secure", hasSecurePerm, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val hasOverlay = OverlayPermissionHelper.canDrawOverlays(context)
            val hasNotif = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            val hasDnd = PermissionManager.hasNotificationPolicyAccess(context)
            statusPill("Overlay", hasOverlay, Modifier.weight(1f))
            statusPill("Listener", hasNotif, Modifier.weight(1f))
            statusPill("Sound/DND", hasDnd, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Auto-Heal restarts dead accessibility binders and safely activates permissions using granted secure settings. The universal ADB command below grants WRITE_SECURE_SETTINGS, runtime permissions, battery optimization exemption, and device admin without prompts. Note: Android requires Accessibility Service and Notification Listener to be enabled in Android Settings or activated via the 'Auto-Heal Now' button below.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.5.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            glassButton(
                text = if (isHealing) "Healing..." else "Auto-Heal Now",
                onClick = {
                    if (isHealing) return@glassButton
                    isHealing = true
                    scope.launch {
                        val healed = withContext(Dispatchers.IO) {
                            AccessibilityHealthManager.ensureAllServicesActive(context)
                            AccessibilityHealthManager.ensureServiceAlive(context)
                        }
                        hasSecurePerm = AccessibilityHealthManager.hasWriteSecureSettings(context)
                        isHealing = false
                        if (healed) {
                            context.showAppToast("Accessibility active & healthy")
                        } else {
                            context.showAppToast("Heal attempted. For full background automation, grant permissions or paste the 1-line ADB command.", isLong = true)
                        }
                    }
                },
                emoji = if (isHealing) "⏳" else "🔄",
                variant = GlassButtonVariant.SUCCESS,
                modifier = Modifier.weight(1f),
            )

            glassButton(
                text = "Copy 1-Line ADB Setup",
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    com.jmd.autotext.compat.ClipboardCompat.setPrimaryClip(cm, ClipData.newPlainText("Universal ADB Command", universalAdbCommand))
                    context.showAppToast("ADB command copied")
                },
                emoji = "📋",
                variant = GlassButtonVariant.PRIMARY,
                modifier = Modifier.weight(1.2f),
            )
        }
    }
}

/**
 * Compact status pill tag rendering green (active) or red (inactive) indicator for diagnostic categories.
 *
 * @param label Short category tag (e.g. "A11y", "Admin", "Overlay").
 * @param isOk True if privilege is confirmed active and granted; false otherwise.
 * @param modifier Optional layout modifier.
 */
@Composable
internal fun statusPill(label: String, isOk: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = AppDesignTokens.ShapePill,
        color = if (isOk) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, if (isOk) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (isOk) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(3.dp),
                    ),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isOk) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Information card instructing users how to lock the app into recent tasks to prevent aggressive OEM background task termination.
 *
 * @param context Local Android context for resolving OEM device care settings intent.
 */
@Composable
fun keepOpenRecentAppsCard(context: Context = LocalContext.current) {
    appCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔒", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Keep Open in Recent Tasks (Prevent App Termination)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "To prevent Samsung One UI, Xiaomi, or OnePlus from terminating the background service or stopping its task assistance engine:\n\n" +
                    "• Samsung: Open Recent Apps (||| button) -> Tap AutoText icon at top of the card -> Tap 'Keep open' (padlock icon).\n" +
                    "• Xiaomi / HyperOS: In Recent Apps -> Long-press AutoText card -> Tap Padlock icon.\n" +
                    "• Oppo / Vivo / OnePlus: In Recent Apps -> Tap 3 dots (⋮) on AutoText -> Select 'Lock'.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        glassButton(
            text = "Open OEM Device Care / Background Settings",
            onClick = { AccessibilityHealthManager.openOemBackgroundSettings(context) },
            icon = Icons.Default.Settings,
            variant = GlassButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Individual permission diagnosis item showing description, granted badge, and direct intent launch trigger.
 *
 * @param title Name of the permission or subsystem privilege.
 * @param desc Explanation of why this permission is necessary for automated dispatches.
 * @param btnText Action button label.
 * @param isGranted Current confirmed grant status of the permission.
 * @param onClick Invoked when action button is tapped to open respective system settings screen.
 */
@Composable
fun permissionItem(
    title: String,
    desc: String,
    btnText: String,
    isGranted: Boolean = false,
    onClick: () -> Unit,
) {
    appCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (isGranted) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        borderColor = if (isGranted) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (isGranted) {
                Surface(
                    shape = AppDesignTokens.ShapePill,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                ) {
                    Text(
                        text = "ACTIVE",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
        Spacer(modifier = Modifier.height(10.dp))
        glassButton(
            text = btnText,
            onClick = onClick,
            variant = if (isGranted) GlassButtonVariant.SUCCESS else GlassButtonVariant.SECONDARY,
            emoji = if (isGranted) "✅" else "⚙️",
            shape = AppDesignTokens.ShapePill,
        )
    }
}


