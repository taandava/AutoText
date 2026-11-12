/**
 * Package: com.jmd.helper.compat
 * Module: Platform Compatibility Layer
 *
 * What it is:
 * Architecture definition and implementation for [SmsManagerCompat].
 *
 * What it does:
 * Provides safe SMS framework abstractions across legacy Android versions and modern subscription-based system services (API 31+).
 *
 * Linked With:
 * - Core Security & Utilities: [android.telephony.SmsManager], [android.util.Log]
 * - Android Framework & Jetpack: [android.os.Build]
 */

package com.jmd.helper.compat

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

/**
 * What it is:
 * Backward-compatible helper for [SmsManager] resolution and SIM subscription binding.
 *
 * What it does:
 * Branches between [Context.getSystemService] on Android 12+ (API 31+) and [SmsManager.getDefault] on legacy platforms.
 *
 * Linked with:
 * - Architecture: [SmsManagerCompat], [SmsManagerCompat]
 */
object SmsManagerCompat {

    private const val TAG = "SmsManagerCompat"

    /**
     * What it is:
     * Resolves default [SmsManager] instance for the device.
     *
     * What it does:
     * Executes getDefault operations conforming to application constraints.
     *
     * Linked with:
     * - Architecture: [SmsManagerCompat], [getDefault]
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
     * What it is:
     * Resolves [SmsManager] configured for a specific SIM subscription ID.
     *
     * What it does:
     * Executes getForSubscriptionId operations conforming to application constraints.
     *
     * Linked with:
     * - Architecture: [SmsManagerCompat], [getForSubscriptionId]
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
     * What it is:
     * Reflectively invokes legacy static `SmsManager.getDefault()` on devices prior to Android 12 (API 31).
     *
     * What it does:
     * Executes resolveLegacyDefault operations conforming to application constraints.
     *
     * Linked with:
     * - Architecture: [SmsManagerCompat], [resolveLegacyDefault]
     * @return Resolved system [SmsManager] instance.
     * @throws IllegalStateException If reflection fails and no default SMS provider exists.
     */
    private fun resolveLegacyDefault(): SmsManager {
        return try {
            val method = SmsManager::class.java.getMethod("getDefault")
            (method.invoke(null) as? SmsManager) ?: throw IllegalStateException("Unable to resolve default SmsManager on device")
        } catch (t: Throwable) {
            Log.e(TAG, "Legacy default SmsManager resolution error: ${t.message}", t)
            throw IllegalStateException("Unable to resolve default SmsManager on device: ${t.message}", t)
        }
    }

    /**
     * What it is:
     * Reflectively invokes legacy `SmsManager.getSmsManagerForSubscriptionId(int)` on multi-SIM
     *
     * What it does:
     * devices prior to Android 12 (API 31).
     *
     * Linked with:
     * - Architecture: [SmsManagerCompat], [resolveLegacyForSubscriptionId]
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
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy SmsManager resolution for subId $subscriptionId: ${t.message}")
            null
        }
    }
}
