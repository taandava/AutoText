

/**
 * Package: com.jmd.autotext.whatsapp
 * Module: WhatsApp Automation Subsystem
 *
 * WhatsApp dialog and confirmation interceptor. Gracefully handles in-app modal prompts,
 * "Continue" or "OK" disclaimers, and network/session popups strictly scoped to WhatsApp packages.
 *
 * Linked With:
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Logging: [com.jmd.autotext.core.AppFileLogger]
 */
package com.jmd.autotext.whatsapp

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.data.model.ExecutionLog
import com.jmd.autotext.data.repo.AppRepository

/**
 * Intercepts and resolves known WhatsApp popup dialogs without disturbing user security boundaries.
 */
object WhatsAppDialogHandler {

    private const val TAG = "WADialogHandler"

    /**
     * Inspects active window for internal WhatsApp modal confirmations (e.g. "Continue", "OK", "Got it") and taps them.
     *
     * @param root Accessibility node root.
     * @param service Active accessibility service instance.
     */
    fun checkAndHandlePermissionOrPopup(root: AccessibilityNodeInfo?, service: AccessibilityService) {
        if (root == null) return
        try {
            val rootPkg = root.packageName?.toString().orEmpty().lowercase()
            if (!rootPkg.contains("com.whatsapp")) {
                // Safeguard: Never dismiss dialogs or click buttons on external apps or Android OS permission dialogs
                return
            }
            val popupKeywords = listOf("allow", "continue", "accept", "ok", "got it")
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var scanned = 0
            while (queue.isNotEmpty() && scanned < 40) {
                val curr = queue.removeFirst()
                scanned++
                val currPkg = curr.packageName?.toString().orEmpty().lowercase()
                if (currPkg.isNotBlank() && !currPkg.contains("com.whatsapp")) {
                    continue
                }
                val txt = curr.text?.toString()?.lowercase() ?: ""
                val desc = curr.contentDescription?.toString()?.lowercase() ?: ""

                if (popupKeywords.any { txt == it || desc == it } || (txt == "never" && curr.isClickable)) {
                    if (AccessibilityNodeUtils.clickElement(curr, service)) {
                        Log.d(TAG, "Dismissed WhatsApp internal dialog button: $txt")
                        return
                    }
                }
                for (i in 0 until curr.childCount) {
                    curr.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Dismiss popup scan encountered exception: ${e.message}")
        }
    }

    /**
     * Detects "Phone number isn't on WhatsApp" or "Invalid phone number" error popups, marks task as invalid, and dismisses prompt.
     *
     * @param root Accessibility node root.
     * @param pkg Target WhatsApp package identifier.
     * @param pendingTaskId Unique task ID.
     * @param pendingRecipient Recipient phone number.
     * @param pendingMessage Message text attempted.
     * @param service Active accessibility service instance.
     * @param onDismissed Callback triggered after error dialog is acknowledged.
     * @return True if an invalid number/recipient error dialog was detected and handled.
     */
    fun checkAndHandleErrorDialog(
        root: AccessibilityNodeInfo?,
        pkg: String,
        pendingTaskId: Long,
        pendingRecipient: String,
        pendingMessage: String,
        service: AccessibilityService,
        onDismissed: () -> Unit
    ): Boolean {
        if (root == null) return false
        try {
            val errorPhrases = listOf(
                "isn't on whatsapp",
                "not on whatsapp",
                "not registered on whatsapp",
                "phone number is invalid",
                "invalid phone number",
                "can't find this phone number",
                "couldn't find this phone number",
                "invite to whatsapp",
                "invite friend"
            )

            var isDialogError = false

            val alertTitleNodes = root.findAccessibilityNodeInfosByViewId("android:id/alertTitle")
                ?: root.findAccessibilityNodeInfosByViewId("$pkg:id/alertTitle")
                ?: emptyList()
            for (titleNode in alertTitleNodes) {
                val title = titleNode.text?.toString()?.lowercase() ?: ""
                if (errorPhrases.any { title.contains(it) }) {
                    isDialogError = true
                    break
                }
            }

            if (!isDialogError) {
                val msgNodes = root.findAccessibilityNodeInfosByViewId("android:id/message")
                    ?: emptyList()
                for (mNode in msgNodes) {
                    val msg = mNode.text?.toString()?.lowercase() ?: ""
                    if (errorPhrases.any { msg.contains(it) }) {
                        isDialogError = true
                        break
                    }
                }
            }

            if (!isDialogError) {
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                queue.add(root)
                var scanned = 0
                while (queue.isNotEmpty() && scanned < 60) {
                    val curr = queue.removeFirst()
                    scanned++
                    val isEditable = curr.isEditable || curr.className?.toString()?.contains("EditText", ignoreCase = true) == true
                    if (!isEditable) {
                        val txt = curr.text?.toString()?.lowercase() ?: ""
                        val desc = curr.contentDescription?.toString()?.lowercase() ?: ""
                        if (errorPhrases.any { txt.contains(it) || desc.contains(it) }) {
                            isDialogError = true
                            break
                        }
                    }
                    for (i in 0 until curr.childCount) {
                        curr.getChild(i)?.let { queue.add(it) }
                    }
                }
            }

            if (isDialogError) {
                Log.w(TAG, "WhatsApp recipient error dialog verified for $pendingRecipient")
                AppRepository(service.applicationContext).insertLog(
                    ExecutionLog(
                        taskId = pendingTaskId,
                        channel = if (pkg.contains("w4b")) "WhatsApp Business" else "WhatsApp",
                        recipient = pendingRecipient,
                        messageSnippet = pendingMessage.take(160),
                        status = "FAILED",
                        details = "Recipient phone number is invalid or not registered on WhatsApp"
                    )
                )
                AppFileLogger.w(service.applicationContext, TAG, "WhatsApp recipient error dialog: $pendingRecipient is not on WhatsApp")

                val buttonIds = listOf("android:id/button1", "android:id/button2", "$pkg:id/ok_btn", "$pkg:id/cancel_btn", "$pkg:id/close", "$pkg:id/btn_close")
                var dismissed = false
                for (bId in buttonIds) {
                    val nodes = root.findAccessibilityNodeInfosByViewId(bId)
                    if (!nodes.isNullOrEmpty()) {
                        if (AccessibilityNodeUtils.clickElement(nodes[0], service)) {
                            dismissed = true
                            break
                        }
                    }
                }
                if (!dismissed) {
                    val fallbackButtons = root.findAccessibilityNodeInfosByText("OK")
                        ?: root.findAccessibilityNodeInfosByText("Cancel")
                        ?: root.findAccessibilityNodeInfosByText("Dismiss")
                        ?: root.findAccessibilityNodeInfosByText("Close")
                    val clickTarget = fallbackButtons?.firstOrNull()
                    if (clickTarget != null) {
                        AccessibilityNodeUtils.clickElement(clickTarget, service)
                    }
                }

                onDismissed()
                return true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Safe catch checkAndHandleErrorDialog: ${t.message}")
        }
        return false
    }
}
