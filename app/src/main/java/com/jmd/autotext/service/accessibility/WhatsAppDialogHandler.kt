package com.jmd.autotext.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.ExecutionLog
import com.jmd.autotext.util.AppFileLogger
import java.util.ArrayDeque

object WhatsAppDialogHandler {

    private const val TAG = "WADialogHandler"

    fun checkAndHandlePermissionOrPopup(root: AccessibilityNodeInfo?, service: AccessibilityService) {
        if (root == null) return
        try {
            val popupKeywords = listOf("allow", "continue", "accept", "ok", "got it")
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var scanned = 0
            while (queue.isNotEmpty() && scanned < 40) {
                val curr = queue.removeFirst()
                scanned++
                val txt = curr.text?.toString()?.lowercase() ?: ""
                val desc = curr.contentDescription?.toString()?.lowercase() ?: ""

                if (popupKeywords.any { txt == it || desc == it } || (txt == "never" && curr.isClickable)) {
                    if (AccessibilityNodeUtils.clickElement(curr, service)) {
                        Log.d(TAG, "Dismissed popup dialog button: $txt")
                        return
                    }
                }
                for (i in 0 until curr.childCount) {
                    curr.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (_: Exception) {}
    }

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

            // 1. Direct search by view IDs
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

            // 2. Comprehensive tree scan for modern bottom-sheets and custom modal dialogs
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
