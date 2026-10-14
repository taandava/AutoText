package com.jmd.autotext.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

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

    private val SEND_KEYWORDS = listOf(
        "send", "send message", "enviar", "envoyer", "envía", "kirim", "invia",
        "enviar mensagem", "wyślij", "mandar", "gửi", "отправить", "ارسال", "post",
        "senden", "gönder", "भेजें", "पाठवा", "पाठान", "పంపు", "அனுப்பு", "మోకलो",
        "stuur", "odeslat", "lähetä", "skicka", "poslat", "trimite", "αποστολή", "küldés"
    )

    fun isVoiceNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        var count = 0
        while (queue.isNotEmpty() && count < 35) {
            val curr = queue.removeFirst()
            count++
            val desc = curr.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val text = curr.text?.toString()?.lowercase()?.trim() ?: ""
            val id = curr.viewIdResourceName?.lowercase()?.trim() ?: ""

            if (id.contains("voice") || id.contains("audio") || id.contains("record") || id.contains("mic") || id.contains("ptt")) {
                return true
            }
            if (isVoiceButtonDesc(desc) || isVoiceButtonDesc(text)) {
                return true
            }
            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    fun isVoiceButtonDesc(desc: String): Boolean {
        if (desc.isBlank()) return false
        val d = desc.lowercase().trim()
        return VOICE_TERMS.any { d.contains(it) }
    }

    fun isSendKeyword(text: String, desc: String): Boolean {
        if (text.startsWith("send to", ignoreCase = true) || desc.startsWith("send to", ignoreCase = true)) {
            return false
        }
        val t = text.lowercase().trim()
        val d = desc.lowercase().trim()
        return SEND_KEYWORDS.any {
            d == it || d.startsWith("$it ") || t == it || t.startsWith("$it ")
        }
    }

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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
