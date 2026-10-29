

/**
 * Package: com.jmd.autotext.data.repo
 * Module: Data Repositories Layer
 *
 * Persists reusable message templates with placeholder variables ({name}, {time}, {date}, etc.).
 *
 * Linked With:
 * - Database: [com.jmd.autotext.data.db.DatabaseHelper]
 * - Resolver: [com.jmd.autotext.core.MessageTemplateResolver]
 * - Models: [com.jmd.autotext.data.model.MessageTemplate]
 * - UI Screens: `TemplatesScreen`
 */
package com.jmd.autotext.data.repo

import android.content.*
import android.database.sqlite.SQLiteDatabase
import com.jmd.autotext.data.db.DatabaseHelper
import com.jmd.autotext.data.model.MessageTemplate

/**
 * Repository providing access to pre-defined and user-customized message templates.
 *
 * @param context Application context.
 */
class TemplateRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * Retrieves all saved message templates ordered by ID descending.
     *
     * @return List of [MessageTemplate] objects.
     */
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

    /**
     * Inserts or replaces a message template in storage.
     *
     * @param template The template model.
     * @return Row ID of the stored template.
     */
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

    /**
     * Updates an existing message template.
     *
     * @param template Modified template object.
     * @return `true` if update succeeded.
     */
    fun updateTemplate(template: MessageTemplate): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("title", template.title)
            put("category", template.category)
            put("content", template.content)
        }
        return db.update(DatabaseHelper.TABLE_TEMPLATES, cv, "id = ?", arrayOf(template.id.toString())) > 0
    }

    /**
     * Deletes a template by ID.
     *
     * @param id The template ID.
     * @return `true` if deleted.
     */
    fun deleteTemplate(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_TEMPLATES, "id = ?", arrayOf(id.toString())) > 0
    }
}
