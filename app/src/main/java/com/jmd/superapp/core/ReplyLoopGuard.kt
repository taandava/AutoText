/**
 * Package: com.jmd.superapp.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Loop protection for automatic replies, split into a pure decision core ([ReplyLoopGuard]) and a
 * small Android-backed ledger ([ReplySafetyGuard]).
 *
 * What it does:
 * - Detects the device's own phone number(s) so the app never auto-replies to itself.
 * - Remembers the last reply sent to each recipient and suppresses an identical reply that would
 *   be sent again inside the cooldown window, which is what a bot-to-bot loop looks like.
 *
 * Linked With:
 * - Receivers & Services: [com.jmd.superapp.receiver.SmsReceiver], [com.jmd.superapp.service.TaskNotificationListenerService]
 * - Utilities: [PhoneNumberNormalizer]
 */

package com.jmd.superapp.core

import android.content.Context
import androidx.core.content.edit
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * What it is:
 * Pure, side-effect-free decision logic for reply-loop suppression.
 *
 * What it does:
 * Normalizes reply content into a fingerprint and decides whether a repeated reply inside the
 * cooldown window must be suppressed.
 */
object ReplyLoopGuard {

    /** Default cooldown applied between two identical replies to the same recipient. */
    const val DEFAULT_COOLDOWN_SECONDS = 120

    /** @property timestampMillis When the previous reply was sent. */
    /** @property fingerprint Normalized content of that reply. */
    data class Entry(val timestampMillis: Long, val fingerprint: String)

    /**
     * Normalizes content so trivial formatting differences do not defeat loop detection.
     */
    fun fingerprint(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * Returns true when the same content was already sent to the same recipient inside the cooldown.
     */
    fun shouldSuppress(
        previous: Entry?,
        nowMillis: Long,
        content: String,
        cooldownMillis: Long
    ): Boolean {
        if (previous == null) return false
        if (cooldownMillis <= 0L) return false
        if (nowMillis - previous.timestampMillis > cooldownMillis) return false
        return previous.fingerprint == fingerprint(content)
    }

    /**
     * Storage key for a recipient. Uses the normalized number when possible, otherwise the raw label
     * (WhatsApp notifications only expose a display name).
     */
    fun storageKey(recipient: String): String =
        "reply_guard_" + PhoneNumberNormalizer.normalize(recipient).ifBlank {
            recipient.trim().lowercase()
        }
}

/**
 * What it is:
 * Android-backed implementation of reply safety checks.
 *
 * What it does:
 * Reads the device's own subscriber numbers and keeps a small per-recipient ledger of the last
 * automatic reply.
 */
class ReplySafetyGuard(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("reply_safety", Context.MODE_PRIVATE)

    /**
     * True when the given address is one of the device's own numbers.
     * Falls back to false when the carrier does not expose the number (common on modern SIMs).
     */
    fun isSelfNumber(rawNumber: String?): Boolean {
        val candidate = normalizeDigits(rawNumber)
        if (candidate.length < 7) return false

        val ownNumbers = readOwnSubscriberNumbers()
        return ownNumbers.any { own ->
            val normalizedOwn = normalizeDigits(own)
            normalizedOwn.isNotEmpty() &&
                (candidate == normalizedOwn ||
                    (candidate.length >= 10 && normalizedOwn.endsWith(candidate.takeLast(10))) ||
                    (normalizedOwn.length >= 10 && candidate.endsWith(normalizedOwn.takeLast(10))))
        }
    }

    /**
     * Reads the SIM numbers the platform exposes.
     *
     * The legacy SubscriptionInfo.number and TelephonyManager.line1Number fields are deprecated and
     * return placeholders on most modern carriers, so only the API 33+ SubscriptionManager lookup is used.
     */
    private fun readOwnSubscriberNumbers(): List<String> {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return emptyList()
        val granted = ContextCompat.checkSelfPermission(
            appContext, android.Manifest.permission.READ_PHONE_NUMBERS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return emptyList()

        val subManager = appContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()
        return try {
            subManager.activeSubscriptionInfoList.orEmpty().mapNotNull { sub ->
                runCatching { subManager.getPhoneNumber(sub.subscriptionId) }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.d("ReplySafetyGuard", "Subscription number lookup unavailable: ${e.message}")
            emptyList()
        }
    }

    /**
     * True when an identical reply to this recipient was sent inside the cooldown window.
     */
    fun shouldSuppressReply(
        recipient: String,
        content: String,
        cooldownSeconds: Int = ReplyLoopGuard.DEFAULT_COOLDOWN_SECONDS
    ): Boolean {
        val entry = lastReply(recipient) ?: return false
        return ReplyLoopGuard.shouldSuppress(
            previous = entry,
            nowMillis = System.currentTimeMillis(),
            content = content,
            cooldownMillis = cooldownSeconds.coerceAtLeast(0) * 1000L
        )
    }

    /** Records that an automatic reply was successfully dispatched. */
    fun recordReply(recipient: String, content: String) {
        val key = ReplyLoopGuard.storageKey(recipient)
        prefs.edit {
            putLong(key + "_at", System.currentTimeMillis())
            putString(key + "_fp", ReplyLoopGuard.fingerprint(content))
        }
    }

    private fun lastReply(recipient: String): ReplyLoopGuard.Entry? {
        val key = ReplyLoopGuard.storageKey(recipient)
        val at = prefs.getLong(key + "_at", 0L)
        if (at <= 0L) return null
        val fingerprint = prefs.getString(key + "_fp", null) ?: return null
        return ReplyLoopGuard.Entry(at, fingerprint)
    }

    private fun normalizeDigits(raw: String?): String =
        raw?.filter(Char::isDigit)?.takeLast(10) ?: ""
}
