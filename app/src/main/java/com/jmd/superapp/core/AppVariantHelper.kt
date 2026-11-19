/**
 * Package: com.jmd.superapp.core
 * Module: Application Variant Management
 *
 * What it is:
 * Helper for runtime flavor detection and channel capability routing.
 *
 * What it does:
 * - Distinguishes between SuperSMS and SuperWA variants based on BuildConfig.APP_VARIANT.
 * - Restricts available channels, background tasks, and UI options according to variant.
 * - Enforces zero-accessibility requirements for SuperSMS and banking shield for SuperWA.
 */

package com.jmd.superapp.core

import com.jmd.superapp.BuildConfig
import com.jmd.superapp.data.model.ChannelType

object AppVariantHelper {

    val isSuperSms: Boolean
        get() = BuildConfig.APP_VARIANT.equals("SUPERSMS", ignoreCase = true)

    val isSuperWa: Boolean
        get() = BuildConfig.APP_VARIANT.equals("SUPERWA", ignoreCase = true)

    val currentVariant: String
        get() = BuildConfig.APP_VARIANT

    val appTitle: String
        get() = if (isSuperSms) "SuperSMS" else "SuperWA"

    val isAccessibilitySupported: Boolean
        get() = isSuperWa

    fun isChannelSupported(channel: ChannelType): Boolean {
        return if (isSuperSms) {
            when (channel) {
                ChannelType.SMS,
                ChannelType.CALL,
                ChannelType.CALL_SIMULATION -> true
                else -> false
            }
        } else {
            when (channel) {
                ChannelType.WHATSAPP,
                ChannelType.WHATSAPP_BUSINESS,
                ChannelType.WHATSAPP_GROUP,
                ChannelType.WHATSAPP_BUSINESS_GROUP,
                ChannelType.WHATSAPP_STATUS,
                ChannelType.WHATSAPP_BUSINESS_STATUS,
                ChannelType.WHATSAPP_CALL,
                ChannelType.WHATSAPP_BUSINESS_CALL -> true
                else -> false
            }
        }
    }

    val supportedChannels: List<ChannelType>
        get() = ChannelType.values().filter { isChannelSupported(it) }
}
