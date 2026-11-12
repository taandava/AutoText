/**
 * Package: com.jmd.helper.receiver
 * Module: Broadcast Receivers Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [BootReceiver].
 *
 * What it does:
 * Catches [Intent.ACTION_BOOT_COMPLETED], [Intent.ACTION_LOCKED_BOOT_COMPLETED], and [Intent.ACTION_MY_PACKAGE_REPLACED] events to reschedule pending alarms, reinitialize accessibility watchdogs, and resume daily campaign cadences. [com.jmd.helper.scheduler.DailyCampaignScheduler]
 *
 * Linked With:
 * - Schedulers & Receivers: [com.jmd.helper.scheduler.AccessibilityWatchdogScheduler], [com.jmd.helper.scheduler.AlarmScheduler], [com.jmd.helper.scheduler.DailyCampaignScheduler]
 * - Services & Engines: [com.jmd.helper.service.TaskAccessibilityService]
 * - UI Screens & Components: [com.jmd.helper.ui.activity.TaskLauncherActivity]
 * - Core Security & Utilities: [android.os.PowerManager], [android.util.Log], [com.jmd.helper.core.AccessibilityHealthManager], [com.jmd.helper.core.AppFileLogger]
 * - Android Framework & Jetpack: [android.content.BroadcastReceiver], [android.os.Handler], [android.os.Looper]
 */

package com.jmd.helper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.scheduler.AccessibilityWatchdogScheduler
import com.jmd.helper.scheduler.AlarmScheduler
import com.jmd.helper.scheduler.DailyCampaignScheduler
import com.jmd.helper.service.TaskAccessibilityService
import com.jmd.helper.ui.activity.TaskLauncherActivity

/**
 * What it is:
 * Device restart and package update broadcast receiver restoring all task background schedules.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [BootReceiver].
 *
 * Linked with:
 * - Architecture: [BootReceiver], [BootReceiver]
 */
class BootReceiver : BroadcastReceiver() {
    /**
     * What it is:
     * Companion object constant and factory provider for [BootReceiver].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [BootReceiver]
     */
    companion object {
        private const val TAG = "BootReceiver"

        /**
         * Dispatches a temporary wake lock and launches [TaskLauncherActivity] to simulate keyguard
         * wake-up and schedule credential-protected screen access assistance directly following a cold reboot.
         *
         * @param context Device protected or application [Context].
         * @param pin Configured numeric security PIN to enter.
         */
        fun triggerPostRebootScreenAssist(context: Context, pin: String) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

                val wakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Helper:BootScreenAssistWakeLock"
                )
                wakeLock?.acquire(15000L)

                val launchIntent = Intent(context, TaskLauncherActivity::class.java).apply {
                    putExtra(TaskLauncherActivity.EXTRA_IS_TEST_UNLOCK, false)
                    putExtra(TaskLauncherActivity.EXTRA_PIN, pin)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launchIntent)

                Handler(Looper.getMainLooper()).postDelayed({
                    TaskAccessibilityService.armScreenAssist(pin = pin, shouldRelockAfterSend = false)
                }, 1200L)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to trigger post-reboot screen assist", t)
            }
        }

        /**
         * Backward-compatible alias for [triggerPostRebootScreenAssist].
         */
        @Deprecated(
            message = "Use triggerPostRebootScreenAssist for policy-compliant naming",
            replaceWith = ReplaceWith("triggerPostRebootScreenAssist(context, pin)")
        )
        fun triggerPostRebootAutoUnlock(context: Context, pin: String) = triggerPostRebootScreenAssist(context, pin)
    }

    /**
     * What it is:
     * Handles system boot and package update broadcasts.
     *
     * What it does:
     * Branches execution between: 1. Direct Boot (FBE locked context): Uses device-protected storage to evaluate auto-unlock credentials. 2. Full Boot / Package Upgrade: Re-arms all scheduled task alarms, daily campaigns, and accessibility watchdogs.
     *
     * Linked with:
     * - Architecture: [BootReceiver], [onReceive]
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
            if (prefs.isScreenAssistEnabled && prefs.screenAssistPin.isNotBlank()) {
                val pin = prefs.screenAssistPin
                Log.i(TAG, "Screen assist PIN configured. Waking screen for post-reboot PIN entry...")
                AppFileLogger.action(deContext, TAG, "Screen assist PIN configured. Waking screen to assist device out of Direct Boot.")
                triggerPostRebootScreenAssist(deContext, pin)
            } else {
                Log.i(TAG, "Screen assist PIN not set. Scheduling watchdog in DE context.")
                AccessibilityWatchdogScheduler.scheduleHeartbeat(deContext)
            }
        } else if (isFullBoot) {
            val prefs = PreferenceManager(context)
            AppFileLogger.action(context, TAG, "Device boot/package replace ($action) detected. Rescheduling active alarms and campaigns.")
            if (!prefs.isA11yUserPaused) {
                AccessibilityHealthManager.ensureAllServicesActive(context)
            }
            AccessibilityHealthManager.enforceAutoRotateInvariant(context)
            AlarmScheduler(context).rescheduleAllPending()
            DailyCampaignScheduler.scheduleAll(context)
            AccessibilityWatchdogScheduler.scheduleHeartbeat(context)
            HelperDeviceAdminReceiver.ensureActive(context)
        }
    }
}
