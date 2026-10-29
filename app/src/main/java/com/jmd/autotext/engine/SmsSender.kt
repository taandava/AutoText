/**
 * Package: com.jmd.autotext.engine
 * Module: Automation Engine
 *
 * Coordinates cellular SMS transmission, dual-SIM slot binding, multipart PDU assembly,
 * radio modem acknowledgement latching, and quota reconciliation.
 *
 * Linked With:
 * - Compat: [com.jmd.autotext.compat.SmsManagerCompat]
 * - Quota: [com.jmd.autotext.core.SmsQuotaManager]
 * - Engine: [com.jmd.autotext.engine.SequentialTaskEngine]
 * - Receivers: [com.jmd.autotext.receiver.SmsReceiver]
 */
package com.jmd.autotext.engine

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.jmd.autotext.core.SmsQuotaManager

/**
 * Enterprise cellular SMS dispatch engine.
 *
 * Utilizes [com.jmd.autotext.compat.SmsManagerCompat] and [SubscriptionManager] to route messages
 * through user-selected SIM slots (SIM 1 or SIM 2), listens for modem broadcast callbacks,
 * and tracks partial delivery outcomes.
 */
class SmsSender(private val context: Context) {

    companion object {
        private const val TAG = "SmsSender"
        private val requestCounter = java.util.concurrent.atomic.AtomicInteger(1000)
    }

    /**
     * Delivery status outcome for an SMS transmission attempt.
     */
    enum class DeliveryStatus {
        /** All PDU parts confirmed by the cellular modem. */
        CONFIRMED,
        /** Dispatched to modem buffer, but acknowledgement timed out within 5 seconds. */
        PENDING_TIMEOUT,
        /** Some PDU parts were delivered while others failed. Whole-message retries are blocked to prevent spamming. */
        PARTIAL_DISPATCH,
        /** Total carrier or radio rejection. */
        FAILED
    }

    /**
     * Detailed result model returned after attempting an SMS dispatch.
     *
     * @property success True if all parts were confirmed by carrier modem.
     * @property segments Number of PDU parts into which message was split.
     * @property reason Human-readable diagnostic description.
     * @property deliveryStatus Structured outcome state enum.
     * @property successfulParts Count of successfully acknowledged parts.
     * @property failedParts Count of rejected or errored parts.
     */
    data class SendResult(
        val success: Boolean,
        val segments: Int,
        val reason: String = "",
        val deliveryStatus: DeliveryStatus = if (success) DeliveryStatus.CONFIRMED else DeliveryStatus.FAILED,
        val successfulParts: Int = 0,
        val failedParts: Int = 0
    )

    /**
     * Dispatches an SMS message synchronously, waiting up to 5 seconds for radio modem acknowledgment.
     *
     * @param recipientPhone Destination phone number string.
     * @param messageText SMS payload string.
     * @param simSlot Target SIM slot (0 = default OS SIM, 1 = SIM 1, 2 = SIM 2).
     * @param enforceQuota If true, checks and reserves daily quota balance before transmission.
     * @return [SendResult] describing delivery status and segment counts.
     */
    fun sendSmsWithResult(
        recipientPhone: String,
        messageText: String,
        simSlot: Int = 0,
        enforceQuota: Boolean = true
    ): SendResult {
        val cleanPhone = recipientPhone.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.isEmpty() || messageText.isEmpty()) {
            Log.e(TAG, "Phone number or message is empty")
            return SendResult(false, 0, "Empty phone number or message", DeliveryStatus.FAILED)
        }

        val partsCount = SmsQuotaManager.calculateSmsSegments(messageText)

        if (enforceQuota) {
            val (reserved, quotaReason) = SmsQuotaManager.reserveSmsQuota(context, partsCount)
            if (!reserved) {
                Log.w(TAG, "Blocked SMS send to $cleanPhone: $quotaReason")
                return SendResult(false, partsCount, quotaReason, DeliveryStatus.FAILED)
            }
        }

        val actionSmsSent = "com.jmd.autotext.ACTION_SMS_SENT_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
        var handlerThread: android.os.HandlerThread? = null
        var sentReceiver: BroadcastReceiver? = null
        val partOutcomes = java.util.concurrent.ConcurrentHashMap<Int, Int>()

