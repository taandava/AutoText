package com.jmd.autotext.ui.automation

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.util.AppFileLogger
import com.jmd.autotext.util.PhoneNumberNormalizer
import java.net.URLEncoder

class AutomationLauncherActivity : Activity() {

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
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Acquire bright wake lock to wake screen for lockscreen auto-unlock
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AutoText:LauncherWakeLock"
            )
            wakeLock?.acquire(15000L)
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock: ${e.message}")
        }

        // Ensure activity shows over lock screen and turns the screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        // Keep window completely transparent so lockscreen bouncer receives direct touches
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(9988)
        }

        // Verify or heal accessibility service just-in-time if dead
        runCatching {
            com.jmd.autotext.service.AccessibilityHealthManager.ensureServiceAlive(this)
        }

        val prefs = PreferenceManager(this)
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        // Disable keyguard via deprecated KeyguardLock (vital for OEM devices like Vivo/Samsung)
        @Suppress("DEPRECATION")
        runCatching {
            val keyguardLock = keyguardManager.newKeyguardLock("AutoTextLock")
            keyguardLock.disableKeyguard()
            keyguardManager.exitKeyguardSecurely(null)
        }

        // Handle Test Screen Unlock mode
        val isTestUnlock = intent.getBooleanExtra(EXTRA_IS_TEST_UNLOCK, false)
        if (isTestUnlock) {
            val passedPin = intent.getStringExtra(EXTRA_PIN) ?: prefs.deviceUnlockPin
            AutoTextAccessibilityService.isTestUnlockMode = true
            AutoTextAccessibilityService.armAutoUnlock(
                pin = passedPin,
                shouldRelockAfterSend = false
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(this, null)
            }
            runCatching {
                wakeLock?.release()
                wakeLock = null
            }
            finish()
            return
        }

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val channelStr = intent.getStringExtra(EXTRA_CHANNEL) ?: ChannelType.WHATSAPP.name
        val channel = runCatching { ChannelType.valueOf(channelStr) }.getOrDefault(ChannelType.WHATSAPP)
        val recipient = intent.getStringExtra(EXTRA_RECIPIENT) ?: ""
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI) ?: ""

        val shouldRelock = intent.getBooleanExtra(EXTRA_RELOCK, prefs.isAutoLockAfterSendEnabled)
        val isLocked = keyguardManager.isKeyguardLocked || keyguardManager.isDeviceLocked ||
                AutoTextAccessibilityService.isKeyguardWindowShowing()

        Log.d(TAG, "Automation launcher created. Screen locked: $isLocked, Task: $taskId, To: $recipient, Media: $mediaUri")
        AppFileLogger.action(this, TAG, "Automation launcher activated. Locked: $isLocked, Task: #$taskId, Recipient: $recipient, Media: $mediaUri")

        if (isLocked && prefs.isAutoUnlockEnabled) {
            val pinToUse = if (prefs.autoUnlockType != "SWIPE") prefs.deviceUnlockPin else ""
            AppFileLogger.action(this, TAG, "Arming auto-unlock routine for task #$taskId")
            AutoTextAccessibilityService.setPendingAutomation(
                taskId = taskId,
                channel = channel,
                recipient = recipient,
                message = message,
                shouldRelock = shouldRelock,
                isBatch = intent.getBooleanExtra(EXTRA_IS_BATCH, false),
                mediaUri = mediaUri
            )
            AutoTextAccessibilityService.armAutoUnlock(
                pin = pinToUse,
                shouldRelockAfterSend = shouldRelock
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(this, null)
            }
            runCatching {
                wakeLock?.release()
                wakeLock = null
            }
            finish()
        } else {
            // Already unlocked or auto-unlock disabled
            AppFileLogger.action(this, TAG, "Screen already unlocked or auto-unlock disabled, launching WhatsApp directly")
            launchWhatsApp(taskId, channel, recipient, message, mediaUri)
        }
    }

    private fun launchWhatsApp(taskId: Long, channel: ChannelType, recipient: String, message: String, mediaUri: String = "") {
        val prefs = PreferenceManager(this)
        val shouldRelock = intent.getBooleanExtra(EXTRA_RELOCK, prefs.isAutoLockAfterSendEnabled)
        val isBatch = intent.getBooleanExtra(EXTRA_IS_BATCH, false)

        com.jmd.autotext.service.accessibility.WhatsAppIntentDispatcher.launchWhatsApp(
            context = this,
            taskId = taskId,
            channel = channel,
            recipient = recipient,
            message = message,
            shouldRelock = shouldRelock,
            isBatch = isBatch,
            mediaUri = mediaUri
        )

        // Finish launcher activity shortly after dispatching
        handler.postDelayed({
            runCatching {
                wakeLock?.release()
                wakeLock = null
            }
            finish()
        }, 900)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        }
    }

    @android.annotation.SuppressLint("Wakelock")
    override fun onDestroy() {
        super.onDestroy()
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        }
    }
}
