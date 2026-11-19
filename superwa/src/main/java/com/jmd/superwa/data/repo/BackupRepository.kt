/**
 * Package: com.jmd.superwa.data.repo
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [BackupRepository].
 *
 * What it does:
 * Provides transactional backup export and restore mechanisms to convert all application SQLite tables to and from structured JSON format for user export/import.
 *
 * Linked With:
 * - Repositories: [android.database.sqlite.SQLiteDatabase], [com.jmd.superwa.data.db.DatabaseHelper]
 * - Models & Entities: [com.jmd.superwa.data.model.AutoReplyBackupDto], [com.jmd.superwa.data.model.BackupDataDto], [com.jmd.superwa.data.model.CampaignBackupDto], [com.jmd.superwa.data.model.ContactBackupDto]
 * - Core Security & Utilities: [android.util.Log], [androidx.core.database.sqlite.transaction], [com.jmd.superwa.core.AppFileLogger]
 * - Android Framework & Jetpack: [android.content.ContentValues], [kotlinx.serialization.encodeToString], [kotlinx.serialization.json.Json]
 */

package com.jmd.superwa.data.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.sqlite.transaction
import com.jmd.superwa.core.AppDataEvents
import com.jmd.superwa.core.AppFileLogger
import com.jmd.superwa.core.BackupCipher
import com.jmd.superwa.data.db.DatabaseHelper
import com.jmd.superwa.data.model.AutoReplyBackupDto
import com.jmd.superwa.data.model.BackupDataDto
import com.jmd.superwa.data.model.CampaignBackupDto
import com.jmd.superwa.data.model.ContactBackupDto
import com.jmd.superwa.data.model.TaskBackupDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * What it is:
 * Repository executing database export and atomic table restoration via JSON serialization.
 *
 * What it does:
 * Supports both high-speed Kotlinx Serialization typed payloads and schema-agnostic raw table dumps.
 *
 * Linked with:
 * - Architecture: [BackupRepository], [BackupRepository]
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
     * What it is:
     * Exports key domain entities using reflection-free, typed Kotlinx Serialization.
     *
     * What it does:
     * Executes [exportStructuredBackupToJson] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [BackupRepository], [exportStructuredBackupToJson]
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
                val tmplIdx = c.getColumnIndex("message")
                val limIdx = c.getColumnIndex("daily_limit")
                val simIdx = c.getColumnIndex("sim_slot")
                val actIdx = c.getColumnIndex("is_active")
                while (c.moveToNext()) {
                    campaigns.add(
                        CampaignBackupDto(
                            id = if (idIdx >= 0) c.getLong(idIdx) else 0L,
                            title = if (titleIdx >= 0) c.getString(titleIdx).orEmpty() else "",
                            channel = if (chanIdx >= 0) c.getString(chanIdx).orEmpty() else "SMS",
                            message = if (tmplIdx >= 0) c.getString(tmplIdx).orEmpty() else "",
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

        val backupData = BackupDataDto(
            version = 2,
            app = "Super",
            exportedAt = System.currentTimeMillis(),
            tasks = tasks,
            autoReplyRules = rules,
            dailyCampaigns = campaigns,
            contacts = contacts
        )

        return jsonSerializer.encodeToString(backupData)
    }

    /**
     * What it is:
     * Imports structured backup using Kotlinx Serialization.
     *
     * What it does:
     * Executes [importStructuredBackupFromJson] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [BackupRepository], [importStructuredBackupFromJson]
     */
    fun importStructuredBackupFromJson(jsonStr: String): Result<String> {
        return try {
            val backupData = jsonSerializer.decodeFromString<BackupDataDto>(jsonStr)
            val db = dbHelper.writableDatabase
            var count = 0

            db.transaction {
                for ((_, title, message, recipient, recipientNames, scheduledTime, channel, status, recurrence, recurrenceIntervalMins, mediaUri, callDurationSeconds, simSlot, soundMode) in backupData.tasks) {
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


                for ((_, title, channel, eventType, keywordTrigger, replyMessage, isEnabled) in backupData.autoReplyRules) {
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

                for ((id, title, channel, message, dailyLimit, simSlot, isActive) in backupData.dailyCampaigns) {
                    val cv = ContentValues().apply {
                        put("id", id)
                        put("title", title)
                        put("channel", channel)
                        put("message", message)
                        put("daily_limit", dailyLimit)
                        put("sim_slot", simSlot)
                        put("is_active", if (isActive) 1 else 0)
                    }
                    db.insertWithOnConflict(DatabaseHelper.TABLE_DAILY_CAMPAIGN, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    count++
                }

                for ((_, displayName, phoneNumber, normalizedPhone, _, lastReachedTimestamp, reachCount) in backupData.contacts) {
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

            AppDataEvents.notifyTasksChanged()
            AppDataEvents.notifyCampaignsChanged()
            AppDataEvents.notifyAutoReplyChanged()
            AppDataEvents.notifyLogsChanged()

            Result.success("Restored $count items via Kotlinx Serialization")
        } catch (e: Exception) {
            AppFileLogger.w("BackupRepository", "Structured import failed, falling back to raw table import: ${e.message}")
            importDatabaseFromJson(jsonStr)
        }
    }

    /**
     * What it is:
     * Serializes all application SQLite tables into a formatted JSON string for export.
     *
     * What it does:
     * Executes [exportDatabaseToJson] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [BackupRepository], [exportDatabaseToJson]
     * @return Formatted multi-table JSON document.
     */
    fun exportDatabaseToJson(passphrase: String = ""): String {
        val db = dbHelper.readableDatabase
        val root = JSONObject()

        val tables = listOf(
            DatabaseHelper.TABLE_TASKS,
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
        // Backups leave the app encrypted (AES-256-GCM portable or device-bound Keystore envelope).
        return BackupCipher.encrypt(root.toString(2), passphrase)
    }

    /**
     * What it is:
     * Atomically restores database state by reading serialized JSON rows and inserting records across all registered application tables within a single transaction.
     *
     * What it does:
     * Executes [importDatabaseFromJson] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [BackupRepository], [importDatabaseFromJson]
     * @param jsonStr Raw JSON backup content.
     * @param passphrase Optional decryption passphrase for portable SUPERBAK2 backups.
     * @return [Result] encapsulating success message with restored count or error exception.
     */
    fun importDatabaseFromJson(jsonStr: String, passphrase: String = ""): Result<String> {
        return try {
            val root = JSONObject(BackupCipher.decryptIfNeeded(jsonStr, passphrase))
            val db = dbHelper.writableDatabase
            var count = 0
            db.transaction {
                val tables = listOf(
                    DatabaseHelper.TABLE_TASKS,
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
                AppDataEvents.notifyTasksChanged()
                AppDataEvents.notifyCampaignsChanged()
                AppDataEvents.notifyAutoReplyChanged()
                AppDataEvents.notifyLogsChanged()
                Result.success("Restored $count records from backup successfully")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * What it is:
     * Directly saves a timestamped JSON backup file to the specified target directory.
     *
     * What it does:
     * Uses Scoped Storage and app-specific directories for offline data sovereignty.
     *
     * Linked with:
     * - Architecture: [BackupRepository], [exportDatabaseToLocalDirectory]
     * @param directory Target directory (e.g. app-specific documents directory).
     * @return [Result] containing the written [File] or failure exception.
     */
    fun exportDatabaseToLocalDirectory(directory: java.io.File, passphrase: String = ""): Result<java.io.File> {
        return try {
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val targetFile = java.io.File(directory, "Super_Backup_$timeStamp.json")
            val json = exportDatabaseToJson(passphrase)
            targetFile.writeText(json)
            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e("BackupRepository", "Failed to export backup to local directory ${directory.absolutePath}: ${e.message}", e)
            Result.failure(e)
        }
    }


    /**
     * What it is:
     * Exports an encrypted backup into the public Documents collection.
     *
     * What it does:
     * On Android 10+ it inserts through MediaStore (no storage permission, survives reinstall);
     * on older releases it falls back to a direct Documents file. Returns the user-visible path.
     *
     * Linked with:
     * - Architecture: [BackupRepository], [exportDatabaseToDocuments]
     * @param context Active Android [Context].
     * @return [Result] with the display path of the written backup.
     */
    fun exportDatabaseToDocuments(context: Context): Result<String> {
        val json = exportDatabaseToJson()
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val fileName = "Super_Backup_$timeStamp.json"
        val relativeDir = "${Environment.DIRECTORY_DOCUMENTS}/Super/Backups"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri: Uri = resolver.insert(collection, values)
                    ?: return Result.failure(java.io.IOException("MediaStore refused to create the backup entry"))
                try {
                    resolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: return Result.failure(java.io.IOException("Unable to open the backup file for writing"))
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Result.success("$relativeDir/$fileName")
                } catch (t: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    Result.failure(t)
                }
            } else {
                val directory = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Super/Backups")
                if (!directory.exists()) directory.mkdirs()
                val target = java.io.File(directory, fileName)
                target.writeText(json)
                Result.success("$relativeDir/$fileName")
            }
        } catch (t: Throwable) {
            Log.e("BackupRepository", "Documents export failed: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * What it is:
     * Scans and returns existing JSON backup files in the specified directory, sorted newest first.
     *
     * What it does:
     * Executes [listLocalBackups] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [BackupRepository], [listLocalBackups]
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

    /**
     * What it is:
     * Scans and returns existing JSON backup documents from a Storage Access Framework (SAF) tree URI.
     *
     * What it does:
     * Leverages [androidx.documentfile.provider.DocumentFile] to traverse user-selected external storage directories.
     *
     * Linked with:
     * - Architecture: [BackupRepository], [listSafBackups]
     * @param context Active Android [Context].
     * @param treeUri SAF Directory Tree [android.net.Uri].
     * @return List of matching JSON [androidx.documentfile.provider.DocumentFile] objects sorted newest first.
     */
    fun listSafBackups(context: Context, treeUri: android.net.Uri): List<androidx.documentfile.provider.DocumentFile> {
        return try {
            val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
            if (!root.isDirectory || !root.canRead()) return emptyList()
            root.listFiles()
                .filter { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
                .sortedByDescending { it.lastModified() }
        } catch (e: Exception) {
            Log.w("BackupRepository", "Failed to list SAF backups from $treeUri: ${e.message}")
            emptyList()
        }
    }
}
