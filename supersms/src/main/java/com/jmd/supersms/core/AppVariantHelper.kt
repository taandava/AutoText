package com.jmd.supersms.core

import com.jmd.supersms.data.model.ChannelType

object AppVariantHelper {

    val isSuperSms: Boolean = true

    val isSuperWa: Boolean = false

    val currentVariant: String = "SUPERSMS"

    val appTitle: String = "SuperSMS"

    val isAccessibilitySupported: Boolean = false

    fun isChannelSupported(channel: ChannelType): Boolean {
        return when (channel) {
            ChannelType.SMS,
            ChannelType.CALL,
            ChannelType.CALL_SIMULATION -> true
            else -> false
        }
    }
}
