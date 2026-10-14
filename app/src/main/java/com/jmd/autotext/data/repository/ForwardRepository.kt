package com.jmd.autotext.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.jmd.autotext.data.DatabaseHelper
import com.jmd.autotext.model.AutoForwardRule

class ForwardRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    fun getAllForwardRules(): List<AutoForwardRule> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<AutoForwardRule>()
        db.query(DatabaseHelper.TABLE_AUTO_FORWARD, null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    AutoForwardRule(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        sourceType = c.getString(c.getColumnIndexOrThrow("source_type")),
                        forwardToNumber = c.getString(c.getColumnIndexOrThrow("forward_to_number")),
                        filterSender = c.getString(c.getColumnIndexOrThrow("filter_sender")),
                        filterKeyword = c.getString(c.getColumnIndexOrThrow("filter_keyword")),
                        isEnabled = c.getInt(c.getColumnIndexOrThrow("is_enabled")) == 1
                    )
                )
            }
        }
        return list
    }

    fun getEnabledForwardRules(sourceType: String = ""): List<AutoForwardRule> {
        val all = getAllForwardRules().filter { it.isEnabled }
        return if (sourceType.isBlank() || sourceType.equals("ALL", ignoreCase = true)) {
            all
        } else {
            all.filter { it.sourceType.equals("ALL", ignoreCase = true) || it.sourceType.equals(sourceType, ignoreCase = true) }
        }
    }

    fun insertForwardRule(rule: AutoForwardRule): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            if (rule.id > 0) put("id", rule.id)
            put("name", rule.name)
            put("source_type", rule.sourceType)
            put("forward_to_number", rule.forwardToNumber)
            put("filter_sender", rule.filterSender)
            put("filter_keyword", rule.filterKeyword)
            put("is_enabled", if (rule.isEnabled) 1 else 0)
        }
        val rowId = db.insertWithOnConflict(DatabaseHelper.TABLE_AUTO_FORWARD, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return if (rule.id > 0 && rowId >= 0) rule.id else rowId
    }

    fun updateForwardRule(rule: AutoForwardRule): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("name", rule.name)
            put("source_type", rule.sourceType)
            put("forward_to_number", rule.forwardToNumber)
            put("filter_sender", rule.filterSender)
            put("filter_keyword", rule.filterKeyword)
            put("is_enabled", if (rule.isEnabled) 1 else 0)
        }
        return db.update(DatabaseHelper.TABLE_AUTO_FORWARD, cv, "id = ?", arrayOf(rule.id.toString())) > 0
    }

    fun deleteForwardRule(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_AUTO_FORWARD, "id = ?", arrayOf(id.toString())) > 0
    }

    fun toggleForwardRule(id: Long, enabled: Boolean): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("is_enabled", if (enabled) 1 else 0)
        }
        return db.update(DatabaseHelper.TABLE_AUTO_FORWARD, cv, "id = ?", arrayOf(id.toString())) > 0
    }
}
