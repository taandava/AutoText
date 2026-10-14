package com.jmd.autotext

import com.jmd.autotext.service.accessibility.VoiceNoteProtectionGuard
import org.junit.Assert.*
import org.junit.Test

class WhatsAppAutomationTest {

    @Test
    fun testVoiceNoteProtectionTerms() {
        // English voice terms
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("Voice message, Button. Double tap and hold to record"))
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("Record audio"))
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("Hold to record, release to send"))
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("Microphone"))
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("Push to talk"))

        // Hindi voice terms
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("वॉइस रिकॉर्ड करें"))
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("माइक"))
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("ध्वनि संदेश"))

        // Spanish & Portuguese
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("Mensaje de voz"))
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("Nota de voz"))
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("Mensagem de voz"))

        // French & German
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("Message vocal"))
        assertTrue(VoiceNoteProtectionGuard.isVoiceButtonDesc("Sprachnachricht"))

        // Send buttons should NOT be flagged as voice
        assertFalse(VoiceNoteProtectionGuard.isVoiceButtonDesc("Send"))
        assertFalse(VoiceNoteProtectionGuard.isVoiceButtonDesc("Send message"))
        assertFalse(VoiceNoteProtectionGuard.isVoiceButtonDesc("Next"))
        assertFalse(VoiceNoteProtectionGuard.isVoiceButtonDesc("Done"))
    }

    @Test
    fun testSendKeywordsIdentification() {
        assertTrue(VoiceNoteProtectionGuard.isSendKeyword("Send", "Send"))
        assertTrue(VoiceNoteProtectionGuard.isSendKeyword("", "Send"))
        assertTrue(VoiceNoteProtectionGuard.isSendKeyword("भेजें", "भेजें"))
        assertTrue(VoiceNoteProtectionGuard.isSendKeyword("Enviar", "Enviar"))
        assertTrue(VoiceNoteProtectionGuard.isSendKeyword("Post", "Post"))

        // Header titles starting with "Send to" should be rejected
        assertFalse(VoiceNoteProtectionGuard.isSendKeyword("Send to...", "Send to contacts"))
        assertFalse(VoiceNoteProtectionGuard.isSendKeyword("Send to", ""))
    }

    @Test
    fun testStatusKeywordsRecognition() {
        val keywords = listOf(
            "my status", "status", "mi estado", "mon statut", "mein status",
            "mio stato", "meu status", "मेरा स्टेटस", "माझा स्टेटस"
        )

        fun matchesStatus(text: String): Boolean {
            val lower = text.lowercase().trim()
            return keywords.any { lower.contains(it) }
        }

        assertTrue(matchesStatus("My status"))
        assertTrue(matchesStatus("My status, My contacts"))
        assertTrue(matchesStatus("मेरा स्टेटस"))
        assertTrue(matchesStatus("Mi estado"))
        assertTrue(matchesStatus("Mon statut"))
        assertFalse(matchesStatus("Rahul Sharma"))
        assertFalse(matchesStatus("General Group"))
    }
}
