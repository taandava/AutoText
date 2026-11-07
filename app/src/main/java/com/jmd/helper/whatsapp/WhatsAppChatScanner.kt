/**
 * Package: com.jmd.helper.whatsapp
 * Module: WhatsApp Task & Tree Traversal
 *
 * Discovery engine for harvesting WhatsApp direct chats via accessibility
 * node traversal, scrolling, and pool enrichment.
 */
package com.jmd.helper.whatsapp

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jmd.helper.core.PhoneNumberNormalizer
import com.jmd.helper.core.showAppToast
import com.jmd.helper.data.model.DynamicContactItem
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.service.TaskAccessibilityService
import com.jmd.helper.whatsapp.AccessibilityNodeUtils.recycleSafely

/**
 * Accessibility-based scanner engine that directly iterates WhatsApp and WhatsApp Business
 * conversation lists, discovers individual contacts and chats, and saves them into the local dynamic pool.
 */
class WhatsAppChatScanner(
    private val service: AccessibilityService,
    private val loggerTag: String = "WAChatScanner",
) {

    /** Target filtering scope for scanner discovery. */
    enum class ScanTarget {
        ALL_CHATS
    }

    private enum class DeepScanPhase {
        ENSURE_CHATS_TAB,
        HARVEST_ROWS,
        SCROLL_DOWN,
        COMPLETE
    }

    var scanTarget: ScanTarget = ScanTarget.ALL_CHATS
    private var activePkg: String = "com.whatsapp"
    private var currentPhase = DeepScanPhase.ENSURE_CHATS_TAB
    private val discoveredChats = linkedMapOf<String, DynamicContactItem>()
    private var bottomTabCheckAttempts = 0
    private var stepAttempts = 0
    private var scrollCycles = 0
    private val maxScrollCycles = 150
    private var lastDiscoveredCount = 0
    private var consecutiveStagnantCycles = 0
    private val maxStagnantCycles = 3

    /**
     * Clears discovery cache and resets traversal state for a fresh chat scanning pass.
     */
    fun resetScanState(target: ScanTarget = ScanTarget.ALL_CHATS) {
        scanTarget = target
        currentPhase = DeepScanPhase.ENSURE_CHATS_TAB
        discoveredChats.clear()
        bottomTabCheckAttempts = 0
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
                    if (chatsTab != null) {
                        val isSelected = chatsTab.isSelected || chatsTab.contentDescription?.contains("selected", true) == true
                        if (isSelected) {
                            Log.i(loggerTag, "Chats bottom tab is already active")
                            currentPhase = DeepScanPhase.HARVEST_ROWS
                            onRetry(250L)
                            return
                        } else {
                            val clicked = AccessibilityNodeUtils.clickElement(chatsTab, service)
                            Log.i(loggerTag, "Clicked Chats bottom tab: result=$clicked")
                            if (clicked) {
                                currentPhase = DeepScanPhase.HARVEST_ROWS
                                onRetry(450L)
                                return
                            }
                        }
                    }

                    if (bottomTabCheckAttempts >= 2) {
                        Log.d(loggerTag, "Chats bottom tab not found or already on conversation list, moving forward")
                        currentPhase = DeepScanPhase.HARVEST_ROWS
                        onRetry(200L)
                    } else {
                        onRetry(300L)
                    }
                }

                DeepScanPhase.HARVEST_ROWS -> {
                    val rowContainers = findAllRowContainers(root)
                    val isBiz = pkg.contains("w4b", ignoreCase = true)
                    val baseSrc = if (isBiz) "WA_BUSINESS" else "WA_CHAT"

                    for (row in rowContainers) {
                        val title = extractTitleFromRow(row) ?: continue
                        val cleanTitle = title.trim().removePrefix("~").trim()
                        if (cleanTitle.isBlank() || !repo.isValidPickedChatName(cleanTitle)) continue

                        val digits = cleanTitle.filter { it.isDigit() }
                        val letters = cleanTitle.filter { it.isLetter() }
                        val isPhone = digits.length >= 7 && letters.isEmpty()

                        if (isPhone) {
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
                            val isBroadcast = cleanTitle.contains("broadcast", ignoreCase = true) || cleanTitle.contains("recipients", ignoreCase = true)
                            if (!isBroadcast) {
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

                    val totalDiscovered = discoveredChats.size
                    if (totalDiscovered > lastDiscoveredCount) {
                        consecutiveStagnantCycles = 0
                        lastDiscoveredCount = totalDiscovered
                    }

                    if ((scrollCycles < maxScrollCycles) && (consecutiveStagnantCycles < maxStagnantCycles)) {
                        currentPhase = DeepScanPhase.SCROLL_DOWN
                        stepAttempts = 0
                        onRetry(350L)
                    } else {
                        Log.i(loggerTag, "Harvest complete. Chats: ${discoveredChats.size}, cycles: $scrollCycles")
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

                    val totalDiscovered = discoveredChats.size
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
        repo.deleteAllWhatsAppGroups()

        val savedChats = if (discoveredChats.isNotEmpty()) {
            val count = repo.upsertDynamicPoolBatch(discoveredChats.values.toList())
            Log.i(loggerTag, "finishAndSave: Ingested $count chats into dynamic pool")
            count
        } else 0

        val msg = "Synced $savedChats WhatsApp conversations"
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
        val scrollable = findScrollableList(root)

        // 1. Primary: Direct children of the scrollable conversation list
        if (scrollable != null && scrollable.childCount > 0) {
            for (i in 0 until scrollable.childCount) {
                val child = scrollable.getChild(i) ?: continue
                val bounds = Rect()
                child.getBoundsInScreen(bounds)
                val height = bounds.height()
                // Valid chat row height is between 70px and 550px and child is on-screen
                if (height in 70..550 && bounds.bottom > 150) {
                    containers.add(child)
                }
            }
            if (containers.isNotEmpty()) {
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
                containers.add(node)
            } else {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { q.add(it) }
                }
            }
        }

        // 3. Tertiary fallback: clickable nodes matching row dimensions with extractable titles
        if (containers.isEmpty()) {
            val fq = ArrayDeque<AccessibilityNodeInfo>()
            fq.add(root)
            while (fq.isNotEmpty()) {
                val node = fq.removeFirst()
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val height = bounds.height()
                if (node.isClickable && height in 70..550 && (extractTitleFromRow(node) != null)) {
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

            // Extract from avatar content descriptions (e.g. "Profile picture of ContactName")
            if (desc.startsWith("Profile picture of ", ignoreCase = true)) {
                val candidate = desc.substring(19).trim().removePrefix("~").trim()
                if (candidate.isNotBlank()) return candidate
            }
            if (desc.startsWith("Profile photo of ", ignoreCase = true)) {
                val candidate = desc.substring(17).trim().removePrefix("~").trim()
                if (candidate.isNotBlank()) return candidate
            }
            if (desc.startsWith("Profile photo, ", ignoreCase = true)) {
                val candidate = desc.substring(15).trim().removePrefix("~").trim()
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
