/**
 * Package: com.jmd.helper.whatsapp
 * Module: WhatsApp Task & Tree Traversal
 *
 * Discovery engine for harvesting WhatsApp direct chats and groups via accessibility
 * node traversal, scrolling, and pool enrichment.
 */
package com.jmd.helper.whatsapp

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jmd.helper.compat.AccessibilityCompat
import com.jmd.helper.core.PhoneNumberNormalizer
import com.jmd.helper.core.showAppToast
import com.jmd.helper.data.model.DynamicContactItem
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.service.TaskAccessibilityService
import com.jmd.helper.whatsapp.AccessibilityNodeUtils.recycleSafely

/** Target filtering scope for scanner discovery. */
enum class ScanTarget {
    ALL_CHATS,
    GROUPS_ONLY,
    DIRECT_CHATS_ONLY
}

/**
 * Accessibility-based scanner engine that directly iterates WhatsApp and WhatsApp Business
 * conversation lists, discovers individual contacts and chats, and saves them into the local dynamic pool.
 */
class WhatsAppChatScanner(
    private val service: AccessibilityService,
    private val loggerTag: String = "WAChatScanner",
) {

    /** Backward compatibility alias for nested enum references. */
    typealias ScanTarget = com.jmd.helper.whatsapp.ScanTarget

    private enum class DeepScanPhase {
        ENSURE_CHATS_TAB,
        ENSURE_GROUPS_FILTER,
        HARVEST_ROWS,
        SCROLL_DOWN,
        COMPLETE
    }

    var scanTarget: ScanTarget = ScanTarget.ALL_CHATS
    private var activePkg: String = "com.whatsapp"
    private var currentPhase = DeepScanPhase.ENSURE_CHATS_TAB
    private val discoveredChats = linkedMapOf<String, DynamicContactItem>()
    private var bottomTabCheckAttempts = 0
    private var groupFilterAttempts = 0
    private var isGroupsFilterActive = false
    private var topScrollAttempts = 0
    private var stepAttempts = 0
    private var scrollCycles = 0
    private val maxScrollCycles = 150
    private var lastDiscoveredCount = 0
    private var consecutiveStagnantCycles = 0
    private val maxStagnantCycles = 4

    /**
     * Clears discovery cache and resets traversal state for a fresh chat scanning pass.
     */
    fun resetScanState(target: ScanTarget = ScanTarget.ALL_CHATS) {
        scanTarget = target
        currentPhase = DeepScanPhase.ENSURE_CHATS_TAB
        discoveredChats.clear()
        bottomTabCheckAttempts = 0
        groupFilterAttempts = 0
        isGroupsFilterActive = false
        topScrollAttempts = 0
        stepAttempts = 0
        scrollCycles = 0
        lastDiscoveredCount = 0
        consecutiveStagnantCycles = 0
        Log.d(loggerTag, "Reset WhatsAppChatScanner state for full scan (target=$scanTarget, maxCycles=$maxScrollCycles)")
    }

    /**
     * Drives accessibility node inspection across recent conversation lists, harvesting contact entities.
     *
     * @param root Accessibility tree root.
     * @param pkg Target WhatsApp package identifier.
     * @param sendAttemptCount Counter of elapsed retry attempts.
     * @param onRetry Callback triggered to schedule subsequent polling pass.
     * @param onFinish Callback triggered when scan completes and records are saved to SQLite.
     */
    fun tryScanWhatsAppChats(
        root: AccessibilityNodeInfo,
        pkg: String,
        sendAttemptCount: Int,
        onRetry: (delayMs: Long) -> Unit,
        onFinish: () -> Unit,
    ) {
        try {
            activePkg = pkg
            val curPkg = root.packageName?.toString().orEmpty()
            val isTargetPkg = curPkg.contains(pkg, ignoreCase = true) || pkg.contains(curPkg, ignoreCase = true) ||
                ((pkg.contains("whatsapp") || pkg.contains("w4b")) && (curPkg.contains("whatsapp") || curPkg.contains("w4b")))
            if (!isTargetPkg) {
                if (sendAttemptCount <= 20) {
                    onRetry(350L)
                } else {
                    finishAndSave(onFinish)
                }
                return
            }

            val repo = AppRepository(service)

            when (currentPhase) {
                DeepScanPhase.ENSURE_CHATS_TAB -> {
                    bottomTabCheckAttempts++
                    val chatsTab = findChatsBottomTab(root)
                    val nextPhase = if (scanTarget == ScanTarget.GROUPS_ONLY) DeepScanPhase.ENSURE_GROUPS_FILTER else DeepScanPhase.HARVEST_ROWS
                    if (chatsTab != null) {
                        val isSelected = isChipSelected(chatsTab)
                        if (isSelected) {
                            Log.i(loggerTag, "Chats bottom tab is already active")
                            currentPhase = nextPhase
                            onRetry(250L)
                            return
                        } else {
                            val clicked = AccessibilityNodeUtils.clickElement(chatsTab, service)
                            Log.i(loggerTag, "Clicked Chats bottom tab: result=$clicked")
                            if (clicked) {
                                currentPhase = nextPhase
                                onRetry(450L)
                                return
                            }
                        }
                    }

                    if (bottomTabCheckAttempts >= 2) {
                        Log.d(loggerTag, "Chats bottom tab not found or already on conversation list, moving forward")
                        currentPhase = nextPhase
                        onRetry(200L)
                    } else {
                        onRetry(300L)
                    }
                }

                DeepScanPhase.ENSURE_GROUPS_FILTER -> {
                    groupFilterAttempts++

                    // Scroll backward to top of list to ensure scanning starts from the very first group
                    if (topScrollAttempts < 3) {
                        topScrollAttempts++
                        val scrollable = findScrollableList(root)
                        scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                        AccessibilityNodeUtils.performVerticalSwipe(service, startYRatio = 0.35f, endYRatio = 0.85f)
                        Log.i(loggerTag, "ENSURE_GROUPS_FILTER: Swiped backward to top of chat list (pass $topScrollAttempts/3)")
                        onRetry(350L)
                        return
                    }

                    // 1. FAST PATH: Check search bar. If it confirms "Search group chats", Groups filter is active!
                    if (isSearchInGroupFilter(root)) {
                        Log.i(loggerTag, "Search bar confirms group filter active")
                        isGroupsFilterActive = true
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        onRetry(350L)
                        return
                    }

                    // 2. Locate the top Groups filter chip
                    val groupChip = findGroupsFilterChip(root)
                    val isSelected = groupChip != null && isChipSelected(groupChip)

                    if (isSelected) {
                        Log.i(loggerTag, "Groups top filter chip is confirmed selected")
                        isGroupsFilterActive = true
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        onRetry(350L)
                        return
                    }

                    if (groupChip != null) {
                        val clicked = AccessibilityNodeUtils.clickElement(groupChip, service)
                        Log.i(loggerTag, "Clicked Groups filter chip (attempt $groupFilterAttempts): result=$clicked")
                        if (clicked) {
                            // Wait for WhatsApp to reload conversations filtered by Groups
                            onRetry(550L)
                            return
                        }
                    }

                    // 3. If filter chip was not found or click failed, check if list is scrolled down
                    if (groupFilterAttempts <= 2) {
                        val scrollable = findScrollableList(root)
                        scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                        AccessibilityNodeUtils.performVerticalSwipe(service, startYRatio = 0.35f, endYRatio = 0.85f)
                        Log.d(loggerTag, "Swiped backward to reveal filter bar at top of chat list (attempt $groupFilterAttempts)")
                        onRetry(450L)
                        return
                    }

                    if (groupFilterAttempts >= 5) {
                        Log.w(loggerTag, "Groups filter chip could not be activated after $groupFilterAttempts attempts; proceeding with strict row filtering")
                        isGroupsFilterActive = false
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        onRetry(250L)
                    } else {
                        onRetry(350L)
                    }
                }

                DeepScanPhase.HARVEST_ROWS -> {
                    // Dynamically verify if search bar indicates group filtering
                    if (!isGroupsFilterActive && scanTarget == ScanTarget.GROUPS_ONLY) {
                        if (isSearchInGroupFilter(root)) {
                            Log.i(loggerTag, "Dynamically verified group filter active via search bar in HARVEST_ROWS")
                            isGroupsFilterActive = true
                        }
                    }

                    val rowContainers = findAllRowContainers(root)
                    Log.i(loggerTag, "HARVEST_ROWS: found ${rowContainers.size} containers, isGroupsFilterActive=$isGroupsFilterActive")
                    val isBiz = pkg.contains("w4b", ignoreCase = true)
                    val baseChatSrc = if (isBiz) "WA_BUSINESS" else "WA_CHAT"
                    val baseGroupSrc = if (isBiz) "WA_BUSINESS_GROUP" else "WA_GROUP"

                    for (row in rowContainers) {
                        val title = extractTitleFromRow(row)
                        if (title == null) {
                            Log.i(loggerTag, "Row title null, skipping row")
                            continue
                        }
                        val cleanTitle = title.trim().removePrefix("~").trim()
                        val isValid = repo.isValidPickedChatName(cleanTitle)
                        val isIgnored = isIgnoredTitle(cleanTitle)
                        val isGroup = isGroupRow(row, cleanTitle, scanTarget, repo)
                        Log.i(loggerTag, "Candidate '$cleanTitle': isValid=$isValid, isIgnored=$isIgnored, isGroup=$isGroup")
                        if (cleanTitle.isBlank() || !isValid || isIgnored) continue

                        val digits = cleanTitle.filter { it.isDigit() }
                        val letters = cleanTitle.filter { it.isLetter() }
                        val isPurePhone = digits.length >= 7 && letters.isEmpty()

                        if (isGroup) {
                            val norm = "group:${cleanTitle.lowercase().trim()}"
                            val phone = "group:$cleanTitle"
                            if (!discoveredChats.containsKey(norm)) {
                                discoveredChats[norm] = DynamicContactItem(
                                    name = cleanTitle,
                                    phone = phone,
                                    normalizedNumber = norm,
                                    source = baseGroupSrc,
                                    photoUri = "",
                                )
                                Log.i(loggerTag, "Harvested group: '$cleanTitle' -> $phone ($baseGroupSrc)")
                            }
                        } else if (scanTarget == ScanTarget.GROUPS_ONLY) {
                            // When scanning groups only, skip non-group chats
                            continue
                        } else if (isPurePhone) {
                            val norm = PhoneNumberNormalizer.normalize(cleanTitle)
                            if (norm.isNotBlank()) {
                                val contactInfo = repo.findContactByPhone(cleanTitle)
                                val resolvedName = contactInfo?.first?.ifBlank { cleanTitle } ?: cleanTitle
                                val resolvedPhoto = contactInfo?.second?.ifBlank { repo.findPhotoForContactName(resolvedName) } ?: repo.findPhotoForContactName(resolvedName)

                                if (!discoveredChats.containsKey(norm)) {
                                    discoveredChats[norm] = DynamicContactItem(
                                        name = resolvedName,
                                        phone = cleanTitle,
                                        normalizedNumber = norm,
                                        source = baseChatSrc,
                                        photoUri = resolvedPhoto,
                                    )
                                    Log.i(loggerTag, "Harvested unsaved phone chat: '$resolvedName' -> $cleanTitle ($baseChatSrc)")
                                }
                            }
                        } else {
                            val isBroadcast = cleanTitle.contains("broadcast", ignoreCase = true) || cleanTitle.contains("recipients", ignoreCase = true)
                            if (!isBroadcast) {
                                val phoneFromBook = repo.findPhoneForContactName(cleanTitle)
                                val (phoneToUse, norm) = if (!phoneFromBook.isNullOrBlank()) {
                                    phoneFromBook to PhoneNumberNormalizer.normalize(phoneFromBook).ifBlank { "chat:${cleanTitle.lowercase().trim()}" }
                                } else {
                                    "chat:$cleanTitle" to "chat:${cleanTitle.lowercase().trim()}"
                                }
                                val photo = repo.findPhotoForContactName(cleanTitle)

                                if (!discoveredChats.containsKey(norm)) {
                                    discoveredChats[norm] = DynamicContactItem(
                                        name = cleanTitle,
                                        phone = phoneToUse,
                                        normalizedNumber = norm,
                                        source = baseChatSrc,
                                        photoUri = photo,
                                    )
                                    Log.i(loggerTag, "Harvested contact chat: '$cleanTitle' -> $phoneToUse ($baseChatSrc)")
                                }
                            }
                        }
                    }

                    val totalDiscovered = discoveredChats.size
                    if (totalDiscovered > lastDiscoveredCount) {
                        consecutiveStagnantCycles = 0
                        lastDiscoveredCount = totalDiscovered
                        Log.i(loggerTag, "HARVEST_ROWS: Discovered new items! Total now: $totalDiscovered")
                    } else {
                        consecutiveStagnantCycles++
                        Log.i(loggerTag, "HARVEST_ROWS: No new items in cycle $scrollCycles (stagnant=$consecutiveStagnantCycles/$maxStagnantCycles, total=$totalDiscovered)")
                    }

                    if ((scrollCycles < maxScrollCycles) && (consecutiveStagnantCycles < maxStagnantCycles)) {
                        currentPhase = DeepScanPhase.SCROLL_DOWN
                        stepAttempts = 0
                        onRetry(350L)
                    } else {
                        Log.i(loggerTag, "Harvest complete. Total items: ${discoveredChats.size}, cycles: $scrollCycles")
                        finishAndSave(onFinish)
                    }
                }

                DeepScanPhase.SCROLL_DOWN -> {
                    scrollCycles++
                    val scrollableNode = findScrollableList(root)
                    var scrolled = false
                    if (scrollableNode != null) {
                        scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        Log.i(loggerTag, "Scrolled chat list forward via node: result=$scrolled (cycle=$scrollCycles)")
                    }

                    if (!scrolled) {
                        scrolled = AccessibilityNodeUtils.performVerticalSwipe(service, startYRatio = 0.75f, endYRatio = 0.25f)
                        Log.i(loggerTag, "Scrolled chat list via vertical swipe gesture: result=$scrolled (cycle=$scrollCycles)")
                    }

                    if (((scrolled || (scrollCycles < 4))) && (consecutiveStagnantCycles < maxStagnantCycles) && (scrollCycles < maxScrollCycles)) {
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        stepAttempts = 0
                        onRetry(500L)
                    } else {
                        Log.i(loggerTag, "Reached end of list or maximum cycles, finishing scan. Discovered: ${discoveredChats.size}")
                        finishAndSave(onFinish)
                    }
                }

                DeepScanPhase.COMPLETE -> {
                    finishAndSave(onFinish)
                }
            }
        } catch (t: Throwable) {
            Log.e(loggerTag, "Error in tryScanWhatsAppChats", t)
            finishAndSave(onFinish)
        }
    }

    private fun finishAndSave(onFinish: () -> Unit) {
        val repo = AppRepository(service)

        // Purge any residual group records from the dynamic pool
        repo.purgeAllGroupsFromPool()

        val savedChats = if (discoveredChats.isNotEmpty()) {
            val count = repo.upsertDynamicPoolBatch(discoveredChats.values.toList())
            Log.i(loggerTag, "finishAndSave: Ingested $count conversations into dynamic pool")
            count
        } else 0

        val label = if (scanTarget == ScanTarget.GROUPS_ONLY) "groups" else "conversations"
        val msg = "Synced $savedChats WhatsApp $label"
        service.showAppToast(msg, isLong = true)

        TaskAccessibilityService.notifyScanCompleted()

        currentPhase = DeepScanPhase.COMPLETE
        onFinish()

        val appIntent = service.packageManager.getLaunchIntentForPackage(service.packageName)
        if (appIntent != null) {
            appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            service.startActivity(appIntent)
        }
    }

    /**
     * Determines whether a filter chip or bottom tab is currently selected.
     */
    private fun isChipSelected(node: AccessibilityNodeInfo): Boolean {
        if (node.isSelected) return true
        if (AccessibilityCompat.isNodeChecked(node)) return true
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        if (desc.contains("unselected")) return false
        if (desc.contains("selected")) return true
        return false
    }

    /**
     * Finds and selects the bottom navigation "Chats" tab if WhatsApp was opened on Updates, Calls, or Tools.
     */
    private fun findChatsBottomTab(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()

            val isChats = text.equals("Chats", ignoreCase = true) ||
                text.equals("चैट", ignoreCase = true) ||
                desc.equals("Chats", ignoreCase = true) ||
                desc.startsWith("Chats, ", ignoreCase = true) ||
                desc.contains("Chats tab", ignoreCase = true) ||
                id.contains("chats_tab")

            if (isChats) {
                var target: AccessibilityNodeInfo? = node
                var depth = 0
                while (target != null && depth < 4) {
                    if (target.isClickable) return target
                    target = target.parent
                    depth++
                }
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return null
    }

    private fun isSearchInGroupFilter(root: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            if (id.contains("search_bar") || id.contains("search_text") || id.contains("search_input") || id.contains("search_src_text")) {
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                val txt = node.text?.toString()?.lowercase().orEmpty()
                if (desc.contains("group") || txt.contains("group")) {
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    private fun findScrollableList(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var fallbackScrollable: AccessibilityNodeInfo? = null

        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val cls = node.className?.toString().orEmpty()

            val isNonChatList = id.contains("updates") || id.contains("status") ||
                id.contains("call") || id.contains("channel") || id.contains("newsletter") ||
                id.contains("community")

            val isHorizontalPager = id.contains("pager") || cls.contains("ViewPager")

            if (!isHorizontalPager && !isNonChatList) {
                val isListClass = cls.contains("RecyclerView") || cls.contains("ListView") || node.isScrollable
                if ((id == "android:id/list" || id.endsWith("/list")) && isListClass) {
                    Log.i(loggerTag, "findScrollableList: matched exact conversation list '$id' ($cls)")
                    return node
                }
                if (isListClass && !id.contains("host") && !id.contains("parent") && fallbackScrollable == null) {
                    fallbackScrollable = node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        Log.i(loggerTag, "findScrollableList: fallback to '${fallbackScrollable?.viewIdResourceName}' (${fallbackScrollable?.className})")
        return fallbackScrollable
    }

    private fun isFilterBarNode(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        if (desc.contains("inbox filters") || desc.contains("radio group") || desc.contains("swipe down to reveal")) {
            return true
        }
        val id = node.viewIdResourceName?.lowercase().orEmpty()
        if (id.contains("filter_debug_view") || (id.contains("filter") && id.contains("group"))) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childId = child.viewIdResourceName?.lowercase().orEmpty()
            val childDesc = child.contentDescription?.toString()?.lowercase().orEmpty()
            if (childId.contains("filter_debug_view") || childDesc.contains("inbox filters") || childDesc.contains("radio group")) {
                return true
            }
        }
        return false
    }

    private fun findAllRowContainers(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val containers = mutableListOf<AccessibilityNodeInfo>()
        val scrollable = findScrollableList(root)
        Log.i(loggerTag, "findAllRowContainers: scrollable=${scrollable?.viewIdResourceName ?: scrollable?.className}, childCount=${scrollable?.childCount}")

        fun isValidRow(node: AccessibilityNodeInfo): Boolean {
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            if (id.contains("status_row") || id.contains("newsletter") || id.contains("explore_more") ||
                id.contains("updates") || id.contains("channels") || id.contains("footer")
            ) {
                Log.i(loggerTag, "isValidRow: rejected because id is non-chat/footer: $id")
                return false
            }
            if (isFilterBarNode(node)) {
                Log.i(loggerTag, "isValidRow: rejected by isFilterBarNode for id=$id")
                return false
            }
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val height = bounds.height()
            if (height !in 70..550 || bounds.bottom <= 150) {
                Log.i(loggerTag, "isValidRow: rejected by bounds height=$height, bottom=${bounds.bottom} for id=${node.viewIdResourceName}")
                return false
            }
            val title = extractTitleFromRow(node)
            if (title == null) {
                Log.i(loggerTag, "isValidRow: rejected because extractTitleFromRow was null for id=${node.viewIdResourceName}")
                return false
            }
            return true
        }

        // 1. Primary: Direct children of the scrollable conversation list
        if (scrollable != null && scrollable.childCount > 0) {
            for (i in 0 until scrollable.childCount) {
                val child = scrollable.getChild(i) ?: continue
                if (isValidRow(child)) {
                    containers.add(child)
                }
            }
            if (containers.isNotEmpty()) {
                Log.i(loggerTag, "findAllRowContainers: returning ${containers.size} primary children from scrollable")
                return containers
            }
        }

        // 2. Fallback: search known ID endings
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            if (id.endsWith("contact_row_container") ||
                id.endsWith("conversations_row_layout") ||
                id.endsWith("contactpicker_row") ||
                id.endsWith("parent_layout") ||
                id.endsWith("chat_row")
            ) {
                if (isValidRow(node)) {
                    containers.add(node)
                }
            } else {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { q.add(it) }
                }
            }
        }

        Log.i(loggerTag, "findAllRowContainers: returning ${containers.size} fallback containers")
        return containers
    }

    private fun extractTitleFromRow(row: AccessibilityNodeInfo): String? {
        // 1. Primary: Direct lookup for known title/contact name resource IDs
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val txt = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()

            val isTitleId = id.endsWith("conversations_row_contact_name") ||
                id.endsWith("conversation_contact_name") ||
                id.endsWith("conversations_row_title") ||
                id.endsWith("conversation_title") ||
                id.endsWith("chat_title") ||
                id.endsWith("contactpicker_row_name") ||
                id.endsWith("chat_name") ||
                id.endsWith("group_name") ||
                id.endsWith("group_title")

            if (isTitleId) {
                val candidate = (if (txt.isNotBlank()) txt else desc).removePrefix("~").trim()
                if (candidate.isNotBlank() && !isIgnoredTitle(candidate)) {
                    return candidate
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        // 2. Secondary: Avatar contact_photo contentDescription
        q.clear()
        q.add(row)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()

            if (id.endsWith("contact_photo") || id.endsWith("avatar") || id.endsWith("photo") || id.endsWith("icon")) {
                val candidate = extractNameFromAvatarDesc(desc)
                if (!candidate.isNullOrBlank() && !isIgnoredTitle(candidate)) {
                    return candidate
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        // 3. Tertiary: Geometric analysis of text candidates in row
        // Contact / Group titles are ALWAYS located in the upper vertical half of the conversation row
        // and left of the time/date column. Message snippets are located in the lower vertical half.
        val rowBounds = Rect()
        row.getBoundsInScreen(rowBounds)
        val rowCenterY = if (rowBounds.height() > 0) rowBounds.centerY() else 0
        val rowRightThreshold = if (rowBounds.width() > 0) rowBounds.left + (rowBounds.width() * 0.78f).toInt() else Int.MAX_VALUE

        q.clear()
        q.add(row)
        val topHalfCandidates = mutableListOf<Pair<String, Int>>() // Pair(text, topCoordinate)
        val fallbackCandidates = mutableListOf<String>()
        val timeRegex = Regex("""(?i)^\d{1,2}:\d{2}(\s?[ap]m)?$|^yesterday$|^today$|^\d{1,2}/\d{1,2}/\d{2,4}$""")

        val childBounds = Rect()
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val txt = node.text?.toString()?.trim().orEmpty()

            val isMsgOrTime = id.endsWith("single_msg_tv") ||
                id.endsWith("msg_from_tv") ||
                id.endsWith("conversations_row_date") ||
                id.endsWith("unread_divider_tv") ||
                id.endsWith("date")

            if (!isMsgOrTime && txt.isNotBlank()) {
                val clean = txt.removePrefix("~").trim()
                if (clean.length in 2..150 && !timeRegex.matches(clean) && !isIgnoredTitle(clean)) {
                    node.getBoundsInScreen(childBounds)
                    if (rowCenterY > 0 && childBounds.centerY() <= rowCenterY && childBounds.left < rowRightThreshold) {
                        topHalfCandidates.add(clean to childBounds.top)
                    } else {
                        fallbackCandidates.add(clean)
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        val sortedTop = topHalfCandidates.sortedBy { it.second }.map { it.first }
        if (sortedTop.isNotEmpty()) {
            return sortedTop.first()
        }

        return fallbackCandidates.firstOrNull()
    }

    private fun extractNameFromAvatarDesc(desc: String): String? {
        val trimmed = desc.trim()
        if (trimmed.isBlank()) return null

        for (suffix in listOf(
            " picture", " Picture", " photo", " Photo",
            " group icon", " Group icon", " group photo", " Group photo",
            " group picture", " Group picture", " icon", " Icon"
        )) {
            if (trimmed.endsWith(suffix, ignoreCase = true)) {
                return trimmed.substring(0, trimmed.length - suffix.length).trim().removePrefix("~").trim()
            }
        }
        if (trimmed.contains(" picture, ", ignoreCase = true)) {
            val before = trimmed.substringBefore(" picture, ", "").trim().removePrefix("~").trim()
            if (before.isNotBlank()) return before
        }
        if (trimmed.contains(" photo, ", ignoreCase = true)) {
            val before = trimmed.substringBefore(" photo, ", "").trim().removePrefix("~").trim()
            if (before.isNotBlank()) return before
        }
        if (trimmed.contains(" icon, ", ignoreCase = true)) {
            val before = trimmed.substringBefore(" icon, ", "").trim().removePrefix("~").trim()
            if (before.isNotBlank()) return before
        }
        if (trimmed.contains("'s status", ignoreCase = true)) {
            val before = trimmed.substringBefore("'s status", "").trim().removePrefix("~").trim()
            if (before.isNotBlank()) return before
        }
        for (prefix in listOf(
            "Profile picture of ",
            "Profile photo of ",
            "Profile photo, ",
            "Group icon of ",
            "Group photo of ",
            "Group picture of ",
            "Double tap to view profile photo of ",
            "Double tap to view group icon of ",
            "Double tap to view group photo of ",
            "Double tap to view photo of "
        )) {
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                return trimmed.substring(prefix.length).trim().removePrefix("~").trim()
            }
        }
        return null
    }

    private fun isIgnoredTitle(title: String): Boolean {
        val lower = title.trim().lowercase()
        val blocked = listOf(
            "photo", "video", "audio", "document", "sticker", "gif", "contact", "contacts", "location",
            "voice call", "video call", "missed call", "no answer",
            "inbox filters", "radio group", "all filter", "groups filter", "unread filter",
            "favorites filter", "new customer filter", "new order filter",
            "hidden updates", "find channels to follow", "explore more", "create channel",
            "recent updates", "add status", "muted updates", "view updates", "channels",
            "ask meta ai or search", "search group chats", "search chats", "search…", "search",
            "manage groups", "manage group", "recent searches", "clear all", "new group",
            "new community", "archived", "starred messages", "disappearing messages",
            "chats", "updates", "communities", "calls", "tools", "filter"
        )
        return blocked.any { lower == it || lower.startsWith("$it ") || lower.startsWith("$it…") || lower.startsWith("$it...") }
    }

    private fun extractSubtitleFromRow(row: AccessibilityNodeInfo): String {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val txt = node.text?.toString()?.trim().orEmpty()

            if (id.endsWith("msg_from_status") || id.endsWith("last_message") || id.endsWith("subtitle") || id.endsWith("snippet") || id.endsWith("single_msg_tv")) {
                if (txt.isNotBlank()) return txt
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return ""
    }

    private fun hasMessageAuthorView(row: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val text = node.text?.toString()?.trim().orEmpty()
            // Strictly check author view IDs (do NOT check msg_from_status which is the delivery status tick!)
            if (id.endsWith("msg_from_tv") || id.endsWith("sender_name") || id.endsWith("message_sender")) {
                if (text.isNotBlank() && !text.equals("You:", ignoreCase = true) && !text.equals("You", ignoreCase = true)) {
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    private fun findGroupsFilterChip(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        val bounds = Rect()

        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val cls = node.className?.toString().orEmpty()

            node.getBoundsInScreen(bounds)

            // Top area filter chips reside in the upper section of the conversation screen (bounds.top in 100..950)
            val isTopArea = bounds.top in 100..950 && bounds.bottom in 200..1200

            // Strictly exclude conversation rows, contact photos, and message bubbles
            val isChatOrAvatar = id.contains("contact_photo") || id.contains("avatar") ||
                id.contains("contact_row") || id.contains("conversations_row") ||
                desc.contains("picture", ignoreCase = true) || desc.contains("photo", ignoreCase = true) ||
                desc.contains("status", ignoreCase = true)

            if (isTopArea && !isChatOrAvatar) {
                val isGroupChip = id.contains("conversations_filter_debug_view_id_groups") ||
                    id.contains("id_groups") ||
                    id.contains("filter_groups") ||
                    id.contains("groups_filter") ||
                    id.contains("groups_chip") ||
                    desc.startsWith("Groups filter", ignoreCase = true) ||
                    desc.contains("Groups filter", ignoreCase = true) ||
                    ((text.equals("Groups", ignoreCase = true) || text.equals("ग्रुप", ignoreCase = true) ||
                      desc.equals("Groups", ignoreCase = true) || desc.equals("ग्रुप", ignoreCase = true)) &&
                     (node.isClickable || node.isCheckable || cls.contains("RadioButton") || cls.contains("Button") || id.contains("filter")))

                if (isGroupChip) {
                    var target: AccessibilityNodeInfo? = node
                    var depth = 0
                    while (target != null && depth < 4) {
                        if (target.isClickable) return target
                        target = target.parent
                        depth++
                    }
                    return node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return null
    }

    private fun isGroupRow(
        row: AccessibilityNodeInfo,
        cleanTitle: String,
        target: ScanTarget,
        repo: AppRepository
    ): Boolean {
        if (target == ScanTarget.DIRECT_CHATS_ONLY) {
            Log.i(loggerTag, "isGroupRow('$cleanTitle'): false (target == DIRECT_CHATS_ONLY)")
            return false
        }

        // 1. If title is an ignored/blocked UI keyword, reject
        if (isIgnoredTitle(cleanTitle) || !repo.isValidPickedChatName(cleanTitle)) {
            Log.i(loggerTag, "isGroupRow('$cleanTitle'): false (isIgnoredTitle=${isIgnoredTitle(cleanTitle)}, isValid=${repo.isValidPickedChatName(cleanTitle)})")
            return false
        }

        // 2. If Groups filter is confirmed active on the chat screen, every valid conversation row is a group
        if (isGroupsFilterActive) {
            Log.i(loggerTag, "isGroupRow('$cleanTitle'): true (isGroupsFilterActive=true)")
            return true
        }

        // 3. If title matches a saved address book contact with a real cellular phone number, it is ALWAYS a direct contact, never a group!
        val hasCellular = repo.hasCellularPhoneContact(cleanTitle)
        if (hasCellular) {
            Log.i(loggerTag, "isGroupRow('$cleanTitle'): false (hasCellularPhoneContact=true)")
            return false
        }

        // 4. If the row explicitly has msg_from_tv (e.g. "VINAY: ", "You: ", "~bajrang: "), it's a group
        if (hasMessageAuthorView(row)) {
            Log.i(loggerTag, "isGroupRow('$cleanTitle'): true (hasMessageAuthorView=true)")
            return true
        }

        val rowDesc = row.contentDescription?.toString()?.lowercase().orEmpty()
        if (rowDesc.contains("group") || rowDesc.contains("ग्रुप")) {
            Log.i(loggerTag, "isGroupRow('$cleanTitle'): true (rowDesc contains group)")
            return true
        }

        val sub = extractSubtitleFromRow(row).trim()
        if (sub.isNotBlank()) {
            val lowerSub = sub.lowercase()
            if (lowerSub.contains("created group") ||
                lowerSub.contains("added you") ||
                lowerSub.contains("joined using") ||
                lowerSub.contains("group call") ||
                lowerSub.contains("left group") ||
                lowerSub.contains("changed the subject") ||
                lowerSub.contains("changed the group")
            ) {
                Log.i(loggerTag, "isGroupRow('$cleanTitle'): true (subtitle contains group event)")
                return true
            }

            // Exclude URLs and protocol strings from colon-based author checks
            if (!sub.contains("://")) {
                val colonIdx = sub.indexOf(':')
                if (colonIdx in 2..30) {
                    val prefix = sub.substring(0, colonIdx).trim()
                    val lowerPrefix = prefix.lowercase()
                    val nonAuthorPrefixes = setOf(
                        "you", "आप", "re", "fw", "fwd", "note", "date", "time", "order", "otp", "call", "ref", "http", "https"
                    )
                    if (lowerPrefix !in nonAuthorPrefixes && prefix.any { it.isLetter() }) {
                        Log.i(loggerTag, "isGroupRow('$cleanTitle'): true (subtitle has colon author '$prefix')")
                        return true
                    }
                }
            }
        }

        val result = target == ScanTarget.GROUPS_ONLY && isGroupsFilterActive
        Log.i(loggerTag, "isGroupRow('$cleanTitle'): fallback result=$result")
        return result
    }
}
