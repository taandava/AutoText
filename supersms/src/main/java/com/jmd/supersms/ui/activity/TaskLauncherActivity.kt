/**
 * Package: com.jmd.supersms.ui.activity
 * Module: Application Core Subsystem
 *
 * What it is:
 * Transparent trampoline Activity that wakes the display and handles keyguard dismissal flags for incoming call simulation or tasks.
 */

package com.jmd.supersms.ui.activity

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import com.jmd.supersms.core.AppFileLogger
import com.jmd.supersms.data.model.ChannelType
import com.jmd.supersms.data.prefs.PreferenceManager

class TaskLauncherActivity : Activity() {

    companion object {
        private const val TAG = "TaskLauncherAct"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_CHANNEL = "extra_channel"
        const val EXTRA_RECIPIENT = "extra_recipient"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_RELOCK = "extra_relock"
        const val EXTRA_IS_BATCH = "extra_is_batch"
        const val EXTRA_IS_VERIFY_ASSIST = "extra_is_verify_assist"
        const val EXTRA_PIN = "extra_pin"
        const val EXTRA_MEDIA_URI = "extra_media_uri"
        const val EXTRA_WAKE_ONLY = "extra_wake_only"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    private val dismissCallback = object : KeyguardManager.KeyguardDismissCallback() {
        override fun onDismissSucceeded() {
            Log.i(TAG, "Keyguard dismiss succeeded callback received.")
            cleanFinish()
        }
        override fun onDismissCancelled() {
            Log.w(TAG, "Keyguard dismiss cancelled by user.")
            cleanFinish()
        }
        override fun onDismissError() {
            Log.e(TAG, "Keyguard dismiss error.")
            cleanFinish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val pm = getSystemService(PowerManager::class.java)
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "SuperSMS:TaskLauncherWakeLock"
            ).apply {
                acquire(15_000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock acquire failed: ${e.message}")
        }

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )

        window.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())

        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.cancel(9988)
        }

        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val channelStr = intent.getStringExtra(EXTRA_CHANNEL) ?: ChannelType.CALL.name
        val recipient = intent.getStringExtra(EXTRA_RECIPIENT) ?: ""

        val isLocked = keyguardManager.isKeyguardLocked || keyguardManager.isDeviceLocked
        AppFileLogger.action(this, TAG, "TaskLauncherActivity: Locked=$isLocked, Task=#$taskId, Recipient=$recipient, Channel=$channelStr")

        if (isLocked) {
            keyguardManager.requestDismissKeyguard(this, dismissCallback)
            pollKeyguardStatusUntilUnlocked(keyguardManager, 8000L)
        } else {
            handler.postDelayed({ cleanFinish() }, 500L)
        }
    }

    private var isFinishingCleanly = false

    private fun cleanFinish() {
        if (isFinishingCleanly || isFinishing || isDestroyed) return
        isFinishingCleanly = true
        handler.removeCallbacksAndMessages(null)
        runCatching {
            wakeLock?.release()
            wakeLock = null
        }
        finish()
    }

    private fun pollKeyguardStatusUntilUnlocked(km: KeyguardManager, timeoutRemainingMs: Long) {
        if (!km.isKeyguardLocked && !km.isDeviceLocked) {
            Log.i(TAG, "Device confirmed unlocked. Finishing trampoline cleanly.")
            cleanFinish()
            return
        }
        if (timeoutRemainingMs <= 0L) {
            Log.w(TAG, "Keyguard unlock timeout reached in trampoline. Finishing.")
            cleanFinish()
            return
        }
        val step = 350L
        handler.postDelayed({
            pollKeyguardStatusUntilUnlocked(km, timeoutRemainingMs - step)
        }, step)
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanFinish()
    }
}
