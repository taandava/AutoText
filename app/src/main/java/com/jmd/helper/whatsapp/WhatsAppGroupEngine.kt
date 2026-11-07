/**
 * Package: com.jmd.helper.whatsapp
 * Module: WhatsApp Task Subsystem
 *
 * Scheduled group messaging dispatcher. Locates WhatsApp groups via search query, opens the group chat,
 * verifies conversation context, and invokes text transmission.
 *
 * Linked With:
 * - Service: [com.jmd.helper.service.TaskAccessibilityService]
 * - Direct Engine: [com.jmd.helper.whatsapp.WhatsAppDirectSendEngine]
 * - Node Utils: [com.jmd.helper.whatsapp.AccessibilityNodeUtils]
 */
package com.jmd.helper.whatsapp

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * WhatsApp group search and message dispatch engine for both WhatsApp and WhatsApp Business.
 *
 * @param service Active accessibility service instance.
 * @param loggerTag Logging tag for diagnostic output.
 */
class WhatsAppGroupEngine(
    private val service: AccessibilityService,
    private val loggerTag: String = "WAGroupEngine"
) {

    /**
     * Conducts group search, navigates into matching group conversation, and transitions mode to message transmission.
     *
     * @param root Active accessibility tree root.
     * @param pkg Target WhatsApp package identifier.
     * @param pendingGroupRecipient Group chat title to find and open.
     * @param sendAttemptCount Elapsed retry loop counter.
     * @param windows Optional list of screen windows.
     * @param onOpenChat Callback invoked when group conversation is confirmed opened.
     * @param onTimeout Callback invoked if group search exceeds maximum retries.
     * @param onRetry Callback invoked to schedule subsequent polling attempt.
     */
    fun tryHandleGroupSearchAndSend(
        root: AccessibilityNodeInfo,
        pkg: String,
        pendingGroupRecipient: String,
        sendAttemptCount: Int,
        windows: List<AccessibilityWindowInfo>? = null,
        onOpenChat: () -> Unit,
        onTimeout: () -> Unit,
        onRetry: (delayMs: Long) -> Unit
    ) {
        try {
            val curPkg = root.packageName?.toString().orEmpty()
            if (!curPkg.contains(pkg, ignoreCase = true) && !pkg.contains(curPkg, ignoreCase = true)) {
                Log.d(loggerTag, "Not in target package $pkg yet ($curPkg)")
                return
            }

            if (sendAttemptCount < 2 && PopupShield.hasActiveBlockingOverlay(service, pkg)) {
                Log.d(loggerTag, "Blocking overlay detected during group send; clearing...")
                PopupShield.clearOverlaysAndNotificationShade(service, pkg)
                onRetry(300L)
                return
            }

            val chatInput = findMessageInputField(root, pkg, windows)
            val conversationTitle = findConversationTitle(root, pkg, windows)
            val targetGroup = normalizeTitle(pendingGroupRecipient)

            val isTitleMatching = if (conversationTitle != null) {
                val normTitle = normalizeTitle(conversationTitle)
                normTitle == targetGroup || normTitle.contains(targetGroup) || targetGroup.contains(normTitle)
            } else true

            // If chat/group conversation is already open, immediately switch to direct send engine
            if (chatInput != null && isTitleMatching) {
                Log.i(loggerTag, "Target chat/group conversation open; switching to send engine directly")
                onOpenChat()
                return
            }

            val cleanQueryText = stripGroupPrefix(pendingGroupRecipient)

            // 1. Directly tap matching chat row if visible in chat list
            val matchingItem = findMatchingChatResult(root, cleanQueryText)
            if (matchingItem != null) {
                val clicked = AccessibilityNodeUtils.clickElement(matchingItem, service)
                Log.i(loggerTag, "Tapped visible chat row for '$cleanQueryText' (result=$clicked)")
                if (clicked) {
                    onRetry(600L)
                    return
                }
            }

            // 2. Check if search input is already active
            val searchInput = findSearchInput(root)
            if (searchInput != null) {
                val currentSearch = searchInput.text?.toString()?.trim().orEmpty()
                if (!currentSearch.equals(cleanQueryText, ignoreCase = true)) {
                    val typed = AccessibilityNodeUtils.setTextOnInput(searchInput, cleanQueryText, service)
                    Log.d(loggerTag, "Typed search query '$cleanQueryText' into search input: $typed")
                    onRetry(500L)
                    return
                } else {
                    // Search query is typed; inspect roots for matching result item
                    val candidateRoots = if (!windows.isNullOrEmpty()) {
                        windows.mapNotNull { it.root }
                    } else {
                        listOf(root)
                    }

                    for (candidateRoot in candidateRoots) {
                        val result = findMatchingChatResult(candidateRoot, cleanQueryText)
                        if (result != null) {
                            val clicked = AccessibilityNodeUtils.clickElement(result, service)
                            Log.i(loggerTag, "Clicked search result for '$cleanQueryText': $clicked")
                            if (clicked) {
                                onRetry(600L)
                                return
                            }
                        }
                    }
                }
            } else {
                // 3. Search input not open; click search button/icon
                val searchBtn = findSearchButton(root, pkg, windows)
                if (searchBtn != null) {
                    val clicked = AccessibilityNodeUtils.clickElement(searchBtn, service)
                    Log.i(loggerTag, "Clicked search icon: $clicked")
                    onRetry(500L)
                    return
                } else {
                    Log.w(loggerTag, "Search icon not found; retry #$sendAttemptCount")
                }
            }

            if (sendAttemptCount >= 16) {
                Log.e(loggerTag, "Timed out searching for group '$pendingGroupRecipient'")
                onTimeout()
            } else {
                onRetry(400L)
            }
        } catch (t: Throwable) {
            Log.e(loggerTag, "Error during group search and send", t)
            onRetry(400L)
        }
    }

    private fun findSearchButton(
        root: AccessibilityNodeInfo,
        pkg: String,
        windows: List<AccessibilityWindowInfo>?
    ): AccessibilityNodeInfo? {
        val searchIds = listOf("menuitem_search", "search_tool_bar", "search_holder", "search_button")
        for (id in searchIds) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, pkg, id, windows)
            nodes.firstOrNull { it.isVisibleToUser && (it.isClickable || it.parent?.isClickable == true) }?.let {
                return if (it.isClickable) it else it.parent
            }
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()

            if ((desc == "search" || desc.startsWith("search") || text == "search") &&
                (node.isClickable || node.parent?.isClickable == true)
            ) {
                return if (node.isClickable) node else node.parent
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findSearchInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val cls = node.className?.toString().orEmpty()

            val isSearch = id.contains("search") || id.contains("query")
            val isEditable = node.isEditable || cls.contains("EditText", ignoreCase = true)

            if (node.isVisibleToUser && isEditable && isSearch) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findMatchingChatResult(
        root: AccessibilityNodeInfo,
        query: String
    ): AccessibilityNodeInfo? {
        val cleanQuery = normalizeTitle(query)
        if (cleanQuery.isBlank()) return null

        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)

        val exactMatches = mutableListOf<AccessibilityNodeInfo>()
        val prefixMatches = mutableListOf<AccessibilityNodeInfo>()
        val partialTitleMatches = mutableListOf<AccessibilityNodeInfo>()
        val snippetIds = listOf("snippet", "msg", "message", "last_msg", "status", "date_time", "timestamp")

        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val className = node.className?.toString().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val isInput = node.isEditable || className.contains("EditText", ignoreCase = true)
            val isSnippet = snippetIds.any { id.contains(it) }

            if (node.isVisibleToUser && !isInput && !isSnippet) {
                val texts = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                    .map(::normalizeTitle)
                val isTitleView = id.contains("title") || id.contains("name") || id.contains("contact")

                if (texts.any { it == cleanQuery }) {
                    exactMatches.add(node)
                } else if (texts.any { it.startsWith(cleanQuery) }) {
                    prefixMatches.add(node)
                } else if (isTitleView && texts.any { it.contains(cleanQuery) }) {
                    partialTitleMatches.add(node)
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { q.add(it) }
        }

        val candidate = exactMatches.firstOrNull { it.text?.toString()?.let(::normalizeTitle) == cleanQuery }
            ?: exactMatches.firstOrNull()
            ?: prefixMatches.firstOrNull()
            ?: partialTitleMatches.firstOrNull()

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
                bounds.top in 0..450 && (id.contains("title") || id.contains("contact") || id.contains("conversation"))) {
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
        val ids = listOf(
            "entry", "caption", "text_entry", "input", "message_input",
            "conversation_text_entry", "compose_message_text", "input_text"
        )
        for (id in ids) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, pkg, id, windows)
            nodes.firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < 60) {
            val node = queue.removeFirst()
            count++
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val isSearch = id.contains("search") || id.contains("query") || id.contains("find")
            if (node.isVisibleToUser && !isSearch && (node.className?.toString()?.contains("EditText", ignoreCase = true) == true || (node.isEditable && !node.isPassword))) {
                return node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }
}

