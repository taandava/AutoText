/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [PermissionManager].
 *
 * What it does:
 * Provides comprehensive, verified permission-to-feature mapping, live runtime state verification, graceful degradation rules, and transparent user-directed settings intent routing. Adheres strictly to Part 9 Invariants: 1. Zero root 'su' commands or artificial elevation. 2. Transparent user-directed system settings navigation. 3. Never claims a permission is granted without verifying live system state via official APIs. 4. Application remains completely stable when optional permissions are unavailable.
 *
 * Linked With:
 * - Schedulers & Receivers: [android.app.AlarmManager], [com.jmd.helper.receiver.HelperDeviceAdminReceiver]
 * - Services & Engines: [com.jmd.helper.service.TaskAccessibilityService], [com.jmd.helper.service.TaskNotificationListenerService]
 * - Core Security & Utilities: [android.app.NotificationManager], [android.content.pm.PackageManager], [android.os.PowerManager], [androidx.core.content.ContextCompat]
 * - Android Framework & Jetpack: [android.Manifest], [android.content.ComponentName], [android.os.Build], [android.provider.Settings]
 */

package com.jmd.helper.core

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.jmd.helper.receiver.HelperDeviceAdminReceiver
import com.jmd.helper.service.TaskAccessibilityService
import com.jmd.helper.service.TaskNotificationListenerService

/**
 * What it is:
 * Model describing the architectural mapping and runtime behavior of a system permission.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [PermissionFeatureInfo].
 *
 * Linked with:
 * - Architecture: [PermissionManager], [PermissionFeatureInfo]
 */
data class PermissionFeatureInfo(
    val permissionId: String,
    val featureName: String,
    val purpose: String,
    val canWorkWithout: Boolean,
    val isRuntimeApproval: Boolean,
    val requiresSpecialUserAction: Boolean,
    val deniedImpact: String,
    val revocationSafety: String,
    val isCurrentlyGranted: Boolean = false,
    val category: PermissionCategory = PermissionCategory.CORE
) {
    val isCritical: Boolean
        get() = !canWorkWithout || permissionId == PermissionManager.ID_ACCESSIBILITY || permissionId == PermissionManager.ID_EXACT_ALARMS
}
/**
 * What it is:
 * Component definition for [PermissionCategory].
 *
 * What it does:
 * Provides domain logic and lifecycle management for PermissionCategory.
 *
 * Linked with:
 * - Component: [PermissionManager]
 */

enum class PermissionCategory(val displayName: String) {
    CORE("Core Permissions"),
    TELEPHONY("Telephony & Calls"),
    MESSAGING("SMS & Messaging"),
    SYSTEM("System & Task"),
    MEDIA("Media & Storage")
}

/**
 * What it is:
 * Centralized, verifiable Permission Manager for Helper.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [PermissionManager].
 *
 * Linked with:
 * - Architecture: [PermissionManager], [PermissionManager]
 */
object PermissionManager {

    const val ID_SMS = Manifest.permission.SEND_SMS
    const val ID_RECEIVE_SMS = Manifest.permission.RECEIVE_SMS
    const val ID_CALL_PHONE = Manifest.permission.CALL_PHONE
    const val ID_READ_PHONE_STATE = Manifest.permission.READ_PHONE_STATE
    const val ID_READ_CALL_LOG = Manifest.permission.READ_CALL_LOG
    const val ID_READ_CONTACTS = Manifest.permission.READ_CONTACTS
    const val ID_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val ID_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    const val ID_READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE
    const val ID_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val ID_EXACT_ALARMS = "android.permission.SCHEDULE_EXACT_ALARM"
    const val ID_ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE"
    const val ID_DEVICE_ADMIN = "android.app.action.ADD_DEVICE_ADMIN"
    const val ID_BATTERY_OPTIMIZATION = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
    const val ID_NOTIFICATION_POLICY = Manifest.permission.ACCESS_NOTIFICATION_POLICY
    const val ID_OVERLAY = "android.permission.SYSTEM_ALERT_WINDOW"

    val CRITICAL_PERMISSIONS: Set<String> = setOf(ID_ACCESSIBILITY, ID_EXACT_ALARMS)

