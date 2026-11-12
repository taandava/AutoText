/**
 * Package: identification
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [WhatsappPackageUtils].
 *
 * What it does:
 * Provides package identification, installation resolution, and intent targeting for consumer WhatsApp and WhatsApp Business editions.
 *
 * Linked With:
 * - Models & Entities: [com.jmd.helper.data.model.ChannelType]
 * - Core Security & Utilities: [android.content.pm.PackageManager]
 * - Android Framework & Jetpack: [android.os.Build]
 */

package com.jmd.helper.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.jmd.helper.data.model.ChannelType

/**
 * What it is:
 * Package resolution and installation verification helper for WhatsApp products.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [WhatsAppPackageHelper].
 *
 * Linked with:
 * - Architecture: [WhatsappPackageUtils], [WhatsAppPackageHelper]
 */
object WhatsAppPackageHelper {

    /** Standard package name for consumer WhatsApp Messenger. */
    const val PACKAGE_WHATSAPP = "com.whatsapp"

    /** Standard package name for WhatsApp Business (W4B). */
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    /**
     * What it is:
     * Resolves the required installed package identifier for a given [ChannelType].
     *
     * What it does:
     * Executes [getPackageForChannel] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsappPackageUtils], [getPackageForChannel]
     * @param context Active Android [Context].
     * @param channel Target communication [ChannelType].
     * @return Package name string if installed, or null if required package is missing.
     */
    fun getPackageForChannel(context: Context, channel: ChannelType): String? {
        val pm = context.packageManager
        return when (channel) {
            ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_BUSINESS_GROUP, ChannelType.WHATSAPP_BUSINESS_STATUS, ChannelType.WHATSAPP_BUSINESS_CALL -> {
                if (isPackageInstalled(pm, PACKAGE_WHATSAPP_BUSINESS)) PACKAGE_WHATSAPP_BUSINESS else null
            }
            ChannelType.WHATSAPP, ChannelType.WHATSAPP_GROUP, ChannelType.WHATSAPP_STATUS, ChannelType.WHATSAPP_CALL -> {
                if (isPackageInstalled(pm, PACKAGE_WHATSAPP)) PACKAGE_WHATSAPP else null
            }
            else -> getInstalledWhatsAppPackage(context)
        }
    }

    /**
     * What it is:
     * Verifies if consumer WhatsApp Messenger is installed on device.
     *
     * What it does:
     * Executes [isWhatsAppInstalled] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsappPackageUtils], [isWhatsAppInstalled]
     * @param context Active Android [Context].
     * @return True if installed, false otherwise.
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        return isPackageInstalled(context.packageManager, PACKAGE_WHATSAPP)
    }

    /**
     * What it is:
     * Verifies if WhatsApp Business is installed on device.
     *
     * What it does:
     * Executes [isWhatsAppBusinessInstalled] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsappPackageUtils], [isWhatsAppBusinessInstalled]
     * @param context Active Android [Context].
     * @return True if installed, false otherwise.
     */
    fun isWhatsAppBusinessInstalled(context: Context): Boolean {
        return isPackageInstalled(context.packageManager, PACKAGE_WHATSAPP_BUSINESS)
    }

    /**
     * What it is:
     * Returns the first available WhatsApp package installed on device (preferring standard WhatsApp).
     *
     * What it does:
     * Executes [getInstalledWhatsAppPackage] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsappPackageUtils], [getInstalledWhatsAppPackage]
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
     * What it is:
     * Inspects device package manager for presence of a specific package name.
     *
     * What it does:
     * Executes [isPackageInstalled] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsappPackageUtils], [isPackageInstalled]
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
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
