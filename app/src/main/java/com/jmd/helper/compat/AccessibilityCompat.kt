/**
 * Package: com.jmd.helper.compat
 * Module: Compat Layer
 *
 * Provides safe accessibility node state extraction across evolving Android accessibility APIs.
 */
package com.jmd.helper.compat

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Backward-compatible helper for [AccessibilityNodeInfo] inspection.
 *
 * Safely accesses toggle and checkbox checked states across legacy and modern Android releases.
 * On Android 16+ (API 36+), uses tri-state checked evaluation.
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
