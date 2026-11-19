/**
 * Package: com.jmd.superwa.telephony
 * Module: Autonomous Telephony Orchestration Engine
 *
 * What it is:
 * Autonomous Telephony Orchestration Engine providing native call dispatch, automated call tracking,
 * permission-guarded native call termination, call state verification, and cross-feature collision gating.
 *
 * What it does:
 * - Coordinates autonomous phone dialing via TelecomManager and Intent fallbacks.
 * - Terminates calls natively on Android 9+ (API 28+) using ANSWER_PHONE_CALLS without requiring AccessibilityService.
 * - Tracks automated scheduled calls to prevent unintended auto-reply triggers in [PhoneCallReceiver].
 * - Provides non-blocking call status inspection to prevent background campaigns from conflicting with active calls.
 *
 * Linked With:
 * - Architecture: [com.jmd.superwa.compat.TelephonyManagerCompat], [com.jmd.superwa.service.TaskAccessibilityService]
 * - Logging & Utilities: [com.jmd.superwa.core.FileLogger], [com.jmd.superwa.util.PhoneNumberNormalizer]
 * - Permissions & System: [android.telecom.TelecomManager], [android.telephony.TelephonyManager]
 */

package com.jmd.superwa.telephony

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.jmd.superwa.compat.TelephonyManagerCompat
import com.jmd.superwa.core.AppFileLogger
import com.jmd.superwa.core.OemBrandHelper
import com.jmd.superwa.service.TaskAccessibilityService
import com.jmd.superwa.core.PhoneNumberNormalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * What it is:
 * Singleton engine for autonomous telephony management and cross-feature call coordination.
 *
 * What it does:
 * Manages cellular call dispatch, tracking, termination, and state verification.
 *
 * Linked with:
 * - Component: [TelephonyCallEngine]
 */
object TelephonyCallEngine {

    private const val TAG = "TelephonyCallEngine"
    private const val AUTOMATED_CALL_TTL_MS = 15 * 60 * 1000L // 15 minutes TTL

    /**
     * In-memory thread-safe registry of automated calls initiated by the Super application.
     * Maps normalized phone number to dispatch timestamp.
     */
    private val automatedCalls = ConcurrentHashMap<String, Long>()

    /**
     * What it is:
     * Registers a phone number as an active automated call dispatched by Super.
     *
     * What it does:
     * Stores timestamped entry and purges expired entries to prevent memory leaks.
     *
     * Linked with:
     * - Architecture: [TelephonyCallEngine], [trackAutomatedCall]
     * @param rawPhone Target phone number.
     */
    fun trackAutomatedCall(rawPhone: String) {
        val normalized = normalize(rawPhone)
        if (normalized.isNotBlank()) {
            val now = System.currentTimeMillis()
            automatedCalls[normalized] = now
            purgeExpiredEntries(now)
        }
    }

    /**
     * What it is:
     * Checks if a phone number was part of an automated scheduled call within the TTL window.
     *
     * What it does:
     * Verifies existence and TTL expiration.
     *
     * Linked with:
     * - Architecture: [TelephonyCallEngine], [isAutomatedCall]
     * @param rawPhone Phone number to query.
     * @return True if number matches an automated call initiated within the last 15 minutes.
     */
    fun isAutomatedCall(rawPhone: String): Boolean {
        val normalized = normalize(rawPhone)
        if (normalized.isBlank()) return false
        val now = System.currentTimeMillis()
        val timestamp = automatedCalls[normalized] ?: return false
        if (now - timestamp > AUTOMATED_CALL_TTL_MS) {
            automatedCalls.remove(normalized)
            return false
        }
        return true
    }

    /**
     * What it is:
     * Clears an automated call tracking entry after post-call handling completes.
     *
     * What it does:
     * Removes the entry from the registry.
     *
     * Linked with:
     * - Architecture: [TelephonyCallEngine], [clearAutomatedCall]
     * @param rawPhone Phone number to clear.
     */
    fun clearAutomatedCall(rawPhone: String) {
        val normalized = normalize(rawPhone)
        if (normalized.isNotBlank()) {
            automatedCalls.remove(normalized)
        }
    }

    /**
     * What it is:
     * Determines whether any cellular call is currently active, dialing, ringing, or in conversation.
     *
     * What it does:
     * Evaluates TelephonyManager and TelecomManager states across all active SIM subscriptions.
     *
     * Linked with:
     * - Architecture: [TelephonyCallEngine], [isCallActive]
     * @param context Active Android [Context].
     * @return True if a cellular call is in progress.
     */
    fun isCallActive(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return TelephonyManagerCompat.isCallActiveOrRinging(context, tm)
    }

    /**
     * What it is:
     * Determines whether an active cellular call has connected and is currently in conversation (OFFHOOK).
     *
     * What it does:
     * Inspects TelephonyManager call state for active answered calls.
     *
     * Linked with:
     * - Architecture: [TelephonyCallEngine], [isCallConnected]
     * @param context Active Android [Context].
     * @return True only when an active call is answered and offhook.
     */
    fun isCallConnected(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return TelephonyManagerCompat.isCallConnected(context, tm)
    }