    val ALL_SPECIFICATIONS: List<PermissionFeatureInfo> = listOf(
        PermissionFeatureInfo(
            permissionId = ID_SMS,
            featureName = "Cellular SMS Sending",
            purpose = "Allows the background executor to dispatch cellular SMS messages via carrier modem.",
            canWorkWithout = false,
            isRuntimeApproval = true,
            requiresSpecialUserAction = false,
            deniedImpact = "Direct cellular SMS tasks fail with ACTION_REQUIRED or open system messaging app.",
            revocationSafety = "Can be safely revoked anytime; WhatsApp and call tasks remain 100% operational.",
            category = PermissionCategory.MESSAGING
        ),
        PermissionFeatureInfo(
            permissionId = ID_RECEIVE_SMS,
            featureName = "Incoming SMS Auto-Reply & Forwarding",
            purpose = "Allows the SMS receiver to detect incoming messages and trigger quick reply rules.",
            canWorkWithout = true,
            isRuntimeApproval = true,
            requiresSpecialUserAction = false,
            deniedImpact = "Auto-reply rules for incoming SMS will not trigger.",
            revocationSafety = "Can be safely revoked anytime without affecting scheduled outbound tasks.",
            category = PermissionCategory.MESSAGING
        ),
        PermissionFeatureInfo(
            permissionId = ID_CALL_PHONE,
            featureName = "Direct Phone Call Placement",
            purpose = "Allows Helper to dial scheduled phone calls directly without requiring manual user tap in dialer.",
            canWorkWithout = true,
            isRuntimeApproval = true,
            requiresSpecialUserAction = false,
            deniedImpact = "Helper opens system dialer (ACTION_DIAL) with number pre-filled; user must tap call.",
            revocationSafety = "Can be revoked safely; fallback to ACTION_DIAL is direct.",
            category = PermissionCategory.TELEPHONY
        ),
        PermissionFeatureInfo(
            permissionId = ID_READ_PHONE_STATE,
            featureName = "Call State & Connected Timing",
            purpose = "Allows Helper to detect when a dialed call is answered (OFFHOOK) vs ringing.",
            canWorkWithout = true,
            isRuntimeApproval = true,
            requiresSpecialUserAction = false,
            deniedImpact = "Call duration timer counts from moment of dialing rather than moment of answer.",
            revocationSafety = "Safe to revoke anytime.",
            category = PermissionCategory.TELEPHONY
        ),
        PermissionFeatureInfo(
            permissionId = ID_READ_CALL_LOG,
            featureName = "Missed Call Auto-Reply",
            purpose = "Distinguishes missed/rejected incoming calls from completed conversations and unanswered outgoing dials.",
            canWorkWithout = true,
            isRuntimeApproval = true,
            requiresSpecialUserAction = false,
            deniedImpact = "Missed call auto-reply rules may not trigger reliably or outgoing dials will be ignored defensively.",
            revocationSafety = "Safe to revoke anytime.",
            category = PermissionCategory.TELEPHONY
        ),
        PermissionFeatureInfo(
            permissionId = ID_READ_CONTACTS,
            featureName = "Contact Picker & Name Resolution",
            purpose = "Provides contact search, phone number suggestions, and personalized name token ({name}) resolution.",
            canWorkWithout = true,
            isRuntimeApproval = true,
            requiresSpecialUserAction = false,
            deniedImpact = "User can still enter phone numbers manually or import via CSV; suggestions are disabled.",
            revocationSafety = "Safe to revoke anytime.",
            category = PermissionCategory.CORE
        ),
        PermissionFeatureInfo(
            permissionId = ID_POST_NOTIFICATIONS,
            featureName = "Execution Alerts & Reminders",
            purpose = "Displays task completion summaries, reminder heads-up alerts, and error feedback.",
            canWorkWithout = true,
            isRuntimeApproval = true,
            requiresSpecialUserAction = false,
            deniedImpact = "Tasks execute normally in background; completion alerts and reminders are suppressed.",
            revocationSafety = "Safe to revoke anytime.",
            category = PermissionCategory.SYSTEM
        ),
        PermissionFeatureInfo(
            permissionId = ID_EXACT_ALARMS,
            featureName = "Exact Precision Scheduling",
            purpose = "Guarantees tasks execute at the exact requested second without Doze-mode drift or power jitter.",
            canWorkWithout = false,
            isRuntimeApproval = false,
            requiresSpecialUserAction = true,
            deniedImpact = "Android may delay scheduled task execution by 15-60 minutes in battery saving modes.",
            revocationSafety = "Safe to revoke, but scheduling precision is compromised.",
            category = PermissionCategory.SYSTEM
        ),
        PermissionFeatureInfo(
            permissionId = ID_ACCESSIBILITY,
            featureName = "Task",
            purpose = "Assists in scheduled WhatsApp message sending, status publishing, and secure screen task assistance.",
            canWorkWithout = true,
            isRuntimeApproval = false,
            requiresSpecialUserAction = true,
            deniedImpact = "Cellular SMS, calls, alarms, and call simulations work normally. WhatsApp assisted clicks are disabled.",
            revocationSafety = "Can be safely disabled anytime; dynamic on-demand mode keeps it detached during idle.",
            category = PermissionCategory.SYSTEM
        ),
        PermissionFeatureInfo(
            permissionId = ID_DEVICE_ADMIN,
            featureName = "Hardware Screen Auto-Lock",
            purpose = "Allows Helper to immediately re-lock the phone via simulated power button lock after unattended tasks conclude.",
            canWorkWithout = true,
            isRuntimeApproval = false,
            requiresSpecialUserAction = true,
            deniedImpact = "Screen lock postcondition falls back to returning to home launcher; device may remain awake.",
            revocationSafety = "Safe to revoke anytime in Settings > Security.",
            category = PermissionCategory.SYSTEM
        ),
        PermissionFeatureInfo(
            permissionId = ID_BATTERY_OPTIMIZATION,
            featureName = "Unrestricted Battery & Power",
            purpose = "Prevents OEM battery optimization from suspending alarms, campaigns, and scheduled tasks.",
            canWorkWithout = true,
            isRuntimeApproval = false,
            requiresSpecialUserAction = true,
            deniedImpact = "OEM system may suspend scheduled tasks or alarms when screen is off for extended periods.",
            revocationSafety = "Safe to revoke anytime.",
            category = PermissionCategory.SYSTEM
        ),
        PermissionFeatureInfo(
            permissionId = ID_NOTIFICATION_POLICY,
            featureName = "Sound Mode & DND Profile Switching",
            purpose = "Enables changing ringer mode across Silent, Vibrate, and Normal states.",
            canWorkWithout = true,
            isRuntimeApproval = false,
            requiresSpecialUserAction = true,
            deniedImpact = "Sound mode scheduler tasks will record ACTION_REQUIRED without crashing.",
            revocationSafety = "Safe to revoke anytime.",
            category = PermissionCategory.SYSTEM
        ),
        PermissionFeatureInfo(
            permissionId = ID_OVERLAY,
            featureName = "Display Over Other Apps (Overlay)",
            purpose = "Allows Helper to display privacy shields, countdown overlays, and incoming call simulation alerts over other applications.",
            canWorkWithout = true,
            isRuntimeApproval = false,
            requiresSpecialUserAction = true,
            deniedImpact = "Privacy shields, countdown floating alerts, and full screen call simulation overlays cannot display over other apps.",
            revocationSafety = "Safe to revoke anytime.",
            category = PermissionCategory.SYSTEM
        ),
        PermissionFeatureInfo(
            permissionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ID_READ_MEDIA_IMAGES else ID_READ_EXTERNAL_STORAGE,
            featureName = "Media & Attachment Access",
            purpose = "Allows attaching photos and videos to scheduled WhatsApp and MMS tasks.",
            canWorkWithout = true,
            isRuntimeApproval = true,
            requiresSpecialUserAction = false,
            deniedImpact = "Text-only messaging and calling work normally; media file attachments cannot be loaded.",
            revocationSafety = "Safe to revoke anytime.",
            category = PermissionCategory.MEDIA
        )
    )
    /**
     * What it is:
     * Execution routine for [getAllPermissions].
     *
     * What it does:
     * Coordinates getAllPermissions operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [PermissionManager]
     */

