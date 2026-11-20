/**
 * Package: com.jmd.supersms.ui.screen.permission
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [AppPrivilegeScreen].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for AppPrivilegeScreen.
 *
 * Linked With:
 * - Schedulers & Receivers: [android.app.AlarmManager]
 * - UI Screens & Components: [androidx.compose.ui.graphics.vector.ImageVector]
 * - Core Security & Utilities: [android.os.PowerManager], [androidx.core.app.NotificationManagerCompat], [androidx.core.net.toUri], [com.jmd.supersms.core.AccessibilityHealthManager]
 * - Android Framework & Jetpack: [android.os.Build], [android.provider.Settings], [androidx.compose.material.icons.Icons], [androidx.compose.material.icons.automirrored.filled.VolumeUp]
 */

package com.jmd.supersms.ui.screen.permission

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
import com.jmd.supersms.core.OverlayPermissionHelper
import com.jmd.supersms.core.PermissionManager

/**
 * What it is:
 * Functional category for organizing system privileges in Permission Center.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [PrivilegeCategory].
 *
 * Linked with:
 * - Architecture: [AppPrivilegeScreen], [PrivilegeCategory]
 */
enum class PrivilegeCategory(val title: String, val subtitle: String) {
    CORE(
        title = "Core Tasks",
        subtitle = "Core Services"
    ),
    SYSTEM(
        title = "System Privileges",
        subtitle = "Background Privileges"
    ),
    COMMUNICATION(
        title = "Device Controls",
        subtitle = "Device Policies"
    )
}

/**
 * What it is:
 * Strongly typed definition of all 8 system privileges managed by Super.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [AppPrivilege].
 *
 * Linked with:
 * - Architecture: [AppPrivilegeScreen], [AppPrivilege]
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
        title = "Accessibility Service",
        subtitle = "Powers scheduled messaging dispatch and hands-free assistance.",
        detailedPurpose = "Allows Super to interact with approved messaging applications at user-scheduled times to populate and dispatch tasks. Never reads personal data or interacts with banking apps.",
        icon = Icons.Default.VerifiedUser,
        category = PrivilegeCategory.CORE,
        buttonLabel = "Enable"
    ),
    EXACT_ALARM(
        title = "Exact Alarms",
        subtitle = "Fires scheduled tasks at the exact specified second without battery jitter.",
        detailedPurpose = "Required on Android 12+ (API 31+) to invoke high-precision RTC alarms, ensuring time-critical tasks trigger promptly without system delay.",
        icon = Icons.Default.Alarm,
        category = PrivilegeCategory.CORE,
        buttonLabel = "Grant"
    ),
    NOTIFICATION_LISTENER(
        title = "Notification Access",
        subtitle = "Detects incoming chats for reply rules and quick responses.",
        detailedPurpose = "Enables Super to read notification actions in messaging apps to process auto-replies directly in the background without needing to illuminate the display.",
        icon = Icons.Default.NotificationsActive,
        category = PrivilegeCategory.CORE,
        buttonLabel = "Enable"
    ),
    BATTERY_SAVER(
        title = "Battery Saver",
        subtitle = "Prevents system power savers from freezing scheduled timers.",
        detailedPurpose = "Adds Super to the system battery optimization allowlist, ensuring background task schedulers are never frozen during Doze mode.",
        icon = Icons.Default.BatteryChargingFull,
        category = PrivilegeCategory.SYSTEM,
        buttonLabel = "Exempt"
    ),
    OVERLAY(
        title = "Display Overlays",
        subtitle = "Renders privacy shields, countdown overlays, and incoming alerts.",
        detailedPurpose = "Required to render transient privacy overlays during scheduled screen tasks and display simulated call interfaces.",
        icon = Icons.Default.Layers,
        category = PrivilegeCategory.SYSTEM,
        buttonLabel = "Grant"
    ),
    COMMUNICATIONS(
        title = "Device Communications",
        subtitle = "Auto-discovers contacts and permits direct cellular messaging and calling.",
        detailedPurpose = "Required to populate recipient contact names, dispatch carrier SMS tasks, and handle incoming call dispatch.",
        icon = Icons.Default.Contacts,
        category = PrivilegeCategory.COMMUNICATION,
        buttonLabel = "Grant All"
    ),
    NOTIFICATION_POLICY(
        title = "Sound Policy",
        subtitle = "Required for Sound Mode Switcher (Silent / Vibrate / Normal).",
        detailedPurpose = "Allows Super to programmatically adjust the system audio stream and sound mode profile between Normal, Vibrate, and Silent states.",
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        category = PrivilegeCategory.COMMUNICATION,
        buttonLabel = "Grant"
    );
    /**
     * What it is:
     * Execution routine for [isGranted].
     *
     * What it does:
     * Coordinates isGranted operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppPrivilegeScreen]
     */

    fun isGranted(context: Context): Boolean = when (this) {
        ACCESSIBILITY -> false
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
    /**
     * What it is:
     * Execution routine for [openSettings].
     *
     * What it does:
     * Coordinates openSettings operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppPrivilegeScreen]
     */

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

    companion object {
        val supportedEntries: List<AppPrivilege>
            get() = if (com.jmd.supersms.core.AppVariantHelper.isSuperSms) {
                entries.filter { it != ACCESSIBILITY }
            } else {
                entries
            }
    }
}
