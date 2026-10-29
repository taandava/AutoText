/**
 * Package: com.jmd.autotext.whatsapp
 * Module: WhatsApp Automation Subsystem
 *
 * WhatsApp Voice Call automation engine inspecting the accessibility hierarchy
 * to locate and click the voice call action in personal and business WhatsApp conversations,
 * confirm calling dialogs, and verify VoIP call screen transitions.
 *
 * Linked With:
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Dispatcher: [com.jmd.autotext.whatsapp.WhatsAppIntentDispatcher]
 * - Node Utils: [com.jmd.autotext.whatsapp.AccessibilityNodeUtils]
 * - Popup Shield: [com.jmd.autotext.whatsapp.PopupShield]
 * - Diagnostics: [com.jmd.autotext.core.AppFileLogger]
 */
package com.jmd.autotext.whatsapp

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.jmd.autotext.core.AppFileLogger

class WhatsAppCallEngine(
    private val service: AccessibilityService,
    private val loggerTag: String = "WACallEngine"
) {
    private var hasAttemptedCallClick = false

    /**
     * Resets internal dispatch flags for new outgoing call attempts.
     */
    fun resetState() {
        hasAttemptedCallClick = false
    }

    /**
     * Inspects active WhatsApp window, taps voice call action, confirms outgoing calling dialogs, and detects VoIP screen.
     *
     * @param root Accessibility tree root.
     * @param pkg Target WhatsApp package identifier.
     * @param pendingRecipient Recipient phone number or contact title.
     * @param sendAttemptCount Counter of elapsed retry attempts.
     * @param windows Optional list of screen windows.
     * @param onSuccess Callback triggered when call screen is confirmed established.
     * @param onTimeout Callback triggered if calling attempt times out.
     * @param onRetry Callback triggered to schedule retry loop iteration.
     */
    fun tryHandleVoiceCall(
        root: AccessibilityNodeInfo,
        pkg: String,
        pendingRecipient: String,
        sendAttemptCount: Int,
        windows: List<AccessibilityWindowInfo>? = null,
        onSuccess: () -> Unit,
        onTimeout: () -> Unit,
        onRetry: (delayMs: Long) -> Unit
    ) {
        val candidateRoots = if (!windows.isNullOrEmpty()) {
            AccessibilityNodeUtils.getTargetRoots(service, windows)
        } else {
            listOf(root)
        }

        val hasTargetOrAllowedWindow = candidateRoots.any { candRoot ->
            val p = candRoot.packageName?.toString().orEmpty()
            p.contains(pkg, ignoreCase = true) ||
            pkg.contains(p, ignoreCase = true) ||
            p.equals("android", ignoreCase = true) ||
            p.contains("permissioncontroller", ignoreCase = true) ||
            p.contains("packageinstaller", ignoreCase = true)
        }

        if (!hasTargetOrAllowedWindow) {
            val rootPkg = root.packageName?.toString().orEmpty()
            Log.d(loggerTag, "Active window package '$rootPkg' does not include target '$pkg' or system dialog, waiting...")
            if (sendAttemptCount <= 30) onRetry(300L) else onTimeout()
            return
        }

        if (PopupShield.hasActiveBlockingOverlay(service, pkg)) {
            Log.d(loggerTag, "Blocking overlay detected during call placement; clearing...")
            PopupShield.clearOverlaysAndNotificationShade(service, pkg)
            onRetry(300L)
            return
        }

        // 1. Check if WhatsApp displayed an error or invalid number dialog
        for (candRoot in candidateRoots) {
            if (WhatsAppDialogHandler.checkAndHandleErrorDialog(
                    root = candRoot,
                    pkg = pkg,
                    pendingTaskId = 0L,
                    pendingRecipient = pendingRecipient,
                    pendingMessage = "WhatsApp Call",
                    service = service,
                    onDismissed = { onTimeout() }
                )
            ) {
                return
            }
            if (checkAndHandleCallErrorDialog(candRoot, pkg, pendingRecipient, onTimeout)) {
                return
            }
        }

        // 2. If initial header call button has already been clicked, verify VoIP transition or confirmation dialog
        if (hasAttemptedCallClick) {
            // 2a. Prioritize verifying if VoIP call screen is active
            if (isInVoipCallScreen(root, windows)) {
                Log.i(loggerTag, "WhatsApp VoIP Call screen detected. Voice call successfully placed!")
                AppFileLogger.action(service, loggerTag, "WhatsApp VoIP Call connected/placed to $pendingRecipient")
                onSuccess()
                return
            }

            // 2b. Check across candidate roots for confirmation dialog/popup (e.g. "Start voice call?", "Call <Name>?", "OK")
            val callDialogButton = findCallConfirmationOrPopupAction(candidateRoots, pkg)
            if (callDialogButton != null) {
                val btnLabel = callDialogButton.text?.toString()
                    ?: callDialogButton.contentDescription?.toString()
                    ?: callDialogButton.viewIdResourceName
                    ?: "OK/Call"
                Log.i(loggerTag, "Found WhatsApp call confirmation/popup action button: '$btnLabel'. Clicking...")
                AppFileLogger.action(service, loggerTag, "Confirming WhatsApp call dialog button '$btnLabel' for $pendingRecipient")
                AccessibilityNodeUtils.clickElement(callDialogButton, service)
                onRetry(600L)
                return
            }

            // 2c. Avoid repeatedly spamming the header voice call button! Re-clicking the header while waiting
            // for the popup or VoIP transition dismisses the dialog or triggers an endless click loop.
            // Only re-attempt header click if at least 15 attempts (~5 seconds) have passed without VoIP screen or popup.
            if (sendAttemptCount % 15 == 0) {
                Log.w(loggerTag, "Neither popup nor VoIP screen appeared after 15 attempts; retrying header call button click...")
                val voiceCallNode = findVoiceCallButton(root, pkg, windows)
                if (voiceCallNode != null) {
                    AccessibilityNodeUtils.clickElement(voiceCallNode, service)
                    onRetry(700L)
                    return
                }
            }

            if (sendAttemptCount <= 35) {
                onRetry(350L)
            } else {
                Log.w(loggerTag, "Voice call did not connect within timeout (attempts=$sendAttemptCount).")
                onTimeout()
            }
            return
        }

        // 3. Initial call placement: Search for the Voice Call button in the chat header
        val voiceCallNode = findVoiceCallButton(root, pkg, windows)
        if (voiceCallNode != null) {
            Log.i(loggerTag, "Found Voice call button in chat header. Clicking voice call...")
            AppFileLogger.action(service, loggerTag, "Clicking Voice Call button in WhatsApp header for $pendingRecipient")
            AccessibilityNodeUtils.clickElement(voiceCallNode, service)
            hasAttemptedCallClick = true
            onRetry(600L)
            return
        }

        if (sendAttemptCount <= 35) {
            onRetry(350L)
        } else {
            Log.w(loggerTag, "Voice call button was not observable or clickable within timeout (attempts=$sendAttemptCount).")
            onTimeout()
        }
    }

    /**
     * Checks for specific call failure dialogs (e.g. "Couldn't place call", "On another call", "Call failed")
     * and gracefully dismisses them while marking failure.
     */
    private fun checkAndHandleCallErrorDialog(
        root: AccessibilityNodeInfo,
        pkg: String,
        pendingRecipient: String,
        onTimeout: () -> Unit
    ): Boolean {
        val errorPhrases = listOf(
            "couldn't place call",
            "can't place call",
            "call failed",
            "on another call",
            "user is busy",
            "currently on another call"
        )
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var isError = false
        var dismissButton: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty() && queue.size < 60) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()

            if (errorPhrases.any { text.contains(it) || desc.contains(it) }) {
                isError = true
            }

            if (id.endsWith("button1") || id.endsWith("ok") || id.endsWith("positive_button") ||
                text == "ok" || desc == "ok" || text == "dismiss" || desc == "dismiss") {
                if (node.isClickable || node.parent?.isClickable == true) {
                    dismissButton = if (node.isClickable) node else node.parent
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        if (isError) {
            Log.w(loggerTag, "Detected WhatsApp call failure dialog for $pendingRecipient")
            AppFileLogger.w(service, loggerTag, "WhatsApp call failure dialog detected for $pendingRecipient")
            if (dismissButton != null) {
                AccessibilityNodeUtils.clickElement(dismissButton, service)
            }
            onTimeout()
            return true
        }
        return false
    }

    private fun hasConversationComposer(root: AccessibilityNodeInfo): Boolean {
        val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            if (id.endsWith("entry") || id.endsWith("edit_layout") || id.endsWith("input_layout") || id.endsWith("voice_note_btn")) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    fun isInVoipCallScreen(
        root: AccessibilityNodeInfo,
        windows: List<AccessibilityWindowInfo>?
    ): Boolean {
        if (!hasAttemptedCallClick) return false

        val candidateRoots = if (!windows.isNullOrEmpty()) {
            AccessibilityNodeUtils.getTargetRoots(service, windows)
        } else {
            listOf(root)
        }

        for (candRoot in candidateRoots) {
            // If the chat message input field is present, we are still on the conversation screen!
            if (hasConversationComposer(candRoot)) {
                continue
            }

            val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
            q.add(candRoot)
            while (q.isNotEmpty()) {
                val node = q.removeFirst()
                val id = node.viewIdResourceName?.lowercase().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                val text = node.text?.toString().orEmpty()

                // VoIP screen specific view IDs
                if (id.endsWith("call_screen_root") || id.endsWith("call_screen") || id.endsWith("call_screen_header_view") ||
                    id.endsWith("call_grid") || id.endsWith("call_controls") || id.contains("voip") ||
                    id.endsWith("mute_btn") || id.endsWith("speaker_btn") || id.endsWith("bluetooth_btn")) {
                    return true
                }
                // End Call button
                if (id.contains("end_call") || id.endsWith("hangup_btn") || id.contains("hangup") || id.endsWith("cancel_call_back_btn")) {
                    return true
                }
                if (desc.equals("End call", ignoreCase = true) || desc.equals("End", ignoreCase = true) || desc.contains("hang up", ignoreCase = true) || desc.contains("leave call", ignoreCase = true) || desc.equals("Leave", ignoreCase = true)) {
                    return true
                }
                if (text.equals("End", ignoreCase = true) || text.startsWith("Calling", ignoreCase = true) || text.startsWith("Ringing", ignoreCase = true) || text.contains("leave", ignoreCase = true)) {
                    return true
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { q.add(it) }
                }
            }
        }
        return false
    }

    /**
     * Searches across all candidate roots (including alert dialogs, bottom sheets, and permission prompts)
     * for positive confirmation actions to initiate or allow the voice call.
     */
    private fun findCallConfirmationOrPopupAction(
        candidateRoots: List<AccessibilityNodeInfo>,
        pkg: String
    ): AccessibilityNodeInfo? {
        val actionWords = listOf(
            "call", "start call", "start voice call", "voice call", "audio call",
            "ok", "call anyway", "continue", "while using the app", "only this time",
            "allow", "yes", "confirm", "got it"
        )

        val rect = Rect()

        for (candRoot in candidateRoots) {
            val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
            q.add(candRoot)
            while (q.isNotEmpty()) {
                val node = q.removeFirst()
                val id = node.viewIdResourceName?.lowercase().orEmpty()
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()

                node.getBoundsInScreen(rect)

                // Skip top header toolbar items to avoid re-matching header icons
                val isTopHeader = rect.top in 0..350 && rect.height() in 1..250

                // 1. Android standard AlertDialog positive button or WhatsApp positive action ID
                val isPositiveId = id.endsWith("button1") ||
                        id.endsWith("ok") ||
                        id.endsWith("btn_ok") ||
                        id.endsWith("positive_button") ||
                        id.endsWith("confirm_button") ||
                        id.endsWith("call_btn") ||
                        id.endsWith("start_call") ||
                        id.endsWith("permission_allow_foreground_only_button") ||
                        id.endsWith("permission_allow_button")

                if (isPositiveId) {
                    val isActionWord = actionWords.any { text.equals(it, ignoreCase = true) || desc.equals(it, ignoreCase = true) }
                    if (isActionWord || text.isNotBlank() || node.isClickable) {
                        return if (node.isClickable) node else node.parent ?: node
                    }
                }

                // 2. Action text/desc match (e.g. "Call", "Voice call", "OK", "Continue", "While using the app")
                val isLayoutContainer = id.contains("main_layout") || id.contains("conversation") || id.contains("root") || rect.height() > 300 || rect.width() > 1000
                if (!isTopHeader && !isLayoutContainer && (node.isClickable || node.parent?.isClickable == true || node.className?.contains("Button") == true)) {
                    val matchesAction = actionWords.any { text.equals(it, ignoreCase = true) || desc.equals(it, ignoreCase = true) }
                    if (matchesAction) {
                        // Make sure it doesn't say "Video call" or "Cancel"
                        if (!desc.contains("video", ignoreCase = true) && !text.contains("video", ignoreCase = true) &&
                            !text.equals("cancel", ignoreCase = true)) {
                            val candidate = if (node.isClickable) node else (node.parent?.takeIf { it.isClickable && !it.viewIdResourceName.orEmpty().contains("main_layout") } ?: node)
                            return candidate
                        }
                    }
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { q.add(it) }
                }
            }
        }
        return null
    }

    private fun findVoiceCallButton(
        root: AccessibilityNodeInfo,
        pkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val candidateRoots = if (!windows.isNullOrEmpty()) {
            AccessibilityNodeUtils.getTargetRoots(service, windows)
        } else {
            listOf(root)
        }

        for (candRoot in candidateRoots) {
            val found = searchVoiceCallInSubtree(candRoot, pkg)
            if (found != null) return found
        }
        return null
    }

    private fun searchVoiceCallInSubtree(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var fallbackTopButton: AccessibilityNodeInfo? = null
        val rect = Rect()
        val screenHeight = service.resources.displayMetrics.heightPixels.coerceAtLeast(800)
        val maxTopBarBottom = (screenHeight * 0.22f).toInt().coerceAtLeast(500)
        val minLeft = (service.resources.displayMetrics.widthPixels * 0.15f).toInt().coerceAtLeast(150)

        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val desc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()

            node.getBoundsInScreen(rect)
            val isTopBar = rect.top in 0..400 && rect.bottom <= maxTopBarBottom && rect.width() > 0 && rect.height() > 0

            // Skip message bubbles or chat list entries
            if (id.contains("bubble") || id.contains("message") || id.contains("call_log")) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { q.add(it) }
                }
                continue
            }

            // 1. Top toolbar item with content-description "Voice call" / "Audio call" / "Call"
            if (isTopBar && rect.left >= minLeft) {
                if (desc.equals("Voice call", ignoreCase = true) ||
                    desc.equals("Audio call", ignoreCase = true) ||
                    desc.equals("Start voice call", ignoreCase = true) ||
                    desc.equals("Start call", ignoreCase = true) ||
                    (desc.equals("Call", ignoreCase = true) && !desc.contains("video", ignoreCase = true))
                ) {
                    return node
                }

                // 2. Resource ID matching voice call in top toolbar
                if (id.endsWith("voice_call") || id.endsWith("voice_call_btn") || id.endsWith("menuitem_call") || id.endsWith("action_call") || id.endsWith("call_btn") || (pkg.isNotBlank() && id == "$pkg:id/action_call")) {
                    return node
                }

                // 3. Text matching "Voice call" / "Audio call"
                if (text.equals("Voice call", ignoreCase = true) || text.equals("Audio call", ignoreCase = true) || (text.equals("Call", ignoreCase = true) && !desc.contains("video", ignoreCase = true))) {
                    return node
                }

                // 4. Fallback: Any button in top right that mentions call but not video
                if (desc.contains("call", ignoreCase = true) && !desc.contains("video", ignoreCase = true)) {
                    fallbackTopButton = node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return fallbackTopButton
    }

    /**
     * Traverses display windows to find and tap the WhatsApp End Call button to terminate active voice call.
     *
     * @param root Accessibility tree root.
     * @param windows Optional list of screen windows.
     * @return True if End Call button was found and clicked.
     */
    fun endCurrentCall(
        root: AccessibilityNodeInfo,
        windows: List<AccessibilityWindowInfo>? = null
    ): Boolean {
        val candidateRoots = if (!windows.isNullOrEmpty()) {
            AccessibilityNodeUtils.getTargetRoots(service, windows)
        } else {
            listOf(root)
        }

        for (candRoot in candidateRoots) {
            val endButton = findEndCallButton(candRoot)
            if (endButton != null) {
                Log.i(loggerTag, "Found WhatsApp VoIP End Call button. Clicking to hang up...")
                AppFileLogger.action(service, loggerTag, "Terminating active WhatsApp VoIP call via End Call button")
                return AccessibilityNodeUtils.clickElement(endButton, service)
            }
        }
        return false
    }

    private fun findEndCallButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var fallbackEndButton: AccessibilityNodeInfo? = null
        val rect = Rect()

        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val desc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()

            node.getBoundsInScreen(rect)

            if (id.contains("end_call") || id.endsWith("hangup_btn") || id.contains("hangup") || id.contains("call_end")) {
                return node
            }
            if (desc.equals("End call", ignoreCase = true) || desc.equals("End", ignoreCase = true) || desc.contains("hang up", ignoreCase = true) || desc.equals("Disconnect call", ignoreCase = true) || desc.contains("leave call", ignoreCase = true) || desc.equals("Leave", ignoreCase = true)) {
                return node
            }
            if (text.equals("End call", ignoreCase = true) || text.equals("End", ignoreCase = true) || text.equals("Hang up", ignoreCase = true) || text.equals("Leave call", ignoreCase = true) || text.equals("Leave", ignoreCase = true)) {
                return node
            }
            if (id.endsWith("cancel_call_back_btn") || id.endsWith("cancel_call_back")) {
                return node
            }

            if (rect.top >= 600 && rect.left >= 350 && (node.isClickable || node.className?.contains("Button") == true)) {
                if (desc.contains("end", ignoreCase = true) || text.contains("end", ignoreCase = true) || desc.contains("leave", ignoreCase = true) || text.contains("leave", ignoreCase = true)) {
                    fallbackEndButton = node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return fallbackEndButton
    }
}
