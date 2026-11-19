/**
 * Package: com.jmd.superwa.whatsapp
 * Module: WhatsApp Automation Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [WhatsAppChatScanner].
 *
 * What it does:
 * Discovery engine for harvesting WhatsApp direct chats and groups via accessibility node traversal, scrolling, and pool enrichment.
 *
 * Linked With:
 * - Repositories: [com.jmd.superwa.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.superwa.data.model.DynamicContactItem]
 * - Services & Engines: [android.accessibilityservice.AccessibilityService], [com.jmd.superwa.service.TaskAccessibilityService], [com.jmd.superwa.whatsapp.AccessibilityNodeUtils.recycleSafely]
 * - Core Security & Utilities: [android.util.Log], [com.jmd.superwa.compat.AccessibilityCompat], [com.jmd.superwa.core.PhoneNumberNormalizer], [com.jmd.superwa.core.showAppToast]
 * - Android Framework & Jetpack: [android.graphics.Rect], [android.view.accessibility.AccessibilityNodeInfo]
 */

package com.jmd.superwa.whatsapp

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jmd.superwa.compat.AccessibilityCompat
import com.jmd.superwa.core.PhoneNumberNormalizer
import com.jmd.superwa.core.showAppToast
import com.jmd.superwa.data.model.DynamicContactItem
import com.jmd.superwa.data.repo.AppRepository
import com.jmd.superwa.service.TaskAccessibilityService
import com.jmd.superwa.whatsapp.AccessibilityNodeUtils.recycleSafely

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
    typealias ScanTarget = com.jmd.superwa.whatsapp.ScanTarget
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
        FILTER_GROUPS,
        HARVEST_GROUPS,
        SCROLL_GROUPS,
        FILTER_ALL,
        HARVEST_ALL,
        SCROLL_DOWN,
        COMPLETE
    }

    var scanTarget: ScanTarget = ScanTarget.ALL_CHATS
    private var activePkg: String = "com.whatsapp"
    private var currentPhase = DeepScanPhase.ENSURE_CHATS_TAB
    private val discoveredChats = linkedMapOf<String, DynamicContactItem>()
    private val discoveredGroups = mutableSetOf<String>()
    private var bottomTabCheckAttempts = 0
    private var stepAttempts = 0
    private var scrollCycles = 0
    private val maxScrollCycles = 60
    private var lastDiscoveredCount = 0
    private var consecutiveStagnantCycles = 0
    private val maxStagnantCycles = 2
    private var reachedListEnd = false
    private var endConfirmations = 0
    private var previousVisibleTitles = setOf<String>()
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
        bottomTabCheckAttempts = 0
        stepAttempts = 0
        scrollCycles = 0
        lastDiscoveredCount = 0
        consecutiveStagnantCycles = 0
        reachedListEnd = false
        endConfirmations = 0
        previousVisibleTitles = emptySet()
        initialLoadRetries = 0
        unresolvedRowCount = 0
        finishReason = ""
        Log.d(loggerTag, "Reset WhatsAppChatScanner state for unified single-pass scan (target=$scanTarget, maxCycles=$maxScrollCycles)")
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
            val isBiz = pkg.contains("w4b", ignoreCase = true)
            val baseChatSrc = if (isBiz) "WA_BUSINESS" else "WA_CHAT"
            val baseGroupSrc = if (isBiz) "WA_BUSINESS_GROUP" else "WA_GROUP"

            when (currentPhase) {
                DeepScanPhase.ENSURE_CHATS_TAB -> {
                    bottomTabCheckAttempts++

                    val backBtn = findNavigateUpOrBackButton(root)
                    if (backBtn != null) {
                        Log.i(loggerTag, "ENSURE_CHATS_TAB: Currently in sub-activity/details, pressing back to return to chats list")
                        AccessibilityNodeUtils.clickElement(backBtn, service)
                        onRetry(350L)
                        return
                    }

                    val chatsTab = findChatsBottomTab(root)
                    if (chatsTab != null && !isChipSelected(chatsTab)) {
                        val clicked = AccessibilityNodeUtils.clickElement(chatsTab, service)
                        Log.i(loggerTag, "Clicked Chats bottom tab: result=$clicked")
                        if (clicked) {
                            onRetry(250L)
                            return
                        }
                    }

                    // Route to Groups first if scanning ALL or GROUPS_ONLY, so all groups are cataloged
                    currentPhase = if (scanTarget == ScanTarget.DIRECT_CHATS_ONLY) {
                        DeepScanPhase.FILTER_ALL
                    } else {
                        DeepScanPhase.FILTER_GROUPS
                    }
                    stepAttempts = 0
                    consecutiveStagnantCycles = 0
                    onRetry(150L)
                }

                DeepScanPhase.FILTER_GROUPS -> {
                    stepAttempts++
                    val groupsChip = findGroupsFilterChip(root)
                    if (groupsChip != null) {
                        if (!isChipSelected(groupsChip)) {
                            val clicked = AccessibilityNodeUtils.clickElement(groupsChip, service)
                            Log.i(loggerTag, "FILTER_GROUPS: Clicked Groups filter chip: result=$clicked")
                            onRetry(350L)
                            return
                        } else {
                            // Groups chip is selected, proceed to harvest groups
                            Log.i(loggerTag, "FILTER_GROUPS: Groups chip selected, proceeding to HARVEST_GROUPS")
                            currentPhase = DeepScanPhase.HARVEST_GROUPS
                            stepAttempts = 0
                            onRetry(250L)
                            return
                        }
                    } else if (stepAttempts < 5) {
                        Log.i(loggerTag, "FILTER_GROUPS: waiting for groups chip ($stepAttempts/5)")
                        onRetry(250L)
                        return
                    } else {
                        // No groups filter chip available after 5 attempts (older WhatsApp version), proceed directly to ALL
                        Log.i(loggerTag, "FILTER_GROUPS: No groups chip found after 5 attempts, falling back to FILTER_ALL")
                        currentPhase = DeepScanPhase.FILTER_ALL
                        stepAttempts = 0
                        onRetry(150L)
                        return
                    }
                }

                DeepScanPhase.HARVEST_GROUPS -> {
                    val rowContainers = findAllRowContainers(root)
                    for (row in rowContainers) {
                        val title = extractTitleFromRow(row) ?: continue
                        val cleanTitle = title.trim().removePrefix("~").trim()
                        if (cleanTitle.isBlank() || !repo.isValidPickedChatName(cleanTitle) || isIgnoredTitle(cleanTitle)) continue

                        val digits = cleanTitle.filter { it.isDigit() }
                        val letters = cleanTitle.filter { it.isLetter() }
                        if (digits.length >= 7 && letters.isEmpty()) continue
                        if (hasIndividualAvatarDesc(row)) continue

                        val norm = "group:${cleanTitle.lowercase().trim()}"
                        val phone = "group:$cleanTitle"
                        discoveredGroups.add(cleanTitle.lowercase().trim())
                        if (!discoveredChats.containsKey(norm)) {
                            discoveredChats[norm] = DynamicContactItem(
                                name = cleanTitle,
                                phone = phone,
                                normalizedNumber = norm,
                                source = baseGroupSrc,
                                photoUri = "",
                            )
                            Log.i(loggerTag, "Harvested verified group from Groups tab: '$cleanTitle' -> $phone")
                        }
                    }

                    val currentVisibleTitles = rowContainers.mapNotNull { extractTitleFromRow(it)?.trim() }
                        .filter { it.isNotBlank() && !isIgnoredTitle(it) }
                        .toSet()

                    if (currentVisibleTitles.isNotEmpty() && previousVisibleTitles.isNotEmpty() && currentVisibleTitles == previousVisibleTitles) {
                        endConfirmations++
                        if (endConfirmations >= 2) {
                            reachedListEnd = true
                        }
                    } else {
                        endConfirmations = 0
                    }
                    if (currentVisibleTitles.isNotEmpty()) {
                        previousVisibleTitles = currentVisibleTitles
                    }

                    val totalDiscovered = discoveredGroups.size
                    val discoveredNew = totalDiscovered > lastDiscoveredCount
                    if (discoveredNew) {
                        consecutiveStagnantCycles = 0
                        lastDiscoveredCount = totalDiscovered
                    } else {
                        consecutiveStagnantCycles++
                    }

                    val groupsFinished = reachedListEnd || consecutiveStagnantCycles >= 2 || scrollCycles >= 25
                    if (groupsFinished) {
                        Log.i(loggerTag, "HARVEST_GROUPS complete: ${discoveredGroups.size} verified groups cataloged")
                        if (scanTarget == ScanTarget.GROUPS_ONLY) {
                            finishReason = "GROUPS_ONLY_COMPLETE"
                            finishAndSave(onFinish)
                            return
                        }
                        // Switch to ALL tab for direct chats and unified completion
                        currentPhase = DeepScanPhase.FILTER_ALL
                        stepAttempts = 0
                        scrollCycles = 0
                        consecutiveStagnantCycles = 0
                        reachedListEnd = false
                        endConfirmations = 0
                        previousVisibleTitles = emptySet()
                        onRetry(250L)
                        return
                    } else {
                        currentPhase = DeepScanPhase.SCROLL_GROUPS
                        stepAttempts = 0
                        onRetry(100L)
                        return
                    }
                }

                DeepScanPhase.SCROLL_GROUPS -> {
                    scrollCycles++
                    val scrollableNode = findScrollableList(root)
                    var scrolled = false
                    if (scrollableNode != null) {
                        scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    }

                    if (!scrolled) {
                        scrolled = AccessibilityNodeUtils.performVerticalSwipe(service, startYRatio = 0.80f, endYRatio = 0.22f)
                    }

                    currentPhase = DeepScanPhase.HARVEST_GROUPS
                    stepAttempts = 0
                    onRetry(250L)
                    return
                }

                DeepScanPhase.FILTER_ALL -> {
                    stepAttempts++
                    val allChip = findAllFilterChip(root)
                    if (allChip != null) {
                        if (!isChipSelected(allChip)) {
                            val clicked = AccessibilityNodeUtils.clickElement(allChip, service)
                            Log.i(loggerTag, "FILTER_ALL: Clicked All filter chip: result=$clicked")
                            onRetry(300L)
                            return
                        } else {
                            // Reset counters for the All-Chats pass
                            Log.i(loggerTag, "FILTER_ALL: All filter is active, starting HARVEST_ALL")
                            scrollCycles = 0
                            consecutiveStagnantCycles = 0
                            lastDiscoveredCount = discoveredChats.size
                            reachedListEnd = false
                            endConfirmations = 0
                            previousVisibleTitles = emptySet()
                            currentPhase = DeepScanPhase.HARVEST_ALL
                            stepAttempts = 0
                            onRetry(200L)
                            return
                        }
                    } else {
                        if (stepAttempts < 4) {
                            Log.i(loggerTag, "FILTER_ALL: waiting for All filter chip ($stepAttempts/4), attempting to scroll up")
                            val chatsTab = findChatsBottomTab(root)
                            if (chatsTab != null) {
                                AccessibilityNodeUtils.clickElement(chatsTab, service)
                            } else {
                                AccessibilityNodeUtils.performVerticalSwipe(service, startYRatio = 0.22f, endYRatio = 0.80f)
                            }
                            onRetry(250L)
                            return
                        } else {
                            // If All chip not found, proceed directly to HARVEST_ALL
                            Log.i(loggerTag, "FILTER_ALL: All chip not found after 4 attempts, proceeding to HARVEST_ALL directly")
                            currentPhase = DeepScanPhase.HARVEST_ALL
                            stepAttempts = 0
                            scrollCycles = 0
                            consecutiveStagnantCycles = 0
                            reachedListEnd = false
                            endConfirmations = 0
                            previousVisibleTitles = emptySet()
                            onRetry(200L)
                            return
                        }
                    }
                }

                DeepScanPhase.HARVEST_ALL -> {
                    val rowContainers = findAllRowContainers(root)
                    for (row in rowContainers) {
                        val title = extractTitleFromRow(row) ?: continue
                        val cleanTitle = title.trim().removePrefix("~").trim()
                        if (cleanTitle.isBlank() || !repo.isValidPickedChatName(cleanTitle) || isIgnoredTitle(cleanTitle)) continue

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
                            val norm = "group:${cleanTitle.lowercase().trim()}"
                            val phone = "group:$cleanTitle"
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

                    val currentVisibleTitles = rowContainers.mapNotNull { extractTitleFromRow(it)?.trim() }
                        .filter { it.isNotBlank() && !isIgnoredTitle(it) }
                        .toSet()

                    if (currentVisibleTitles.isNotEmpty() && previousVisibleTitles.isNotEmpty() && currentVisibleTitles == previousVisibleTitles) {
                        endConfirmations++
                        if (endConfirmations >= 2) {
                            reachedListEnd = true
                        }
                    } else {
                        endConfirmations = 0
                    }
                    if (currentVisibleTitles.isNotEmpty()) {
                        previousVisibleTitles = currentVisibleTitles
                    }

                    val totalDiscovered = discoveredChats.size
                    val discoveredNew = totalDiscovered > lastDiscoveredCount
                    if (discoveredNew) {
                        consecutiveStagnantCycles = 0
                        lastDiscoveredCount = totalDiscovered
                    } else {
                        consecutiveStagnantCycles++
                    }

                    finishReason = when {
                        reachedListEnd && !discoveredNew -> "END_OF_LIST"
                        scrollCycles >= maxScrollCycles -> "HARD_CAP"
                        consecutiveStagnantCycles >= maxStagnantCycles -> "STAGNANT"
                        else -> ""
                    }
                    if (finishReason.isEmpty()) {
                        currentPhase = DeepScanPhase.SCROLL_DOWN
                        stepAttempts = 0
                        onRetry(100L)
                    } else {
                        Log.i(loggerTag, "Unified scan complete ($finishReason): items=${discoveredChats.size}, unresolved=$unresolvedRowCount, cycles=$scrollCycles")
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
                        scrolled = AccessibilityNodeUtils.performVerticalSwipe(service, startYRatio = 0.80f, endYRatio = 0.22f)
                    }

                    currentPhase = DeepScanPhase.HARVEST_ALL
                    stepAttempts = 0
                    onRetry(250L)
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
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isSelected || AccessibilityCompat.isNodeChecked(child)) return true
            val cd = child.contentDescription?.toString()?.lowercase().orEmpty()
            if (cd.contains("unselected")) return false
            if (cd.contains("selected")) return true
        }
        return false
    }

    private fun findNavigateUpOrBackButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        val bounds = Rect()
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            node.getBoundsInScreen(bounds)
            val isTopLeft = bounds.top <= 250 && bounds.left <= 200
            val isBack = desc == "navigate up" || desc == "back" || desc.contains("navigate up") ||
                desc == "à¤ªà¥€à¤›à¥‡ à¤œà¤¾à¤à¤‚" || id.endsWith("back") || id.endsWith("action_bar_back")
            if (isTopLeft && isBack) {
                var target: AccessibilityNodeInfo? = node
                var depth = 0
                while (target != null && depth < 3) {
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
                text.equals("à¤šà¥ˆà¤Ÿ", ignoreCase = true) ||
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
        var bestScrollable: AccessibilityNodeInfo? = null
        var maxArea = 0
        val bounds = Rect()

        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val cls = node.className?.toString().orEmpty()

            val isNonChatList = id.contains("updates") || id.contains("status") ||
                id.contains("call") || id.contains("channel") || id.contains("newsletter") ||
                id.contains("community") || id.contains("filter") || id.contains("tab")

            val isHorizontalPager = id.contains("pager") || cls.contains("ViewPager")

            if (!isHorizontalPager && !isNonChatList) {
                node.getBoundsInScreen(bounds)
                val isListClass = cls.contains("RecyclerView") || cls.contains("ListView") || node.isScrollable
                val isLargeList = bounds.height() >= 450 // Conversation list takes majority of screen height

                if ((id == "android:id/list" || id.endsWith(":id/list") || id.endsWith("/list")) && isListClass && isLargeList) {
                    Log.i(loggerTag, "findScrollableList: matched exact conversation list '$id' ($cls, height=${bounds.height()})")
                    return node
                }
                if (isListClass && isLargeList && !id.contains("host") && !id.contains("parent")) {
                    val area = bounds.width() * bounds.height()
                    if (area > maxArea) {
                        maxArea = area
                        bestScrollable = node
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        Log.i(loggerTag, "findScrollableList: fallback to '${bestScrollable?.viewIdResourceName}' (${bestScrollable?.className})")
        return bestScrollable
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
            if (isIgnoredTitle(title)) {
                Log.i(loggerTag, "isValidRow: rejected by isIgnoredTitle: '$title'")
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
        if (lower.length < 2) return true
        if (lower.contains("automatically update") || lower.contains("all group chats") ||
            lower.contains("this list") || lower.contains("group chats") ||
            lower.contains("manage group") || lower.contains("manage list") ||
            lower.contains("no chats") || lower.contains("chats will appear") ||
            lower.contains("start a chat") || lower.contains("tap and hold") ||
            lower.contains("swipe down") || lower.contains("add new list")
        ) {
            return true
        }
        val blocked = listOf(
            "photo", "video", "audio", "document", "sticker", "gif", "contact", "contacts", "location",
            "voice call", "video call", "missed call", "no answer",
            "inbox filters", "radio group", "all filter", "groups filter", "unread filter",
            "favorites filter", "new customer filter", "new order filter",
            "hidden updates", "find channels to follow", "explore more", "create channel",
            "recent updates", "add status", "muted updates", "view updates", "channels",
            "ask meta ai or search", "search group chats", "search chats", "search...", "search",
            "manage groups", "manage group", "recent searches", "clear all", "new group",
            "new community", "archived", "starred messages", "disappearing messages",
            "chats", "updates", "communities", "calls", "tools", "filter", "group chats"
        )
        return blocked.any { lower == it || lower.startsWith("$it ") || lower.startsWith("$it...") }
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

            // Strictly reject footer/manage buttons
            if (id.contains("footer") || id.contains("manage") ||
                desc.contains("manage", ignoreCase = true) || text.contains("manage", ignoreCase = true)
            ) {
                continue
            }

            node.getBoundsInScreen(bounds)
            // Top area filter chips reside strictly in the horizontal filter bar below search bar (height 40..150)
            val isFilterBarArea = bounds.top in 150..450 && bounds.height() in 40..150

            val isChatOrAvatar = id.contains("contact_photo") || id.contains("avatar") ||
                id.contains("contact_row") || id.contains("conversations_row") ||
                desc.contains("picture", ignoreCase = true) || desc.contains("photo", ignoreCase = true) ||
                desc.contains("status", ignoreCase = true)

            if (isFilterBarArea && !isChatOrAvatar) {
                val isGroupChip = id.contains("conversations_filter_debug_view_id_groups") ||
                    id.endsWith("_id_groups") ||
                    desc.startsWith("Groups filter", ignoreCase = true) ||
                    desc.contains("Groups filter", ignoreCase = true) ||
                    ((text.startsWith("Groups", ignoreCase = true) || text.startsWith("à¤—à¥à¤°à¥à¤ª", ignoreCase = true) ||
                      desc.startsWith("Groups", ignoreCase = true) || desc.startsWith("à¤—à¥à¤°à¥à¤ª", ignoreCase = true)) &&
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
     * Execution routine for [findAllFilterChip].
     *
     * What it does:
     * Coordinates findAllFilterChip operations conforming to application constraints.
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

            if (id.contains("footer") || id.contains("manage")) {
                continue
            }

            node.getBoundsInScreen(bounds)
            val isFilterBarArea = bounds.top in 150..450 && bounds.height() in 40..150
            val isChatOrAvatar = id.contains("contact_photo") || id.contains("avatar") ||
                id.contains("contact_row") || id.contains("conversations_row") ||
                desc.contains("picture", ignoreCase = true) || desc.contains("photo", ignoreCase = true)

            if (isFilterBarArea && !isChatOrAvatar) {
                val isAllChip = id.contains("conversations_filter_debug_view_id_all") ||
                    id.endsWith("_id_all") ||
                    desc.startsWith("All filter", ignoreCase = true) ||
                    desc.contains("All filter", ignoreCase = true) ||
                    ((text.equals("All", ignoreCase = true) || text.equals("à¤¸à¤­à¥€", ignoreCase = true) ||
                      desc.equals("All", ignoreCase = true) || desc.equals("à¤¸à¤­à¥€", ignoreCase = true)) &&
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

    private fun hasIndividualAvatarDesc(row: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            if (desc.startsWith("profile photo of") ||
                desc.startsWith("profile picture of") ||
                desc.startsWith("profile photo,") ||
                desc.startsWith("double tap to view profile photo") ||
                desc.startsWith("double tap to view photo") ||
                desc.contains("'s status")
            ) {
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
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            if (desc.startsWith("group icon of") ||
                desc.startsWith("group photo of") ||
                desc.startsWith("group picture of") ||
                desc.startsWith("double tap to view group icon") ||
                desc.startsWith("double tap to view group photo") ||
                desc.startsWith("double tap to view group picture") ||
                desc == "group icon" || desc == "group photo" || desc == "group picture" ||
                desc.contains("à¤—à¥à¤°à¥à¤ª à¤†à¤‡à¤•à¥‰à¤¨") || desc.contains("à¤—à¥à¤°à¥à¤ª à¤«à¤¼à¥‹à¤Ÿà¥‹")
            ) {
                return true
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
            "created group", "created the group", "you were added to the group",
            "added you to the group", "joined using this group", "left the group",
            "changed the group subject", "changed the group icon"
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

        // 2. Known / confirmed group cataloged from Groups filter tab
        if (discoveredGroups.contains(cleanTitle.lowercase().trim())) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): GROUP (matches confirmed group)")
            return RowKind.GROUP
        }

        // 3. Pure phone number is ALWAYS a direct chat (WhatsApp groups never have pure phone numbers as title)
        val digits = cleanTitle.filter { it.isDigit() }
        val letters = cleanTitle.filter { it.isLetter() }
        if (digits.length >= 7 && letters.isEmpty()) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (pure phone number)")
            return if (target == ScanTarget.GROUPS_ONLY) RowKind.UNRESOLVED else RowKind.DIRECT
        }

        // 4. Contact in device address book is ALWAYS a direct chat
        if (repo.hasCellularPhoneContact(cleanTitle) || repo.findPhoneForContactName(cleanTitle) != null) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (phonebook contact match)")
            return if (target == ScanTarget.GROUPS_ONLY) RowKind.UNRESOLVED else RowKind.DIRECT
        }

        // 5. Verified Business row is always a direct chat
        if (isBusinessRow(row)) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (verified business row)")
            return if (target == ScanTarget.GROUPS_ONLY) RowKind.UNRESOLVED else RowKind.DIRECT
        }

        // 6. Explicit individual profile avatar description ("Profile photo of ...") is ALWAYS a direct chat
        if (hasIndividualAvatarDesc(row)) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (individual avatar description)")
            return if (target == ScanTarget.GROUPS_ONLY) RowKind.UNRESOLVED else RowKind.DIRECT
        }

        if (target == ScanTarget.DIRECT_CHATS_ONLY) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (DIRECT_CHATS_ONLY target)")
            return RowKind.DIRECT
        }

        // 7. WhatsApp's dedicated sender-prefix TextView: msg_from_tv
        if (hasMsgFromNode(row)) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): GROUP (has msg_from_tv node)")
            discoveredGroups.add(cleanTitle.lowercase().trim())
            return RowKind.GROUP
        }

        // 8. Group avatar content description ("Group icon of ...")
        if (hasGroupAvatarDesc(row)) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): GROUP (group avatar description)")
            discoveredGroups.add(cleanTitle.lowercase().trim())
            return RowKind.GROUP
        }

        // 9. Subtitle snippet contains group event notifications
        if (hasGroupEventText(row)) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): GROUP (group event text in snippet)")
            discoveredGroups.add(cleanTitle.lowercase().trim())
            return RowKind.GROUP
        }

        // 10. If targeting GROUPS_ONLY and not matched any group indicator, it is NOT a group
        if (target == ScanTarget.GROUPS_ONLY) {
            Log.i(loggerTag, "classifyRow('$cleanTitle'): UNRESOLVED (not a verified group in GROUPS_ONLY)")
            return RowKind.UNRESOLVED
        }

        // 11. Default Direct:
        Log.i(loggerTag, "classifyRow('$cleanTitle'): DIRECT (default direct chat)")
        return RowKind.DIRECT
    }

}
