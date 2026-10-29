

/**
 * Package: com.jmd.autotext.data.repo
 * Module: Data Repositories Layer
 *
 * Provides transactional backup export and restore mechanisms to convert all application SQLite tables
 * to and from structured JSON format for user export/import.
 *
 * Linked With:
 * - Database: [com.jmd.autotext.data.db.DatabaseHelper]
 * - UI Screens: `SettingsScreen`
 */
package com.jmd.autotext.data.repo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.sqlite.transaction
import com.jmd.autotext.data.db.DatabaseHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository executing database export and atomic table restoration via JSON serialization.
 *
 * @param context Application context.
 */
class BackupRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * Serializes all application SQLite tables into a formatted JSON string for export.
     *
     * @return Formatted multi-table JSON document.
     */
    fun exportDatabaseToJson(): String {
        val db = dbHelper.readableDatabase
        val root = JSONObject()

        val tables = listOf(
            DatabaseHelper.TABLE_TASKS,
            DatabaseHelper.TABLE_TEMPLATES,
            DatabaseHelper.TABLE_AUTO_REPLY,
            DatabaseHelper.TABLE_DAILY_CAMPAIGN,
            DatabaseHelper.TABLE_DYNAMIC_POOL,
            DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT,
            DatabaseHelper.TABLE_CONTACT_SEQUENCES,
            DatabaseHelper.TABLE_SEQUENCE_STEPS,
            DatabaseHelper.TABLE_RECIPIENTS,
            DatabaseHelper.TABLE_WHATSAPP_GROUPS,
            DatabaseHelper.TABLE_CAMPAIGNS,
            DatabaseHelper.TABLE_DRIP_STEPS,
            DatabaseHelper.TABLE_LOGS,
            DatabaseHelper.TABLE_AUTO_REPLY_HISTORY,
            DatabaseHelper.TABLE_TASK_DELIVERIES,
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
            } catch (e: Exception) {
                Log.w("BackupRepository", "Failed to query table $table for backup: ${e.message}")
            }
            root.put(table, array)
        }
        return root.toString(2)
    }

    /**
     * Atomically restores database state by reading serialized JSON rows and inserting
     * records across all registered application tables within a single transaction.
     *
     * @param jsonStr Raw JSON backup content.
     * @return [Result] encapsulating success message with restored count or error exception.
     */
    fun importDatabaseFromJson(jsonStr: String): Result<String> {
        return try {
            val root = JSONObject(jsonStr)
            val db = dbHelper.writableDatabase
            var count = 0
            db.transaction {
                val tables = listOf(
                    DatabaseHelper.TABLE_TASKS,
                    DatabaseHelper.TABLE_TEMPLATES,
                    DatabaseHelper.TABLE_AUTO_REPLY,
                    DatabaseHelper.TABLE_DAILY_CAMPAIGN,
                    DatabaseHelper.TABLE_DYNAMIC_POOL,
                    DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT,
                    DatabaseHelper.TABLE_CONTACT_SEQUENCES,
                    DatabaseHelper.TABLE_SEQUENCE_STEPS,
                    DatabaseHelper.TABLE_RECIPIENTS,
                    DatabaseHelper.TABLE_WHATSAPP_GROUPS,
                    DatabaseHelper.TABLE_CAMPAIGNS,
                    DatabaseHelper.TABLE_DRIP_STEPS,
                    DatabaseHelper.TABLE_LOGS,
                    DatabaseHelper.TABLE_AUTO_REPLY_HISTORY,
                    DatabaseHelper.TABLE_TASK_DELIVERIES,
                )

                for (table in tables) {
                    if (root.has(table)) {
                        val array = root.getJSONArray(table)
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
                            val cv = ContentValues()
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
                            db.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                            count++
                        }
                    }
                }
                Result.success("Restored $count records from backup successfully")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Directly saves a timestamped JSON backup file to the specified target directory.
     * Leveraging MANAGE_EXTERNAL_STORAGE when available for direct offline data retention.
     *
     * @param directory Target directory (e.g. /sdcard/Documents/AutoText/backups/).
     * @return [Result] containing the written [File] or failure exception.
     */
    fun exportDatabaseToLocalDirectory(directory: java.io.File): Result<java.io.File> {
        return try {
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val targetFile = java.io.File(directory, "AutoText_Backup_$timeStamp.json")
            val json = exportDatabaseToJson()
            targetFile.writeText(json)
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e("BackupRepository", "Failed to export backup to local directory ${directory.absolutePath}: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Scans and returns existing JSON backup files in the specified directory, sorted newest first.
     *
     * @param directory Target backup directory to inspect.
     * @return List of backup files.
     */
    fun listLocalBackups(directory: java.io.File): List<java.io.File> {
        return try {
            if (!directory.exists() || !directory.isDirectory) return emptyList()
            directory.listFiles { file -> file.isFile && file.name.endsWith(".json", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (e: Exception) {
            Log.w("BackupRepository", "Failed to list local backups in ${directory.absolutePath}: ${e.message}")
            emptyList()
        }
    }
}
