

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
                val idIdx = c.getColumnIndex("id")
                val titleIdx = c.getColumnIndex("title")
                val msgIdx = if (c.getColumnIndex("message_text") >= 0) c.getColumnIndex("message_text") else c.getColumnIndex("message")
                val recIdx = if (c.getColumnIndex("recipients") >= 0) c.getColumnIndex("recipients") else c.getColumnIndex("recipient")
                val recNamesIdx = c.getColumnIndex("recipient_names")
                val timeIdx = if (c.getColumnIndex("trigger_time") >= 0) c.getColumnIndex("trigger_time") else c.getColumnIndex("scheduled_time")
                val channelIdx = c.getColumnIndex("channel")
                val statusIdx = c.getColumnIndex("status")
                val recTypeIdx = c.getColumnIndex("recurrence")
                val intervalIdx = if (c.getColumnIndex("custom_interval_minutes") >= 0) c.getColumnIndex("custom_interval_minutes") else c.getColumnIndex("recurrence_interval_mins")
                val mediaIdx = c.getColumnIndex("media_uri")
                val callDurIdx = c.getColumnIndex("call_duration_seconds")
                val simIdx = c.getColumnIndex("sim_slot")
                val soundIdx = c.getColumnIndex("sound_mode")

                while (c.moveToNext()) {
                    tasks.add(
                        TaskBackupDto(
                            id = if (idIdx >= 0) c.getLong(idIdx) else 0L,
                            title = if (titleIdx >= 0) c.getString(titleIdx).orEmpty() else "",
                            message = if (msgIdx >= 0) c.getString(msgIdx).orEmpty() else "",
                            recipient = if (recIdx >= 0) c.getString(recIdx).orEmpty() else "",
                            recipientNames = if (recNamesIdx >= 0) c.getString(recNamesIdx).orEmpty() else "",
                            scheduledTime = if (timeIdx >= 0) c.getLong(timeIdx) else 0L,
                            channel = if (channelIdx >= 0) c.getString(channelIdx).orEmpty() else "",
                            status = if (statusIdx >= 0) c.getString(statusIdx).orEmpty() else "",
                            recurrence = if (recTypeIdx >= 0) c.getString(recTypeIdx).orEmpty() else "NONE",
                            recurrenceIntervalMins = if (intervalIdx >= 0) c.getLong(intervalIdx) else 0L,
                            mediaUri = if (mediaIdx >= 0) c.getString(mediaIdx) else null,
                            callDurationSeconds = if (callDurIdx >= 0) c.getInt(callDurIdx) else 0,
                            simSlot = if (simIdx >= 0) c.getInt(simIdx) else 0,
                            soundMode = if (soundIdx >= 0) c.getString(soundIdx) else null
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
                val idIdx = c.getColumnIndex("id")
                val titleIdx = c.getColumnIndex("title")
                val catIdx = c.getColumnIndex("category")
                val contentIdx = c.getColumnIndex("content")

                while (c.moveToNext()) {
                    templates.add(
                        TemplateBackupDto(
                            id = if (idIdx >= 0) c.getLong(idIdx) else 0L,
                            title = if (titleIdx >= 0) c.getString(titleIdx).orEmpty() else "",
                            category = if (catIdx >= 0) c.getString(catIdx).orEmpty() else "",
                            content = if (contentIdx >= 0) c.getString(contentIdx).orEmpty() else "",
                            createdAt = System.currentTimeMillis()
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
                val idIdx = c.getColumnIndex("id")
                val nameIdx = if (c.getColumnIndex("name") >= 0) c.getColumnIndex("name") else c.getColumnIndex("title")
                val channelIdx = c.getColumnIndex("channel")
                val callEventIdx = if (c.getColumnIndex("call_event_type") >= 0) c.getColumnIndex("call_event_type") else c.getColumnIndex("event_type")
                val patternIdx = if (c.getColumnIndex("trigger_pattern") >= 0) c.getColumnIndex("trigger_pattern") else c.getColumnIndex("keyword_trigger")
                val replyIdx = if (c.getColumnIndex("reply_text") >= 0) c.getColumnIndex("reply_text") else c.getColumnIndex("reply_message")
                val enabledIdx = c.getColumnIndex("is_enabled")

                while (c.moveToNext()) {
                    rules.add(
                        AutoReplyBackupDto(
                            id = if (idIdx >= 0) c.getLong(idIdx) else 0L,
                            title = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else "",
                            channel = if (channelIdx >= 0) c.getString(channelIdx).orEmpty() else "",
                            eventType = if (callEventIdx >= 0) c.getString(callEventIdx).orEmpty() else "MESSAGE",
                            keywordTrigger = if (patternIdx >= 0) c.getString(patternIdx).orEmpty() else "*",
                            replyMessage = if (replyIdx >= 0) c.getString(replyIdx).orEmpty() else "",
                            isEnabled = enabledIdx >= 0 && c.getInt(enabledIdx) == 1,
                            simSlot = 0
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppFileLogger.w("BackupRepository", "Failed to serialize auto-reply rules: ${e.message}")
        }

        val campaigns = mutableListOf<CampaignBackupDto>()
        try {
            db.query(DatabaseHelper.TABLE_DAILY_CAMPAIGN, null, null, null, null, null, null).use { c ->
                val idIdx = c.getColumnIndex("id")
                val titleIdx = c.getColumnIndex("title")
                val chanIdx = c.getColumnIndex("channel")
                val tmplIdx = c.getColumnIndex("message_template")
                val limIdx = c.getColumnIndex("daily_limit")
                val simIdx = c.getColumnIndex("sim_slot")
                val actIdx = c.getColumnIndex("is_active")
                while (c.moveToNext()) {
                    campaigns.add(
                        CampaignBackupDto(
                            id = if (idIdx >= 0) c.getLong(idIdx) else 0L,
                            title = if (titleIdx >= 0) c.getString(titleIdx).orEmpty() else "",
                            channel = if (chanIdx >= 0) c.getString(chanIdx).orEmpty() else "SMS",
                            messageTemplate = if (tmplIdx >= 0) c.getString(tmplIdx).orEmpty() else "",
                            dailyLimit = if (limIdx >= 0) c.getInt(limIdx) else 50,
                            simSlot = if (simIdx >= 0) c.getInt(simIdx) else 0,
                            isActive = actIdx >= 0 && c.getInt(actIdx) == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppFileLogger.w("BackupRepository", "Failed to serialize daily campaigns: ${e.message}")
        }

        val contacts = mutableListOf<ContactBackupDto>()
        try {
            db.query(DatabaseHelper.TABLE_DYNAMIC_POOL, null, null, null, null, null, null).use { c ->
                val idIdx = c.getColumnIndex("id")
                val nameIdx = c.getColumnIndex("name")
                val phoneIdx = c.getColumnIndex("phone")
                val normIdx = c.getColumnIndex("normalized_number")
                val waCountIdx = c.getColumnIndex("wa_send_count")
                val lastSentIdx = c.getColumnIndex("last_sent_at")
                val sendCountIdx = c.getColumnIndex("send_count")
                while (c.moveToNext()) {
                    contacts.add(
                        ContactBackupDto(
                            id = if (idIdx >= 0) c.getLong(idIdx) else 0L,
                            displayName = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else "",
                            phoneNumber = if (phoneIdx >= 0) c.getString(phoneIdx).orEmpty() else "",
                            normalizedPhone = if (normIdx >= 0) c.getString(normIdx).orEmpty() else "",
                            isWhatsAppUser = waCountIdx >= 0 && c.getInt(waCountIdx) > 0,
                            lastReachedTimestamp = if (lastSentIdx >= 0) c.getLong(lastSentIdx) else 0L,
                            reachCount = if (sendCountIdx >= 0) c.getInt(sendCountIdx) else 0
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppFileLogger.w("BackupRepository", "Failed to serialize dynamic pool contacts: ${e.message}")
        }

        val payload = BackupPayloadDto(
            version = 2,
            app = "Helper",
            exportedAt = System.currentTimeMillis(),
            tasks = tasks,
            templates = templates,
            autoReplyRules = rules,
            dailyCampaigns = campaigns,
            contacts = contacts
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
                for ((_, title, message, recipient, recipientNames, scheduledTime, channel, status, recurrence, recurrenceIntervalMins, mediaUri, callDurationSeconds, simSlot, soundMode) in payload.tasks) {
                    val cv = ContentValues().apply {
                        put("title", title)
                        put("message_text", message)
                        put("recipients", recipient)
                        put("recipient_names", recipientNames)
                        put("trigger_time", scheduledTime)
                        put("channel", channel)
                        put("status", status)
                        put("recurrence", recurrence)
                        put("custom_interval_minutes", recurrenceIntervalMins.toInt())
                        put("media_uri", mediaUri ?: "")
                        put("call_duration_seconds", callDurationSeconds)
                        put("sim_slot", simSlot)
                        put("sound_mode", soundMode ?: "NORMAL")
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_TASKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    count++
                }

                for ((_, title, category, content) in payload.templates) {
                    val cv = ContentValues().apply {
                        put("title", title)
                        put("category", category)
                        put("content", content)
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_TEMPLATES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    count++
                }

                for ((_, title, channel, eventType, keywordTrigger, replyMessage, isEnabled) in payload.autoReplyRules) {
                    val cv = ContentValues().apply {
                        put("name", title)
                        put("channel", channel)
                        put("call_event_type", eventType)
                        put("trigger_pattern", keywordTrigger)
                        put("reply_text", replyMessage)
                        put("is_enabled", if (isEnabled) 1 else 0)
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_AUTO_REPLY, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    count++
                }

                for ((id, title, channel, messageTemplate, dailyLimit, simSlot, isActive) in payload.dailyCampaigns) {
                    val cv = ContentValues().apply {
                        put("id", id)
                        put("title", title)
                        put("channel", channel)
                        put("message_template", messageTemplate)
                        put("daily_limit", dailyLimit)
                        put("sim_slot", simSlot)
                        put("is_active", if (isActive) 1 else 0)
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_DAILY_CAMPAIGN, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    count++
                }

                for ((_, displayName, phoneNumber, normalizedPhone, _, lastReachedTimestamp, reachCount) in payload.contacts) {
                    val cv = ContentValues().apply {
                        put("name", displayName)
                        put("phone", phoneNumber)
                        put("normalized_number", normalizedPhone.ifBlank { phoneNumber })
                        put("source", "BACKUP")
                        put("enabled", 1)
                        put("last_sent_at", lastReachedTimestamp)
                        put("send_count", reachCount)
                        put("created_at", System.currentTimeMillis())
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_DYNAMIC_POOL, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
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
            DatabaseHelper.TABLE_RECIPIENTS,
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
                    DatabaseHelper.TABLE_RECIPIENTS,
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
     * Uses Scoped Storage and app-specific directories for offline data sovereignty.
     *
     * @param directory Target directory (e.g. app-specific documents directory).
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