    fun getAllPermissions(): List<PermissionFeatureInfo> = ALL_SPECIFICATIONS
    /**
     * What it is:
     * Execution routine for [getPermission].
     *
     * What it does:
     * Coordinates getPermission operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [PermissionManager]
     */

    fun getPermission(permissionId: String): PermissionFeatureInfo? =
        ALL_SPECIFICATIONS.firstOrNull { it.permissionId == permissionId }

    /**
     * What it is:
     * Inspects live status of a standard Android runtime permission.
     *
     * What it does:
     * Executes [isPermissionGranted] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [isPermissionGranted]
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * What it is:
     * Checks if the app can dispatch cellular SMS messages directly.
     *
     * What it does:
     * Executes [hasSmsSendPermission] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [hasSmsSendPermission]
     */
    fun hasSmsSendPermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.SEND_SMS)

    /**
     * What it is:
     * Checks if the app can read incoming SMS messages for auto-reply rules.
     *
     * What it does:
     * Executes [hasSmsReadPermission] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [hasSmsReadPermission]
     */
    fun hasSmsReadPermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.READ_SMS) &&
        isPermissionGranted(context, Manifest.permission.RECEIVE_SMS)

    /**
     * What it is:
     * Checks if the app can place direct cellular phone calls.
     *
     * What it does:
     * Executes [hasCallPhonePermission] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [hasCallPhonePermission]
     */
    fun hasCallPhonePermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.CALL_PHONE)

    /**
     * What it is:
     * Checks if the app can inspect telephony radio state for call connection detection.
     *
     * What it does:
     * Executes [hasPhoneStatePermission] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [hasPhoneStatePermission]
     */
    fun hasPhoneStatePermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.READ_PHONE_STATE)

    /**
     * What it is:
     * Checks if the app can inspect CallLog to discriminate missed calls from completed calls.
     *
     * What it does:
     * Executes [hasCallLogPermission] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [hasCallLogPermission]
     */
    fun hasCallLogPermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.READ_CALL_LOG)

    /**
     * What it is:
     * Checks if the app can access device contacts for recipient selection and auto-reply filters.
     *
     * What it does:
     * Executes [hasContactsPermission] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [hasContactsPermission]
     */
    fun hasContactsPermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.READ_CONTACTS)

    /**
     * What it is:
     * Checks if the app can access device media files for photo/video attachments.
     *
     * What it does:
     * Executes [hasMediaPermission] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [hasMediaPermission]
     */
    fun hasMediaPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.READ_MEDIA_IMAGES) &&
                isPermissionGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            isPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * What it is:
     * Resolves the list of runtime permissions needed for media attachments based on platform version.
     *
     * What it does:
     * Executes [getRequiredMediaPermissions] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [getRequiredMediaPermissions]
     */
    fun getRequiredMediaPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * What it is:
     * Checks if the app can post notifications on Android 13+ (API 33+).
     *
     * What it does:
     * Executes [hasPostNotificationsPermission] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [hasPostNotificationsPermission]
     */
    fun hasPostNotificationsPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * What it is:
     * Checks if the app can schedule exact RTC_WAKEUP alarms on Android 12+ (API 31+).
     *
     * What it does:
     * Executes [canScheduleExactAlarms] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [canScheduleExactAlarms]
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    /**
     * What it is:
     * Checks if battery optimization is ignored for Helper background survival.
     *
     * What it does:
     * Executes [isBatteryOptimizationIgnored] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [isBatteryOptimizationIgnored]
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * What it is:
     * Checks if Helper Accessibility Service is active and bound.
     *
     * What it does:
     * Executes [isAccessibilityOperational] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [isAccessibilityOperational]
     */
    fun isAccessibilityOperational(context: Context): Boolean {
        return TaskAccessibilityService.isServiceRunning || AccessibilityHealthManager.isAccessibilityEnabledInSystem(context)
    }

    /**
     * What it is:
     * Checks if notification policy access (DND control) is granted.
     *
     * What it does:
     * Executes [hasNotificationPolicyAccess] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [hasNotificationPolicyAccess]
     */
    fun hasNotificationPolicyAccess(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return notificationManager?.isNotificationPolicyAccessGranted ?: false
    }

    /**
     * What it is:
     * Checks if Device Administration is active for instant hardware screen re-locking.
     *
     * What it does:
     * Executes [isDeviceAdminActive] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [isDeviceAdminActive]
     */
    fun isDeviceAdminActive(context: Context): Boolean {
        return HelperDeviceAdminReceiver.isAdminActive(context)
    }

    /**
     * What it is:
     * Checks if Notification Listener Service is enabled in system settings.
     *
     * What it does:
     * Executes [isNotificationListenerActive] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [isNotificationListenerActive]
     */
    fun isNotificationListenerActive(context: Context): Boolean {
        val cr = context.contentResolver
        val expected = ComponentName(context, TaskNotificationListenerService::class.java).flattenToString()
        val current = Settings.Secure.getString(cr, "enabled_notification_listeners") ?: ""
        return current.contains(expected)
    }

    /**
     * What it is:
     * Comprehensive live audit generating the complete Permission-to-Feature mapping.
     *
     * What it does:
     * Executes [getPermissionFeatureCatalog] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [getPermissionFeatureCatalog]
     */
    fun getPermissionFeatureCatalog(context: Context): List<PermissionFeatureInfo> {
        return ALL_SPECIFICATIONS.map { spec ->
            val granted = when (spec.permissionId) {
                ID_SMS -> hasSmsSendPermission(context)
                ID_RECEIVE_SMS -> isPermissionGranted(context, Manifest.permission.RECEIVE_SMS)
                ID_CALL_PHONE -> hasCallPhonePermission(context)
                ID_READ_PHONE_STATE -> hasPhoneStatePermission(context)
                ID_READ_CALL_LOG -> hasCallLogPermission(context)
                ID_READ_CONTACTS -> hasContactsPermission(context)
                ID_POST_NOTIFICATIONS -> hasPostNotificationsPermission(context)
                ID_EXACT_ALARMS -> canScheduleExactAlarms(context)
                ID_ACCESSIBILITY -> isAccessibilityOperational(context)
                ID_DEVICE_ADMIN -> isDeviceAdminActive(context)
                ID_BATTERY_OPTIMIZATION -> isBatteryOptimizationIgnored(context)
                ID_NOTIFICATION_POLICY -> hasNotificationPolicyAccess(context)
                ID_OVERLAY -> hasOverlayPermission(context)
                else -> isPermissionGranted(context, spec.permissionId)
            }
            spec.copy(isCurrentlyGranted = granted)
        }
    }

    /**
     * What it is:
     * Generates standard, transparent user-directed settings intent for resolving permissions.
     *
     * What it does:
     * Executes [createSettingsIntent] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [createSettingsIntent]
     */
    fun createSettingsIntent(context: Context, permissionId: String): Intent {
        return when (permissionId) {
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" -> {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
            ID_EXACT_ALARMS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                } else {
                    createAppDetailsIntent(context)
                }
            }
            "android.permission.BIND_ACCESSIBILITY_SERVICE" -> {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
            "android.service.notification.NotificationListenerService" -> {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            Manifest.permission.ACCESS_NOTIFICATION_POLICY -> {
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            }
            ID_OVERLAY,
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION -> {
                OverlayPermissionHelper.getManageOverlayIntent(context)
            }
            else -> createAppDetailsIntent(context)
        }
    }

    /**
     * What it is:
     * Dispatches intent to launch OEM-specific background management or autostart settings.
     *
     * What it does:
     * Executes [openOemBackgroundSettings] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [openOemBackgroundSettings]
     */
    fun openOemBackgroundSettings(context: Context): Boolean =
        OemBrandHelper.openOemBackgroundSettings(context)

    /**
     * What it is:
     * Checks if the app has permission to display overlays on top of other applications.
     *
     * What it does:
     * Executes [hasOverlayPermission] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [hasOverlayPermission]
     */
    fun hasOverlayPermission(context: Context): Boolean =
        OverlayPermissionHelper.canDrawOverlays(context)

    /**
     * What it is:
     * Standard Application Details settings intent.
     *
     * What it does:
     * Executes [createAppDetailsIntent] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PermissionManager], [createAppDetailsIntent]
     */
    fun createAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
