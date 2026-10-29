

/**
 * Package: com.jmd.autotext.whatsapp
 * Module: WhatsApp Automation & Tree Traversal
 *
 * Provides resilient accessibility node BFS search algorithms, boundary inspection,
 * gesture dispatch, physical coordinate tapping, and text-entry routines for UI automation.
 *
 * Linked With:
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Engines: [com.jmd.autotext.whatsapp.WhatsAppDirectSendEngine], [com.jmd.autotext.whatsapp.WhatsAppStatusEngine]
 * - Guard: [com.jmd.autotext.whatsapp.VoiceNoteProtectionGuard]
 */
package com.jmd.autotext.whatsapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.jmd.autotext.data.prefs.PreferenceManager

/**
 * Utility functions for navigating, searching, and manipulating accessibility node hierarchies.
 */
object AccessibilityNodeUtils {

    private const val TAG = "A11yNodeUtils"

    /** Returns whether an accessibility node is checked or toggled on. */
    fun isNodeChecked(node: AccessibilityNodeInfo?): Boolean =
        com.jmd.autotext.compat.AccessibilityCompat.isNodeChecked(node)


    /**
     * Traverses descendants breadth-first starting from this node, returning the first node satisfying [predicate].
     *
     * @param predicate Filter condition evaluated on each discovered node.
     * @return The first matching [AccessibilityNodeInfo] or `null` if none found.
     */
    fun AccessibilityNodeInfo.findFirstDescendant(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(this)
        while (q.isNotEmpty()) {
            val curr = q.removeFirst()
            if (predicate(curr)) return curr
            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { q.add(it) }
            }
        }
        return null
    }

    /**
     * Traverses all descendants breadth-first and collects all nodes satisfying [predicate].
     *
     * @param predicate Filter condition evaluated on each discovered node.
     * @return A list of matching [AccessibilityNodeInfo] elements.
     */
    fun AccessibilityNodeInfo.findAllDescendants(predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(this)
        while (q.isNotEmpty()) {
            val curr = q.removeFirst()
            if (predicate(curr)) list.add(curr)
            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { q.add(it) }
            }
        }
        return list
    }

    /**
     * Searches for accessibility nodes by view ID resource name across multiple fallback namespaces.
     *
     * @param root The root [AccessibilityNodeInfo] hierarchy to search.
     * @param targetPkg Target application package name.
     * @param idName The raw resource identifier string (e.g. "send", "entry").
     * @param windows Optional list of active accessibility windows to search if root does not yield matches.
     * @return List of matching [AccessibilityNodeInfo] instances.
     */
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
            if (!found.isNullOrEmpty()) results.addAll(found)
        }
        if (results.isNotEmpty()) return results

        val matchesPredicate: (AccessibilityNodeInfo) -> Boolean = { node ->
            val resId = node.viewIdResourceName ?: ""
            resId.endsWith(":id/$idName", ignoreCase = true) || resId.equals(idName, ignoreCase = true)
        }

        results.addAll(root.findAllDescendants(matchesPredicate))
        if (results.isNotEmpty() || windows == null) return results

        try {
            windows.reversed().forEach { win ->
                if (win.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    win.root?.let { r ->
                        for (id in possibleIds) {
                            val found = r.findAccessibilityNodeInfosByViewId(id)
                            if (!found.isNullOrEmpty()) results.addAll(found)
                        }
                        if (results.isEmpty()) results.addAll(r.findAllDescendants(matchesPredicate))
                    }
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Window node traversal exception: ${t.message}")
        }

        return results
    }

    /**
     * Finds the first accessibility node matching any string in [keywords] against its text or content description.
     *
     * @param root The root [AccessibilityNodeInfo] tree.
     * @param keywords Collection of strings to test against.
     * @param windows Optional list of active accessibility windows for secondary scanning.
     * @param exact When `true`, demands an exact case-insensitive match; otherwise accepts substrings.
     * @return Matching [AccessibilityNodeInfo] or `null` if no node matches.
     */
    fun findNodeByTextOrDesc(
        root: AccessibilityNodeInfo?,
        keywords: List<String>,
        windows: List<AccessibilityWindowInfo>? = null,
        exact: Boolean = false
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val lowerKeywords = keywords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }

        val matchesKeyword: (AccessibilityNodeInfo) -> Boolean = { node ->
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            lowerKeywords.any { kw ->
                if (exact) (text == kw || desc == kw)
                else (text.isNotEmpty() && text.contains(kw)) || (desc.isNotEmpty() && desc.contains(kw))
            }
        }

        root.findFirstDescendant(matchesKeyword)?.let { return it }

        if (windows != null) {
            try {
                windows.reversed().forEach { win ->
                    if (win.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        win.root?.findFirstDescendant(matchesKeyword)?.let { return it }
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Window node text scan exception: ${t.message}")
            }
        }

        return null
    }

    /**
     * Resiliently executes a click on an accessibility element using standard action dispatch,
     * parent traversal, child traversal, or physical coordinate tap gestures depending on user preferences.
     *
     * @param node Candidate [AccessibilityNodeInfo] to click.
     * @param service The active [AccessibilityService] to execute actions or gesture taps.
     * @param onTapFallbackNeeded Optional custom callback invoked if gesture tap fallback is required.
     * @return `true` if an accessibility action succeeded or a physical tap gesture was dispatched.
     */
    fun clickElement(
        node: AccessibilityNodeInfo?,
        service: AccessibilityService,
        onTapFallbackNeeded: ((x: Float, y: Float) -> Unit)? = null
    ): Boolean {
        if (node == null) return false

        val prefs = PreferenceManager(service)
        val strategy = prefs.fallbackClickStrategy
        val gestureEnabled = prefs.isGestureFallbackEnabled
        val offsetY = prefs.customClickCoordinateOffsetY.toFloat()

        val rect = Rect()
        node.getBoundsInScreen(rect)
        val hasBounds = rect.width() > 0 && rect.height() > 0
        val resId = node.viewIdResourceName?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()

        if (strategy == "GESTURE_FORCE" && hasBounds && gestureEnabled) {
            val tapX = rect.centerX().toFloat()
            val tapY = (rect.centerY() + offsetY).coerceAtLeast(0f)
            Log.d(TAG, "clickElement: GESTURE_FORCE active. Tap ($tapX, $tapY)")
            if (onTapFallbackNeeded != null) {
                onTapFallbackNeeded(tapX, tapY)
            } else {
                dispatchPhysicalTap(service, tapX, tapY)
            }
            return true
        }

        var elementActionSuccess = false
        if (node.isClickable) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                Log.d(TAG, "clickElement: Direct ACTION_CLICK succeeded on ${node.viewIdResourceName ?: node.className}")
                elementActionSuccess = true
            }
        }

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

        if (!elementActionSuccess && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "clickElement: Forced ACTION_CLICK succeeded on ${node.viewIdResourceName ?: node.className}")
            elementActionSuccess = true
        }

        if (hasBounds && !elementActionSuccess && gestureEnabled && strategy != "A11Y_FIRST") {
            val tapX = rect.centerX().toFloat()
            val tapY = (rect.centerY() + offsetY).coerceAtLeast(0f)
            Log.d(TAG, "clickElement: Dispatching gesture tap fallback to bounds center ($tapX, $tapY, offset=$offsetY) for $resId / $desc")
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
     * Inputs text into an editable accessibility node, attempting ACTION_SET_TEXT first
     * and falling back to clipboard copy-paste injection if direct text setting fails.
     *
     * @param node The editable [AccessibilityNodeInfo] target.
     * @param text The string content to populate.
     * @param service The active [AccessibilityService].
     * @return `true` if text was successfully set or pasted; `false` otherwise.
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
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true

            val clip = ClipData.newPlainText("text", text)
            val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
            com.jmd.autotext.compat.ClipboardCompat.setPrimaryClip(cm, clip)
            return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (t: Throwable) {
            Log.e(TAG, "setTextOnInput error: ${t.message}")
            return false
        }
    }

    /**
     * Synthesizes a physical touch tap gesture at exact screen coordinates (x, y).
     *
     * @param service The active [AccessibilityService] used to dispatch gestures.
     * @param x Screen horizontal coordinate in pixels.
     * @param y Screen vertical coordinate in pixels.
     * @param durationMs Tap touch hold duration in milliseconds (defaults to 50ms).
     * @param onComplete Optional callback fired when the gesture completes.
     */
    fun dispatchPhysicalTap(
        service: AccessibilityService,
        x: Float,
        y: Float,
        durationMs: Long = 50L,
        onComplete: (() -> Unit)? = null
    ) {
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

    /**
     * Synthesizes an automated vertical swipe gesture across the display.
     *
     * @param service The active [AccessibilityService].
     * @param startYRatio Screen height ratio for swipe start (e.g. 0.75 for 75% down).
     * @param endYRatio Screen height ratio for swipe termination (e.g. 0.25 for 25% down).
     * @param durationMs Duration of swipe movement in milliseconds.
     * @return `true` if gesture dispatch was accepted by the accessibility framework.
     */
    fun performVerticalSwipe(
        service: AccessibilityService,
        startYRatio: Float = 0.75f,
        endYRatio: Float = 0.25f,
        durationMs: Long = 280L
    ): Boolean {
        return try {
            val metrics = service.resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()
            val midX = width / 2f
            val startY = height * startYRatio
            val endY = height * endYRatio

            val path = Path().apply {
                moveTo(midX, startY)
                lineTo(midX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (t: Throwable) {
            Log.e(TAG, "performVerticalSwipe failed: ${t.message}")
            false
        }
    }

    /**
     * Finds the first descendant node in [root] marked as scrollable.
     *
     * @param root The parent [AccessibilityNodeInfo].
     * @return First scrollable [AccessibilityNodeInfo] or `null`.
     */
    fun findFirstScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        return root?.findFirstDescendant { it.isScrollable }
    }

    /**
     * Collects all target candidate root nodes across active windows, filtering out soft input methods.
     *
     * @param service The active [AccessibilityService].
     * @param windows Optional list of accessibility windows to inspect.
     * @return Distinct list of root [AccessibilityNodeInfo] instances.
     */
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
            } catch (t: Throwable) {
                Log.d(TAG, "Target roots retrieval exception: ${t.message}")
            }
        }
        return roots
    }
}
