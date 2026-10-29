/**
 * Package: com.jmd.autotext.compat
 * Module: Compat Layer
 *
 * Provides safe SMS framework abstractions across legacy Android versions and modern
 * subscription-based system services (API 31+).
 */
package com.jmd.autotext.compat

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/**
 * Backward-compatible helper for [SmsManager] resolution and SIM subscription binding.
 *
 * Automatically branches between [Context.getSystemService] on Android 12+ (API 31+)
 * and [SmsManager.getDefault] on legacy platforms.
 */
object SmsManagerCompat {

    /**
     * Resolves default [SmsManager] instance for the device.
     *
     * @param context Active Android [Context] (nullable for legacy fallbacks).
     * @return Resolved [SmsManager] instance.
     */
    @JvmStatic
    fun getDefault(context: Context?): SmsManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null) {
            val manager = context.getSystemService(SmsManager::class.java)
            if (manager != null) return manager
        }
        return resolveLegacyDefault()
    }

    /**
     * Resolves [SmsManager] configured for a specific SIM subscription ID.
     *
     * @param context Active Android [Context] for system service resolution.
     * @param subscriptionId Target telephony subscription identifier.
     * @return Bound [SmsManager] instance or null if unavailable.
     */
    @JvmStatic
    fun getForSubscriptionId(context: Context?, subscriptionId: Int): SmsManager? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null) {
            val baseManager = context.getSystemService(SmsManager::class.java)
            if (baseManager != null) {
                return baseManager.createForSubscriptionId(subscriptionId)
            }
        }
        return resolveLegacyForSubscriptionId(subscriptionId)
    }

    /**
     * Reflectively invokes legacy static `SmsManager.getDefault()` on devices prior to Android 12 (API 31).
     *
     * @return Resolved system [SmsManager] instance.
     * @throws IllegalStateException If reflection fails and no default SMS provider exists.
     */
    private fun resolveLegacyDefault(): SmsManager {
        return try {
            val method = SmsManager::class.java.getMethod("getDefault")
            method.invoke(null) as SmsManager
        } catch (_: Throwable) {
            throw IllegalStateException("Unable to resolve default SmsManager on device")
        }
    }

    /**
     * Reflectively invokes legacy `SmsManager.getSmsManagerForSubscriptionId(int)` on multi-SIM
     * devices prior to Android 12 (API 31).
     *
     * @param subscriptionId Cellular telephony subscription identifier.
     * @return Bound [SmsManager] instance or null if invocation fails.
     */
    private fun resolveLegacyForSubscriptionId(subscriptionId: Int): SmsManager? {
        return try {
            val method = SmsManager::class.java.getMethod(
                "getSmsManagerForSubscriptionId",
                Int::class.javaPrimitiveType,
            )
            method.invoke(null, subscriptionId) as? SmsManager
        } catch (_: Throwable) {
            null
        }
    }
}
