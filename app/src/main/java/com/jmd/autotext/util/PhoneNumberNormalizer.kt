package com.jmd.autotext.util

object PhoneNumberNormalizer {
    fun normalize(raw: String, defaultCountryCode: String = "+91"): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        // Keep only digits and '+'
        val clean = trimmed.replace(Regex("[^+0-9]"), "")
        if (clean.startsWith("+")) {
            val digits = clean.drop(1).filter(Char::isDigit)
            return if (digits.isNotEmpty()) "+$digits" else ""
        }

        val digits = clean.filter(Char::isDigit)
        if (digits.isEmpty()) return ""

        return when {
            digits.startsWith("00") && digits.length > 2 -> "+" + digits.drop(2)
            digits.length == 11 && digits.startsWith("0") && defaultCountryCode.isNotBlank() ->
                defaultCountryCode + digits.drop(1)
            digits.length == 12 && digits.startsWith("91") ->
                "+$digits"
            digits.length == 10 && defaultCountryCode.isNotBlank() ->
                defaultCountryCode + digits
            else -> digits
        }
    }

    /**
     * Formats phone number specifically for WhatsApp deep links.
     * International format WITHOUT '+' and WITHOUT leading zeros (e.g. 919876543210).
     */
    fun formatForWhatsApp(raw: String, defaultCountryCode: String = "+91"): String {
        val norm = normalize(raw, defaultCountryCode)
        return norm.replace("+", "").trim()
    }

    fun isPersonalPhoneNumber(raw: String): Boolean {
        return isMobileNumber(raw)
    }

    /**
     * Strictly verifies whether a given phone number / caller address is a legitimate MOBILE number.
     * Rejects:
     * - Alphanumeric senders (e.g., "VM-HDFCBK", "AX-AIRTEL")
     * - Short codes (e.g., "121", "198", "1900", length < 10 digits)
     * - Landlines (Indian landlines with STD codes like 011, 022, 080 where number doesn't start with 6-9)
     * - Toll-free numbers (1800, 1860, 800, 888, 877, 866, 855)
     * - Repeating dummy numbers (e.g., "0000000000", "1111111111")
     *
     * Accepts:
     * - Indian mobile numbers: 10 digits starting with 6, 7, 8, 9 (optionally prefixed with +91, 91, or 0)
     * - International mobile numbers (10 to 15 digits)
     */
    fun isMobileNumber(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false

        val trimmed = raw.trim()

        // 1. Alphanumeric check (sender IDs like "VK-SBI", "HDFCBK")
        if (trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }) return false

        // Extract pure digits
        val digits = trimmed.filter(Char::isDigit)

        // 2. Length check: standard mobile numbers must have between 10 and 15 digits
        if (digits.length !in 10..15) return false

        // 3. Toll-free numbers rejection
        if (digits.startsWith("1800") || digits.startsWith("1860") ||
            (digits.length == 10 && (digits.startsWith("800") || digits.startsWith("888") || digits.startsWith("877") || digits.startsWith("866") || digits.startsWith("855")))) {
            return false
        }

        // 4. Dummy repetition rejection
        if (digits.all { it == digits[0] }) return false

        // 5. Specific International mobile checks
        if (digits.startsWith("44") && digits.length in 11..13) { // UK mobile (+44) typically 7xxx
            return digits.substring(2).startsWith("7")
        }
        if (digits.startsWith("971") && digits.length in 11..13) { // UAE mobile (+971) typically 5xxx
            return digits.substring(3).startsWith("5")
        }
        if (digits.startsWith("61") && digits.length in 11..12) { // Australia mobile (+61) typically 4xxx
            return digits.substring(2).startsWith("4")
        }
        if (digits.startsWith("1") && digits.length == 11) { // US/Canada (+1)
            val areaCode = digits.substring(1, 4)
            val tollFree = listOf("800", "888", "877", "866", "855", "844", "833", "900")
            return areaCode !in tollFree
        }

        // 6. Indian mobile numbers validation (10 digits starting with 6, 7, 8, or 9)
        var nationalNumber = digits
        if (nationalNumber.startsWith("91") && nationalNumber.length == 12) {
            nationalNumber = nationalNumber.substring(2)
        } else if (nationalNumber.startsWith("0") && nationalNumber.length == 11) {
            nationalNumber = nationalNumber.substring(1)
        }

        if (nationalNumber.length == 10) {
            val firstDigit = nationalNumber[0]
            return firstDigit in listOf('6', '7', '8', '9')
        }

        return digits.length in 10..15
    }

    fun getContactName(context: android.content.Context, number: String): String? {
        if (number.isBlank()) return null
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isContact(context: android.content.Context, number: String): Boolean {
        return getContactName(context, number) != null
    }
}
