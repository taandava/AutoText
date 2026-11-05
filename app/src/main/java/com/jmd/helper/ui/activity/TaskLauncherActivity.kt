

/**
 * Package: com.jmd.helper.ui.activity
 * Module: UI Task Trampoline Layer
 *
 * Transparent trampoline Activity that wakes the display, handles keyguard dismissal flags,
 * and launches WhatsApp or phone intents from background contexts (e.g. AlarmManager or Service)
 * complying with Android 10+ background activity start restrictions.
 *
 * Linked With:
 * - Engine: [com.jmd.helper.engine.SequentialTaskEngine]
 * - Dispatcher: [com.jmd.helper.whatsapp.WhatsAppIntentDispatcher]
 * - Keyguard: [com.jmd.helper.whatsapp.KeyguardUnlockEngine]
 * - Service: [com.jmd.helper.service.TaskAccessibilityService]
 */
package com.jmd.helper.ui.activity

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
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.service.TaskAccessibilityService
import com.jmd.helper.whatsapp.WhatsAppIntentDispatcher

/**
 * Transparent bridge activity enabling background task startup.
 */
class TaskLauncherActivity : Activity() {

    companion object {
        private const val TAG = "AutoLauncherAct"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_CHANNEL = "extra_channel"
        const val EXTRA_RECIPIENT = "extra_recipient"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_RELOCK = "extra_relock"
        const val EXTRA_IS_BATCH = "extra_is_batch"
        const val EXTRA_IS_TEST_UNLOCK = "extra_is_test_unlock"
        const val EXTRA_PIN = "extra_pin"
        const val EXTRA_MEDIA_URI = "extra_media_uri"
        const val EXTRA_WAKE_ONLY = "extra_wake_only"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Acquire wake lock to ensure CPU remains active during unlock execution
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AutoText:LauncherWakeLock",
            )
            wakeLock?.acquire(15000L)
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock: ${e.message}")
        }

        // Ensure activity shows over lock screen and turns the screen on (minSdk 26, O_MR1 27)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Keep window completely transparent so lockscreen bouncer receives direct touches
        window.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())

        runCatching {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(9988)
        }

        val prefs = PreferenceManager(this)
        if (intent.getBooleanExtra(EXTRA_IS_TEST_UNLOCK, false)) {
            prefs.isA11yUserPaused = false
        }

        // Verify or enable accessibility service just-in-time for lockscreen dispatch
        runCatching {
            if (!AccessibilityHealthManager.isServiceRunning() && AccessibilityHealthManager.hasWriteSecureSettings(this)) {
                AccessibilityHealthManager.enableAccessibilityService(this)
            } else {
                AccessibilityHealthManager.ensureServiceAlive(this)
            }
        }
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

        val dismissCallback = object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                Log.i(TAG, "Keyguard dismiss succeeded callback received.")
                cleanFinish()
            }
            override fun onDismissCancelled() {
                Log.w(TAG, "Keyguard dismiss cancelled callback received; allowing accessibility unlock engine to continue.")
            }
            override fun onDismissError() {
                Log.e(TAG, "Keyguard dismiss error callback received; waiting for polling timeout or accessibility unlock.")
            }
        }

        // Handle Test Screen Unlock mode
        val isTestUnlock = intent.getBooleanExtra(EXTRA_IS_TEST_UNLOCK, false)
        if (isTestUnlock) {
            val passedPin = intent.getStringExtra(EXTRA_PIN) ?: prefs.deviceUnlockPin
            TaskAccessibilityService.isTestUnlockMode = true
            keyguardManager.requestDismissKeyguard(this, dismissCallback)

            TaskAccessibilityService.armScreenAssist(
                pin = passedPin,
                shouldRelockAfterSend = false,
            )
            pollKeyguardStatusUntilUnlocked(keyguardManager, 12000L)
            return
        }

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val channelStr = intent.getStringExtra(EXTRA_CHANNEL) ?: ChannelType.WHATSAPP.name
        val channel = ChannelType.fromString(channelStr)
        val recipient = intent.getStringExtra(EXTRA_RECIPIENT) ?: ""
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI) ?: ""

        val isWakeOnly = intent.getBooleanExtra(EXTRA_WAKE_ONLY, false)
        if (isWakeOnly) {
            val isLockedNow = keyguardManager.isKeyguardLocked || keyguardManager.isDeviceLocked ||
                    TaskAccessibilityService.isKeyguardWindowShowing()
            AppFileLogger.action(this, TAG, "Wake-only trampoline invoked. Locked: $isLockedNow, Task: #$taskId, Channel: $channelStr")

            if (isLockedNow && prefs.isScreenAssistEnabled) {
                val pinToUse = if (prefs.screenAssistType != "SWIPE") prefs.screenAssistPin else ""
                TaskAccessibilityService.armScreenAssist(
                    pin = pinToUse,
                    shouldRelockAfterSend = prefs.isAutoLockAfterSendEnabled,
                )
            }
            keyguardManager.requestDismissKeyguard(this, dismissCallback)
            pollKeyguardStatusUntilUnlocked(keyguardManager, 12000L)
            return
        }

        val shouldRelock = intent.getBooleanExtra(EXTRA_RELOCK, prefs.isAutoLockAfterSendEnabled)
        val isLocked = keyguardManager.isKeyguardLocked || keyguardManager.isDeviceLocked ||
                TaskAccessibilityService.isKeyguardWindowShowing()

        Log.d(TAG, "Task launcher created. Screen locked: $isLocked, Task: $taskId, To: $recipient, Media: $mediaUri")
        AppFileLogger.action(this, TAG, "Task launcher activated. Locked: $isLocked, Task: #$taskId, Recipient: $recipient, Media: $mediaUri")

        if (isLocked && prefs.isScreenAssistEnabled) {
            val pinToUse = if (prefs.screenAssistType != "SWIPE") prefs.screenAssistPin else ""
            AppFileLogger.action(this, TAG, "Arming screen task assistance routine for task #$taskId")
            TaskAccessibilityService.setPendingTask(
                taskId = taskId,
                channel = channel,
                recipient = recipient,
                message = message,
                shouldRelock = shouldRelock,
                isBatch = intent.getBooleanExtra(EXTRA_IS_BATCH, false),
                mediaUri = mediaUri,
            )
            TaskAccessibilityService.armScreenAssist(
                pin = pinToUse,
                shouldRelockAfterSend = shouldRelock,
            )

            keyguardManager.requestDismissKeyguard(this, dismissCallback)
            pollKeyguardStatusUntilUnlocked(keyguardManager, 12000L)
        } else {
            // Already unlocked or auto-unlock disabled
            when (channel) {
                ChannelType.CALL -> {
                    AppFileLogger.action(this, TAG, "Screen awake/unlocked. Dismissing trampoline for Phone Call to $recipient")
                    keyguardManager.requestDismissKeyguard(this, dismissCallback)
                    handler.postDelayed({ cleanFinish() }, 300L)
                }
                ChannelType.WHATSAPP_CALL, ChannelType.WHATSAPP_BUSINESS_CALL -> {
                    AppFileLogger.action(this, TAG, "Screen awake/unlocked, launching WhatsApp call directly")
                    WhatsAppIntentDispatcher.launchWhatsAppCall(
                        context = this,
                        taskId = taskId,
                        channel = channel,
                        recipient = recipient,
                        shouldRelock = shouldRelock,
                    )
                    handler.postDelayed({ cleanFinish() }, 900L)
                }
                else -> {
                    AppFileLogger.action(this, TAG, "Screen already unlocked or auto-unlock disabled, launching WhatsApp directly")
                    launchWhatsApp(taskId, channel, recipient, message, mediaUri)
                }
            }
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
            Log.i(TAG, "Device confirmed unlocked during trampoline wait. Finishing trampoline cleanly.")
            cleanFinish()
            return
        }
        if (timeoutRemainingMs <= 0L) {
            Log.w(TAG, "Keyguard unlock timeout reached in trampoline. Finishing safely.")
            cleanFinish()
            return
        }
        val step = 350L
        handler.postDelayed({
            pollKeyguardStatusUntilUnlocked(km, timeoutRemainingMs - step)
        }, step)
    }

    /**
     * Forwards task payloads to [WhatsAppIntentDispatcher] and gracefully finishes trampoline activity.
     *
     * @param taskId Unique identifier of the scheduled task.
     * @param channel Target channel type (WhatsApp or WA Business).
     * @param recipient Target phone number or group name.
     * @param message Message body to send.
     * @param mediaUri Optional local URI of image/video attachment.
     */
    private fun launchWhatsApp(taskId: Long, channel: ChannelType, recipient: String, message: String, mediaUri: String = "") {
        val prefs = PreferenceManager(this)
        val shouldRelock = intent.getBooleanExtra(EXTRA_RELOCK, prefs.isAutoLockAfterSendEnabled)
        val isBatch = intent.getBooleanExtra(EXTRA_IS_BATCH, false)

        WhatsAppIntentDispatcher.launchWhatsApp(
            context = this,
            taskId = taskId,
            channel = channel,
            recipient = recipient,
            message = message,
            shouldRelock = shouldRelock,
            isBatch = isBatch,
            mediaUri = mediaUri,
        )

        // Finish launcher activity shortly after dispatching
        handler.postDelayed(
            {
                runCatching {
                    wakeLock?.release()
                    wakeLock = null
                }
                finish()
            },
            900,
        )
    }

    override fun onResume() {
        super.onResume()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }

    override fun onPause() {
        super.onPause()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

}

// ============================================================================
// CALL SIMULATOR ACTIVITY
// ============================================================================


