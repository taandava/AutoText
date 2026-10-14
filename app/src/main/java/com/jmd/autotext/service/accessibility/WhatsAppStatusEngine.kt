package com.jmd.autotext.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.jmd.autotext.util.AppFileLogger
import java.util.ArrayDeque

class WhatsAppStatusEngine(
    private val service: AccessibilityService,
    private val loggerTag: String = "WAStatusEngine"
) {

    private var hasClickedSendButton = false
    private var hasClickedShareProceedButton = false
    private var hasClickedMyStatusRow = false
    private var hasTappedUpdatesTab = false
    private var hasTappedTextStatusFab = false
    private var statusComposerWaitCount = 0

    fun resetState() {
        hasClickedSendButton = false
        hasClickedShareProceedButton = false
        hasClickedMyStatusRow = false
        hasTappedUpdatesTab = false
        hasTappedTextStatusFab = false
        statusComposerWaitCount = 0
    }

    fun tryHandleStatusPost(
        root: AccessibilityNodeInfo,
        targetPkg: String,
        pendingMessage: String,
        isMediaStatus: Boolean = false,
        sendAttemptCount: Int,
        windows: List<AccessibilityWindowInfo>? = null,
        onSuccess: () -> Unit,
        onTimeout: () -> Unit,
        onRetry: (delayMs: Long) -> Unit
    ) {
        try {
            if (pendingMessage.isBlank() && !isMediaStatus) {
                Log.w(loggerTag, "Refusing to publish a blank text-only WhatsApp Status.")
                onTimeout()
                return
            }

            val curPkg = root.packageName?.toString() ?: ""
            if (!curPkg.contains(targetPkg, ignoreCase = true) && !targetPkg.contains(curPkg, ignoreCase = true)) {
                Log.d(loggerTag, "Not in target package $targetPkg yet for status ($curPkg)")
                onRetry(300L)
                return
            }

            // Quick check: If search bar is currently active, dismiss it to reveal the main screen
            val searchBox = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, "search_view_edit_text", windows)
            // Keep the share picker open for media-status flows; it also exposes a search field.
            if (searchBox.any { it.isVisibleToUser } && !isSharePickerScreen(root, targetPkg, windows)) {
                Log.d(loggerTag, "Search view active while preparing status post, pressing Back to dismiss search...")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                onRetry(400L)
                return
            }

            // Check and handle any permission / error popups
            WhatsAppDialogHandler.checkAndHandlePermissionOrPopup(root, service)

            // A successful status post returns to a screen outside the composer. Only that
            // observed screen transition is sufficient evidence of success.
            if (hasClickedSendButton && !isStatusComposerScreen(root, targetPkg, windows)) {
                val updatesTab = findWhatsAppUpdatesTab(root, targetPkg, windows)
                val textFab = findWhatsAppTextStatusFab(root, targetPkg, windows)
                val returnedToUpdates = isNodeOrAncestorSelected(updatesTab) || textFab?.isVisibleToUser == true
                if (returnedToUpdates) {
                    AppFileLogger.action(service, loggerTag, "Status composer closed and WhatsApp Updates screen is visible after Send.")
                    resetState()
                    onSuccess()
                } else {
                    statusComposerWaitCount++
                    if (statusComposerWaitCount >= 12) {
                        AppFileLogger.w(service, loggerTag, "Composer closed after Send but publication could not be confirmed on the Updates screen.")
                        onTimeout()
                    } else onRetry(650L)
                }
                return
            }

            // -------------------------------------------------------------------------
            // SCREEN TYPE 1: ExternalShareAlias / ContactPickerActivity (Share Picker)
            // -------------------------------------------------------------------------
            val isSharePicker = isSharePickerScreen(root, targetPkg, windows)
            if (isSharePicker) {
                AppFileLogger.action(service, loggerTag, "WhatsApp Share Picker detected")
                if (hasClickedShareProceedButton) {
                    if (sendAttemptCount > 22) {
                        Log.w(loggerTag, "Share picker did not advance after tapping proceed")
                        onTimeout()
                    } else onRetry(600L)
                    return
                }

                // A generic Send/FAB can forward to the wrong recipient if clicked before My status is
                // selected. Select and verify My status first, then advance only after selection is real.
                val isSelected = isMyStatusSelected(root, targetPkg, windows)
                if (!isSelected) {
                    val myStatusRow = findWhatsAppShareMyStatusRow(root, targetPkg, windows)
                    if (myStatusRow != null) {
                        val clicked = AccessibilityNodeUtils.clickElement(myStatusRow, service)
                        Log.d(loggerTag, "Clicked 'My status' row result: $clicked (node: ${myStatusRow.className}, checkable=${myStatusRow.isCheckable})")
                        if (clicked) hasClickedMyStatusRow = true
                        if (!clicked && sendAttemptCount > 22) onTimeout() else onRetry(600L)
                        return
                    }
                    val scrollable = AccessibilityNodeUtils.findFirstScrollableNode(root)
                    if (scrollable != null && scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                        onRetry(500L)
                    } else if (sendAttemptCount > 22) {
                        Log.w(loggerTag, "My status row not found in share picker")
                        onTimeout()
                    } else onRetry(350L)
                    return
                }

                hasClickedMyStatusRow = false
                val proceedBtn = findWhatsAppStatusProceedButton(root, targetPkg, windows)
                if (proceedBtn != null) {
                    val clicked = AccessibilityNodeUtils.clickElement(proceedBtn, service)
                    Log.d(loggerTag, "Clicked status proceed button result=$clicked")
                    if (clicked) {
                        hasClickedShareProceedButton = true
                        onRetry(700L)
                    } else onRetry(400L)
                } else if (sendAttemptCount > 22) {
                    Log.w(loggerTag, "My status selected but proceed button unavailable")
                    onTimeout()
                } else onRetry(350L)
                return
            }

            // -------------------------------------------------------------------------
            // SCREEN TYPE 2: Status Composer (TextStatusComposerActivity / MediaComposerActivity)
            // -------------------------------------------------------------------------
            val isComposer = isStatusComposerScreen(root, targetPkg, windows)
            if (isComposer) {
                // We have entered the composer; never re-tap the previous screen's controls.
                hasClickedShareProceedButton = false
                hasTappedUpdatesTab = false
                hasTappedTextStatusFab = false
                val statusInput = findWhatsAppStatusInputField(root, targetPkg, windows)
                val composerSendBtn = findWhatsAppStatusSendButton(root, targetPkg, windows)

                // If send button was already clicked on previous iteration: verify completion
                if (hasClickedSendButton) {
                    statusComposerWaitCount++
                    if (statusComposerWaitCount >= 12) {
                        AppFileLogger.w(service, loggerTag, "Status composer is still visible after send click; could not confirm publication.")
                        onTimeout()
                    } else {
                        onRetry(650L)
                    }
                    return
                }

                // If message text needs to be injected into composer
                if (pendingMessage.isNotBlank()) {
                    if (statusInput == null) {
                        if (sendAttemptCount > 22) {
                            Log.w(loggerTag, "Text status composer input is not observable; refusing to publish unverified text")
                            onTimeout()
                        } else onRetry(350L)
                        return
                    }
                    val currentText = statusInput.text?.toString()?.replace("\r\n", "\n")?.trim().orEmpty()
                    val expectedText = pendingMessage.replace("\r\n", "\n").trim()
                    if (currentText != expectedText) {
                        AppFileLogger.action(service, loggerTag, "Injecting/verifying exact status text in composer...")
                        val injected = AccessibilityNodeUtils.setTextOnInput(statusInput, pendingMessage, service)
                        if (!injected && sendAttemptCount > 22) {
                            Log.w(loggerTag, "Unable to inject status text into the composer")
                            onTimeout()
                        } else onRetry(if (injected) 450L else 350L)
                        return
                    }
                }

                // Click Send button in composer
                if (composerSendBtn != null) {
                    AppFileLogger.action(service, loggerTag, "Found Status Send button (${composerSendBtn.viewIdResourceName ?: composerSendBtn.contentDescription})! Publishing status via element click...")
                    val clicked = AccessibilityNodeUtils.clickElement(composerSendBtn, service)
                    Log.d(loggerTag, "Status send button clicked result: $clicked")
                    if (clicked) {
                        hasClickedSendButton = true
                        statusComposerWaitCount = 0
                        onRetry(700L)
                    } else if (sendAttemptCount > 22) {
                        onTimeout()
                    } else {
                        onRetry(350L)
                    }
                    return
                } else {
                    if (sendAttemptCount <= 22) {
                        onRetry(350L)
                    } else {
                        Log.w(loggerTag, "Status composer send button timeout")
                        onTimeout()
                    }
                    return
                }
            }

            // -------------------------------------------------------------------------
            // SCREEN TYPE 3: HomeActivity / Updates Tab Fallback
            // -------------------------------------------------------------------------
            val updatesTab = findWhatsAppUpdatesTab(root, targetPkg, windows)
            val textFab = findWhatsAppTextStatusFab(root, targetPkg, windows)
            val isUpdatesSelected = isNodeOrAncestorSelected(updatesTab) || textFab?.isVisibleToUser == true

            if (!isUpdatesSelected && updatesTab != null) {
                if (hasTappedUpdatesTab) {
                    if (sendAttemptCount > 12) {
                        Log.w(loggerTag, "Updates tab did not become selected after tap")
                        onTimeout()
                    } else onRetry(500L)
                    return
                }
                AppFileLogger.action(service, loggerTag, "Tapping Updates tab element in bottom nav...")
                val clicked = AccessibilityNodeUtils.clickElement(updatesTab, service)
                if (clicked) {
                    hasTappedUpdatesTab = true
                    onRetry(800L)
                } else onRetry(350L)
                return
            }
            if (isUpdatesSelected) hasTappedUpdatesTab = false

            if (textFab != null) {
                if (hasTappedTextStatusFab) {
                    if (sendAttemptCount > 18) {
                        Log.w(loggerTag, "Text status composer did not open after tapping pencil FAB")
                        onTimeout()
                    } else onRetry(500L)
                    return
                }
                AppFileLogger.action(service, loggerTag, "Tapping text status pencil FAB element...")
                val clicked = AccessibilityNodeUtils.clickElement(textFab, service)
                if (clicked) {
                    hasTappedTextStatusFab = true
                    onRetry(850L)
                } else onRetry(350L)
                return
            }

            if (sendAttemptCount <= 45) {
                onRetry(350L)
            } else {
                Log.w(loggerTag, "WhatsApp Status posting timed out")
                onTimeout()
            }
        } catch (t: Throwable) {
            Log.e(loggerTag, "Error in tryHandleStatusPost", t)
            onTimeout()
        }
    }

    private fun isSharePickerScreen(
        root: AccessibilityNodeInfo,
        targetPkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): Boolean {
        val contactPickerLayout = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, "contact_picker_layout", windows)
        if (contactPickerLayout.any { it.isVisibleToUser }) return true

        val contactRows = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, "contactpicker_row_name", windows)
        if (contactRows.any { it.isVisibleToUser }) return true

        // Do not use the mere presence of a “My status” row as evidence of the share picker:
        // the normal Updates screen also displays a My Status card. False detection here traps
        // text-status navigation on Updates and can tap the wrong generic action.
        val pickerOnlyIds = listOf("contactpicker_row_name", "contactpicker_row", "contact_picker_row")
        return pickerOnlyIds.any { AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, it, windows).any { node -> node.isVisibleToUser } }
    }

    private fun isStatusComposerScreen(
        root: AccessibilityNodeInfo,
        targetPkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): Boolean {
        val composerLayout = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, "status_composer_layout", windows)
        if (composerLayout.any { it.isVisibleToUser }) return true

        val captionLayout = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, "caption_layout", windows)
        if (captionLayout.any { it.isVisibleToUser }) return true

        val fontBtn = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, "font_picker_btn", windows)
        val colorBtn = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, "color_picker_btn", windows)
        if (fontBtn.any { it.isVisibleToUser } || colorBtn.any { it.isVisibleToUser }) return true

        val bottomBar = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, "text_status_creation_mode_bottom_bar", windows)
        if (bottomBar.any { it.isVisibleToUser }) return true

        val caption: AccessibilityNodeInfo? = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, "caption", windows).firstOrNull { it.isVisibleToUser }
        return caption != null
    }

    @Suppress("DEPRECATION")
    private fun isMyStatusSelected(
        root: AccessibilityNodeInfo,
        targetPkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): Boolean {
        // 1. Check if recipient chip/text indicates Status is selected
        val recipientNodes: List<AccessibilityNodeInfo> = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, "recipients", windows)
        for (node in recipientNodes) {
            if (!node.isVisibleToUser) continue
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""
            if (text.contains("my status") || text.contains("मेरा स्टेटस") || text.contains("माझा स्टेटस")) return true
        }

        // 2. Check if the "My status" RadioButton / row is checked
        val row = findWhatsAppShareMyStatusRow(root, targetPkg, windows)
        if (row != null) {
            if (row.isChecked || row.isSelected) return true
            var p: AccessibilityNodeInfo? = row
            var depth = 0
            while (p != null && depth < 4) {
                if (p.isChecked || p.isSelected) return true
                p = p.parent
                depth++
            }
            // Check children of row
            for (i in 0 until row.childCount) {
                val c = row.getChild(i)
                if (c != null && (c.isChecked || c.isSelected)) return true
            }
        }

        // Never infer recipient selection from a generic Send button: its visibility alone is ambiguous.
        return false
    }

    private fun findWhatsAppShareMyStatusRow(
        root: AccessibilityNodeInfo,
        targetPkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val keywords = listOf(
            "my status", "mi estado", "mon statut", "mein status",
            "mio stato", "meu status", "मेरा स्टेटस", "माझा स्टेटस", "ना स्टेटस"
        )

        // 1. Search for RadioButton container where description or child contains "My status"
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""
            val cls = node.className?.toString().orEmpty()

            if (node.isVisibleToUser) {
                val matchesKeyword = keywords.any { desc.startsWith(it) || text.startsWith(it) || desc.contains(it) || text.contains(it) }
                if (matchesKeyword && !desc.contains("privacy") && !text.contains("privacy")) {
                    if (cls.contains("RadioButton", ignoreCase = true) || node.isCheckable) {
                        return node
                    }
                    var parent = node.parent
                    var depth = 0
                    while (parent != null && depth < 5) {
                        if (parent.className?.toString()?.contains("RadioButton", ignoreCase = true) == true || parent.isCheckable) {
                            return parent
                        }
                        parent = parent.parent
                        depth++
                    }
                    return node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        // 2. Search by row name view IDs
        val rowIds = listOf("contactpicker_row_name", "name", "title", "contact_name", "status_title", "text1")
        for (id in rowIds) {
            val rowNames: List<AccessibilityNodeInfo> = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, id, windows)
            for (nameNode in rowNames) {
                if (!nameNode.isVisibleToUser) continue
                val text = nameNode.text?.toString()?.lowercase()?.trim() ?: ""
                val desc = nameNode.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                if (keywords.any { text.contains(it) || desc.contains(it) }) {
                    var parent = nameNode.parent
                    var depth = 0
                    while (parent != null && depth < 5) {
                        if (parent.className?.toString()?.contains("RadioButton", ignoreCase = true) == true || parent.isCheckable) {
                            return parent
                        }
                        parent = parent.parent
                        depth++
                    }
                    return nameNode
                }
            }
        }

        // 3. Fallback search
        return AccessibilityNodeUtils.findNodeByTextOrDesc(
            root,
            keywords,
            windows = windows
        )?.takeIf { it.isVisibleToUser }
    }

    private fun findWhatsAppStatusProceedButton(
        root: AccessibilityNodeInfo,
        targetPkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        // In WhatsApp Share Picker, the proceed button is id/send or id/fab or id/next with desc="Send" or "Next"
        val proceedIds = listOf("send", "fab", "next_btn", "action_send", "bottom_action", "floating_action_button", "forward")
        for (id in proceedIds) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, id, windows)
            for (n in nodes) {
                if (n.isVisibleToUser && !VoiceNoteProtectionGuard.isVoiceNode(n)) {
                    // Only tap an action anchored to a currently visible control.
                    var candidate: AccessibilityNodeInfo? = n
                    var depth = 0
                    while (candidate != null && depth < 3) {
                        if (candidate.isVisibleToUser && candidate.isClickable) return candidate
                        candidate = candidate.parent
                        depth++
                    }
                    return n
                }
            }
        }

        // Also check by content description or text
        val descNode = AccessibilityNodeUtils.findNodeByTextOrDesc(
            root,
            listOf("send", "next", "continue", "forward", "proceed", "done"),
            windows = windows,
            exact = true
        )
        if (descNode != null && descNode.isVisibleToUser && !VoiceNoteProtectionGuard.isVoiceNode(descNode)) {
            var candidate: AccessibilityNodeInfo? = descNode
            var depth = 0
            while (candidate != null && depth < 3) {
                if (candidate.isClickable) return candidate
                candidate = candidate.parent
                depth++
            }
            return descNode
        }

        return null
    }

    private fun findWhatsAppStatusInputField(
        root: AccessibilityNodeInfo,
        targetPkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val ids = listOf("entry", "caption", "status_entry", "text_entry")
        for (id in ids) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, id, windows)
            nodes.firstOrNull { it.isVisibleToUser }?.let { return it }
        }

        try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isVisibleToUser && (focused.isEditable || focused.className?.toString()?.contains("EditText", ignoreCase = true) == true)) {
                return focused
            }
        } catch (_: Throwable) {}

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

    private fun findWhatsAppStatusSendButton(
        root: AccessibilityNodeInfo,
        targetPkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val ids = listOf("send", "send_btn", "confirm", "done", "fab", "next_btn", "send_button", "action_send")
        for (id in ids) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, id, windows)
            for (n in nodes) {
                if (n.isVisibleToUser && !VoiceNoteProtectionGuard.isVoiceNode(n)) return n
            }
        }

        val sendNode: AccessibilityNodeInfo? = AccessibilityNodeUtils.findNodeByTextOrDesc(
            root,
            listOf("send", "post", "share", "done"),
            windows = windows,
            exact = true
        )
        if (sendNode != null && sendNode.isVisibleToUser && !VoiceNoteProtectionGuard.isVoiceNode(sendNode)) {
            return sendNode
        }

        return null
    }

    @Suppress("DEPRECATION")
    private fun isNodeOrAncestorSelected(node: AccessibilityNodeInfo?): Boolean {
        var candidate = node
        var depth = 0
        while (candidate != null && depth < 5) {
            if (candidate.isSelected || candidate.isChecked) return true
            candidate = candidate.parent
            depth++
        }
        return false
    }

    private fun findWhatsAppUpdatesTab(
        root: AccessibilityNodeInfo,
        targetPkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val navLabels = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, "navigation_bar_item_large_label_view", windows)
        for (node in navLabels) {
            val text = node.text?.toString()?.lowercase() ?: ""
            if (node.isVisibleToUser && (text.contains("update") || text.contains("status") || text.contains("अपडेट"))) {
                var p: AccessibilityNodeInfo? = node
                var depth = 0
                while (p != null && depth < 3) {
                    if (p.isClickable) return p
                    p = p.parent
                    depth++
                }
                return node
            }
        }
        return AccessibilityNodeUtils.findNodeByTextOrDesc(
            root,
            listOf("updates", "status", "novedades", "aktualisierungen", "अपडेट"),
            windows = windows
        )?.takeIf { it.isVisibleToUser }
    }

    private fun findWhatsAppTextStatusFab(
        root: AccessibilityNodeInfo,
        targetPkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val fabIds = listOf("extended_mini_fab", "status_text_fab", "fab_aux", "text_status", "pencil_fab")
        for (id in fabIds) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, targetPkg, id, windows)
            for (n in nodes) {
                val desc = n.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                if (n.isVisibleToUser && !desc.contains("assistant") && !desc.contains("meta ai")) {
                    return n
                }
            }
        }

        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            if (node.isVisibleToUser && (desc.contains("text status") || desc.contains("new text") || desc.contains("type a status") || desc.contains("pencil")) &&
                !desc.contains("assistant") && !desc.contains("meta ai")) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        return null
    }
}
