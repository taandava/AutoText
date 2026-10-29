/**
 * Package: com.jmd.autotext.compat
 * Module: Compat Layer
 *
 * Provides backward-compatible framework adaptations across Android SDK versions (API 26 through API 36+),
 * strictly avoiding lint suppressions through explicit permission verification and runtime SDK branching.
 */
package com.jmd.autotext.compat

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Backward-compatible helper for [TelephonyManager] call state inspection.
 *
 * Handles SDK branching between legacy [TelephonyManager.getCallState] (< API 31)
 * and [TelephonyManager.getCallStateForSubscription] (>= API 31) across multi-SIM subscriptions.
 * Gracefully recovers from missing permissions and runtime security exceptions without failing task execution.
 */
object TelephonyManagerCompat {

    /**
     * Retrieves current call state for the given TelephonyManager instance.
     *
     * @param tm Target [TelephonyManager] or null.
     * @return One of [TelephonyManager.CALL_STATE_IDLE], [TelephonyManager.CALL_STATE_RINGING],
     *         or [TelephonyManager.CALL_STATE_OFFHOOK]. Returns IDLE on null or security exception.
     */
    @JvmStatic
    fun getCallState(tm: TelephonyManager?): Int {
        if (tm == null) return TelephonyManager.CALL_STATE_IDLE
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tm.callStateForSubscription
            } else {
                val method = TelephonyManager::class.java.getMethod("getCallState")
                (method.invoke(tm) as? Int) ?: TelephonyManager.CALL_STATE_IDLE
            }
        } catch (_: SecurityException) {
            TelephonyManager.CALL_STATE_IDLE
        } catch (_: Throwable) {
            TelephonyManager.CALL_STATE_IDLE
        }
    }

    /**
     * Safely checks [android.Manifest.permission.READ_PHONE_STATE] before inspecting call state.
     * Evaluates all active multi-SIM subscriptions to ensure call activity on secondary SIMs is not missed.
     *
     * @param context Active Android [Context] for runtime permission verification.
     * @param tm Target [TelephonyManager] or null.
     * @return Call state integer, or [TelephonyManager.CALL_STATE_IDLE] if permission is missing.
     */
    @JvmStatic
    fun getCallState(context: Context?, tm: TelephonyManager?): Int {
        if (context == null || tm == null) return TelephonyManager.CALL_STATE_IDLE
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return TelephonyManager.CALL_STATE_IDLE

        // Multi-SIM subscription resolution for API 31+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeSubs = subManager?.activeSubscriptionInfoList
                if (!activeSubs.isNullOrEmpty()) {
                    var anyRinging = false
                    for (subInfo in activeSubs) {
                        val subTm = tm.createForSubscriptionId(subInfo.subscriptionId)
                        val subState = subTm.callStateForSubscription
                        if (subState == TelephonyManager.CALL_STATE_OFFHOOK) {
                            return TelephonyManager.CALL_STATE_OFFHOOK
                        }
                        if (subState == TelephonyManager.CALL_STATE_RINGING) {
                            anyRinging = true
                        }
                    }
                    if (anyRinging) return TelephonyManager.CALL_STATE_RINGING
                }
            } catch (_: SecurityException) {
                // Fall back to default subscription state
            } catch (_: Throwable) {
                // Fall back to default subscription state
            }
        }

        return getCallState(tm)
    }

    /**
     * Determines whether an active cellular call has connected and is currently in conversation (OFFHOOK).
     *
     * @param context Active Android [Context] or null.
     * @param tm Target [TelephonyManager] or null.
     * @return True only when an active cellular call is in progress and answered.
     */
    @JvmStatic
    fun isCallConnected(context: Context?, tm: TelephonyManager?): Boolean {
        if (context == null || tm == null) return false
        return getCallState(context, tm) == TelephonyManager.CALL_STATE_OFFHOOK
    }

    /**
     * Determines whether any cellular call is active, dialing, ringing, or in conversation.
     * Uses TelecomManager and TelephonyManager multi-SIM states.
     *
     * @param context Active Android [Context] or null.
     * @param tm Target [TelephonyManager] or null.
     * @return True if a cellular call is currently ongoing in any state.
     */
    @JvmStatic
    fun isCallActiveOrRinging(context: Context?, tm: TelephonyManager?): Boolean {
        if (context == null) return false
        val state = getCallState(context, tm)
        if (state != TelephonyManager.CALL_STATE_IDLE) return true

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (telecomManager != null) {
            try {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm && telecomManager.isInCall) {
                    return true
                }
            } catch (_: SecurityException) {
                // Non-fatal permission limitation
            } catch (_: Throwable) {
                // Ignore unexpected reflection or vendor-specific telecom faults
            }
        }
        return false
    }
}
