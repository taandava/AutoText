/**
 * Package: com.jmd.helper.compat
 * Module: Platform Compatibility Layer
 *
 * What it is:
 * Architecture definition and implementation for [TelephonyManagerCompat].
 *
 * What it does:
 * Provides backward-compatible framework adaptations across Android SDK versions (API 26 through API 36+), strictly avoiding lint suppressions through explicit permission verification and runtime SDK branching.
 *
 * Linked With:
 * - Core Security & Utilities: [android.content.pm.PackageManager], [android.telecom.TelecomManager], [android.telephony.SubscriptionManager], [android.telephony.TelephonyManager]
 * - Android Framework & Jetpack: [android.os.Build]
 */

package com.jmd.helper.compat

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * What it is:
 * Backward-compatible helper for [TelephonyManager] call state inspection.
 *
 * What it does:
 * Handles SDK branching between legacy [TelephonyManager.getCallState] (< API 31) and [TelephonyManager.getCallStateForSubscription] (>= API 31) across multi-SIM subscriptions. Gracefully recovers from missing permissions and runtime security exceptions without failing task execution.
 *
 * Linked with:
 * - Architecture: [TelephonyManagerCompat], [TelephonyManagerCompat]
 */
object TelephonyManagerCompat {

    private const val TAG = "TelephonyCompat"

    /**
     * What it is:
     * Retrieves current call state for the given TelephonyManager instance.
     *
     * What it does:
     * or [TelephonyManager.CALL_STATE_OFFHOOK]. Returns IDLE on null or security exception.
     *
     * Linked with:
     * - Architecture: [TelephonyManagerCompat], [getCallState]
     * @param tm Target [TelephonyManager] or null.
     * @return One of [TelephonyManager.CALL_STATE_IDLE], [TelephonyManager.CALL_STATE_RINGING],
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
        } catch (e: SecurityException) {
            Log.d(TAG, "Call state security note: ${e.message}")
            TelephonyManager.CALL_STATE_IDLE
        } catch (t: Throwable) {
            Log.d(TAG, "Call state resolution note: ${t.message}")
            TelephonyManager.CALL_STATE_IDLE
        }
    }

    /**
     * What it is:
     * Safely checks [android.Manifest.permission.READ_PHONE_STATE] before inspecting call state.
     *
     * What it does:
     * Evaluates all active multi-SIM subscriptions to ensure call activity on secondary SIMs is not missed.
     *
     * Linked with:
     * - Architecture: [TelephonyManagerCompat], [getCallState]
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
            } catch (e: SecurityException) {
                Log.d(TAG, "Multi-SIM call state security note: ${e.message}")
            } catch (t: Throwable) {
                Log.d(TAG, "Multi-SIM call state resolution note: ${t.message}")
            }
        }

        return getCallState(tm)
    }

    /**
     * What it is:
     * Determines whether an active cellular call has connected and is currently in conversation (OFFHOOK).
     *
     * What it does:
     * Executes isCallConnected operations conforming to application constraints.
     *
     * Linked with:
     * - Architecture: [TelephonyManagerCompat], [isCallConnected]
     * @param context Active Android [Context] or null.
     * @param tm Target [TelephonyManager] or null.
     * @return True only when an active cellular call is in progress and answered.
     */
    @JvmStatic
    fun isCallConnected(context: Context?, tm: TelephonyManager?): Boolean {
        return context != null && tm != null && getCallState(context, tm) == TelephonyManager.CALL_STATE_OFFHOOK
    }

    /**
     * What it is:
     * Determines whether any cellular call is active, dialing, ringing, or in conversation.
     *
     * What it does:
     * Uses TelecomManager and TelephonyManager multi-SIM states.
     *
     * Linked with:
     * - Architecture: [TelephonyManagerCompat], [isCallActiveOrRinging]
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
            } catch (e: SecurityException) {
                Log.d(TAG, "TelecomManager isInCall permission note: ${e.message}")
            } catch (t: Throwable) {
                Log.d(TAG, "TelecomManager isInCall resolution note: ${t.message}")
            }
        }
        return false
    }
}
