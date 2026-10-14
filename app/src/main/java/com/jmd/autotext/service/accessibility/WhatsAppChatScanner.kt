package com.jmd.autotext.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.WhatsAppGroupItem
import java.util.ArrayDeque

class WhatsAppChatScanner(
    private val service: AccessibilityService,
    private val loggerTag: String = "WAChatScanner"
) {

    private enum class DeepScanPhase {
        ACTIVATE_GROUPS_FILTER,
        HARVEST_ROWS,
        OPEN_GROUP_CHAT,
        EXTRACT_GROUP_METADATA,
        RETURN_TO_GROUPS_LIST,
        SCROLL_DOWN,
        COMPLETE
    }

    private var currentPhase = DeepScanPhase.ACTIVATE_GROUPS_FILTER
    private val discoveredGroups = linkedMapOf<String, WhatsAppGroupItem>()
    private val visitedGroupTitles = mutableSetOf<String>()
    private var currentGroupTargetTitle: String = ""
    private var stepAttempts = 0
    private var scrollCycles = 0
    private val maxScrollCycles = 5

    fun resetScanState() {
        currentPhase = DeepScanPhase.ACTIVATE_GROUPS_FILTER
        discoveredGroups.clear()
        visitedGroupTitles.clear()
        currentGroupTargetTitle = ""
        stepAttempts = 0
        scrollCycles = 0
        Log.d(loggerTag, "Reset WhatsAppChatScanner state for fresh deep scan")
    }

    fun tryScanWhatsAppChats(
        root: AccessibilityNodeInfo,
        pkg: String,
        sendAttemptCount: Int,
        onRetry: (delayMs: Long) -> Unit,
        onFinish: () -> Unit
    ) {
        try {
            val curPkg = root.packageName?.toString().orEmpty()
            if (!curPkg.contains(pkg, ignoreCase = true) && !pkg.contains(curPkg, ignoreCase = true)) {
                if (sendAttemptCount <= 20) {
                    onRetry(350L)
                } else {
                    finishAndSave(onFinish)
                }
                return
            }

            val repo = AppRepository(service)

            when (currentPhase) {
                DeepScanPhase.ACTIVATE_GROUPS_FILTER -> {
                    // Check if Groups filter is already active or search token is Groups
                    val isTokenPresent = hasGroupsFilterTokenOrSearch(root)
                    if (isTokenPresent) {
                        Log.i(loggerTag, "Groups filter tab/token already active! Proceeding to harvest rows.")
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        stepAttempts = 0
                        onRetry(300L)
                        return
                    }

                    val groupsFilterTab = findGroupsFilterTab(root)
                    if (groupsFilterTab != null) {
                        val desc = groupsFilterTab.contentDescription?.toString().orEmpty()
                        Log.i(loggerTag, "Activating Groups Filter: $desc")
                        AccessibilityNodeUtils.clickElement(groupsFilterTab, service)
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        stepAttempts = 0
                        onRetry(550L)
                        return
                    }

                    stepAttempts++
                    if (stepAttempts > 4) {
                        // If no filter tab found (e.g. tablet or alternate layout), proceed to harvest anyway
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        stepAttempts = 0
                        onRetry(300L)
                    } else {
                        onRetry(400L)
                    }
                }

                DeepScanPhase.HARVEST_ROWS -> {
                    // Check if WhatsApp displays the "Create a group" empty state when Groups filter is active
                    if (isGroupsEmptyState(root)) {
                        Log.i(loggerTag, "WhatsApp indicates no groups found on account (empty state displayed).")
                        finishAndSave(onFinish)
                        return
                    }

                    // Harvest all visible groups on current screen
                    val visibleRows = getVisibleGroupRows(root, repo)
                    var newlyHarvested = 0

                    for ((_, title) in visibleRows) {
                        if (repo.isValidGroupName(title)) {
                            if (!discoveredGroups.containsKey(title)) {
                                discoveredGroups[title] = WhatsAppGroupItem(name = title, memberCountStr = "WhatsApp Group")
                                Log.i(loggerTag, "Harvested group from row: '$title'")
                                newlyHarvested++
                            }
                            visitedGroupTitles.add(title)
                        }
                    }

                    // Check if there is an uninspected group we want to fetch deeper metadata for (limit to first 3 to prevent infinite loops)
                    val candidateForDeepInspect = if (visitedGroupTitles.size <= 3) {
                        visibleRows.firstOrNull { (_, title) -> !visitedGroupTitles.contains(title) }
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
                    } else if (scrollCycles < maxScrollCycles) {
                        currentPhase = DeepScanPhase.SCROLL_DOWN
                        stepAttempts = 0
                        onRetry(350L)
                    } else {
                        Log.i(loggerTag, "Harvest complete. Total groups collected: ${discoveredGroups.size}")
                        finishAndSave(onFinish)
                    }
                }

                DeepScanPhase.OPEN_GROUP_CHAT -> {
                    // Fallback state if opening was delayed
                    currentPhase = DeepScanPhase.EXTRACT_GROUP_METADATA
                    stepAttempts = 0
                    onRetry(400L)
                }

                DeepScanPhase.EXTRACT_GROUP_METADATA -> {
                    // Inside chat or group info
                    val groupHeader = findConversationHeader(root)
                    if (groupHeader != null) {
                        val (extractedName, subtitle) = groupHeader
                        val nameToUse = extractedName.ifBlank { currentGroupTargetTitle }
                        // Strict check: only accept real groups with member count or group indicator
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
                                memberCountStr = updatedCount
                            )
                            Log.i(loggerTag, "Verified exact group: '$nameToUse' ($subtitle)")
                        }

                        // Press back to return to the group list
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
                            // If took too long, press back and continue to next group
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                            currentPhase = DeepScanPhase.RETURN_TO_GROUPS_LIST
                            stepAttempts = 0
                            onRetry(400L)
                        }
                    }
                }

                DeepScanPhase.RETURN_TO_GROUPS_LIST -> {
                    // Check if back on group list or chat list screen
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
                    if (scrollableNode != null) {
                        val scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        Log.i(loggerTag, "Scrolled group list forward: result=$scrolled")
                        scrollCycles++
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        stepAttempts = 0
                        onRetry(600L)
                    } else {
                        // Could not find scrollable container, conclude scan
                        Log.i(loggerTag, "No scrollable container found, finishing scan.")
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
        val validItems = discoveredGroups.values.filter { repo.isValidGroupName(it.name) }
        if (validItems.isNotEmpty()) {
            val savedCount = repo.bulkSaveWhatsAppGroupItems(validItems)
            Log.i(loggerTag, "finishAndSave: Saved $savedCount exact groups to database")
            Toast.makeText(service, "✅ Successfully synced ${validItems.size} WhatsApp groups!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(service, "Group scan finished", Toast.LENGTH_SHORT).show()
        }

        currentPhase = DeepScanPhase.COMPLETE
        onFinish()

        // Return to AutoText app
        val appIntent = service.packageManager.getLaunchIntentForPackage(service.packageName)
        if (appIntent != null) {
            appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            service.startActivity(appIntent)
        }
    }

    private fun hasGroupsFilterTokenOrSearch(root: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            if (id.endsWith("smart_filter_token") && text.contains("groups")) return true
            if (id.endsWith("search_input") && text.contains("group")) return true
            if (desc.contains("groups filter") && desc.contains("selected") && !desc.contains("unselected")) return true
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return false
    }

    private fun isGroupsEmptyState(root: AccessibilityNodeInfo): Boolean {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()
            if (id.endsWith("no_groups_filter_cta_text_layout") ||
                id.endsWith("no_groups_filter_create_group_button") ||
                (text == "create a group" && id.contains("button"))) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return false
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
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return null
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

    private fun getVisibleGroupRows(
        root: AccessibilityNodeInfo,
        repo: AppRepository
    ): List<Pair<AccessibilityNodeInfo, String>> {
        val result = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
        val rowContainers = findAllRowContainers(root)

        for (row in rowContainers) {
            val title = extractTitleFromRow(row)
            if (!title.isNullOrBlank() && repo.isValidGroupName(title)) {
                result.add(Pair(row, title))
            }
        }
        return result
    }

    private fun findGroupsFilterTab(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()

            val isGroupKeywords = text == "groups" || text == "समूह" || text == "grupos" || text == "groupes" ||
                    desc.contains("groups") || desc.contains("समूह") || desc.contains("grupos")

            if (id.endsWith("conversations_filter_debug_view_id_groups") ||
                (isGroupKeywords && (node.isClickable || node.isCheckable || node.parent?.isClickable == true))
            ) {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable && !target.isCheckable) {
                    target = target.parent
                }
                return target ?: node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
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

        // Fallback: search for any clickable node that has a title TextView
        if (containers.isEmpty()) {
            val fq = ArrayDeque<AccessibilityNodeInfo>()
            fq.add(root)
            while (fq.isNotEmpty()) {
                val node = fq.removeFirst()
                if (node.isClickable && extractTitleFromRow(node) != null) {
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
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val id = node.viewIdResourceName?.lowercase().orEmpty()

            if (id.endsWith("conversations_row_contact_name") ||
                id.endsWith("chat_title") ||
                id.endsWith("contactpicker_row_name") ||
                id.endsWith("conversation_contact_name") ||
                id.endsWith("name") ||
                id.endsWith("title")
            ) {
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrBlank()) {
                    return text
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }
        return null
    }
}
