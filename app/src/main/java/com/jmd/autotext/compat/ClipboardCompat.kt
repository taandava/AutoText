/**
 * Package: com.jmd.autotext.compat
 * Module: Compat Layer
 *
 * Provides safe clipboard synchronization, clip attachment, sensitive content protection,
 * and automatic clipboard wipe across Android versions.
 */
package com.jmd.autotext.compat

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log

/**
 * Backward-compatible helper for [ClipboardManager] interactions with built-in privacy protection.
 */
object ClipboardCompat {

    private const val TAG = "ClipboardCompat"
    private val handler = Handler(Looper.getMainLooper())

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

    /**
     * Copies text to system clipboard with privacy safeguards:
     * 1. Marks content as sensitive ([ClipDescription.EXTRA_IS_SENSITIVE]) on Android 13+ (API 33+)
     *    to hide visual clipboard overlay and prevent clipboard manager leakage.
     * 2. Automatically schedules clipboard wipe after [autoClearSeconds] if > 0.
     * 3. Verifies target package exclusion via PrivacyManager before proceeding.
     */
    @JvmStatic
    fun copyToClipboardWithPrivacy(
        context: Context,
        label: String,
        text: String,
        targetPackage: String? = null,
        isSensitive: Boolean = true,
        autoClearSeconds: Int = 15
    ): Boolean {
        try {
            if (targetPackage != null) {
                val privacyMgr = com.jmd.autotext.core.PrivacyManager.getInstance(context)
                if (privacyMgr.isClipboardDisabledForPackage(targetPackage)) {
                    Log.d(TAG, "Clipboard interaction disabled for excluded package: $targetPackage")
                    return false
                }
            }

            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
            val clipData = ClipData.newPlainText(label, text)

            // On Android 13+ (API 33+), mark as sensitive to hide from clipboard UI
            if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    clipData.description.extras = (clipData.description.extras ?: PersistableBundle()).apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                } catch (_: Throwable) {}
            }

            cm.setPrimaryClip(clipData)

            // Auto-clear clipboard after timeout to prevent sensitive text from lingering
            if (autoClearSeconds > 0) {
                scheduleClipboardClear(context, text, autoClearSeconds)
            }
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "copyToClipboardWithPrivacy failed: ${t.message}")
            return false
        }
    }

    /**
     * Schedules a delayed wipe of system clipboard if it still contains the specified text.
     */
    @JvmStatic
    fun scheduleClipboardClear(context: Context, expectedText: String, delaySeconds: Int) {
        handler.postDelayed({
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return@postDelayed
                val currentClip = cm.primaryClip
                if (currentClip != null && currentClip.itemCount > 0) {
                    val currentText = currentClip.getItemAt(0)?.text?.toString()
                    if (currentText == expectedText) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            cm.clearPrimaryClip()
                        } else {
                            cm.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                        Log.d(TAG, "System clipboard cleared automatically for privacy.")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to clear clipboard: ${t.message}")
            }
        }, delaySeconds * 1000L)
    }

    /**
     * Clears primary clipboard immediately.
     */
    @JvmStatic
    fun clearClipboard(context: Context) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm.clearPrimaryClip()
            } else {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: Throwable) {}
    }
}
