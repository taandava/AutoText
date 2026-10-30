

/**
 * Package: com.jmd.autotext.data.repo
 * Module: Data Repositories Layer
 *
 * Manages saved contacts and grouped recipients used across campaign sequences and ad-hoc task creation.
 *
 * Linked With:
 * - Database: [com.jmd.autotext.data.db.DatabaseHelper]
 * - Models: [com.jmd.autotext.data.model.RecipientItem]
 * - UI Screens: `ContactsScreen`
 */
 package com.jmd.autotext.data.repo

import android.content.*
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.jmd.autotext.data.db.DatabaseHelper
import com.jmd.autotext.data.model.RecipientItem

/**
 * Repository providing CRUD access to saved recipient contacts and group definitions.
 *
 * @param context Application context.
 */
class RecipientRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * Retrieves all saved recipient records sorted alphabetically by contact name.
     *
     * @return List of [RecipientItem] contacts.
     */
    fun getAllRecipients(): List<RecipientItem> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<RecipientItem>()
        db.query(DatabaseHelper.TABLE_RECIPIENTS, null, null, null, null, null, "name ASC").use { c ->
            while (c.moveToNext()) {
                val groupColIndex = c.getColumnIndex("group_name")
                val group = if (groupColIndex != -1) c.getString(groupColIndex) ?: "General" else "General"
                list.add(
                    RecipientItem(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        phoneOrEmail = c.getString(c.getColumnIndexOrThrow("phone_or_email")),
                        groupName = group
                    )
                )
            }
        }
        return list
    }

    /**
     * Inserts or replaces a recipient record in the local database.
     *
     * @param recipient The recipient model to persist.
     * @return Row ID of the stored recipient.
     */
    fun insertRecipient(recipient: RecipientItem): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            if (recipient.id > 0) put("id", recipient.id)
            put("name", recipient.name)
            put("phone_or_email", recipient.phoneOrEmail)
            put("group_name", recipient.groupName)
        }
        val rowId = db.insertWithOnConflict(DatabaseHelper.TABLE_RECIPIENTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return if (recipient.id > 0 && rowId >= 0) recipient.id else rowId
    }

    /**
     * Updates an existing recipient record in storage.
     *
     * @param recipient The modified recipient model.
     * @return `true` if update affected 1 or more rows.
     */
    fun updateRecipient(recipient: RecipientItem): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("name", recipient.name)
            put("phone_or_email", recipient.phoneOrEmail)
            put("group_name", recipient.groupName)
        }
        return db.update(DatabaseHelper.TABLE_RECIPIENTS, cv, "id = ?", arrayOf(recipient.id.toString())) > 0
    }

    /**
     * Deletes a recipient record by ID.
     *
     * @param id The recipient identifier.
     * @return `true` if deletion succeeded.
     */
    fun deleteRecipient(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_RECIPIENTS, "id = ?", arrayOf(id.toString())) > 0
    }

    fun bulkInsertRecipients(items: List<RecipientItem>): Int {
        val db = dbHelper.writableDatabase
        var count = 0
        return db.transaction {
            for ((id, name, phoneOrEmail, groupName) in items) {
                val cv = ContentValues().apply {
                    if (id > 0) put("id", id)
                    put("name", name)
                    put("phone_or_email", phoneOrEmail)
                    put("group_name", groupName)
                }
                val rowId = db.insertWithOnConflict(DatabaseHelper.TABLE_RECIPIENTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                if (rowId >= 0) count++
            }
            count
        }
    }

    fun importContactsFromCsv(csvText: String): Int {
        val lines = csvText.lines()
        val items = mutableListOf<RecipientItem>()
        for (line in lines) {
            val parts = line.split(",")
            if (parts.size >= 2) {
                val name = parts[0].trim()
                val phone = parts[1].trim()
                if (phone.isNotBlank()) {
                    items.add(RecipientItem(name = name, phoneOrEmail = phone))
                }
            }
        }
        return bulkInsertRecipients(items)
    }

    fun exportContactsToCsv(): String {
        val list = getAllRecipients()
        return list.joinToString("\n") { "${it.name},${it.phoneOrEmail}" }
    }
}
