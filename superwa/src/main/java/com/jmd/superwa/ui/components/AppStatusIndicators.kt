/**
 * Package: com.jmd.superwa.ui.components
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [AppStatusIndicators].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for AppStatusIndicators.
 *
 * Linked With:
 * - Schedulers & Receivers: [android.app.AlarmManager], [com.jmd.superwa.receiver.SuperDeviceAdminReceiver]
 * - Services & Engines: [com.jmd.superwa.service.TaskAccessibilityService], [com.jmd.superwa.service.TaskNotificationListenerService]
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.clip], [androidx.compose.ui.graphics.Color]
 * - Core Security & Utilities: [android.os.PowerManager], [androidx.core.net.toUri], [com.jmd.superwa.core.AccessibilityHealthManager], [com.jmd.superwa.core.OverlayPermissionHelper]
 * - Android Framework & Jetpack: [android.os.Build], [android.provider.Settings], [androidx.compose.foundation.BorderStroke], [androidx.compose.foundation.background]
 */

package com.jmd.superwa.ui.components

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.jmd.superwa.core.AccessibilityHealthManager
import com.jmd.superwa.core.OverlayPermissionHelper
import com.jmd.superwa.core.TaskSafetyManager
import com.jmd.superwa.core.showAppToast
import com.jmd.superwa.data.prefs.PreferenceManager
import com.jmd.superwa.receiver.SuperDeviceAdminReceiver
import com.jmd.superwa.service.TaskAccessibilityService
import com.jmd.superwa.service.TaskNotificationListenerService

/**
 * Vivid status indicator color palette strictly conforming to specification:
 * Active/Healthy = Vivid Emerald Green (#00E676)
 * Inactive/Problematic = Vivid Carmine Red (#FF5252)
 */
val IndicatorActiveGreen = Color(0xFF00E676)
val IndicatorInactiveRed = Color(0xFFFF5252)
/**
 * What it is:
 * Component definition for [SystemIndicator].
 *
 * What it does:
 * Provides domain logic and lifecycle management for SystemIndicator.
 *
 * Linked with:
 * - Component: [AppStatusIndicators]
 */

data class SystemIndicator(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isActive: Boolean,
    val actionLabel: String? = null,
    val onAction: ((Context) -> Unit)? = null
)
/**
 * What it is:
 * Component definition for [SystemIndicatorResolver].
 *
 * What it does:
 * Provides domain logic and lifecycle management for SystemIndicatorResolver.
 *
 * Linked with:
 * - Component: [AppStatusIndicators]
 */

object SystemIndicatorResolver {
    /**
     * What it is:
     * Execution routine for [resolveAll].
     *
     * What it does:
     * Coordinates resolveAll operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppStatusIndicators]
     */

