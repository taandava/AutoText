package com.jmd.autotext.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.jmd.autotext.util.AppFileLogger
import java.util.ArrayDeque

class WhatsAppGroupEngine(
    private val service: AccessibilityService,
    private val loggerTag: String = "WAGroupEngine"
) {

    fun tryHandleGroupSearchAndSend(
        root: AccessibilityNodeInfo,
        pkg: String,
        pendingGroupRecipient: String,
        groupSearchStep: Int,
        sendAttemptCount: Int,
        windows: List<AccessibilityWindowInfo>? = null,
        onNextStep: (nextStep: Int) -> Unit,
        onOpenChat: () -> Unit,
        onTimeout: () -> Unit,
        onRetry: (delayMs: Long) -> Unit
    ) {
        try {
            val curPkg = root.packageName?.toString() ?: ""
            if (!curPkg.contains(pkg, ignoreCase = true) && !pkg.contains(curPkg, ignoreCase = true)) {
                Log.d(loggerTag, "Not in target package $pkg yet for group search ($curPkg)")
                return
            }

            // A message input alone is not proof that the requested group is open: WhatsApp can
            // resume a previously open/private chat. Verify the visible conversation title exactly.
            val chatInput = findMessageInputField(root, pkg, windows)
            val conversationTitle = findConversationTitle(root, pkg, windows)
            val targetGroup = normalizeTitle(pendingGroupRecipient)
            if (chatInput != null && groupSearchStep >= 2 &&
                conversationTitle != null && normalizeTitle(conversationTitle) == targetGroup) {
                Log.i(loggerTag, "Verified requested group '$pendingGroupRecipient' is open; switching to send engine")
                onOpenChat()
                return
            }

            when (groupSearchStep) {
                0 -> {
                    // Step 0: Find and click Search icon in WhatsApp top bar
                    val searchBtn = findWhatsAppSearchButton(root, pkg, windows)
                    if (searchBtn != null) {
                        val clicked = AccessibilityNodeUtils.clickElement(searchBtn, service)
                        Log.i(loggerTag, "Tapped WhatsApp search icon element (result=$clicked). Waiting for search bar...")
                        if (clicked) {
                            onNextStep(1)
                            onRetry(450L)
                        } else if (sendAttemptCount > 35) onTimeout() else onRetry(350L)
                    } else {
                        if (sendAttemptCount <= 35) {
                            onRetry(350L)
                        } else {
                            onTimeout()
                        }
                    }
                }
                1 -> {
                    // Step 1: Find search input field and inject group name
                    val searchInput = findWhatsAppSearchInputField(root, pkg, windows)
                    if (searchInput != null) {
                        val set = AccessibilityNodeUtils.setTextOnInput(searchInput, stripGroupPrefix(pendingGroupRecipient), service)
                        Log.i(loggerTag, "Injected group search keyword '$pendingGroupRecipient' (success=$set). Waiting for search results...")
                        if (set) {
                            onNextStep(2)
                            onRetry(650L)
                        } else if (sendAttemptCount > 35) onTimeout() else onRetry(350L)
                    } else {
                        if (sendAttemptCount <= 35) {
                            onRetry(350L)
                        } else {
                            onTimeout()
                        }
                    }
                }
                2 -> {
                    // Step 2: Find matching chat item in search results and click it
                    val matchingItem = findMatchingChatResult(root, pendingGroupRecipient, windows)
                    if (matchingItem != null) {
                        val clicked = AccessibilityNodeUtils.clickElement(matchingItem, service)
                        Log.i(loggerTag, "Clicked exact matching chat item for '$pendingGroupRecipient' (result=$clicked). Waiting for chat to open...")
                        if (clicked) {
                            onNextStep(3)
                            onRetry(700L)
                        } else if (sendAttemptCount > 40) onTimeout() else onRetry(400L)
                    } else {
                        if (sendAttemptCount <= 40) {
                            onRetry(400L)
                        } else {
                            Log.w(loggerTag, "Search result timeout for group '$pendingGroupRecipient' in WhatsApp.")
                            onTimeout()
                        }
                    }
                }
                3 -> {
                    // Step 3: only proceed when both the text input and exact group title match.
                    val title = findConversationTitle(root, pkg, windows)
                    val inputNode = findMessageInputField(root, pkg, windows)
                    if (inputNode != null && title != null && normalizeTitle(title) == targetGroup) {
                        onOpenChat()
                    } else if (sendAttemptCount <= 35) {
                        onRetry(400L)
                    } else {
                        Log.w(loggerTag, "Requested group not verified (target='$targetGroup', visible='$title'); refusing to send to a possibly wrong chat")
                        onTimeout()
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(loggerTag, "Error in tryHandleGroupSearchAndSend", t)
            onTimeout()
        }
    }

    private fun findWhatsAppSearchButton(
        root: AccessibilityNodeInfo,
        pkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val ids = listOf("menuitem_search", "search", "search_holder", "search_btn", "action_search")
        for (id in ids) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, pkg, id, windows)
            nodes.firstOrNull { it.isVisibleToUser }?.let { return it }
        }

        return AccessibilityNodeUtils.findNodeByTextOrDesc(
            root,
            listOf("search", "खोजें", "buscar", "buscar en whatsapp"),
            windows = windows,
            exact = false
        )?.takeIf { it.isVisibleToUser }
    }

    private fun findWhatsAppSearchInputField(
        root: AccessibilityNodeInfo,
        pkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val ids = listOf("search_view_edit_text", "search_src_text", "search_input", "my_search_bar", "search_bar")
        for (id in ids) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, pkg, id, windows)
            nodes.firstOrNull { it.isVisibleToUser }?.let { return it }
        }

        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val hint = node.hintText?.toString()?.lowercase().orEmpty()
            val description = node.contentDescription?.toString()?.lowercase().orEmpty()
            if (node.isVisibleToUser && node.className?.toString()?.contains("EditText", ignoreCase = true) == true &&
                (id.contains("search") || hint.contains("search") || description.contains("search"))) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        return null
    }

    private fun findMatchingChatResult(
        root: AccessibilityNodeInfo,
        groupName: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val cleanQuery = normalizeTitle(groupName)
        if (cleanQuery.isBlank()) return null
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        val exactMatches = mutableListOf<AccessibilityNodeInfo>()
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val className = node.className?.toString().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val isInput = node.isEditable || className.contains("EditText", ignoreCase = true) || id.contains("search")
            if (node.isVisibleToUser && !isInput) {
                val texts = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                    .map(::normalizeTitle)
                if (texts.any { it == cleanQuery }) exactMatches.add(node)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { q.add(it) }
        }
        // Prefer the tightest exact text match and walk to the clickable row container.
        val candidate = exactMatches.firstOrNull { it.text?.toString()?.let(::normalizeTitle) == cleanQuery }
            ?: exactMatches.firstOrNull()
        if (candidate != null) {
            var target: AccessibilityNodeInfo? = candidate
            var depth = 0
            while (target != null && depth < 7) {
                if (target.isClickable) return target
                target = target.parent
                depth++
            }
            return candidate
        }
        return null
    }

    private fun findConversationTitle(
        root: AccessibilityNodeInfo,
        pkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): String? {
        val titleIds = listOf("conversation_contact_name", "conversation_contact", "group_title", "action_bar_title", "toolbar_title")
        for (id in titleIds) {
            val titles = AccessibilityNodeUtils.findNodesByViewId(root, pkg, id, windows)
            titles.firstOrNull { it.isVisibleToUser && !it.text.isNullOrBlank() }?.text?.toString()?.trim()?.let { return it }
        }
        // Fallback only to likely title nodes near the top of the visible screen, not arbitrary matching text.
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val cls = node.className?.toString().orEmpty()
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val text = node.text?.toString()?.trim().orEmpty()
            if (node.isVisibleToUser && text.isNotBlank() && !node.isEditable && cls.contains("TextView", true) &&
                bounds.top in 0..240 && (id.contains("title") || id.contains("contact") || id.contains("conversation"))) {
                return text
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun stripGroupPrefix(value: String): String =
        value.replaceFirst(Regex("^group\\s*:", RegexOption.IGNORE_CASE), "").trim()

    private fun normalizeTitle(value: String): String = stripGroupPrefix(value)
        .replace(Regex("\\s+"), " ")
        .lowercase()

    private fun findMessageInputField(
        root: AccessibilityNodeInfo,
        pkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val ids = listOf("entry", "caption", "text_entry")
        for (id in ids) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, pkg, id, windows)
            nodes.firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        return null
    }
}
