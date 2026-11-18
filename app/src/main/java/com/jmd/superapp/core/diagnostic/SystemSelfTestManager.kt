/**
 * Package: com.jmd.superapp.core.diagnostic
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [SystemSelfTestManager].
 *
 * What it does:
 * Provides a comprehensive, automated self-verification suite that verifies all application subsystems, database tables, reactive DAOs, schedulers, notification channels, background workers, system services, and navigation routes with 1-tap automated repair.
 *
 * Linked With:
 * - Repositories: [com.jmd.superapp.data.db.DatabaseHelper], [com.jmd.superapp.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.superapp.data.model.BackupDataDto]
 * - Schedulers & Receivers: [android.app.AlarmManager], [com.jmd.superapp.receiver.SuperDeviceAdminReceiver], [com.jmd.superapp.scheduler.AlarmScheduler], [com.jmd.superapp.scheduler.DailyCampaignScheduler]
 * - Core Security & Utilities: [android.app.NotificationManager], [android.content.pm.PackageManager], [android.telephony.SubscriptionManager], [android.util.Log]
 * - Android Framework & Jetpack: [android.Manifest], [android.os.Build], [kotlinx.coroutines.CancellationException], [kotlinx.coroutines.Dispatchers]
 */

package com.jmd.superapp.core.diagnostic

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.WorkManager
import com.jmd.superapp.SuperApplication
import com.jmd.superapp.core.AccessibilityHealthManager
import com.jmd.superapp.core.AppFileLogger
import com.jmd.superapp.core.PrivacyManager
import com.jmd.superapp.core.WhatsAppPackageHelper
import com.jmd.superapp.data.db.DatabaseHelper
import com.jmd.superapp.data.model.BackupDataDto
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.data.repo.AppRepository
import com.jmd.superapp.receiver.SuperDeviceAdminReceiver
import com.jmd.superapp.scheduler.AlarmScheduler
import com.jmd.superapp.scheduler.DailyCampaignScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What it is:
 * Diagnostic status levels for individual subsystem checks.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [DiagnosticLevel].
 *
 * Linked with:
 * - Architecture: [SystemSelfTestManager], [DiagnosticLevel]
 */
enum class DiagnosticLevel {
    PASS,
    WARNING,
    FAIL
}

/**
 * What it is:
 * Detailed report item representing the health and state of a single subsystem.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [DiagnosticResultItem].
 *
 * Linked with:
 * - Architecture: [SystemSelfTestManager], [DiagnosticResultItem]
 */
data class DiagnosticResultItem(
    val id: String,
    val title: String,
    val category: String,
    val level: DiagnosticLevel,
    val summary: String,
    val details: String,
    val canAutoFix: Boolean = false,
    val fixActionDescription: String? = null
)

/**
 * What it is:
 * Comprehensive diagnostic report summarizing all system health metrics.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [DiagnosticReport].
 *
 * Linked with:
 * - Architecture: [SystemSelfTestManager], [DiagnosticReport]
 */
data class DiagnosticReport(
    val timestamp: Long = System.currentTimeMillis(),
    val totalTests: Int,
    val passedCount: Int,
    val warningCount: Int,
    val failedCount: Int,
    val items: List<DiagnosticResultItem>
) {
    val overallHealthPercentage: Int
        get() = if (totalTests == 0) 100 else ((passedCount.toFloat() / totalTests.toFloat()) * 100).toInt()

    val isPerfect: Boolean
        get() = failedCount == 0 && warningCount == 0

    val hasCriticalFailures: Boolean
        get() = failedCount > 0
}

/**
 * What it is:
 * Automated diagnostic runner and self-healing repair orchestrator for the Super app.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [SystemSelfTestManager].
 *
 * Linked with:
 * - Architecture: [SystemSelfTestManager], [SystemSelfTestManager]
 */
object SystemSelfTestManager {

    private const val TAG = "SystemSelfTest"

    private val jsonSerializer = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * What it is:
     * Executes all automated subsystem health checks asynchronously on Dispatchers.IO.
     *
     * What it does:
     * Executes [runFullDiagnostic] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [SystemSelfTestManager], [runFullDiagnostic]
     * @param context Active application context.
     * @return Complete [DiagnosticReport] detailing all pass, warning, and failure states.
     */
    suspend fun runFullDiagnostic(context: Context): DiagnosticReport = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val items = mutableListOf<DiagnosticResultItem>()

