/**
 * Package: com.jmd.helper.ui.screen.permission
 * Module: UI Permission Center - Privilege Domain Models & Evaluation Logic
 */
package com.jmd.helper.ui.screen.permission

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.OverlayPermissionHelper
import com.jmd.helper.core.PermissionManager
import com.jmd.helper.receiver.HelperDeviceAdminReceiver

/**
 * Functional category for organizing system privileges in Permission Center.
 */
enum class PrivilegeCategory(val title: String, val subtitle: String) {
    CORE(
        title = "Core Automation Privileges",
        subtitle = "Foundational services powering scheduled execution"
    ),
    SYSTEM(
        title = "System & Power Privileges",
        subtitle = "Prevent OS task termination and background throttling"
    ),
    COMMUNICATION(
        title = "Device & Communication Privileges",
        subtitle = "Screen locking, messaging, and audio policy controls"
    )
}

/**
 * Strongly typed definition of all 8 system privileges managed by Helper.
 */
enum class AppPrivilege(
    val title: String,
    val subtitle: String,
    val detailedPurpose: String,
    val icon: ImageVector,
    val category: PrivilegeCategory,
    val buttonLabel: String
) {
    ACCESSIBILITY(
        title = "Task Accessibility Service",
        subtitle = "Powers scheduled messaging dispatch and hands-free assistance.",
        detailedPurpose = "Allows Helper to interact with approved messaging applications at user-scheduled times to populate and dispatch tasks. Never reads personal data or interacts with banking apps.",
        icon = Icons.Default.VerifiedUser,
        category = PrivilegeCategory.CORE,
        buttonLabel = "Enable"
    ),
    EXACT_ALARM(
        title = "Schedule Exact Alarms",
        subtitle = "Fires scheduled tasks at the exact specified second without battery jitter.",
        detailedPurpose = "Required on Android 12+ (API 31+) to invoke high-precision RTC alarms, ensuring time-critical tasks trigger promptly without system delay.",
        icon = Icons.Default.Alarm,
        category = PrivilegeCategory.CORE,
        buttonLabel = "Grant"
    ),
    NOTIFICATION_LISTENER(
        title = "Notification Listener Access",
        subtitle = "Detects incoming chats for automated rules and quick responses.",
        detailedPurpose = "Enables Helper to read notification actions in messaging apps to process auto-replies directly in the background without needing to illuminate the display.",
        icon = Icons.Default.NotificationsActive,
        category = PrivilegeCategory.CORE,
        buttonLabel = "Enable"
    ),
    BATTERY_SAVER(
        title = "Battery Saver Exemption",
        subtitle = "Prevents system power savers from freezing scheduled timers.",
        detailedPurpose = "Adds Helper to the system battery optimization allowlist, ensuring background task schedulers are never frozen during Doze mode.",
        icon = Icons.Default.BatteryChargingFull,
        category = PrivilegeCategory.SYSTEM,
        buttonLabel = "Exempt"
    ),
    OVERLAY(
        title = "Display Over Other Apps",
        subtitle = "Renders privacy shields, countdown overlays, and incoming alerts.",
        detailedPurpose = "Required to render transient privacy overlays during automated screen actions and display simulated call interfaces.",
        icon = Icons.Default.Layers,
        category = PrivilegeCategory.SYSTEM,
        buttonLabel = "Grant"
    ),
    DEVICE_ADMIN(
        title = "Device Administration (Lock Screen)",
        subtitle = "Hardware screen lock authorization to secure phone post-task.",
        detailedPurpose = "Grants standard device administration privileges solely used to secure the display immediately after scheduled tasks conclude.",
        icon = Icons.Default.AdminPanelSettings,
        category = PrivilegeCategory.COMMUNICATION,
        buttonLabel = "Activate"
    ),
    COMMUNICATIONS(
        title = "Contacts, SMS & Phone Access",
        subtitle = "Auto-discovers contacts and permits direct cellular messaging and calling.",
        detailedPurpose = "Required to populate recipient contact names, dispatch carrier SMS tasks, and handle incoming call automation.",
        icon = Icons.Default.Contacts,
        category = PrivilegeCategory.COMMUNICATION,
        buttonLabel = "Grant All"
    ),
    NOTIFICATION_POLICY(
        title = "Do Not Disturb & Sound Policy",
        subtitle = "Required for Sound Mode Switcher (Silent / Vibrate / Normal).",
        detailedPurpose = "Allows Helper to programmatically adjust the system audio stream and sound mode profile between Normal, Vibrate, and Silent states.",
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        category = PrivilegeCategory.COMMUNICATION,
        buttonLabel = "Grant"
    );

    fun isGranted(context: Context): Boolean = when (this) {
        ACCESSIBILITY -> {
            val state = AccessibilityHealthManager.getServiceState(context)
            state == AccessibilityHealthManager.ServiceState.OPERATIONAL ||
                    state == AccessibilityHealthManager.ServiceState.STANDBY
        }
        EXACT_ALARM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() == true
            } else true
        }
        NOTIFICATION_LISTENER -> {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }
        BATTERY_SAVER -> {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        OVERLAY -> OverlayPermissionHelper.canDrawOverlays(context)
        DEVICE_ADMIN -> HelperDeviceAdminReceiver.isAdminActive(context)
        COMMUNICATIONS -> {
            PermissionManager.hasContactsPermission(context) &&
                    PermissionManager.hasSmsSendPermission(context) &&
                    PermissionManager.hasCallPhonePermission(context) &&
                    PermissionManager.hasPhoneStatePermission(context) &&
                    PermissionManager.hasCallLogPermission(context) &&
                    PermissionManager.hasMediaPermission(context) &&
                    PermissionManager.hasPostNotificationsPermission(context)
        }
        NOTIFICATION_POLICY -> PermissionManager.hasNotificationPolicyAccess(context)
    }

    fun openSettings(context: Context) {
        when (this) {
            ACCESSIBILITY -> {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = "package:${context.packageName}".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
            NOTIFICATION_LISTENER -> {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            BATTERY_SAVER -> {
                try {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
            OVERLAY -> {
                if (!OverlayPermissionHelper.launchOverlaySettings(context)) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
            DEVICE_ADMIN -> {
                HelperDeviceAdminReceiver.requestActivation(context)
            }
            COMMUNICATIONS -> {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            NOTIFICATION_POLICY -> {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
        }
    }
}
