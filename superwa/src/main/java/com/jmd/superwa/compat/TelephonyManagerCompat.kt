/**
 * Package: com.jmd.superwa.compat
 * Module: Platform Compatibility Layer
 *
 * What it is:
 * Architecture definition and implementation for [TelephonyManagerCompat] in SuperWA.
 */

package com.jmd.superwa.compat

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

object TelephonyManagerCompat {

    private const val TAG = "TelephonyCompat"

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

    @JvmStatic
    fun getCallState(context: Context?, tm: TelephonyManager?): Int {
        if (context == null || tm == null) return TelephonyManager.CALL_STATE_IDLE
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return TelephonyManager.CALL_STATE_IDLE

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

    @JvmStatic
    fun isCallConnected(context: Context?, tm: TelephonyManager?): Boolean {
        return context != null && tm != null && getCallState(context, tm) == TelephonyManager.CALL_STATE_OFFHOOK
    }

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
