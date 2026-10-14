package com.jmd.autotext.util

import android.content.Context
import android.content.pm.PackageManager

object WhatsAppPackageHelper {

    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    /**
     * Returns the optimal installed WhatsApp package.
     * Prefers regular WhatsApp if installed, else WhatsApp Business.
     * Returns null if neither is installed.
     */
    fun getInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        if (isPackageInstalled(pm, PACKAGE_WHATSAPP)) {
            return PACKAGE_WHATSAPP
        }
        if (isPackageInstalled(pm, PACKAGE_WHATSAPP_BUSINESS)) {
            return PACKAGE_WHATSAPP_BUSINESS
        }
        return null
    }

    /**
     * Checks if WhatsApp (regular or Business) is installed.
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        return getInstalledWhatsAppPackage(context) != null
    }

    /**
     * Gets all installed WhatsApp packages on the device.
     */
    fun getInstalledWhatsAppPackages(context: Context): List<String> {
        val pm = context.packageManager
        val list = mutableListOf<String>()
        if (isPackageInstalled(pm, PACKAGE_WHATSAPP)) list.add(PACKAGE_WHATSAPP)
        if (isPackageInstalled(pm, PACKAGE_WHATSAPP_BUSINESS)) list.add(PACKAGE_WHATSAPP_BUSINESS)
        return list
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
