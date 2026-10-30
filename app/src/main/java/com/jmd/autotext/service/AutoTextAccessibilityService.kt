

/**
 * Package: com.jmd.autotext.service
 * Module: Accessibility Automation Service
 *
 * Background accessibility service delivering physical UI interaction, window inspection,
 * dynamic popup dismissal, text injection, and send button clicks across WhatsApp Messenger and WhatsApp Business.
 *
 * Linked With:
 * - Engine: [com.jmd.autotext.engine.WhatsAppAutomationSender], [com.jmd.autotext.engine.SequentialTaskEngine]
 * - Sub-Engines: [com.jmd.autotext.whatsapp.WhatsAppDirectSendEngine], [com.jmd.autotext.whatsapp.WhatsAppStatusEngine],
 *   [com.jmd.autotext.whatsapp.WhatsAppGroupEngine], [com.jmd.autotext.whatsapp.PopupShield],
 *   [com.jmd.autotext.whatsapp.VoiceNoteProtectionGuard], [com.jmd.autotext.whatsapp.KeyguardUnlockEngine]
 * - Health: [com.jmd.autotext.core.AccessibilityHealthManager]
 * - Diagnostics: [com.jmd.autotext.core.AppFileLogger]
 */
package com.jmd.autotext.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.jmd.autotext.AutoTextApplication
import com.jmd.autotext.ui.activity.AutomationLauncherActivity
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.core.PhoneNumberNormalizer
import com.jmd.autotext.core.WhatsAppPackageHelper
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.DynamicContactItem
import com.jmd.autotext.data.model.ExecutionLog
import com.jmd.autotext.data.model.PickedChatInfo
import com.jmd.autotext.data.model.PickedChatType
import com.jmd.autotext.data.model.TaskStatus
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.receiver.AutoTextDeviceAdminReceiver
import com.jmd.autotext.whatsapp.AccessibilityNodeUtils
import com.jmd.autotext.whatsapp.KeyguardUnlockEngine
import com.jmd.autotext.whatsapp.PopupShield
import com.jmd.autotext.whatsapp.WhatsAppChatScanner
import com.jmd.autotext.whatsapp.WhatsAppDirectSendEngine
import com.jmd.autotext.whatsapp.WhatsAppGroupEngine
import com.jmd.autotext.whatsapp.WhatsAppIntentDispatcher
import com.jmd.autotext.whatsapp.WhatsAppStatusEngine
import com.jmd.autotext.whatsapp.WhatsAppCallEngine
import com.jmd.autotext.core.AutomationSafetyManager

/**
 * Android Accessibility Service executing automated WhatsApp UI workflows.
 *
 * Implements strict security invariants:
 * - Never modifies device auto-rotate or unrelated display settings.
 * - Safely recovers from lockouts, dialog traps, or blocked numbers.
 * - Supports emergency kill-switch via [AutomationSafetyManager].
 */
class AutoTextAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private val whatsAppStatusEngine by lazy { WhatsAppStatusEngine(this) }
    private val whatsAppDirectSendEngine by lazy { WhatsAppDirectSendEngine(this) }
    private val whatsAppGroupEngine by lazy { WhatsAppGroupEngine(this) }
    private val whatsAppChatScanner by lazy { WhatsAppChatScanner(this) }
    private val whatsAppCallEngine by lazy { WhatsAppCallEngine(this) }
    private val keyguardUnlockEngine by lazy { KeyguardUnlockEngine(this, handler) }

    companion object {
        private const val TAG = "AutoTextA11y"

        var isServiceRunning = false
            private set

        @Volatile
        var isTestUnlockMode = false

        @Volatile
        var currentForegroundPackage: String? = null

        private var instance: AutoTextAccessibilityService? = null

        /**
         * Returns the active live accessibility service instance, or null if unbound/disconnected.
         */
        fun getInstance(): AutoTextAccessibilityService? = instance

        /**
         * Known banking, UPI, wallet, and financial package prefixes.
         * The accessibility service strictly drops events and never inspects window content for these packages.
         */
        val KNOWN_FINANCIAL_PACKAGE_PREFIXES = listOf(
            "com.sbi.",
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.",
            "net.one97.paytm",
            "com.icicibank.",
            "com.hdfcbank.",
            "com.axis.",
            "com.kotak.",
            "in.org.npci.",
            "com.csam.icici.",
            "com.yono.",
            "com.bank.",
            "com.saraswatbank.",
            "com.freecharge.",
            "com.mobikwik.",
            "com.bajajfinserv.",
            "com.cred.",
            "com.paytm.",
            "com.amazon.mShop.android.shopping.in",
            "com.rblbank.",
            "com.indusind.",
            "com.canarabank.",
            "com.pnb.",
            "com.bob.",
            "com.unionbankofindia.",
            "com.bankofbaroda.",
            "com.idbi.",
            "com.federalbank.",
            "com.yesbank.",
            "com.aubank."
        )

        /**
         * Returns true if the package belongs to a banking, payment, or financial application.
         */
        fun isFinancialOrBankingPackage(pkg: String): Boolean {
            if (pkg.isBlank()) return false
            val lower = pkg.lowercase()
            for (prefix in KNOWN_FINANCIAL_PACKAGE_PREFIXES) {
                if (lower.startsWith(prefix) || lower == prefix) return true
            }
            return lower.contains("saraswat") || lower.contains("gomonxt") || lower.contains("banking") ||
                lower.contains("bank") || lower.contains(".yono.") || lower.contains("npci.bhim") ||
                lower.contains(".upi.") || lower.contains("mobikwik") || lower.contains("paytm") ||
                lower.contains("phonepe") || lower.contains("paisa") || lower.contains("cred.app") ||
                lower.contains("axis") || lower.contains("icici") || lower.contains("kotak") ||
                lower.contains("hdfc") || lower.contains("pnb") || lower.contains("canara") ||
                lower.contains("indusind") || lower.contains("baroda") || lower.contains("unionbank") ||
                lower.contains("bajaj") || lower.contains("navi") || lower.contains("groww") ||
                lower.contains("zerodha") || lower.contains("angelone") || lower.contains("dhan") ||
                lower.contains("upstox")
        }
        /**
         * Checks whether the active window belongs to the system keyguard or Android SystemUI lockscreen.
         *
         * @return True if keyguard or bouncer package is currently rendering on display.
         */
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

        private const val STANDBY_EVENT_MASK =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START

        private const val ACTIVE_AUTOMATION_EVENT_MASK =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_CLICKED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED or
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START

        @Volatile
        private var isStandbyThrottlingActive = true

        fun setStandbyEventMask(standby: Boolean) {
            isStandbyThrottlingActive = standby
        }

        private fun setAutomatingState(automating: Boolean) {
            isAutomating = automating
            setStandbyEventMask(!automating)
        }

        /**
         * Returns the task ID currently undergoing accessibility automation, or 0 if idle.
         */
        fun getActiveTaskId(): Long = pendingTaskId

        // Send result tracking (used by batch campaigns and scheduled tasks)
        const val RESULT_NONE = -1
        const val RESULT_PENDING = 0
        const val RESULT_SUCCESS = 1
        const val RESULT_INVALID = 2
        const val RESULT_TIMEOUT = 3

        @Volatile
        var lastSendResult: Int = RESULT_NONE
            private set

        /**
         * Resets and arms last send result tracker into [RESULT_PENDING] state.
         */
        fun markDispatchPending() {
            lastSendResult = RESULT_PENDING
        }

        /**
         * Executes an accessibility global back navigation action.
         *
         * @return True if system back event was dispatched successfully.
         */
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
            MANUAL_PICK_CHAT,
            WHATSAPP_VOICE_CALL
        }

        @Volatile
        var currentMode: AutomationMode = AutomationMode.DIRECT_SEND
            private set

        // Callback when user taps a chat in manual picker mode
        var onManualChatInfoPicked: ((chatInfo: PickedChatInfo) -> Unit)? = null
        var onManualChatPicked: ((chatTitle: String) -> Unit)? = null

        /**
         * Launches WhatsApp in manual picker mode, allowing the user to tap any chat or group to capture its name and metadata.
         *
         * @param context Android context for launching WhatsApp package intent.
         * @param targetPackage Explicit WhatsApp package identifier (com.whatsapp or com.whatsapp.w4b).
         * @param channel Channel configuration indicating standard or business WhatsApp.
         * @param onPicked Callback invoked when accessibility service detects active conversation header.
         */
        fun startManualChatPicker(
            context: Context,
            targetPackage: String? = null,
            channel: ChannelType? = null,
            onPicked: (chatInfo: PickedChatInfo) -> Unit,
        ) {
            val targetPkg = targetPackage ?: (if (channel != null) WhatsAppPackageHelper.getPackageForChannel(context, channel) else null)
                ?: WhatsAppPackageHelper.getInstalledWhatsAppPackage(context)
            val isBusiness = (targetPkg == WhatsAppPackageHelper.PACKAGE_WHATSAPP_BUSINESS) || (channel == ChannelType.WHATSAPP_BUSINESS)
            val appLabel = if (isBusiness) "WhatsApp Business" else "WhatsApp"

            if (targetPkg == null) {
                setAutomatingState(false)
                currentMode = AutomationMode.DIRECT_SEND
                context.showAppToast("$appLabel is not installed")
                return
            }
            if (AutomationSafetyManager.isEmergencyStopActive(context)) {
                context.showAppToast("Emergency stop is active. Reset safeguards in Settings first.")
                return
            }
            currentMode = AutomationMode.MANUAL_PICK_CHAT
            pendingPackageName = targetPkg
            setAutomatingState(true)
            sendAttemptCount = 0
            onManualChatInfoPicked = onPicked
            onManualChatPicked = null
            AutomationSafetyManager.lastPickerLaunchTimestamp = System.currentTimeMillis()
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    context.startActivity(launchIntent)
                    context.showAppToast("Tap a chat in $appLabel to select", isLong = true)

                    instance?.let { s ->
                        s.handler.removeCallbacksAndMessages(null)
                        s.handler.postDelayed({
                            if (isAutomating && currentMode == AutomationMode.MANUAL_PICK_CHAT) {
                                Log.i(TAG, "Manual chat picker safety timeout (30s) reached. Resetting mode.")
                                clearPending()
                            }
                        }, 30_000L)
                    }
                } else {
                    setAutomatingState(false)
                    currentMode = AutomationMode.DIRECT_SEND
                    context.showAppToast("Could not open $appLabel")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch $appLabel for manual chat pick", e)
            }
        }

        /**
         * Simplified manual chat picker returning only the resolved conversation title string.
         *
         * @param context Android context for launching target application.
         * @param targetPackage Explicit WhatsApp package identifier.
         * @param channel Channel configuration indicating standard or business WhatsApp.
         * @param onPickedTitle Callback invoked with captured chat title.
         */
        fun startManualChatPickerTitle(
            context: Context,
            targetPackage: String? = null,
            channel: ChannelType? = null,
            onPickedTitle: (chatTitle: String) -> Unit,
        ) {
            startManualChatPicker(context, targetPackage, channel) { info ->
                onPickedTitle(info.title)
            }
        }


        // Preserve the requested operation for result logging even after a group is opened and
        // currentMode transitions to DIRECT_SEND for the actual message-send phase.
        private var pendingOperationMode: AutomationMode = AutomationMode.DIRECT_SEND

        // When true, the app is brought back to the foreground once a WhatsApp chat scan finishes.
        private var pendingReturnToApp: Boolean = false

        /**
         * Opens WhatsApp and drives the automated accessibility chat scanner across recent conversation items.
         *
         * @param context Local context for launching package intent.
         * @param targetPackage WhatsApp package to inspect (standard or business).
         * @param channel Target channel type.
         * @param scanTarget Scan target scope (ALL_CHATS, GROUPS_ONLY, DIRECT_CHATS_ONLY).
         * @param returnToApp When true, returns to AutoText application upon completing scan.
         */
        fun startWhatsAppChatScan(
            context: Context,
            targetPackage: String? = null,
            channel: ChannelType? = null,
            scanTarget: WhatsAppChatScanner.ScanTarget = WhatsAppChatScanner.ScanTarget.ALL_CHATS,
            returnToApp: Boolean = true
        ) {
            pendingReturnToApp = returnToApp
            val targetPkg = targetPackage ?: (if (channel != null) WhatsAppPackageHelper.getPackageForChannel(context, channel) else null)
                ?: WhatsAppPackageHelper.getInstalledWhatsAppPackage(context)
            val isBusiness = targetPkg == WhatsAppPackageHelper.PACKAGE_WHATSAPP_BUSINESS || channel == ChannelType.WHATSAPP_BUSINESS
            val appLabel = if (isBusiness) "WhatsApp Business" else "WhatsApp"

            if (targetPkg == null) {
                setAutomatingState(false)
                currentMode = AutomationMode.DIRECT_SEND
                pendingPackageName = null
                lastSendResult = RESULT_INVALID
                context.showAppToast("$appLabel is not installed")
                return
            }
            currentMode = AutomationMode.WHATSAPP_SCAN_CHATS
            pendingPackageName = targetPkg
            setAutomatingState(true)
            sendAttemptCount = 0
            instance?.let { s ->
                s.whatsAppChatScanner.resetScanState(scanTarget)
                s.handler.removeCallbacks(s.sendRunnable)
                s.handler.postDelayed(s.sendRunnable, 650L)
            }
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    context.startActivity(launchIntent)
                    Log.d(TAG, "startWhatsAppChatScan: Launched $targetPkg for chat scan")
                    context.showAppToast("Scanning $appLabel chats...")
                } else {
                    setAutomatingState(false)
                    currentMode = AutomationMode.DIRECT_SEND
                    pendingPackageName = null
                    lastSendResult = RESULT_INVALID
                    context.showAppToast("Could not open $appLabel")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch $appLabel for scan", e)
            }
        }

        /**
         * Arms automation engine for group message dispatch by transitioning to group search mode.
         *
         * @param taskId Unique task ID.
         * @param packageName WhatsApp package to target.
         * @param groupName Group chat title.
         * @param messageText Message body to dispatch.
         * @param shouldRelock True to auto-lock device upon completing dispatch.
         * @param isBatchCampaign True if part of recurring daily campaign.
         */
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

        /**
         * Arms automation engine for posting text or media to WhatsApp Status.
         *
         * @param taskId Unique task ID.
         * @param packageName WhatsApp package to target.
         * @param messageText Text body or status caption.
         * @param shouldRelock True to auto-lock device after posting.
         * @param mediaUri Local URI of photo or video to post.
         */
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
            prepareForSend(taskId, packageName, "STATUS", messageText, shouldRelock, isBatchCampaign = false)
        }

        /**
         * Arms automation engine for executing outbound WhatsApp voice calls.
         *
         * @param taskId Unique task ID.
         * @param packageName WhatsApp package to target.
         * @param recipient Target phone number or contact name.
         * @param callDurationSeconds Auto-hangup timer in seconds.
         * @param shouldRelock True to re-lock device after completing call.
         */
        fun prepareForWhatsAppCall(
            taskId: Long,
            packageName: String,
            recipient: String,
            callDurationSeconds: Int = 0,
            shouldRelock: Boolean = false
        ) {
            currentMode = AutomationMode.WHATSAPP_VOICE_CALL
            pendingOperationMode = currentMode
            pendingTaskId = taskId
            pendingPackageName = packageName
            pendingRecipient = recipient
            pendingMessage = "WhatsApp Voice Call"
            setAutomatingState(true)
            sendAttemptCount = 0
            isBatchCampaignMode = false
            instance?.hasAttemptedSendClick = false
            instance?.whatsAppCallEngine?.resetState()
            shouldRelockAfterSend = shouldRelock
            lastSendResult = RESULT_PENDING
            Log.d(TAG, "Prepared automated WhatsApp voice call for $packageName, recipient: $recipient, duration: ${callDurationSeconds}s, relock: $shouldRelock")
            AppFileLogger.action(AutoTextApplication.instance, TAG, "Prepared WhatsApp voice call to $recipient for task #$taskId")

            instance?.let { s ->
                s.handler.removeCallbacks(s.sendRunnable)
                s.handler.postDelayed(s.sendRunnable, 450L)
            }
        }

        /**
         * Programmatically terminates an active WhatsApp voice call via accessibility tap on End Call button.
         *
         * @param pkg Target WhatsApp package identifier.
         * @return True if end call action was executed.
         */
        @JvmStatic
        fun endWhatsAppCall(pkg: String): Boolean {
            return instance?.requestEndWhatsAppCall(pkg) ?: false
        }

        /**
         * Programmatically terminates an active phone call via TelecomManager.endCall() or accessibility click on in-call UI.
         *
         * @param context Active context used to access TelecomManager.
         * @return True if the call was terminated.
         */
        @JvmStatic
        fun endPhoneCall(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    if (tm != null) {
                        try {
                            val endCallMethod = tm.javaClass.getMethod("endCall")
                            val ended = endCallMethod.invoke(tm) as? Boolean == true
                            if (ended) {
                                AppFileLogger.action(context, TAG, "Terminated active phone call via TelecomManager.endCall()")
                                return true
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "TelecomManager.endCall invocation: ${e.message}")
                        }
                    }
                }
            }
            return instance?.requestEndPhoneCall() ?: false
        }

        /**
         * Sets up state variables, logs action, and arms send timeout watchdog for direct message dispatch.
         *
         * @param taskId Unique task ID.
         * @param packageName Target WhatsApp package.
         * @param recipient Target phone number or contact name.
         * @param messageText Message body.
         * @param shouldRelock True if phone should be locked after dispatch.
         * @param isBatchCampaign True if running inside batch campaign loop.
         */
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
            setAutomatingState(true)
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

        /**
         * Arms the KeyguardUnlockEngine to watch for lockscreen bouncer appearance and enter PIN digits.
         *
         * @param pin Secret keyguard PIN or swipe code.
         * @param shouldRelockAfterSend True to re-lock screen after sending finishes.
         */
        @JvmStatic
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

        /**
         * Queues automation payload parameters to execute immediately upon confirmed lockscreen dismissal.
         *
         * @param taskId Unique task ID.
         * @param channel Target channel.
         * @param recipient Target recipient phone or group.
         * @param message Message body.
         * @param shouldRelock True if hardware lock should be restored after dispatch.
         * @param isBatch True if running inside batch campaign.
         * @param mediaUri Local URI of image or video.
         */
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

        /**
         * Records accessibility service timestamp heartbeat to [AccessibilityHealthManager].
         */
        fun notifyHeartbeat() {
            AccessibilityHealthManager.recordHeartbeat()
        }

        /**
         * Immediately locks the physical screen using Android Pie+ accessibility global action or DeviceAdmin lockNow fallback.
         */
        @JvmStatic
        fun lockScreenNow(): Boolean {
            return try {
                notifyHeartbeat()
                val inst = instance
                val ctx: Context = inst ?: runCatching { AutoTextApplication.instance }.getOrNull() ?: return false
                if (inst != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lockedCleanly = inst.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    if (lockedCleanly) {
                        Log.i(TAG, "Screen locked cleanly via Accessibility GLOBAL_ACTION_LOCK_SCREEN")
                        AppFileLogger.i(ctx, TAG, "Screen locked cleanly via Accessibility GLOBAL_ACTION_LOCK_SCREEN")
                        return true
                    }
                }
                val locked = AutoTextDeviceAdminReceiver.lockNow(ctx)
                if (locked) {
                    Log.i(TAG, "Screen locked via Device Admin lockNow() fallback")
                    AppFileLogger.i(ctx, TAG, "Screen locked via Device Admin lockNow() fallback")
                    return true
                }
                inst?.performGlobalAction(GLOBAL_ACTION_HOME)
                Log.w(TAG, "Screen lock fell back to HOME; ensure Device Admin is active")
                AppFileLogger.w(ctx, TAG, "Screen lock fell back to HOME; ensure Device Admin is active")
                false
            } catch (t: Throwable) {
                Log.e(TAG, "Error executing lockScreenNow", t)
                false
            }
        }

        /**
         * Checks whether WhatsApp is currently rendering an active VoIP call screen.
         */
        @JvmStatic
        fun isInWhatsAppCall(): Boolean {
            val inst = instance ?: return false
            val root = inst.rootInActiveWindow ?: return false
            return inst.whatsAppCallEngine.isInVoipCallScreen(root, inst.windows)
        }

        /**
         * Checks whether WhatsApp is currently rendering an active, answered VoIP call with conversation timer.
         */
        @JvmStatic
        fun isWhatsAppCallConnected(): Boolean {
            val inst = instance ?: return false
            val root = inst.rootInActiveWindow ?: return false
            return inst.whatsAppCallEngine.isVoipCallConnected(root, inst.windows)
        }

        /**
         * Checks whether a native cellular in-call or dialer screen is actively rendered.
         */
        @JvmStatic
        fun isPhoneCallUiActive(): Boolean {
            val inst = instance ?: return false
            val targetRoots = AccessibilityNodeUtils.getTargetRoots(inst, inst.windows)
            for (root in targetRoots) {
                val pkg = root.packageName?.toString().orEmpty().lowercase()
                if (pkg.contains("incall") || pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("telecom")) {
                    return true
                }
            }
            return false
        }

        /**
         * Diagnostic tool locking the phone immediately and triggering automated unlock 4 seconds later to verify setup.
         *
         * @param context Local Android context.
         * @param pin Secret lockscreen PIN to test.
         */
        fun testLockAndAutoUnlock(context: Context, pin: String) {
            if (!isServiceRunning) {
                context.showAppToast("Enable Accessibility first", isLong = true)
                return
            }
            isTestUnlockMode = true
            context.showAppToast("🔒 Locking screen now... Auto-unlock test will trigger in 4 seconds. Keep phone steady!", isLong = true)

            lockScreenNow()

            val appContext = context.applicationContext
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.postDelayed({
                try {
                    val pm = appContext.getSystemService(POWER_SERVICE) as? PowerManager

                    val wakeLock = pm?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
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

        /**
         * Clears all pending automation variables, disarms keyguard watchdog, and resets engine back to idle.
         */
        @JvmStatic
        fun clearPending() {
            if (lastSendResult == RESULT_PENDING) lastSendResult = RESULT_TIMEOUT
            setAutomatingState(false)
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
            onManualChatPicked = null
            onManualChatInfoPicked = null
            instance?.let { s ->
                s.releaseUnlockWakeLock()
                s.hasAttemptedSendClick = false
                s.groupSearchStep = 0
                s.whatsAppCallEngine.resetState()
                s.keyguardUnlockEngine.isUnlockPending = false
                s.keyguardUnlockEngine.isEnteringPinDigits = false
                s.keyguardUnlockEngine.unlockAttemptCount = 0
                s.keyguardUnlockEngine.unlockLoopAttempts = 0
                s.handler.removeCallbacksAndMessages(null)
            }
            Log.i(TAG, "clearPending: All automations and pending tasks stopped.")
        }

        /**
         * Checks whether accessibility automation is currently running an active dispatch workflow.
         */
        fun isCurrentlyAutomating(): Boolean = isAutomating

        /**
         * Safely aborts active accessibility automation upon detected human interaction.
         */
        fun abortDueToHumanInterruption() {
            val taskId = pendingTaskId
            clearPending()
            Log.w(TAG, "Active accessibility automation aborted due to human interaction (Task #$taskId)")
            instance?.let { s ->
                AppFileLogger.action(s, TAG, "Aborted active accessibility automation for Task #$taskId due to detected human interaction.")
            }
        }

        /**
         * Navigates the device back to the home launcher screen using accessibility global actions.
         *
         * @param context Optional Android context for fallback home intent dispatch.
         */
        @JvmStatic
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

        /**
         * Disables the accessibility service directly using disableSelf() on API 24+ without requiring
         * manual navigation through system settings. Allows immediate access to banking and UPI apps.
         */
        fun disableServiceIfActive(): Boolean {
            return instance?.disableService() ?: false
        }
    }

    /**
     * Programmatically disables this accessibility service directly on API 24+ (Android 7.0+).
     */
    fun disableService(): Boolean {
        return try {
            disableSelf()
            isServiceRunning = false
            instance = null
            AppFileLogger.action(this, TAG, "Accessibility service disabled via disableSelf() for banking compatibility.")
            true
        } catch (e: Exception) {
            AppFileLogger.e(this, TAG, "Error disabling accessibility service", e)
            false
        }
    }

    private var hasAttemptedSendClick = false
    @Volatile var groupSearchStep = 0
    private var unlockWakeLock: PowerManager.WakeLock? = null

    private fun acquireUnlockWakeLock() {
        try {
            if (unlockWakeLock?.isHeld != true) {
                val pm = getSystemService(POWER_SERVICE) as? PowerManager

                unlockWakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
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
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to release unlock wake lock: ${t.message}")
        }
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
                    showAppToast("Auto-unlock aborted: $reason", isLong = true)
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
            showAppToast("Auto-unlock verified!", isLong = true)
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

            Log.i(TAG, "Auto-unlock confirmed! Clearing pop-ups before dispatching $ch to $rc...")
            handler.postDelayed({
                PopupShield.clearAllBlockingPopups(this@AutoTextAccessibilityService) {
                    when (ch) {
                        ChannelType.CALL -> {
                            launchPhoneCallFromService(rc)
                        }
                        ChannelType.WHATSAPP_CALL, ChannelType.WHATSAPP_BUSINESS_CALL -> {
                            WhatsAppIntentDispatcher.launchWhatsAppCall(
                                context = this@AutoTextAccessibilityService,
                                taskId = tId,
                                channel = ch,
                                recipient = rc,
                                shouldRelock = rel
                            )
                        }
                        else -> {
                            launchWhatsAppFromService(tId, ch, rc, msg, rel, bat, med)
                        }
                    }
                }
            }, 400L)
        }
    }

    private val sendRunnable: Runnable = object : Runnable {
        override fun run() {
            try {
                if (!isAutomating || currentMode == AutomationMode.MANUAL_PICK_CHAT) return
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
                val privacyMgr = com.jmd.autotext.core.PrivacyManager.getInstance(this@AutoTextAccessibilityService)
                // Stealth Protection Guard: Instantly drop if an excluded or banking app is foregrounded
                if (privacyMgr.isAppExcluded(curPkg) || isFinancialOrBankingPackage(curPkg)) {
                    Log.d(TAG, "sendRunnable: Excluded or banking app foregrounded ($curPkg); skipping window inspection")
                    return
                }

                // --- Pop-up & Overlay Shield: dismiss notification shade, Truecaller,
                // floating overlays, and system dialogs before evaluating automation actions
                if (PopupShield.clearOverlaysAndNotificationShade(this@AutoTextAccessibilityService, targetPkg)) {
                    Log.d(TAG, "sendRunnable: overlay/popup cleared; pausing 300ms for UI to settle")
                    handler.postDelayed(this, 300L)
                    return
                }

                val isTargetOrAllowedDialog = curPkg.contains(targetPkg, ignoreCase = true) ||
                    targetPkg.contains(curPkg, ignoreCase = true) ||
                    curPkg.equals("android", ignoreCase = true) ||
                    curPkg.contains("permissioncontroller", ignoreCase = true) ||
                    curPkg.contains("packageinstaller", ignoreCase = true)

                if (!isTargetOrAllowedDialog) {
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
                            sendAttemptCount = sendAttemptCount,
                            windows = windows,
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
                            pendingRecipient = pendingRecipient,
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
                                setAutomatingState(false)
                                currentMode = AutomationMode.DIRECT_SEND
                                if (pendingReturnToApp) {
                                    pendingReturnToApp = false
                                    runCatching {
                                        val back = packageManager.getLaunchIntentForPackage(packageName)
                                        back?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                        back?.let { startActivity(it) }
                                    }.onFailure {
                                        Log.w(TAG, "Could not return to app after chat scan: ${it.message}")
                                    }
                                }
                            }
                        )
                    }
                    AutomationMode.WHATSAPP_VOICE_CALL -> {
                        whatsAppCallEngine.tryHandleVoiceCall(
                            root = activeRoot,
                            pkg = targetPkg,
                            pendingRecipient = pendingRecipient,
                            sendAttemptCount = sendAttemptCount,
                            windows = windows,
                            onSuccess = { finishCallSuccess(targetPkg) },
                            onTimeout = { handleSendTimeout(targetPkg) },
                            onRetry = { handler.postDelayed(sendRunnable, it) }
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
        try {
            val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
            info.flags = info.flags or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            info.eventTypes = ACTIVE_AUTOMATION_EVENT_MASK
            serviceInfo = info
            com.jmd.autotext.core.PrivacyManager.getInstance(this).updateAccessibilityScoping()
        } catch (_: Exception) {}
        isServiceRunning = true
        instance = this
        Log.d(TAG, "AutoText Accessibility Service Connected & Active (Global Interaction & Idle Guard Active)")
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
        AppFileLogger.w(applicationContext ?: this, TAG, "AutoText Accessibility Service Unbound by OS")
        // Returning true is critical so Android framework invokes onRebind when reconnecting
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        isServiceRunning = true
        instance = this
        try {
            val info = serviceInfo ?: android.accessibilityservice.AccessibilityServiceInfo()
            info.flags = info.flags or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            info.eventTypes = ACTIVE_AUTOMATION_EVENT_MASK
            serviceInfo = info
            com.jmd.autotext.core.PrivacyManager.getInstance(this).updateAccessibilityScoping()
        } catch (_: Exception) {}
        AccessibilityHealthManager.recordHeartbeat()
        Log.i(TAG, "AutoText Accessibility Service Re-bound by OS and Active")
        AppFileLogger.i(this, TAG, "AutoText Accessibility Service Re-bound by OS and Active")
        if (isAutomating) {
            handler.removeCallbacks(sendRunnable)
            handler.postDelayed(sendRunnable, 300L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        instance = null
        releaseUnlockWakeLock()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "AutoText Accessibility Service Destroyed")
    }

    /**
     * Helper to dispatch WhatsApp launch intent directly from the accessibility service context.
     *
     * @param taskId Unique task ID.
     * @param channel Target channel.
     * @param recipient Target recipient phone or group.
     * @param message Message body.
     * @param shouldRelock True if phone should be locked after completion.
     * @param isBatch True if running as part of batch campaign.
     * @param mediaUri Local URI of image or video attachment.
     */
    fun launchWhatsAppFromService(
        taskId: Long,
        channel: ChannelType,
        recipient: String,
        message: String,
        shouldRelock: Boolean,
        isBatch: Boolean,
        mediaUri: String = ""
    ) {
        WhatsAppIntentDispatcher.launchWhatsApp(
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

    /**
     * Primary accessibility event handler intercepting window changes, user interactions, and popups across targeted packages.
     *
     * @param event The accessibility event dispatched by Android framework.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            if (!isAutomating) {
                val type = event.eventType
                if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    type == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                    type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                ) {
                    return
                }
            }
            val currentPkg = event.packageName?.toString() ?: return

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                currentForegroundPackage = currentPkg
            }

            // Privacy & Banking Shield Guard: Completely isolate accessibility from excluded & banking apps
            // Never inspect, read, or process events from financial or excluded applications
            val privacyMgr = com.jmd.autotext.core.PrivacyManager.getInstance(this)
            if (privacyMgr.isAppExcluded(currentPkg) || isFinancialOrBankingPackage(currentPkg)) {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    if (com.jmd.autotext.core.AccessibilityHealthManager.hasWriteSecureSettings(this) && !isAutomating) {
                        Log.i(TAG, "Banking/Shielded app foreground ($currentPkg), immediately retracting accessibility registration")
                        com.jmd.autotext.core.AccessibilityHealthManager.disableAccessibilityService(applicationContext)
                    }
                }
                return
            }

            val eventType = event.eventType

            // Standby Event Throttling: When idle and not automating or unlocking, filter non-interaction events
            if (isStandbyThrottlingActive && !isAutomating && !isUnlockPending) {
                if (eventType != AccessibilityEvent.TYPE_TOUCH_INTERACTION_START &&
                    eventType != AccessibilityEvent.TYPE_GESTURE_DETECTION_START &&
                    eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    return
                }
            }

            // 0a. Detect Genuine Human Touch Interactions without false scroll/layout flags
            val isGenuineUserTouch = when (eventType) {
                AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> true
                else -> false
            }

            // Recognized system and automation-related packages that must never trigger takeover aborts
            val isAllowedAutomationPackage = currentPkg.contains("whatsapp", ignoreCase = true) ||
                currentPkg == packageName ||
                currentPkg.equals("android", ignoreCase = true) ||
                currentPkg.contains("permissioncontroller", ignoreCase = true) ||
                currentPkg.contains("packageinstaller", ignoreCase = true) ||
                currentPkg.contains("systemui", ignoreCase = true) ||
                currentPkg.contains("incallui", ignoreCase = true) ||
                currentPkg.contains("dialer", ignoreCase = true) ||
                currentPkg.contains("telecom", ignoreCase = true) ||
                currentPkg.contains("telephony", ignoreCase = true) ||
                currentPkg.contains("phone", ignoreCase = true) ||
                currentPkg.contains("contacts", ignoreCase = true) ||
                currentPkg.contains("truecaller", ignoreCase = true) ||
                currentPkg.contains("keyguard", ignoreCase = true) ||
                currentPkg.contains("inputmethod", ignoreCase = true) ||
                currentPkg.contains("latin", ignoreCase = true) ||
                currentPkg.contains("launcher", ignoreCase = true) ||
                currentPkg.contains("home", ignoreCase = true) ||
                currentPkg.contains("vivo", ignoreCase = true) ||
                currentPkg.contains("nexuslauncher", ignoreCase = true) ||
                currentPkg.contains("trebuchet", ignoreCase = true)

            if (isAutomating) {
                // If user switched away from target automation package to an unrelated third-party app
                if (!isAllowedAutomationPackage) {
                    Log.w(TAG, "Human interrupted automation! User navigated to: $currentPkg")
                    AutomationSafetyManager.recordHumanInteraction(this, "User navigated to $currentPkg during automation")
                    return
                }
                // Real physical touch detected during automation
                if (isGenuineUserTouch) {
                    Log.w(TAG, "Physical human touch interaction detected during automation in $currentPkg")
                    AutomationSafetyManager.recordHumanInteraction(this, "Physical touch interaction in $currentPkg")
                    return
                }
            } else {
                // When idle, only record interaction if screen is on and it's a real user touch event
                val pm = getSystemService(POWER_SERVICE) as? PowerManager
                val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                if (pm?.isInteractive == true && km?.isKeyguardLocked == false && isGenuineUserTouch) {
                    AutomationSafetyManager.recordHumanInteraction(this, "Physical touch in $currentPkg")
                }
            }

            // 0. Global Automation Kill Switch
            val prefs = com.jmd.autotext.data.prefs.PreferenceManager(this)
            val isEmergencyStop = AutomationSafetyManager.isEmergencyStopActive(this)
            if (isEmergencyStop || !prefs.isTasksMasterEnabled) {
                if (isAutomating) {
                    Log.w(TAG, "Master switch disabled or emergency stop active! Halting accessibility automation immediately.")
                    setAutomatingState(false)
                    isUnlockPending = false
                    clearPending()
                    handler.removeCallbacksAndMessages(null)
                }
                return
            }

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
                    var clickedNode: AccessibilityNodeInfo? = null

                    // User explicitly tapped on a chat item row
                    if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                        val sourceNode = event.source
                        if (sourceNode != null) {
                            clickedNode = sourceNode
                            pickedName = extractChatNameFromNode(sourceNode)
                        }
                    }

                    // Only inspect active conversation header if user actually opened a conversation
                    if (pickedName.isBlank() && root != null && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                        if (isInsideActiveConversation(root)) {
                            pickedName = extractHeaderTitle(root)
                        }
                    }

                    val cleanPicked = pickedName.trim().removePrefix("~").trim()
                    val repo = AppRepository(applicationContext)
                    if (cleanPicked.isNotBlank() && repo.isValidPickedChatName(cleanPicked)) {
                        Log.i(TAG, "User clicked and selected WhatsApp chat: '$cleanPicked'")
                        setAutomatingState(false)
                        currentMode = AutomationMode.DIRECT_SEND

                        val chatInfo = analyzePickedChat(
                            sourceNode = clickedNode,
                            root = root,
                            chatTitle = cleanPicked,
                            pkg = currentPkg,
                            repo = repo
                        )

                        Log.i(TAG, "Resolved WhatsApp chat type for '${chatInfo.title}': ${chatInfo.type} (phone=${chatInfo.phoneOrId}, subtitle='${chatInfo.subtitle}')")

                        when (chatInfo.type) {
                            PickedChatType.INDIVIDUAL -> {
                                val phoneToSave = chatInfo.phoneOrId ?: repo.findPhoneForContactName(chatInfo.title) ?: chatInfo.title
                                val norm = PhoneNumberNormalizer.normalize(phoneToSave)
                                val isBiz = currentPkg.contains("w4b", ignoreCase = true)
                                val src = if (isBiz) "WA_BUSINESS" else "WA_CHAT"
                                repo.upsertDynamicPoolBatch(listOf(
                                    DynamicContactItem(
                                        name = chatInfo.title,
                                        phone = phoneToSave,
                                        normalizedNumber = norm,
                                        source = src
                                    )
                                ))
                                // Clean up any erroneous entry in groups table
                                repo.deleteWhatsAppGroup(chatInfo.title)
                            }
                            PickedChatType.GROUP -> {
                                repo.saveWhatsAppGroup(chatInfo.title, memberCount = chatInfo.subtitle)
                            }
                            PickedChatType.BROADCAST, PickedChatType.CHANNEL, PickedChatType.STATUS -> {
                                // Handled via callback
                            }
                        }

                        // Trigger callbacks
                        val cbInfo = onManualChatInfoPicked
                        val cbTitle = onManualChatPicked
                        onManualChatInfoPicked = null
                        onManualChatPicked = null

                        handler.post {
                            if (cbInfo != null) {
                                cbInfo.invoke(chatInfo)
                            } else {
                                cbTitle?.invoke(chatInfo.title)
                            }
                            val typeLabel = chatInfo.type.displayName
                            showAppToast("Selected $typeLabel: $cleanPicked")
                        }

                        // Bring AutoText app back to front
                        handler.postDelayed({
                            val appIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            }
                            if (appIntent != null) startActivity(appIntent)
                        }, 400L)
                        return
                    }
                }
            }

            // 3. Handle WhatsApp send automation
            if (!isAutomating) return
            val targetPkg = pendingPackageName ?: return
            if (!currentPkg.contains(targetPkg, ignoreCase = true) &&
                !currentPkg.contains("inputmethod", ignoreCase = true) &&
                !currentPkg.contains("latin", ignoreCase = true) &&
                !currentPkg.equals("android", ignoreCase = true) &&
                !currentPkg.contains("permissioncontroller", ignoreCase = true) &&
                !currentPkg.contains("packageinstaller", ignoreCase = true)) return

            handler.removeCallbacks(sendRunnable)
            handler.postDelayed(sendRunnable, 250L)
        } catch (t: Throwable) {
            Log.e(TAG, "Unhandled exception in onAccessibilityEvent", t)
        }
    }

    private fun extractChatNameFromNode(node: AccessibilityNodeInfo): String {
        val direct = searchChatNameInSubtree(node)
        if (direct.isNotBlank()) return direct

        var parent = node.parent
        var levels = 0
        while (parent != null && levels < 5) {
            val fromParent = searchChatNameInSubtree(parent)
            if (fromParent.isNotBlank()) return fromParent
            parent = parent.parent
            levels++
        }
        return ""
    }

    private fun searchChatNameInSubtree(root: AccessibilityNodeInfo): String {
        val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            if (id.endsWith("conversations_row_contact_name") || id.endsWith("chat_title") ||
                id.endsWith("conversation_contact_name") || id.endsWith("contactpicker_row_name") ||
                id.endsWith("contact_row_name") || id.endsWith("c_name")) {
                val t = n.text?.toString()?.trim()
                if (!t.isNullOrBlank()) return t
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
        return ""
    }

    private fun isInsideActiveConversation(root: AccessibilityNodeInfo): Boolean {
        val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            if (id.endsWith("entry") || id.endsWith("conversation_text_entry") ||
                id.endsWith("input_layout") || id.endsWith("send")) {
                return true
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    private fun extractHeaderTitle(root: AccessibilityNodeInfo): String {
        val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            // Strictly exclude generic action bar/toolbar titles (e.g. "WhatsApp", "Chats")
            if (id.endsWith("conversation_contact_name") || id.endsWith("group_title") ||
                id.endsWith("chat_title")) {
                val t = n.text?.toString()?.trim()
                if (!t.isNullOrBlank()) return t
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
        return ""
    }

    private fun analyzePickedChat(
        sourceNode: AccessibilityNodeInfo?,
        root: AccessibilityNodeInfo?,
        chatTitle: String,
        pkg: String,
        repo: AppRepository
    ): PickedChatInfo {
        val clean = chatTitle.trim().removePrefix("~").trim()
        val lower = clean.lowercase()

        val nodePkg = sourceNode?.packageName?.toString() ?: root?.packageName?.toString() ?: ""
        if (pkg.isNotBlank() && nodePkg.isNotBlank() && !nodePkg.contains(pkg)) {
            Log.d(TAG, "analyzePickedChat: node package '$nodePkg' does not match target '$pkg'")
        }

        // 1. Status check
        if (lower == "status" || lower == "my status" || lower.startsWith("tap to add status")) {
            return PickedChatInfo(clean, PickedChatType.STATUS)
        }

        // Collect all text and content descriptions from sourceNode and its parent row
        val rowTexts = mutableListOf<String>()
        var hasGroupKeywordInRow = false
        var hasBroadcastKeywordInRow = false
        var hasChannelKeywordInRow = false

        var rowContainer = sourceNode
        var depth = 0
        while (rowContainer != null && depth < 5) {
            val id = rowContainer.viewIdResourceName?.lowercase().orEmpty()
            if (id.endsWith("conversations_row_layout") || id.endsWith("contactpicker_row") ||
                id.endsWith("contact_row_container") || id.endsWith("chat_row")
            ) {
                break
            }
            rowContainer = rowContainer.parent
            depth++
        }

        val targetSubtree = rowContainer ?: sourceNode
        if (targetSubtree != null) {
            val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
            q.add(targetSubtree)
            while (q.isNotEmpty()) {
                val n = q.removeFirst()
                val desc = n.contentDescription?.toString()?.trim().orEmpty()
                val txt = n.text?.toString()?.trim().orEmpty()
                val id = n.viewIdResourceName?.lowercase().orEmpty()

                if (desc.isNotBlank()) {
                    val descLower = desc.lowercase()
                    if (descLower.contains("group profile") || descLower.contains("group icon") ||
                        descLower.contains("group photo") || descLower.startsWith("group ")
                    ) {
                        hasGroupKeywordInRow = true
                    }
                    if (descLower.contains("broadcast")) {
                        hasBroadcastKeywordInRow = true
                    }
                    if (descLower.contains("channel") || descLower.contains("community") || descLower.contains("announcement")) {
                        hasChannelKeywordInRow = true
                    }
                }

                if (txt.isNotBlank()) {
                    rowTexts.add(txt)
                }

                if (id.endsWith("group_indicator") || id.endsWith("group_badge") || id.endsWith("group_title") || id.endsWith("group_avatar")) {
                    hasGroupKeywordInRow = true
                }
                if (id.endsWith("broadcast_indicator") || id.endsWith("broadcast_title")) hasBroadcastKeywordInRow = true
                if (id.endsWith("channel_indicator") || id.endsWith("channel_title")) hasChannelKeywordInRow = true

                for (i in 0 until n.childCount) {
                    n.getChild(i)?.let { q.add(it) }
                }
            }
        }

        // Check if row text indicates status
        if (rowTexts.any { it.contains("tap to add status", ignoreCase = true) || it.contains("disappears after", ignoreCase = true) }) {
            return PickedChatInfo(clean, PickedChatType.STATUS)
        }

        // 2. Active Conversation Header Inspection (if user navigated inside or window is conversation)
        var convSubtitle = ""
        var convHasCallButtons = false
        var convHasGroupHeader = false
        var convHasBroadcastHeader = false
        var convHasChannelHeader = false

        if (root != null && isInsideActiveConversation(root)) {
            val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
            q.add(root)
            while (q.isNotEmpty()) {
                val n = q.removeFirst()
                val id = n.viewIdResourceName?.lowercase().orEmpty()
                val txt = n.text?.toString()?.trim().orEmpty()
                val desc = n.contentDescription?.toString()?.trim().orEmpty()

                if (id.endsWith("conversation_contact_status") || id.endsWith("group_details_card_subtitle")) {
                    if (txt.isNotBlank()) convSubtitle = txt
                }

                if (desc.contains("voice call", ignoreCase = true) || desc.contains("audio call", ignoreCase = true) ||
                    desc.contains("video call", ignoreCase = true)
                ) {
                    convHasCallButtons = true
                }

                if (desc.contains("group info", ignoreCase = true) || desc.contains("group profile photo", ignoreCase = true)) {
                    convHasGroupHeader = true
                }
                if (desc.contains("broadcast", ignoreCase = true)) {
                    convHasBroadcastHeader = true
                }
                if (desc.contains("channel", ignoreCase = true) || desc.contains("community", ignoreCase = true)) {
                    convHasChannelHeader = true
                }

                for (i in 0 until n.childCount) {
                    n.getChild(i)?.let { q.add(it) }
                }
            }
        }

        val subLower = convSubtitle.lowercase()
        val isSubGroup = (subLower.contains("participant") || subLower.contains("member") ||
                subLower.contains("tap here for group info") || subLower.contains("सदस्य")) &&
                !subLower.contains("online") && !subLower.contains("typing") && !subLower.contains("last seen")

        val isSubBroadcast = subLower.contains("recipient") || subLower.contains("broadcast")
        val isSubChannel = subLower.contains("follower") || subLower.contains("channel") || subLower.contains("announcement")
        val isSubIndividual = subLower.contains("online") || subLower.contains("typing") || subLower.contains("last seen") ||
                PhoneNumberNormalizer.isMobileNumber(clean)

        // If a known phone number exists in device contacts or dynamic pool, or the title itself is a phone number,
        // it is unequivocally an INDIVIDUAL contact, never a group or channel.
        val phoneFound = repo.findPhoneForContactName(clean)
        val directDigits = if (clean.filter { it.isDigit() }.length >= 7) clean else null
        val resolvedPhone = phoneFound ?: directDigits

        if (resolvedPhone != null) {
            repo.deleteWhatsAppGroup(clean)
            return PickedChatInfo(
                title = clean,
                type = PickedChatType.INDIVIDUAL,
                phoneOrId = resolvedPhone,
                subtitle = convSubtitle
            )
        }

        // Evaluate priority:
        // A. Broadcast list
        if (hasBroadcastKeywordInRow || convHasBroadcastHeader || isSubBroadcast) {
            return PickedChatInfo(clean, PickedChatType.BROADCAST, subtitle = convSubtitle)
        }

        // B. Channel / Community
        if (hasChannelKeywordInRow || convHasChannelHeader || isSubChannel) {
            return PickedChatInfo(clean, PickedChatType.CHANNEL, subtitle = convSubtitle)
        }

        // C. Group (requires positive proof)
        val hasGroupSignals = (hasGroupKeywordInRow || convHasGroupHeader || isSubGroup) && !isSubIndividual && !convHasCallButtons
        if (hasGroupSignals) {
            return PickedChatInfo(clean, PickedChatType.GROUP, subtitle = convSubtitle)
        }

        // D. Individual / Normal Chat (default fallback for contacts)
        repo.deleteWhatsAppGroup(clean)

        return PickedChatInfo(
            title = clean,
            type = PickedChatType.INDIVIDUAL,
            phoneOrId = null,
            subtitle = convSubtitle
        )
    }

    private fun finishSendSuccess(pkg: String) {
        lastSendResult = RESULT_SUCCESS
        val channelName = when {
            pkg.contains("w4b", ignoreCase = true) && pendingOperationMode == AutomationMode.WHATSAPP_STATUS -> "WhatsApp Business Status"
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
        // Task status finalization is owned exclusively by SequentialTaskEngine upon completing all recipients.
        AppFileLogger.action(applicationContext, TAG, "Successfully delivered $channelName to $pendingRecipient for task #$pendingTaskId")
        showAppToast("Sent: $channelName")

        setAutomatingState(false)
        hasAttemptedSendClick = false
        groupSearchStep = 0
        whatsAppStatusEngine.resetState()
        currentMode = AutomationMode.DIRECT_SEND

        val needsRelock = shouldRelockAfterSend || keyguardUnlockEngine.shouldRelockAfterSend
        if (needsRelock) {
            shouldRelockAfterSend = false
            keyguardUnlockEngine.shouldRelockAfterSend = false
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

    private fun finishCallSuccess(pkg: String) {
        lastSendResult = RESULT_SUCCESS
        val channelName = if (pkg.contains("w4b", ignoreCase = true)) "WhatsApp Business Call" else "WhatsApp Call"
        AppRepository(applicationContext).insertLog(
            ExecutionLog(
                taskId = pendingTaskId,
                channel = channelName,
                recipient = pendingRecipient,
                messageSnippet = "WhatsApp Voice Call connected/initiated successfully",
                status = "SUCCESS",
                details = "WhatsApp Voice Call action triggered and VoIP call screen transition confirmed."
            )
        )
        AppFileLogger.action(applicationContext, TAG, "Successfully placed $channelName to $pendingRecipient for task #$pendingTaskId")
        showAppToast("Calling: $channelName")

        setAutomatingState(false)
        hasAttemptedSendClick = false
        whatsAppCallEngine.resetState()
        currentMode = AutomationMode.DIRECT_SEND
    }

    fun requestEndWhatsAppCall(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val ended = whatsAppCallEngine.endCurrentCall(root, windows)
        if (ended) {
            Log.i(TAG, "requestEndWhatsAppCall: Successfully triggered End Call for $pkg")
            AppFileLogger.action(applicationContext, TAG, "Terminated active WhatsApp VoIP call for $pkg")
            handler.postDelayed({
                minimizeToHome()
            }, 800L)
        }
        return ended
    }

    /**
     * Programmatically terminates an active cellular phone call via accessibility click or TelecomManager.
     */
    fun requestEndPhoneCall(): Boolean {
        val targetRoots = AccessibilityNodeUtils.getTargetRoots(this, windows)
        for (root in targetRoots) {
            val pkg = root.packageName?.toString().orEmpty()
            val isInCallUi = pkg.contains("incall", ignoreCase = true) ||
                    pkg.contains("dialer", ignoreCase = true) ||
                    pkg.contains("phone", ignoreCase = true) ||
                    pkg.contains("telecom", ignoreCase = true)
            if (isInCallUi) {
                val endBtn = findInCallEndButton(root)
                if (endBtn != null) {
                    val clicked = endBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        Log.i(TAG, "requestEndPhoneCall: Successfully clicked End Call button in dialer ($pkg)")
                        AppFileLogger.action(applicationContext, TAG, "Clicked End Call button in dialer ($pkg)")
                        handler.postDelayed({
                            minimizeToHome()
                        }, 800L)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun findInCallEndButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val resId = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()

            val isEndId = resId.endsWith("end_call_button") ||
                    resId.endsWith("disconnect_button") ||
                    resId.endsWith("incall_end_call") ||
                    resId.endsWith("endbutton") ||
                    resId.endsWith("end_call")
            val isEndDesc = desc == "end call" || desc == "hang up" || desc == "end" || desc == "disconnect"
            val isEndText = text == "end call" || text == "hang up" || text == "end"

            if ((isEndId || isEndDesc || isEndText) && (node.isClickable || node.parent?.isClickable == true)) {
                return if (node.isClickable) node else node.parent
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }
        }
        return null
    }

    /**
     * Places a direct phone call from accessibility service context following keyguard auto-unlock.
     */
    fun launchPhoneCallFromService(recipient: String) {
        try {
            val cleanPhone = PhoneNumberNormalizer.cleanPhoneNumber(recipient)
            val uri = Uri.fromParts("tel", cleanPhone, null)
            val hasCallPerm = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CALL_PHONE,
            ) == PackageManager.PERMISSION_GRANTED

            val callIntent = if (hasCallPerm) {
                Intent(Intent.ACTION_CALL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                Intent(Intent.ACTION_DIAL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            startActivity(callIntent)
            Log.i(TAG, "launchPhoneCallFromService: Dispatched phone call intent for $cleanPhone")
            AppFileLogger.action(applicationContext, TAG, "Placed phone call to $cleanPhone post-unlock")
        } catch (e: Exception) {
            Log.e(TAG, "launchPhoneCallFromService failed: ${e.message}", e)
        }
    }

    private fun handleSendTimeout(pkg: String) {
        lastSendResult = RESULT_TIMEOUT
        val channelName = when {
            pendingOperationMode == AutomationMode.WHATSAPP_VOICE_CALL && pkg.contains("w4b", ignoreCase = true) -> "WhatsApp Business Call"
            pendingOperationMode == AutomationMode.WHATSAPP_VOICE_CALL -> "WhatsApp Call"
            pkg.contains("w4b", ignoreCase = true) && pendingOperationMode == AutomationMode.WHATSAPP_STATUS -> "WhatsApp Business Status"
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
        if (!isBatchCampaignMode && pendingTaskId > 0) {
            AppRepository(applicationContext).updateTaskStatus(pendingTaskId, TaskStatus.FAILED)
        }
        setAutomatingState(false)
        hasAttemptedSendClick = false
        groupSearchStep = 0
        whatsAppStatusEngine.resetState()
        currentMode = AutomationMode.DIRECT_SEND

        val needsRelock = shouldRelockAfterSend || keyguardUnlockEngine.shouldRelockAfterSend
        if (needsRelock) {
            shouldRelockAfterSend = false
            keyguardUnlockEngine.shouldRelockAfterSend = false
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
        setAutomatingState(false)
        isUnlockPending = false
        hasAttemptedSendClick = false
        handler.removeCallbacks(sendRunnable)
    }
}
