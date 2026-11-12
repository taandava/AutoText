/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [TemplateResolver].
 *
 * What it does:
 * Provides dynamic placeholder substitution for scheduled message drafting and templating.
 *
 * Linked With:
 * - Core Security & Utilities: [java.util.*]
 */

package com.jmd.helper.core

import java.text.SimpleDateFormat
import java.util.*

/** Default fallback message text across the application (clean empty default). */
const val DEFAULT_MESSAGE_TEXT = ""

/**
 * What it is:
 * Dynamic message template token replacer.
 *
 * What it does:
 * Supports contextual tokens:
 *
 * Linked with:
 * - Architecture: [TemplateResolver], [MessageTemplateResolver]
 */
object MessageTemplateResolver {

    /**
     * What it is:
     * Resolves all placeholder tokens within a template string.
     *
     * What it does:
     * Executes [resolve] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TemplateResolver], [resolve]
     * @param template Raw template text containing token patterns.
     * @param name Recipient's display name (defaults to "there" if blank).
     * @param phone Recipient's phone number or identifier.
     * @param event Associated event trigger description (e.g. "missed call").
     * @param targetTimestamp Milliseconds timestamp used for date/time formatting.
     * @param channel Channel display name or type (e.g. "SMS", "WhatsApp").
     * @param recipientCount Total recipients targeted in task/dispatch batch.
     * @return Fully resolved message string.
     */
    fun resolve(
        template: String,
        name: String = "",
        phone: String = "",
        event: String = "",
        targetTimestamp: Long = System.currentTimeMillis(),
        channel: String = "",
        recipientCount: Int = 1,
        prefs: com.jmd.helper.data.prefs.PreferenceManager? = null,
    ): String {
        if (template.isEmpty()) return ""
        val cal = Calendar.getInstance().apply { timeInMillis = targetTimestamp }
        val locale = Locale.getDefault()

        val activePrefs = prefs ?: runCatching {
            val app = com.jmd.helper.HelperApplication.instance
            com.jmd.helper.data.prefs.PreferenceManager(app)
        }.getOrNull()

        val dayOfWeek = SimpleDateFormat("EEEE", locale).format(cal.time)
        val shortDay = SimpleDateFormat("EEE", locale).format(cal.time)
        val dayNumber = SimpleDateFormat("dd", locale).format(cal.time)

        // Resolve customizable date pattern
        val datePattern = resolveEffectiveDatePattern(
            baseFormat = activePrefs?.customDateFormat ?: "dd MMM yyyy",
            customPattern = activePrefs?.customDateCustomPattern ?: "dd-MM-yyyy",
            separator = activePrefs?.customDateSeparator ?: "auto"
        )
        val dateFormatted = try {
            SimpleDateFormat(datePattern, locale).format(cal.time)
        } catch (_: Exception) {
            SimpleDateFormat("dd MMM yyyy", locale).format(cal.time)
        }

        // Resolve customizable short date pattern
        val shortDatePattern = resolveEffectiveDatePattern(
            baseFormat = activePrefs?.customShortDateFormat ?: "dd/MM/yyyy",
            customPattern = "dd/MM/yyyy",
            separator = activePrefs?.customDateSeparator ?: "auto"
        )
        val shortDate = try {
            SimpleDateFormat(shortDatePattern, locale).format(cal.time)
        } catch (_: Exception) {
            SimpleDateFormat("dd/MM/yyyy", locale).format(cal.time)
        }

        // Resolve customizable time pattern
        val timePattern = activePrefs?.customTimeFormat ?: "hh:mm a"
        val timeFormatted = try {
            SimpleDateFormat(timePattern, locale).format(cal.time)
        } catch (_: Exception) {
            SimpleDateFormat("hh:mm a", locale).format(cal.time)
        }

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }

        val resolvedGreeting = when (activePrefs?.customGreetingStyle?.uppercase()) {
            "NAMASTE" -> activePrefs.customNamasteText.ifBlank { "Namaste" }
            "PRANAM" -> "Pranam"
            "HELLO" -> "Hello"
            "HI" -> "Hi"
            "CUSTOM" -> activePrefs.customGreetingCustomText.ifBlank { timeGreeting }
            else -> timeGreeting // "DYNAMIC" or null
        }

        val resolvedNamaste = activePrefs?.customNamasteText?.ifBlank { "Namaste" } ?: "Namaste"
        val resolvedCurrency = activePrefs?.customCurrencySymbol?.ifBlank { "₹" } ?: "₹"
        val resolvedCurrencyCode = if (resolvedCurrency == "₹") "INR" else resolvedCurrency

        val cleanName = name.trim().ifBlank { "there" }
        val firstName = cleanName.split(Regex("\\s+")).firstOrNull() ?: cleanName
        val cleanPhone = PhoneNumberNormalizer.extractRawPhoneNumber(phone)

