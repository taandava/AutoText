

/**
 * Package: com.jmd.autotext.whatsapp
 * Module: WhatsApp Automation Subsystem
 *
 * Prevents accidental voice note recording triggers when attempting to click the WhatsApp Send button.
 * Detects whether the button at the send position has transitioned from a Voice Note mic icon to a Send paper airplane.
 *
 * Linked With:
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Engines: [com.jmd.autotext.whatsapp.WhatsAppDirectSendEngine]
 */
package com.jmd.autotext.whatsapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Safety guard verifying Send button state to avoid triggering voice note recording.
 */
object VoiceNoteProtectionGuard {

    private const val TAG = "VoiceProtectionGuard"

    private val VOICE_TERMS = listOf(
        "voice", "audio", "record", "mic", "ptt", "push to talk",
        "hold to record", "release to send", "slide to cancel", "swipe up to lock",
        "video message", "instant video", "switch to video", "video note",
        "voice message", "voice note", "audio message", "voice msg", "sound", "micro",
        "microphone", "rec", "वॉयस", "बोलें", "रेकॉर्ड",
        "आवाज़", "ऑडियो", "वॉइस", "ध्वनि", "रिकॉर्ड", "माइक", "व्हॉइस",
        "grabando", "mensaje de voz", "nota de voz", "mensagem de voz", "message vocal", "sprachnachricht",
        "pesan suara", "golosovoe"
    )

    val SEND_KEYWORDS = listOf(
        "send", "send message", "enviar", "envoyer", "envía", "kirim", "invia",
        "enviar mensagem", "wyślij", "mandar", "gửi", "отправить", "ارسال", "post",
        "senden", "gönder", "भेजें", "पाठवा", "पाठान", "पंपु", "அனுப்பு", "మోకలో",
        "stuur", "odeslat", "lähetä", "skicka", "poslat", "trimite", "αποστολή", "küldés"
    )

    /**
     * Determines whether the given accessibility description represents a valid "Send" message action.
     *
     * @param desc The content description or text string inspected from an accessibility node.
     * @return `true` if the string matches any localized Send keyword or begins with one followed by a space; `false` otherwise.
     */
    fun isSendDesc(desc: String): Boolean {
        if (desc.isBlank()) return false
        val d = desc.lowercase().trim()
        return SEND_KEYWORDS.any { d == it || d.startsWith("$it ") }
    }

    /**
     * Evaluates whether an accessibility node is explicitly identified as the "Send" button.
     *
     * Checks both the content description and textual content against the set of known multilingual
     * send keywords to avoid misidentifying adjacent buttons or voice note recorders.
     *
     * @param node The [AccessibilityNodeInfo] representing the candidate UI control.
     * @return `true` if the node's description or text matches a send command; `false` if null or unmatched.
     */
    fun isSendNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val desc = node.contentDescription?.toString().orEmpty()
        val text = node.text?.toString().orEmpty()
        return isSendDesc(desc) || isSendDesc(text)
    }

    /**
     * Traverses the descendant hierarchy of an accessibility node to verify if it represents a voice note
     * recording control or microphone input rather than a dispatch button.
     *
     * WhatsApp frequently reuses the same bottom-right container layout for both voice recording and message sending.
     * Clicking a voice button instead of send will trigger audio recording, locking the UI. This method protects
     * against that edge case by inspecting resource IDs and descriptions up to a breadth limit.
     *
     * @param node The candidate [AccessibilityNodeInfo] container or leaf element.
     * @return `true` if any child node or the node itself exhibits voice/audio/mic recording characteristics.
     */
    fun isVoiceNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var count = 0
        with(AccessibilityNodeUtils) {
            val match = node.findFirstDescendant { curr ->
                count++
                if (count > 35) return@findFirstDescendant false
                val desc = curr.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                val text = curr.text?.toString()?.lowercase()?.trim() ?: ""
                val id = curr.viewIdResourceName?.lowercase()?.trim() ?: ""

                (id.contains("voice") || id.contains("audio") || id.contains("record") || id.contains("mic") || id.contains("ptt")) ||
                    isVoiceButtonDesc(desc) || isVoiceButtonDesc(text)
            }
            return match != null
        }
    }

    /**
     * Checks whether an individual description string indicates a voice note or audio recording button.
     *
     * @param desc The description or text string extracted from the node.
     * @return `true` if the string contains any known multilingual voice/recording term.
     */
    fun isVoiceButtonDesc(desc: String): Boolean {
        if (desc.isBlank()) return false
        val d = desc.lowercase().trim()
        return VOICE_TERMS.any { d.contains(it) }
    }

    /**
     * Detects if WhatsApp has inadvertently entered active voice recording mode and cancels it safely.
     *
     * If an automation step erroneously held down or tapped the voice recorder button instead of send,
     * this recovery routine searches for the trash/cancel button IDs or "slide to cancel" keywords,
     * performing an accessibility click or synthesizing a leftward swipe gesture to discard the recording.
     *
     * @param root The root [AccessibilityNodeInfo] of the active WhatsApp window.
     * @param pkg The target package name (standard WhatsApp or WhatsApp Business).
     * @param service The active [AccessibilityService] used to dispatch recovery gestures.
     * @return `true` if an active recording was detected and successfully canceled; `false` otherwise.
     */
    fun handleAndCancelActiveVoiceRecording(
        root: AccessibilityNodeInfo?,
        pkg: String,
        service: AccessibilityService
    ): Boolean {
        if (root == null) return false
        try {
            val cancelIds = listOf(
                "$pkg:id/cancel",
                "com.whatsapp:id/cancel",
                "com.whatsapp.w4b:id/cancel",
                "$pkg:id/delete_recording_btn",
                "com.whatsapp:id/delete_recording_btn",
                "com.whatsapp.w4b:id/delete_recording_btn",
                "$pkg:id/voice_note_cancel",
                "com.whatsapp:id/voice_note_cancel",
                "$pkg:id/cancel_btn",
                "com.whatsapp:id/cancel_btn"
            )
            for (id in cancelIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        if (AccessibilityNodeUtils.clickElement(node, service)) {
                            Log.w(TAG, "Clicked cancel/delete recording button by ID: $id")
                            return true
                        }
                    }
                }
            }

            val cancelKeywords = listOf("slide to cancel", "cancel recording", "delete recording", "रद्द करने के लिए स्वाइप करें")
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var count = 0
            while (queue.isNotEmpty() && count < 50) {
                val curr = queue.removeFirst()
                count++
                val txt = curr.text?.toString()?.lowercase() ?: ""
                val desc = curr.contentDescription?.toString()?.lowercase() ?: ""
                if (cancelKeywords.any { txt.contains(it) || desc.contains(it) }) {
                    if (AccessibilityNodeUtils.clickElement(curr, service)) {
                        Log.w(TAG, "Clicked cancel recording via text/desc: $txt / $desc")
                        return true
                    }
                    val metrics = service.resources.displayMetrics
                    val swipeY = metrics.heightPixels * 0.90f
                    val startX = metrics.widthPixels * 0.85f
                    val endX = metrics.widthPixels * 0.20f
                    val path = Path().apply {
                        moveTo(startX, swipeY)
                        lineTo(endX, swipeY)
                    }
                    val stroke = GestureDescription.StrokeDescription(path, 0, 180L)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    service.dispatchGesture(gesture, null, null)
                    Log.w(TAG, "Dispatched swipe left to cancel active voice recording")
                    return true
                }
                for (i in 0 until curr.childCount) {
                    curr.getChild(i)?.let { queue.add(it) }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Safe catch handleAndCancelActiveVoiceRecording", t)
        }
        return false
    }
}