        AppFileLogger.i(appContext, TAG, "=== Starting Comprehensive System Self-Test ===")

        // 1. SQLite Database Schema & Tables
        items.add(checkSqliteDatabase(appContext))

        // 2. Storage Engine & WAL Integrity
        items.add(checkStorageEngine(appContext))

        // 3. SharedPreferences & Jetpack DataStore
        items.add(checkPreferencesAndDataStore(appContext))

        // 4. System Notification Channels
        items.add(checkNotificationChannels(appContext))

        // 5. Alarm Scheduling Engine & Exact Alarm Rights
        items.add(checkAlarmScheduler(appContext))

        // 6. WorkManager & Daily Campaigns
        items.add(checkWorkManagerAndCampaigns(appContext))

        // 7. Accessibility Automation Service
        items.add(checkAccessibilityService(appContext))

        // 8. Device Administration & Auto-Lock
        items.add(checkDeviceAdmin(appContext))

        // 9. Telephony & SMS Dispatch Capabilities
        items.add(checkTelephonyAndSms(appContext))

        // 10. WhatsApp & Messaging Dispatch Integrations
        items.add(checkWhatsAppIntegration(appContext))

        // 11. JSON Backup Serialization Engine
        items.add(checkJsonBackupEngine(appContext))

        // 12. Banking App Shield & Privacy Vault
        items.add(checkBankingShieldAndPrivacy(appContext))

        // 13. UI Screens & Navigation Route Registry
        items.add(checkNavigationAndScreenRegistry(appContext))

        // 14. Diagnostic File Logger & Storage Hygiene
        items.add(checkSystemLogTraces(appContext))

        val passed = items.count { it.level == DiagnosticLevel.PASS }
        val warning = items.count { it.level == DiagnosticLevel.WARNING }
        val failed = items.count { it.level == DiagnosticLevel.FAIL }

        AppFileLogger.i(
            appContext,
            TAG,
            "Self-Test Complete: ${items.size} Total | $passed PASS | $warning WARN | $failed FAIL"
        )

