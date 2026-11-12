/**
 * Package: com.jmd.helper.compat
 * Module: Platform Compatibility Layer
 *
 * What it is:
 * Architecture definition and implementation for [AccessibilityCompat].
 *
 * What it does:
 * Provides safe accessibility node state extraction across evolving Android accessibility APIs.
 *
 * Linked With:
 * - Android Framework & Jetpack: [android.os.Build], [android.view.accessibility.AccessibilityNodeInfo]
 */

package com.jmd.helper.compat

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * What it is:
 * Backward-compatible helper for [AccessibilityNodeInfo] inspection.
 *
 * What it does:
 * Safely accesses toggle and checkbox checked states across legacy and modern Android releases. On Android 16+ (API 36+), uses tri-state checked evaluation.
 *
 * Linked with:
 * - Architecture: [AccessibilityCompat], [AccessibilityCompat]
 */
object AccessibilityCompat {

    /**
     * What it is:
     * Determines whether an accessibility node is currently in a checked or selected state.
     *
     * What it does:
     * Executes isNodeChecked operations conforming to application constraints.
     *
     * Linked with:
     * - Architecture: [AccessibilityCompat], [isNodeChecked]
     * @param node The [AccessibilityNodeInfo] to inspect, or null.
     * @return True if checked, false otherwise.
     */
    @JvmStatic
    fun isNodeChecked(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        return if (Build.VERSION.SDK_INT >= 36) {
            node.checked == AccessibilityNodeInfo.CHECKED_STATE_TRUE
        } else {
            try {
                val isCheckedMethod = AccessibilityNodeInfo::class.java.getMethod("isChecked")
                (isCheckedMethod.invoke(node) as? Boolean) == true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
