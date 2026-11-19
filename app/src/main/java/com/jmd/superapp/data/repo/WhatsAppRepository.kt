/**
 * Package: com.jmd.superapp.data.repo
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [WhatsAppRepository].
 *
 * What it does:
 * Manages harvested WhatsApp contacts, group items, chat title validation heuristics, contact book cross-referencing, and profile photo resolution.
 *
 * Linked With:
 * - Repositories: [android.database.sqlite.SQLiteDatabase], [com.jmd.superapp.data.db.DatabaseHelper]
 * - Models & Entities: [com.jmd.superapp.data.model.ChannelType], [com.jmd.superapp.data.model.WhatsAppContactItem], [com.jmd.superapp.data.model.WhatsAppGroupItem]
 * - Core Security & Utilities: [android.content.pm.PackageManager], [android.util.Log], [androidx.core.content.ContextCompat], [androidx.core.database.sqlite.transaction]
 * - Android Framework & Jetpack: [android.content.ContentValues], [android.net.Uri], [android.provider.ContactsContract]
 */

package com.jmd.superapp.data.repo

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.database.sqlite.transaction
import com.jmd.superapp.core.PhoneNumberNormalizer
import com.jmd.superapp.data.db.DatabaseHelper
import com.jmd.superapp.data.model.ChannelType
import com.jmd.superapp.data.model.WhatsAppContactItem
import com.jmd.superapp.data.model.WhatsAppGroupItem

/**
 * What it is:
 * Repository managing WhatsApp contact items and address book lookups.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [WhatsAppRepository].
 *
 * Linked with:
 * - Architecture: [WhatsAppRepository], [WhatsAppRepository]
 */
class WhatsAppRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * What it is:
     * Verifies that a user-selected or accessibility-scraped string corresponds to a valid conversation title.
     *
     * What it does:
     * Filters out standard UI controls, action prompts, status tabs, and header labels.
     *
     * Linked with:
     * - Architecture: [WhatsAppRepository], [isValidPickedChatName]
     * @param name Scraped or selected chat title.
     * @return True if valid conversation title, false if blocked UI keyword.
     */
    fun isValidPickedChatName(name: String): Boolean {
        val trimmed = name.trim().removePrefix("~").trim()
        if (trimmed.length !in 1..150) return false
        if (trimmed.contains("\n") || trimmed.contains("\r")) return false

        val lower = trimmed.lowercase()
        if (lower.contains("automatically update") || lower.contains("all group chats") ||
            lower.contains("this list") || lower.contains("group chats") ||
            lower.contains("manage group") || lower.contains("manage list") ||
            lower.contains("no chats") || lower.contains("chats will appear") ||
            lower.contains("start a chat") || lower.contains("tap and hold") ||
            lower.contains("swipe down") || lower.contains("add new list")
        ) {
            return false
        }
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
     * What it is:
     * Resolves the cellular phone number for a contact name by checking the local dynamic contact pool, falling back to the Android device ContactsContract provider.
     *
     * What it does:
     * Executes [findPhoneForContactName] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsAppRepository], [findPhoneForContactName]
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

            // 3. Prefix match in dynamic contact pool (e.g., "John" matches "John Doe")
            if (alphaName.length >= 3) {
                db.rawQuery(
                    "SELECT phone FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE LOWER(name) LIKE ? AND phone != '' AND enabled = 1 LIMIT 1",
                    arrayOf("${alphaName.lowercase()}%")
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
     * What it is:
     * Checks whether a contact has a verified numeric cellular phone number (excluding group/chat virtual identifiers).
     *
     * What it does:
     * Performs strict exact name matching only to avoid false positives on groups sharing substrings with contacts.
     *
     * Linked with:
     * - Architecture: [WhatsAppRepository], [hasCellularPhoneContact]
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
        } catch (e: Exception) {
            Log.d("WhatsAppRepository", "Recipient-name lookup skipped: ${e.message}")
        }

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
            } catch (e: Exception) {
                Log.d("WhatsAppRepository", "Contacts name lookup skipped: ${e.message}")
            }
        }

        return false
    }
    /**
     * What it is:
     * Execution routine for [findPhotoForContactName].
     *
     * What it does:
     * Coordinates findPhotoForContactName operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppRepository]
     */

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
    /**
     * What it is:
     * Execution routine for [findContactByPhone].
     *
     * What it does:
     * Coordinates findContactByPhone operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppRepository]
     */

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
    /**
     * What it is:
     * Execution routine for [getWhatsAppContacts].
     *
     * What it does:
     * Coordinates getWhatsAppContacts operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsAppRepository]
     */

    fun getWhatsAppContacts(
        query: String = "",
        channel: ChannelType = ChannelType.WHATSAPP,
        limit: Int = 10000
    ): List<WhatsAppContactItem> {
        val map = LinkedHashMap<String, WhatsAppContactItem>()
        val queryLower = query.trim().lowercase()
        val isGroupChannel = channel == ChannelType.WHATSAPP_GROUP || channel == ChannelType.WHATSAPP_BUSINESS_GROUP
        val isBusiness = channel == ChannelType.WHATSAPP_BUSINESS || channel == ChannelType.WHATSAPP_BUSINESS_GROUP

        // Super to check match
        /**
         * What it is:
         * Operational routine [matchesQuery].
         *
         * What it does:
         * Executes matchesQuery actions according to system invariants.
         *
         * Linked with:
         * - Component: [WhatsAppRepository]
         */
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
                    WHERE enabled = 1 AND phone NOT LIKE 'group:%' AND normalized_number NOT LIKE 'group:%'
                        AND source NOT IN ('WA_GROUP', 'WA_BUSINESS_GROUP')
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
                    WHERE enabled = 1 AND phone NOT LIKE 'group:%' AND normalized_number NOT LIKE 'group:%'
                        AND source NOT IN ('WA_GROUP', 'WA_BUSINESS_GROUP')
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

                    // Strict validation for group channel: phonebook contacts and cellular numbers are never groups
                    if (isGroupChannel) {
                        val digitsName = name.filter { it.isDigit() }
                        val lettersName = name.filter { it.isLetter() }
                        if (digitsName.length >= 7 && lettersName.isEmpty()) continue
                        val rawPhone = phone.removePrefix("group:").trim()
                        val digitsPhone = rawPhone.filter { it.isDigit() }
                        val lettersPhone = rawPhone.filter { it.isLetter() }
                        if (digitsPhone.length >= 7 && lettersPhone.isEmpty()) continue
                        if (hasCellularPhoneContact(name) || findPhoneForContactName(name) != null) continue
                        if (name.isBlank() || name.trim().length < 2) continue
                    }

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
                    "${ContactsContract.Data.MIMETYPE} LIKE '%whatsapp%' OR " +
                    "${ContactsContract.Data.MIMETYPE} LIKE '%w4b%' OR " +
                    "${ContactsContract.Data.DATA1} LIKE '%@s.whatsapp.net%' OR " +
                    "${ContactsContract.Data.DATA3} LIKE '%+%'",
                    null,
                    "${ContactsContract.Data.DISPLAY_NAME} ASC"
                )
                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                    val data3Idx = c.getColumnIndex(ContactsContract.Data.DATA3)
                    val data1Idx = c.getColumnIndex(ContactsContract.Data.DATA1)
                    val cidIdx = c.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                    val mimeIdx = c.getColumnIndex(ContactsContract.Data.MIMETYPE)
                    val phoneRegex = Regex("""\+?[0-9]{7,15}""")

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

        // Final guard: one row per real phone number regardless of how each source normalized it.
        // Pool rows carry their stored normalized_number, address-book rows carry a freshly computed
        // one, so the same person could otherwise appear twice in the picker.
        val deduped = LinkedHashMap<String, WhatsAppContactItem>()
        for (item in map.values) {
            val isGroupItem = item.phone.startsWith("group:", ignoreCase = true) ||
                item.normalizedPhone.startsWith("group:", ignoreCase = true)
            val digits = item.phone.filter { it.isDigit() }
            val key = when {
                isGroupItem -> "group:" + item.name.lowercase().trim()
                digits.length >= 10 -> digits.takeLast(10)
                else -> item.normalizedPhone.ifBlank { item.phone }
            }
            val existing = deduped[key]
            if (existing == null) {
                deduped[key] = item
            } else {
                val betterName = if (existing.name == existing.phone && item.name != item.phone) item.name else existing.name
                val betterPhoto = existing.photoUri.ifBlank { item.photoUri }
                deduped[key] = existing.copy(name = betterName, photoUri = betterPhoto)
            }
        }
        return deduped.values.toList()
    }

    /**
     * What it is:
     * Clears saved WhatsApp groups for a specific source (WA_GROUP or WA_BUSINESS_GROUP), or all groups if source is omitted.
     *
     * What it does:
     * Executes [deleteAllWhatsAppGroups] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsAppRepository], [deleteAllWhatsAppGroups]
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
                    "source = ? OR (source = ? AND (phone LIKE 'group:%' OR normalized_number LIKE 'group:%'))",
                    arrayOf(source, source)
                )
            }
        } catch (e: Exception) {
            Log.e("WhatsAppRepo", "Failed clearing groups: ${e.message}")
            0
        }
    }

    /**
     * What it is:
     * Convenience method to purge all group entries from the dynamic pool.
     *
     * What it does:
     * Executes [purgeAllGroupsFromPool] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsAppRepository], [purgeAllGroupsFromPool]
     */
    fun purgeAllGroupsFromPool(): Int = deleteAllWhatsAppGroups(null)

    /**
     * What it is:
     * Purges corrupted or misclassified groups from the dynamic pool (e.g.
     * system footers or contacts mislabeled as groups).
     *
     * What it does:
     * Executes [purgeCorruptedGroupsFromPool] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsAppRepository], [purgeCorruptedGroupsFromPool]
     */
    fun purgeCorruptedGroupsFromPool(): Int {
        return try {
            val db = dbHelper.writableDatabase
            var count = 0

            // 1. Delete UI system strings and empty names
            count += db.delete(
                DatabaseHelper.TABLE_DYNAMIC_POOL,
                "LOWER(TRIM(name)) LIKE '%automatically update%' OR " +
                "LOWER(TRIM(name)) LIKE '%all group chats%' OR " +
                "LOWER(TRIM(name)) LIKE '%this list%' OR " +
                "LOWER(TRIM(name)) LIKE '%manage group%' OR " +
                "LOWER(TRIM(name)) IN ('group chats', 'all filter', 'groups filter', 'unread filter') OR " +
                "((source IN ('WA_GROUP', 'WA_BUSINESS_GROUP') OR phone LIKE 'group:%' OR normalized_number LIKE 'group:%') AND (" +
                    "LENGTH(TRIM(name)) < 2 OR " +
                    "LOWER(TRIM(name)) IN ('archived', 'chats', 'updates', 'calls', 'tools', 'communities', 'status', 'meta ai', 'ask meta ai', 'broadcast', 'new group', 'new broadcast', 'linked devices', 'starred', 'starred messages', 'disappearing messages', 'settings', 'help', 'search', 'search…', 'search chats', 'search group chats', 'inbox filters', 'radio group', 'filter', 'all filter', 'groups filter', 'unread filter', 'favorites filter', 'explore more', 'find channels', 'find channels to follow', 'hidden updates', 'recent updates', 'add status', 'recent searches', 'clear all', 'manage groups', 'manage group', 'you', 'group chats') OR " +
                    "phone IN ('group:Hidden updates', 'group:Find channels to follow', 'group:Explore more', 'group:Create channel', 'group:Inbox filters', 'group:Recent updates', 'group:Add status', 'group:Manage Groups', 'group:Manage groups', 'group:New group', 'group:Archived', 'group:Chats', 'group:Updates', 'group:Calls', 'group:Tools', 'group:Communities') OR " +
                    "normalized_number IN ('group:hidden updates', 'group:find channels to follow', 'group:explore more', 'group:create channel', 'group:inbox filters', 'group:recent updates', 'group:add status', 'group:manage groups', 'group:new group', 'group:archived', 'group:chats', 'group:updates', 'group:calls', 'group:tools', 'group:communities')" +
                "))",
                null
            )

            // 2. Any row with source NOT IN ('WA_GROUP', 'WA_BUSINESS_GROUP') should NEVER have phone starting with 'group:'
            val nonGroupWithGroupPrefix = mutableListOf<Long>()
            db.rawQuery(
                "SELECT id FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE source NOT IN ('WA_GROUP', 'WA_BUSINESS_GROUP') AND (phone LIKE 'group:%' OR normalized_number LIKE 'group:%')",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    nonGroupWithGroupPrefix.add(c.getLong(0))
                }
            }
            for (id in nonGroupWithGroupPrefix) {
                val deleted = db.delete(DatabaseHelper.TABLE_DYNAMIC_POOL, "id = ?", arrayOf(id.toString()))
                if (deleted > 0) count++
            }

            // 3. For rows that are groups: find all phone numbers, phonebook matches, or direct contacts and DELETE THEM from groups!
            val groupsToDelete = mutableListOf<Long>()
            db.rawQuery(
                "SELECT id, name, phone, normalized_number FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE source IN ('WA_GROUP', 'WA_BUSINESS_GROUP') OR phone LIKE 'group:%' OR normalized_number LIKE 'group:%'",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1).orEmpty().trim()
                    val phone = c.getString(2).orEmpty().trim()
                    val rawPhone = phone.removePrefix("group:").trim()

                    val digitsName = name.filter { it.isDigit() }
                    val lettersName = name.filter { it.isLetter() }
                    val digitsPhone = rawPhone.filter { it.isDigit() }
                    val lettersPhone = rawPhone.filter { it.isLetter() }

                    val isNamePureNumber = digitsName.length >= 7 && lettersName.isEmpty()
                    val isPhonePureNumber = digitsPhone.length >= 7 && lettersPhone.isEmpty()
                    val phoneFromBook = if (name.isNotBlank()) findPhoneForContactName(name) else null
                    val hasCellular = hasCellularPhoneContact(name)

                    // If it is a phone number or in the phonebook, it is an individual contact, NOT a group!
                    if (isNamePureNumber || isPhonePureNumber || !phoneFromBook.isNullOrBlank() || hasCellular) {
                        groupsToDelete.add(id)
                    }
                }
            }

            for (id in groupsToDelete) {
                val deleted = db.delete(DatabaseHelper.TABLE_DYNAMIC_POOL, "id = ?", arrayOf(id.toString()))
                if (deleted > 0) count++
            }
            Log.i("WhatsAppRepo", "Purged/repaired $count corrupted group entries from dynamic pool")
            count
        } catch (e: Exception) {
            Log.w("WhatsAppRepo", "purgeCorruptedGroupsFromPool failed: ${e.message}")
            0
        }
    }
}

