package com.jmd.autotext.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.ArrayDeque

object AccessibilityNodeUtils {

    private const val TAG = "A11yNodeUtils"

    fun findNodesByViewId(
        root: AccessibilityNodeInfo?,
        targetPkg: String,
        idName: String,
        windows: List<AccessibilityWindowInfo>? = null
    ): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val possibleIds = listOf(
            "$targetPkg:id/$idName",
            "com.whatsapp:id/$idName",
            "com.whatsapp.w4b:id/$idName",
            "android:id/$idName"
        ).distinct()

        val results = mutableListOf<AccessibilityNodeInfo>()
        for (id in possibleIds) {
            val found = root.findAccessibilityNodeInfosByViewId(id)
            if (!found.isNullOrEmpty()) {
                results.addAll(found)
            }
        }

        if (results.isNotEmpty()) return results

        // BFS fallback searching by ending view ID name
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val resId = node.viewIdResourceName ?: ""
            if (resId.endsWith(":id/$idName", ignoreCase = true) || resId.equals(idName, ignoreCase = true)) {
                results.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        if (results.isNotEmpty()) return results

        // Multi-window fallback
        if (windows != null) {
            try {
                windows.reversed().forEach { win ->
                    if (win.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        win.root?.let { r ->
                            for (id in possibleIds) {
                                val found = r.findAccessibilityNodeInfosByViewId(id)
                                if (!found.isNullOrEmpty()) {
                                    results.addAll(found)
                                }
                            }
                            if (results.isEmpty()) {
                                val wq = ArrayDeque<AccessibilityNodeInfo>()
                                wq.add(r)
                                while (wq.isNotEmpty()) {
                                    val node = wq.removeFirst()
                                    val resId = node.viewIdResourceName ?: ""
                                    if (resId.endsWith(":id/$idName", ignoreCase = true) || resId.equals(idName, ignoreCase = true)) {
                                        results.add(node)
                                    }
                                    for (i in 0 until node.childCount) {
                                        node.getChild(i)?.let { wq.add(it) }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        return results
    }

    fun findNodeByTextOrDesc(
        root: AccessibilityNodeInfo?,
        keywords: List<String>,
        windows: List<AccessibilityWindowInfo>? = null,
        exact: Boolean = false
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val lowerKeywords = keywords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }

        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""

            for (kw in lowerKeywords) {
                if (exact) {
                    if (text == kw || desc == kw) return node
                } else {
                    if ((text.isNotEmpty() && text.contains(kw)) || (desc.isNotEmpty() && desc.contains(kw))) {
                        return node
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { q.add(it) }
            }
        }

        // Multi-window fallback
        if (windows != null) {
            try {
                windows.reversed().forEach { win ->
                    if (win.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        win.root?.let { r ->
                            val wq = ArrayDeque<AccessibilityNodeInfo>()
                            wq.add(r)
                            while (wq.isNotEmpty()) {
                                val node = wq.removeFirst()
                                val text = node.text?.toString()?.lowercase()?.trim() ?: ""
                                val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""

                                for (kw in lowerKeywords) {
                                    if (exact) {
                                        if (text == kw || desc == kw) return node
                                    } else {
                                        if ((text.isNotEmpty() && text.contains(kw)) || (desc.isNotEmpty() && desc.contains(kw))) {
                                            return node
                                        }
                                    }
                                }

                                for (i in 0 until node.childCount) {
                                    node.getChild(i)?.let { wq.add(it) }
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        return null
    }

    /**
     * Pure Element-Based Clicker:
     * 1. Checks if target is clickable and executes ACTION_CLICK.
     * 2. Walks up the ancestor chain looking for a clickable container and executes ACTION_CLICK.
     * 3. Checks direct children for clickable elements.
     * 4. Attempts ACTION_CLICK directly on target even if isClickable is reported false by OEM.
     * 5. Attempts ACTION_SELECT.
     * 6. Extreme fallback: only dispatches a hardware tap if all element-based actions failed.
     */
    fun clickElement(
        node: AccessibilityNodeInfo?,
        service: AccessibilityService,
        onTapFallbackNeeded: ((x: Float, y: Float) -> Unit)? = null
    ): Boolean {
        if (node == null) return false

        val rect = Rect()
        node.getBoundsInScreen(rect)
        val hasBounds = rect.width() > 0 && rect.height() > 0
        val resId = node.viewIdResourceName?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()

        // 1. Direct click on node if clickable
        var elementActionSuccess = false
        if (node.isClickable) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                Log.d(TAG, "clickElement: Direct ACTION_CLICK succeeded on ${node.viewIdResourceName ?: node.className}")
                elementActionSuccess = true
            }
        }

        // 2. Walk up parent hierarchy for clickable container
        if (!elementActionSuccess) {
            var parent = node.parent
            var depth = 0
            while (parent != null && depth < 6) {
                if (parent.isClickable) {
                    val parentClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (parentClicked) {
                        Log.d(TAG, "clickElement: Parent ACTION_CLICK succeeded on ${parent.viewIdResourceName ?: parent.className}")
                        elementActionSuccess = true
                        break
                    }
                }
                parent = parent.parent
                depth++
            }
        }

        // 3. Check direct children for clickable element
        if (!elementActionSuccess) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null && child.isClickable) {
                    val childClicked = child.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (childClicked) {
                        Log.d(TAG, "clickElement: Child ACTION_CLICK succeeded on ${child.viewIdResourceName ?: child.className}")
                        elementActionSuccess = true
                        break
                    }
                }
            }
        }

        // 4. Try ACTION_CLICK on node regardless of isClickable flag
        if (!elementActionSuccess && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "clickElement: Forced ACTION_CLICK succeeded on ${node.viewIdResourceName ?: node.className}")
            elementActionSuccess = true
        }

        // 5. Use a physical gesture when node has valid bounds and standard ACTION_CLICK did not apply
        if (hasBounds && !elementActionSuccess) {
            val tapX = rect.centerX().toFloat()
            val tapY = rect.centerY().toFloat()
            Log.d(TAG, "clickElement: Dispatching gesture tap to bounds center ($tapX, $tapY) for $resId / $desc")
            if (onTapFallbackNeeded != null) {
                onTapFallbackNeeded(tapX, tapY)
            } else {
                dispatchPhysicalTap(service, tapX, tapY)
            }
            return true
        }

        return elementActionSuccess
    }

    /**
     * Backward-compatible alias for clickElement.
     */
    fun clickNodeProperly(
        node: AccessibilityNodeInfo,
        service: AccessibilityService,
        onTapFallbackNeeded: ((x: Float, y: Float) -> Unit)? = null
    ): Boolean {
        return clickElement(node, service, onTapFallbackNeeded)
    }

    /**
     * Injects text into an input field using ACTION_SET_TEXT combined with clipboard paste fallback,
     * ensuring TextWatcher and IME listeners in WhatsApp are reliably triggered.
     */
    fun setTextOnInput(
        node: AccessibilityNodeInfo?,
        text: String,
        service: AccessibilityService
    ): Boolean {
        if (node == null) return false
        try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val setSuccess = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (setSuccess) {
                return true
            }

            // Clipboard paste is a fallback only. Doing both ACTION_SET_TEXT and paste
            // appended the message twice on devices where SET_TEXT already worked.
            return try {
                val clip = ClipData.newPlainText("text", text)
                val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (cm == null) {
                    false
                } else {
                    cm.setPrimaryClip(clip)
                    node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }
            } catch (_: Exception) { false }
        } catch (t: Throwable) {
            Log.e(TAG, "setTextOnInput error: ${t.message}")
            return false
        }
    }

    fun dispatchPhysicalTap(
        service: AccessibilityService,
        x: Float,
        y: Float,
        durationMs: Long = 50L,
        onComplete: (() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Physical tap completed at ($x, $y)")
                onComplete?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Physical tap cancelled at ($x, $y)")
            }
        }, null)
    }

    fun findFirstScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
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

    fun isVoiceNode(node: AccessibilityNodeInfo): Boolean {
        return VoiceNoteProtectionGuard.isVoiceNode(node)
    }

    fun getTargetRoots(service: AccessibilityService, windows: List<AccessibilityWindowInfo>? = null): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        service.rootInActiveWindow?.let { roots.add(it) }

        if (windows != null) {
            try {
                windows.reversed().forEach { win ->
                    if (win.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        win.root?.let { r ->
                            if (!roots.contains(r)) roots.add(r)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        return roots
    }
}

