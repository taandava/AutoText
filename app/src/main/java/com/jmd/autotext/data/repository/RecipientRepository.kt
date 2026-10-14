package com.jmd.autotext.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.jmd.autotext.data.DatabaseHelper
import com.jmd.autotext.model.RecipientItem

class RecipientRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    fun getAllRecipients(): List<RecipientItem> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<RecipientItem>()
        db.query(DatabaseHelper.TABLE_RECIPIENTS, null, null, null, null, null, "name ASC").use { c ->
            while (c.moveToNext()) {
                val groupColIndex = c.getColumnIndex("group_name").takeIf { it != -1 } ?: c.getColumnIndex("notes")
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

    fun updateRecipient(recipient: RecipientItem): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("name", recipient.name)
            put("phone_or_email", recipient.phoneOrEmail)
            put("group_name", recipient.groupName)
        }
        return db.update(DatabaseHelper.TABLE_RECIPIENTS, cv, "id = ?", arrayOf(recipient.id.toString())) > 0
    }

    fun deleteRecipient(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_RECIPIENTS, "id = ?", arrayOf(id.toString())) > 0
    }

    fun bulkInsertRecipients(items: List<RecipientItem>): Int {
        val db = dbHelper.writableDatabase
        var count = 0
        db.beginTransaction()
        try {
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
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
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
