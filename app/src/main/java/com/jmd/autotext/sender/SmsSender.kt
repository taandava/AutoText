package com.jmd.autotext.sender

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.jmd.autotext.util.SmsQuotaManager

class SmsSender(private val context: Context) {

    companion object {
        private const val TAG = "SmsSender"
    }

    data class SendResult(
        val success: Boolean,
        val segments: Int,
        val reason: String = ""
    )

    fun sendSmsWithResult(
        recipientPhone: String,
        messageText: String,
        simSlot: Int = 0,
        enforceQuota: Boolean = true
    ): SendResult {
        val cleanPhone = recipientPhone.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.isEmpty() || messageText.isEmpty()) {
            Log.e(TAG, "Phone number or message is empty")
            return SendResult(false, 0, "Empty phone number or message")
        }

        val partsCount = SmsQuotaManager.calculateSmsSegments(messageText)

        if (enforceQuota) {
            val (canSend, quotaReason) = SmsQuotaManager.canDispatchSms(context, messageText)
            if (!canSend) {
                Log.w(TAG, "Blocked SMS send to $cleanPhone: $quotaReason")
                return SendResult(false, partsCount, quotaReason)
            }
        }

        return try {
            val smsManager = getSmsManagerForSlot(simSlot)
            val parts = smsManager.divideMessage(messageText)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanPhone, null, messageText, null, null)
            }
            Log.d(TAG, "SMS successfully dispatched to $cleanPhone ($partsCount segments)")
            SendResult(true, partsCount, "Dispatched $partsCount segments")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $recipientPhone: ${e.message}", e)
            SendResult(false, partsCount, e.message ?: "Unknown error")
        }
    }

    fun sendSms(
        recipientPhone: String,
        messageText: String,
        simSlot: Int = 0,
        enforceQuota: Boolean = true
    ): Boolean {
        return sendSmsWithResult(recipientPhone, messageText, simSlot, enforceQuota).success
    }

    private fun getSmsManagerForSlot(simSlot: Int): SmsManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val baseManager = context.getSystemService(SmsManager::class.java)
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            try {
                val activeSubs = subManager?.activeSubscriptionInfoList
                if (!activeSubs.isNullOrEmpty()) {
                    if (simSlot in activeSubs.indices) {
                        val subId = activeSubs[simSlot].subscriptionId
                        return baseManager.createForSubscriptionId(subId)
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission missing for subscription query, using default SIM", e)
            }
            return baseManager
        } else {
            @Suppress("DEPRECATION")
            return SmsManager.getDefault()
        }
    }
}
