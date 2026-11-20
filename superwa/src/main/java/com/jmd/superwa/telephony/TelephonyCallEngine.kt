/**
 * Package: com.jmd.superwa.telephony
 * Module: Telephony State Monitor
 *
 * What it is:
 * Read-only telephony state inspector used to avoid interrupting active phone calls during background WhatsApp tasks.
 */

package com.jmd.superwa.telephony

import android.content.Context
import android.telephony.TelephonyManager
import com.jmd.superwa.compat.TelephonyManagerCompat

object TelephonyCallEngine {

    /**
     * Determines whether any cellular call is currently active, dialing, ringing, or in conversation.
     * Evaluates TelephonyManager state to avoid interrupting voice calls during WhatsApp tasks.
     *
     * @param context Active Android [Context].
     * @return True if a cellular call is in progress.
     */
    fun isCallActive(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return TelephonyManagerCompat.isCallActiveOrRinging(context, tm)
    }

    /**
     * Determines whether an active cellular call has connected and is currently in conversation.
     *
     * @param context Active Android [Context].
     * @return True only when an active call is answered and offhook.
     */
    fun isCallConnected(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return TelephonyManagerCompat.isCallConnected(context, tm)
    }
}
