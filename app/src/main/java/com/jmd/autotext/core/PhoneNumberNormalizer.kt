/**
 * Package: com.jmd.autotext.core
 * Module: Core Automation Utilities
 *
 * Provides international and national phone number standardization, emergency service rejection,
 * contact verification, duplicate detection, and multi-delimiter input ingestion.
 */
package com.jmd.autotext.core

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.jmd.autotext.data.db.DatabaseHelper

/**
 * Normalization, validation, and parsing engine for cellular phone numbers and recipient inputs.
 */
object PhoneNumberNormalizer {

    /**
     * Standardizes raw phone input into E.164 international format (e.g. +919876543210).
     *
     * Automatically strips whitespace, punctuation, and leading zeroes.
     * Safely returns empty string if input matches an emergency service number.
     *
     * @param raw Raw input string from user, clipboard, or contact book.
     * @param defaultCountryCode Default international dialing code (defaults to "+91").
     * @return E.164 normalized phone string with leading '+', or empty string if invalid.
     */
    fun normalize(raw: String, defaultCountryCode: String = "+91"): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || isEmergencyNumber(trimmed)) return ""

        val clean = trimmed.replace(Regex("[^+0-9]"), "")
        if (clean.startsWith("+")) {
            val digits = clean.drop(1).filter(Char::isDigit)
            return if (digits.isNotEmpty()) "+$digits" else ""
        }

        val digits = clean.filter(Char::isDigit)
        if (digits.isEmpty()) return ""

        return when {
            digits.startsWith("00") && (digits.length > 2) -> "+" + digits.drop(2)
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
     * Formats normalized phone number specifically for WhatsApp deep-links (stripping leading '+').
     *
     * @param raw Raw phone number input.
     * @param defaultCountryCode Country code prefix.
     * @return Pure digit string formatted for whatsapp:// and api.whatsapp.com URIs.
     */
    fun formatForWhatsApp(raw: String, defaultCountryCode: String = "+91"): String {
        val norm = normalize(raw, defaultCountryCode)
        return norm.replace("+", "").trim()
    }

    /**
     * Checks if two phone number strings represent the same recipient.
     */
    fun isSamePhoneNumber(p1: String, p2: String): Boolean {
        if (p1.equals(p2, ignoreCase = true)) return true
        val norm1 = normalize(p1)
        val norm2 = normalize(p2)
        if (norm1.isNotBlank() && norm2.isNotBlank() && norm1 == norm2) return true
        val d1 = p1.filter { it.isDigit() }
        val d2 = p2.filter { it.isDigit() }
        if (d1.isNotBlank() && d2.isNotBlank()) {
            if (d1 == d2) return true
            if (d1.length >= 10 && d2.length >= 10 && d1.takeLast(10) == d2.takeLast(10)) return true
        }
        return false
    }

    /**
     * Extracts raw phone digits from decorated contact labels (e.g., "+919876543210 (John)").
     *
     * @param decorated Formatted string containing number and optional trailing name.
     * @return Extracted raw phone number digits.
     */
    fun extractRawPhoneNumber(decorated: String): String {
        val trimmed = decorated.trim()
        if (trimmed.isEmpty()) return ""
        val parenIdx = trimmed.indexOf('(')
        val candidate = if (parenIdx > 0) trimmed.substring(0, parenIdx).trim() else trimmed
        return cleanPhoneNumber(candidate)
    }

    /**
     * Strips whitespace, parentheses, and dashes from a phone string.
     *
     * @param raw Target raw phone string.
     * @return Stripped phone string.
     */
    fun cleanPhoneNumber(raw: String): String {
        return raw.trim().replace(Regex("[\\s\\-()]"), "")
    }

    /**
     * Verifies whether a given number represents a genuine mobile subscriber rather than a landline,
     * toll-free service (1800/1860/800), or invalid repetitive sequence.
     *
     * @param raw Raw or normalized phone number string.
     * @return True if valid mobile number, false otherwise.
     */
    fun isMobileNumber(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val trimmed = raw.trim()

        if (trimmed.any { it in 'a'..'z' || it in 'A'..'Z' }) return false
        val digits = trimmed.filter(Char::isDigit)

        if (digits.length !in 10..15) return false

        if (digits.startsWith("1800") || digits.startsWith("1860") ||
            (digits.length == 10 && (digits.startsWith("800") || digits.startsWith("888") || digits.startsWith("877") || digits.startsWith("866") || digits.startsWith("855")))) {
            return false
        }

        if (digits.all { it == digits[0] }) return false

        if (digits.startsWith("44") && digits.length in 11..13) {
            return digits.substring(2).startsWith("7")
        }
        if (digits.startsWith("971") && digits.length in 11..13) {
            return digits.substring(3).startsWith("5")
        }
        if (digits.startsWith("61") && digits.length in 11..12) {
            return digits.substring(2).startsWith("4")
        }
        if (digits.startsWith("1") && digits.length == 11) {
            val areaCode = digits.substring(1, 4)
            val tollFree = listOf("800", "888", "877", "866", "855", "844", "833", "900")
            return areaCode !in tollFree
        }

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

        return true
    }

    /**
     * Queries the Android device ContactsContract to resolve a contact's display name by phone.
     *
     * @param context Active Android [Context].
     * @param number Phone number to look up.
     * @return Contact display name string, or null if unlisted.
     */
    fun getContactName(context: Context, number: String): String? {
        if (number.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number),
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Determines whether an identifier (phone number or name) corresponds to an existing contact
     * in the device address book or local dynamic contact pool.
     *
     * @param context Active Android [Context].
     * @param identifier Phone number or contact name string.
     * @return True if recognized as a known contact, false otherwise.
     */
    fun isContact(context: Context, identifier: String): Boolean {
        if (identifier.isBlank()) return false
        if (getContactName(context, identifier) != null) return true

        // Check if identifier matches a contact display name directly in Android ContactsContract
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.DISPLAY_NAME} = ? COLLATE NOCASE",
                arrayOf(identifier.trim()),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) return true
            }
        } catch (_: Exception) {}

        // Check dynamic contact pool as fallback
        return try {
            val db = DatabaseHelper.getInstance(context).readableDatabase
            val c = db.rawQuery(
                "SELECT id FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) LIMIT 1",
                arrayOf(identifier.trim())
            )
            c.use { it.moveToFirst() }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Identifies emergency service numbers (112, 911, 100, 101, 102, 108, 999) to strictly block automated dispatch.
     *
     * @param raw Target phone string.
     * @return True if identified as emergency number, false otherwise.
     */
    fun isEmergencyNumber(raw: String): Boolean {
        val digits = raw.filter(Char::isDigit)
        return digits in setOf("112", "911", "100", "101", "102", "108", "999")
    }

    /**
     * Detects duplicate phone numbers across diverse input formats (e.g. "9876543210" and "+91 98765-43210").
     *
     * @param numbers List of candidate raw phone numbers.
     * @return Map of normalized phone number to list of indices where duplicates occur.
     */
    fun detectDuplicates(numbers: List<String>): Map<String, List<Int>> {
        val occurrences = mutableMapOf<String, MutableList<Int>>()
        for ((idx, raw) in numbers.withIndex()) {
            val norm = normalize(raw)
            if (norm.isNotBlank()) {
                occurrences.getOrPut(norm) { mutableListOf() }.add(idx)
            }
        }
        return occurrences.filter { it.value.size > 1 }
    }

    /**
     * Ingests multi-line or comma/semicolon delimited recipient inputs into structured tokens.
     *
     * @param input Raw pasted text containing multiple recipient records.
     * @param defaultCountryCode Default country code for normalization.
     * @return List of Pairs: original raw token to normalized mobile number (or null if invalid).
     */
    fun parseMultiInput(input: String, defaultCountryCode: String = "+91"): List<Pair<String, String?>> {
        if (input.isBlank()) return emptyList()
        val tokens = input.split(Regex("[\n,;]+")).map { it.trim() }.filter { it.isNotEmpty() }
        return tokens.map { token ->
            val norm = normalize(token, defaultCountryCode)
            token to (if (norm.isNotBlank() && isMobileNumber(norm)) norm else null)
        }
    }
}
