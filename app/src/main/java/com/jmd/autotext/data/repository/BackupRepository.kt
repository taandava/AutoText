package com.jmd.autotext.data.repository

import android.content.Context
import com.jmd.autotext.data.DatabaseHelper
import org.json.JSONArray
import org.json.JSONObject

class BackupRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    fun exportDatabaseToJson(): String {
        val db = dbHelper.readableDatabase
        val root = JSONObject()

        val tables = listOf(
            DatabaseHelper.TABLE_TASKS,
            DatabaseHelper.TABLE_TEMPLATES,
            DatabaseHelper.TABLE_AUTO_REPLY,
            DatabaseHelper.TABLE_AUTO_FORWARD,
            DatabaseHelper.TABLE_DAILY_CAMPAIGN,
            DatabaseHelper.TABLE_DYNAMIC_POOL,
            DatabaseHelper.TABLE_CONTACT_SEQUENCES,
            DatabaseHelper.TABLE_SEQUENCE_STEPS,
            DatabaseHelper.TABLE_RECIPIENTS,
            DatabaseHelper.TABLE_WHATSAPP_GROUPS,
            DatabaseHelper.TABLE_CAMPAIGNS,
            DatabaseHelper.TABLE_DRIP_STEPS
        )

        for (table in tables) {
            val array = JSONArray()
            try {
                db.query(table, null, null, null, null, null, null).use { cursor ->
                    val colNames: Array<String> = cursor.columnNames
                    while (cursor.moveToNext()) {
                        val row = JSONObject()
                        for (i in colNames.indices) {
                            val value: String? = cursor.getString(i)
                            if (value != null) {
                                val keyName: String = colNames[i]
                                row.put(keyName, value)
                            }
                        }
                        array.put(row)
                    }
                }
            } catch (_: Exception) {}
            root.put(table, array)
        }
        return root.toString(2)
    }

    fun importDatabaseFromJson(jsonStr: String): Result<String> {
        return try {
            val root = JSONObject(jsonStr)
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            var count = 0
            try {
                val tables = listOf(
                    DatabaseHelper.TABLE_TASKS,
                    DatabaseHelper.TABLE_TEMPLATES,
                    DatabaseHelper.TABLE_AUTO_REPLY,
                    DatabaseHelper.TABLE_AUTO_FORWARD,
                    DatabaseHelper.TABLE_DAILY_CAMPAIGN,
                    DatabaseHelper.TABLE_DYNAMIC_POOL,
                    DatabaseHelper.TABLE_CONTACT_SEQUENCES,
                    DatabaseHelper.TABLE_SEQUENCE_STEPS,
                    DatabaseHelper.TABLE_RECIPIENTS,
                    DatabaseHelper.TABLE_WHATSAPP_GROUPS,
                    DatabaseHelper.TABLE_CAMPAIGNS,
                    DatabaseHelper.TABLE_DRIP_STEPS
                )

                for (table in tables) {
                    if (root.has(table)) {
                        val array = root.getJSONArray(table)
                        // Retrieve existing column names for table to guarantee zero SQLiteException on unknown keys
                        val validColumns = mutableSetOf<String>()
                        db.rawQuery("PRAGMA table_info($table)", null).use { pragmaCursor ->
                            val nameIdx = pragmaCursor.getColumnIndex("name")
                            if (nameIdx >= 0) {
                                while (pragmaCursor.moveToNext()) {
                                    validColumns.add(pragmaCursor.getString(nameIdx))
                                }
                            }
                        }

                        db.delete(table, null, null)
                        for (i in 0 until array.length()) {
                            val row = array.getJSONObject(i)
                            val cv = android.content.ContentValues()
                            val keys = row.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                if (validColumns.contains(key) && !row.isNull(key)) {
                                    val strVal = row.optString(key, "")
                                    if (strVal != "null") {
                                        cv.put(key, strVal)
                                    }
                                }
                            }
                            db.insertWithOnConflict(table, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                            count++
                        }
                    }
                }
                db.setTransactionSuccessful()
                Result.success("Restored $count records from backup successfully")
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

