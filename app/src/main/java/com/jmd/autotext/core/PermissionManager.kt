/**
 * Package: com.jmd.autotext.core
 * Module: Permission Management & Verification Layer
 *
 * Provides comprehensive, verified permission-to-feature mapping, live runtime state verification,
 * graceful degradation rules, and transparent user-directed settings intent routing.
 *
 * Adheres strictly to Part 9 Invariants:
 * 1. Zero root 'su' commands or artificial elevation.
 * 2. Transparent user-directed system settings navigation.
 * 3. Never claims a permission is granted without verifying live system state via official APIs.
 * 4. Application remains completely stable when optional permissions are unavailable.
 */
package com.jmd.autotext.core

import android.annotation.SuppressLint
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
import com.jmd.autotext.receiver.AutoTextDeviceAdminReceiver
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.service.AutoTextNotificationListenerService

/**
 * Model describing the architectural mapping and runtime behavior of a system permission.
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

enum class PermissionCategory(val displayName: String) {
    CORE("Core Permissions"),
    TELEPHONY("Telephony & Calls"),
    MESSAGING("SMS & Messaging"),
    SYSTEM("System & Automation"),
    MEDIA("Media & Storage")
}

/**
 * Centralized, verifiable Permission Manager for AutoText.
 */
@SuppressLint("InlinedApi", "BatteryLife")
object PermissionManager {

    const val ID_SMS = Manifest.permission.SEND_SMS
    const val ID_RECEIVE_SMS = Manifest.permission.RECEIVE_SMS
    const val ID_CALL_PHONE = Manifest.permission.CALL_PHONE
    const val ID_READ_PHONE_STATE = Manifest.permission.READ_PHONE_STATE
    const val ID_READ_CALL_LOG = Manifest.permission.READ_CALL_LOG
    const val ID_READ_CONTACTS = Manifest.permission.READ_CONTACTS
    const val ID_POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
    const val ID_EXACT_ALARMS = Manifest.permission.SCHEDULE_EXACT_ALARM
    const val ID_ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE"
    const val ID_DEVICE_ADMIN = "android.app.action.ADD_DEVICE_ADMIN"
    const val ID_BATTERY_OPTIMIZATION = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    const val ID_NOTIFICATION_POLICY = Manifest.permission.ACCESS_NOTIFICATION_POLICY

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
            purpose = "Allows the SMS receiver to detect incoming messages and trigger automated reply rules.",
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
            purpose = "Allows AutoText to dial scheduled phone calls directly without requiring manual user tap in dialer.",
            canWorkWithout = true,
            isRuntimeApproval = true,
            requiresSpecialUserAction = false,
            deniedImpact = "AutoText opens system dialer (ACTION_DIAL) with number pre-filled; user must tap call.",
            revocationSafety = "Can be revoked safely; fallback to ACTION_DIAL is automatic.",
            category = PermissionCategory.TELEPHONY
        ),
        PermissionFeatureInfo(
            permissionId = ID_READ_PHONE_STATE,
            featureName = "Call State & Connected Timing",
            purpose = "Allows AutoText to detect when a dialed call is answered (OFFHOOK) vs ringing.",
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
            featureName = "Accessibility Service",
            purpose = "Assists in automated WhatsApp message sending, status publishing, and lockscreen PIN input.",
            canWorkWithout = true,
            isRuntimeApproval = false,
            requiresSpecialUserAction = true,
            deniedImpact = "Cellular SMS, calls, alarms, and fake calls work normally. WhatsApp automated clicks are disabled.",
            revocationSafety = "Can be safely disabled anytime; dynamic on-demand mode automatically keeps it detached during idle.",
            category = PermissionCategory.SYSTEM
        ),
        PermissionFeatureInfo(
            permissionId = ID_DEVICE_ADMIN,
            featureName = "Hardware Screen Auto-Lock",
            purpose = "Allows AutoText to immediately re-lock the phone via lockNow() after unattended tasks conclude.",
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
            purpose = "Prevents OEM aggressive process killer from terminating alarms, campaigns, and scheduled tasks.",
            canWorkWithout = true,
            isRuntimeApproval = false,
            requiresSpecialUserAction = true,
            deniedImpact = "OEM system may kill scheduled tasks or alarms when screen is off for extended periods.",
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
        )
    )

    fun getAllPermissions(): List<PermissionFeatureInfo> = ALL_SPECIFICATIONS

    fun getPermission(permissionId: String): PermissionFeatureInfo? =
        ALL_SPECIFICATIONS.firstOrNull { it.permissionId == permissionId }

    /**
     * Inspects live status of a standard Android runtime permission.
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the app can dispatch cellular SMS messages directly.
     */
    fun hasSmsSendPermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.SEND_SMS)

    /**
     * Checks if the app can read incoming SMS messages for auto-reply rules.
     */
    fun hasSmsReadPermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.READ_SMS) &&
        isPermissionGranted(context, Manifest.permission.RECEIVE_SMS)

    /**
     * Checks if the app can place direct cellular phone calls.
     */
    fun hasCallPhonePermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.CALL_PHONE)

    /**
     * Checks if the app can inspect telephony radio state for call connection detection.
     */
    fun hasPhoneStatePermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.READ_PHONE_STATE)

    /**
     * Checks if the app can inspect CallLog to discriminate missed calls from completed calls.
     */
    fun hasCallLogPermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.READ_CALL_LOG)

    /**
     * Checks if the app can access device contacts for recipient selection and auto-reply filters.
     */
    fun hasContactsPermission(context: Context): Boolean =
        isPermissionGranted(context, Manifest.permission.READ_CONTACTS)

    /**
     * Checks if the app can post notifications on Android 13+ (API 33+).
     */
    fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    /**
     * Checks if the app can schedule exact RTC_WAKEUP alarms on Android 12+ (API 31+).
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
     * Checks if battery optimization is ignored for AutoText background survival.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * Checks if AutoText Accessibility Service is active and bound.
     */
    fun isAccessibilityOperational(context: Context): Boolean {
        return AutoTextAccessibilityService.isServiceRunning || AccessibilityHealthManager.isAccessibilityEnabledInSystem(context)
    }

    /**
     * Checks if notification policy access (DND control) is granted.
     */
    fun hasNotificationPolicyAccess(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return notificationManager?.isNotificationPolicyAccessGranted ?: false
    }

    /**
     * Checks if Device Administration is active for instant hardware screen re-locking.
     */
    fun isDeviceAdminActive(context: Context): Boolean {
        return AutoTextDeviceAdminReceiver.isAdminActive(context)
    }

    /**
     * Checks if Notification Listener Service is enabled in system settings.
     */
    fun isNotificationListenerActive(context: Context): Boolean {
        val cr = context.contentResolver
        val expected = ComponentName(context, AutoTextNotificationListenerService::class.java).flattenToString()
        val current = Settings.Secure.getString(cr, "enabled_notification_listeners") ?: ""
        return current.contains(expected)
    }

    /**
     * Comprehensive live audit generating the complete Permission-to-Feature mapping.
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
                else -> isPermissionGranted(context, spec.permissionId)
            }
            spec.copy(isCurrentlyGranted = granted)
        }
    }

    /**
     * Generates standard, transparent user-directed settings intent for resolving permissions.
     */
    fun createSettingsIntent(context: Context, permissionId: String): Intent {
        return when (permissionId) {
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
            Manifest.permission.SCHEDULE_EXACT_ALARM -> {
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
            else -> createAppDetailsIntent(context)
        }
    }

    /**
     * Standard Application Details settings intent.
     */
    fun createAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
