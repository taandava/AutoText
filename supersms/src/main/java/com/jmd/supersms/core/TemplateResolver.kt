/**
 * Package: com.jmd.supersms.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [TemplateResolver].
 *
 * What it does:
 * Provides dynamic placeholder substitution for scheduled messages, auto-replies and campaigns.
 *
 * Linked With:
 * - Core Security & Utilities: [java.util.*]
 * - Core Personalization: [MessageVariables]
 */

package com.jmd.supersms.core

import java.text.SimpleDateFormat
import java.util.*

/** Default fallback message text across the application (clean empty default). */
const val DEFAULT_MESSAGE_TEXT = ""

/**
 * What it is:
 * Dynamic message token replacer.
 *
 * What it does:
 * Replaces every placeholder listed in [MessageVariables] with live date, time,
 * greeting, currency, recipient or channel values.
 *
 * Linked with:
 * - Architecture: [MessageVariables], [resolve]
 */
object MessageTemplateResolver {

    /**
     * What it is:
     * Resolves all placeholder tokens within a message string.
     *
     * What it does:
     * Executes [resolve] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [MessageVariables], [resolve]
     * @param template Raw message text containing token patterns.
     * @param phone Recipient's phone number or identifier.
     * @param event Associated event trigger description (e.g. "missed call").
     * @param targetTimestamp Milliseconds timestamp used for date/time formatting.
     * @param channel Channel display name or type (e.g. "SMS", "Call").
     * @param recipientCount Total recipients targeted in task/dispatch batch.
     * @return Fully resolved message string.
     */
    fun resolve(
        template: String,
        phone: String = "",
        event: String = "",
        targetTimestamp: Long = System.currentTimeMillis(),
        channel: String = "",
        recipientCount: Int = 1,
        prefs: com.jmd.supersms.data.prefs.PreferenceManager? = null,
    ): String {
        if (template.isEmpty()) return ""
        val cal = Calendar.getInstance().apply { timeInMillis = targetTimestamp }
        val locale = Locale.getDefault()

        val activePrefs = prefs ?: runCatching {
            val app = com.jmd.supersms.SuperApplication.instance
            com.jmd.supersms.data.prefs.PreferenceManager(app)
        }.getOrNull()

        val dayOfWeek = SimpleDateFormat("EEEE", locale).format(cal.time)

        // Resolve customizable date pattern
        val datePattern = activePrefs?.buildEffectiveDateFormat() ?: resolveEffectiveDatePattern(
            baseFormat = activePrefs?.customDateFormat ?: "dd MMM yyyy",
            customPattern = activePrefs?.customDateCustomPattern ?: "dd-MM-yyyy",
            separator = activePrefs?.customDateSeparator ?: "auto"
        )
        val dateFormatted = try {
            SimpleDateFormat(datePattern, locale).format(cal.time)
        } catch (_: IllegalArgumentException) {
            SimpleDateFormat("dd-MMM-yyyy", locale).format(cal.time)
        }

        // Resolve customizable time pattern (12hr vs 24hr)
        val timePattern = if (activePrefs?.timeFormatOption == "24HR") "HH:mm" else "hh:mm a"
        val timeFormatted = try {
            SimpleDateFormat(timePattern, locale).format(cal.time)
        } catch (_: IllegalArgumentException) {
            SimpleDateFormat("hh:mm a", locale).format(cal.time)
        }

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }

        val resolvedGreeting = if (activePrefs?.salutationOption == "CUSTOM") {
            activePrefs.customGreetingCustomText.ifBlank { timeGreeting }
        } else {
            timeGreeting
        }

        val resolvedCurrency = if (activePrefs?.currencyOption == "CUSTOM") {
            activePrefs.customCurrencySymbol.ifBlank { "\u20B9" }
        } else {
            "\u20B9"
        }
        val cleanPhone = PhoneNumberNormalizer.extractRawPhoneNumber(phone)

        return template
            .replace("{greeting}", resolvedGreeting, ignoreCase = true)
            .replace("{salutation}", resolvedGreeting, ignoreCase = true)
            .replace("{rupee}", resolvedCurrency, ignoreCase = true)
            .replace("{currency}", resolvedCurrency, ignoreCase = true)
            .replace("{day}", dayOfWeek, ignoreCase = true)
            .replace("{date}", dateFormatted, ignoreCase = true)
            .replace("{time}", timeFormatted, ignoreCase = true)
            .replace("{phone}", cleanPhone, ignoreCase = true)
    }

    /**
     * What it is:
     * Resolves the final SimpleDateFormat pattern based on base format choice, custom pattern, and custom separator.
     *
     * What it does:
     * Executes [resolveEffectiveDatePattern] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [MessageTemplateResolver], [resolveEffectiveDatePattern]
     */
    fun resolveEffectiveDatePattern(
        baseFormat: String,
        customPattern: String = "dd-MM-yyyy",
        separator: String = "auto"
    ): String {
        val pattern = if (baseFormat.equals("CUSTOM", ignoreCase = true)) {
            customPattern.ifBlank { "dd-MM-yyyy" }
        } else {
            baseFormat
        }

        if (separator == "auto" || separator.isEmpty() && !pattern.contains(Regex("[-/.]"))) {
            return pattern
        }

        // Only substitute separators in date patterns containing standard delimiter characters (- / . or space)
        return when (separator) {
            "-" -> pattern.replace('/', '-').replace('.', '-')
            "/" -> pattern.replace('-', '/').replace('.', '/')
            "." -> pattern.replace('-', '.').replace('/', '.')
            " " -> pattern.replace('-', ' ').replace('/', ' ').replace('.', ' ')
            "" -> pattern.replace("-", "").replace("/", "").replace(".", "")
            else -> pattern
        }
    }
}