    fun resolveAll(context: Context, prefs: PreferenceManager): List<SystemIndicator> {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

        val isComponentEnabled = AccessibilityHealthManager.isServiceComponentEnabled(context)
        val hasWriteSecure = AccessibilityHealthManager.hasWriteSecureSettings(context)
        val isA11yRunning = AccessibilityHealthManager.isServiceOperational()
        val isA11ySettingOn = AccessibilityHealthManager.isAccessibilitySettingEnabled(context)
        val isA11yHealthy = when {
            !isComponentEnabled -> false
            prefs.isA11yUserPaused -> false
            else -> isA11yRunning || isA11ySettingOn
        }

        val a11yName = when {
            !isComponentEnabled -> "Super Assistant (Disabled in System)"
            prefs.isA11yUserPaused -> "Super Assistant (Paused for Payments)"
            isA11yRunning -> "Super Scheduled Assistant (Active • Bank-Safe)"
            else -> "Super Scheduled Assistant"
        }

        val a11yDesc = when {
            !isComponentEnabled -> "The assistant service component was disabled by the system. Tap to re-enable in system settings."
            prefs.isA11yUserPaused -> "Assistant is temporarily paused so banking and payment apps process transactions without interference. Tap to resume."
            isA11yRunning -> "Active in background for scheduled message delivery. Isolated from banking and payment apps with auto-hide."
            else -> "Enables hands-free message sending and lockscreen assistance. Tap to enable."
        }

        val isNotificationListenerActive = TaskNotificationListenerService.isListenerConnected ||
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        val isDeviceAdminActive = SuperDeviceAdminReceiver.isAdminActive(context)

        val isBankingShieldActive = prefs.isBankingShieldEnabled

        val isOverlayActive = OverlayPermissionHelper.canDrawOverlays(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val isExactAlarmActive = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (alarmManager?.canScheduleExactAlarms() == true)

        val isAutoRotateOff = try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 0
        } catch (e: Exception) {
            android.util.Log.w("AppStatusIndicators", "Failed reading ACCELEROMETER_ROTATION setting: ${e.message}")
            true
        }
        val isRotationGuardActive = prefs.isPreventSystemAutoRotateEnabled && isAutoRotateOff

        return buildList {
            if (com.jmd.superwa.core.AppVariantHelper.isSuperWa) {
                add(
                    SystemIndicator(
                        id = "banking_shield_guard",
                        name = "Payment Compatibility Guard",
                        description = "Protects banking and payment applications (UPI, wallets, net banking) from accessibility alerts by keeping Super isolated or detached when idle.",
                        icon = Icons.Default.Security,
                        isActive = isBankingShieldActive,
                        actionLabel = if (!isBankingShieldActive) "Enable Guard" else null,
                        onAction = { _ ->
                            prefs.isBankingShieldEnabled = true
                        }
                    )
                )
                add(
                    SystemIndicator(
                        id = "rotation_guard",
                        name = "Screen Orientation Guard",
                        description = "Strictly protects system display settings: ensures auto-rotate remains disabled and keeps screen orientation locked to portrait.",
                        icon = Icons.Default.StayCurrentPortrait,
                        isActive = isRotationGuardActive,
                        actionLabel = if (!isRotationGuardActive) "Lock Portrait" else null,
                        onAction = { ctx ->
                            AccessibilityHealthManager.enforceAutoRotateInvariant(ctx)
                        }
                    )
                )
                add(
                    SystemIndicator(
                        id = "accessibility",
                        name = a11yName,
                        description = a11yDesc,
                        icon = Icons.Default.AccessibilityNew,
                        isActive = isA11yHealthy,
                        actionLabel = when {
                            prefs.isA11yUserPaused && hasWriteSecure -> "Resume Assistant"
                            prefs.isA11yUserPaused -> "Open Settings"
                            isA11yRunning -> "Pause Service"
                            hasWriteSecure -> "Enable Assistant"
                            else -> "Open Settings"
                        },
                        onAction = { ctx ->
                            if (isA11yRunning) {
                                prefs.isA11yUserPaused = true
                                TaskAccessibilityService.disableServiceIfActive()
                                if (hasWriteSecure) {
                                    AccessibilityHealthManager.disableAccessibilityService(ctx)
                                }
                                ctx.showAppToast("Assistant paused. Banking and payment apps isolated.")
                            } else if (hasWriteSecure) {
                                prefs.isA11yUserPaused = false
                                AccessibilityHealthManager.enableAccessibilityService(ctx)
                                ctx.showAppToast("Super Scheduled Assistant Enabled!")
                            } else {
                                prefs.isA11yUserPaused = false
                                AccessibilityHealthManager.ensureServiceComponentEnabled(ctx)
                                runCatching {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    ctx.startActivity(intent)
                                }
                            }
                        }
                    )
                )
            }

            add(
                SystemIndicator(
                    id = "notification_listener",
                    name = "Notification Quick-Reply Listener",
                    description = "Monitors incoming messages and calls to trigger instant auto-replies directly via notification action buttons without opening foreground apps.",
                    icon = Icons.Default.NotificationsActive,
                    isActive = isNotificationListenerActive,
                    actionLabel = if (!isNotificationListenerActive) "Grant Access" else null,
                    onAction = { ctx ->
                        runCatching {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(intent)
                        }
                    }
                )
            )
            add(
                SystemIndicator(
                    id = "device_admin",
                    name = "Device Admin & Screen Auto-Lock",
                    description = "Allows Super to immediately re-lock the phone screen after completing unattended background tasks, securing your device against unauthorized access.",
                    icon = Icons.Default.Lock,
                    isActive = isDeviceAdminActive,
                    actionLabel = if (!isDeviceAdminActive) "Device Admin" else null,
                    onAction = { ctx ->
                        SuperDeviceAdminReceiver.requestActivation(ctx)
                    }
                )
            )
            add(
                SystemIndicator(
                    id = "battery_opt",
                    name = "Unrestricted Battery & Power",
                    description = "Ensures the Android operating system does not suspend background tasks, drip campaigns, or scheduled alarms due to battery saver restrictions.",
                    icon = Icons.Default.BatteryChargingFull,
                    isActive = isBatteryUnrestricted,
                    actionLabel = if (!isBatteryUnrestricted) "Battery Access" else null,
                    onAction = { ctx ->
                        runCatching {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(intent)
                        }
                    }
                )
            )
            add(
                SystemIndicator(
                    id = "display_overlay",
                    name = "Display Over Other Apps (Overlay)",
                    description = "Allows Super to display privacy shields, countdown overlays, and incoming call simulation alerts over other applications.",
                    icon = Icons.Default.Layers,
                    isActive = isOverlayActive,
                    actionLabel = if (!isOverlayActive) "Grant Overlay" else null,
                    onAction = { ctx ->
                        OverlayPermissionHelper.launchOverlaySettings(ctx)
                    }
                )
            )
            add(
                SystemIndicator(
                    id = "exact_alarms",
                    name = "Exact Precision Alarms",
                    description = "Guarantees tasks execute at the exact requested second without Doze-mode drift or power jitter.",
                    icon = Icons.Default.Alarm,
                    isActive = isExactAlarmActive,
                    actionLabel = if (!isExactAlarmActive) "Grant Alarm" else null,
                    onAction = { ctx ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = "package:${ctx.packageName}".toUri()
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(intent)
                            }
                        }
                    }
                )
            )
        }
    }
}

