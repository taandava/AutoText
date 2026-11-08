

/**
 * Package: com.jmd.helper.data.repo
 * Module: Data Access Layer
 *
 * Manages harvested WhatsApp contacts, group items, chat title validation heuristics,
 * contact book cross-referencing, and profile photo resolution.
 *
 * Linked With:
 * - Database: [com.jmd.helper.data.db.DatabaseHelper]
 * - Models: [com.jmd.helper.data.model.WhatsAppContactItem], [com.jmd.helper.data.model.WhatsAppGroupItem]
 * - Normalizer: [com.jmd.helper.core.PhoneNumberNormalizer]
 * - Scanner: [com.jmd.helper.whatsapp.WhatsAppChatScanner]
 * - UI Screens: `WhatsAppSetupScreen`, `ComposeTaskScreen`
 */
package com.jmd.helper.data.repo

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.database.sqlite.transaction
import com.jmd.helper.core.PhoneNumberNormalizer
import com.jmd.helper.data.db.DatabaseHelper
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.WhatsAppContactItem
import com.jmd.helper.data.model.WhatsAppGroupItem

/**
 * Repository managing WhatsApp contact items and address book lookups.
 */
class WhatsAppRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * Verifies that a user-selected or accessibility-scraped string corresponds to a valid conversation title.
     * Filters out standard UI controls, action prompts, status tabs, and header labels.
     *
     * @param name Scraped or selected chat title.
     * @return True if valid conversation title, false if blocked UI keyword.
     */
    fun isValidPickedChatName(name: String): Boolean {
        val trimmed = name.trim().removePrefix("~").trim()
        if (trimmed.length !in 1..150) return false
        if (trimmed.contains("\n") || trimmed.contains("\r")) return false

        val lower = trimmed.lowercase()
        val blockedExactOrPrefix = listOf(
            "whatsapp", "whatsapp business", "wa business", "w4b",
            "search", "chats", "updates", "calls", "tools", "camera", "settings",
            "more options", "new chat", "back", "video call", "voice call",
            "type a message", "message", "unread message", "create ads",
            "reach potential", "get started", "dismiss", "online", "typing",
            "last seen", "swipe down to reveal", "swipe down", "select all",
            "starred messages", "linked devices", "clear call log", "mute notifications",
            "yesterday", "today", "status", "my status", "channels", "communities",
            "new group", "new community", "new broadcast",
            "hidden updates", "find channels to follow", "explore more", "create channel",
            "inbox filters", "radio group", "add status", "recent updates", "view updates",
            "muted updates", "recent searches", "clear all", "all filter", "groups filter",
            "unread filter", "favorites filter", "new customer filter", "new order filter",
            "ask meta ai or search", "search group chats", "search chats",
            "manage groups", "manage group"
        )
        return !blockedExactOrPrefix.any { lower == it || lower.startsWith("$it ") || lower.startsWith("$it…") || lower.startsWith("$it...") }
    }

    /**
     * Resolves the cellular phone number for a contact name by checking the local dynamic contact pool,
     * falling back to the Android device ContactsContract provider.
     *
     * @param name Target contact display name.
     * @return Resolved phone number string or null if unlisted.
     */
    fun findPhoneForContactName(name: String): String? {
        val cleanName = name.trim().removePrefix("~").trim()
        if (cleanName.isBlank()) return null
        val digitsOnly = cleanName.replace(Regex("""[\s+\-()]"""), "")
        if (digitsOnly.length >= 7 && digitsOnly.all { it.isDigit() }) {
            return cleanName
        }

        // Clean alphanumeric name for fuzzy matching (strips emojis and special symbols)
        val alphaName = cleanName.replace(Regex("""[^\p{L}\p{Nd}\s]"""), "").trim()

        try {
            val db = dbHelper.readableDatabase
            // 1. Exact match in dynamic contact pool
            db.rawQuery(
                "SELECT phone FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) AND phone != '' AND enabled = 1 LIMIT 1",
                arrayOf(cleanName)
            ).use { c ->
                if (c.moveToNext()) {
                    val p = c.getString(0)
                    if (!p.isNullOrBlank()) return p
                }
            }

            // 2. Alphanumeric match in dynamic contact pool
            if (alphaName.isNotBlank() && alphaName != cleanName) {
                db.rawQuery(
                    "SELECT phone FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) AND phone != '' AND enabled = 1 LIMIT 1",
                    arrayOf(alphaName)
                ).use { c ->
                    if (c.moveToNext()) {
                        val p = c.getString(0)
                        if (!p.isNullOrBlank()) return p
                    }
                }
            }

            // 3. Prefix match in dynamic contact pool
            if (alphaName.length >= 3) {
                db.rawQuery(
                    "SELECT phone FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE (LOWER(name) LIKE ? OR LOWER(?) LIKE '%' || LOWER(name) || '%') AND phone != '' AND enabled = 1 LIMIT 1",
                    arrayOf("%${alphaName.lowercase()}%", alphaName.lowercase())
                ).use { c ->
                    if (c.moveToNext()) {
                        val p = c.getString(0)
                        if (!p.isNullOrBlank()) return p
                    }
                }
            }

            // 4. Exact match in saved custom recipients
            db.rawQuery(
                "SELECT phone_or_email FROM ${DatabaseHelper.TABLE_RECIPIENTS} WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) AND phone_or_email != '' LIMIT 1",
                arrayOf(cleanName)
            ).use { c ->
                if (c.moveToNext()) {
                    val p = c.getString(0)
                    if (!p.isNullOrBlank()) return p
                }
            }
        } catch (e: Exception) {
            Log.w("WhatsAppRepo", "findPhoneForContactName failed for $cleanName: ${e.message}")
        }

        // 5. System ContactsContract resolution
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                // Direct exact match
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "LOWER(TRIM(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME})) = LOWER(TRIM(?)) OR LOWER(TRIM(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY})) = LOWER(TRIM(?))",
                    arrayOf(cleanName, cleanName),
                    null
                )?.use { c ->
                    if (c.moveToNext()) {
                        val p = c.getString(0)
                        if (!p.isNullOrBlank()) return p
                    }
                }

                // Alphanumeric/fuzzy wildcard match in device contacts
                if (alphaName.length >= 3) {
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "LOWER(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}) LIKE ? OR LOWER(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY}) LIKE ?",
                        arrayOf("%${alphaName.lowercase()}%", "%${alphaName.lowercase()}%"),
                        null
                    )?.use { c ->
                        if (c.moveToNext()) {
                            val p = c.getString(0)
                            if (!p.isNullOrBlank()) return p
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("WhatsAppRepo", "findPhoneForContactName ContactsContract query failed: ${e.message}")
            }
        }
        return null
    }

    /**
     * Checks whether a contact has a verified numeric cellular phone number (excluding group/chat virtual identifiers).
     * Performs strict exact name matching only to avoid false positives on groups sharing substrings with contacts.
     */
    fun hasCellularPhoneContact(name: String): Boolean {
        val cleanName = name.trim().removePrefix("~").trim()
        if (cleanName.isBlank()) return false

        // 1. Check exact match in custom recipients
        try {
            val db = dbHelper.readableDatabase
            db.rawQuery(
                "SELECT phone_or_email FROM ${DatabaseHelper.TABLE_RECIPIENTS} WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) AND phone_or_email != '' LIMIT 1",
                arrayOf(cleanName)
            ).use { c ->
                if (c.moveToNext()) {
                    val p = c.getString(0).orEmpty().trim()
                    val digits = p.filter { it.isDigit() }
                    if (digits.length >= 7 && !p.startsWith("group:", ignoreCase = true) && !p.startsWith("chat:", ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Check strict exact match in device ContactsContract
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "LOWER(TRIM(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME})) = LOWER(TRIM(?)) OR LOWER(TRIM(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY})) = LOWER(TRIM(?))",
                    arrayOf(cleanName, cleanName),
                    null
                )?.use { c ->
                    if (c.moveToNext()) {
                        val p = c.getString(0).orEmpty().trim()
                        val digits = p.filter { it.isDigit() }
                        if (digits.length >= 7) {
                            return true
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return false
    }

    fun findPhotoForContactName(name: String): String {
        val cleanName = name.trim().removePrefix("~").trim()
        if (cleanName.isBlank()) return ""

        try {
            val db = dbHelper.readableDatabase
            db.rawQuery(
                "SELECT photo_uri FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE name = ? AND photo_uri != '' AND enabled = 1 LIMIT 1",
                arrayOf(cleanName)
            ).use { c ->
                if (c.moveToNext()) {
                    val p = c.getString(0)
                    if (!p.isNullOrBlank()) return p
                }
            }
        } catch (e: Exception) {
            Log.w("WhatsAppRepo", "findPhotoForContactName DB query failed: ${e.message}")
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ? OR ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} = ?",
                    arrayOf(cleanName, cleanName),
                    null
                )?.use { c ->
                    if (c.moveToNext()) {
                        val thumb = c.getString(0).orEmpty().trim()
                        if (thumb.isNotBlank()) return thumb
                        val full = c.getString(1).orEmpty().trim()
                        if (full.isNotBlank()) return full
                        val cid = c.getLong(2)
                        if (cid > 0) return "content://com.android.contacts/contacts/$cid/photo"
                    }
                }
            } catch (e: Exception) {
                Log.w("WhatsAppRepo", "findPhotoForContactName ContactsContract query failed: ${e.message}")
            }
        }
        return ""
    }

    fun findContactByPhone(phone: String): Pair<String, String>? {
        val raw = phone.trim()
        if (raw.isBlank()) return null
        val norm = PhoneNumberNormalizer.normalize(raw)

        try {
            val db = dbHelper.readableDatabase
            db.rawQuery(
                "SELECT name, photo_uri FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE (normalized_number = ? OR phone = ?) AND enabled = 1 LIMIT 1",
                arrayOf(norm, raw)
            ).use { c ->
                if (c.moveToNext()) {
                    val n = c.getString(0).orEmpty()
                    val p = c.getString(1).orEmpty()
                    if (n.isNotBlank() && n != raw) return Pair(n, p)
                }
            }
        } catch (e: Exception) {
            Log.w("WhatsAppRepo", "findContactByPhone DB query failed for $norm: ${e.message}")
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val filterUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(raw))
                context.contentResolver.query(
                    filterUri,
                    arrayOf(
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                        ContactsContract.PhoneLookup.PHOTO_URI
                    ),
                    null, null, null
                )?.use { c ->
                    if (c.moveToNext()) {
                        val n = c.getString(0).orEmpty().trim()
                        var photo = c.getString(1).orEmpty().trim()
                        if (photo.isBlank()) photo = c.getString(2).orEmpty().trim()
                        if (n.isNotBlank()) return Pair(n, photo)
                    }
                }
            } catch (e: Exception) {
                Log.w("WhatsAppRepo", "findContactByPhone PhoneLookup query failed: ${e.message}")
            }
        }
        return null
    }

    fun getWhatsAppContacts(
        query: String = "",
        channel: ChannelType = ChannelType.WHATSAPP,
        limit: Int = 10000
    ): List<WhatsAppContactItem> {
        val map = LinkedHashMap<String, WhatsAppContactItem>()
        val queryLower = query.trim().lowercase()
        val isGroupChannel = channel == ChannelType.WHATSAPP_GROUP || channel == ChannelType.WHATSAPP_BUSINESS_GROUP
        val isBusiness = channel == ChannelType.WHATSAPP_BUSINESS || channel == ChannelType.WHATSAPP_BUSINESS_GROUP

        // Helper to check match
        fun matchesQuery(name: String, phone: String, norm: String): Boolean =
            queryLower.isEmpty() || name.lowercase().contains(queryLower) || phone.contains(queryLower) || norm.contains(queryLower)

        // 1. Query TABLE_DYNAMIC_POOL (Main Pool) with channel prioritization
        try {
            val db = dbHelper.readableDatabase
            val poolSql = when {
                isGroupChannel && isBusiness -> """
                    SELECT name, phone, normalized_number, IFNULL(photo_uri, ''), IFNULL(source, '')
                    FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL}
                    WHERE enabled = 1 AND (source IN ('WA_BUSINESS_GROUP', 'WA_GROUP') OR phone LIKE 'group:%' OR normalized_number LIKE 'group:%')
                    ORDER BY CASE
                        WHEN source = 'WA_BUSINESS_GROUP' THEN 0
                        WHEN source = 'WA_GROUP' THEN 1
                        ELSE 2 END, name ASC
                    LIMIT ?
                """.trimIndent()
                isGroupChannel -> """
                    SELECT name, phone, normalized_number, IFNULL(photo_uri, ''), IFNULL(source, '')
                    FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL}
                    WHERE enabled = 1 AND (source IN ('WA_GROUP', 'WA_BUSINESS_GROUP') OR phone LIKE 'group:%' OR normalized_number LIKE 'group:%')
                    ORDER BY CASE
                        WHEN source = 'WA_GROUP' THEN 0
                        WHEN source = 'WA_BUSINESS_GROUP' THEN 1
                        ELSE 2 END, name ASC
                    LIMIT ?
                """.trimIndent()
                isBusiness -> """
                    SELECT name, phone, normalized_number, IFNULL(photo_uri, ''), IFNULL(source, '')
                    FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL}
                    WHERE enabled = 1
                    ORDER BY CASE
                        WHEN source = 'WA_BUSINESS' THEN 0
                        WHEN source = 'WA_BUSINESS_GROUP' THEN 1
                        WHEN source = 'WA_CHAT' THEN 2
                        WHEN source = 'WA_GROUP' THEN 3
                        ELSE 4 END, name ASC
                    LIMIT ?
                """.trimIndent()
                else -> """
                    SELECT name, phone, normalized_number, IFNULL(photo_uri, ''), IFNULL(source, '')
                    FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL}
                    WHERE enabled = 1
                    ORDER BY CASE
                        WHEN source = 'WA_CHAT' THEN 0
                        WHEN source = 'WA_GROUP' THEN 1
                        WHEN source = 'WA_BUSINESS' THEN 2
                        WHEN source = 'WA_BUSINESS_GROUP' THEN 3
                        ELSE 4 END, name ASC
                    LIMIT ?
                """.trimIndent()
            }
            db.rawQuery(poolSql, arrayOf(limit.toString())).use { c ->
                while (c.moveToNext() && map.size < limit) {
                    val name = c.getString(0) ?: ""
                    val phone = c.getString(1) ?: ""
                    val norm = c.getString(2) ?: PhoneNumberNormalizer.normalize(phone)
                    val photo = c.getString(3) ?: ""
                    val source = if (c.columnCount >= 5) c.getString(4).orEmpty() else ""
                    val isGroup = phone.startsWith("group:", ignoreCase = true) || norm.startsWith("group:", ignoreCase = true) || source.contains("group", ignoreCase = true)
                    val isW4bEntry = source.contains("w4b", ignoreCase = true) || source.contains("business", ignoreCase = true)

                    val dedupeKey = if (isGroup) "group:${name.lowercase()}" else norm.ifBlank { phone }
                    if (phone.isNotBlank() && !map.containsKey(dedupeKey)) {
                        if (matchesQuery(name, phone, norm)) {
                            val accType = when {
                                isGroup -> if (isW4bEntry || isBusiness) "com.whatsapp.w4b" else "com.whatsapp"
                                isW4bEntry || isBusiness -> "com.whatsapp.w4b"
                                else -> "com.whatsapp"
                            }
                            map[dedupeKey] = WhatsAppContactItem(
                                name = name.ifBlank { phone },
                                phone = phone,
                                normalizedPhone = norm,
                                accountType = accType,
                                photoUri = photo
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("WhatsAppRepo", "Error fetching dynamic pool WhatsApp contacts: ${e.message}")
        }

        // 2. Query ContactsContract.Data for WhatsApp sync profiles if supported
        if (!isGroupChannel && map.size < limit && ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                val profileMime = if (isBusiness) "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile" else "vnd.android.cursor.item/vnd.com.whatsapp.profile"
                val cursor = resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Data.DISPLAY_NAME,
                        ContactsContract.Data.DATA1,
                        ContactsContract.Data.DATA3,
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.Data.MIMETYPE
                    ),
                    "${ContactsContract.Data.MIMETYPE} = '$profileMime' OR ${ContactsContract.Data.DATA3} LIKE 'Message +%'",
                    null,
                    "${ContactsContract.Data.DISPLAY_NAME} ASC"
                )
                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                    val data3Idx = c.getColumnIndex(ContactsContract.Data.DATA3)
                    val data1Idx = c.getColumnIndex(ContactsContract.Data.DATA1)
                    val cidIdx = c.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                    val mimeIdx = c.getColumnIndex(ContactsContract.Data.MIMETYPE)
                    val phoneRegex = Regex("""\+?[0-9]{10,13}""")

                    while (c.moveToNext() && map.size < limit) {
                        val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty().trim() else ""
                        val data3 = if (data3Idx >= 0) c.getString(data3Idx).orEmpty().trim() else ""
                        val data1 = if (data1Idx >= 0) c.getString(data1Idx).orEmpty().trim() else ""
                        val mime = if (mimeIdx >= 0) c.getString(mimeIdx).orEmpty().trim() else ""
                        val candidatePhone = when {
                            data3.isNotBlank() && phoneRegex.find(data3) != null -> phoneRegex.find(data3)?.value.orEmpty()
                            data1.contains("@") -> "+" + data1.substringBefore("@").filter { it.isDigit() }
                            else -> phoneRegex.find(data1)?.value.orEmpty()
                        }

                        if (candidatePhone.isNotBlank() && PhoneNumberNormalizer.isMobileNumber(candidatePhone)) {
                            val norm = PhoneNumberNormalizer.normalize(candidatePhone)
                            val contactId = if (cidIdx >= 0) c.getLong(cidIdx) else 0L
                            val photoUri = if (contactId > 0) "content://com.android.contacts/contacts/$contactId/photo" else ""

                            val existing = map[norm]
                            if (existing != null) {
                                val updatedPhoto = if (existing.photoUri.isBlank() && photoUri.isNotBlank()) photoUri else existing.photoUri
                                val updatedName = if (existing.name == existing.phone && name.isNotBlank() && name != candidatePhone) name else existing.name
                                if (updatedPhoto != existing.photoUri || updatedName != existing.name) {
                                    map[norm] = existing.copy(name = updatedName, photoUri = updatedPhoto)
                                }
                            } else {
                                if (matchesQuery(name, candidatePhone, norm)) {
                                    val cleanName = if (name.isNotBlank() && name != candidatePhone) name else candidatePhone
                                    map[norm] = WhatsAppContactItem(
                                        name = cleanName,
                                        phone = candidatePhone,
                                        normalizedPhone = norm,
                                        accountType = if (mime.contains("w4b") || isBusiness) "com.whatsapp.w4b" else "com.whatsapp",
                                        photoUri = photoUri
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("WhatsAppRepo", "WhatsApp raw contact data query failed: ${e.message}")
            }
        }

        // 4. Fill with General Contacts (ContactsContract.CommonDataKinds.Phone) if still below limit
        if (!isGroupChannel && map.size < limit && ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                val selection = if (queryLower.isNotBlank()) {
                    "(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?)"
                } else null
                val selectionArgs = if (queryLower.isNotBlank()) {
                    arrayOf("%$queryLower%", "%$queryLower%")
                } else null

                val fullProjection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                )
                val cursor = try {
                    resolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        fullProjection,
                        selection,
                        selectionArgs,
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                    )
                } catch (e: Exception) {
                    Log.w("WhatsAppRepo", "Failed querying phone contacts: ${e.message}")
                    null
                }

                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photoThumbIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                    val photoUriIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                    val contactIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                    while (c.moveToNext() && map.size < limit) {
                        val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty().trim() else ""
                        val phone = if (numIdx >= 0) c.getString(numIdx).orEmpty().trim() else ""
                        var photo = if (photoThumbIdx >= 0) c.getString(photoThumbIdx).orEmpty().trim() else ""
                        if (photo.isBlank() && photoUriIdx >= 0) {
                            photo = c.getString(photoUriIdx).orEmpty().trim()
                        }
                        if (photo.isBlank() && contactIdIdx >= 0) {
                            val cid = c.getLong(contactIdIdx)
                            if (cid > 0) {
                                photo = "content://com.android.contacts/contacts/$cid/photo"
                            }
                        }

                        if (phone.isNotBlank() && PhoneNumberNormalizer.isMobileNumber(phone)) {
                            val norm = PhoneNumberNormalizer.normalize(phone)
                            val existing = map[norm]
                            if (existing != null) {
                                val updatedPhoto = if (existing.photoUri.isBlank() && photo.isNotBlank()) photo else existing.photoUri
                                val updatedName = if (existing.name == existing.phone && name.isNotBlank() && name != phone) name else existing.name
                                if (updatedPhoto != existing.photoUri || updatedName != existing.name) {
                                    map[norm] = existing.copy(name = updatedName, photoUri = updatedPhoto)
                                }
                            } else {
                                if (matchesQuery(name, phone, norm)) {
                                    val cleanName = if (name.isNotBlank() && name != phone) name else phone
                                    map[norm] = WhatsAppContactItem(
                                        name = cleanName,
                                        phone = phone,
                                        normalizedPhone = norm,
                                        accountType = if (isBusiness) "com.whatsapp.w4b" else "com.whatsapp",
                                        photoUri = photo
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("WhatsAppRepo", "General contacts query failed: ${e.message}")
            }
        }

        return map.values.toList()
    }

    /**
     * Clears saved WhatsApp groups for a specific source (WA_GROUP or WA_BUSINESS_GROUP),
     * or all groups if source is omitted.
     */
    fun deleteAllWhatsAppGroups(source: String? = null): Int {
        return try {
            val db = dbHelper.writableDatabase
            if (source.isNullOrBlank()) {
                db.delete(
                    DatabaseHelper.TABLE_DYNAMIC_POOL,
                    "source IN ('WA_GROUP', 'WA_BUSINESS_GROUP') OR phone LIKE 'group:%' OR normalized_number LIKE 'group:%'",
                    null
                )
            } else {
                db.delete(
                    DatabaseHelper.TABLE_DYNAMIC_POOL,
                    "source = ? OR (source = ? AND phone LIKE 'group:%')",
                    arrayOf(source, source)
                )
            }
        } catch (e: Exception) {
            Log.e("WhatsAppRepo", "Failed clearing groups: ${e.message}")
            0
        }
    }

    /**
     * Purges corrupted or misclassified groups from the dynamic pool (e.g. system footers or contacts mislabeled as groups).
     */
    fun purgeCorruptedGroupsFromPool(): Int {
        return try {
            val db = dbHelper.writableDatabase
            val count = db.delete(
                DatabaseHelper.TABLE_DYNAMIC_POOL,
                "(source IN ('WA_GROUP', 'WA_BUSINESS_GROUP') OR phone LIKE 'group:%' OR normalized_number LIKE 'group:%') AND (" +
                    "LOWER(name) IN ('hidden updates', 'find channels to follow', 'explore more', 'create channel', 'inbox filters', 'radio group', 'add status', 'recent updates', 'recent searches', 'clear all', 'ask meta ai or search', 'search group chats', 'search chats', 'manage groups', 'manage group', 'new group', 'new community', 'archived', 'starred messages', 'disappearing messages', 'chats', 'updates', 'communities', 'calls', 'tools', 'all filter', 'groups filter', 'unread filter', 'favorites filter') " +
                    "OR phone IN ('group:Hidden updates', 'group:Find channels to follow', 'group:Explore more', 'group:Create channel', 'group:Inbox filters', 'group:Recent updates', 'group:Add status', 'group:Manage Groups', 'group:Manage groups', 'group:New group', 'group:Archived') " +
                    "OR normalized_number IN ('group:hidden updates', 'group:find channels to follow', 'group:explore more', 'group:create channel', 'group:inbox filters', 'group:recent updates', 'group:add status', 'group:manage groups', 'group:new group', 'group:archived')" +
                ")",
                null
            )
            Log.i("WhatsAppRepo", "Purged $count corrupted group entries from dynamic pool")
            count
        } catch (e: Exception) {
            Log.w("WhatsAppRepo", "purgeCorruptedGroupsFromPool failed: ${e.message}")
            0
        }
    }
}
