/**
 * Package: com.jmd.autotext.core
 * Module: Core Automation Utilities
 *
 * Provides package identification, installation resolution, and intent targeting
 * for consumer WhatsApp and WhatsApp Business editions.
 */
package com.jmd.autotext.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.jmd.autotext.data.model.ChannelType

/**
 * Package resolution and installation verification helper for WhatsApp products.
 */
object WhatsAppPackageHelper {

    /** Standard package name for consumer WhatsApp Messenger. */
    const val PACKAGE_WHATSAPP = "com.whatsapp"

    /** Standard package name for WhatsApp Business (W4B). */
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    /**
     * Resolves the required installed package identifier for a given [ChannelType].
     *
     * @param context Active Android [Context].
     * @param channel Target communication [ChannelType].
     * @return Package name string if installed, or null if required package is missing.
     */
    fun getPackageForChannel(context: Context, channel: ChannelType): String? {
        val pm = context.packageManager
        return when (channel) {
            ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_BUSINESS_STATUS, ChannelType.WHATSAPP_BUSINESS_CALL -> {
                if (isPackageInstalled(pm, PACKAGE_WHATSAPP_BUSINESS)) PACKAGE_WHATSAPP_BUSINESS else null
            }
            ChannelType.WHATSAPP, ChannelType.WHATSAPP_STATUS, ChannelType.WHATSAPP_CALL -> {
                if (isPackageInstalled(pm, PACKAGE_WHATSAPP)) PACKAGE_WHATSAPP else null
            }
            ChannelType.WHATSAPP_GROUP -> {
                getInstalledWhatsAppPackage(context)
            }
            else -> getInstalledWhatsAppPackage(context)
        }
    }

    /**
     * Verifies if consumer WhatsApp Messenger is installed on device.
     *
     * @param context Active Android [Context].
     * @return True if installed, false otherwise.
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        return isPackageInstalled(context.packageManager, PACKAGE_WHATSAPP)
    }

    /**
     * Verifies if WhatsApp Business is installed on device.
     *
     * @param context Active Android [Context].
     * @return True if installed, false otherwise.
     */
    fun isWhatsAppBusinessInstalled(context: Context): Boolean {
        return isPackageInstalled(context.packageManager, PACKAGE_WHATSAPP_BUSINESS)
    }

    /**
     * Returns the first available WhatsApp package installed on device (preferring standard WhatsApp).
     *
     * @param context Active Android [Context].
     * @return Installed package name string, or null if neither version is installed.
     */
    fun getInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        if (isPackageInstalled(pm, PACKAGE_WHATSAPP)) return PACKAGE_WHATSAPP
        if (isPackageInstalled(pm, PACKAGE_WHATSAPP_BUSINESS)) return PACKAGE_WHATSAPP_BUSINESS
        return null
    }

    /**
     * Inspects device package manager for presence of a specific package name.
     *
     * @param pm Active [PackageManager].
     * @param packageName Target package name string.
     * @return True if package is present, false otherwise.
     */
    fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
