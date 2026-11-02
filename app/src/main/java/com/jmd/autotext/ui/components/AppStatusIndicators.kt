package com.jmd.autotext.ui.components

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.AutomationSafetyManager
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.receiver.AutoTextDeviceAdminReceiver
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.service.AutoTextNotificationListenerService

/**
 * Vivid status indicator color palette strictly conforming to specification:
 * Active/Healthy = Vivid Emerald Green (#00E676)
 * Inactive/Problematic = Vivid Carmine Red (#FF5252)
 */
val IndicatorActiveGreen = Color(0xFF00E676)
val IndicatorInactiveRed = Color(0xFFFF5252)

data class SystemIndicator(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isActive: Boolean,
    val actionLabel: String? = null,
    val onAction: ((Context) -> Unit)? = null
)

object SystemIndicatorResolver {

    @android.annotation.SuppressLint("BatteryLife")
    fun resolveAll(context: Context, prefs: PreferenceManager): List<SystemIndicator> {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

        val isComponentEnabled = AccessibilityHealthManager.isServiceComponentEnabled(context)
        val hasWriteSecure = AccessibilityHealthManager.hasWriteSecureSettings(context)
        val isDynamicStandby = prefs.isDynamicA11yOnDemandEnabled && hasWriteSecure
        val isA11yRunning = AccessibilityHealthManager.isServiceOperational()
        val isA11ySettingOn = AccessibilityHealthManager.isAccessibilitySettingEnabled(context)
        val isA11yHealthy = when {
            !isComponentEnabled -> false
            prefs.isA11yUserPaused -> false
            isDynamicStandby -> true
            else -> isA11yRunning || isA11ySettingOn
        }

        val a11yState = AccessibilityHealthManager.getServiceState(context)
        val a11yName = when {
            !isComponentEnabled -> "AutoText Assistant (Disabled in System)"
            prefs.isA11yUserPaused -> "AutoText Assistant (Paused for Payments)"
            isDynamicStandby && !isA11yRunning -> "AutoText Scheduled Assistant (Bank-Safe Standby)"
            isA11yRunning -> "AutoText Scheduled Assistant (Active)"
            else -> "AutoText Scheduled Assistant"
        }

        val a11yDesc = when {
            !isComponentEnabled -> "The assistant service component was disabled by the system. Tap to re-enable in system settings."
            prefs.isA11yUserPaused -> "Assistant is temporarily paused so banking and payment apps process transactions without interference. Tap to resume."
            isDynamicStandby && !isA11yRunning -> "Bank-Safe Standby: Kept inactive while idle so payment and banking apps run with zero warnings. Automatically connects only during scheduled dispatches."
            isA11yRunning -> "Active in background for scheduled message delivery. Isolated from banking and payment apps."
            else -> "Enables hands-free message sending and lockscreen assistance. Tap to enable."
        }

        val isNotificationListenerActive = AutoTextNotificationListenerService.isListenerConnected ||
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        val isDeviceAdminActive = AutoTextDeviceAdminReceiver.isAdminActive(context)

        val isSchedulerActive = prefs.isTasksMasterEnabled && !AutomationSafetyManager.isEmergencyStopActive(context)

        val isBankingShieldActive = prefs.isBankingShieldEnabled

        val isAutoRotateOff = try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 0
        } catch (_: Exception) {
            true
        }
        val isRotationGuardActive = prefs.isPreventSystemAutoRotateEnabled && isAutoRotateOff