        return try {
            val smsManager = getSmsManagerForSlot(simSlot)
            val parts = smsManager.divideMessage(messageText)
            val partsSize = maxOf(1, parts.size)
            val sentCountLatch = java.util.concurrent.CountDownLatch(partsSize)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val partIdx = intent?.getIntExtra("part_index", 0) ?: 0
                    val code = resultCode
                    partOutcomes[partIdx] = code
                    sentCountLatch.countDown()
                }
            }
            sentReceiver = receiver

            // Dedicated background handler thread so receiver callbacks are never blocked by or dependent on the main UI thread
            val thread = android.os.HandlerThread("SmsCb_${requestCounter.incrementAndGet()}").apply { start() }
            handlerThread = thread
            val callbackHandler = Handler(thread.looper)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(actionSmsSent), null, callbackHandler, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(actionSmsSent), null, callbackHandler)
            }

            val sentIntents = ArrayList<PendingIntent>(partsSize)
            val baseCode = (requestCounter.incrementAndGet() and 0xFFFF)
            for (idx in 0 until partsSize) {
                val sentIntent = Intent(actionSmsSent).apply {
                    setPackage(context.packageName)
                    putExtra("part_index", idx)
                }
                sentIntents.add(
                    PendingIntent.getBroadcast(
                        context,
                        baseCode + idx,
                        sentIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanPhone, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(cleanPhone, null, messageText, sentIntents.firstOrNull(), null)
            }

            // Wait up to 5 seconds for radio modem confirmation
            val completedInTime = sentCountLatch.await(5000, java.util.concurrent.TimeUnit.MILLISECONDS)

            var successCount = 0
            var failureCount = 0
            val failureCodes = mutableListOf<Int>()
            for (idx in 0 until partsSize) {
                val outcome = partOutcomes[idx]
                if (outcome == Activity.RESULT_OK) {
                    successCount++
                } else if (outcome != null) {
                    failureCount++
                    failureCodes.add(outcome)
                }
            }

            if (failureCount > 0 && successCount == 0 && failureCount == partsSize) {
                val errorMsg = when (val primaryCode = failureCodes.firstOrNull() ?: -1) {
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic carrier failure (radio/signal rejected)"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "Device radio is turned off (Airplane mode)"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU received from modem"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service / outside network coverage"
                    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Carrier message limit exceeded on SIM"
                    else -> "Modem dispatch error code: $primaryCode"
                }
                Log.e(TAG, "SMS total delivery failure for $cleanPhone: $errorMsg ($failureCount/$partsSize parts failed)")
                if (enforceQuota) SmsQuotaManager.releaseSmsQuotaReservation(partsCount)
                SendResult(false, partsCount, errorMsg, DeliveryStatus.FAILED, successCount, failureCount)
            } else if (failureCount > 0 && successCount > 0) {
                val unknownCount = partsSize - (successCount + failureCount)
                if (enforceQuota) {
                    SmsQuotaManager.commitSmsQuotaReservation(successCount + maxOf(0, unknownCount))
                    SmsQuotaManager.releaseSmsQuotaReservation(failureCount)
                }
                val partialMsg = "Partial dispatch: $successCount/$partsSize parts confirmed by carrier, $failureCount failed, ${maxOf(0, unknownCount)} unacknowledged. Whole-message retry blocked to prevent duplication."
                Log.w(TAG, "SMS to $cleanPhone: $partialMsg")
                SendResult(false, partsCount, partialMsg, DeliveryStatus.PARTIAL_DISPATCH, successCount, failureCount)
            } else if (!completedInTime || (successCount + failureCount < partsSize)) {
                if (enforceQuota) SmsQuotaManager.commitSmsQuotaReservation(partsCount)
                val pendingMsg = "Dispatched to cellular modem ($partsCount segments, $successCount confirmed); awaiting carrier acknowledgement"
                Log.w(TAG, "SMS to $cleanPhone: $pendingMsg")
                SendResult(false, partsCount, pendingMsg, DeliveryStatus.PENDING_TIMEOUT, successCount, failureCount)
            } else {
                val statusDetail = "Confirmed by radio modem ($partsCount segments)"
                Log.d(TAG, "SMS to $cleanPhone: $statusDetail")
                SendResult(true, partsCount, statusDetail, DeliveryStatus.CONFIRMED, successCount, failureCount)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $recipientPhone: ${e.message}", e)
            if (enforceQuota) SmsQuotaManager.releaseSmsQuotaReservation(partsCount)
            SendResult(false, partsCount, e.message ?: "Unknown error", DeliveryStatus.FAILED)
        } finally {
            if (sentReceiver != null) {
                try { context.unregisterReceiver(sentReceiver) } catch (e: Exception) { Log.d(TAG, "unregisterReceiver: ${e.message}") }
            }
            if (handlerThread != null) {
                try { handlerThread.quitSafely() } catch (e: Exception) { Log.d(TAG, "handlerThread.quitSafely: ${e.message}") }
            }
        }
    }

    /**
     * Resolves the proper [SmsManager] instance for a specified SIM slot index.
     *
     * @param simSlot 0 for default SIM, 1 for SIM slot 1, 2 for SIM slot 2.
     * @return Resolved [SmsManager] bound to requested subscription.
     * @throws IllegalStateException If requested subscription is inactive or unavailable.
     */
    private fun getSmsManagerForSlot(simSlot: Int): SmsManager {
        if (simSlot <= 0) {
            return com.jmd.autotext.compat.SmsManagerCompat.getDefault(context)
        }

        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val activeSubs = try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                subManager?.activeSubscriptionInfoList
            } else null
        } catch (e: Throwable) {
            Log.w(TAG, "Exception during subscription query: ${e.message}")
            null
        }

        val targetPhysicalSlot = simSlot - 1
        val targetSub = activeSubs?.find { it.simSlotIndex == targetPhysicalSlot }

        if (targetSub == null) {
            throw IllegalStateException("Selected SIM $simSlot is not active or available on this device")
        }

        return com.jmd.autotext.compat.SmsManagerCompat.getForSubscriptionId(context, targetSub.subscriptionId)
            ?: throw IllegalStateException("Unable to obtain SmsManager for subscription ${targetSub.subscriptionId}")
    }
}
