

/**
 * Package: com.jmd.helper.data.repo
 * Module: Unified Repository Facade Layer
 *
 * Exposes a unified, transaction-safe facade aggregating access across all domain repositories:
 * tasks, templates, dynamic contact pool, daily campaigns, execution logs, auto-reply rules, and WhatsApp contacts.
 *
 * Linked With:
 * - Persistence: [com.jmd.helper.data.db.DatabaseHelper]
 * - Sub-Repositories: [com.jmd.helper.data.repo.TaskRepository], [com.jmd.helper.data.repo.DailyCampaignRepository],
 *   [com.jmd.helper.data.repo.LogRepository], [com.jmd.helper.data.repo.RecipientRepository],
 *   [com.jmd.helper.data.repo.AutoReplyRepository], [com.jmd.helper.data.repo.TemplateRepository],
 *   [com.jmd.helper.data.repo.SequenceRepository], [com.jmd.helper.data.repo.WhatsAppRepository]
 * - UI Screens: `TasksScreen`, `DailyCampaignScreen`,
 *   `ComposeTaskScreen`, `DailyCampaignScreen`
 * - Schedulers: [com.jmd.helper.scheduler.AlarmScheduler]
 */
package com.jmd.helper.data.repo

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.core.database.sqlite.transaction
import com.jmd.helper.scheduler.AlarmScheduler
import com.jmd.helper.core.PhoneNumberNormalizer
import com.jmd.helper.data.db.DatabaseHelper
import com.jmd.helper.data.model.AutoReplyRule
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.DailyCampaignConfig
import com.jmd.helper.data.model.DynamicContactItem
import com.jmd.helper.data.model.DynamicPoolStats
import com.jmd.helper.data.model.ExecutionLog
import com.jmd.helper.data.model.MessageTemplate
import com.jmd.helper.data.model.RecipientItem
import com.jmd.helper.data.model.RecurrenceType
import com.jmd.helper.data.model.ScheduledTask
import com.jmd.helper.data.model.TaskStatus
import com.jmd.helper.data.model.WhatsAppContactItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified data access facade coordinating CRUD operations across all sub-repositories.
 */
class AppRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    val taskRepo = TaskRepository(context)
    val dailyCampaignRepo = DailyCampaignRepository(context)
    val autoReplyRepo = AutoReplyRepository(context)
    val logRepo = LogRepository(context)
    val backupRepo = BackupRepository(context)
    val templateRepo = TemplateRepository(context)
    val recipientRepo = RecipientRepository(context)
    val whatsAppRepo = WhatsAppRepository(context)
    val privacyRepo = PrivacyRepository(context)

    // ==================== TASKS ====================
    fun insertTask(task: ScheduledTask): Long = taskRepo.insertTask(task)
    fun updateTask(task: ScheduledTask): Boolean = taskRepo.updateTask(task)
    fun updateTaskStatus(id: Long, status: TaskStatus, lastExecuted: Long = System.currentTimeMillis()) = taskRepo.updateTaskStatus(id, status, lastExecuted)
    fun deleteTask(id: Long): Boolean = taskRepo.deleteTask(id)
    fun getTaskById(id: Long): ScheduledTask? = taskRepo.getTaskById(id)
    fun getAllTasks(): List<ScheduledTask> = taskRepo.getAllTasks()
    fun getPendingTasks(): List<ScheduledTask> = taskRepo.getPendingTasks()
    fun recoverInterruptedRunningTasks(): Int = taskRepo.recoverInterruptedRunningTasks()
    fun postponeTask(id: Long, postponeMillis: Long): Boolean = taskRepo.postponeTask(id, postponeMillis)
    fun postponeUpcomingTasks(postponeMillis: Long): List<ScheduledTask> = taskRepo.postponeUpcomingTasks(postponeMillis)

    // ==================== DAILY CAMPAIGN & DYNAMIC POOL ====================
    fun getPendingPoolContactsForCampaign(campaignId: Long, isWhatsApp: Boolean = false, limit: Int = 100): List<DynamicContactItem> =
        dailyCampaignRepo.getPendingPoolContactsForCampaign(campaignId, isWhatsApp, limit)

    fun markCampaignContactSuccess(campaignId: Long, contact: DynamicContactItem, channel: String, segments: Int = 1) =
        dailyCampaignRepo.markCampaignContactSuccess(campaignId, contact, channel, segments)

    fun markCampaignContactDispatched(campaignId: Long, contact: DynamicContactItem, channel: String, segments: Int = 1) =
        dailyCampaignRepo.markCampaignContactDispatched(campaignId, contact, channel, segments)

    fun getSentContactsForCampaign(campaignId: Long, limit: Int = 100): List<DynamicContactItem> =
        dailyCampaignRepo.getSentContactsForCampaign(campaignId, limit)

    fun resetCampaignLedger(campaignId: Long) =
        dailyCampaignRepo.resetCampaignLedger(campaignId)

    fun requeueContactForCampaign(campaignId: Long, contactId: Long) =
        dailyCampaignRepo.requeueContactForCampaign(campaignId, contactId)

    fun getCampaignStats(campaignId: Long): DynamicPoolStats =
        dailyCampaignRepo.getCampaignStats(campaignId)

    fun markContactSent(id: Long, whatsApp: Boolean = false) = dailyCampaignRepo.markContactSuccess(id, whatsApp)
    fun markContactPendingRetry(id: Long, whatsApp: Boolean = false, status: String = "QUEUED") = dailyCampaignRepo.markContactPendingRetry(id, whatsApp, status)
    fun markContactInvalid(id: Long, whatsApp: Boolean = false) = dailyCampaignRepo.markContactInvalid(id, whatsApp)

    fun getDynamicPoolStats(whatsApp: Boolean = false): DynamicPoolStats {
        val (contacted, total) = dailyCampaignRepo.getPoolStats(whatsApp)
        return DynamicPoolStats(
            totalCount = total,
            unsentCount = total - contacted,
            sentCount = contacted,
        )
    }

    fun getDynamicPool(
        limit: Int = 1000,
        searchQuery: String = "",
        filterUnsentOnly: Boolean = false,
        whatsApp: Boolean = false,
    ): List<DynamicContactItem> {
        if (filterUnsentOnly) {
            val list = dailyCampaignRepo.getPendingPoolContactsBatch(whatsApp, limit)
            if (searchQuery.isBlank()) return list
            return list.filter { (_, name, phone) ->
                name.contains(searchQuery, ignoreCase = true) || phone.contains(searchQuery)
            }
        }

        val list = mutableListOf<DynamicContactItem>()
        try {
            val db = dbHelper.readableDatabase
            val trimmedQuery = searchQuery.trim()
            val sql = if (trimmedQuery.isNotBlank()) {
                "SELECT id, name, phone, normalized_number, source, enabled, last_sent_at, send_count, created_at, wa_last_sent_at, wa_send_count, IFNULL(sms_status, 'PENDING'), IFNULL(wa_status, 'PENDING'), IFNULL(photo_uri, '') FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE (name LIKE ? OR phone LIKE ? OR normalized_number LIKE ?) AND enabled = 1 ORDER BY name ASC LIMIT ?"
            } else {
                "SELECT id, name, phone, normalized_number, source, enabled, last_sent_at, send_count, created_at, wa_last_sent_at, wa_send_count, IFNULL(sms_status, 'PENDING'), IFNULL(wa_status, 'PENDING'), IFNULL(photo_uri, '') FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE enabled = 1 ORDER BY name ASC LIMIT ?"
            }
            val args = if (trimmedQuery.isNotBlank()) {
                arrayOf("%$trimmedQuery%", "%$trimmedQuery%", "%$trimmedQuery%", limit.toString())
            } else {
                arrayOf(limit.toString())
            }
            db.rawQuery(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        DynamicContactItem(
                            id = cursor.getLong(0),
                            name = cursor.getString(1) ?: "",
                            phone = cursor.getString(2) ?: "",
                            normalizedNumber = cursor.getString(3) ?: "",
                            source = cursor.getString(4) ?: "CONTACT",
                            enabled = cursor.getInt(5) == 1,
                            lastSentAt = cursor.getLong(6),
                            sendCount = cursor.getInt(7),
                            createdAt = cursor.getLong(8),
                            waLastSentAt = cursor.getLong(9),
                            waSendCount = cursor.getInt(10),
                            smsStatus = cursor.getString(11) ?: "PENDING",
                            waStatus = cursor.getString(12) ?: "PENDING",
                            photoUri = cursor.getString(13) ?: ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "Error querying dynamic pool contacts: ${e.message}")
        }
        return list
    }

    fun clearDynamicPool() {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_DYNAMIC_POOL, null, null)
    }

    fun deleteDynamicContact(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_DYNAMIC_POOL, "id = ?", arrayOf(id.toString())) > 0
    }

    fun toggleDynamicContact(id: Long, enabled: Boolean): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
        }
        return db.update(DatabaseHelper.TABLE_DYNAMIC_POOL, cv, "id = ?", arrayOf(id.toString())) > 0
    }

    fun upsertDynamicPoolBatch(items: List<DynamicContactItem>): Int {
        val db = dbHelper.writableDatabase
        var inserted = 0
        db.transaction {
            for ((_, name, phone, normalizedNumber, source, enabled, _, _, createdAt, _, _, smsStatus, waStatus, photoUri) in items) {
                if (phone.isBlank() && normalizedNumber.isBlank()) continue
                val norm = normalizedNumber.ifBlank { PhoneNumberNormalizer.normalize(phone) }
                if (norm.isBlank()) continue

                val insertCv = ContentValues().apply {
                    put("name", name.ifBlank { phone })
                    put("phone", phone)
                    put("normalized_number", norm)
                    put("source", source.ifBlank { "CONTACT" })
                    put("enabled", if (enabled) 1 else 0)
                    put("created_at", if (createdAt > 0) createdAt else System.currentTimeMillis())
                    put("sms_status", smsStatus.ifBlank { "PENDING" })
                    put("wa_status", waStatus.ifBlank { "PENDING" })
                    if (photoUri.isNotBlank()) {
                        put("photo_uri", photoUri)
                    }
                }
                val rowId = db.insertWithOnConflict(DatabaseHelper.TABLE_DYNAMIC_POOL, null, insertCv, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
                if (rowId >= 0) {
                    inserted++
                } else {
                    val updateCv = ContentValues().apply {
                        if (name.isNotBlank() && (name != phone)) {
                            put("name", name)
                        }
                        if (photoUri.isNotBlank()) {
                            put("photo_uri", photoUri)
                        }
                        if (source.isNotBlank() && source != "CONTACT") {
                            put("source", source)
                        }
                        if (phone.isNotBlank() && !phone.startsWith("group:") && !phone.startsWith("chat:")) {
                            put("phone", phone)
                        }
                    }
                    if (updateCv.size() > 0) {
                        db.update(DatabaseHelper.TABLE_DYNAMIC_POOL, updateCv, "normalized_number = ?", arrayOf(norm))
                    }
                }
            }
        }
        return inserted
    }

    fun getAllDailyCampaignConfigs(): List<DailyCampaignConfig> {
        val list = mutableListOf<DailyCampaignConfig>()
        val db = dbHelper.readableDatabase
        try {
            db.query(
                DatabaseHelper.TABLE_DAILY_CAMPAIGN,
                null, null, null, null, null,
                "id ASC"
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(
                        DailyCampaignConfig(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            channel = c.getString(c.getColumnIndexOrThrow("channel")) ?: "SMS",
                            title = c.getString(c.getColumnIndexOrThrow("title")) ?: "Daily Campaign",
                            messageTemplate = c.getString(c.getColumnIndexOrThrow("message_template")) ?: "",
                            dailyLimit = c.getInt(c.getColumnIndexOrThrow("daily_limit")),
                            intervalSeconds = c.getInt(c.getColumnIndexOrThrow("interval_seconds")),
                            scheduledHour = c.getInt(c.getColumnIndexOrThrow("scheduled_hour")),
                            scheduledMinute = c.getInt(c.getColumnIndexOrThrow("scheduled_minute")),
                            simSlot = c.getInt(c.getColumnIndexOrThrow("sim_slot")),
                            autoScanEnabled = c.getInt(c.getColumnIndexOrThrow("auto_scan_enabled")) == 1,
                            isActive = c.getInt(c.getColumnIndexOrThrow("is_active")) == 1,
                            lastRunDate = c.getString(c.getColumnIndexOrThrow("last_run_date")) ?: ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "Error querying daily campaign configs: ${e.message}")
        }
        if (list.isEmpty()) {
            list.add(DailyCampaignConfig(id = 1L, channel = "SMS", title = "Daily SMS Campaign"))
            list.add(DailyCampaignConfig(id = 2L, channel = "WHATSAPP", title = "Daily WhatsApp Campaign"))
            list.add(DailyCampaignConfig(id = 3L, channel = "WHATSAPP_BUSINESS", title = "Daily WhatsApp Business Campaign"))
        }
        return list
    }

    fun getDailyCampaignConfig(id: Long): DailyCampaignConfig {
        val db = dbHelper.readableDatabase
        try {
            db.query(
                DatabaseHelper.TABLE_DAILY_CAMPAIGN,
                null,
                "id = ?",
                arrayOf(id.toString()),
                null, null, null
            ).use { c ->
                if (c.moveToFirst()) {
                    return DailyCampaignConfig(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        channel = c.getString(c.getColumnIndexOrThrow("channel")) ?: "SMS",
                        title = c.getString(c.getColumnIndexOrThrow("title")) ?: "Daily Campaign",
                        messageTemplate = c.getString(c.getColumnIndexOrThrow("message_template")) ?: "",
                        dailyLimit = c.getInt(c.getColumnIndexOrThrow("daily_limit")),
                        intervalSeconds = c.getInt(c.getColumnIndexOrThrow("interval_seconds")),
                        scheduledHour = c.getInt(c.getColumnIndexOrThrow("scheduled_hour")),
                        scheduledMinute = c.getInt(c.getColumnIndexOrThrow("scheduled_minute")),
                        simSlot = c.getInt(c.getColumnIndexOrThrow("sim_slot")),
                        autoScanEnabled = c.getInt(c.getColumnIndexOrThrow("auto_scan_enabled")) == 1,
                        isActive = c.getInt(c.getColumnIndexOrThrow("is_active")) == 1,
                        lastRunDate = c.getString(c.getColumnIndexOrThrow("last_run_date")) ?: ""
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("AppRepository", "Error querying daily campaign config #$id: ${e.message}")
        }
        return when (id) {
            2L -> DailyCampaignConfig(id = 2L, channel = "WHATSAPP", title = "Daily WhatsApp Campaign")
            3L -> DailyCampaignConfig(id = 3L, channel = "WHATSAPP_BUSINESS", title = "Daily WhatsApp Business Campaign")
            else -> DailyCampaignConfig(id = id, channel = "SMS", title = "Daily SMS Campaign")
        }
    }

    fun insertDailyCampaignConfig(config: DailyCampaignConfig): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("channel", config.channel)
            put("title", config.title)
            put("message_template", config.messageTemplate)
            put("daily_limit", config.dailyLimit)
            put("interval_seconds", config.intervalSeconds)
            put("scheduled_hour", config.scheduledHour)
            put("scheduled_minute", config.scheduledMinute)
            put("sim_slot", config.simSlot)
            put("auto_scan_enabled", if (config.autoScanEnabled) 1 else 0)
            put("is_active", if (config.isActive) 1 else 0)
            put("last_run_date", config.lastRunDate)
        }
        return db.insert(DatabaseHelper.TABLE_DAILY_CAMPAIGN, null, cv)
    }

    fun saveDailyCampaignConfig(config: DailyCampaignConfig): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("title", config.title)
            put("message_template", config.messageTemplate)
            put("daily_limit", config.dailyLimit)
            put("interval_seconds", config.intervalSeconds)
            put("scheduled_hour", config.scheduledHour)
            put("scheduled_minute", config.scheduledMinute)
            put("sim_slot", config.simSlot)
            put("auto_scan_enabled", if (config.autoScanEnabled) 1 else 0)
            put("is_active", if (config.isActive) 1 else 0)
            put("last_run_date", config.lastRunDate)
            put("channel", config.channel)
        }
        val rows = db.update(DatabaseHelper.TABLE_DAILY_CAMPAIGN, cv, "id = ?", arrayOf(config.id.toString()))
        if (rows > 0) return true
        val channelRows = db.update(DatabaseHelper.TABLE_DAILY_CAMPAIGN, cv, "channel = ?", arrayOf(config.channel))
        if (channelRows > 0) return true
        if (config.id > 0L) {
            cv.put("id", config.id)
        }
        val insertId = db.insertWithOnConflict(DatabaseHelper.TABLE_DAILY_CAMPAIGN, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        return insertId > 0
    }

    fun deleteDailyCampaignConfig(id: Long): Boolean {
        if (id <= 3L) return false // Protect primary seed campaigns
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_DAILY_CAMPAIGN, "id = ?", arrayOf(id.toString())) > 0
    }

    fun updateDailyCampaignLastRunDate(id: Long, date: String) {
        val config = getDailyCampaignConfig(id)
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("last_run_date", date) }
        val rows = db.update(DatabaseHelper.TABLE_DAILY_CAMPAIGN, cv, "id = ?", arrayOf(id.toString()))
        if (rows <= 0) {
            db.update(DatabaseHelper.TABLE_DAILY_CAMPAIGN, cv, "channel = ?", arrayOf(config.channel))
        }
    }

    fun getSentCountForCampaignToday(id: Long): Int {
        return dailyCampaignRepo.getSentCountForCampaignToday(id)
    }

    fun getSentSegmentsForCampaignToday(id: Long): Int = dailyCampaignRepo.getSentSegmentsForCampaignToday(id)

    fun isRecipientDeliveredForTask(taskId: Long, recipient: String, occurrenceId: String = ""): Boolean =
        taskRepo.isRecipientDeliveredForTask(taskId, recipient, occurrenceId)

    fun recordRecipientDelivery(taskId: Long, recipient: String, channel: String, status: String = "SUCCESS", occurrenceId: String = ""): Boolean =
        taskRepo.recordRecipientDelivery(taskId, recipient, channel, status, occurrenceId)

    fun clearDeliveriesForTask(taskId: Long): Int =
        taskRepo.clearDeliveriesForTask(taskId)

    // ==================== AUTO REPLY ====================
    fun insertAutoReplyRule(rule: AutoReplyRule): Long = autoReplyRepo.insertRule(rule)
    fun updateAutoReplyRule(rule: AutoReplyRule): Boolean = autoReplyRepo.updateRule(rule)
    fun getAllAutoReplyRules(): List<AutoReplyRule> = autoReplyRepo.getAllRules()
    fun getEnabledAutoReplyRules(channel: ChannelType): List<AutoReplyRule> = autoReplyRepo.getEnabledRules(channel)
    fun deleteAutoReplyRule(id: Long): Boolean = autoReplyRepo.deleteRule(id)
    fun canReplyToNumber(rule: AutoReplyRule, phone: String): Boolean = autoReplyRepo.canReplyToNumber(rule, phone)
    fun recordReplySent(ruleId: Long, phone: String) = autoReplyRepo.recordReplySent(ruleId, phone)

    // ==================== LOGS ====================
    fun logExecution(taskId: Long = 0, channel: String, recipient: String, messageSnippet: String, status: String, details: String = "", segments: Int = 1): Long =
        logRepo.logExecution(taskId, channel, recipient, messageSnippet, status, details, segments)
    fun insertLog(taskId: Long = 0, channel: String, recipient: String, messageSnippet: String, status: String, details: String = "", segments: Int = 1): Long =
        logExecution(taskId, channel, recipient, messageSnippet, status, details, segments)
    fun insertLog(log: ExecutionLog): Long = logRepo.insertLog(log)
    fun getAllLogs(limit: Int = 200): List<ExecutionLog> = logRepo.getAllLogs(limit)
    fun clearLogs() = logRepo.clearAllLogs()

    fun pruneOldLogs(maxCount: Int = LogRepository.MAX_LOG_RETENTION_COUNT, maxAgeDays: Int = LogRepository.MAX_LOG_RETENTION_DAYS): Int =
        logRepo.pruneOldLogs(maxCount, maxAgeDays)

    fun getLogsForToday(): List<ExecutionLog> {
        return logRepo.getLogsForToday()
    }

    fun exportLogsToCsv(): String {
        val logs = logRepo.getAllLogs(limit = 10000)
        val sb = StringBuilder("ID,TaskID,Channel,Recipient,Status,Timestamp,Date,Snippet,Details\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for ((id, taskId, channel, recipient, messageSnippet, status, details, _, timestampMillis) in logs) {
            val dateStr = sdf.format(Date(timestampMillis))
            val cleanSnippet = messageSnippet.replace("\"", "\"\"").replace("\n", " ")
            val cleanDetails = details.replace("\"", "\"\"").replace("\n", " ")
            sb.append("$id,$taskId,\"$channel\",\"$recipient\",\"$status\",$timestampMillis,\"$dateStr\",\"$cleanSnippet\",\"$cleanDetails\"\n")
        }
        return sb.toString()
    }

    // ==================== TEMPLATES ====================
    fun getAllTemplates(): List<MessageTemplate> = templateRepo.getAllTemplates()
    fun insertTemplate(template: MessageTemplate): Long = templateRepo.insertTemplate(template)
    fun updateTemplate(template: MessageTemplate): Boolean = templateRepo.updateTemplate(template)
    fun deleteTemplate(id: Long): Boolean = templateRepo.deleteTemplate(id)

    // ==================== RECIPIENTS ====================
    fun getAllRecipients(): List<RecipientItem> = recipientRepo.getAllRecipients()
    fun insertRecipient(recipient: RecipientItem): Long = recipientRepo.insertRecipient(recipient)
    fun updateRecipient(recipient: RecipientItem): Boolean = recipientRepo.updateRecipient(recipient)
    fun deleteRecipient(id: Long): Boolean = recipientRepo.deleteRecipient(id)
    fun importContactsFromCsv(csvText: String): Int = recipientRepo.importContactsFromCsv(csvText)
    fun exportContactsToCsv(): String = recipientRepo.exportContactsToCsv()

    // ==================== WHATSAPP CONTACTS ====================
    fun isValidPickedChatName(name: String): Boolean = whatsAppRepo.isValidPickedChatName(name)
    fun findPhoneForContactName(name: String): String? = whatsAppRepo.findPhoneForContactName(name)
    fun hasCellularPhoneContact(name: String): Boolean = whatsAppRepo.hasCellularPhoneContact(name)
    fun findPhotoForContactName(name: String): String = whatsAppRepo.findPhotoForContactName(name)
    fun findContactByPhone(phone: String): Pair<String, String>? = whatsAppRepo.findContactByPhone(phone)
    fun getWhatsAppContacts(query: String = "", channel: ChannelType = ChannelType.WHATSAPP, limit: Int = 10000): List<WhatsAppContactItem> =
        whatsAppRepo.getWhatsAppContacts(query, channel, limit)

    // ==================== WHATSAPP DATA CLEANUP ====================
    fun deleteAllWhatsAppGroups(source: String? = null): Int = whatsAppRepo.deleteAllWhatsAppGroups(source)
    fun purgeCorruptedGroupsFromPool(): Int = whatsAppRepo.purgeCorruptedGroupsFromPool()
    fun purgeAllGroupsFromPool(): Int = whatsAppRepo.purgeAllGroupsFromPool()

    // ==================== BACKUP ====================
    fun exportDatabaseToJson(): String = backupRepo.exportDatabaseToJson()
    fun importDatabaseFromJson(jsonStr: String): Result<String> = backupRepo.importDatabaseFromJson(jsonStr)
    fun exportDatabaseToLocalDirectory(directory: java.io.File): Result<java.io.File> = backupRepo.exportDatabaseToLocalDirectory(directory)
    fun listLocalBackups(directory: java.io.File): List<java.io.File> = backupRepo.listLocalBackups(directory)
}