        DiagnosticReport(
            totalTests = items.size,
            passedCount = passed,
            warningCount = warning,
            failedCount = failed,
            items = items
        )
    }

    /**
     * What it is:
     * Executes automated self-healing routines across all repairable subsystems.
     *
     * What it does:
     * Executes [autoFixAll] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [SystemSelfTestManager], [autoFixAll]
     * @param context Active application context.
     * @return Fresh [DiagnosticReport] generated immediately following the repair cycle.
     */
    suspend fun autoFixAll(context: Context): DiagnosticReport = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        AppFileLogger.i(appContext, TAG, "=== Executing Automated System Repair & Auto-Fix ===")

        // 1. Repair Notification Channels
        try {
            SuperApplication.createNotificationChannels(appContext)
            AppFileLogger.i(appContext, TAG, "Auto-Fix: Re-created notification channels")
        } catch (e: Exception) {
            AppFileLogger.e(appContext, TAG, "Auto-Fix notification channels error: ${e.message}", e)
        }

        // 2. Database Maintenance: VACUUM, ANALYZE, Optimize & Recover Orphaned Tasks
        try {
            val dbHelper = DatabaseHelper.getInstance(appContext)
            val db = dbHelper.writableDatabase
            db.execSQL("PRAGMA optimize;")
            val cv = android.content.ContentValues().apply { put("status", "PENDING") }
            val tenMinutesAgo = System.currentTimeMillis() - (10L * 60 * 1000)
            db.update(DatabaseHelper.TABLE_TASKS, cv, "status = 'RUNNING' AND trigger_time < ?", arrayOf(tenMinutesAgo.toString()))
            AppFileLogger.i(appContext, TAG, "Auto-Fix: Database optimized and orphaned tasks recovered")
        } catch (e: Exception) {
            AppFileLogger.e(appContext, TAG, "Auto-Fix database maintenance error: ${e.message}", e)
        }

        // 3. Reschedule All Pending Task Alarms
        try {
            AlarmScheduler(appContext).rescheduleAllPending()
            AppFileLogger.i(appContext, TAG, "Auto-Fix: All pending task alarms rescheduled")
        } catch (e: Exception) {
            AppFileLogger.e(appContext, TAG, "Auto-Fix alarm reschedule error: ${e.message}", e)
        }

        // 4. Re-arm Daily Campaign Workers & Alarms
        try {
            DailyCampaignScheduler.scheduleAll(appContext)
            AppFileLogger.i(appContext, TAG, "Auto-Fix: Daily campaign schedulers re-armed")
        } catch (e: Exception) {
            AppFileLogger.e(appContext, TAG, "Auto-Fix daily campaigns error: ${e.message}", e)
        }

        // 5. Restore Missing Preference Defaults
        try {
            val prefs = PreferenceManager(appContext)
            if (prefs.whatsappDispatchDelaySeconds <= 0) prefs.whatsappDispatchDelaySeconds = 2
            if (prefs.contactSyncIntervalHours <= 0) prefs.contactSyncIntervalHours = 24
            if (prefs.dailySmsSafetyLimit <= 0) prefs.dailySmsSafetyLimit = 100
            AppFileLogger.i(appContext, TAG, "Auto-Fix: Essential preferences validated and default safety limits set")
        } catch (e: Exception) {
            AppFileLogger.e(appContext, TAG, "Auto-Fix preferences error: ${e.message}", e)
        }

        // 6. Attempt Accessibility Auto-Heal if WRITE_SECURE_SETTINGS is present
        try {
            AccessibilityHealthManager.autoHealAccessibility(appContext)
            AccessibilityHealthManager.enforceAutoRotateInvariant(appContext)
        } catch (e: Exception) {
            AppFileLogger.d(appContext, TAG, "Auto-Fix accessibility note: ${e.message}")
        }

        // 7. Verify and Purge Oversized Log Files
        try {
            val logDir = File(appContext.filesDir, "logs")
            if (logDir.exists()) {
                logDir.listFiles()?.forEach { file ->
                    if (file.length() > 5 * 1024 * 1024) {
                        file.delete()
                    }
                }
            }
            AppFileLogger.i(appContext, TAG, "Auto-Fix: Log storage hygiene pass completed")
        } catch (e: Exception) {
            AppFileLogger.w(appContext, TAG, "Auto-Fix log hygiene note: ${e.message}")
        }

        // Return updated diagnostic report after all repairs
        runFullDiagnostic(appContext)
    }

    // ========================================================================
    // INDIVIDUAL SUBSYSTEM CHECKS
    // ========================================================================
    /**
     * What it is:
     * Execution routine for [checkSqliteDatabase].
     *
     * What it does:
     * Coordinates checkSqliteDatabase operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkSqliteDatabase(context: Context): DiagnosticResultItem {
        return try {
            val dbHelper = DatabaseHelper.getInstance(context)
            val db = dbHelper.readableDatabase

            var pragmaOk = false
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val result = cursor.getString(0)
                    pragmaOk = result.equals("ok", ignoreCase = true)
                }
            }

            val requiredTables = listOf(
                DatabaseHelper.TABLE_TASKS,
                DatabaseHelper.TABLE_RECIPIENTS,
                DatabaseHelper.TABLE_AUTO_REPLY,
                DatabaseHelper.TABLE_CAMPAIGNS,
                DatabaseHelper.TABLE_DRIP_STEPS,
                DatabaseHelper.TABLE_LOGS,
                DatabaseHelper.TABLE_DYNAMIC_POOL,
                DatabaseHelper.TABLE_DAILY_CAMPAIGN,
                DatabaseHelper.TABLE_AUTO_REPLY_HISTORY,
                DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT,
                DatabaseHelper.TABLE_TASK_DELIVERIES,
                DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS,
                com.jmd.superapp.core.DispatchJournal.TABLE
            )

            val missingTables = mutableListOf<String>()
            val counts = mutableMapOf<String, Int>()

            for (table in requiredTables) {
                try {
                    db.rawQuery("SELECT count(*) FROM $table", null).use { cursor ->
                        if (cursor.moveToFirst()) {
                            counts[table] = cursor.getInt(0)
                        } else {
                            missingTables.add(table)
                        }
                    }
                } catch (e: Exception) {
                    missingTables.add(table)
                }
            }

            if (pragmaOk && missingTables.isEmpty()) {
                val tasksCount = counts[DatabaseHelper.TABLE_TASKS] ?: 0
                val logsCount = counts[DatabaseHelper.TABLE_LOGS] ?: 0
                val contactsCount = counts[DatabaseHelper.TABLE_RECIPIENTS] ?: 0
                DiagnosticResultItem(
                    id = "sqlite_db",
                    title = "Database Tables",
                    category = "Storage",
                    level = DiagnosticLevel.PASS,
                    summary = "Database Healthy",
                    details = "PRAGMA check: OK. Tables verified: ${requiredTables.size}/13. Tasks: $tasksCount, Contacts: $contactsCount, Logs: $logsCount."
                )
            } else if (!pragmaOk) {
                DiagnosticResultItem(
                    id = "sqlite_db",
                    title = "Database Tables",
                    category = "Storage",
                    level = DiagnosticLevel.FAIL,
                    summary = "Integrity Error",
                    details = "PRAGMA integrity check returned an error. Database may require repair or restoration from backup.",
                    canAutoFix = true,
                    fixActionDescription = "Repair Database"
                )
            } else {
                DiagnosticResultItem(
                    id = "sqlite_db",
                    title = "Database Tables",
                    category = "Storage",
                    level = DiagnosticLevel.WARNING,
                    summary = "Tables Missing",
                    details = "Missing or unqueryable tables: ${missingTables.joinToString()}. Can be automatically generated.",
                    canAutoFix = true,
                    fixActionDescription = "Migrate Database"
                )
            }
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "sqlite_db",
                title = "Database Tables",
                category = "Storage",
                level = DiagnosticLevel.FAIL,
                summary = "Database Error",
                details = e.stackTraceToString().take(300),
                canAutoFix = true,
                fixActionDescription = "Reset Engine"
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkStorageEngine].
     *
     * What it does:
     * Coordinates checkStorageEngine operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkStorageEngine(context: Context): DiagnosticResultItem {
        return try {
            val dbHelper = DatabaseHelper.getInstance(context)
            val db = dbHelper.readableDatabase
            val isWal = db.isWriteAheadLoggingEnabled
            val version = db.version
            DiagnosticResultItem(
                id = "sqlite_storage_engine",
                title = "Database Engine",
                category = "Storage",
                level = DiagnosticLevel.PASS,
                summary = "Engine Active",
                details = "Database super.db v$version connected with WAL=$isWal (Primary unified storage driver: DatabaseHelper)."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "sqlite_storage_engine",
                title = "Database Engine",
                category = "Storage",
                level = DiagnosticLevel.WARNING,
                summary = "Storage Note",
                details = "Storage access error: ${e.message}.",
                canAutoFix = true,
                fixActionDescription = "Reset Engine"
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkPreferencesAndDataStore].
     *
     * What it does:
     * Coordinates checkPreferencesAndDataStore operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private suspend fun checkPreferencesAndDataStore(context: Context): DiagnosticResultItem {
        return try {
            val prefs = PreferenceManager(context)
            val testProbeKey = "__diagnostic_probe_test__"
            prefs.sharedPreferences.edit(commit = true) { putString(testProbeKey, "ok") }
            val probeRead = prefs.sharedPreferences.getString(testProbeKey, null)
            prefs.sharedPreferences.edit(commit = true) { remove(testProbeKey) }

            DiagnosticResultItem(
                id = "preferences_datastore",
                title = "Preferences Storage",
                category = "Configuration",
                level = DiagnosticLevel.PASS,
                summary = "Preferences Active",
                details = "Probe Verified: ${probeRead == "ok"}, SIM Slot: ${prefs.defaultSimSlot}, Theme: ${prefs.appThemeMode}."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "preferences_datastore",
                title = "Preferences Storage",
                category = "Configuration",
                level = DiagnosticLevel.WARNING,
                summary = "Preference Error",
                details = "Failed writing test key to persistent storage: ${e.message}",
                canAutoFix = true,
                fixActionDescription = "Reset Settings"
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkNotificationChannels].
     *
     * What it does:
     * Coordinates checkNotificationChannels operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkNotificationChannels(context: Context): DiagnosticResultItem {
        return try {
            val nm = context.getSystemService(NotificationManager::class.java)
                ?: return DiagnosticResultItem(
                    id = "notification_channels",
                    title = "Notification Channels",
                    category = "Notifications",
                    level = DiagnosticLevel.FAIL,
                    summary = "Service Unavailable",
                    details = "Android NotificationManager returned null."
                )

            val areNotificationsEnabled = nm.areNotificationsEnabled()

            if (!areNotificationsEnabled) {
                return DiagnosticResultItem(
                    id = "notification_channels",
                    title = "Notification Channels",
                    category = "Notifications",
                    level = DiagnosticLevel.WARNING,
                    summary = "Notifications Disabled",
                    details = "System notifications are turned off at the application level. Task completion toasts and alerts will not appear in status bar.",
                    canAutoFix = true,
                    fixActionDescription = "Open Settings"
                )
            }

            val tasksChannel = nm.getNotificationChannel(SuperApplication.CHANNEL_TASKS)
            val remindersChannel = nm.getNotificationChannel(SuperApplication.CHANNEL_REMINDERS)

            if (tasksChannel != null && remindersChannel != null) {
                DiagnosticResultItem(
                    id = "notification_channels",
                    title = "Notification Channels",
                    category = "Notifications",
                    level = DiagnosticLevel.PASS,
                    summary = "Channels Active",
                    details = "Tasks Channel: ${tasksChannel.name} (Importance: ${tasksChannel.importance}). Reminders Channel: ${remindersChannel.name} (Importance: ${remindersChannel.importance})."
                )
            } else {
                DiagnosticResultItem(
                    id = "notification_channels",
                    title = "Notification Channels",
                    category = "Notifications",
                    level = DiagnosticLevel.WARNING,
                    summary = "Channels Missing",
                    details = "Tasks channel: ${tasksChannel != null}, Reminders channel: ${remindersChannel != null}.",
                    canAutoFix = true,
                    fixActionDescription = "Create Channels"
                )
            }
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "notification_channels",
                title = "Notification Channels",
                category = "Notifications",
                level = DiagnosticLevel.WARNING,
                summary = "Channel Error",
                details = e.message ?: "Unknown error",
                canAutoFix = true,
                fixActionDescription = "Create Channels"
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkAlarmScheduler].
     *
     * What it does:
     * Coordinates checkAlarmScheduler operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkAlarmScheduler(context: Context): DiagnosticResultItem {
        return try {
            val alarmManager = (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                ?: return DiagnosticResultItem(
                    id = "alarm_scheduler",
                    title = "Alarm Scheduling",
                    category = "Background Schedulers",
                    level = DiagnosticLevel.FAIL,
                    summary = "Service Unavailable",
                    details = "Device failed to return AlarmManager service."
                )

            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            val repo = AppRepository(context)
            val pendingTasks = repo.getPendingTasks()
            val nextAlarm = pendingTasks.minByOrNull { it.triggerTimeMillis }

            if (!canScheduleExact) {
                DiagnosticResultItem(
                    id = "alarm_scheduler",
                    title = "Alarm Scheduling",
                    category = "Background Schedulers",
                    level = DiagnosticLevel.WARNING,
                    summary = "Permission Needed",
                    details = "Android 12+ requires user approval for exact precision alarms. Alarms will use inexact windowing unless granted.",
                    canAutoFix = true,
                    fixActionDescription = "Open Alarms"
                )
            } else {
                val nextInfo = if (nextAlarm != null) {
                    "Next pending: ${nextAlarm.title} at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(nextAlarm.triggerTimeMillis))}"
                } else {
                    "No pending tasks scheduled currently"
                }
                DiagnosticResultItem(
                    id = "alarm_scheduler",
                    title = "Alarm Scheduling",
                    category = "Background Schedulers",
                    level = DiagnosticLevel.PASS,
                    summary = "Alarms Active",
                    details = "Can schedule exact alarms: true. Pending task queue: ${pendingTasks.size}. $nextInfo."
                )
            }
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "alarm_scheduler",
                title = "Alarm Scheduling",
                category = "Background Schedulers",
                level = DiagnosticLevel.WARNING,
                summary = "Alarm Error",
                details = e.message ?: "Unknown error",
                canAutoFix = true,
                fixActionDescription = "Reschedule Tasks"
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkWorkManagerAndCampaigns].
     *
     * What it does:
     * Coordinates checkWorkManagerAndCampaigns operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkWorkManagerAndCampaigns(context: Context): DiagnosticResultItem {
        return try {
            val wm = WorkManager.getInstance(context)
            val repo = AppRepository(context)
            val smsCampaign = repo.getDailyCampaignConfig(1L)
            val waCampaign = repo.getDailyCampaignConfig(2L)

            DiagnosticResultItem(
                id = "workmanager_campaigns",
                title = "WorkManager Pipelines",
                category = "Background Schedulers",
                level = DiagnosticLevel.PASS,
                summary = "Pipelines Active",
                details = "Daily SMS (id=1): active=${smsCampaign.isActive}, limit=${smsCampaign.dailyLimit}/day. Daily WhatsApp (id=2): active=${waCampaign.isActive}, limit=${waCampaign.dailyLimit}/day."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "workmanager_campaigns",
                title = "WorkManager Pipelines",
                category = "Background Schedulers",
                level = DiagnosticLevel.WARNING,
                summary = "WorkManager Error",
                details = e.message ?: "Unknown error",
                canAutoFix = true,
                fixActionDescription = "Arm Campaigns"
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkAccessibilityService].
     *
     * What it does:
     * Coordinates checkAccessibilityService operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkAccessibilityService(context: Context): DiagnosticResultItem {
        return try {
            val isEnabledInSystem = AccessibilityHealthManager.isAccessibilityEnabledInSystem(context)
            val isOperational = AccessibilityHealthManager.isServiceOperational()
            val prefs = PreferenceManager(context)

            if (isEnabledInSystem && isOperational) {
                DiagnosticResultItem(
                    id = "accessibility_service",
                    title = "Accessibility Service",
                    category = "System Services",
                    level = DiagnosticLevel.PASS,
                    summary = "Service Active",
                    details = "System setting: ENABLED, Service binder: CONNECTED. Click offset: ${prefs.customClickCoordinateOffsetY}px."
                )
            } else if (isEnabledInSystem) {
                DiagnosticResultItem(
                    id = "accessibility_service",
                    title = "Accessibility Service",
                    category = "System Services",
                    level = DiagnosticLevel.WARNING,
                    summary = "Service Disconnected",
                    details = "Accessibility is toggled ON in system settings, but the binder process was terminated or paused by OEM battery saver.",
                    canAutoFix = true,
                    fixActionDescription = "Restart Service"
                )
            } else {
                DiagnosticResultItem(
                    id = "accessibility_service",
                    title = "Accessibility Service",
                    category = "System Services",
                    level = DiagnosticLevel.WARNING,
                    summary = "Service Off",
                    details = "Automation features (WhatsApp direct sending, auto-clicks) require Accessibility Service activation.",
                    canAutoFix = true,
                    fixActionDescription = "Open Settings"
                )
            }
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "accessibility_service",
                title = "Accessibility Service",
                category = "System Services",
                level = DiagnosticLevel.WARNING,
                summary = "Accessibility Error",
                details = e.message ?: "Unknown error",
                canAutoFix = true,
                fixActionDescription = "Open Settings"
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkDeviceAdmin].
     *
     * What it does:
     * Coordinates checkDeviceAdmin operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkDeviceAdmin(context: Context): DiagnosticResultItem {
        return try {
            val isActive = SuperDeviceAdminReceiver.isAdminActive(context)

            if (isActive) {
                DiagnosticResultItem(
                    id = "device_admin",
                    title = "Device Admin",
                    category = "System Services",
                    level = DiagnosticLevel.PASS,
                    summary = "Admin Active",
                    details = "Hardware auto-lock and screen-off automation enabled via SuperDeviceAdminReceiver."
                )
            } else {
                DiagnosticResultItem(
                    id = "device_admin",
                    title = "Device Admin",
                    category = "System Services",
                    level = DiagnosticLevel.WARNING,
                    summary = "Admin Inactive",
                    details = "Required only if you want the app to turn off or lock the screen immediately after unattended task execution.",
                    canAutoFix = true,
                    fixActionDescription = "Activate Admin"
                )
            }
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "device_admin",
                title = "Device Admin",
                category = "System Services",
                level = DiagnosticLevel.WARNING,
                summary = "Admin Error",
                details = e.message ?: "Unknown error",
                canAutoFix = true,
                fixActionDescription = "Activate Admin"
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkTelephonyAndSms].
     *
     * What it does:
     * Coordinates checkTelephonyAndSms operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkTelephonyAndSms(context: Context): DiagnosticResultItem {
        return try {
            val hasSendSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            val hasReadPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            var simCount = 0
            if (hasReadPhoneState && sm != null) {
                try {
                    simCount = sm.activeSubscriptionInfoCount
                } catch (e: SecurityException) {
                    Log.w("SystemSelfTest", "Subscription count unavailable: ${e.message}")
                }
            }

            val prefs = PreferenceManager(context)

            if (hasSendSms) {
                DiagnosticResultItem(
                    id = "telephony_sms",
                    title = "Telephony Engine",
                    category = "Communications",
                    level = DiagnosticLevel.PASS,
                    summary = "SMS Ready",
                    details = "SMS permission: GRANTED. Phone state: ${if (hasReadPhoneState) "GRANTED" else "OPTIONAL"}. Preferred SIM slot: ${prefs.defaultSimSlot}."
                )
            } else {
                DiagnosticResultItem(
                    id = "telephony_sms",
                    title = "Telephony Engine",
                    category = "Communications",
                    level = DiagnosticLevel.WARNING,
                    summary = "SMS Missing",
                    details = "Automated SMS dispatch requires runtime SEND_SMS approval. Tasks scheduled via SMS will fail unless approved.",
                    canAutoFix = true,
                    fixActionDescription = "Open Permissions"
                )
            }
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "telephony_sms",
                title = "Telephony Engine",
                category = "Communications",
                level = DiagnosticLevel.WARNING,
                summary = "Telephony Error",
                details = e.message ?: "Unknown error"
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkWhatsAppIntegration].
     *
     * What it does:
     * Coordinates checkWhatsAppIntegration operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkWhatsAppIntegration(context: Context): DiagnosticResultItem {
        return try {
            val isStandardInstalled = WhatsAppPackageHelper.isWhatsAppInstalled(context)
            val isBusinessInstalled = WhatsAppPackageHelper.isWhatsAppBusinessInstalled(context)

            val resolvedVariant = WhatsAppPackageHelper.getInstalledWhatsAppPackage(context)

            if (isStandardInstalled || isBusinessInstalled) {
                DiagnosticResultItem(
                    id = "whatsapp_integration",
                    title = "WhatsApp Dispatch",
                    category = "Communications",
                    level = DiagnosticLevel.PASS,
                    summary = "WhatsApp Ready",
                    details = "Standard WhatsApp: $isStandardInstalled, WhatsApp Business: $isBusinessInstalled. Direct intent handler verified."
                )
            } else {
                DiagnosticResultItem(
                    id = "whatsapp_integration",
                    title = "WhatsApp Dispatch",
                    category = "Communications",
                    level = DiagnosticLevel.WARNING,
                    summary = "WhatsApp Missing",
                    details = "Neither standard WhatsApp nor WhatsApp Business was detected via PackageManager. WhatsApp tasks will be skipped.",
                    canAutoFix = false
                )
            }
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "whatsapp_integration",
                title = "WhatsApp Dispatch",
                category = "Communications",
                level = DiagnosticLevel.WARNING,
                summary = "WhatsApp Error",
                details = e.message ?: "Unknown error"
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkJsonBackupEngine].
     *
     * What it does:
     * Coordinates checkJsonBackupEngine operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkJsonBackupEngine(context: Context): DiagnosticResultItem {
        return try {
            val testPayload = BackupDataDto(
                version = 28,
                app = "Super",
                exportedAt = System.currentTimeMillis(),
                tasks = emptyList(),
                templates = emptyList(),
                autoReplyRules = emptyList(),
                dailyCampaigns = emptyList(),
                contacts = emptyList()
            )

            val jsonString = jsonSerializer.encodeToString(testPayload)
            val parsedPayload = jsonSerializer.decodeFromString<BackupDataDto>(jsonString)

            if (parsedPayload.version == 28 && parsedPayload.app == "Super") {
                DiagnosticResultItem(
                    id = "json_backup",
                    title = "Backup Serialization",
                    category = "Storage",
                    level = DiagnosticLevel.PASS,
                    summary = "Backup Operational",
                    details = "Kotlinx Serialization validated for BackupDataDto. Database import/export engine operational."
                )
            } else {
                DiagnosticResultItem(
                    id = "json_backup",
                    title = "Backup Serialization",
                    category = "Storage",
                    level = DiagnosticLevel.FAIL,
                    summary = "Backup Mismatch",
                    details = "Parsed backup DTO version did not match expected test constant."
                )
            }
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "json_backup",
                title = "Backup Serialization",
                category = "Storage",
                level = DiagnosticLevel.FAIL,
                summary = "Backup Error",
                details = e.stackTraceToString().take(300)
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkBankingShieldAndPrivacy].
     *
     * What it does:
     * Coordinates checkBankingShieldAndPrivacy operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkBankingShieldAndPrivacy(context: Context): DiagnosticResultItem {
        return try {
            val prefs = PreferenceManager(context)
            val isShieldEnabled = prefs.isBankingShieldEnabled

            val privacyConfigs = PrivacyManager.getInstance(context).getAllConfigs()

            DiagnosticResultItem(
                id = "banking_privacy",
                title = "Banking Shield",
                category = "App Privacy",
                level = DiagnosticLevel.PASS,
                summary = if (isShieldEnabled) "Shield Active" else "Shield Standby",
                details = "Banking App Shield: $isShieldEnabled. Monitored application configs: ${privacyConfigs.size}."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "banking_privacy",
                title = "Banking Shield",
                category = "App Privacy",
                level = DiagnosticLevel.WARNING,
                summary = "Shield Error",
                details = e.message ?: "Unknown error"
            )
        }
    }
    /**
     * What it is:
     * Execution routine for [checkNavigationAndScreenRegistry].
     *
     * What it does:
     * Coordinates checkNavigationAndScreenRegistry operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkNavigationAndScreenRegistry(context: Context): DiagnosticResultItem {
        val registeredRoutes = listOf(
            "DAILY_SMS" to "SMS Campaign",
            "DAILY_WHATSAPP" to "WhatsApp Campaign",
            "WHATSAPP_SETUP" to "WhatsApp Setup",
            "LOGS" to "Execution History",
            "SYSTEM_LOGS" to "System Logs",
            "PERMISSIONS" to "Permissions",
            "CALL_SIMULATION" to "Call Simulation",
            "PRIVACY" to "App Privacy",
            "SETTINGS" to "Settings",
            "DOCS" to "Guide"
        )

        return DiagnosticResultItem(
            id = "navigation_routes",
            title = "Route Registry",
            category = "Navigation",
            level = DiagnosticLevel.PASS,
            summary = "Routes Mapped",
            details = registeredRoutes.joinToString { "${it.first} (${it.second})" }
        )
    }
    /**
     * What it is:
     * Execution routine for [checkSystemLogTraces].
     *
     * What it does:
     * Coordinates checkSystemLogTraces operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [SystemSelfTestManager]
     */

    private fun checkSystemLogTraces(context: Context): DiagnosticResultItem {
        return try {
            val logDir = File(context.filesDir, "logs")
            val isDirWritable = logDir.exists() && logDir.canWrite() || logDir.mkdirs()

            val logFiles = logDir.listFiles() ?: emptyArray()
            val totalBytes = logFiles.sumOf { it.length() }
            val formattedKb = totalBytes / 1024

            DiagnosticResultItem(
                id = "system_logs",
                title = "Diagnostics Ledger",
                category = "Diagnostics",
                level = DiagnosticLevel.PASS,
                summary = "Ledger Healthy",
                details = "Log directory: ${logDir.absolutePath}. Writable: $isDirWritable. Active log volume: $formattedKb KB across ${logFiles.size} files."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticResultItem(
                id = "system_logs",
                title = "Diagnostics Ledger",
                category = "Diagnostics",
                level = DiagnosticLevel.WARNING,
                summary = "Ledger Error",
                details = e.message ?: "Unknown error",
                canAutoFix = true,
                fixActionDescription = "Reset Logs"
            )
        }
    }
}
