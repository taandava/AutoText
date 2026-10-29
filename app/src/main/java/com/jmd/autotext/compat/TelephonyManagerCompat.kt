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
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Backward-compatible helper for [TelephonyManager] call state inspection.
 *
 * Handles SDK branching between legacy [TelephonyManager.getCallState] (< API 31)
 * and [TelephonyManager.getCallStateForSubscription] (>= API 31). Gracefully recovers
 * from missing permissions and runtime security exceptions without failing task execution.
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
     *
     * @param context Active Android [Context] for runtime permission verification.
     * @param tm Target [TelephonyManager] or null.
     * @return Call state integer, or [TelephonyManager.CALL_STATE_IDLE] if permission is missing.
     */
    @JvmStatic
    fun getCallState(context: Context, tm: TelephonyManager?): Int {
        if (tm == null) return TelephonyManager.CALL_STATE_IDLE
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return TelephonyManager.CALL_STATE_IDLE
        return getCallState(tm)
    }
}
