/**
 * Package: com.jmd.helper.data.repo
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [RecipientRepository].
 *
 * What it does:
 * Manages saved contacts and grouped recipients used across campaign sequences and ad-hoc task creation.
 *
 * Linked With:
 * - Repositories: [android.database.sqlite.SQLiteDatabase], [com.jmd.helper.data.db.DatabaseHelper]
 * - Models & Entities: [com.jmd.helper.data.model.RecipientItem]
 * - Core Security & Utilities: [androidx.core.database.sqlite.transaction]
 */

package com.jmd.helper.data.repo

import android.content.*
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.jmd.helper.data.db.DatabaseHelper
import com.jmd.helper.data.model.RecipientItem

/**
 * What it is:
 * Repository providing CRUD access to saved recipient contacts and group definitions.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [RecipientRepository].
 *
 * Linked with:
 * - Architecture: [RecipientRepository], [RecipientRepository]
 * @param context Application context.
 */
class RecipientRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * What it is:
     * Retrieves all saved recipient records sorted alphabetically by contact name.
     *
     * What it does:
     * Executes [getAllRecipients] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [RecipientRepository], [getAllRecipients]
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
     * What it is:
     * Inserts or replaces a recipient record in the local database.
     *
     * What it does:
     * Executes [insertRecipient] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [RecipientRepository], [insertRecipient]
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
     * What it is:
     * Updates an existing recipient record in storage.
     *
     * What it does:
     * Executes [updateRecipient] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [RecipientRepository], [updateRecipient]
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
     * What it is:
     * Deletes a recipient record by ID.
     *
     * What it does:
     * Executes [deleteRecipient] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [RecipientRepository], [deleteRecipient]
     * @param id The recipient identifier.
     * @return `true` if deletion succeeded.
     */
    fun deleteRecipient(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_RECIPIENTS, "id = ?", arrayOf(id.toString())) > 0
    }
    /**
     * What it is:
     * Execution routine for [bulkInsertRecipients].
     *
     * What it does:
     * Coordinates bulkInsertRecipients operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [RecipientRepository]
     */

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
    /**
     * What it is:
     * Execution routine for [importContactsFromCsv].
     *
     * What it does:
     * Coordinates importContactsFromCsv operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [RecipientRepository]
     */

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
    /**
     * What it is:
     * Execution routine for [exportContactsToCsv].
     *
     * What it does:
     * Coordinates exportContactsToCsv operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [RecipientRepository]
     */

    fun exportContactsToCsv(): String {
        val list = getAllRecipients()
        return list.joinToString("\n") { "${it.name},${it.phoneOrEmail}" }
    }
}
