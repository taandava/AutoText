/**
 * Package: com.jmd.autotext.compat
 * Module: Compat Layer
 *
 * Provides safe accessibility node state extraction across evolving Android accessibility APIs.
 */
package com.jmd.autotext.compat

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Backward-compatible helper for [AccessibilityNodeInfo] inspection.
 *
 * Safely accesses toggle and checkbox checked states across legacy and modern Android releases.
 */
object AccessibilityCompat {

    /**
     * Determines whether an accessibility node is currently in a checked or selected state.
     *
     * @param node The [AccessibilityNodeInfo] to inspect, or null.
     * @return True if checked, false otherwise.
     */
    @JvmStatic
    fun isNodeChecked(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (Build.VERSION.SDK_INT >= 36) {
            return node.checked == AccessibilityNodeInfo.CHECKED_STATE_TRUE
        }
        return runCatching {
            val method = AccessibilityNodeInfo::class.java.getMethod("isChecked")
            (method.invoke(node) as? Boolean) ?: false
        }.getOrDefault(false)
    }
}
