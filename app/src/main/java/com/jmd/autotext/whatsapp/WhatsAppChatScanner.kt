

/**
 * Package: com.jmd.autotext.whatsapp
 * Module: WhatsApp Automation & Tree Traversal
 *
 * Automated discovery engine for harvesting WhatsApp chats and groups via accessibility
 * node traversal, scrolling, and pool enrichment.
 *
 * Linked With:
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Repository: [com.jmd.autotext.data.repo.WhatsAppRepository]
 * - Ingestion: [com.jmd.autotext.engine.ContactIngestionEngine]
 */
package com.jmd.autotext.whatsapp

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jmd.autotext.core.PhoneNumberNormalizer
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.model.DynamicContactItem
import com.jmd.autotext.data.model.WhatsAppGroupItem
import com.jmd.autotext.data.repo.AppRepository

/**
 * Accessibility-based scanner engine that automatically iterates WhatsApp conversation lists,
 * discovers chats and groups, and saves them into the local dynamic contact pool.
 */
class WhatsAppChatScanner(
    private val service: AccessibilityService,
    private val loggerTag: String = "WAChatScanner",
) {

    /** Target filtering scope for scanner discovery. */
    enum class ScanTarget {
        /** Discover both direct personal conversations and groups. */
        ALL_CHATS,
        /** Filter strictly for group chat entities. */
        GROUPS_ONLY
    }

    private enum class DeepScanPhase {
        HARVEST_ROWS,
        OPEN_GROUP_CHAT,
        EXTRACT_GROUP_METADATA,
        RETURN_TO_GROUPS_LIST,
        SCROLL_DOWN,
        COMPLETE
    }

    var scanTarget: ScanTarget = ScanTarget.ALL_CHATS
    private var currentPhase = DeepScanPhase.HARVEST_ROWS
    private val discoveredGroups = linkedMapOf<String, WhatsAppGroupItem>()
    private val discoveredChats = linkedMapOf<String, DynamicContactItem>()
    private val visitedGroupTitles = mutableSetOf<String>()
    private var currentGroupTargetTitle: String = ""
    private var stepAttempts = 0
    private var scrollCycles = 0
    private val maxScrollCycles = 150
    private var lastDiscoveredCount = 0
    private var consecutiveStagnantCycles = 0
    private val maxStagnantCycles = 3

    /**
     * Clears all temporary discovery caches and resets traversal state for a fresh chat scanning pass.
     *
     * @param target Discovery filter scope (ALL_CHATS or GROUPS_ONLY).
     */
    fun resetScanState(target: ScanTarget = ScanTarget.ALL_CHATS) {
        scanTarget = target
        currentPhase = DeepScanPhase.HARVEST_ROWS
        discoveredGroups.clear()
        discoveredChats.clear()
        visitedGroupTitles.clear()
        currentGroupTargetTitle = ""
        stepAttempts = 0
        scrollCycles = 0
        lastDiscoveredCount = 0
        consecutiveStagnantCycles = 0
        Log.d(loggerTag, "Reset WhatsAppChatScanner state for full scan (target=$scanTarget, maxCycles=$maxScrollCycles)")
    }

    /**
     * Drives accessibility node inspection across recent conversation lists, harvesting group and chat entities.
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
                DeepScanPhase.HARVEST_ROWS -> {
                    val rowContainers = findAllRowContainers(root)
                    val isBiz = pkg.contains("w4b", ignoreCase = true)
                    val baseSrc = if (isBiz) "WA_BUSINESS" else "WA_CHAT"

                    for (row in rowContainers) {
                        val title = extractTitleFromRow(row) ?: continue
                        val cleanTitle = title.trim().removePrefix("~").trim()
                        if (cleanTitle.isNotBlank() && repo.isValidPickedChatName(cleanTitle)) {
                            val phoneDigits = cleanTitle.filter { it.isDigit() }
                            val isPhone = phoneDigits.length >= 7
                            val isBroadcast = cleanTitle.contains("broadcast", ignoreCase = true) || cleanTitle.contains("recipients", ignoreCase = true)
                            val isStatus = cleanTitle.equals("status", ignoreCase = true) || cleanTitle.equals("my status", ignoreCase = true)

                            if (isStatus) continue

                            if (isPhone) {
                                // 1. Unsaved phone number chat
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
                                // Check if this title is a SAVED contact in the device phonebook or dynamic pool
                                val phoneFromBook = repo.findPhoneForContactName(cleanTitle)
                                if (!phoneFromBook.isNullOrBlank()) {
                                    // 2. Saved contact in phonebook!
                                    val norm = PhoneNumberNormalizer.normalize(phoneFromBook)
                                    val photo = repo.findPhotoForContactName(cleanTitle)

                                    if (!discoveredChats.containsKey(norm)) {
                                        discoveredChats[norm] = DynamicContactItem(
                                            name = cleanTitle,
                                            phone = phoneFromBook,
                                            normalizedNumber = norm,
                                            source = baseSrc,
                                            photoUri = photo,
                                        )
                                        Log.i(loggerTag, "Harvested saved contact chat: '$cleanTitle' -> $phoneFromBook ($baseSrc)")
                                    }
                                } else {
                                    val hasGroupSignals = (scanTarget == ScanTarget.GROUPS_ONLY) || hasGroupSignalsInRow(row)
                                    val hasBroadcastSignals = isBroadcast || hasBroadcastSignalsInRow(row)

                                    if (hasBroadcastSignals) {
                                        val targetPhone = "broadcast:$cleanTitle"
                                        val photo = repo.findPhotoForContactName(cleanTitle)
                                        if (!discoveredChats.containsKey(targetPhone)) {
                                            discoveredChats[targetPhone] = DynamicContactItem(
                                                name = cleanTitle,
                                                phone = targetPhone,
                                                normalizedNumber = targetPhone,
                                                source = "WA_BROADCAST",
                                                photoUri = photo,
                                            )
                                            Log.i(loggerTag, "Harvested broadcast chat: '$cleanTitle'")
                                        }
                                    } else if (hasGroupSignals && repo.isValidGroupName(cleanTitle)) {
                                        // 3. Valid Group Chat!
                                        val groupPhone = "group:$cleanTitle"
                                        val groupNorm = "group:$cleanTitle"
                                        val groupSource = if (isBiz) "WA_BUSINESS" else "WA_GROUP"

                                        if (!discoveredGroups.containsKey(cleanTitle)) {
                                            discoveredGroups[cleanTitle] = WhatsAppGroupItem(
                                                name = cleanTitle,
                                                memberCountStr = if (isBiz) "WhatsApp Business Group" else "WhatsApp Group",
                                            )
                                            Log.i(loggerTag, "Harvested group from row: '$cleanTitle'")
                                        }
                                        visitedGroupTitles.add(cleanTitle)

                                        if (!discoveredChats.containsKey(groupNorm)) {
                                            discoveredChats[groupNorm] = DynamicContactItem(
                                                name = cleanTitle,
                                                phone = groupPhone,
                                                normalizedNumber = groupNorm,
                                                source = groupSource,
                                                photoUri = "",
                                            )
                                            Log.i(loggerTag, "Harvested group chat for pool: '$cleanTitle' -> $groupPhone ($groupSource)")
                                        }
                                    } else {
                                        // 4. Individual contact chat (not a group)
                                        val targetPhone = cleanTitle
                                        val photo = repo.findPhotoForContactName(cleanTitle)
                                        val norm = PhoneNumberNormalizer.normalize(cleanTitle)

                                        if (!discoveredChats.containsKey(norm)) {
                                            discoveredChats[norm] = DynamicContactItem(
                                                name = cleanTitle,
                                                phone = targetPhone,
                                                normalizedNumber = norm,
                                                source = baseSrc,
                                                photoUri = photo,
                                            )
                                            Log.i(loggerTag, "Harvested individual chat row: '$cleanTitle' ($baseSrc)")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val totalDiscovered = discoveredGroups.size + discoveredChats.size
                    if (totalDiscovered > lastDiscoveredCount) {
                        consecutiveStagnantCycles = 0
                        lastDiscoveredCount = totalDiscovered
                    }

                    val candidateForDeepInspect = if ((scanTarget == ScanTarget.GROUPS_ONLY) && (visitedGroupTitles.size <= 3)) {
                        val validRows = rowContainers.mapNotNull { r ->
                            val t = extractTitleFromRow(r)?.trim()?.removePrefix("~")?.trim().orEmpty()
                            if (t.isNotBlank() && repo.isValidGroupName(t) && !t.all { char -> char.isDigit() || (char in "+- ()") }) Pair(r, t) else null
                        }
                        validRows.firstOrNull { (_, title) -> !visitedGroupTitles.contains(title) }
                    } else null

                    if (candidateForDeepInspect != null) {
                        val (rowNode, title) = candidateForDeepInspect
                        currentGroupTargetTitle = title
                        visitedGroupTitles.add(title)
                        Log.i(loggerTag, "Opening group chat for exact metadata: '$title'")
                        AccessibilityNodeUtils.clickElement(rowNode, service)
                        currentPhase = DeepScanPhase.EXTRACT_GROUP_METADATA
                        stepAttempts = 0
                        onRetry(650L)
                    } else if ((scrollCycles < maxScrollCycles) && (consecutiveStagnantCycles < maxStagnantCycles)) {
                        currentPhase = DeepScanPhase.SCROLL_DOWN
                        stepAttempts = 0
                        onRetry(350L)
                    } else {
                        Log.i(loggerTag, "Harvest complete (all chats scanned). Total groups: ${discoveredGroups.size}, chats: ${discoveredChats.size}, cycles: $scrollCycles")
                        finishAndSave(onFinish)
                    }
                }

                DeepScanPhase.OPEN_GROUP_CHAT -> {
                    currentPhase = DeepScanPhase.EXTRACT_GROUP_METADATA
                    stepAttempts = 0
                    onRetry(400L)
                }

                DeepScanPhase.EXTRACT_GROUP_METADATA -> {
                    val groupHeader = findConversationHeader(root)
                    if (groupHeader != null) {
                        val (extractedName, subtitle) = groupHeader
                        val nameToUse = extractedName.ifBlank { currentGroupTargetTitle }
                        val isGroup = (subtitle.contains("members", ignoreCase = true) ||
                                subtitle.contains("group", ignoreCase = true) ||
                                subtitle.contains("participants", ignoreCase = true) ||
                                subtitle.contains("सदस्य", ignoreCase = true)) &&
                                !subtitle.contains("online", ignoreCase = true) &&
                                !subtitle.contains("last seen", ignoreCase = true) &&
                                !subtitle.contains("typing", ignoreCase = true)

                        if ((isGroup || subtitle.isBlank()) && nameToUse.isNotBlank() && repo.isValidGroupName(nameToUse)) {
                            val existing = discoveredGroups[nameToUse] ?: WhatsAppGroupItem(name = nameToUse)
                            val updatedCount = if (subtitle.contains("member", ignoreCase = true) || subtitle.contains("participant", ignoreCase = true) || subtitle.contains("सदस्य", ignoreCase = true)) subtitle else existing.memberCountStr
                            discoveredGroups[nameToUse] = existing.copy(
                                name = nameToUse,
                                memberCountStr = updatedCount,
                            )
                            Log.i(loggerTag, "Verified exact group: '$nameToUse' ($subtitle)")
                        }

                        Log.d(loggerTag, "Navigating back to group list...")
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        currentPhase = DeepScanPhase.RETURN_TO_GROUPS_LIST
                        stepAttempts = 0
                        onRetry(500L)
                    } else {
                        stepAttempts++
                        if (stepAttempts <= 4) {
                            onRetry(350L)
                        } else {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                            currentPhase = DeepScanPhase.RETURN_TO_GROUPS_LIST
                            stepAttempts = 0
                            onRetry(400L)
                        }
                    }
                }

                DeepScanPhase.RETURN_TO_GROUPS_LIST -> {
                    val isBack = isGroupListScreen(root)
                    if (isBack) {
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        stepAttempts = 0
                        onRetry(350L)
                    } else {
                        stepAttempts++
                        if (stepAttempts > 2) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                            onRetry(450L)
                        } else {
                            onRetry(300L)
                        }
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

                    val totalDiscovered = discoveredGroups.size + discoveredChats.size
                    if (totalDiscovered == lastDiscoveredCount) {
                        consecutiveStagnantCycles++
                        Log.d(loggerTag, "No new chats in cycle $scrollCycles (stagnant=$consecutiveStagnantCycles/$maxStagnantCycles)")
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
                        Log.i(loggerTag, "Reached end of all chats or maximum cycles, finishing scan. Discovered: $totalDiscovered")
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
        val validItems = discoveredGroups.values.filter { (_, name) -> repo.isValidGroupName(name) }
        val savedGroups = if (validItems.isNotEmpty()) {
            val count = repo.bulkSaveWhatsAppGroupItems(validItems)
            Log.i(loggerTag, "finishAndSave: Saved $count exact groups to database")
            count
        } else 0
        val savedChats = if (discoveredChats.isNotEmpty()) {
            val count = repo.upsertDynamicPoolBatch(discoveredChats.values.toList())
            Log.i(loggerTag, "finishAndSave: Ingested $count chats into dynamic pool")
            count
        } else 0
        service.showAppToast("Synced $savedGroups groups & $savedChats chats", isLong = true)

        currentPhase = DeepScanPhase.COMPLETE
        onFinish()

        val appIntent = service.packageManager.getLaunchIntentForPackage(service.packageName)
        if (appIntent != null) {
            appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            service.startActivity(appIntent)
        }
    }

    private fun isGroupListScreen(root: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            if (id.endsWith("result_list") ||
                id.endsWith("conversations_filter_debug_view_id_groups") ||
                id.endsWith("smart_filter_token") ||
                id.endsWith("my_search_bar") ||
                id.endsWith("conversation_list_view_host") ||
                id.endsWith("bottom_nav") ||
                id.endsWith("conversations_row_lists_manage_footer") ||
                id.endsWith("contact_row_container")
            ) {
                return true
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

            val isHorizontalPager = id.contains("pager") || cls.contains("ViewPager")

            if (!isHorizontalPager) {
                if ((id == "android:id/list") || cls.contains("RecyclerView") || cls.contains("ListView")) {
                    return node
                }
                if (node.isScrollable && (fallbackScrollable == null)) {
                    fallbackScrollable = node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return fallbackScrollable
    }

    private fun findConversationHeader(root: AccessibilityNodeInfo): Pair<String, String>? {
        var groupTitle = ""
        var statusOrSubtitle = ""

        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()

            if (id.endsWith("conversation_contact_name") || id.endsWith("group_title")) {
                val t = node.text?.toString()?.trim()
                if (!t.isNullOrBlank()) groupTitle = t
            }
            if (id.endsWith("conversation_contact_status") || id.endsWith("group_details_card_subtitle")) {
                val s = node.text?.toString()?.trim()
                if (!s.isNullOrBlank()) statusOrSubtitle = s
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        if (groupTitle.isNotBlank()) {
            return Pair(groupTitle, statusOrSubtitle)
        }
        return null
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

            // 1. Direct view ID match
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

            // 2. Avatar profile picture contentDescription check: "Profile picture of {Name}"
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

        // 3. Row container contentDescription fallback: often contains "{Title}, {Time}, {Snippet}"
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

        // 4. Fallback to first sensible non-time text node in the row
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

    private fun hasGroupSignalsInRow(row: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val desc = n.contentDescription?.toString()?.lowercase().orEmpty()
            val txt = n.text?.toString()?.lowercase().orEmpty()
            val id = n.viewIdResourceName?.lowercase().orEmpty()

            if (desc.contains("group profile") || desc.contains("group icon") || desc.contains("group photo") ||
                desc.startsWith("group ") || desc.contains(" group") || id.contains("group")
            ) {
                return true
            }
            if (txt.contains(" participants") || txt.contains(" members") || txt.contains(" सदस्य")) {
                return true
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    private fun hasBroadcastSignalsInRow(row: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(row)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val desc = n.contentDescription?.toString()?.lowercase().orEmpty()
            val txt = n.text?.toString()?.lowercase().orEmpty()
            val id = n.viewIdResourceName?.lowercase().orEmpty()

            if (desc.contains("broadcast") || id.contains("broadcast") || txt.contains("recipient")) {
                return true
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }
}
