/**
 * Package: com.jmd.superapp.whatsapp
 * Module: WhatsApp Dispatch Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [WhatsAppChatScanner].
 *
 * What it does:
 * Discovery engine for harvesting WhatsApp direct chats and groups via accessibility node traversal, scrolling, and pool enrichment.
 *
 * Linked With:
 * - Repositories: [com.jmd.superapp.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.superapp.data.model.DynamicContactItem]
 * - Services & Engines: [android.accessibilityservice.AccessibilityService], [com.jmd.superapp.service.TaskAccessibilityService], [com.jmd.superapp.whatsapp.AccessibilityNodeUtils.recycleSafely]
 * - Core Security & Utilities: [android.util.Log], [com.jmd.superapp.compat.AccessibilityCompat], [com.jmd.superapp.core.PhoneNumberNormalizer], [com.jmd.superapp.core.showAppToast]
 * - Android Framework & Jetpack: [android.graphics.Rect], [android.view.accessibility.AccessibilityNodeInfo]
 */

package com.jmd.superapp.whatsapp

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jmd.superapp.compat.AccessibilityCompat
import com.jmd.superapp.core.PhoneNumberNormalizer
import com.jmd.superapp.core.showAppToast
import com.jmd.superapp.data.model.DynamicContactItem
import com.jmd.superapp.data.repo.AppRepository
import com.jmd.superapp.service.TaskAccessibilityService
import com.jmd.superapp.whatsapp.AccessibilityNodeUtils.recycleSafely

/**
 * What it is:
 * Component definition for [ScanTarget].
 *
 * What it does:
 * Provides domain logic and lifecycle management for ScanTarget.
 *
 * Linked with:
 * - Architecture: [WhatsAppChatScanner], [ScanTarget]
 */
enum class ScanTarget {
    ALL_CHATS,
    GROUPS_ONLY,
    DIRECT_CHATS_ONLY
}

/** Classification outcome for one conversation row. UNRESOLVED rows are logged, never stored. */
private enum class RowKind { GROUP, DIRECT, UNRESOLVED }

/**
 * What it is:
 * Accessibility-based scanner engine that directly iterates WhatsApp and WhatsApp Business conversation lists, discovers individual contacts and chats, and saves them into the local dynamic pool.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [WhatsAppChatScanner].
 *
 * Linked with:
 * - Architecture: [WhatsAppChatScanner], [WhatsAppChatScanner]
 */
