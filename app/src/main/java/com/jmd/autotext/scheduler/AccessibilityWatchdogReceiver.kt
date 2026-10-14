package com.jmd.autotext.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.service.AccessibilityHealthManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccessibilityWatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "A11yWatchdog"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoText:WatchdogWakeLock"
        )
        wakeLock?.acquire(2000L)

        // 1. Check and revive accessibility service if binder dropped
        try {
            if (!AccessibilityHealthManager.isServiceRunning()) {
                Log.d(TAG, "Watchdog heartbeat tick received. A11y service inactive, checking autoHeal...")
                if (AccessibilityHealthManager.hasWriteSecureSettings(context)) {
                    Log.i(TAG, "Watchdog heartbeat detected dropped A11y binder! Reviving via WRITE_SECURE_SETTINGS...")
                    AccessibilityHealthManager.autoHealAccessibility(context)
                }
                AccessibilityHealthManager.healNotificationListener(context)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in watchdog service verification", t)
        }

        // 2. Chain next watchdog alarm (15 minutes)
        AccessibilityWatchdogScheduler.scheduleHeartbeat(context)

        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Throwable) {}
    }
}
