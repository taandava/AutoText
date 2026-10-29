

/**
 * Package: com.jmd.autotext.whatsapp
 * Module: WhatsApp Automation Subsystem
 *
 * Direct message dispatcher executing accessibility tree inspection, input field text injection,
 * clipboard synchronization, and send button clicks across WhatsApp and WhatsApp Business conversations.
 *
 * Linked With:
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Node Utils: [com.jmd.autotext.whatsapp.AccessibilityNodeUtils]
 * - Voice Note Guard: [com.jmd.autotext.whatsapp.VoiceNoteProtectionGuard]
 * - Popup Shield: [com.jmd.autotext.whatsapp.PopupShield]
 * - Diagnostics: [com.jmd.autotext.core.AppFileLogger]
 */
package com.jmd.autotext.whatsapp

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.*
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.jmd.autotext.core.AppFileLogger

/**
 * Direct chat interaction engine identifying and clicking the send button in WhatsApp chat screens.
 */
class WhatsAppDirectSendEngine(
    private val service: AccessibilityService,
    private val loggerTag: String = "WADirectSendEngine"
) {

    /**
     * Traverses accessibility hierarchy, validates input text injection, protects voice notes, and clicks Send button.
     *
     * @param root Active accessibility node root.
     * @param pkg Target WhatsApp package identifier.
     * @param pendingMessage Expected message body to dispatch.
     * @param sendAttemptCount Number of loop retries attempted.
     * @param hasAttemptedSendClick True if send button was already tapped.
     * @param windows Optional list of accessibility windows on display.
     * @param onSuccess Callback invoked when message is successfully sent.
     * @param onTimeout Callback invoked if send attempts exceed threshold.
     * @param onRetry Callback invoked to schedule subsequent polling attempt.
     * @param onAttemptClicked Callback invoked immediately when send button is clicked.
     */
    fun tryClickSendButton(
        root: AccessibilityNodeInfo,
        pkg: String,
        pendingMessage: String,
        sendAttemptCount: Int,
        hasAttemptedSendClick: Boolean,
        windows: List<AccessibilityWindowInfo>? = null,
        pendingRecipient: String = "",
        onSuccess: () -> Unit,
        onTimeout: () -> Unit,
        onRetry: (delayMs: Long) -> Unit,
        onAttemptClicked: () -> Unit
    ) {
        try {
            val curPkg = root.packageName?.toString() ?: ""
            if (!curPkg.contains(pkg, ignoreCase = true) && !pkg.contains(curPkg, ignoreCase = true)) {
                Log.d(loggerTag, "Active window package '$curPkg' is not target '$pkg', skipping send check")
                return
            }

            if (PopupShield.hasActiveBlockingOverlay(service, pkg)) {
                Log.d(loggerTag, "Blocking overlay detected during direct send; clearing...")
                PopupShield.clearOverlaysAndNotificationShade(service, pkg)
                onRetry(300L)
                return
            }

            VoiceNoteProtectionGuard.handleAndCancelActiveVoiceRecording(root, pkg, service)

            if (WhatsAppDialogHandler.checkAndHandleErrorDialog(
                    root = root,
                    pkg = pkg,
                    pendingTaskId = 0L,
                    pendingRecipient = pendingRecipient,
                    pendingMessage = pendingMessage,
                    service = service,
                    onDismissed = { onTimeout() }
                )) {
                return
            }

            val inputNode = findMessageInputField(root, pkg, windows)

            if (!hasAttemptedSendClick && inputNode == null) {
                if (pendingRecipient.isNotBlank() && tryResolveContactPicker(root, pkg, pendingRecipient)) {
                    Log.i(loggerTag, "Dispatched contact selection in picker for '$pendingRecipient', awaiting chat transition...")
                    onRetry(650L)
                    return
                }
                if (sendAttemptCount <= 25) onRetry(350L) else {
                    Log.w(loggerTag, "Message composer is not observable; refusing to click any Send-like control without a visible composer.")
                    onTimeout()
                }
                return
            }

            if (hasAttemptedSendClick) {
                if (inputNode == null) {
                    Log.d(loggerTag, "WhatsApp composer is no longer visible after send click; treating UI transition as send confirmation.")
                    AppFileLogger.action(service, loggerTag, "WhatsApp composer closed/transitioned after send click; UI submission confirmed.")
                    onSuccess()
                    return
                }
                if (isInputEmptyOrHint(inputNode)) {
                    Log.d(loggerTag, "WhatsApp composer cleared after send click; treating UI transition as send confirmation.")
                    AppFileLogger.action(service, loggerTag, "WhatsApp composer is empty after the send click; UI submission confirmed.")
                    onSuccess()
                } else if (!inputContainsPendingMessage(inputNode, pendingMessage) && !isInputEmptyOrHint(inputNode)) {
                    Log.d(loggerTag, "WhatsApp composer draft cleared or modified; UI submission confirmed.")
                    AppFileLogger.action(service, loggerTag, "WhatsApp composer draft cleared; UI submission confirmed.")
                    onSuccess()
                } else if (sendAttemptCount <= 25) {
                    if (sendAttemptCount % 2 == 0) {
                        dispatchSendCoordinateFallback(inputNode, service, loggerTag)
                    }
                    onRetry(500L)
                } else {
                    Log.w(loggerTag, "Could not verify WhatsApp composer changed after send click within attempt limit.")
                    onTimeout()
                }
                return
            }

            val currentInput = inputNode ?: run {
                if (sendAttemptCount <= 25) onRetry(350L) else {
                    Log.w(loggerTag, "Message composer is not observable; refusing to click any Send-like control without a visible composer.")
                    onTimeout()
                }
                return
            }

            val inputMatchesPending = inputContainsPendingMessage(currentInput, pendingMessage)
            val isInputBlankOrHint = isInputEmptyOrHint(currentInput)

            if (pendingMessage.isNotBlank() && (isInputBlankOrHint || !inputMatchesPending)) {
                if (sendAttemptCount > 15) {
                    Log.w(loggerTag, "Unable to prepare matching pending message in WhatsApp input; refusing to send unknown draft.")
                    onTimeout()
                    return
                }
                val injected = AccessibilityNodeUtils.setTextOnInput(currentInput, pendingMessage, service)
                Log.d(loggerTag, "Prepared pending message in input (success=$injected, empty=$isInputBlankOrHint, matches=$inputMatchesPending)")
                onRetry(if (injected) 450L else 350L)
                return
            }

            val sendNode = findSendButton(root, pkg, windows, currentInput)

            if (sendNode != null) {
                if (VoiceNoteProtectionGuard.isVoiceNode(sendNode)) {
                    Log.w(loggerTag, "sendNode is flagged as voice node! Waiting for send button...")
                    if (!inputMatchesPending) {
                        AccessibilityNodeUtils.setTextOnInput(currentInput, pendingMessage, service)
                    }
                    onRetry(400L)
                    return
                }

                val rect = Rect()
                sendNode.getBoundsInScreen(rect)
                val hasBounds = rect.width() > 0 && rect.height() > 0

                val a11yClicked = sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(loggerTag, "Tier 1 Send button A11y ACTION_CLICK result: $a11yClicked (bounds=$rect)")

                if (!a11yClicked) {
                    Log.d(loggerTag, "Tier 1 A11y ACTION_CLICK returned false; escalating to Tier 2 & 3...")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        currentInput.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    }
                    if (hasBounds) {
                        val tapX = rect.centerX().toFloat()
                        val tapY = rect.centerY().toFloat()
                        AccessibilityNodeUtils.dispatchPhysicalTap(service, tapX, tapY)
                        Log.d(loggerTag, "Dispatched physical gesture tap fallback on send bounds at ($tapX, $tapY)")
                    } else {
                        dispatchSendCoordinateFallback(currentInput, service, loggerTag)
                    }
                }

                onAttemptClicked()
                AppFileLogger.action(service, loggerTag, "Executed send action (Tier 1 a11yClick=$a11yClicked, bounds=$rect); waiting for composer verification.")
                onRetry(600L)
            } else {
                Log.w(loggerTag, "Send node not resolved via standard accessibility tree (attempt $sendAttemptCount).")
                if (sendAttemptCount >= 2) {
                    val imeActionResult = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) &&
                        currentInput.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    Log.d(loggerTag, "Tier 2 IME_ACTION dispatch result: $imeActionResult")

                    dispatchSendCoordinateFallback(currentInput, service, loggerTag)

                    onAttemptClicked()
                    AppFileLogger.action(service, loggerTag, "Tier 3: Dispatched coordinate tap fallback against Meta A/B layout; waiting for composer verification.")
                    onRetry(650L)
                } else {
                    onRetry(350L)
                }
            }
        } catch (t: Throwable) {
            Log.e(loggerTag, "Error in tryClickSendButton", t)
            onTimeout()
        }
    }

    private fun dispatchSendCoordinateFallback(
        inputNode: AccessibilityNodeInfo?,
        service: AccessibilityService,
        loggerTag: String
    ) {
        val metrics = service.resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat()
        val screenHeight = metrics.heightPixels.toFloat()

        var tapX = screenWidth * 0.925f
        var tapY = screenHeight * 0.942f

        if (inputNode != null) {
            val inputRect = Rect()
            inputNode.getBoundsInScreen(inputRect)
            if (inputRect.width() > 0 && inputRect.height() > 0 && inputRect.bottom > screenHeight * 0.35f) {
                val rightSpace = screenWidth - inputRect.right
                tapX = if (rightSpace > 30) {
                    inputRect.right + (rightSpace * 0.5f)
                } else {
                    screenWidth * 0.925f
                }
                tapY = inputRect.centerY().toFloat()
                Log.d(loggerTag, "Calculated input-relative send coordinates: ($tapX, $tapY) from inputRect=$inputRect")
            }
        }

        AccessibilityNodeUtils.dispatchPhysicalTap(service, tapX, tapY)
        Log.i(loggerTag, "Tier 3 physical coordinate tap dispatched at ($tapX, $tapY) [screen: ${metrics.widthPixels}x${metrics.heightPixels}]")
    }

    /**
     * Identifies the primary message composer EditText node within the active WhatsApp chat window.
     *
     * @param root Accessibility tree root.
     * @param pkg Target WhatsApp package identifier.
     * @param windows Optional list of screen windows.
     * @return Resolved input node, or null if not found.
     */
    fun findMessageInputField(
        root: AccessibilityNodeInfo,
        pkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val ids = listOf(
            "entry", "caption", "text_entry", "input", "message_input",
            "conversation_text_entry", "compose_message_text", "input_text"
        )
        for (id in ids) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, pkg, id, windows)
            nodes.firstOrNull { it.isVisibleToUser }?.let { return it }
        }

        // Exclude search inputs and status/updates screens from being treated as chat composer
        fun isSearchOrStatusTab(node: AccessibilityNodeInfo): Boolean {
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()
            if (id.contains("search") || id.contains("query") || id.contains("find")) return true
            if (desc.contains("search") || desc.contains("query") || desc.contains("find")) return true
            if (text == "search" || text == "search…" || text == "search...") return true
            return false
        }

        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var depth = 0
        while (q.isNotEmpty() && depth < 120) {
            val node = q.removeFirst()
            depth++
            if (node.isVisibleToUser) {
                val cls = node.className?.toString().orEmpty()
                val isEdit = cls.contains("EditText", ignoreCase = true) ||
                             (node.isEditable && !node.isPassword)
                if (isEdit && !isSearchOrStatusTab(node)) {
                    return node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        val hintMatches = AccessibilityNodeUtils.findNodeByTextOrDesc(
            root,
            listOf("type a message", "message", "संदेश", "mensaje", "mensagem"),
            windows = windows,
            exact = false
        )
        if (hintMatches != null && hintMatches.isVisibleToUser && (hintMatches.isClickable || hintMatches.isFocusable || hintMatches.isEditable) && !isSearchOrStatusTab(hintMatches)) {
            return hintMatches
        }

        return null
    }

    /**
     * Resolves the WhatsApp Send button node while strictly guarding against microphone voice note nodes.
     *
     * @param root Accessibility tree root.
     * @param pkg Target WhatsApp package identifier.
     * @param windows Optional list of screen windows.
     * @param inputNode Optional already-resolved message input field node for relative position matching.
     * @return Resolved send button node, or null if not yet present.
     */
    fun findSendButton(
        root: AccessibilityNodeInfo,
        pkg: String,
        windows: List<AccessibilityWindowInfo>?,
        inputNode: AccessibilityNodeInfo? = null
    ): AccessibilityNodeInfo? {
        val ids = listOf(
            "send", "send_old", "send_btn", "fab", "entry_send", "action_send",
            "send_button", "send_container", "action_submit", "conversation_send_button", "submit"
        )
        for (id in ids) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, pkg, id, windows)
            for (n in nodes) {
                if (n.isVisibleToUser && !VoiceNoteProtectionGuard.isVoiceNode(n)) {
                    if (n.childCount > 0 && n.className?.toString()?.contains("ImageButton") != true) {
                        for (c in 0 until n.childCount) {
                            val child = n.getChild(c)
                            if (child != null && child.isVisibleToUser && !VoiceNoteProtectionGuard.isVoiceNode(child)) {
                                return child
                            }
                        }
                    }
                    return n
                }
            }
        }

        val textNode: AccessibilityNodeInfo? = AccessibilityNodeUtils.findNodeByTextOrDesc(
            root,
            VoiceNoteProtectionGuard.SEND_KEYWORDS,
            windows = windows,
            exact = false
        )
        if (textNode != null && textNode.isVisibleToUser && !VoiceNoteProtectionGuard.isVoiceNode(textNode)) {
            return textNode
        }

        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var count = 0
        while (q.isNotEmpty() && count < 100) {
            val n = q.removeFirst()
            count++
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            val cls = n.className?.toString().orEmpty()
            if (n.isVisibleToUser && !VoiceNoteProtectionGuard.isVoiceNode(n)) {
                val matchesDesc = VoiceNoteProtectionGuard.isSendNode(n)
                val matchesId = id.endsWith(":id/send") || id.endsWith(":id/send_btn") || id.contains("send")
                if ((matchesDesc || matchesId) &&
                    (n.isClickable || cls.contains("Image", ignoreCase = true) || cls.contains("Button", ignoreCase = true))) {
                    return n
                }
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }

        if (inputNode != null) {
            try {
                val parent = inputNode.parent
                if (parent != null) {
                    val inputBounds = Rect()
                    inputNode.getBoundsInScreen(inputBounds)
                    for (i in 0 until parent.childCount) {
                        val sibling = parent.getChild(i) ?: continue
                        if (!sibling.isVisibleToUser || VoiceNoteProtectionGuard.isVoiceNode(sibling)) continue
                        val siblingBounds = Rect()
                        sibling.getBoundsInScreen(siblingBounds)
                        if (siblingBounds.left >= inputBounds.right - 10 && siblingBounds.width() > 0 &&
                            (sibling.isClickable || sibling.className?.toString()?.contains("Image") == true || sibling.className?.toString()?.contains("Button") == true)) {
                            Log.d(loggerTag, "Found send button via adjacent rightward sibling to input field: bounds=$siblingBounds")
                            return sibling
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(loggerTag, "Sibling inspection for send button failed: ${e.message}")
            }
        }

        return null
    }

    private fun isInputEmptyOrHint(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.trim().orEmpty()
        val hint = node.hintText?.toString()?.trim().orEmpty()
        if (text.isBlank() || (hint.isNotBlank() && text.equals(hint, ignoreCase = true))) return true
        val placeholders = setOf(
            "type a message", "message", "संदेश", "संदेश टाइप करें", "संदेश लिखें",
            "mensaje", "mensagem", "message…", "type a message…"
        )
        return placeholders.any { text.equals(it, ignoreCase = true) }
    }

    private fun inputContainsPendingMessage(node: AccessibilityNodeInfo, pendingMessage: String): Boolean {
        val visible = node.text?.toString().orEmpty().replace("\r\n", "\n").trim()
        val expected = pendingMessage.replace("\r\n", "\n").trim()
        if (expected.isBlank()) return visible.isNotBlank()
        if (visible == expected) return true
        if (visible.contains(expected) || expected.contains(visible)) return true
        if (expected.length >= 10 && (visible.contains(expected.take(10)) || visible.startsWith(expected.take(10)))) return true
        if (visible.length >= 10 && expected.contains(visible.take(10))) return true
        return false
    }

    private fun tryResolveContactPicker(
        root: AccessibilityNodeInfo,
        pkg: String,
        pendingRecipient: String
    ): Boolean {
        try {
            val cleanRecipient = pendingRecipient.replace(Regex("[^0-9a-zA-Z]"), "").lowercase()
            if (cleanRecipient.isBlank()) return false

            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var searchButtonNode: AccessibilityNodeInfo? = null
            var searchInputNode: AccessibilityNodeInfo? = null
            val matchingCandidates = mutableListOf<AccessibilityNodeInfo>()

            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val id = node.viewIdResourceName?.lowercase().orEmpty()
                val cls = node.className?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                val text = node.text?.toString().orEmpty()

                if (node.isVisibleToUser) {
                    if (id.contains("search") || desc.contains("search", true)) {
                        if (node.isEditable || cls.contains("EditText", true)) {
                            searchInputNode = node
                        } else if (node.isClickable) {
                            searchButtonNode = node
                        }
                    }

                    val cleanText = text.replace(Regex("[^0-9a-zA-Z]"), "").lowercase()
                    val cleanDesc = desc.replace(Regex("[^0-9a-zA-Z]"), "").lowercase()

                    val matchesText = cleanText.isNotBlank() && (cleanText == cleanRecipient ||
                            cleanText.contains(cleanRecipient) ||
                            (cleanRecipient.length >= 7 && cleanText.endsWith(cleanRecipient.takeLast(7))))
                    val matchesDesc = cleanDesc.isNotBlank() && (cleanDesc == cleanRecipient ||
                            cleanDesc.contains(cleanRecipient) ||
                            (cleanRecipient.length >= 7 && cleanDesc.endsWith(cleanRecipient.takeLast(7))))

                    if ((matchesText || matchesDesc) && !node.isEditable && !cls.contains("EditText", true)) {
                        matchingCandidates.add(node)
                    }
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }

            for (candidate in matchingCandidates) {
                var target: AccessibilityNodeInfo? = candidate
                var depth = 0
                while (target != null && depth < 6) {
                    if (target.isClickable) {
                        Log.i(loggerTag, "Clicking matching contact picker item for '$pendingRecipient'")
                        return AccessibilityNodeUtils.clickElement(target, service)
                    }
                    target = target.parent
                    depth++
                }
                return AccessibilityNodeUtils.clickElement(candidate, service)
            }

            if (searchInputNode != null) {
                val currentSearch = searchInputNode.text?.toString().orEmpty()
                if (!currentSearch.contains(cleanRecipient) && !cleanRecipient.contains(currentSearch)) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, pendingRecipient)
                    }
                    val typed = searchInputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    Log.i(loggerTag, "Injected '$pendingRecipient' into ContactPicker search field (result=$typed)")
                    return typed
                }
            }

            if (searchButtonNode != null) {
                val clicked = AccessibilityNodeUtils.clickElement(searchButtonNode, service)
                Log.i(loggerTag, "Clicked ContactPicker search button to find '$pendingRecipient' (result=$clicked)")
                return clicked
            }
        } catch (e: Exception) {
            Log.e(loggerTag, "Error in tryResolveContactPicker: ${e.message}")
        }
        return false
    }
}
