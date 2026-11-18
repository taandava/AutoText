package com.jmd.superapp.core

import com.jmd.superapp.data.model.ChannelType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test suite verifying WhatsAppPackageHelper constants and contracts.
 */
class WhatsAppPackageHelperTest {

    @Test
    fun testPackageConstants() {
        assertEquals("com.whatsapp", WhatsAppPackageHelper.PACKAGE_WHATSAPP)
        assertEquals("com.whatsapp.w4b", WhatsAppPackageHelper.PACKAGE_WHATSAPP_BUSINESS)
    }

    @Test
    fun testWhatsAppChannelDisplayNames() {
        assertEquals("WhatsApp", ChannelType.WHATSAPP.displayName)
        assertEquals("WA Business", ChannelType.WHATSAPP_BUSINESS.displayName)
        assertEquals("WhatsApp Group", ChannelType.WHATSAPP_GROUP.displayName)
        assertEquals("Business Group", ChannelType.WHATSAPP_BUSINESS_GROUP.displayName)
        assertEquals("WA Status", ChannelType.WHATSAPP_STATUS.displayName)
        assertEquals("W4B Status", ChannelType.WHATSAPP_BUSINESS_STATUS.displayName)
        assertEquals("WhatsApp Call", ChannelType.WHATSAPP_CALL.displayName)
        assertEquals("Business Call", ChannelType.WHATSAPP_BUSINESS_CALL.displayName)
    }
}
