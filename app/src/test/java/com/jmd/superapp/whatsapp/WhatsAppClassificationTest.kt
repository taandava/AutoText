package com.jmd.superapp.whatsapp

import com.jmd.superapp.data.model.ChannelType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppClassificationTest {

    private val validGroupEvents = listOf(
        "created group", "created the group", "added you", "you were added",
        "joined using", "left group", "left the group",
        "changed the group", "changed the subject"
    )

    private fun isGroupEvent(text: String): Boolean {
        val lower = text.lowercase()
        return validGroupEvents.any { lower.contains(it) }
    }

    private fun isGroupAvatar(desc: String): Boolean {
        val lower = desc.lowercase()
        return lower == "group icon" || lower == "group photo" || lower == "group picture" ||
            lower == "group profile picture" || lower == "ग्रुप आइकन" || lower == "ग्रुप फ़ोटो" ||
            lower.startsWith("group icon") || lower.startsWith("group photo") || lower.startsWith("group picture")
    }

    private fun validateRecipientForChannel(phone: String, channel: ChannelType): Pair<Boolean, String?> {
        val isBusiness = channel == ChannelType.WHATSAPP_BUSINESS ||
            channel == ChannelType.WHATSAPP_BUSINESS_GROUP ||
            channel == ChannelType.WHATSAPP_BUSINESS_CALL ||
            channel == ChannelType.WHATSAPP_BUSINESS_STATUS

        if (phone.startsWith("group:", ignoreCase = true)) {
            if (channel == ChannelType.SMS || channel == ChannelType.CALL || channel == ChannelType.CALL_SIMULATION) {
                return false to "Groups cannot receive calls or SMS"
            }
            val isBizGroup = phone.startsWith("group:w4b:", ignoreCase = true)
            if (isBusiness && !isBizGroup) {
                return false to "Cannot add WhatsApp group to Business task"
            }
            if (!isBusiness && isBizGroup) {
                return false to "Cannot add Business group to WhatsApp task"
            }
        }
        return true to null
    }

    @Test
    fun testSecurityCodeChangedIsNotGroupEvent() {
        // WhatsApp posts this in 1-on-1 direct chats when a contact reinstalls or changes device
        val notice = "Your security code with Rahul Sharma changed. Tap to learn more."
        assertFalse(isGroupEvent(notice))
        assertFalse(isGroupEvent("security code changed"))
    }

    @Test
    fun testLegitimateGroupEvents() {
        assertTrue(isGroupEvent("Amit created group 'Family'"))
        assertTrue(isGroupEvent("You were added to 'Project Alpha'"))
        assertTrue(isGroupEvent("Priya added you"))
        assertTrue(isGroupEvent("Rajesh left group"))
        assertTrue(isGroupEvent("Joined using this group's invite link"))
    }

    @Test
    fun testContactNameContainingGroupIsNotGroupAvatar() {
        // Individual contact or company named "JMD Group" or "Adani Group"
        assertFalse(isGroupAvatar("Profile photo of JMD Group"))
        assertFalse(isGroupAvatar("Profile photo of Adani Group"))
        assertFalse(isGroupAvatar("Profile picture of Tech Support Group"))
        assertFalse(isGroupAvatar("Leaving group meeting at 5"))
    }

    @Test
    fun testTrueGroupAvatarDescriptions() {
        assertTrue(isGroupAvatar("Group icon"))
        assertTrue(isGroupAvatar("Group photo"))
        assertTrue(isGroupAvatar("Group picture"))
        assertTrue(isGroupAvatar("Group profile picture"))
        assertTrue(isGroupAvatar("ग्रुप आइकन"))
    }

    @Test
    fun testRecipientChannelBoundaryGuards() {
        // WhatsApp Personal Channel
        val (ok1, err1) = validateRecipientForChannel("group:wa:family", ChannelType.WHATSAPP)
        assertTrue(ok1)

        val (ok2, err2) = validateRecipientForChannel("group:w4b:clients", ChannelType.WHATSAPP)
        assertFalse(ok2)
        assertTrue(err2!!.contains("Business group to WhatsApp"))

        // WhatsApp Business Channel
        val (ok3, err3) = validateRecipientForChannel("group:w4b:clients", ChannelType.WHATSAPP_BUSINESS)
        assertTrue(ok3)

        val (ok4, err4) = validateRecipientForChannel("group:wa:family", ChannelType.WHATSAPP_BUSINESS)
        assertFalse(ok4)
        assertTrue(err4!!.contains("WhatsApp group to Business"))

        // SMS / Calls cannot have groups
        val (ok5, err5) = validateRecipientForChannel("group:wa:family", ChannelType.SMS)
        assertFalse(ok5)
        assertTrue(err5!!.contains("Groups cannot receive"))

        val (ok6, err6) = validateRecipientForChannel("group:w4b:clients", ChannelType.CALL)
        assertFalse(ok6)
        assertTrue(err6!!.contains("Groups cannot receive"))

        // Regular phone numbers pass on all channels
        val (ok7, _) = validateRecipientForChannel("+919769602020", ChannelType.CALL)
        assertTrue(ok7)
        val (ok8, _) = validateRecipientForChannel("+919500910092", ChannelType.WHATSAPP)
        assertTrue(ok8)
        val (ok9, _) = validateRecipientForChannel("+919500910092", ChannelType.WHATSAPP_BUSINESS)
        assertTrue(ok9)
    }
}
