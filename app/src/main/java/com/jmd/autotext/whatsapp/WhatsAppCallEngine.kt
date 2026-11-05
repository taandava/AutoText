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

        if (sendAttemptCount < 2 && PopupShield.hasActiveBlockingOverlay(service, pkg)) {
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

        // Discard stale unsent voice note draft if blocking voice call
        findDiscardVoiceNoteButton(candidateRoots)?.let { discardBtn ->
            Log.i(loggerTag, "Discarding stale voice note draft blocking voice call...")
            val dRect = Rect()
            discardBtn.getBoundsInScreen(dRect)
            AccessibilityNodeUtils.clickElement(discardBtn, service)
            if (dRect.width() > 0 && dRect.height() > 0) {
                AccessibilityNodeUtils.dispatchPhysicalTap(service, dRect.centerX().toFloat(), dRect.centerY().toFloat())
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

            // 2b. Check across candidate roots for confirmation dialog/popup (e.g. "Start voice call?", "Call <Name>?", "Voice call" dropdown)
            val callDialogButton = findCallConfirmationOrPopupAction(candidateRoots, pkg)
            if (callDialogButton != null) {
                val btnLabel = callDialogButton.text?.toString()
                    ?: callDialogButton.contentDescription?.toString()
                    ?: callDialogButton.viewIdResourceName
                    ?: "Voice Call / Confirm"
                Log.i(loggerTag, "Found WhatsApp call confirmation/popup action button: '$btnLabel'. Clicking designated element...")
                AppFileLogger.action(service, loggerTag, "Confirming designated WhatsApp call dialog/dropdown button '$btnLabel' for $pendingRecipient")

                val targetNode = if (callDialogButton.childCount > 0) {
                    (0 until callDialogButton.childCount).mapNotNull { callDialogButton.getChild(it) }
                        .firstOrNull {
                            val cTxt = it.text?.toString().orEmpty()
                            val cDesc = it.contentDescription?.toString().orEmpty()
                            (cTxt.contains("voice", ignoreCase = true) || cDesc.contains("voice", ignoreCase = true)) &&
                            !cTxt.contains("video", ignoreCase = true) && !cDesc.contains("video", ignoreCase = true)
                        } ?: callDialogButton
                } else {
                    callDialogButton
                }

                val dRect = Rect()
                targetNode.getBoundsInScreen(dRect)
                val a11yClicked = AccessibilityNodeUtils.clickElement(callDialogButton, service)

                if (!a11yClicked && dRect.width() > 0 && dRect.height() > 0) {
                    val tapX = dRect.centerX().toFloat()
                    val tapY = dRect.centerY().toFloat()
                    AccessibilityNodeUtils.dispatchPhysicalTap(service, tapX, tapY)
                    Log.d(loggerTag, "Dispatched physical gesture tap on designated call button at ($tapX, $tapY)")
                }
                onRetry(600L)
                return
            }

            // 2c. Avoid indefinitely waiting if the header click was dropped by WhatsApp.
            // Re-attempt header click every 4 attempts (~1.4s) if neither popup nor VoIP screen appears.
            if (sendAttemptCount % 4 == 0) {
                Log.w(loggerTag, "Neither popup nor VoIP screen appeared after $sendAttemptCount attempts; retrying header call button click...")
                val voiceCallNode = findVoiceCallButton(root, pkg, windows)
                if (voiceCallNode != null) {
                    val vRect = Rect()
                    voiceCallNode.getBoundsInScreen(vRect)
                    val a11yClicked = AccessibilityNodeUtils.clickElement(voiceCallNode, service)
                    if (!a11yClicked && vRect.width() > 0 && vRect.height() > 0) {
                        val tapX = vRect.centerX().toFloat()
                        val tapY = vRect.centerY().toFloat()
                        AccessibilityNodeUtils.dispatchPhysicalTap(service, tapX, tapY)
                    }
                    onRetry(500L)
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
            val vRect = Rect()
            voiceCallNode.getBoundsInScreen(vRect)
            AccessibilityNodeUtils.clickElement(voiceCallNode, service)
            if (vRect.width() > 0 && vRect.height() > 0) {
                val tapX = vRect.centerX().toFloat()
                val tapY = vRect.centerY().toFloat()
                AccessibilityNodeUtils.dispatchPhysicalTap(service, tapX, tapY)
                Log.d(loggerTag, "Dispatched physical gesture tap on voice call button at ($tapX, $tapY)")
            }
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
     * Determines whether the active VoIP call is confirmed connected (answered) rather than still dialing or ringing.
     *
     * @param root Active window root node.
     * @param windows List of window infos from accessibility service.
     * @return True if VoIP call has connected and ongoing conversation timer is active.
     */
    fun isVoipCallConnected(
        root: AccessibilityNodeInfo,
        windows: List<AccessibilityWindowInfo>?
    ): Boolean {
        if (!isInVoipCallScreen(root, windows)) return false

        val candidateRoots = if (!windows.isNullOrEmpty()) {
            AccessibilityNodeUtils.getTargetRoots(service, windows)
        } else {
            listOf(root)
        }

        var isDialingOrRinging = false
        var hasActiveCallIndicator = false
        val durationRegex = Regex("^(\\d{1,2}:)?\\d{1,2}:\\d{2}$")

        for (candRoot in candidateRoots) {
            val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
            q.add(candRoot)
            while (q.isNotEmpty()) {
                val node = q.removeFirst()
                val id = node.viewIdResourceName?.lowercase().orEmpty()
                val text = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()

                if (text.startsWith("Calling", ignoreCase = true) ||
                    text.startsWith("Ringing", ignoreCase = true) ||
                    text.startsWith("Connecting", ignoreCase = true) ||
                    desc.startsWith("Calling", ignoreCase = true) ||
                    desc.startsWith("Ringing", ignoreCase = true)) {
                    isDialingOrRinging = true
                }

                if (durationRegex.matches(text.trim()) ||
                    id.endsWith("call_time") ||
                    id.endsWith("elapsed_time") ||
                    id.endsWith("call_duration") ||
                    id.endsWith("call_timer")) {
                    hasActiveCallIndicator = true
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { q.add(it) }
                }
            }
        }

        return hasActiveCallIndicator || (!isDialingOrRinging && isInVoipCallScreen(root, windows))
    }

    /**
     * Searches across all candidate roots (including alert dialogs, bottom sheets, popup menus, and permission prompts)
     * for positive confirmation actions to initiate or allow the voice call.
     */
    private fun findCallConfirmationOrPopupAction(
        candidateRoots: List<AccessibilityNodeInfo>,
        pkg: String
    ): AccessibilityNodeInfo? {
        val voiceSpecificTerms = listOf(
            "voice call", "audio call", "start voice call",
            "वॉयस कॉल", "व्हॉईस कॉल", "ভয়েস কল", "குரல் அழைப்பு", "వాయిస్ కాల్", "ಧ್ವನಿ ಕರೆ"
        )

        val generalActionWords = listOf(
            "call", "start call", "ok", "call anyway", "continue",
            "while using the app", "only this time", "allow", "yes", "confirm", "got it",
            "कॉल", "कॉल करें", "कॉल करा", "কল", "அழை"
        )

        val rect = Rect()

        val screenHeight = service.resources.displayMetrics.heightPixels.coerceAtLeast(800)
        val maxTopBarBottom = (screenHeight * 0.22f).toInt().coerceAtLeast(500)

        // Pass 1: Prioritize explicit "Voice call" dropdown / popup / bottom-sheet items
        for (candRoot in candidateRoots) {
            val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
            q.add(candRoot)
            while (q.isNotEmpty()) {
                val node = q.removeFirst()
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val id = node.viewIdResourceName?.lowercase().orEmpty()

                node.getBoundsInScreen(rect)

                val hasExplicitVoiceText = voiceSpecificTerms.any { text.contains(it, ignoreCase = true) }
                val hasExplicitVoiceDesc = voiceSpecificTerms.any { desc.contains(it, ignoreCase = true) }
                val isVideo = text.contains("video", ignoreCase = true) || desc.contains("video", ignoreCase = true)
                val isToolbarAction = id.contains("action_bar") || id.contains("toolbar") || id.contains("menuitem") ||
                        id.contains("action_call") || id.contains("voice_call_btn") ||
                        (text.isBlank() && rect.bottom <= maxTopBarBottom && !id.contains("title") && !id.contains("popup") && !id.contains("menu") && !id.contains("item"))

                if ((hasExplicitVoiceText || (hasExplicitVoiceDesc && !isToolbarAction)) && !isVideo && !isToolbarAction) {
                    var clickableTarget = node
                    var curr: AccessibilityNodeInfo? = node
                    while (curr != null) {
                        if (curr.isClickable) {
                            clickableTarget = curr
                            break
                        }
                        curr = curr.parent
                    }
                    Log.i(loggerTag, "findCallConfirmationOrPopupAction: Matched dedicated voice call popup item: '$text' / '$desc' at bounds=$rect")
                    return clickableTarget
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { q.add(it) }
                }
            }
        }

        // Pass 2: Check standard dialog positive buttons and generic confirm actions
        for (candRoot in candidateRoots) {
            val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
            q.add(candRoot)
            while (q.isNotEmpty()) {
                val node = q.removeFirst()
                val id = node.viewIdResourceName?.lowercase().orEmpty()
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()

                node.getBoundsInScreen(rect)
                val isTopHeader = id.contains("action_bar") || id.contains("toolbar") || id.contains("menuitem") ||
                        id.contains("action_call") || id.contains("voice_call_btn") ||
                        (rect.bottom <= maxTopBarBottom && rect.height() in 1..250 && !id.contains("button1") && !id.contains("dialog") && !id.contains("popup") && !id.contains("menu"))

                val isCancelWord = text.contains("cancel", ignoreCase = true) || desc.contains("cancel", ignoreCase = true) ||
                    text.contains("रद्द", ignoreCase = true) || desc.contains("रद्द", ignoreCase = true) ||
                    text.contains("বাতিল", ignoreCase = true)

                if (isCancelWord) {
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { q.add(it) }
                    }
                    continue
                }

                val isPositiveId = id.endsWith("button1") ||
                        id.endsWith("ok") ||
                        id.endsWith("btn_ok") ||
                        id.endsWith("positive_button") ||
                        id.endsWith("confirm_button") ||
                        id.endsWith("call_btn") ||
                        id.endsWith("start_call") ||
                        id.endsWith("audio_call_item") ||
                        id.endsWith("voice_call_item") ||
                        id.endsWith("permission_allow_foreground_only_button") ||
                        id.endsWith("permission_allow_button")

                if (isPositiveId) {
                    val isActionWord = generalActionWords.any { text.equals(it, ignoreCase = true) || desc.equals(it, ignoreCase = true) }
                    if (isActionWord || text.isNotBlank() || node.isClickable || desc.isNotBlank()) {
                        return if (node.isClickable) node else node.parent ?: node
                    }
                }

                val isLayoutContainer = id.contains("main_layout") || id.contains("conversation") || id.contains("root") || rect.height() > 350
                if (!isTopHeader && !isLayoutContainer && (node.isClickable || node.parent?.isClickable == true || node.className?.contains("Button") == true)) {
                    val matchesAction = generalActionWords.any { text.equals(it, ignoreCase = true) || desc.equals(it, ignoreCase = true) }
                    if (matchesAction && !desc.contains("video", ignoreCase = true) && !text.contains("video", ignoreCase = true)) {
                        val candidate = if (node.isClickable) node else (node.parent?.takeIf { it.isClickable && !it.viewIdResourceName.orEmpty().contains("main_layout") } ?: node)
                        return candidate
                    }
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { q.add(it) }
                }
            }
        }
        return null
    }

    private fun findDiscardVoiceNoteButton(candidateRoots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (candRoot in candidateRoots) {
            val q = java.util.ArrayDeque<AccessibilityNodeInfo>()
            q.add(candRoot)
            while (q.isNotEmpty()) {
                val node = q.removeFirst()
                val id = node.viewIdResourceName?.lowercase().orEmpty()
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                if (id.contains("voice_note_cancel") || desc.contains("discard voice message") || desc.contains("cancel voice message")) {
                    return if (node.isClickable) node else node.parent ?: node
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
                    desc.equals("वॉयस कॉल", ignoreCase = true) ||
                    desc.equals("व्हॉईस कॉल", ignoreCase = true) ||
                    desc.equals("ভয়েস কল", ignoreCase = true) ||
                    desc.equals("குரல் அழைப்பு", ignoreCase = true) ||
                    (desc.equals("Call", ignoreCase = true) && !desc.contains("video", ignoreCase = true)) ||
                    desc.equals("कॉल करें", ignoreCase = true) ||
                    desc.equals("कॉल करा", ignoreCase = true)
                ) {
                    return node
                }

                // 2. Resource ID matching voice call in top toolbar
                if (id.endsWith("voice_call") || id.endsWith("voice_call_btn") || id.endsWith("menuitem_call") || id.endsWith("action_call") || id.endsWith("call_btn") || (pkg.isNotBlank() && id == "$pkg:id/action_call")) {
                    return node
                }

                // 3. Text matching "Voice call" / "Audio call"
                if (text.equals("Voice call", ignoreCase = true) || text.equals("Audio call", ignoreCase = true) ||
                    text.equals("वॉयस कॉल", ignoreCase = true) || text.equals("व्हॉईस कॉल", ignoreCase = true) ||
                    (text.equals("Call", ignoreCase = true) && !desc.contains("video", ignoreCase = true)) ||
                    text.equals("कॉल", ignoreCase = true)
                ) {
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
        root: AccessibilityNodeInfo? = null,
        windows: List<AccessibilityWindowInfo>? = null
    ): Boolean {
        val candidateRoots = if (!windows.isNullOrEmpty()) {
            AccessibilityNodeUtils.getTargetRoots(service, windows)
        } else if (root != null) {
            listOf(root)
        } else {
            val activeRoot = service.rootInActiveWindow
            if (activeRoot != null) listOf(activeRoot) else emptyList()
        }

        for (candRoot in candidateRoots) {
            val endButton = findEndCallButton(candRoot)
            if (endButton != null) {
                Log.i(loggerTag, "Found WhatsApp VoIP End Call button. Clicking to hang up...")
                AppFileLogger.action(service, loggerTag, "Terminating active WhatsApp VoIP call via End Call button")
                val bRect = Rect()
                endButton.getBoundsInScreen(bRect)
                val res = AccessibilityNodeUtils.clickElement(endButton, service)
                if (bRect.width() > 0 && bRect.height() > 0) {
                    val tapX = bRect.centerX().toFloat()
                    val tapY = bRect.centerY().toFloat()
                    AccessibilityNodeUtils.dispatchPhysicalTap(service, tapX, tapY)
                }
                return res
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
