/**
 * Package: com.jmd.helper.ui.activity
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [TaskLauncherActivity].
 *
 * What it does:
 * Transparent trampoline Activity that wakes the display, handles keyguard dismissal flags, and launches WhatsApp or phone intents from background contexts (e.g. AlarmManager or Service) complying with Android 10+ background activity start restrictions.
 *
 * Linked With:
 * - Models & Entities: [com.jmd.helper.data.model.ChannelType]
 * - Services & Engines: [com.jmd.helper.service.TaskAccessibilityService], [com.jmd.helper.whatsapp.WhatsAppIntentDispatcher]
 * - Core Security & Utilities: [android.app.KeyguardManager], [android.app.NotificationManager], [android.os.PowerManager], [android.util.Log]
 * - Android Framework & Jetpack: [android.app.Activity], [android.os.Bundle], [android.os.Handler], [android.os.Looper]
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
 * What it is:
 * Transparent bridge activity enabling background task startup.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [TaskLauncherActivity].
 *
 * Linked with:
 * - Architecture: [TaskLauncherActivity], [TaskLauncherActivity]
 */
class TaskLauncherActivity : Activity() {
    /**
     * What it is:
     * Companion object constant and factory provider for [TaskLauncherActivity].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [TaskLauncherActivity]
     */

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

    private val dismissCallback = object : KeyguardManager.KeyguardDismissCallback() {
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
    /**
     * What it is:
     * Execution routine for [onCreate].
     *
     * What it does:
     * Coordinates onCreate operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskLauncherActivity]
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Acquire wake lock to ensure CPU remains active during unlock execution
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Helper:LauncherWakeLock",
            )
            wakeLock?.acquire(15000L)
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock: ${e.message}")
        }

        // Ensure activity shows over lock screen and turns the screen on (minSdk 26, O_MR1 27)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )

        // Keep window completely transparent so lockscreen bouncer receives direct touches
        window.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())

        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.cancel(9988)
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
        val keyguardManager = getSystemService(KeyguardManager::class.java)

        // Handle Test Screen Unlock mode
        val isTestUnlock = intent.getBooleanExtra(EXTRA_IS_TEST_UNLOCK, false)
        if (isTestUnlock) {
            val passedPin = intent.getStringExtra(EXTRA_PIN) ?: prefs.deviceUnlockPin
            TaskAccessibilityService.isTestUnlockMode = true
            TaskAccessibilityService.armScreenAssist(
                pin = passedPin,
                shouldRelockAfterSend = false,
            )
            keyguardManager.requestDismissKeyguard(this, dismissCallback)
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
    /**
     * What it is:
     * Execution routine for [cleanFinish].
     *
     * What it does:
     * Coordinates cleanFinish operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskLauncherActivity]
     */

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
    /**
     * What it is:
     * Execution routine for [pollKeyguardStatusUntilUnlocked].
     *
     * What it does:
     * Coordinates pollKeyguardStatusUntilUnlocked operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskLauncherActivity]
     */

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
     * What it is:
     * Forwards task payloads to [WhatsAppIntentDispatcher] and gracefully finishes trampoline activity.
     *
     * What it does:
     * Executes [launchWhatsApp] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TaskLauncherActivity], [launchWhatsApp]
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
    /**
     * What it is:
     * Execution routine for [onResume].
     *
     * What it does:
     * Coordinates onResume operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskLauncherActivity]
     */

    override fun onResume() {
        super.onResume()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }
    /**
     * What it is:
     * Execution routine for [onWindowFocusChanged].
     *
     * What it does:
     * Coordinates onWindowFocusChanged operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskLauncherActivity]
     */

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            if (keyguardManager?.isKeyguardLocked == true) {
                Log.d(TAG, "TaskLauncher gained window focus over keyguard. Requesting keyguard dismissal.")
                keyguardManager.requestDismissKeyguard(this, dismissCallback)
            }
        }
    }
    /**
     * What it is:
     * Execution routine for [onPause].
     *
     * What it does:
     * Coordinates onPause operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskLauncherActivity]
     */

    override fun onPause() {
        super.onPause()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            AppFileLogger.d(this, "TaskLauncher", "WakeLock release: ${e.message}")
        }
        wakeLock = null
    }
    /**
     * What it is:
     * Execution routine for [onDestroy].
     *
     * What it does:
     * Coordinates onDestroy operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskLauncherActivity]
     */

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

}

// ============================================================================
// CALL SIMULATOR ACTIVITY
// ============================================================================
