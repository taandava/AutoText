/**
 * Package: com.jmd.superapp.receiver
 * Module: Broadcast Receivers Subsystem
 *
 * What it is:
 * Receiver for carrier delivery reports attached to outgoing SMS PDUs.
 *
 * What it does:
 * [com.jmd.superapp.engine.SmsSender] attaches one delivered PendingIntent per PDU part. When the
 * network reports the outcome, this receiver writes a truthful delivery entry to the execution
 * history, so the app no longer treats "accepted by the modem" as "delivered".
 *
 * Linked With:
 * - Engines: [com.jmd.superapp.engine.SmsSender]
 * - Repositories: [com.jmd.superapp.data.repo.AppRepository]
 * - Models: [com.jmd.superapp.data.model.ExecutionLog]
 */

package com.jmd.superapp.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmd.superapp.SuperApplication
import com.jmd.superapp.core.AppFileLogger
import com.jmd.superapp.data.model.ExecutionLog
import com.jmd.superapp.data.repo.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What it is:
 * Delivery-report sink for outgoing SMS parts.
 */
class SmsDeliveryReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsDelivery"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != com.jmd.superapp.engine.SmsSender.ACTION_SMS_DELIVERED) return

        val recipient = intent.getStringExtra("recipient") ?: return
        if (recipient.isBlank()) return

        val taskId = intent.getLongExtra("task_id", 0L)
        val partIndex = intent.getIntExtra("part_index", 0) + 1
        val parts = intent.getIntExtra("parts", 1).coerceAtLeast(1)
        val delivered = resultCode == Activity.RESULT_OK

        val pendingResult = goAsync()
        val scope = (context.applicationContext as? SuperApplication)?.applicationScope
            ?: CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch(Dispatchers.IO) {
            try {
                val repository = AppRepository(context)
                if (delivered) {
                    repository.insertLog(
                        ExecutionLog(
                            taskId = taskId,
                            channel = "SMS Delivery",
                            recipient = recipient,
                            messageSnippet = "",
                            status = "DELIVERED",
                            details = "Carrier confirmed delivery of part $partIndex/$parts"
                        )
                    )
                    AppFileLogger.i(context, TAG, "Delivery confirmed for $recipient (part $partIndex/$parts)")
                } else {
                    repository.insertLog(
                        ExecutionLog(
                            taskId = taskId,
                            channel = "SMS Delivery",
                            recipient = recipient,
                            messageSnippet = "",
                            status = "FAILED",
                            details = "Carrier reported delivery failure for part $partIndex/$parts (code $resultCode)"
                        )
                    )
                    AppFileLogger.w(context, TAG, "Delivery failed for $recipient (part $partIndex/$parts, code $resultCode)")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to record SMS delivery report", t)
            } finally {
                try {
                    pendingResult.finish()
                } catch (t: Throwable) {
                    Log.d(TAG, "pendingResult.finish: ${t.message}")
                }
            }
        }
    }
}
