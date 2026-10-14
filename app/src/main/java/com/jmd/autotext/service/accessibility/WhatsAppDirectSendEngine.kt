package com.jmd.autotext.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.jmd.autotext.util.AppFileLogger
import java.util.ArrayDeque

class WhatsAppDirectSendEngine(
    private val service: AccessibilityService,
    private val loggerTag: String = "WADirectSendEngine"
) {

    fun tryClickSendButton(
        root: AccessibilityNodeInfo,
        pkg: String,
        pendingMessage: String,
        sendAttemptCount: Int,
        hasAttemptedSendClick: Boolean,
        windows: List<AccessibilityWindowInfo>? = null,
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

            // Check if active voice recording occurred and cancel it immediately
            VoiceNoteProtectionGuard.handleAndCancelActiveVoiceRecording(root, pkg, service)

            // Check if an error dialog appeared (e.g. number not on WhatsApp)
            if (WhatsAppDialogHandler.checkAndHandleErrorDialog(
                    root = root,
                    pkg = pkg,
                    pendingTaskId = 0L,
                    pendingRecipient = "",
                    pendingMessage = pendingMessage,
                    service = service,
                    onDismissed = { onTimeout() }
                )) {
                return
            }

            val inputNode = findMessageInputField(root, pkg, windows)

            // Never click a Send-like control for a non-empty message unless the exact draft is
            // observable. A stale/hidden composer can otherwise send somebody else's draft.
            if (!hasAttemptedSendClick && inputNode == null) {
                if (sendAttemptCount <= 20) onRetry(400L) else {
                    Log.w(loggerTag, "Message composer is not observable; refusing to click any Send-like control without a visible composer.")
                    onTimeout()
                }
                return
            }

            // After a send click, verify the composer changed before declaring success.
            // A missing accessibility input node is inconclusive, not proof of delivery.
            if (hasAttemptedSendClick) {
                if (inputNode != null && isInputEmptyOrHint(inputNode)) {
                    Log.d(loggerTag, "WhatsApp composer cleared/changed after send click; treating UI transition as send confirmation.")
                    AppFileLogger.action(service, loggerTag, "WhatsApp composer is empty after the send click; UI submission confirmed, delivery receipt not checked.")
                    onSuccess()
                } else if (sendAttemptCount <= 12) {
                    onRetry(500L)
                } else {
                    Log.w(loggerTag, "Could not verify WhatsApp composer changed after send click.")
                    onTimeout()
                }
                return
            }

            // Never send a pre-existing draft that doesn't match the requested message.
            val sendNodeCheck = findSendButton(root, pkg, windows)
            val isMicButton = sendNodeCheck != null && VoiceNoteProtectionGuard.isVoiceNode(sendNodeCheck)
            val inputMatchesPending = inputNode != null && inputContainsPendingMessage(inputNode, pendingMessage)

            if (inputNode != null && pendingMessage.isNotBlank() && !hasAttemptedSendClick &&
                (!inputMatchesPending || isInputEmptyOrHint(inputNode) || isMicButton || sendNodeCheck == null)) {
                if (sendAttemptCount > 12) {
                    Log.w(loggerTag, "Unable to prepare matching pending message in WhatsApp input; refusing to send any other draft.")
                    onTimeout()
                    return
                }
                val injected = AccessibilityNodeUtils.setTextOnInput(inputNode, pendingMessage, service)
                Log.d(loggerTag, "Prepared pending message in input (success=$injected, isMicButton=$isMicButton, sendNodeNull=${sendNodeCheck == null})")
                onRetry(if (injected) 450L else 350L)
                return
            }

            // If a visible input still does not contain our pending message, fail closed.
            if (inputNode != null && pendingMessage.isNotBlank() && !hasAttemptedSendClick && !inputMatchesPending) {
                Log.w(loggerTag, "Pending message does not match input; refusing to click Send.")
                onTimeout()
                return
            }

            val sendNode = sendNodeCheck ?: findSendButton(root, pkg, windows)

            if (sendNode != null) {
                if (VoiceNoteProtectionGuard.isVoiceNode(sendNode)) {
                    Log.w(loggerTag, "sendNode is flagged as voice node! Force-refreshing text watcher...")
                    if (inputNode != null) {
                        AccessibilityNodeUtils.setTextOnInput(inputNode, pendingMessage, service)
                    }
                    onRetry(400L)
                    return
                }

                val clickedViaA11y = AccessibilityNodeUtils.clickElement(
                    node = sendNode,
                    service = service
                )
                Log.d(loggerTag, "Send button element click result: $clickedViaA11y")
                if (clickedViaA11y) {
                    onAttemptClicked()
                    AppFileLogger.action(service, loggerTag, "Clicked WhatsApp send button; waiting for composer verification.")
                    // Verification is performed on the next pass. Do not click the send button again.
                    onRetry(600L)
                } else if (sendAttemptCount <= 12) {
                    onRetry(400L)
                } else {
                    onTimeout()
                }
            } else {
                if (sendAttemptCount <= 45) {
                    onRetry(350L)
                } else {
                    Log.w(loggerTag, "Send button not found within attempt limit")
                    onTimeout()
                }
            }
        } catch (t: Throwable) {
            Log.e(loggerTag, "Error in tryClickSendButton", t)
            onTimeout()
        }
    }

    fun findMessageInputField(
        root: AccessibilityNodeInfo,
        pkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val ids = listOf("entry", "caption", "text_entry")
        for (id in ids) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, pkg, id, windows)
            nodes.firstOrNull { it.isVisibleToUser }?.let { return it }
        }

        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            if (node.isVisibleToUser && node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        return null
    }

    fun findSendButton(
        root: AccessibilityNodeInfo,
        pkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val ids = listOf("send", "send_old", "send_btn", "fab", "entry_send", "action_send", "send_button")
        for (id in ids) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, pkg, id, windows)
            for (n in nodes) {
                if (n.isVisibleToUser && !VoiceNoteProtectionGuard.isVoiceNode(n)) return n
            }
        }

        val textNode: AccessibilityNodeInfo? = AccessibilityNodeUtils.findNodeByTextOrDesc(
            root,
            listOf("send", "enviar", "भेजें", "post"),
            windows = windows,
            exact = true
        )
        if (textNode != null && textNode.isVisibleToUser && !VoiceNoteProtectionGuard.isVoiceNode(textNode)) {
            return textNode
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
        return expected.isNotEmpty() && visible == expected
    }
}