        val istCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply { timeInMillis = targetTimestamp }
        val istTimeFormatted = SimpleDateFormat("hh:mm a 'IST'", Locale.forLanguageTag("en-IN")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(istCal.time)

        // Expanded explicit date and time variants
        val ddmmyyyy = SimpleDateFormat("ddMMyyyy", locale).format(cal.time)
        val ddmmyy = SimpleDateFormat("ddMMyy", locale).format(cal.time)
        val ddMmYyyyDash = SimpleDateFormat("dd-MM-yyyy", locale).format(cal.time)
        val ddMmYyDash = SimpleDateFormat("dd-MM-yy", locale).format(cal.time)
        val ddMmYyyySlash = SimpleDateFormat("dd/MM/yyyy", locale).format(cal.time)
        val ddMmYyyyDot = SimpleDateFormat("dd.MM.yyyy", locale).format(cal.time)
        val dateWords = SimpleDateFormat("dd MMMM yyyy", locale).format(cal.time)
        val monthNum = SimpleDateFormat("MM", locale).format(cal.time)
        val monthName = SimpleDateFormat("MMMM", locale).format(cal.time)
        val shortMonthName = SimpleDateFormat("MMM", locale).format(cal.time)
        val fullYear = SimpleDateFormat("yyyy", locale).format(cal.time)
        val shortYear = SimpleDateFormat("yy", locale).format(cal.time)
        val time12h = SimpleDateFormat("hh:mm a", locale).format(cal.time)
        val time24h = SimpleDateFormat("HH:mm", locale).format(cal.time)
        val ampm = SimpleDateFormat("a", locale).format(cal.time)
        val timeHm = SimpleDateFormat("hh:mm", locale).format(cal.time)

        return template
            .replace("{greeting}", resolvedGreeting, ignoreCase = true)
            .replace("{time_greeting}", timeGreeting, ignoreCase = true)
            .replace("{namaste}", resolvedNamaste, ignoreCase = true)
            .replace("{pranam}", "Pranam", ignoreCase = true)
            .replace("{rupee}", resolvedCurrency, ignoreCase = true)
            .replace("{currency}", resolvedCurrencyCode, ignoreCase = true)
            .replace("{today}", dateFormatted, ignoreCase = true)
            .replace("{ist_time}", istTimeFormatted, ignoreCase = true)
            .replace("{day}", dayOfWeek, ignoreCase = true)
            .replace("{short_day}", shortDay, ignoreCase = true)
            .replace("{date}", dateFormatted, ignoreCase = true)
            .replace("{short_date}", shortDate, ignoreCase = true)
            .replace("{date_ddmmyyyy}", ddmmyyyy, ignoreCase = true)
            .replace("{date_ddmmyy}", ddmmyy, ignoreCase = true)
            .replace("{date_dd_mm_yyyy}", ddMmYyyyDash, ignoreCase = true)
            .replace("{date_dd_mm_yy}", ddMmYyDash, ignoreCase = true)
            .replace("{date_slash}", ddMmYyyySlash, ignoreCase = true)
            .replace("{date_dot}", ddMmYyyyDot, ignoreCase = true)
            .replace("{date_words}", dateWords, ignoreCase = true)
            .replace("{month}", monthNum, ignoreCase = true)
            .replace("{month_name}", monthName, ignoreCase = true)
            .replace("{short_month}", shortMonthName, ignoreCase = true)
            .replace("{year}", fullYear, ignoreCase = true)
            .replace("{year_short}", shortYear, ignoreCase = true)
            .replace("{day_number}", dayNumber, ignoreCase = true)
            .replace("{day_num}", dayNumber, ignoreCase = true)
            .replace("{time}", timeFormatted, ignoreCase = true)
            .replace("{time_12h}", time12h, ignoreCase = true)
            .replace("{time_24h}", time24h, ignoreCase = true)
            .replace("{ampm}", ampm, ignoreCase = true)
            .replace("{time_hm}", timeHm, ignoreCase = true)
            .replace("{name}", cleanName, ignoreCase = true)
            .replace("{first_name}", firstName, ignoreCase = true)
            .replace("{phone}", cleanPhone, ignoreCase = true)
            .replace("{event}", event.ifBlank { "notification" }, ignoreCase = true)
            .replace("{channel}", channel.ifBlank { "Helper" }, ignoreCase = true)
            .replace("{recipient_count}", recipientCount.toString(), ignoreCase = true)
    }

    /**
     * What it is:
     * Resolves the final SimpleDateFormat pattern based on base format choice, custom pattern, and custom separator.
     *
     * What it does:
     * Executes [resolveEffectiveDatePattern] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [TemplateResolver], [resolveEffectiveDatePattern]
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
