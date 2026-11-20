package com.jmd.superwa.core

import com.jmd.superwa.data.model.ChannelType

object AppVariantHelper {

    val isSuperSms: Boolean = false

    val isSuperWa: Boolean = true

    val currentVariant: String = "SUPERWA"

    val appTitle: String = "SuperWA"

    val isAccessibilitySupported: Boolean = true

    fun isChannelSupported(channel: ChannelType): Boolean = true
}
