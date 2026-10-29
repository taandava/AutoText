

/**
 * Package: com.jmd.autotext.data.repo
 * Module: Data Access Layer
 *
 * Manages harvested WhatsApp contacts, group items, chat title validation heuristics,
 * contact book cross-referencing, and profile photo resolution.
 *
 * Linked With:
 * - Database: [com.jmd.autotext.data.db.DatabaseHelper]
 * - Models: [com.jmd.autotext.data.model.WhatsAppContactItem], [com.jmd.autotext.data.model.WhatsAppGroupItem]
 * - Normalizer: [com.jmd.autotext.core.PhoneNumberNormalizer]
 * - Scanner: [com.jmd.autotext.whatsapp.WhatsAppChatScanner]
 * - UI Screens: `WhatsAppSetupScreen`, `ComposeTaskScreen`
 */
package com.jmd.autotext.data.repo

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.jmd.autotext.core.PhoneNumberNormalizer
import com.jmd.autotext.data.db.DatabaseHelper
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.WhatsAppContactItem
import com.jmd.autotext.data.model.WhatsAppGroupItem

/**
 * Repository managing WhatsApp contact items, group discovery lists, and address book lookups.
 */
class WhatsAppRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    private val invalidGroupKeywords = listOf(
        "tap to add status", "my status", "disappears after", "tap to add",
        "joined using a group link", "security code changed",
        "waiting for this message", "missed call", "http://", "https://", "www.",
        "ask meta ai", "typing...", "delete for everyone", "deleted this message",
        "message was deleted", "reacted to", "call ended", "incoming call", "outgoing call",
    )

    /**
     * Validates candidate string to ensure it represents a genuine WhatsApp group title.
     * Rejects phone numbers, system notifications, meta AI triggers, and truncated strings.
     *
     * @param name Candidate group name string.
     * @return True if name satisfies group title criteria, false otherwise.
     */
    fun isValidGroupName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length !in 3..60) return false
        if (trimmed.contains("\n") || trimmed.contains("\r")) return false

        if (trimmed.endsWith("…") || trimmed.endsWith("...") || trimmed.endsWith("..")) return false

        val lower = trimmed.lowercase()

        if (invalidGroupKeywords.any { lower.contains(it) }) return false

        if (trimmed.startsWith("~") || trimmed.startsWith("~\u202f")) return false

        val stripped = trimmed.replace(Regex("""[\s+\-()]"""), "")
        if (stripped.length >= 7 && stripped.all { it.isDigit() }) return false

        val words = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return words.size <= 8 && trimmed.any { it.isLetter() }
    }

    /**
     * Verifies that a user-selected or accessibility-scraped string corresponds to a valid conversation title.
     * Filters out standard UI controls, action prompts, status tabs, and header labels.
     *
     * @param name Scraped or selected chat title.
     * @return True if valid conversation title, false if blocked UI keyword.
     */
    fun isValidPickedChatName(name: String): Boolean {
        val trimmed = name.trim().removePrefix("~").trim()
        if (trimmed.length !in 1..80) return false
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
            "new group", "new community", "new broadcast"
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

        try {
            val db = dbHelper.readableDatabase
            db.rawQuery(
                "SELECT phone FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) AND phone != '' AND enabled = 1 LIMIT 1",
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
                        val p = c.getString(0)
                        if (!p.isNullOrBlank()) return p
                    }
                }
            } catch (e: Exception) {
                Log.w("WhatsAppRepo", "findPhoneForContactName ContactsContract query failed: ${e.message}")
            }
        }
        return null
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

    fun getWhatsAppGroupItems(): List<WhatsAppGroupItem> {
        val list = mutableListOf<WhatsAppGroupItem>()
        try {
            val db = dbHelper.readableDatabase
            db.query(
                DatabaseHelper.TABLE_WHATSAPP_GROUPS,
                arrayOf("id", "name", "group_jid", "member_count", "created_at"),
                null, null, null, null,
                "name ASC"
            ).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: ""
                    val jid = c.getString(2) ?: ""
                    val count = c.getString(3) ?: ""
                    val created = c.getLong(4)
                    if (isValidGroupName(name)) {
                        list.add(
                            WhatsAppGroupItem(
                                id = id,
                                name = name,
                                groupJid = jid,
                                memberCountStr = count,
                                createdAt = created
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("WhatsAppRepo", "Error fetching WhatsApp groups from DB: ${e.message}")
        }
        return list
    }

    fun saveWhatsAppGroup(
        groupName: String,
        groupJid: String = "",
        memberCount: String = ""
    ): Boolean {
        if (!isValidGroupName(groupName)) return false
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("name", groupName.trim())
            if (groupJid.isNotBlank()) put("group_jid", groupJid.trim())
            if (memberCount.isNotBlank()) put("member_count", memberCount.trim())
            put("created_at", System.currentTimeMillis())
        }
        val rows = db.update(
            DatabaseHelper.TABLE_WHATSAPP_GROUPS,
            cv,
            "name = ?",
            arrayOf(groupName.trim())
        )
        return rows > 0 || db.insertWithOnConflict(
            DatabaseHelper.TABLE_WHATSAPP_GROUPS,
            null,
            cv,
            SQLiteDatabase.CONFLICT_IGNORE
        ) >= 0
    }

    fun deleteWhatsAppGroup(groupName: String): Boolean {
        val db = dbHelper.writableDatabase
        return db.delete(
            DatabaseHelper.TABLE_WHATSAPP_GROUPS,
            "name = ?",
            arrayOf(groupName.trim())
        ) > 0
    }

    fun isSavedGroup(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return false
        }
        return try {
            val db = dbHelper.readableDatabase
            db.rawQuery(
                "SELECT 1 FROM ${DatabaseHelper.TABLE_WHATSAPP_GROUPS} WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) LIMIT 1",
                arrayOf(trimmed)
            ).use { c -> c.moveToFirst() }
        } catch (_: Exception) {
            false
        }
    }

    fun cleanupMistakenGroups() {
        try {
            val groups = getWhatsAppGroupItems()
            for ((_, name, groupJid, memberCountStr) in groups) {
                // If saved with empty memberCount and empty groupJid, verify if it's actually an individual contact
                if (memberCountStr.isBlank() && groupJid.isBlank()) {
                    val phone = findPhoneForContactName(name)
                    if (!phone.isNullOrBlank()) {
                        deleteWhatsAppGroup(name)
                        Log.i("WhatsAppRepo", "Cleaned up mistakenly saved contact from groups table: '$name'")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("WhatsAppRepo", "Error cleaning up mistaken groups: ${e.message}")
        }
    }

    fun bulkSaveWhatsAppGroupItems(groups: List<WhatsAppGroupItem>): Int {
        if (groups.isEmpty()) return 0
        val db = dbHelper.writableDatabase
        var inserted = 0
        val now = System.currentTimeMillis()
        return db.transaction {
            for ((_, groupName, groupJid, memberCountStr, createdAt) in groups) {
                val trimmed = groupName.trim()
                if (isValidGroupName(trimmed)) {
                    val cv = ContentValues().apply {
                        put("name", trimmed)
                        if (groupJid.isNotBlank()) put("group_jid", groupJid.trim())
                        if (memberCountStr.isNotBlank()) put("member_count", memberCountStr.trim())
                        put("created_at", if (createdAt > 0) createdAt else now)
                    }
                    val updated = db.update(
                        DatabaseHelper.TABLE_WHATSAPP_GROUPS,
                        cv,
                        "name = ?",
                        arrayOf(trimmed)
                    )
                    if (updated > 0) {
                        inserted++
                    } else {
                        val rowId = db.insertWithOnConflict(
                            DatabaseHelper.TABLE_WHATSAPP_GROUPS,
                            null,
                            cv,
                            SQLiteDatabase.CONFLICT_IGNORE
                        )
                        if (rowId >= 0) inserted++
                    }
                }
            }
            inserted
        }
    }

    fun getWhatsAppContacts(
        query: String = "",
        channel: ChannelType = ChannelType.WHATSAPP,
        limit: Int = 10000
    ): List<WhatsAppContactItem> {
        val map = LinkedHashMap<String, WhatsAppContactItem>()
        val queryLower = query.trim().lowercase()
        val isBusiness = channel == ChannelType.WHATSAPP_BUSINESS

        // Helper to check match
        fun matchesQuery(name: String, phone: String, norm: String): Boolean =
            queryLower.isEmpty() || name.lowercase().contains(queryLower) || phone.contains(queryLower) || norm.contains(queryLower)

        // 1. Query TABLE_DYNAMIC_POOL (Main Pool) with channel prioritization
        try {
            val db = dbHelper.readableDatabase
            val poolSql = if (isBusiness) {
                """
                SELECT name, phone, normalized_number, IFNULL(photo_uri, ''), IFNULL(source, '')
                FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL}
                WHERE enabled = 1
                ORDER BY CASE
                    WHEN source = 'WA_BUSINESS' THEN 0
                    WHEN source = 'WA_CHAT' THEN 1
                    WHEN source = 'WA_GROUP' THEN 2
                    ELSE 3 END, name ASC
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT name, phone, normalized_number, IFNULL(photo_uri, ''), IFNULL(source, '')
                FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL}
                WHERE enabled = 1
                ORDER BY CASE
                    WHEN source = 'WA_CHAT' THEN 0
                    WHEN source = 'WA_GROUP' THEN 1
                    WHEN source = 'WA_BUSINESS' THEN 2
                    ELSE 3 END, name ASC
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
                    val isGroup = phone.startsWith("group:") || norm.startsWith("group:")
                    val isW4bEntry = source.contains("w4b", ignoreCase = true) || source.contains("business", ignoreCase = true)

                    val dedupeKey = if (isGroup) "group:${name.lowercase()}" else norm.ifBlank { phone }
                    if (phone.isNotBlank() && !map.containsKey(dedupeKey)) {
                        if (matchesQuery(name, phone, norm)) {
                            val accType = when {
                                isGroup -> if (isBusiness) "W4B Group" else "WhatsApp Group"
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

        // 2. Query TABLE_WHATSAPP_GROUPS to ensure all harvested groups are available
        if (map.size < limit) {
            try {
                val db = dbHelper.readableDatabase
                val gSql = if (queryLower.isNotBlank()) {
                    "SELECT name, member_count FROM ${DatabaseHelper.TABLE_WHATSAPP_GROUPS} WHERE name LIKE ? ORDER BY name ASC LIMIT ?"
                } else {
                    "SELECT name, member_count FROM ${DatabaseHelper.TABLE_WHATSAPP_GROUPS} ORDER BY name ASC LIMIT ?"
                }
                val gArgs = if (queryLower.isNotBlank()) arrayOf("%$queryLower%", limit.toString()) else arrayOf(limit.toString())
                db.rawQuery(gSql, gArgs).use { c ->
                    val nIdx = c.getColumnIndex("name")
                    val mIdx = c.getColumnIndex("member_count")
                    while (c.moveToNext() && map.size < limit) {
                        val gName = if (nIdx >= 0) c.getString(nIdx).orEmpty().trim() else ""
                        val mCount = if (mIdx >= 0) c.getString(mIdx).orEmpty().trim() else ""
                        val dedupeKey = "group:${gName.lowercase()}"
                        if (gName.isNotBlank() && !map.containsKey(dedupeKey)) {
                            // If this name actually belongs to an individual contact with a phone number, clean it up from groups table
                            val realPhone = findPhoneForContactName(gName)
                            if (realPhone != null) {
                                try {
                                    db.delete(DatabaseHelper.TABLE_WHATSAPP_GROUPS, "LOWER(TRIM(name)) = LOWER(TRIM(?))", arrayOf(gName))
                                } catch (_: Exception) {}
                            } else {
                                map[dedupeKey] = WhatsAppContactItem(
                                    name = gName,
                                    phone = "group:$gName",
                                    normalizedPhone = "group:$gName",
                                    accountType = if (mCount.isNotBlank()) "Group ($mCount)" else if (isBusiness) "W4B Group" else "WhatsApp Group",
                                    photoUri = ""
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("WhatsAppRepo", "Error querying TABLE_WHATSAPP_GROUPS: ${e.message}")
            }
        }

        // 3. Query ContactsContract.Data for WhatsApp sync profiles if supported
        if (map.size < limit && ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
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
                            data3.isNotBlank() && phoneRegex.find(data3) != null -> phoneRegex.find(data3)!!.value
                            data1.contains("@") -> "+" + data1.substringBefore("@").filter { it.isDigit() }
                            phoneRegex.find(data1) != null -> phoneRegex.find(data1)!!.value
                            else -> ""
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
        if (map.size < limit && ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
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
                } catch (_: Exception) { null }

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


}
