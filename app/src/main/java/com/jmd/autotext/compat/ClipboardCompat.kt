/**
 * Package: com.jmd.autotext.compat
 * Module: Compat Layer
 *
 * Provides safe clipboard synchronization and clip attachment across Android versions.
 */
package com.jmd.autotext.compat

import android.content.ClipData
import android.content.ClipboardManager

/**
 * Backward-compatible helper for [ClipboardManager] interactions.
 *
 * Guarantees null safety and suppresses runtime clipboard transaction faults when setting primary clips.
 */
object ClipboardCompat {

    /**
     * Safely assigns primary clip data to system clipboard.
     *
     * @param clipboardManager System [ClipboardManager] service or null.
     * @param clipData [ClipData] payload to set or null.
     */
    @JvmStatic
    fun setPrimaryClip(clipboardManager: ClipboardManager?, clipData: ClipData?) {
        if (clipboardManager != null && clipData != null) {
            try {
                clipboardManager.setPrimaryClip(clipData)
            } catch (_: Throwable) {
                // Clipboard access error ignored for resilience
            }
        }
    }
}