class WhatsAppChatScanner(
    private val service: AccessibilityService,
    private val loggerTag: String = "WAChatScanner",
) {

    /** Backward compatibility alias for nested enum references. */
    typealias ScanTarget = com.jmd.superapp.whatsapp.ScanTarget
    /**
     * What it is:
     * Component definition for [DeepScanPhase].
     *
     * What it does:
     * Provides domain logic and lifecycle management for DeepScanPhase.
     *
     * Linked with:
     * - Component: [WhatsAppChatScanner]
     */

    private enum class DeepScanPhase {
        ENSURE_CHATS_TAB,
        HARVEST_GROUPS,
        SWITCH_TO_ALL,
        HARVEST_ROWS,
        SCROLL_DOWN,
        COMPLETE
    }

    var scanTarget: ScanTarget = ScanTarget.ALL_CHATS
    private var activePkg: String = "com.whatsapp"
    private var currentPhase = DeepScanPhase.ENSURE_CHATS_TAB
    private val discoveredChats = linkedMapOf<String, DynamicContactItem>()
    private val discoveredGroups = mutableSetOf<String>()
    private val seenRowTitlesInScan = mutableSetOf<String>()
    private var bottomTabCheckAttempts = 0
    private var stepAttempts = 0
    private var scrollCycles = 0
    // Hard safety cap; the scan normally ends on the verified end-of-list condition.
    private val maxScrollCycles = 250
    private var lastDiscoveredCount = 0
    private var consecutiveStagnantCycles = 0
    private val maxStagnantCycles = 5
    private var reachedListEnd = false
    private var endConfirmations = 0
    private var initialLoadRetries = 0
    private var unresolvedRowCount = 0
    private var finishReason = ""

    /**
     * What it is:
     * Clears discovery cache and resets traversal state for a fresh chat scanning pass.
     *
     * What it does:
     * Executes [resetScanState] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsAppChatScanner], [resetScanState]
     */
    fun resetScanState(target: ScanTarget = ScanTarget.ALL_CHATS) {
        scanTarget = target
        currentPhase = DeepScanPhase.ENSURE_CHATS_TAB
        discoveredChats.clear()
        discoveredGroups.clear()
        seenRowTitlesInScan.clear()
        bottomTabCheckAttempts = 0
        stepAttempts = 0
        scrollCycles = 0
        lastDiscoveredCount = 0
        consecutiveStagnantCycles = 0
        reachedListEnd = false
        endConfirmations = 0
        initialLoadRetries = 0
        unresolvedRowCount = 0
        finishReason = ""
        Log.d(loggerTag, "Reset WhatsAppChatScanner state for single-pass scan (target=$scanTarget, maxCycles=$maxScrollCycles)")
    }

    /**
     * What it is:
     * Drives accessibility node inspection across recent conversation lists, harvesting contact entities.
     *
     * What it does:
     * Executes [tryScanWhatsAppChats] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsAppChatScanner], [tryScanWhatsAppChats]
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
                    onRetry(200L)
                } else {
                    finishAndSave(onFinish)
                }
                return
            }

            val repo = AppRepository(service)

            when (currentPhase) {
                DeepScanPhase.ENSURE_CHATS_TAB -> {
                    bottomTabCheckAttempts++

                    // 1. If inside Search mode, exit search mode first
                    if (isSearchModeActive(root)) {
                        Log.i(loggerTag, "ENSURE_CHATS_TAB: Detected active search screen. Dismissing search with Back...")
                        val backBtn = findSearchBackButton(root)
                        val clicked = if (backBtn != null) AccessibilityNodeUtils.clickElement(backBtn, service) else false
                        if (!clicked) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        }
                        onRetry(350L)
                        return
                    }

                    // 2. If opened inside a conversation rather than the conversation list, press Back
                    if (isInsideConversation(root)) {
                        Log.i(loggerTag, "ENSURE_CHATS_TAB: Inside a chat conversation. Pressing Back to return to chat list...")
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        onRetry(400L)
                        return
                    }

                    val chatsTab = findChatsBottomTab(root)
                    if (chatsTab != null && !isChipSelected(chatsTab)) {
                        val clicked = AccessibilityNodeUtils.clickElement(chatsTab, service)
                        Log.i(loggerTag, "Clicked Chats bottom tab: result=$clicked")
                        if (clicked) {
                            onRetry(200L)
                            return
                        }
                    }

                    // Check if Groups filter chip exists and scanTarget is not DIRECT_CHATS_ONLY
                    val groupsChip = findGroupsFilterChip(root)
                    if (groupsChip != null && scanTarget != ScanTarget.DIRECT_CHATS_ONLY) {
                        Log.i(loggerTag, "ENSURE_CHATS_TAB: Found Groups filter chip. Initiating Stage 1: HARVEST_GROUPS")
                        val clicked = AccessibilityNodeUtils.clickElement(groupsChip, service)
                        Log.i(loggerTag, "Clicked Groups filter chip: result=$clicked")
                        currentPhase = DeepScanPhase.HARVEST_GROUPS
                        stepAttempts = 0
                        consecutiveStagnantCycles = 0
                        onRetry(200L)
                        return
                    }

                    // Otherwise ensure All filter chip or proceed to HARVEST_ROWS
                    val allChip = findAllFilterChip(root)
                    if (allChip != null && !isChipSelected(allChip)) {
                        AccessibilityNodeUtils.clickElement(allChip, service)
                    }
                    currentPhase = DeepScanPhase.HARVEST_ROWS
                    stepAttempts = 0
                    consecutiveStagnantCycles = 0
                    onRetry(140L)
                }

                DeepScanPhase.HARVEST_GROUPS -> {
                    // Verify that Groups filter chip is actively selected before harvesting rows
                    val groupsChip = findGroupsFilterChip(root)
                    if (groupsChip != null && !isChipSelected(groupsChip)) {
                        Log.i(loggerTag, "HARVEST_GROUPS: Groups filter chip is not yet active. Clicking chip...")
                        AccessibilityNodeUtils.clickElement(groupsChip, service)
                        onRetry(250L)
                        return
                    }

                    val rowContainers = findAllRowContainers(root)
                    val isBiz = pkg.contains("w4b", ignoreCase = true)
                    val baseGroupSrc = if (isBiz) "WA_BUSINESS_GROUP" else "WA_GROUP"
                    var anyNewRowSeenThisPass = false

                    for (row in rowContainers) {
                        val title = extractTitleFromRow(row) ?: continue
                        val cleanTitle = title.trim().removePrefix("~").trim()
                        if (cleanTitle.isBlank() || !repo.isValidPickedChatName(cleanTitle) || isIgnoredTitle(cleanTitle)) continue

                        // Reject individual contacts: direct avatar photo, pure phone number, or device address book entry
                        if (hasDirectAvatarDesc(row)) {
                            Log.i(loggerTag, "HARVEST_GROUPS: Skipping '$cleanTitle' (direct profile photo detected)")
                            continue
                        }
                        val digits = cleanTitle.filter { it.isDigit() }
                        val letters = cleanTitle.filter { it.isLetter() }
                        if (digits.length >= 7 && letters.isEmpty()) continue
                        if (repo.hasCellularPhoneContact(cleanTitle) || repo.findPhoneForContactName(cleanTitle) != null) continue

                        if (seenRowTitlesInScan.add(cleanTitle.lowercase().trim())) {
                            anyNewRowSeenThisPass = true
                        }

                        val norm = if (isBiz) "group:w4b:${cleanTitle.lowercase().trim()}" else "group:wa:${cleanTitle.lowercase().trim()}"
                        val phone = if (isBiz) "group:w4b:$cleanTitle" else "group:wa:$cleanTitle"
                        discoveredGroups.add(cleanTitle.lowercase().trim())
                        if (!discoveredChats.containsKey(norm)) {
                            discoveredChats[norm] = DynamicContactItem(
                                name = cleanTitle,
                                phone = phone,
                                normalizedNumber = norm,
                                source = baseGroupSrc,
                                photoUri = "",
                            )
                            Log.i(loggerTag, "Harvested verified group: '$cleanTitle' -> $phone")
                        }
                    }

                    val totalDiscovered = discoveredChats.size
                    val discoveredNew = (totalDiscovered > lastDiscoveredCount) || anyNewRowSeenThisPass
                    if (discoveredNew) {
                        consecutiveStagnantCycles = 0
                        lastDiscoveredCount = totalDiscovered
                    } else {
                        consecutiveStagnantCycles++
                    }

                    val scrollableNode = findScrollableList(root)
                    var scrolled = false
                    if (scrollableNode != null) {
                        scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    }
                    if (!scrolled) {
                        scrolled = AccessibilityNodeUtils.performVerticalSwipe(service, startYRatio = 0.82f, endYRatio = 0.20f)
                    }

                    val canScrollFurther = scrollableNode?.actionList?.any {
                        it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    } ?: scrolled

                    val endOfGroups = (!canScrollFurther && !discoveredNew) || consecutiveStagnantCycles >= 2 || scrollCycles >= 30

                    if (!endOfGroups && scrolled) {
                        scrollCycles++
                        onRetry(120L)
                    } else {
                        Log.i(loggerTag, "Groups stage finished: discovered ${discoveredGroups.size} verified groups")
                        if (scanTarget == ScanTarget.GROUPS_ONLY) {
                            finishAndSave(onFinish)
                        } else {
                            currentPhase = DeepScanPhase.SWITCH_TO_ALL
                            scrollCycles = 0
                            consecutiveStagnantCycles = 0
                            reachedListEnd = false
                            endConfirmations = 0
                            onRetry(150L)
                        }
                    }
                }

                DeepScanPhase.SWITCH_TO_ALL -> {
                    val allChip = findAllFilterChip(root)
                    var switched = false
                    if (allChip != null) {
                        switched = AccessibilityNodeUtils.clickElement(allChip, service)
                        Log.i(loggerTag, "SWITCH_TO_ALL: Clicked All filter chip: result=$switched")
                    }
                    if (!switched) {
                        val chatsTab = findChatsBottomTab(root)
                        if (chatsTab != null) {
                            switched = AccessibilityNodeUtils.clickElement(chatsTab, service)
                            Log.i(loggerTag, "SWITCH_TO_ALL: Clicked Chats bottom tab to reset scroll: result=$switched")
                        }
                    }

                    currentPhase = DeepScanPhase.HARVEST_ROWS
                    scrollCycles = 0
                    consecutiveStagnantCycles = 0
                    lastDiscoveredCount = discoveredChats.size
                    reachedListEnd = false
                    endConfirmations = 0
                    onRetry(200L)
                }

                DeepScanPhase.HARVEST_ROWS -> {
                    val rowContainers = findAllRowContainers(root)
                    val isBiz = pkg.contains("w4b", ignoreCase = true)
                    val baseChatSrc = if (isBiz) "WA_BUSINESS" else "WA_CHAT"
                    val baseGroupSrc = if (isBiz) "WA_BUSINESS_GROUP" else "WA_GROUP"
                    var anyNewRowSeenThisPass = false

                    for (row in rowContainers) {
                        val title = extractTitleFromRow(row) ?: continue
                        val cleanTitle = title.trim().removePrefix("~").trim()
                        if (cleanTitle.isBlank() || !repo.isValidPickedChatName(cleanTitle) || isIgnoredTitle(cleanTitle)) continue

                        if (seenRowTitlesInScan.add(cleanTitle.lowercase().trim())) {
                            anyNewRowSeenThisPass = true
                        }

                        val kind = classifyRow(row, cleanTitle, scanTarget, repo)
                        if (kind == RowKind.UNRESOLVED) {
                            unresolvedRowCount++
                            continue
                        }
                        val isGroup = kind == RowKind.GROUP

                        val digits = cleanTitle.filter { it.isDigit() }
                        val letters = cleanTitle.filter { it.isLetter() }
                        val isPurePhone = digits.length >= 7 && letters.isEmpty()

                        if (isGroup) {
                            val norm = if (isBiz) "group:w4b:${cleanTitle.lowercase().trim()}" else "group:wa:${cleanTitle.lowercase().trim()}"
                            val phone = if (isBiz) "group:w4b:$cleanTitle" else "group:wa:$cleanTitle"
                            discoveredGroups.add(cleanTitle.lowercase().trim())
                            if (!discoveredChats.containsKey(norm)) {
                                discoveredChats[norm] = DynamicContactItem(
                                    name = cleanTitle,
                                    phone = phone,
                                    normalizedNumber = norm,
                                    source = baseGroupSrc,
                                    photoUri = "",
                                )
                                Log.i(loggerTag, "Harvested group: '$cleanTitle' -> $phone")
                            }
                        } else if (scanTarget == ScanTarget.GROUPS_ONLY) {
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
                                    Log.i(loggerTag, "Harvested unsaved direct chat: '$resolvedName' -> $cleanTitle")
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
                                    Log.i(loggerTag, "Harvested contact direct chat: '$cleanTitle' -> $phoneToUse")
                                }
                            }
                        }
                    }

                    val totalDiscovered = discoveredChats.size
                    val discoveredNew = (totalDiscovered > lastDiscoveredCount) || anyNewRowSeenThisPass
                    if (discoveredNew) {
                        consecutiveStagnantCycles = 0
                        lastDiscoveredCount = totalDiscovered
                    } else {
                        consecutiveStagnantCycles++
                    }

                    finishReason = when {
                        reachedListEnd && !discoveredNew -> "END_OF_LIST: no new unique rows and last row visible"
                        scrollCycles >= maxScrollCycles -> "HARD_CAP: reached $maxScrollCycles scroll passes"
                        consecutiveStagnantCycles >= maxStagnantCycles -> "STAGNANT: $maxStagnantCycles passes without new rows"
                        else -> ""
                    }
                    if (finishReason.isEmpty()) {
                        currentPhase = DeepScanPhase.SCROLL_DOWN
                        stepAttempts = 0
                        onRetry(140L)
                    } else {
                        Log.i(loggerTag, "Harvest complete ($finishReason): items=${discoveredChats.size}, unresolved=$unresolvedRowCount, cycles=$scrollCycles")
                        finishAndSave(onFinish)
                    }
                }

                DeepScanPhase.SCROLL_DOWN -> {
                    scrollCycles++
                    val scrollableNode = findScrollableList(root)
                    var scrolled = false
                    if (scrollableNode != null) {
                        scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    }

                    if (!scrolled) {
                        scrolled = AccessibilityNodeUtils.performVerticalSwipe(service, startYRatio = 0.82f, endYRatio = 0.20f)
                    }

                    val canScrollFurther = scrollableNode?.actionList?.any {
                        it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    } ?: scrolled
                    val atEnd = !canScrollFurther
                    if (atEnd) {
                        endConfirmations++
                        if (discoveredChats.size < 5 && initialLoadRetries < 3) {
                            initialLoadRetries++
                            currentPhase = DeepScanPhase.HARVEST_ROWS
                            stepAttempts = 0
                            onRetry(350L)
                            return
                        }
                        if (endConfirmations >= 2) {
                            reachedListEnd = true
                        }
                    } else {
                        endConfirmations = 0
                    }

                    if (!reachedListEnd && scrollCycles < maxScrollCycles && consecutiveStagnantCycles < maxStagnantCycles) {
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        stepAttempts = 0
                        onRetry(140L)
                    } else {
                        if (finishReason.isEmpty()) {
                            finishReason = if (reachedListEnd) "END_OF_LIST: no new unique rows and last row visible"
                            else "HARD_CAP: reached $maxScrollCycles scroll passes"
                        }
                        Log.i(loggerTag, "Finishing scan ($finishReason). Discovered: ${discoveredChats.size}")
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
    /**
     * What it is:
     * Execution routine for [finishAndSave].
     *
     * What it does:
     * Coordinates finishAndSave operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppChatScanner]
     */

    private fun finishAndSave(onFinish: () -> Unit) {
        val repo = AppRepository(service)

        // Clean up corrupted group entries before batch upsert
        repo.purgeCorruptedGroupsFromPool()
        if (scanTarget == ScanTarget.GROUPS_ONLY) {
            val targetGroupSource = if (activePkg.contains("w4b", ignoreCase = true)) "WA_BUSINESS_GROUP" else "WA_GROUP"
            if (discoveredChats.isNotEmpty()) {
                repo.deleteAllWhatsAppGroups(targetGroupSource)
            }
        }

        val savedChats = if (discoveredChats.isNotEmpty()) {
            val count = repo.upsertDynamicPoolBatch(discoveredChats.values.toList())
            Log.i(loggerTag, "finishAndSave: Ingested $count conversations into dynamic pool")
            count
        } else 0

        Log.i(loggerTag, "finishAndSave: reason=${finishReason.ifBlank { "UNKNOWN" }}, saved=$savedChats, " +
            "discovered=${discoveredChats.size}, unresolved=$unresolvedRowCount")
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
     * What it is:
     * Determines whether a filter chip or bottom tab is currently selected.
     *
     * What it does:
     * Executes [isChipSelected] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsAppChatScanner], [isChipSelected]
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
     * What it is:
     * Finds and selects the bottom navigation "Chats" tab if WhatsApp was opened on Updates, Calls, or Tools.
     *
     * What it does:
     * Executes [findChatsBottomTab] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsAppChatScanner], [findChatsBottomTab]
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
    /**
     * What it is:
     * Execution routine for [isSearchInGroupFilter].
     *
     * What it does:
     * Coordinates isSearchInGroupFilter operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppChatScanner]
     */

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
    /**
     * What it is:
     * Execution routine for [findScrollableList].
     *
     * What it does:
     * Coordinates findScrollableList operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppChatScanner]
     */

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
    /**
     * What it is:
     * Execution routine for [isFilterBarNode].
     *
     * What it does:
     * Coordinates isFilterBarNode operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppChatScanner]
     */

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
    /**
     * What it is:
     * Execution routine for [findAllRowContainers].
     *
     * What it does:
     * Coordinates findAllRowContainers operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppChatScanner]
     */

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
    /**
     * What it is:
     * Execution routine for [extractTitleFromRow].
     *
     * What it does:
     * Coordinates extractTitleFromRow operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppChatScanner]
     */

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
    /**
     * What it is:
     * Execution routine for [extractNameFromAvatarDesc].
     *
     * What it does:
     * Coordinates extractNameFromAvatarDesc operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppChatScanner]
     */

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
    /**
     * What it is:
     * Execution routine for [isIgnoredTitle].
     *
     * What it does:
     * Coordinates isIgnoredTitle operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppChatScanner]
     */

    private fun isIgnoredTitle(title: String): Boolean {
        val lower = title.trim().lowercase()
        val blocked = listOf(
            "photo", "video", "audio", "document", "sticker", "gif", "contact", "contacts", "location",
            "voice call", "video call", "missed call", "no answer",
            "inbox filters", "radio group", "all filter", "groups filter", "unread filter",
            "favorites filter", "new customer filter", "new order filter",
            "follow-up", "follow up", "todo", "to-do", "new customer", "new order", "pending payment", "paid", "order complete",
            "maharashtra cyber", "maharashtra cyber - home department government of maharashtra",
            "hidden updates", "find channels to follow", "explore more", "create channel",
            "recent updates", "add status", "muted updates", "view updates", "channels",
            "ask meta ai or search", "search group chats", "search chats", "search…", "search",
            "manage groups", "manage group", "recent searches", "clear all", "new group",
            "new community", "archived", "starred messages", "disappearing messages",
            "chats", "updates", "communities", "calls", "tools", "filter",
            "group chats", "group chat", "this list automatically updates",
            "this list dynamically updates"
        )
        if (lower.contains("this list") && (lower.contains("group chats") || lower.contains("updates"))) return true
        if (lower.contains("automatically updates") || lower.contains("dynamically updates")) return true
        if (lower.contains("maharashtra cyber")) return true
        if (lower.contains("channel") || lower.contains("newsletter")) return true
        return blocked.any { lower == it || lower.startsWith("$it ") || lower.startsWith("$it…") || lower.startsWith("$it...") }
    }
    /**
     * What it is:
     * Execution routine for [findGroupsFilterChip].
     *
     * What it does:
     * Coordinates findGroupsFilterChip operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppChatScanner]
     */

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
    /**
     * What it is:
     * Execution routine for [isGroupRow].
     *
     * What it does:
     * Coordinates isGroupRow operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppChatScanner]
     */

    private fun findAllFilterChip(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
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
            val isTopArea = bounds.top in 100..950 && bounds.bottom in 200..1200
            val isChatOrAvatar = id.contains("contact_photo") || id.contains("avatar") ||
                id.contains("contact_row") || id.contains("conversations_row") ||
                desc.contains("picture", ignoreCase = true) || desc.contains("photo", ignoreCase = true)

            if (isTopArea && !isChatOrAvatar) {
                val isAllChip = id.contains("conversations_filter_debug_view_id_all") ||
                    id.contains("id_all") ||
                    id.contains("filter_all") ||
                    id.contains("all_filter") ||
                    desc.startsWith("All filter", ignoreCase = true) ||
                    desc.contains("All filter", ignoreCase = true) ||
                    ((text.equals("All", ignoreCase = true) || text.equals("सभी", ignoreCase = true) ||
                      desc.equals("All", ignoreCase = true) || desc.equals("सभी", ignoreCase = true)) &&
                     (node.isClickable || node.isCheckable || cls.contains("RadioButton") || cls.contains("Button") || id.contains("filter")))

                if (isAllChip) {
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

    private fun hasMsgFromNode(row: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val text = node.text?.toString()?.trim().orEmpty()
            if (id.endsWith("msg_from_tv") && text.isNotBlank()) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    private fun hasGroupAvatarDesc(row: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val isAvatarNode = id.contains("photo") || id.contains("avatar") || id.contains("icon") || id.contains("picture")
            if (isAvatarNode) {
                if (desc == "group icon" || desc == "group photo" || desc == "group picture" ||
                    desc == "group profile picture" || desc == "ग्रुप आइकन" || desc == "ग्रुप फ़ोटो" ||
                    desc.startsWith("group icon") || desc.startsWith("group photo") || desc.startsWith("group picture")
                ) {
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    private fun hasGroupEventText(row: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        val groupEvents = listOf(
            "created group", "created the group", "added you", "you were added",
            "joined using", "left group", "left the group",
            "changed the group", "changed the subject"
        )
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            if (groupEvents.any { text.contains(it) || desc.contains(it) }) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    private fun isBusinessRow(row: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            if (id.contains("verified") || id.contains("business")) return true
            if (desc.contains("verified business") || desc.contains("business account")) return true
            if (text.contains("verified business")) return true
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    private fun isSearchModeActive(root: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            if (id.contains("search_fragment_holder") || id.contains("search_holder")) {
                if (node.isVisibleToUser) return true
            }
            if (id.contains("search_src_text") || id.contains("search_input")) {
                val txt = node.text?.toString().orEmpty()
                if (txt.isNotBlank() || node.isFocused) return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    private fun findSearchBackButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val isBack = id.contains("back") || id.contains("up") || id.contains("action_mode_close") ||
                desc.contains("back") || desc.contains("navigate up") || desc.contains("collapse") ||
                desc.contains("close") || desc.contains("clear query")
            if (isBack && node.isClickable) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return null
    }

    private fun isInsideConversation(root: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var hasEntry = false
        var hasVoiceOrSend = false
        var hasBottomNav = false
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            if (id.endsWith("entry") || id.endsWith("input_text") || id.endsWith("caption")) {
                hasEntry = true
            }
            if (id.endsWith("send") || id.endsWith("voice_note_btn") || id.endsWith("camera_btn")) {
                hasVoiceOrSend = true
            }
            if (id.contains("bottom_nav") || id.contains("chats_tab") || desc.contains("chats tab")) {
                hasBottomNav = true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return (hasEntry || hasVoiceOrSend) && !hasBottomNav
    }

    private fun hasDirectAvatarDesc(row: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val isAvatar = id.contains("photo") || id.contains("avatar") || id.contains("picture")
            if (isAvatar && !desc.contains("group")) {
                if (desc == "profile photo" || desc == "profile picture" ||
                    desc.startsWith("profile photo of") || desc.startsWith("profile picture of") ||
                    desc.contains("view profile photo") || desc.contains("view profile picture")
                ) {
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    private fun classifyRow(
        row: AccessibilityNodeInfo,
        cleanTitle: String,
        target: ScanTarget,
        repo: AppRepository
    ): RowKind {
        // 1. Ignore non-conversation header/status rows
        if (isIgnoredTitle(cleanTitle) || !repo.isValidPickedChatName(cleanTitle)) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): UNRESOLVED (invalid or ignored title)")
            return RowKind.UNRESOLVED
        }

        // Direct profile avatar strictly identifies an individual contact
        if (hasDirectAvatarDesc(row)) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (direct profile photo avatar)")
            return RowKind.DIRECT
        }

        val digits = cleanTitle.filter { it.isDigit() }
        val letters = cleanTitle.filter { it.isLetter() }
        val isPurePhone = digits.length >= 7 && letters.isEmpty()
        if (isPurePhone) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (pure phone number)")
            return RowKind.DIRECT
        }

        val isInPhoneBook = repo.hasCellularPhoneContact(cleanTitle) || repo.findPhoneForContactName(cleanTitle) != null
        if (isInPhoneBook) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (phonebook contact match)")
            return RowKind.DIRECT
        }

        if (isBusinessRow(row)) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (verified business row)")
            return RowKind.DIRECT
        }

        // 2. Concrete group indicators ONLY
        val isKnownGroup = discoveredGroups.contains(cleanTitle.lowercase().trim())
        val hasAvatarDesc = hasGroupAvatarDesc(row)
        val hasMsgFrom = hasMsgFromNode(row)
        val hasEvent = hasGroupEventText(row)

        val isConfirmedGroup = (isKnownGroup || hasAvatarDesc || hasMsgFrom || hasEvent)

        if (target == ScanTarget.GROUPS_ONLY) {
            return if (isConfirmedGroup) {
                Log.i(loggerTag, "classifyRow('$cleanTitle'): GROUP (GROUPS_ONLY target confirmed by markers)")
                discoveredGroups.add(cleanTitle.lowercase().trim())
                RowKind.GROUP
            } else {
                Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (rejected from GROUPS_ONLY, no group markers)")
                RowKind.DIRECT
            }
        }

        if (target == ScanTarget.DIRECT_CHATS_ONLY) {
            return RowKind.DIRECT
        }

        if (isConfirmedGroup) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): GROUP (confirmed by markers)")
            discoveredGroups.add(cleanTitle.lowercase().trim())
            return RowKind.GROUP
        }

        // Default Direct
        Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (default direct chat)")
        return RowKind.DIRECT
    }
}
