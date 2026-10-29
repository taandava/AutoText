

/**
 * Package: com.jmd.autotext.ui.screen
 * Module: UI Permission & Health Screen Layer
 *
 * Dedicated permission verification and diagnostic hub displaying real-time status of:
 * - Accessibility Service
 * - Notification Listener Service
 * - Exact Alarm privileges
 * - Battery optimization whitelist
 * - Overlay permission
 * - Device Admin privileges
 *
 * Linked With:
 * - Health Manager: [com.jmd.autotext.core.AccessibilityHealthManager]
 * - Overlay Helper: [com.jmd.autotext.core.OverlayPermissionHelper]
 * - Components: `GlassComponentsKt`
 */
package com.jmd.autotext.ui.screen

import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.*

import com.jmd.autotext.core.*
import com.jmd.autotext.receiver.*
import com.jmd.autotext.service.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*


/**
 * Main diagnostic and permission resolution center rendering automated heal utilities,
 * ADB synchronization commands, and granular system permission toggles.
 *
 * @param context Local Android context used to query system managers and dispatch settings intents.
 * @param onBack Callback invoked when user taps the top bar back navigation arrow.
 */
@Composable
fun permissionCenterView(context: Context, onBack: () -> Unit) {
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
                Text("Ensure these are granted so WhatsApp auto-unlock, alarms, and background sending run smoothly.", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // 3. Floating Button & Overlay Permission (SYSTEM_ALERT_WINDOW)
            item {
                val hasOverlay = OverlayPermissionHelper.canDrawOverlays(context)
                permissionItem(
                    title = if (hasOverlay) "Display Over Other Apps: Granted" else "Display Over Other Apps / Floating Window",
                    desc = "Critical for Android 10+ and Vivo / Xiaomi / Samsung to wake the screen from sleep, launch over the lockscreen bouncer, and show countdown status.",
                    btnText = if (hasOverlay) "Overlay Permission Granted" else "Grant Overlay Permission",
                    isGranted = hasOverlay,
                ) {
                    context.startActivity(OverlayPermissionHelper.getManageOverlayIntent(context))
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

            // 5. Accessibility Automation Service
            item {
                val a11yState = AccessibilityHealthManager.getServiceState(context)
                val isA11y = a11yState == AccessibilityHealthManager.ServiceState.OPERATIONAL
                val a11yTitle = when (a11yState) {
                    AccessibilityHealthManager.ServiceState.OPERATIONAL -> "Accessibility Automation: Active"
                    AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND -> "Accessibility: Enabled (Binder Paused)"
                    AccessibilityHealthManager.ServiceState.DISABLED -> "Accessibility Automation Service"
                }
                val a11yDesc = when (a11yState) {
                    AccessibilityHealthManager.ServiceState.OPERATIONAL -> "Required to auto-send scheduled WhatsApp messages and auto-unlock phone via lockscreen PIN."
                    AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND -> "Accessibility is enabled in Settings, but the Android background binder is paused. Tap to re-toggle in Settings, or use Auto-Heal below."
                    AccessibilityHealthManager.ServiceState.DISABLED -> "Required to auto-send scheduled WhatsApp messages and auto-unlock phone via lockscreen PIN."
                }
                val a11yBtnText = when (a11yState) {
                    AccessibilityHealthManager.ServiceState.OPERATIONAL -> "Accessibility Active"
                    AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND -> "Toggle in Settings"
                    AccessibilityHealthManager.ServiceState.DISABLED -> "Accessibility Settings"
                }
                permissionItem(
                    title = a11yTitle,
                    desc = a11yDesc,
                    btnText = a11yBtnText,
                    isGranted = isA11y,
                ) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
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
                    } catch (_: Exception) {
                        // Restricted environment fallback
                    }
                }
            }

            // 9. Contacts, SMS & Call Log Access
            item {
                permissionItem(
                    title = "Contacts, SMS & Call Log Access",
                    desc = "Required to auto-discover mobile contacts into the internal dynamic pool for daily SMS campaigns.",
                    btnText = "App Permissions",
                ) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
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
    val hasOverlay = OverlayPermissionHelper.canDrawOverlays(context)
    val isA11yActive = AccessibilityHealthManager.isServiceOperational()
    var isHealing by remember { mutableStateOf(value = false) }

    // Safe ADB command: grants WRITE_SECURE_SETTINGS, runtime permissions, and key appops without destructively overwriting existing third-party accessibility services
    val universalAdbCommand = "adb shell \"pm grant com.jmd.autotext android.permission.WRITE_SECURE_SETTINGS; pm grant com.jmd.autotext android.permission.READ_PHONE_STATE; pm grant com.jmd.autotext android.permission.CALL_PHONE; pm grant com.jmd.autotext android.permission.ANSWER_PHONE_CALLS; pm grant com.jmd.autotext android.permission.READ_CONTACTS; pm grant com.jmd.autotext android.permission.POST_NOTIFICATIONS; pm grant com.jmd.autotext android.permission.READ_SMS; pm grant com.jmd.autotext android.permission.SEND_SMS; appops set com.jmd.autotext SYSTEM_ALERT_WINDOW allow; appops set com.jmd.autotext TURN_SCREEN_ON allow; appops set com.jmd.autotext SCHEDULE_EXACT_ALARM allow; appops set com.jmd.autotext START_ACTIVITIES_FROM_BACKGROUND allow; appops set com.jmd.autotext ACCESS_RESTRICTED_SETTINGS allow; appops set com.jmd.autotext AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore; dumpsys deviceidle whitelist +com.jmd.autotext; dpm set-active-admin com.jmd.autotext/.AutoTextDeviceAdminReceiver\""

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
            statusPill("A11y", isA11yActive, Modifier.weight(1f))
            statusPill("Admin", isAdminActive, Modifier.weight(1f))
            statusPill("Overlay", hasOverlay, Modifier.weight(1f))
            statusPill("Secure", hasSecurePerm, Modifier.weight(1f))
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
                            AccessibilityHealthManager.autoApproveAllPermissions(context)
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
 * Information card instructing users how to lock the app into recent tasks to prevent aggressive OEM process killers.
 *
 * @param context Local Android context for resolving OEM device care settings intent.
 */
@Composable
fun keepOpenRecentAppsCard(context: Context = LocalContext.current) {
    appCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔒", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Keep Open in Recent Apps (Prevent Killing)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "To prevent Samsung One UI, Xiaomi, or OnePlus from killing the app or stopping its accessibility engine:\n\n" +
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


