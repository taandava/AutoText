

/**
 * Package: com.jmd.autotext.whatsapp
 * Module: WhatsApp Automation Subsystem
 *
 * Automated group messaging dispatcher. Locates WhatsApp groups via search query, opens the group chat,
 * verifies conversation context, and invokes text transmission.
 *
 * Linked With:
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Direct Engine: [com.jmd.autotext.whatsapp.WhatsAppDirectSendEngine]
 * - Node Utils: [com.jmd.autotext.whatsapp.AccessibilityNodeUtils]
 */
package com.jmd.autotext.whatsapp

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * WhatsApp group search and automated messaging dispatch engine.
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
            val curPkg = root.packageName?.toString() ?: ""
            if (!curPkg.contains(pkg, ignoreCase = true) && !pkg.contains(curPkg, ignoreCase = true)) {
                Log.d(loggerTag, "Not in target package $pkg yet ($curPkg)")
                return
            }

            if (PopupShield.hasActiveBlockingOverlay(service, pkg)) {
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

            // 1. Directly tap matching chat row if visible in chat list
            val matchingItem = findMatchingChatResult(root, pendingGroupRecipient)
            if (matchingItem != null) {
                val clicked = AccessibilityNodeUtils.clickElement(matchingItem, service)
                Log.i(loggerTag, "Tapped visible chat row for '$pendingGroupRecipient' (result=$clicked)")
                if (clicked) {
                    onRetry(600L)
                } else if (sendAttemptCount > 25) onTimeout() else onRetry(350L)
                return
            }

            // 2. If not immediately visible, scroll chat list forward (attempts 2..5)
            if (sendAttemptCount in 2..5) {
                val scrollable = findScrollableList(root)
                var scrolled = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
                if (!scrolled) {
                    scrolled = AccessibilityNodeUtils.performVerticalSwipe(service, startYRatio = 0.75f, endYRatio = 0.35f)
                }
                Log.d(loggerTag, "Scrolled chat list forward looking for '$pendingGroupRecipient' (result=$scrolled)")
                onRetry(550L)
                return
            }

            // 3. In later attempts (>= 6), use search if available
            if (sendAttemptCount in 6..18) {
                val searchInput = findSearchInput(root)
                if (searchInput != null) {
                    val typed = AccessibilityNodeUtils.setTextOnInput(searchInput, pendingGroupRecipient, service)
                    Log.d(loggerTag, "Typed search query '$pendingGroupRecipient' into search input: $typed")
                    onRetry(650L)
                    return
                } else {
                    val searchIcon = findSearchIcon(root, pkg, windows)
                    if (searchIcon != null) {
                        val clicked = AccessibilityNodeUtils.clickElement(searchIcon, service)
                        Log.d(loggerTag, "Clicked search icon: $clicked")
                        onRetry(500L)
                        return
                    }
                }
            }

            if (sendAttemptCount <= 25) {
                onRetry(350L)
            } else {
                Log.w(loggerTag, "Chat '$pendingGroupRecipient' not open or resolved within attempt limit")
                onTimeout()
            }
        } catch (t: Throwable) {
            Log.e(loggerTag, "Error in tryHandleGroupSearchAndSend", t)
            onTimeout()
        }
    }

    private fun findSearchInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val cls = node.className?.toString().orEmpty()
            if (node.isVisibleToUser && (node.isEditable || cls.contains("EditText", ignoreCase = true)) &&
                (id.contains("search") || id.contains("query") || id.contains("find"))) {
                return node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { q.add(it) }
        }
        return null
    }

    private fun findSearchIcon(root: AccessibilityNodeInfo, pkg: String, windows: List<AccessibilityWindowInfo>?): AccessibilityNodeInfo? {
        val ids = listOf("menuitem_search", "search_btn", "search", "action_search", "search_holder")
        for (id in ids) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, pkg, id, windows)
            nodes.firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        val textNode = AccessibilityNodeUtils.findNodeByTextOrDesc(
            root,
            listOf("Search", "Search…", "खोजें", "Buscar", "Rechercher"),
            windows = windows,
            exact = false
        )
        if (textNode != null && textNode.isVisibleToUser) return textNode
        return null
    }

    private fun findScrollableList(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var fallbackScrollable: AccessibilityNodeInfo? = null
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val cls = node.className?.toString().orEmpty()
            val isHorizontalPager = id.contains("pager") || cls.contains("ViewPager")
            if (!isHorizontalPager) {
                if (id == "android:id/list" || cls.contains("RecyclerView") || cls.contains("ListView")) {
                    return node
                }
                if (node.isScrollable && fallbackScrollable == null) {
                    fallbackScrollable = node
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { q.add(it) }
        }
        return fallbackScrollable
    }

    private fun findMatchingChatResult(
        root: AccessibilityNodeInfo,
        groupName: String
    ): AccessibilityNodeInfo? {
        val cleanQuery = normalizeTitle(groupName)
        if (cleanQuery.isBlank()) return null
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        val exactMatches = mutableListOf<AccessibilityNodeInfo>()
        val partialMatches = mutableListOf<AccessibilityNodeInfo>()
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val className = node.className?.toString().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val isInput = node.isEditable || className.contains("EditText", ignoreCase = true) || id.contains("search")
            if (node.isVisibleToUser && !isInput) {
                val texts = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                    .map(::normalizeTitle)
                if (texts.any { it == cleanQuery }) {
                    exactMatches.add(node)
                } else if (texts.any { it.startsWith(cleanQuery) || it.contains(cleanQuery) || (cleanQuery.contains(it) && it.length >= 4) }) {
                    partialMatches.add(node)
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { q.add(it) }
        }

        val candidate = exactMatches.firstOrNull { it.text?.toString()?.let(::normalizeTitle) == cleanQuery }
            ?: exactMatches.firstOrNull()
            ?: partialMatches.firstOrNull()

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