    /**
     * What it is:
     * Places an outgoing cellular phone call autonomously to the target recipient.
     *
     * What it does:
     * Resolves explicit SIM accounts, configures OEM telephony extras, tracks call dispatch,
     * and executes direct TelecomManager call placement or Intent fallback.
     *
     * Linked with:
     * - Architecture: [TelephonyCallEngine], [placeCall]
     * @param context Active Android [Context].
     * @param recipient Target phone number.
     * @param simSlot Target SIM slot (0 = default, 1 = SIM1, 2 = SIM2).
     * @return True if call was successfully dispatched or dialer launched.
     */
    fun placeCall(context: Context, recipient: String, simSlot: Int): Boolean {
        val cleanPhone = PhoneNumberNormalizer.cleanPhoneNumber(recipient)
        if (cleanPhone.length < 3) return false

        val hasCallPerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        trackAutomatedCall(cleanPhone)

        val uri = Uri.fromParts("tel", cleanPhone, null)
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val extras = Bundle().apply {
            if (simSlot in 1..2 && telecomManager != null) {
                val targetPhysicalSlot = simSlot - 1
                var matchedSubId: Int? = null
                try {
                    val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    val activeSubs = subManager?.activeSubscriptionInfoList
                    val targetSub = activeSubs?.find { it.simSlotIndex == targetPhysicalSlot }
                    matchedSubId = targetSub?.subscriptionId
                    val accounts = telecomManager.callCapablePhoneAccounts
                    val matchedAccount = accounts.find { handle ->
                        if (targetSub != null) {
                            val handleId = handle.id ?: ""
                            (handleId == targetSub.subscriptionId.toString()) ||
                                    ((targetSub.iccId != null) && (handleId.contains(targetSub.iccId)))
                        } else false
                    }
                    if (matchedAccount != null) {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, matchedAccount)
                    } else {
                        val candidate = accounts.getOrNull(targetPhysicalSlot) ?: accounts.firstOrNull()
                        if (candidate != null) {
                            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, candidate)
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error resolving SIM $simSlot: ${t.message}")
                }
                OemBrandHelper.populateTelecomSimExtras(this, targetPhysicalSlot, matchedSubId)
            }
        }

        if (hasCallPerm && telecomManager != null) {
            try {
                telecomManager.placeCall(uri, extras)
                AppFileLogger.action(context, TAG, "Placed direct telecom call to $cleanPhone (SIM $simSlot)")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "telecomManager.placeCall failed, falling back to Intent: ${e.message}")
            }
        }

        val callIntent = if (hasCallPerm) {
            Intent(Intent.ACTION_CALL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtras(extras)
            }
        } else {
            Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        return runCatching {
            context.startActivity(callIntent)
            AppFileLogger.action(context, TAG, "Launched call intent for $cleanPhone (direct=$hasCallPerm)")
            true
        }.onFailure { t ->
            // A failed call launch must be visible in diagnostics, otherwise a task silently looks "sent".
            android.util.Log.e(TAG, "Failed to launch call intent for $cleanPhone: ${t.message}", t)
            AppFileLogger.e(context, TAG, "Failed to launch call intent for $cleanPhone", t)
        }.getOrDefault(false)
    }

    /**
     * What it is:
     * Programmatically terminates an active cellular phone call.
     *
     * What it does:
     * First attempts native [TelecomManager.endCall] on API 28+ using ANSWER_PHONE_CALLS permission (without accessibility).
     * If native termination is unsupported or fails, falls back to [TaskAccessibilityService.endPhoneCall].
     *
     * Linked with:
     * - Architecture: [TelephonyCallEngine], [endCall]
     * @param context Active Android [Context].
     * @return True if the call termination command was successfully dispatched.
     */
    fun endCall(context: Context): Boolean {
        // 1. Primary: Native TelecomManager.endCall() on API 28+ (No accessibility required)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val hasAnswerPerm = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasAnswerPerm) {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (tm != null) {
                    try {
                        @Suppress("DEPRECATION")
                        val ended = tm.endCall()
                        if (ended) {
                            AppFileLogger.action(context, TAG, "Terminated active phone call via native TelecomManager.endCall()")
                            return true
                        }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Native endCall SecurityException: ${e.message}")
                    } catch (t: Throwable) {
                        Log.w(TAG, "Native endCall error: ${t.message}")
                    }
                }
            }
        }

        // 2. Secondary fallback: TaskAccessibilityService in-call UI click (SuperWA only)
        if (com.jmd.superwa.core.AppVariantHelper.isSuperWa) {
            val a11yEnded = TaskAccessibilityService.endPhoneCall(context)
            if (a11yEnded) {
                AppFileLogger.action(context, TAG, "Terminated active phone call via TaskAccessibilityService fallback")
                return true
            }
        }

        return false
    }

    /**
     * What it is:
     * Normalizes a phone number for unified registry storage and comparison.
     *
     * What it does:
     * Extracts digits and returns last 10 digits for consistent lookup across formats.
     *
     * Linked with:
     * - Architecture: [TelephonyCallEngine], [normalize]
     * @param phone Raw phone string.
     * @return Normalized digits-only phone key.
     */
    private fun normalize(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    /**
     * What it is:
     * Purges expired entries from the automated calls cache.
     *
     * What it does:
     * Iterates through registry and removes keys older than TTL.
     *
     * Linked with:
     * - Architecture: [TelephonyCallEngine], [purgeExpiredEntries]
     */
    private fun purgeExpiredEntries(now: Long) {
        val iterator = automatedCalls.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > AUTOMATED_CALL_TTL_MS) {
                iterator.remove()
            }
        }
    }
}
