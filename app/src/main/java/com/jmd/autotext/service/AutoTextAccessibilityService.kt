package com.jmd.autotext.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.jmd.autotext.AutoTextApplication
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ExecutionLog
import com.jmd.autotext.model.TaskStatus
import com.jmd.autotext.service.accessibility.*
import com.jmd.autotext.ui.automation.AutomationLauncherActivity
import com.jmd.autotext.util.AppFileLogger
import com.jmd.autotext.util.PhoneNumberNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

class AutoTextAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private val whatsAppStatusEngine by lazy { WhatsAppStatusEngine(this) }
    private val whatsAppDirectSendEngine by lazy { WhatsAppDirectSendEngine(this) }
    private val whatsAppGroupEngine by lazy { WhatsAppGroupEngine(this) }
    private val whatsAppChatScanner by lazy { WhatsAppChatScanner(this) }
    private val keyguardUnlockEngine by lazy { KeyguardUnlockEngine(this, handler) }

    companion object {
        private const val TAG = "AutoTextA11y"

        var isServiceRunning = false
            private set

        @Volatile
        var isTestUnlockMode = false

        private var instance: AutoTextAccessibilityService? = null

        fun isKeyguardWindowShowing(): Boolean {
            return try {
                val root = instance?.rootInActiveWindow
                val pkg = root?.packageName?.toString() ?: ""
                pkg.contains("systemui", ignoreCase = true) || pkg.contains("keyguard", ignoreCase = true)
            } catch (_: Throwable) {
                false
            }
        }

        private var pendingPackageName: String? = null
        private var pendingTaskId: Long = 0
        private var pendingRecipient: String = ""
        private var pendingMessage: String = ""
        private var pendingMediaUri: String = ""
        private var isAutomating = false
        private var sendAttemptCount = 0

        // Send result tracking (used by batch campaigns and scheduled tasks)
        const val RESULT_NONE = -1
        const val RESULT_PENDING = 0
        const val RESULT_SUCCESS = 1
        const val RESULT_INVALID = 2
        const val RESULT_TIMEOUT = 3

        @Volatile
        var lastSendResult: Int = RESULT_NONE
            private set

        fun markDispatchPending() {
            lastSendResult = RESULT_PENDING
        }

        fun navigateBack(): Boolean {
            return try {
                instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
            } catch (_: Throwable) {
                false
            }
        }

        // Auto-Unlock properties
        private var isUnlockPending = false
        private var unlockPin: String = ""
        private var shouldRelockAfterSend: Boolean = false
        private var isBatchCampaignMode = false

        enum class AutomationMode {
            DIRECT_SEND,
            WHATSAPP_SEARCH_GROUP,
            WHATSAPP_STATUS,
            WHATSAPP_SCAN_CHATS,
            MANUAL_PICK_CHAT
        }

        @Volatile
        var currentMode: AutomationMode = AutomationMode.DIRECT_SEND
            private set

        // Callback when user taps a chat in manual picker mode
        var onManualChatPicked: ((chatTitle: String) -> Unit)? = null

        fun startManualChatPicker(context: Context, onPicked: (chatTitle: String) -> Unit) {
            val targetPkg = com.jmd.autotext.util.WhatsAppPackageHelper.getInstalledWhatsAppPackage(context)
            if (targetPkg == null) {
                isAutomating = false
                currentMode = AutomationMode.DIRECT_SEND
                Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                return
            }
            currentMode = AutomationMode.MANUAL_PICK_CHAT
            pendingPackageName = targetPkg
            isAutomating = true
            sendAttemptCount = 0
            onManualChatPicked = onPicked
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    context.startActivity(launchIntent)
                    Toast.makeText(context, "👉 Tap any WhatsApp chat or group to select it!", Toast.LENGTH_LONG).show()
                } else {
                    isAutomating = false
                    currentMode = AutomationMode.DIRECT_SEND
                    Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch WhatsApp for manual chat pick", e)
            }
        }

        // Preserve the requested operation for result logging even after a group is opened and
        // currentMode transitions to DIRECT_SEND for the actual message-send phase.
        private var pendingOperationMode: AutomationMode = AutomationMode.DIRECT_SEND

        fun startWhatsAppChatScan(context: Context, returnToApp: Boolean = true) {
            val targetPkg = com.jmd.autotext.util.WhatsAppPackageHelper.getInstalledWhatsAppPackage(context)
            if (targetPkg == null) {
                isAutomating = false
                currentMode = AutomationMode.DIRECT_SEND
                pendingPackageName = null
                lastSendResult = RESULT_INVALID
                Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                return
            }
            currentMode = AutomationMode.WHATSAPP_SCAN_CHATS
            pendingPackageName = targetPkg
            isAutomating = true
            sendAttemptCount = 0
            instance?.let { s ->
                s.whatsAppChatScanner.resetScanState()
                s.handler.removeCallbacks(s.sendRunnable)
                s.handler.postDelayed(s.sendRunnable, 650L)
            }
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    context.startActivity(launchIntent)
                    Log.d(TAG, "startWhatsAppChatScan: Launched $targetPkg for chat scan")
                    Toast.makeText(context, "🔍 Scanning WhatsApp chats & groups...", Toast.LENGTH_SHORT).show()
                } else {
                    isAutomating = false
                    currentMode = AutomationMode.DIRECT_SEND
                    pendingPackageName = null
                    lastSendResult = RESULT_INVALID
                    Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch WhatsApp for scan", e)
            }
        }

        fun prepareForGroup(
            taskId: Long,
            packageName: String,
            groupName: String,
            messageText: String,
            shouldRelock: Boolean = false,
            isBatchCampaign: Boolean = false
        ) {
            currentMode = AutomationMode.WHATSAPP_SEARCH_GROUP
            prepareForSend(taskId, packageName, groupName, messageText, shouldRelock, isBatchCampaign)
        }

        fun prepareForStatus(
            taskId: Long,
            packageName: String,
            messageText: String,
            shouldRelock: Boolean = false,
            mediaUri: String = ""
        ) {
            currentMode = AutomationMode.WHATSAPP_STATUS
            pendingMediaUri = mediaUri
            instance?.whatsAppStatusEngine?.resetState()
            prepareForSend(taskId, packageName, "STATUS", messageText, shouldRelock, false)
        }

        fun prepareForSend(
            taskId: Long,
            packageName: String,
            recipient: String,
            messageText: String,
            shouldRelock: Boolean = false,
            isBatchCampaign: Boolean = false
        ) {
            if (currentMode != AutomationMode.WHATSAPP_SEARCH_GROUP && currentMode != AutomationMode.WHATSAPP_STATUS) {
                currentMode = AutomationMode.DIRECT_SEND
            }
            pendingOperationMode = currentMode
            pendingTaskId = taskId
            pendingPackageName = packageName
            pendingRecipient = recipient
            pendingMessage = messageText
            isAutomating = true
            sendAttemptCount = 0
            isBatchCampaignMode = isBatchCampaign
            instance?.hasAttemptedSendClick = false
            instance?.groupSearchStep = 0
            shouldRelockAfterSend = shouldRelock
            lastSendResult = RESULT_PENDING
            Log.d(TAG, "Prepared automated WhatsApp send for $packageName, mode: $currentMode, recipient: $recipient, relock: $shouldRelock, batch: $isBatchCampaign")
            AppFileLogger.action(AutoTextApplication.instance, TAG, "Prepared WhatsApp send ($currentMode) to $recipient for task #$taskId")

            instance?.let { s ->
                s.handler.removeCallbacks(s.sendRunnable)
                s.handler.postDelayed(s.sendRunnable, 550L)
            }
        }

        fun armAutoUnlock(pin: String, shouldRelockAfterSend: Boolean = false) {
            val inst = instance
            if (isUnlockPending && inst != null && inst.keyguardUnlockEngine.isEnteringPinDigits) {
                Log.w(TAG, "armAutoUnlock: PIN digit entry is already actively in progress! Skipping duplicate request.")
                return
            }
            this.unlockPin = pin
            this.isUnlockPending = true
            this.shouldRelockAfterSend = shouldRelockAfterSend
            Log.d(TAG, "Armed auto-unlock. PIN length: ${pin.length}, relock: $shouldRelockAfterSend")
            inst?.let { s ->
                s.acquireUnlockWakeLock()
                s.keyguardUnlockEngine.isUnlockPending = true
                s.keyguardUnlockEngine.unlockPin = pin
                s.keyguardUnlockEngine.shouldRelockAfterSend = shouldRelockAfterSend
                s.keyguardUnlockEngine.isTestUnlockMode = isTestUnlockMode
                s.keyguardUnlockEngine.unlockAttemptCount = 0
                s.keyguardUnlockEngine.unlockLoopAttempts = 0
                s.keyguardUnlockEngine.isEnteringPinDigits = false
                s.startProactiveUnlockLoop()
                s.handler.removeCallbacks(s.masterDisarmRunnable)
                s.handler.postDelayed(s.masterDisarmRunnable, 15000L)
            }
        }

        // Pending automation to launch once screen is confirmed unlocked
        @Volatile var pendingUnlockTaskId: Long = -1L
        @Volatile var pendingUnlockChannel: ChannelType = ChannelType.WHATSAPP
        @Volatile var pendingUnlockRecipient: String = ""
        @Volatile var pendingUnlockMessage: String = ""
        @Volatile var pendingUnlockMediaUri: String = ""
        @Volatile var pendingUnlockBatch: Boolean = false

        fun setPendingAutomation(
            taskId: Long,
            channel: ChannelType,
            recipient: String,
            message: String,
            shouldRelock: Boolean,
            isBatch: Boolean,
            mediaUri: String = ""
        ) {
            pendingUnlockTaskId = taskId
            pendingUnlockChannel = channel
            pendingUnlockRecipient = recipient
            pendingUnlockMessage = message
            pendingUnlockMediaUri = mediaUri
            shouldRelockAfterSend = shouldRelock
            pendingUnlockBatch = isBatch
            Log.i(TAG, "Queued pending automation for post-unlock: to=$recipient, task=$taskId, media=$mediaUri")
        }

        fun notifyHeartbeat() {
        }

        fun lockScreenNow() {
            try {
                notifyHeartbeat()
                val inst = instance
                if (inst != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lockedCleanly = inst.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    if (lockedCleanly) {
                        Log.i(TAG, "Screen locked cleanly via Accessibility GLOBAL_ACTION_LOCK_SCREEN")
                        return
                    }
                }
                if (inst != null && AutoTextDeviceAdminReceiver.isAdminActive(inst)) {
                    val locked = AutoTextDeviceAdminReceiver.lockNow(inst)
                    if (locked) {
                        Log.i(TAG, "Screen locked via Device Admin lockNow() fallback")
                        return
                    }
                }
                inst?.performGlobalAction(GLOBAL_ACTION_HOME)
            } catch (t: Throwable) {
                Log.e(TAG, "Error executing lockScreenNow", t)
            }
        }

        fun testLockAndAutoUnlock(context: Context, pin: String) {
            if (!isServiceRunning) {
                Toast.makeText(context, "⚠️ Please enable AutoText Accessibility Service first", Toast.LENGTH_LONG).show()
                return
            }
            isTestUnlockMode = true
            Toast.makeText(
                context,
                "🔒 Locking screen now... Auto-unlock test will trigger in 4 seconds. Keep phone steady!",
                Toast.LENGTH_LONG
            ).show()

            lockScreenNow()

            val appContext = context.applicationContext
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.postDelayed({
                try {
                    val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    @Suppress("DEPRECATION")
                    val wakeLock = pm?.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "AutoText:TestUnlockWakeLock"
                    )
                    wakeLock?.acquire(15000L)

                    val testIntent = Intent(appContext, AutomationLauncherActivity::class.java).apply {
                        putExtra(AutomationLauncherActivity.EXTRA_IS_TEST_UNLOCK, true)
                        putExtra(AutomationLauncherActivity.EXTRA_PIN, pin)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    appContext.startActivity(testIntent)

                    armAutoUnlock(pin = pin, shouldRelockAfterSend = false)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error initiating test auto unlock", t)
                }
            }, 3500L)
        }

        fun clearPending() {
            if (lastSendResult == RESULT_PENDING) lastSendResult = RESULT_TIMEOUT
            isAutomating = false
            isUnlockPending = false
            isTestUnlockMode = false
            isBatchCampaignMode = false
            currentMode = AutomationMode.DIRECT_SEND
            unlockPin = ""
            pendingPackageName = null
            pendingTaskId = 0
            pendingRecipient = ""
            pendingMessage = ""
            pendingUnlockTaskId = -1L
            pendingUnlockRecipient = ""
            pendingUnlockMessage = ""
            pendingUnlockMediaUri = ""
            pendingMediaUri = ""
            instance?.let { s ->
                s.releaseUnlockWakeLock()
                s.hasAttemptedSendClick = false
                s.groupSearchStep = 0
                s.keyguardUnlockEngine.isUnlockPending = false
                s.keyguardUnlockEngine.isEnteringPinDigits = false
                s.keyguardUnlockEngine.unlockAttemptCount = 0
                s.keyguardUnlockEngine.unlockLoopAttempts = 0
                s.handler.removeCallbacksAndMessages(null)
            }
            Log.i(TAG, "clearPending: All automations and pending tasks stopped.")
        }

        fun minimizeToHome(context: Context? = null) {
            try {
                val inst = instance
                if (inst != null) {
                    inst.performGlobalAction(GLOBAL_ACTION_HOME)
                    inst.handler.postDelayed({
                        inst.performGlobalAction(GLOBAL_ACTION_HOME)
                    }, 300L)
                } else {
                    val ctx = context ?: AutoTextApplication.instance
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    ctx.startActivity(homeIntent)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to minimizeToHome", t)
            }
        }

    }

    private var hasAttemptedSendClick = false
    @Volatile var groupSearchStep = 0
    private var unlockWakeLock: PowerManager.WakeLock? = null

    private fun acquireUnlockWakeLock() {
        try {
            if (unlockWakeLock?.isHeld != true) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                @Suppress("DEPRECATION")
                unlockWakeLock = pm?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "AutoText:UnlockWakeLock"
                )
                unlockWakeLock?.acquire(15000L)
                Log.d(TAG, "Acquired unlock WakeLock for 15s")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "acquireUnlockWakeLock error: ${t.message}")
        }
    }

    private fun releaseUnlockWakeLock() {
        try {
            if (unlockWakeLock?.isHeld == true) {
                unlockWakeLock?.release()
                Log.d(TAG, "Released unlock WakeLock")
            }
            unlockWakeLock = null
        } catch (_: Throwable) {}
    }

    private val masterDisarmRunnable = Runnable {
        if (isUnlockPending) {
            Log.w(TAG, "Master auto-unlock safety timeout fired (15s). Stopping all auto-unlock activity cleanly.")
            clearPending()
        }
    }

    private val debouncedUnlockRunnable = Runnable {
        runUnlockLogic()
    }

    private val unlockLoopRunnable = object : Runnable {
        override fun run() {
            try {
                if (!isUnlockPending) return
                val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                if (km != null && !km.isKeyguardLocked) {
                    Log.d(TAG, "Keyguard already unlocked according to KeyguardManager")
                    onUnlockSuccess()
                    return
                }

                if (keyguardUnlockEngine.isEnteringPinDigits) {
                    handler.postDelayed(this, 400L)
                    return
                }

                runUnlockLogic()

                keyguardUnlockEngine.unlockLoopAttempts++
                if (isUnlockPending && keyguardUnlockEngine.unlockLoopAttempts < 8) {
                    handler.postDelayed(this, 550L)
                } else if (keyguardUnlockEngine.unlockLoopAttempts >= 8) {
                    Log.w(TAG, "Proactive unlock loop reached max attempts (8). Stopping cleanly.")
                    isUnlockPending = false
                    keyguardUnlockEngine.isEnteringPinDigits = false
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in unlockLoopRunnable", t)
            }
        }
    }

    private fun startProactiveUnlockLoop() {
        handler.removeCallbacks(unlockLoopRunnable)
        handler.removeCallbacks(debouncedUnlockRunnable)
        keyguardUnlockEngine.unlockLoopAttempts = 0
        keyguardUnlockEngine.unlockAttemptCount = 0
        handler.post(unlockLoopRunnable)
    }

    private fun runUnlockLogic() {
        keyguardUnlockEngine.handleKeyguardUnlock(
            onUnlockSuccess = { onUnlockSuccess() },
            onUnlockAbort = { reason ->
                Log.w(TAG, "Auto-unlock aborted: $reason")
                isUnlockPending = false
                releaseUnlockWakeLock()
                if (isTestUnlockMode) {
                    isTestUnlockMode = false
                    Toast.makeText(this@AutoTextAccessibilityService, "⚠️ Auto-unlock aborted: $reason", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun onUnlockSuccess() {
        isUnlockPending = false
        keyguardUnlockEngine.isUnlockPending = false
        keyguardUnlockEngine.isEnteringPinDigits = false
        releaseUnlockWakeLock()
        handler.removeCallbacks(unlockLoopRunnable)
        handler.removeCallbacks(debouncedUnlockRunnable)
        handler.removeCallbacks(masterDisarmRunnable)

        if (isTestUnlockMode) {
            isTestUnlockMode = false
            Toast.makeText(this@AutoTextAccessibilityService, "🎉 Screen auto-unlocked successfully! Test passed.", Toast.LENGTH_LONG).show()
        }

        if (pendingUnlockTaskId != -1L || pendingUnlockRecipient.isNotBlank()) {
            val tId = pendingUnlockTaskId
            val ch = pendingUnlockChannel
            val rc = pendingUnlockRecipient
            val msg = pendingUnlockMessage
            val rel = shouldRelockAfterSend
            val bat = pendingUnlockBatch
            val med = pendingUnlockMediaUri

            pendingUnlockTaskId = -1L
            pendingUnlockRecipient = ""
            pendingUnlockMessage = ""
            pendingUnlockMediaUri = ""

            Log.i(TAG, "Auto-unlock confirmed! Launching queued WhatsApp message to $rc...")
            handler.postDelayed({
                launchWhatsAppFromService(tId, ch, rc, msg, rel, bat, med)
            }, 400L)
        }
    }

    private val sendRunnable: Runnable = object : Runnable {
        override fun run() {
            try {
                if (!isAutomating) return
                // Count every processing pass so all state-machine branches have a bounded timeout.
                sendAttemptCount++
                val targetPkg = pendingPackageName
                if (targetPkg.isNullOrBlank()) {
                    Log.e(TAG, "Automation was active without a target package; clearing the invalid pending operation.")
                    clearPending()
                    return
                }
                val activeRoots = AccessibilityNodeUtils.getTargetRoots(this@AutoTextAccessibilityService, windows)
                val activeRoot = activeRoots.firstOrNull { it.packageName?.contains(targetPkg, ignoreCase = true) == true }
                    ?: rootInActiveWindow

                if (activeRoot == null) {
                    if (sendAttemptCount < 45) {
                        handler.postDelayed(this, 300L)
                    } else {
                        handleSendTimeout(targetPkg)
                    }
                    return
                }

                val curPkg = activeRoot.packageName?.toString() ?: ""
                if (!curPkg.contains(targetPkg, ignoreCase = true) && !targetPkg.contains(curPkg, ignoreCase = true)) {
                    if (sendAttemptCount < 45) {
                        handler.postDelayed(this, 300L)
                    } else {
                        handleSendTimeout(targetPkg)
                    }
                    return
                }

                when (currentMode) {
                    AutomationMode.WHATSAPP_SEARCH_GROUP -> {
                        whatsAppGroupEngine.tryHandleGroupSearchAndSend(
                            root = activeRoot,
                            pkg = targetPkg,
                            pendingGroupRecipient = pendingRecipient,
                            groupSearchStep = groupSearchStep,
                            sendAttemptCount = sendAttemptCount,
                            windows = windows,
                            onNextStep = { groupSearchStep = it; sendAttemptCount = 0 },
                            onOpenChat = {
                            currentMode = AutomationMode.DIRECT_SEND
                            sendAttemptCount = 0
                            hasAttemptedSendClick = false
                            handler.removeCallbacks(sendRunnable)
                            handler.postDelayed(sendRunnable, 350L)
                        },
                            onTimeout = { handleSendTimeout(targetPkg) },
                            onRetry = { handler.postDelayed(sendRunnable, it) }
                        )
                    }
                    AutomationMode.WHATSAPP_STATUS -> {
                        whatsAppStatusEngine.tryHandleStatusPost(
                            root = activeRoot,
                            targetPkg = targetPkg,
                            pendingMessage = pendingMessage,
                            isMediaStatus = pendingMediaUri.isNotBlank(),
                            sendAttemptCount = sendAttemptCount,
                            windows = windows,
                            onSuccess = { finishSendSuccess(targetPkg) },
                            onTimeout = { handleSendTimeout(targetPkg) },
                            onRetry = { handler.postDelayed(sendRunnable, it) }
                        )
                    }
                    AutomationMode.DIRECT_SEND -> {
                        whatsAppDirectSendEngine.tryClickSendButton(
                            root = activeRoot,
                            pkg = targetPkg,
                            pendingMessage = pendingMessage,
                            sendAttemptCount = sendAttemptCount,
                            hasAttemptedSendClick = hasAttemptedSendClick,
                            windows = windows,
                            onSuccess = { finishSendSuccess(targetPkg) },
                            onTimeout = { handleSendTimeout(targetPkg) },
                            onRetry = { handler.postDelayed(sendRunnable, it) },
                            onAttemptClicked = { hasAttemptedSendClick = true }
                        )
                    }
                    AutomationMode.WHATSAPP_SCAN_CHATS -> {
                        whatsAppChatScanner.tryScanWhatsAppChats(
                            root = activeRoot,
                            pkg = targetPkg,
                            sendAttemptCount = sendAttemptCount,
                            onRetry = { handler.postDelayed(sendRunnable, it) },
                            onFinish = {
                                isAutomating = false
                                currentMode = AutomationMode.DIRECT_SEND
                            }
                        )
                    }
                    AutomationMode.MANUAL_PICK_CHAT -> {
                        // User manual tap selection handled reactively via onAccessibilityEvent
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error inside WhatsApp automation state machine", t)
                val targetPkg = pendingPackageName
                if (isAutomating && targetPkg != null) {
                    if (sendAttemptCount >= 45) {
                        handleSendTimeout(targetPkg)
                    } else {
                        handler.postDelayed(this, 500L)
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        instance = this
        Log.d(TAG, "AutoText Accessibility Service Connected & Active")
        // A task may have been queued while Android was still binding this service.
        if (isAutomating) {
            handler.removeCallbacks(sendRunnable)
            handler.postDelayed(sendRunnable, 500L)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        instance = null
        Log.w(TAG, "AutoText Accessibility Service Unbound by OS")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        instance = null
        releaseUnlockWakeLock()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "AutoText Accessibility Service Destroyed")
    }

    fun launchWhatsAppFromService(
        taskId: Long,
        channel: ChannelType,
        recipient: String,
        message: String,
        shouldRelock: Boolean,
        isBatch: Boolean,
        mediaUri: String = ""
    ) {
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            val currentPkg = event.packageName?.toString() ?: return

            // 1. Handle Keyguard auto-unlock
            if (isUnlockPending) {
                if (keyguardUnlockEngine.isEnteringPinDigits) return
                val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                if (km?.isKeyguardLocked == true ||
                    currentPkg.contains("systemui", ignoreCase = true) ||
                    currentPkg.contains("lock", ignoreCase = true) ||
                    currentPkg.contains("guard", ignoreCase = true) ||
                    currentPkg.contains("biometric", ignoreCase = true)) {
                    handler.removeCallbacks(debouncedUnlockRunnable)
                    handler.postDelayed(debouncedUnlockRunnable, 450L)
                    return
                }
            }

            // 2. Handle Manual Chat / Group Picker mode
            if (isAutomating && currentMode == AutomationMode.MANUAL_PICK_CHAT) {
                if (currentPkg.contains("whatsapp", ignoreCase = true)) {
                    val root = rootInActiveWindow
                    var pickedName = ""

                    // User explicitly tapped on a chat item row
                    if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                        val sourceNode = event.source
                        if (sourceNode != null) {
                            pickedName = extractChatNameFromNode(sourceNode)
                        }
                    }

                    // Or inspect active conversation header if user opened a chat
                    if (pickedName.isBlank() && root != null) {
                        pickedName = extractHeaderTitle(root)
                    }

                    if (pickedName.isNotBlank() && AppRepository(applicationContext).isValidGroupName(pickedName)) {
                        Log.i(TAG, "User clicked and selected WhatsApp chat: '$pickedName'")
                        isAutomating = false
                        currentMode = AutomationMode.DIRECT_SEND

                        // Save to repository
                        AppRepository(applicationContext).saveWhatsAppGroup(pickedName)

                        // Trigger callback
                        val cb = onManualChatPicked
                        onManualChatPicked = null
                        handler.post {
                            cb?.invoke(pickedName)
                            Toast.makeText(this, "✅ Selected '$pickedName'!", Toast.LENGTH_SHORT).show()
                        }

                        // Bring AutoText app back to front
                        handler.postDelayed({
                            val appIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            }
                            if (appIntent != null) startActivity(appIntent)
                        }, 500L)
                        return
                    }
                }
            }

            // 3. Handle WhatsApp send automation
            if (!isAutomating) return
            val targetPkg = pendingPackageName ?: return
            if (!currentPkg.contains(targetPkg, ignoreCase = true) &&
                !currentPkg.contains("inputmethod", ignoreCase = true) &&
                !currentPkg.contains("latin", ignoreCase = true)) return

            handler.removeCallbacks(sendRunnable)
            handler.postDelayed(sendRunnable, 250L)
        } catch (t: Throwable) {
            Log.e(TAG, "Unhandled exception in onAccessibilityEvent", t)
        }
    }

    private fun extractChatNameFromNode(node: AccessibilityNodeInfo): String {
        val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
        q.add(node)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            if (id.endsWith("conversations_row_contact_name") || id.endsWith("chat_title") || id.endsWith("conversation_contact_name") || id.endsWith("contactpicker_row_name")) {
                val t = n.text?.toString()?.trim()
                if (!t.isNullOrBlank()) return t
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
        return ""
    }

    private fun extractHeaderTitle(root: AccessibilityNodeInfo): String {
        val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            if (id.endsWith("conversation_contact_name") || id.endsWith("group_title")) {
                val t = n.text?.toString()?.trim()
                if (!t.isNullOrBlank()) return t
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
        return ""
    }

    private fun finishSendSuccess(pkg: String) {
        lastSendResult = RESULT_SUCCESS
        val channelName = when {
            pkg.contains("w4b", ignoreCase = true) -> "WhatsApp Business"
            pendingOperationMode == AutomationMode.WHATSAPP_STATUS -> "WhatsApp Status"
            pendingOperationMode == AutomationMode.WHATSAPP_SEARCH_GROUP -> "WhatsApp Group"
            else -> "WhatsApp"
        }
        AppRepository(applicationContext).insertLog(
            ExecutionLog(
                taskId = pendingTaskId,
                channel = channelName,
                recipient = pendingRecipient,
                messageSnippet = pendingMessage.take(160),
                status = "SUCCESS",
                details = "Send action and composer-cleared UI transition verified; WhatsApp delivery receipt is not independently confirmed."
            )
        )
        if (pendingTaskId > 0) {
            AppRepository(applicationContext).updateTaskStatus(pendingTaskId, TaskStatus.DONE)
        }
        AppFileLogger.action(applicationContext, TAG, "Successfully delivered $channelName to $pendingRecipient for task #$pendingTaskId")
        Toast.makeText(this, "✅ $channelName send action completed for $pendingRecipient", Toast.LENGTH_SHORT).show()

        isAutomating = false
        hasAttemptedSendClick = false
        groupSearchStep = 0
        whatsAppStatusEngine.resetState()
        currentMode = AutomationMode.DIRECT_SEND

        if (shouldRelockAfterSend) {
            handler.postDelayed({
                minimizeToHome()
                handler.postDelayed({
                    Log.i(TAG, "Auto-locking screen following message delivery")
                    lockScreenNow()
                }, 500L)
            }, 600L)
        } else if (!isBatchCampaignMode) {
            handler.postDelayed({
                minimizeToHome()
            }, 500L)
        }
    }

    private fun handleSendTimeout(pkg: String) {
        lastSendResult = RESULT_TIMEOUT
        val channelName = when {
            pkg.contains("w4b", ignoreCase = true) -> "WhatsApp Business"
            pendingOperationMode == AutomationMode.WHATSAPP_STATUS -> "WhatsApp Status"
            pendingOperationMode == AutomationMode.WHATSAPP_SEARCH_GROUP -> "WhatsApp Group"
            else -> "WhatsApp"
        }
        AppFileLogger.w(applicationContext, TAG, "Automation timeout reached sending to $pendingRecipient")
        AppRepository(applicationContext).insertLog(
            ExecutionLog(
                taskId = pendingTaskId,
                channel = channelName,
                recipient = pendingRecipient,
                messageSnippet = pendingMessage.take(160),
                status = "FAILED",
                details = "Automation timed out or could not verify the requested UI transition. See app accessibility logs for the failing stage."
            )
        )
        if (pendingTaskId > 0) {
            AppRepository(applicationContext).updateTaskStatus(pendingTaskId, TaskStatus.FAILED)
        }
        isAutomating = false
        hasAttemptedSendClick = false
        groupSearchStep = 0
        whatsAppStatusEngine.resetState()
        currentMode = AutomationMode.DIRECT_SEND

        if (shouldRelockAfterSend) {
            handler.postDelayed({
                minimizeToHome()
                handler.postDelayed({ lockScreenNow() }, 400L)
            }, 400L)
        } else if (!isBatchCampaignMode) {
            handler.postDelayed({ minimizeToHome() }, 400L)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
        if (lastSendResult == RESULT_PENDING) lastSendResult = RESULT_TIMEOUT
        isAutomating = false
        isUnlockPending = false
        hasAttemptedSendClick = false
        handler.removeCallbacks(sendRunnable)
    }
}
