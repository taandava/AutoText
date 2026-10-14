package com.jmd.autotext.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.jmd.autotext.data.DatabaseHelper
import com.jmd.autotext.model.WhatsAppContactItem
import com.jmd.autotext.model.WhatsAppGroupItem
import com.jmd.autotext.util.PhoneNumberNormalizer

class WhatsAppRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    private val invalidGroupKeywords = listOf(
        "status", "add status", "disappears after", "tap to add", "photo", "video",
        "voice message", "joined using a group link", "left", "added", "security code changed",
        "waiting for this message", "missed call", "http://", "https://", "www.",
        "ask meta ai", "meta ai", "cheque", "bhai", "tomorrow", "yesterday", "today",
        "typing...", "online", "last seen", "delete for everyone", "deleted this message",
        "message was deleted", "reacted to", "call ended", "incoming call", "outgoing call"
    )

    fun isValidGroupName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length !in 3..60) return false
        if (trimmed.contains("\n") || trimmed.contains("\r")) return false

        // Filter out truncated message previews or drafts ending with ellipsis
        if (trimmed.endsWith("…") || trimmed.endsWith("...") || trimmed.endsWith("..")) return false

        val lower = trimmed.lowercase()

        // 1. Blacklist typical WhatsApp system UI messages, status rows, and meta AI prompts
        if (invalidGroupKeywords.any { lower.contains(it) }) return false

        // 2. Chat draft indicators, message sender prefix (e.g. "~ Murari lal:", "~ Ravi Gupta:")
        if (trimmed.startsWith("~") || trimmed.startsWith("~\u202f")) return false

        // 3. Raw phone numbers or message timestamps/counts (e.g. "+91 91366 85544", "10 photos, 3 videos")
        val stripped = trimmed.replace(Regex("""[\s\+\-\(\)]"""), "")
        if (stripped.length >= 7 && stripped.all { it.isDigit() }) return false

        // 4. Exclude full conversational sentences (more than 7 words or conversational punctuation like '!')
        val words = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size > 8) return false

        // 5. Must contain at least one letter or alphanumeric character
        if (!trimmed.any { it.isLetter() }) return false

        return true
    }

    fun cleanupInvalidGroupNames(): Int {
        val db = dbHelper.writableDatabase
        var deleted = 0
        try {
            val toDelete = mutableListOf<String>()
            db.query(DatabaseHelper.TABLE_WHATSAPP_GROUPS, arrayOf("name"), null, null, null, null, null).use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: ""
                    if (!isValidGroupName(name)) {
                        toDelete.add(name)
                    }
                }
            }
            for (invalidName in toDelete) {
                deleted += db.delete(DatabaseHelper.TABLE_WHATSAPP_GROUPS, "name = ?", arrayOf(invalidName))
            }
        } catch (_: Exception) {}
        return deleted
    }

    fun getWhatsAppGroups(): List<String> {
        return getWhatsAppGroupItems().map { it.name }
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
        } catch (_: Exception) {}
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
        if (rows > 0) return true
        return db.insertWithOnConflict(
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

    fun bulkSaveWhatsAppGroups(groups: List<String>): Int {
        return bulkSaveWhatsAppGroupItems(groups.map { WhatsAppGroupItem(name = it) })
    }

    fun bulkSaveWhatsAppGroupItems(groups: List<WhatsAppGroupItem>): Int {
        if (groups.isEmpty()) return 0
        val db = dbHelper.writableDatabase
        var inserted = 0
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
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
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    fun getWhatsAppContacts(query: String = "", limit: Int = 3000): List<WhatsAppContactItem> {
        val results = mutableListOf<WhatsAppContactItem>()
        val seen = mutableSetOf<String>()
        val queryLower = query.trim().lowercase()

        // 1. Query device phonebook contacts with photo thumbnails (Fastest & Most reliable source)
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val resolver = context.contentResolver
                val selection = if (queryLower.isNotBlank()) {
                    "(${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?)"
                } else null
                val selectionArgs = if (queryLower.isNotBlank()) {
                    arrayOf("%$queryLower%", "%$queryLower%")
                } else null

                val cursor = resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                    ),
                    selection,
                    selectionArgs,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )

                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photoThumbIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                    val photoUriIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                    val contactIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                    while (c.moveToNext() && results.size < limit) {
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
                            if (seen.add(norm)) {
                                val cleanName = if (name.isNotBlank() && name != phone) name else phone
                                results.add(
                                    WhatsAppContactItem(
                                        name = cleanName,
                                        phone = phone,
                                        normalizedPhone = norm,
                                        accountType = "com.whatsapp",
                                        photoUri = photo
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Query ContactsContract.Data specifically for WhatsApp & WhatsApp Business profiles
            if (results.size < limit) {
                try {
                    val resolver = context.contentResolver
                    val cursor = resolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        arrayOf(
                            ContactsContract.Data.DISPLAY_NAME,
                            ContactsContract.Data.DATA1,
                            ContactsContract.Data.DATA3,
                            ContactsContract.Data.CONTACT_ID,
                            ContactsContract.Data.MIMETYPE
                        ),
                        "${ContactsContract.Data.MIMETYPE} = 'vnd.android.cursor.item/vnd.com.whatsapp.profile' OR " +
                        "${ContactsContract.Data.MIMETYPE} = 'vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile' OR " +
                        "${ContactsContract.Data.DATA3} LIKE 'Message +%'",
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

                        while (c.moveToNext() && results.size < limit) {
                            val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty().trim() else ""
                            val data3 = if (data3Idx >= 0) c.getString(data3Idx).orEmpty().trim() else ""
                            val data1 = if (data1Idx >= 0) c.getString(data1Idx).orEmpty().trim() else ""
                            val mime = if (mimeIdx >= 0) c.getString(mimeIdx).orEmpty().trim() else ""

                            // Extract valid phone number from data3 or data1
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

                                val existingIdx = results.indexOfFirst { it.normalizedPhone == norm || PhoneNumberNormalizer.normalize(it.phone) == norm }
                                if (existingIdx >= 0) {
                                    val current = results[existingIdx]
                                    if (current.photoUri.isBlank() && photoUri.isNotBlank()) {
                                        results[existingIdx] = current.copy(photoUri = photoUri)
                                    }
                                    if (current.name == current.phone && name.isNotBlank() && name != candidatePhone) {
                                        results[existingIdx] = results[existingIdx].copy(name = name)
                                    }
                                } else if (seen.add(norm)) {
                                    if (queryLower.isEmpty() || name.lowercase().contains(queryLower) || candidatePhone.contains(queryLower)) {
                                        val cleanName = if (name.isNotBlank() && name != candidatePhone) name else candidatePhone
                                        results.add(
                                            WhatsAppContactItem(
                                                name = cleanName,
                                                phone = candidatePhone,
                                                normalizedPhone = norm,
                                                accountType = if (mime.contains("w4b")) "com.whatsapp.w4b" else "com.whatsapp",
                                                photoUri = photoUri
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Fallback: augment from local SQLite Dynamic Pool if device permissions restricted or pool has cached contacts
        if (results.size < limit) {
            try {
                val db = dbHelper.readableDatabase
                val sql = if (queryLower.isNotBlank()) {
                    "SELECT name, phone, normalized_number, IFNULL(photo_uri, '') FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE (name LIKE ? OR phone LIKE ? OR normalized_number LIKE ?) AND enabled = 1 ORDER BY name ASC LIMIT ?"
                } else {
                    "SELECT name, phone, normalized_number, IFNULL(photo_uri, '') FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE enabled = 1 ORDER BY name ASC LIMIT ?"
                }
                val args = if (queryLower.isNotBlank()) {
                    arrayOf("%$queryLower%", "%$queryLower%", "%$queryLower%", limit.toString())
                } else {
                    arrayOf(limit.toString())
                }
                db.rawQuery(sql, args).use { c ->
                    while (c.moveToNext() && results.size < limit) {
                        val name = c.getString(0) ?: ""
                        val phone = c.getString(1) ?: ""
                        val norm = c.getString(2) ?: PhoneNumberNormalizer.normalize(phone)
                        val photo = c.getString(3) ?: ""
                        if (phone.isNotBlank() && seen.add(norm)) {
                            results.add(
                                WhatsAppContactItem(
                                    name = name.ifBlank { phone },
                                    phone = phone,
                                    normalizedPhone = norm,
                                    accountType = "com.whatsapp",
                                    photoUri = photo
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Asynchronously persist discovered contacts to database
        if (results.isNotEmpty()) {
            try {
                saveWhatsAppContactsToDatabase(results.take(500))
            } catch (_: Exception) {}
        }

        return results
    }

    fun saveWhatsAppContactsToDatabase(contacts: List<WhatsAppContactItem>): Int {
        if (contacts.isEmpty()) return 0
        val db = dbHelper.writableDatabase
        var inserted = 0
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for ((name, phone, normalizedPhone, _, photoUri) in contacts) {
                val norm = normalizedPhone.ifBlank { PhoneNumberNormalizer.normalize(phone) }
                val cv = ContentValues().apply {
                    put("name", name)
                    put("phone", phone)
                    put("normalized_number", norm)
                    put("source", "WHATSAPP")
                    put("enabled", 1)
                    put("created_at", now)
                    if (photoUri.isNotBlank()) {
                        put("photo_uri", photoUri)
                    }
                }
                val updated = if (photoUri.isNotBlank()) {
                    db.update(DatabaseHelper.TABLE_DYNAMIC_POOL, cv, "normalized_number = ?", arrayOf(norm))
                } else 0

                if (updated > 0) {
                    inserted++
                } else {
                    val rowId = db.insertWithOnConflict(
                        DatabaseHelper.TABLE_DYNAMIC_POOL,
                        null,
                        cv,
                        SQLiteDatabase.CONFLICT_IGNORE
                    )
                    if (rowId >= 0) inserted++
                }
            }
            db.setTransactionSuccessful()
        } catch (_: Exception) {
        } finally {
            db.endTransaction()
        }
        return inserted
    }
}
