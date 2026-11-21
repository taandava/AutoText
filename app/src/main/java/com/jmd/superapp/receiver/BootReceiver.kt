/**
 * Package: com.jmd.superapp.receiver
 * Module: Broadcast Receivers Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [BootReceiver].
 *
 * What it does:
 * Catches [Intent.ACTION_BOOT_COMPLETED], [Intent.ACTION_LOCKED_BOOT_COMPLETED], and [Intent.ACTION_MY_PACKAGE_REPLACED] events to reschedule pending alarms, reinitialize accessibility watchdogs, and resume daily campaign cadences. [com.jmd.superapp.scheduler.DailyCampaignScheduler]
 *
 * Linked With:
 * - Schedulers & Receivers: [com.jmd.superapp.scheduler.AccessibilityWatchdogScheduler], [com.jmd.superapp.scheduler.AlarmScheduler], [com.jmd.superapp.scheduler.DailyCampaignScheduler]
 * - Services & Engines: [com.jmd.superapp.service.TaskAccessibilityService]
 * - UI Screens & Components: [com.jmd.superapp.ui.activity.TaskLauncherActivity]
 * - Core Security & Utilities: [android.os.PowerManager], [android.util.Log], [com.jmd.superapp.core.AccessibilityHealthManager], [com.jmd.superapp.core.AppFileLogger]
 * - Android Framework & Jetpack: [android.content.BroadcastReceiver], [android.os.Handler], [android.os.Looper]
 */

package com.jmd.superapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.jmd.superapp.core.AccessibilityHealthManager
import com.jmd.superapp.core.AppFileLogger
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.scheduler.AccessibilityWatchdogScheduler
import com.jmd.superapp.scheduler.AlarmScheduler
import com.jmd.superapp.scheduler.DailyCampaignScheduler
import com.jmd.superapp.service.TaskAccessibilityService
import com.jmd.superapp.ui.activity.TaskLauncherActivity

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
         * What it is:
         * Dispatches a temporary wake lock and launches [TaskLauncherActivity] to simulate keyguard wake-up and schedule credential-protected screen access assistance directly following a cold reboot.
         *
         * What it does:
         * Executes [triggerPostRebootScreenAssist] logic in accordance with system invariants.
         *
         * Linked with:
         * - Architecture: [BootReceiver], [triggerPostRebootScreenAssist]
         * @param context Device protected or application [Context].
         * @param pin Configured numeric security PIN to enter.
         */
        fun triggerPostRebootScreenAssist(context: Context, pin: String) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

                val wakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Super:BootScreenAssistWakeLock"
                )
                wakeLock?.acquire(15000L)

                val launchIntent = Intent(context, TaskLauncherActivity::class.java).apply {
                    putExtra(TaskLauncherActivity.EXTRA_IS_VERIFY_ASSIST, false)
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
    }

    /**
     * What it is:
     * Handles system boot and package update broadcasts.
     *
     * What it does:
     * Branches execution between: 1. Direct Boot (FBE locked context): Uses device-protected storage to evaluate screen-assist credentials. 2. Full Boot / Package Upgrade: Re-arms all scheduled task alarms, daily campaigns, and accessibility watchdogs.
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
            AppFileLogger.action(deContext, TAG, "Direct Boot ($action) detected. CE storage locked. Checking screen-assist PIN...")

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
            com.jmd.superapp.core.DispatchJournal.reconcileStale(context)
            if (!prefs.isA11yUserPaused) {
                AccessibilityHealthManager.ensureAllServicesActive(context)
            }
            AlarmScheduler(context).rescheduleAllPending()
            DailyCampaignScheduler.scheduleAll(context)
            AccessibilityWatchdogScheduler.scheduleHeartbeat(context)
            SuperDeviceAdminReceiver.ensureActive(context)
        }
    }
}

