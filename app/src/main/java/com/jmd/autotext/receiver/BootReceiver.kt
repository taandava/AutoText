

/**
 * Package: com.jmd.autotext.receiver
 * Module: Broadcast Receivers Layer
 *
 * Catches [Intent.ACTION_BOOT_COMPLETED], [Intent.ACTION_LOCKED_BOOT_COMPLETED], and [Intent.ACTION_MY_PACKAGE_REPLACED]
 * events to reschedule pending alarms, reinitialize accessibility watchdogs, and resume daily campaign cadences.
 *
 * Linked With:
 * - Direct Boot: Operates in encrypted Direct Boot mode before user credentials are typed.
 * - Schedulers: [com.jmd.autotext.scheduler.AlarmScheduler], [com.jmd.autotext.scheduler.AccessibilityWatchdogScheduler],
 *   [com.jmd.autotext.scheduler.DailyCampaignScheduler]
 * - Preferences: [com.jmd.autotext.data.prefs.PreferenceManager]
 * - Diagnostics: [com.jmd.autotext.core.AppFileLogger]
 */
package com.jmd.autotext.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.jmd.autotext.ui.activity.AutomationLauncherActivity
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.scheduler.AccessibilityWatchdogScheduler
import com.jmd.autotext.scheduler.AlarmScheduler
import com.jmd.autotext.scheduler.DailyCampaignScheduler
import com.jmd.autotext.service.AutoTextAccessibilityService

/**
 * Device restart and package update broadcast receiver restoring all automation background schedules.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"

        /**
         * Dispatches a temporary wake lock and launches [AutomationLauncherActivity] to simulate keyguard
         * wake-up and automate credential-protected unlock directly following a cold reboot.
         *
         * @param context Device protected or application [Context].
         * @param pin Configured numeric security PIN to enter.
         */
        fun triggerPostRebootAutoUnlock(context: Context, pin: String) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

                val wakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AutoText:BootUnlockWakeLock"
                )
                wakeLock?.acquire(15000L)

                val launchIntent = Intent(context, AutomationLauncherActivity::class.java).apply {
                    putExtra(AutomationLauncherActivity.EXTRA_IS_TEST_UNLOCK, false)
                    putExtra(AutomationLauncherActivity.EXTRA_PIN, pin)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launchIntent)

                Handler(Looper.getMainLooper()).postDelayed({
                    AutoTextAccessibilityService.armAutoUnlock(pin = pin, shouldRelockAfterSend = false)
                }, 1200L)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to trigger post-reboot auto unlock", t)
            }
        }
    }

    /**
     * Handles system boot and package update broadcasts.
     *
     * Branches execution between:
     * 1. Direct Boot (FBE locked context): Uses device-protected storage to evaluate auto-unlock credentials.
     * 2. Full Boot / Package Upgrade: Re-arms all scheduled task alarms, daily campaigns, and accessibility watchdogs.
     *
     * @param context Active Android [Context].
     * @param intent Broadcast [Intent] received from Android OS.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isDirectBoot = action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        val isFullBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"

        if (isDirectBoot) {
            val deContext = context.createDeviceProtectedStorageContext()
            Log.d(TAG, "Direct boot (LOCKED_BOOT_COMPLETED) detected, CE storage locked.")
            AppFileLogger.action(deContext, TAG, "Direct Boot ($action) detected. CE storage locked. Checking auto-unlock PIN...")

            val prefs = PreferenceManager(deContext)
            if (!prefs.isTasksMasterEnabled || com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive(deContext)) {
                Log.d(TAG, "Direct boot: Master task scheduler is paused or emergency stop active. Suppressing auto unlock.")
                return
            }
            if (prefs.isAutoUnlockEnabled && prefs.deviceUnlockPin.isNotBlank()) {
                val pin = prefs.deviceUnlockPin
                Log.i(TAG, "Auto-unlock PIN configured. Waking screen for post-reboot PIN entry...")
                AppFileLogger.action(deContext, TAG, "Auto-unlock PIN configured. Waking screen to auto-unlock device out of Direct Boot.")
                triggerPostRebootAutoUnlock(deContext, pin)
            } else {
                Log.i(TAG, "Auto-unlock PIN not set. Scheduling watchdog in DE context.")
                AccessibilityWatchdogScheduler.scheduleHeartbeat(deContext)
            }
        } else if (isFullBoot) {
            Log.d(TAG, "Full boot or upgrade detected ($action), checking master scheduler state...")
            val prefs = PreferenceManager(context)
            if (!prefs.isTasksMasterEnabled || com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive(context)) {
                Log.d(TAG, "Master task scheduler is paused or emergency stop active. Suppressing boot reschedule.")
                AppFileLogger.action(context, TAG, "Device boot ($action): Master task scheduler is paused. Canceling all background schedules.")
                AlarmScheduler(context).cancelAll()
                DailyCampaignScheduler.cancelAllCampaigns(context)
                return
            }
            AppFileLogger.action(context, TAG, "Device boot/package replace ($action) detected. Rescheduling active alarms and campaigns.")
            AccessibilityHealthManager.ensureAllServicesActive(context)
            AccessibilityHealthManager.enforceAutoRotateInvariant(context)
            AlarmScheduler(context).rescheduleAllPending()
            DailyCampaignScheduler.scheduleAll(context)
            AccessibilityWatchdogScheduler.scheduleHeartbeat(context)
            AutoTextDeviceAdminReceiver.ensureActive(context)
        }
    }
}