/**
 * What it is:
 * Compact top-header status bar showing indicators strictly as icons.
 *
 * What it does:
 * Active = Green (#00E676), Inactive/Disabled = Red (#FF5252).
 *
 * Linked with:
 * - Architecture: [AppStatusIndicators], [AppHeaderStatusIndicators]
 */
@Composable
fun AppHeaderStatusIndicators(
    indicators: List<SystemIndicator>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            indicators.forEach { indicator ->
                val tintColor = if (indicator.isActive) IndicatorActiveGreen else IndicatorInactiveRed
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(tintColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = indicator.icon,
                        contentDescription = indicator.name,
                        tint = tintColor,
                        modifier = Modifier.size(10.5.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusIndicatorsDialog(
    indicators: List<SystemIndicator>,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val allActive = indicators.all { it.isActive }
    val activeCount = indicators.count { it.isActive }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "System Health",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (allActive) IndicatorActiveGreen.copy(alpha = 0.15f) else IndicatorInactiveRed.copy(alpha = 0.15f),
                        border = BorderStroke(0.75.dp, if (allActive) IndicatorActiveGreen.copy(alpha = 0.5f) else IndicatorInactiveRed.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "$activeCount/${indicators.size} Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (allActive) IndicatorActiveGreen else IndicatorInactiveRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "WhatsApp Dispatch Subsystem • Banking Protection Guard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(indicators.size) { index ->
                    val indicator = indicators[index]
                    val tintColor = if (indicator.isActive) IndicatorActiveGreen else IndicatorInactiveRed

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(0.5.dp, tintColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(tintColor.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = indicator.icon,
                                        contentDescription = indicator.name,
                                        tint = tintColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = indicator.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = indicator.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (!indicator.isActive && indicator.actionLabel != null && indicator.onAction != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { indicator.onAction.invoke(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = tintColor.copy(alpha = 0.18f),
                                        contentColor = tintColor
                                    ),
                                    border = BorderStroke(1.dp, tintColor.copy(alpha = 0.6f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = indicator.actionLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = if (indicator.isActive) "ACTIVE" else "OFF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tintColor,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", fontWeight = FontWeight.Bold)
            }
        }
    )
}
