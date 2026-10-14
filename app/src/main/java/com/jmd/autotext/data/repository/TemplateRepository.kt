package com.jmd.autotext.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.jmd.autotext.data.DatabaseHelper
import com.jmd.autotext.model.MessageTemplate

class TemplateRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    fun getAllTemplates(): List<MessageTemplate> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<MessageTemplate>()
        db.query(DatabaseHelper.TABLE_TEMPLATES, null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    MessageTemplate(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        category = c.getString(c.getColumnIndexOrThrow("category")),
                        content = c.getString(c.getColumnIndexOrThrow("content"))
                    )
                )
            }
        }
        return list
    }

    fun insertTemplate(template: MessageTemplate): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            if (template.id > 0) put("id", template.id)
            put("title", template.title)
            put("category", template.category)
            put("content", template.content)
        }
        val rowId = db.insertWithOnConflict(DatabaseHelper.TABLE_TEMPLATES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return if (template.id > 0 && rowId >= 0) template.id else rowId
    }

    fun updateTemplate(template: MessageTemplate): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("title", template.title)
            put("category", template.category)
            put("content", template.content)
        }
        return db.update(DatabaseHelper.TABLE_TEMPLATES, cv, "id = ?", arrayOf(template.id.toString())) > 0
    }

    fun deleteTemplate(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_TEMPLATES, "id = ?", arrayOf(id.toString())) > 0
    }
}
