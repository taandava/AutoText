package com.jmd.autotext.ui.components

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.Security
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
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.receiver.AutoTextDeviceAdminReceiver
import com.jmd.autotext.service.AutoTextNotificationListenerService
import com.jmd.autotext.core.AutomationSafetyManager
import com.jmd.autotext.data.prefs.PreferenceManager

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

        val isA11yOperational = AccessibilityHealthManager.isAccessibilitySettingEnabled(context) &&
            AccessibilityHealthManager.isServiceOperational()

        val isNotificationListenerActive = AutoTextNotificationListenerService.isListenerConnected ||
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        val isDeviceAdminActive = AutoTextDeviceAdminReceiver.isAdminActive(context)

        val isSchedulerActive = prefs.isTasksMasterEnabled && !AutomationSafetyManager.isEmergencyStopActive(context)

        val isStealthActive = prefs.isBankingShieldEnabled

        val isAutoRotateOff = try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 0
        } catch (_: Exception) {
            true
        }
        val isRotationGuardActive = prefs.isPreventSystemAutoRotateEnabled && isAutoRotateOff

        return listOf(
            SystemIndicator(
                id = "stealth_guard",
                name = "Stealth Guard (Banking Shield)",
                description = "Isolates automation from banking, payment, and UPI apps so your bank apps remain functional without accessibility alerts. Always active by default.",
                icon = Icons.Default.Security,
                isActive = isStealthActive,
                actionLabel = if (!isStealthActive) "Enable Shield" else null,
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
                name = "Accessibility Service Engine",
                description = "Enables automated UI interaction, touch simulation, WhatsApp navigation, and message dispatch. When inactive, only SMS and call dispatch operate.",
                icon = Icons.Default.AccessibilityNew,
                isActive = isA11yOperational,
                actionLabel = if (!isA11yOperational) "Open Accessibility Settings" else null,
                onAction = { ctx ->
                    runCatching {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
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
                name = "Task Scheduler Engine",
                description = "The master control engine coordinating scheduled tasks, repeating sequences, and daily campaigns. Turns red if master toggle is paused or emergency stop is active.",
                icon = Icons.Default.Schedule,
                isActive = isSchedulerActive,
                actionLabel = if (!isSchedulerActive) "Resume Engine" else null,
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
