/**
 * Package: com.jmd.helper.core
 * Module: Core Task Utilities
 *
 * Provides dynamic placeholder substitution for scheduled message drafting and templating.
 */
package com.jmd.helper.core

import java.text.SimpleDateFormat
import java.util.*

/** Default fallback message text across the application (clean empty default). */
const val DEFAULT_MESSAGE_TEXT = ""

/**
 * Dynamic message template token replacer.
 *
 * Supports contextual tokens:
 * - Date/Time: `{date}`, `{short_date}`, `{time}`, `{day}`, `{short_day}`, `{day_number}`, `{day_num}`, `{today}`, `{ist_time}`
 * - Recipient: `{name}`, `{first_name}`, `{phone}`, `{recipient_count}`
 * - Contextual: `{event}`, `{greeting}`, `{time_greeting}`, `{namaste}`, `{pranam}`, `{rupee}`, `{currency}`, `{channel}`
 */
object MessageTemplateResolver {

    /**
     * Resolves all placeholder tokens within a template string.
     *
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
    ): String {
        if (template.isEmpty()) return ""
        val cal = Calendar.getInstance().apply { timeInMillis = targetTimestamp }
        val locale = Locale.getDefault()

        val dayOfWeek = SimpleDateFormat("EEEE", locale).format(cal.time)
        val shortDay = SimpleDateFormat("EEE", locale).format(cal.time)
        val dateFormatted = SimpleDateFormat("dd MMM yyyy", locale).format(cal.time)
        val shortDate = SimpleDateFormat("dd/MM/yyyy", locale).format(cal.time)
        val dayNumber = SimpleDateFormat("dd", locale).format(cal.time)
        val timeFormatted = SimpleDateFormat("hh:mm a", locale).format(cal.time)

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }

        val cleanName = name.trim().ifBlank { "there" }
        val firstName = cleanName.split(Regex("\\s+")).firstOrNull() ?: cleanName
        val cleanPhone = PhoneNumberNormalizer.extractRawPhoneNumber(phone)

        val istCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply { timeInMillis = targetTimestamp }
        val istTimeFormatted = SimpleDateFormat("hh:mm a 'IST'", Locale.forLanguageTag("en-IN")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(istCal.time)

        return template
            .replace("{greeting}", timeGreeting, ignoreCase = true)
            .replace("{time_greeting}", timeGreeting, ignoreCase = true)
            .replace("{namaste}", "Namaste", ignoreCase = true)
            .replace("{pranam}", "Pranam", ignoreCase = true)
            .replace("{rupee}", "₹", ignoreCase = true)
            .replace("{currency}", "INR", ignoreCase = true)
            .replace("{today}", dateFormatted, ignoreCase = true)
            .replace("{ist_time}", istTimeFormatted, ignoreCase = true)
            .replace("{day}", dayOfWeek, ignoreCase = true)
            .replace("{short_day}", shortDay, ignoreCase = true)
            .replace("{date}", dateFormatted, ignoreCase = true)
            .replace("{short_date}", shortDate, ignoreCase = true)
            .replace("{day_number}", dayNumber, ignoreCase = true)
            .replace("{day_num}", dayNumber, ignoreCase = true)
            .replace("{time}", timeFormatted, ignoreCase = true)
            .replace("{name}", cleanName, ignoreCase = true)
            .replace("{first_name}", firstName, ignoreCase = true)
            .replace("{phone}", cleanPhone, ignoreCase = true)
            .replace("{event}", event.ifBlank { "notification" }, ignoreCase = true)
            .replace("{channel}", channel.ifBlank { "Helper" }, ignoreCase = true)
            .replace("{recipient_count}", recipientCount.toString(), ignoreCase = true)
    }
}
