/**
 * Package: com.jmd.helper.whatsapp
 * Module: WhatsApp Task & Tree Traversal
 *
 * Discovery engine for harvesting WhatsApp chats and groups via accessibility
 * node traversal, native filter tab detection, scrolling, and pool enrichment.
 *
 * Linked With:
 * - Service: [com.jmd.helper.service.TaskAccessibilityService]
 * - Repository: [com.jmd.helper.data.repo.AppRepository]
 * - Ingestion: [com.jmd.helper.engine.ContactIngestionEngine]
 */
package com.jmd.helper.whatsapp

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jmd.helper.core.PhoneNumberNormalizer
import com.jmd.helper.core.showAppToast
import com.jmd.helper.data.model.DynamicContactItem
import com.jmd.helper.data.model.WhatsAppGroupItem
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.whatsapp.AccessibilityNodeUtils.recycleSafely

/**
 * Accessibility-based scanner engine that directly iterates WhatsApp and WhatsApp Business
 * conversation lists, discovers contacts and groups, and saves them into the local dynamic pool.
 */
class WhatsAppChatScanner(
    private val service: AccessibilityService,
    private val loggerTag: String = "WAChatScanner",
) {

    /** Target filtering scope for scanner discovery. */
    enum class ScanTarget {
        ALL_CHATS,
        GROUPS_ONLY
    }

    private enum class DeepScanPhase {
        ENSURE_FILTER_TAB,
        HARVEST_ROWS,
        SCROLL_DOWN,
        COMPLETE
    }

    var scanTarget: ScanTarget = ScanTarget.ALL_CHATS
    private var currentPhase = DeepScanPhase.HARVEST_ROWS
    private val discoveredGroups = linkedMapOf<String, WhatsAppGroupItem>()
    private val discoveredChats = linkedMapOf<String, DynamicContactItem>()
    private var isGroupsTabActivated = false
    private var tabSearchAttempts = 0
    private var stepAttempts = 0
    private var scrollCycles = 0
    private val maxScrollCycles = 150
    private var lastDiscoveredCount = 0
    private var consecutiveStagnantCycles = 0
    private val maxStagnantCycles = 3

    /**
     * Clears discovery cache and resets traversal state for a fresh chat/group scanning pass.
     */
    fun resetScanState(target: ScanTarget = ScanTarget.ALL_CHATS) {
        scanTarget = target
        currentPhase = if (target == ScanTarget.GROUPS_ONLY) DeepScanPhase.ENSURE_FILTER_TAB else DeepScanPhase.HARVEST_ROWS
        discoveredGroups.clear()
        discoveredChats.clear()
        isGroupsTabActivated = false
        tabSearchAttempts = 0
        stepAttempts = 0
        scrollCycles = 0
        lastDiscoveredCount = 0
        consecutiveStagnantCycles = 0
        Log.d(loggerTag, "Reset WhatsAppChatScanner state for full scan (target=$scanTarget, maxCycles=$maxScrollCycles)")
    }

    /**
     * Drives accessibility node inspection across recent conversation lists, harvesting contact and group entities.
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
                DeepScanPhase.ENSURE_FILTER_TAB -> {
                    // Try to locate and activate WhatsApp's native top 'Groups' filter chip
                    tabSearchAttempts++
                    val groupsChip = findGroupsFilterTab(root)
                    if (groupsChip != null) {
                        val isSelected = groupsChip.isSelected || groupsChip.contentDescription?.contains("selected", true) == true
                        if (isSelected) {
                            isGroupsTabActivated = true
                            Log.i(loggerTag, "Native Groups filter chip is already selected")
                            currentPhase = DeepScanPhase.HARVEST_ROWS
                            onRetry(250L)
                            return
                        } else {
                            val clicked = AccessibilityNodeUtils.clickElement(groupsChip, service)
                            Log.i(loggerTag, "Clicked native Groups filter chip: result=$clicked")
                            if (clicked) {
                                isGroupsTabActivated = true
                                currentPhase = DeepScanPhase.HARVEST_ROWS
                                onRetry(450L) // Allow WhatsApp list to re-filter
                                return
                            }
                        }
                    }

                    if (tabSearchAttempts >= 3) {
                        Log.d(loggerTag, "Native Groups filter chip not found or not clickable, proceeding with row heuristics")
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        onRetry(200L)
                    } else {
                        onRetry(300L)
                    }
                }

                DeepScanPhase.HARVEST_ROWS -> {
                    val rowContainers = findAllRowContainers(root)
                    val isBiz = pkg.contains("w4b", ignoreCase = true)
                    val baseSrc = if (scanTarget == ScanTarget.GROUPS_ONLY) "WA_GROUP" else (if (isBiz) "WA_BUSINESS" else "WA_CHAT")

                    for (row in rowContainers) {
                        val title = extractTitleFromRow(row) ?: continue
                        val cleanTitle = title.trim().removePrefix("~").trim()
                        if (cleanTitle.isBlank() || !repo.isValidPickedChatName(cleanTitle)) continue

                        val phoneDigits = cleanTitle.filter { it.isDigit() }
                        val isPhone = phoneDigits.length >= 7
                        val isBroadcast = cleanTitle.contains("broadcast", ignoreCase = true) || cleanTitle.contains("recipients", ignoreCase = true)
                        val isStatus = cleanTitle.equals("status", ignoreCase = true) || cleanTitle.equals("my status", ignoreCase = true)

                        if (isStatus) continue

                        if (scanTarget == ScanTarget.GROUPS_ONLY) {
                            // Verify group nature: either native Groups tab was activated, or row shows group signals
                            val isGroup = isGroupsTabActivated || hasGroupSignalsInRow(row) || cleanTitle.contains("group", ignoreCase = true)
                            if (isGroup && !isPhone && !isBroadcast) {
                                if (!discoveredGroups.containsKey(cleanTitle)) {
                                    discoveredGroups[cleanTitle] = WhatsAppGroupItem(
                                        name = cleanTitle,
                                        participantCount = extractSubtitleFromRow(row),
                                        lastDiscoveredAt = System.currentTimeMillis(),
                                        packageName = pkg,
                                        photoUri = "",
                                        description = ""
                                    )
                                    Log.i(loggerTag, "Harvested WhatsApp group: '$cleanTitle' ($pkg)")
                                }
                                val normGroup = "group:$cleanTitle"
                                if (!discoveredChats.containsKey(normGroup)) {
                                    discoveredChats[normGroup] = DynamicContactItem(
                                        name = cleanTitle,
                                        phone = normGroup,
                                        normalizedNumber = normGroup,
                                        source = "WA_GROUP",
                                        photoUri = ""
                                    )
                                }
                            }
                        } else {
                            if (isPhone) {
                                // Unsaved phone number chat
                                val norm = PhoneNumberNormalizer.normalize(cleanTitle)
                                val contactInfo = repo.findContactByPhone(cleanTitle)
                                val resolvedName = contactInfo?.first?.ifBlank { cleanTitle } ?: cleanTitle
                                val resolvedPhoto = contactInfo?.second?.ifBlank { repo.findPhotoForContactName(resolvedName) } ?: repo.findPhotoForContactName(resolvedName)

                                if (!discoveredChats.containsKey(norm)) {
                                    discoveredChats[norm] = DynamicContactItem(
                                        name = resolvedName,
                                        phone = cleanTitle,
                                        normalizedNumber = norm,
                                        source = baseSrc,
                                        photoUri = resolvedPhoto,
                                    )
                                    Log.i(loggerTag, "Harvested unsaved phone chat: '$resolvedName' -> $cleanTitle ($baseSrc)")
                                }
                            } else {
                                // Contact name or group in ALL_CHATS mode
                                val isGroup = isGroupsTabActivated || hasGroupSignalsInRow(row)
                                if (isGroup) {
                                    if (!discoveredGroups.containsKey(cleanTitle)) {
                                        discoveredGroups[cleanTitle] = WhatsAppGroupItem(
                                            name = cleanTitle,
                                            participantCount = extractSubtitleFromRow(row),
                                            lastDiscoveredAt = System.currentTimeMillis(),
                                            packageName = pkg
                                        )
                                    }
                                    val normGroup = "group:$cleanTitle"
                                    if (!discoveredChats.containsKey(normGroup)) {
                                        discoveredChats[normGroup] = DynamicContactItem(
                                            name = cleanTitle,
                                            phone = normGroup,
                                            normalizedNumber = normGroup,
                                            source = "WA_GROUP"
                                        )
                                    }
                                } else if (!isBroadcast) {
                                    val phoneFromBook = repo.findPhoneForContactName(cleanTitle)
                                    val norm = if (!phoneFromBook.isNullOrBlank()) PhoneNumberNormalizer.normalize(phoneFromBook) else PhoneNumberNormalizer.normalize(cleanTitle)
                                    val photo = repo.findPhotoForContactName(cleanTitle)
                                    val phoneToUse = phoneFromBook ?: cleanTitle

                                    if (!discoveredChats.containsKey(norm)) {
                                        discoveredChats[norm] = DynamicContactItem(
                                            name = cleanTitle,
                                            phone = phoneToUse,
                                            normalizedNumber = norm,
                                            source = baseSrc,
                                            photoUri = photo,
                                        )
                                        Log.i(loggerTag, "Harvested contact chat: '$cleanTitle' -> $phoneToUse ($baseSrc)")
                                    }
                                }
                            }
                        }
                    }

                    val totalDiscovered = if (scanTarget == ScanTarget.GROUPS_ONLY) discoveredGroups.size else discoveredChats.size
                    if (totalDiscovered > lastDiscoveredCount) {
                        consecutiveStagnantCycles = 0
                        lastDiscoveredCount = totalDiscovered
                    }

                    if ((scrollCycles < maxScrollCycles) && (consecutiveStagnantCycles < maxStagnantCycles)) {
                        currentPhase = DeepScanPhase.SCROLL_DOWN
                        stepAttempts = 0
                        onRetry(350L)
                    } else {
                        Log.i(loggerTag, "Harvest complete. Groups: ${discoveredGroups.size}, chats: ${discoveredChats.size}, cycles: $scrollCycles")
                        finishAndSave(onFinish)
                    }
                }

                DeepScanPhase.SCROLL_DOWN -> {
                    val scrollableNode = findScrollableList(root)
                    var scrolled = false
                    if (scrollableNode != null) {
                        scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        Log.i(loggerTag, "Scrolled chat list forward via node: result=$scrolled")
                    }

                    if (!scrolled) {
                        scrolled = AccessibilityNodeUtils.performVerticalSwipe(service, startYRatio = 0.75f, endYRatio = 0.25f)
                        Log.i(loggerTag, "Scrolled chat list via vertical swipe gesture: result=$scrolled")
                    }

                    val totalDiscovered = if (scanTarget == ScanTarget.GROUPS_ONLY) discoveredGroups.size else discoveredChats.size
                    if (totalDiscovered == lastDiscoveredCount) {
                        consecutiveStagnantCycles++
                        Log.d(loggerTag, "No new items in cycle $scrollCycles (stagnant=$consecutiveStagnantCycles/$maxStagnantCycles)")
                    } else {
                        consecutiveStagnantCycles = 0
                        lastDiscoveredCount = totalDiscovered
                    }

                    if (((scrolled || (scrollCycles < 4))) && (consecutiveStagnantCycles < maxStagnantCycles) && (scrollCycles < maxScrollCycles)) {
                        scrollCycles++
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        stepAttempts = 0
                        onRetry(600L)
                    } else {
                        Log.i(loggerTag, "Reached end of list or maximum cycles, finishing scan. Discovered: $totalDiscovered")
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
        if (discoveredGroups.isNotEmpty()) {
            val count = repo.bulkSaveWhatsAppGroupItems(discoveredGroups.values.toList())
            Log.i(loggerTag, "finishAndSave: Persisted $count WhatsApp groups to database")
        }
        val savedChats = if (discoveredChats.isNotEmpty()) {
            val count = repo.upsertDynamicPoolBatch(discoveredChats.values.toList())
            Log.i(loggerTag, "finishAndSave: Ingested $count chats/groups into dynamic pool")
            count
        } else 0

        val msg = if (scanTarget == ScanTarget.GROUPS_ONLY) {
            "Harvested ${discoveredGroups.size} WhatsApp groups"
        } else {
            "Synced $savedChats WhatsApp conversations"
        }
        service.showAppToast(msg, isLong = true)

        currentPhase = DeepScanPhase.COMPLETE
        onFinish()

        val appIntent = service.packageManager.getLaunchIntentForPackage(service.packageName)
        if (appIntent != null) {
            appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            service.startActivity(appIntent)
        }
    }

    /**
     * Searches the top filter bar for WhatsApp's native 'Groups' filter chip / tab.
     */
    private fun findGroupsFilterTab(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()

            val isGroupLabel = text.equals("Groups", ignoreCase = true) ||
                text.equals("ग्रुप", ignoreCase = true) ||
                text.equals("समूह", ignoreCase = true) ||
                desc.equals("Groups", ignoreCase = true) ||
                desc.startsWith("Groups, ", ignoreCase = true) ||
                id.contains("filter_chip_groups")

            if (isGroupLabel && (node.isClickable || node.parent?.isClickable == true)) {
                return if (node.isClickable) node else node.parent
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return null
    }

    /**
     * Evaluates whether a conversation row exhibits group characteristics.
     */
    private fun hasGroupSignalsInRow(row: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val desc = n.contentDescription?.toString()?.lowercase().orEmpty()
            val txt = n.text?.toString()?.trim().orEmpty()
            val id = n.viewIdResourceName?.lowercase().orEmpty()

            if (desc.contains("group icon") || desc.contains("group profile") || desc.contains("group photo") ||
                desc.startsWith("group ") || desc.contains(" group") || id.contains("group")
            ) {
                return true
            }

            // In group chats, message snippet often starts with "Sender: Message"
            if (txt.contains(":") && !txt.startsWith("http", ignoreCase = true)) {
                val prefix = txt.substringBefore(":").trim().removePrefix("~").trim()
                if (prefix.isNotBlank() && prefix.length in 1..30 && !prefix.contains("\n")) {
                    return true
                }
            }

            if (txt.contains("added", ignoreCase = true) ||
                txt.contains("created group", ignoreCase = true) ||
                txt.contains("left", ignoreCase = true) ||
                txt.contains("joined using", ignoreCase = true)
            ) {
                return true
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
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

            val isHorizontalPager = id.contains("pager") || cls.contains("ViewPager")

            if (!isHorizontalPager) {
                if ((id == "android:id/list") || cls.contains("RecyclerView") || cls.contains("ListView")) {
                    while (q.isNotEmpty()) {
                        val rem = q.removeFirst()
                        if (rem != node && rem != root && rem != fallbackScrollable) rem.recycleSafely()
                    }
                    if (fallbackScrollable != null && fallbackScrollable != node && fallbackScrollable != root) {
                        fallbackScrollable.recycleSafely()
                    }
                    return node
                }
                if (node.isScrollable && (fallbackScrollable == null)) {
                    fallbackScrollable = node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
            if (node != root && node != fallbackScrollable) {
                node.recycleSafely()
            }
        }
        return fallbackScrollable
    }

    private fun findAllRowContainers(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val containers = mutableListOf<AccessibilityNodeInfo>()
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
                containers.add(node)
            } else {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { q.add(it) }
                }
            }
        }

        if (containers.isEmpty()) {
            val fq = ArrayDeque<AccessibilityNodeInfo>()
            fq.add(root)
            while (fq.isNotEmpty()) {
                val node = fq.removeFirst()
                if (node.isClickable && (extractTitleFromRow(node) != null)) {
                    containers.add(node)
                } else {
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { fq.add(it) }
                    }
                }
            }
        }

        return containers
    }

    private fun extractTitleFromRow(row: AccessibilityNodeInfo): String? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        val textCandidates = mutableListOf<String>()

        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val txt = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()

            if (id.endsWith("conversations_row_contact_name") ||
                id.endsWith("chat_title") ||
                id.endsWith("contactpicker_row_name") ||
                id.endsWith("conversation_contact_name") ||
                id.endsWith("chat_name") ||
                id.endsWith("contact_name") ||
                id.endsWith("name") ||
                id.endsWith("title")
            ) {
                if (txt.isNotBlank()) return txt
            }

            if (desc.startsWith("Profile picture of ", ignoreCase = true)) {
                val candidate = desc.substring(19).trim().removePrefix("~").trim()
                if (candidate.isNotBlank()) return candidate
            }

            if (txt.isNotBlank()) {
                textCandidates.add(txt)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        val rowDesc = row.contentDescription?.toString()?.trim().orEmpty()
        if (rowDesc.isNotBlank()) {
            val firstPart = rowDesc.substringBefore(",").trim().removePrefix("~").trim()
            val isLengthOk = (firstPart.length >= 2) && (firstPart.length <= 80)
            val isNotDigits = !firstPart.all { char -> char.isDigit() || (char in "+- ()") }
            val isNotSelected = !firstPart.lowercase().contains("selected")
            if (isLengthOk && isNotDigits && isNotSelected) {
                return firstPart
            }
        }

        val timeRegex = Regex("""(?i)^\d{1,2}:\d{2}(\s?[ap]m)?$|^yesterday$|^\d{1,2}/\d{1,2}/\d{2,4}$""")
        for (candidate in textCandidates) {
            val clean = candidate.removePrefix("~").trim()
            val isCleanLengthOk = clean.isNotEmpty() && (clean.length <= 80)
            if (isCleanLengthOk && !timeRegex.matches(clean)) {
                return clean
            }
        }

        return null
    }

    private fun extractSubtitleFromRow(row: AccessibilityNodeInfo): String {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val txt = node.text?.toString()?.trim().orEmpty()

            if (id.endsWith("msg_from_status") || id.endsWith("last_message") || id.endsWith("subtitle") || id.endsWith("snippet")) {
                if (txt.isNotBlank()) return txt
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return ""
    }
}