        return listOf(
            SystemIndicator(
                id = "banking_shield_guard",
                name = "Payment Compatibility Guard",
                description = "Protects banking and payment applications (UPI, wallets, net banking) from accessibility alerts by keeping AutoText isolated or detached when idle.",
                icon = Icons.Default.Security,
                isActive = isBankingShieldActive,
                actionLabel = if (!isBankingShieldActive) "Enable Guard" else null,
                onAction = { _ ->
                    prefs.isBankingShieldEnabled = true
                }
            ),
            SystemIndicator(
                id = "rotation_guard",
                name = "Screen Orientation Guard",
                description = "Strictly protects system display settings: ensures auto-rotate remains disabled and keeps screen orientation locked to portrait.",
                icon = Icons.Default.ScreenLockRotation,
                isActive = isRotationGuardActive,
                actionLabel = if (!isRotationGuardActive) "Lock Portrait Orientation" else null,
                onAction = { ctx ->
                    AccessibilityHealthManager.enforceAutoRotateInvariant(ctx)
                }
            ),
            SystemIndicator(
                id = "accessibility",
                name = a11yName,
                description = a11yDesc,
                icon = Icons.Default.AccessibilityNew,
                isActive = isA11yHealthy,
                actionLabel = when {
                    isDynamicStandby && isA11yRunning -> "Safe Detach"
                    isDynamicStandby -> "Test Connection"
                    prefs.isA11yUserPaused && hasWriteSecure -> "Resume Assistant"
                    prefs.isA11yUserPaused -> "Enable in Settings"
                    isA11yRunning -> "Pause for Banking Apps"
                    hasWriteSecure -> "Enable Assistant"
                    else -> "Enable in Settings"
                },
                onAction = { ctx ->
                    if (isDynamicStandby) {
                        if (isA11yRunning) {
                            AccessibilityHealthManager.disableAccessibilityService(ctx)
                            ctx.showAppToast("Safe Standby: Assistant detached for banking safety.")
                        } else {
                            AccessibilityHealthManager.enableAccessibilityService(ctx)
                            ctx.showAppToast("⚡ Connected! AutoText Assistant is ready.")
                        }
                    } else if (isA11yRunning) {
                        prefs.isA11yUserPaused = true
                        AutoTextAccessibilityService.disableServiceIfActive()
                        if (hasWriteSecure) {
                            AccessibilityHealthManager.disableAccessibilityService(ctx)
                        }
                        ctx.showAppToast("Assistant paused. Banking and payment apps isolated.")
                    } else if (hasWriteSecure) {
                        prefs.isA11yUserPaused = false
                        AccessibilityHealthManager.enableAccessibilityService(ctx)
                        ctx.showAppToast("⚡ AutoText Scheduled Assistant Enabled!")
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
            ),
            SystemIndicator(
                id = "notification_listener",
                name = "Notification Quick-Reply Listener",
                description = "Monitors incoming messages and calls to trigger instant auto-replies directly via notification action buttons without opening foreground apps.",
                icon = Icons.Default.NotificationsActive,
                isActive = isNotificationListenerActive,
                actionLabel = if (!isNotificationListenerActive) "Grant Notification Access" else null,
                onAction = { ctx ->
                    runCatching {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    }
                }
            ),
            SystemIndicator(
                id = "device_admin",
                name = "Device Admin & Screen Auto-Lock",
                description = "Allows AutoText to immediately re-lock the phone screen after completing unattended background tasks, securing your device against unauthorized access.",
                icon = Icons.Default.Lock,
                isActive = isDeviceAdminActive,
                actionLabel = if (!isDeviceAdminActive) "Enable Device Admin" else null,
                onAction = { ctx ->
                    runCatching {
                        val intent = AutoTextDeviceAdminReceiver.getAddDeviceAdminIntent(ctx).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    }
                }
            ),
            SystemIndicator(
                id = "battery_opt",
                name = "Unrestricted Battery & Power",
                description = "Ensures the Android operating system never kills the background automation worker, drip campaigns, or scheduled alarms due to battery saver restrictions.",
                icon = Icons.Default.Bolt,
                isActive = isBatteryUnrestricted,
                actionLabel = if (!isBatteryUnrestricted) "Disable Battery Optimization" else null,
                onAction = { ctx ->
                    runCatching {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = "package:${ctx.packageName}".toUri()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    }
                }
            ),
            SystemIndicator(
                id = "task_scheduler",
                name = "Scheduled Dispatcher",
                description = "The master service coordinating scheduled messages, repeating sequences, and daily campaigns. Turns red if master toggle is paused or emergency stop is active.",
                icon = Icons.Default.Schedule,
                isActive = isSchedulerActive,
                actionLabel = if (!isSchedulerActive) "Resume Dispatcher" else null,
                onAction = { ctx ->
                    AutomationSafetyManager.resetEmergencyStop(ctx)
                    prefs.isTasksMasterEnabled = true
                }
            )
        )
    }
}

/**
 * Compact top-header status bar showing indicators strictly as icons.
 * Active = Green (#00E676), Inactive/Disabled = Red (#FF5252).
 */
@Composable
fun appHeaderStatusIndicators(
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
