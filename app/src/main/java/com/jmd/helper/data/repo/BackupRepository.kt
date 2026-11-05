

/**
 * Package: com.jmd.helper.data.repo
 * Module: Data Repositories Layer
 *
 * Provides transactional backup export and restore mechanisms to convert all application SQLite tables
 * to and from structured JSON format for user export/import.
 *
 * Linked With:
 * - Database: [com.jmd.helper.data.db.DatabaseHelper]
 * - UI Screens: `SettingsScreen`
 */
package com.jmd.helper.data.repo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.sqlite.transaction
import com.jmd.helper.core.AppFileLogger
import com.jmd.helper.data.db.DatabaseHelper
import com.jmd.helper.data.model.AutoReplyBackupDto
import com.jmd.helper.data.model.BackupPayloadDto
import com.jmd.helper.data.model.CampaignBackupDto
import com.jmd.helper.data.model.ContactBackupDto
import com.jmd.helper.data.model.TaskBackupDto
import com.jmd.helper.data.model.TemplateBackupDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository executing database export and atomic table restoration via JSON serialization.
 * Supports both high-speed Kotlinx Serialization typed payloads and schema-agnostic raw table dumps.
 *
 * @param context Application context.
 */
class BackupRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    private val jsonSerializer = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Exports key domain entities using reflection-free, typed Kotlinx Serialization.
     */
    fun exportStructuredBackupToJson(): String {
        val db = dbHelper.readableDatabase

        val tasks = mutableListOf<TaskBackupDto>()
        try {
            db.query(DatabaseHelper.TABLE_TASKS, null, null, null, null, null, null).use { c ->
                while (c.moveToNext()) {
                    tasks.add(
                        TaskBackupDto(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            title = c.getString(c.getColumnIndexOrThrow("title")),
                            message = c.getString(c.getColumnIndexOrThrow("message")),
                            recipient = c.getString(c.getColumnIndexOrThrow("recipient")),
                            scheduledTime = c.getLong(c.getColumnIndexOrThrow("scheduled_time")),
                            channel = c.getString(c.getColumnIndexOrThrow("channel")),
                            status = c.getString(c.getColumnIndexOrThrow("status")),
                            recurrence = c.getString(c.getColumnIndexOrThrow("recurrence")),
                            recurrenceIntervalMins = c.getLong(c.getColumnIndexOrThrow("recurrence_interval_mins")),
                            mediaUri = c.getString(c.getColumnIndexOrThrow("media_uri")),
                            callDurationSeconds = c.getInt(c.getColumnIndexOrThrow("call_duration_seconds")),
                            simSlot = c.getInt(c.getColumnIndexOrThrow("sim_slot")),
                            soundMode = c.getString(c.getColumnIndexOrThrow("sound_mode"))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppFileLogger.w("BackupRepository", "Failed to serialize tasks for typed backup: ${e.message}")
        }

        val templates = mutableListOf<TemplateBackupDto>()
        try {
            db.query(DatabaseHelper.TABLE_TEMPLATES, null, null, null, null, null, null).use { c ->
                while (c.moveToNext()) {
                    templates.add(
                        TemplateBackupDto(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            title = c.getString(c.getColumnIndexOrThrow("title")),
                            category = c.getString(c.getColumnIndexOrThrow("category")),
                            content = c.getString(c.getColumnIndexOrThrow("content")),
                            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppFileLogger.w("BackupRepository", "Failed to serialize templates for typed backup: ${e.message}")
        }

        val rules = mutableListOf<AutoReplyBackupDto>()
        try {
            db.query(DatabaseHelper.TABLE_AUTO_REPLY, null, null, null, null, null, null).use { c ->
                while (c.moveToNext()) {
                    rules.add(
                        AutoReplyBackupDto(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            title = c.getString(c.getColumnIndexOrThrow("title")),
                            channel = c.getString(c.getColumnIndexOrThrow("channel")),
                            eventType = c.getString(c.getColumnIndexOrThrow("event_type")),
                            keywordTrigger = c.getString(c.getColumnIndexOrThrow("keyword_trigger")),
                            replyMessage = c.getString(c.getColumnIndexOrThrow("reply_message")),
                            isEnabled = c.getInt(c.getColumnIndexOrThrow("is_enabled")) == 1,
                            simSlot = c.getInt(c.getColumnIndexOrThrow("sim_slot"))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppFileLogger.w("BackupRepository", "Failed to serialize auto-reply rules: ${e.message}")
        }

        val payload = BackupPayloadDto(
            version = 2,
            app = "Helper",
            exportedAt = System.currentTimeMillis(),
            tasks = tasks,
            templates = templates,
            autoReplyRules = rules
        )

        return jsonSerializer.encodeToString(payload)
    }

    /**
     * Imports structured backup using Kotlinx Serialization.
     */
    fun importStructuredBackupFromJson(jsonStr: String): Result<String> {
        return try {
            val payload = jsonSerializer.decodeFromString<BackupPayloadDto>(jsonStr)
            val db = dbHelper.writableDatabase
            var count = 0

            db.transaction {
                for (task in payload.tasks) {
                    val cv = ContentValues().apply {
                        put("title", task.title)
                        put("message", task.message)
                        put("recipient", task.recipient)
                        put("scheduled_time", task.scheduledTime)
                        put("channel", task.channel)
                        put("status", task.status)
                        put("recurrence", task.recurrence)
                        put("recurrence_interval_mins", task.recurrenceIntervalMins)
                        put("media_uri", task.mediaUri)
                        put("call_duration_seconds", task.callDurationSeconds)
                        put("sim_slot", task.simSlot)
                        put("sound_mode", task.soundMode)
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_TASKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    count++
                }

                for (tpl in payload.templates) {
                    val cv = ContentValues().apply {
                        put("title", tpl.title)
                        put("category", tpl.category)
                        put("content", tpl.content)
                        put("created_at", tpl.createdAt)
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_TEMPLATES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    count++
                }

                for (rule in payload.autoReplyRules) {
                    val cv = ContentValues().apply {
                        put("title", rule.title)
                        put("channel", rule.channel)
                        put("event_type", rule.eventType)
                        put("keyword_trigger", rule.keywordTrigger)
                        put("reply_message", rule.replyMessage)
                        put("is_enabled", if (rule.isEnabled) 1 else 0)
                        put("sim_slot", rule.simSlot)
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_AUTO_REPLY, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    count++
                }
            }

            Result.success("Restored $count items via Kotlinx Serialization")
        } catch (e: Exception) {
            AppFileLogger.w("BackupRepository", "Structured import failed, falling back to raw table import: ${e.message}")
            importDatabaseFromJson(jsonStr)
        }
    }

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
            DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS,
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
                    DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS,
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
     * @param directory Target directory (e.g. /sdcard/Documents/Helper/backups/).
     * @return [Result] containing the written [File] or failure exception.
     */
    fun exportDatabaseToLocalDirectory(directory: java.io.File): Result<java.io.File> {
        return try {
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val targetFile = java.io.File(directory, "Helper_Backup_$timeStamp.json")
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
