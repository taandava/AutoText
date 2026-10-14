package com.jmd.autotext.data

import android.content.ContentValues
import android.content.Context
import com.jmd.autotext.data.repository.*
import com.jmd.autotext.model.*

class AppRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    val taskRepo = TaskRepository(context)
    val dailyCampaignRepo = DailyCampaignRepository(context)
    val autoReplyRepo = AutoReplyRepository(context)
    val logRepo = LogRepository(context)
    val sequenceRepo = SequenceRepository(context)
    val backupRepo = BackupRepository(context)
    val templateRepo = TemplateRepository(context)
    val recipientRepo = RecipientRepository(context)
    val forwardRepo = ForwardRepository(context)
    val whatsAppRepo = WhatsAppRepository(context)

    // ==================== TASKS ====================
    fun insertTask(task: ScheduledTask): Long = taskRepo.insertTask(task)
    fun updateTask(task: ScheduledTask): Boolean = taskRepo.updateTask(task)
    fun updateTaskStatus(id: Long, status: TaskStatus, lastExecuted: Long = System.currentTimeMillis()) = taskRepo.updateTaskStatus(id, status, lastExecuted)
    fun deleteTask(id: Long): Boolean = taskRepo.deleteTask(id)
    fun getTaskById(id: Long): ScheduledTask? = taskRepo.getTaskById(id)
    fun getAllTasks(): List<ScheduledTask> = taskRepo.getAllTasks()
    fun getPendingTasks(): List<ScheduledTask> = taskRepo.getPendingTasks()
    fun getTasksByStatus(status: TaskStatus): List<ScheduledTask> = taskRepo.getTasksByStatus(status)

    // ==================== DAILY CAMPAIGN & DYNAMIC POOL ====================
    fun getPendingPoolContact(whatsApp: Boolean = false): DynamicContactItem? = dailyCampaignRepo.getPendingPoolContact(whatsApp)
    fun getPendingPoolContactsBatch(whatsApp: Boolean = false, limit: Int = 100): List<DynamicContactItem> = dailyCampaignRepo.getPendingPoolContactsBatch(whatsApp, limit)
    fun getUnsentRecipientsForDailyBatch(whatsApp: Boolean = false, limit: Int = 100): List<DynamicContactItem> = dailyCampaignRepo.getPendingPoolContactsBatch(whatsApp, limit)
    fun markContactSuccess(id: Long, whatsApp: Boolean = false) = dailyCampaignRepo.markContactSuccess(id, whatsApp)
    fun markContactSent(id: Long, whatsApp: Boolean = false) = dailyCampaignRepo.markContactSuccess(id, whatsApp)
    fun markContactPendingRetry(id: Long, whatsApp: Boolean = false, status: String = "QUEUED") = dailyCampaignRepo.markContactPendingRetry(id, whatsApp, status)
    fun markContactInvalid(id: Long, whatsApp: Boolean = false) = dailyCampaignRepo.markContactInvalid(id, whatsApp)
    fun getPoolStats(whatsApp: Boolean = false): Pair<Int, Int> = dailyCampaignRepo.getPoolStats(whatsApp)

    fun getDynamicPoolStats(whatsApp: Boolean = false): DynamicPoolStats {
        val (contacted, total) = dailyCampaignRepo.getPoolStats(whatsApp)
        return DynamicPoolStats(
            totalCount = total,
            unsentCount = total - contacted,
            sentCount = contacted
        )
    }

    fun getDynamicPool(
        limit: Int = 1000,
        searchQuery: String = "",
        filterUnsentOnly: Boolean = false,
        whatsApp: Boolean = false
    ): List<DynamicContactItem> {
        if (filterUnsentOnly) {
            val list = dailyCampaignRepo.getPendingPoolContactsBatch(whatsApp, limit)
            if (searchQuery.isBlank()) return list
            return list.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery) }
        }

        // Query entire dynamic pool ordered by name
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
        } catch (_: Exception) {}
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

    fun getSentContactsLedger(whatsApp: Boolean = false, limit: Int = 100): List<DynamicContactItem> {
        return dailyCampaignRepo.getSentContactsLedger(whatsApp, limit)
    }

    fun resetDailyCampaignLedger(whatsApp: Boolean = false) {
        dailyCampaignRepo.resetDailyCampaignLedger(whatsApp)
    }

    fun resetContactLedger(id: Long, whatsApp: Boolean = false) {
        dailyCampaignRepo.resetContactLedger(id, whatsApp)
    }

    fun upsertDynamicPoolBatch(items: List<DynamicContactItem>): Int {
        val db = dbHelper.writableDatabase
        var inserted = 0
        db.beginTransaction()
        try {
            for (item in items) {
                val cv = ContentValues().apply {
                    if (item.name.isNotBlank() && item.name != item.phone) {
                        put("name", item.name)
                    }
                    if (item.photoUri.isNotBlank()) {
                        put("photo_uri", item.photoUri)
                    }
                }
                val updated = if (cv.size() > 0) {
                    db.update(DatabaseHelper.TABLE_DYNAMIC_POOL, cv, "normalized_number = ?", arrayOf(item.normalizedNumber))
                } else 0

                if (updated > 0) {
                    inserted++
                } else {
                    val insertCv = ContentValues().apply {
                        put("name", item.name)
                        put("phone", item.phone)
                        put("normalized_number", item.normalizedNumber)
                        put("source", item.source)
                        put("enabled", if (item.enabled) 1 else 0)
                        put("created_at", item.createdAt)
                        if (item.photoUri.isNotBlank()) {
                            put("photo_uri", item.photoUri)
                        }
                    }
                    val rowId = db.insertWithOnConflict(DatabaseHelper.TABLE_DYNAMIC_POOL, null, insertCv, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
                    if (rowId >= 0) inserted++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return inserted
    }

    fun getDailyCampaignConfig(whatsApp: Boolean = false): DailyCampaignConfig {
        val db = dbHelper.readableDatabase
        val channelKey = if (whatsApp) "WHATSAPP" else "SMS"
        db.query(
            DatabaseHelper.TABLE_DAILY_CAMPAIGN,
            null,
            "channel = ?",
            arrayOf(channelKey),
            null, null, null
        ).use { c ->
            if (c.moveToFirst()) {
                return DailyCampaignConfig(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    channel = channelKey,
                    title = c.getString(c.getColumnIndexOrThrow("title")),
                    messageTemplate = c.getString(c.getColumnIndexOrThrow("message_template")),
                    dailyLimit = c.getInt(c.getColumnIndexOrThrow("daily_limit")),
                    intervalSeconds = c.getInt(c.getColumnIndexOrThrow("interval_seconds")),
                    scheduledHour = c.getInt(c.getColumnIndexOrThrow("scheduled_hour")),
                    scheduledMinute = c.getInt(c.getColumnIndexOrThrow("scheduled_minute")),
                    simSlot = c.getInt(c.getColumnIndexOrThrow("sim_slot")),
                    autoScanEnabled = c.getInt(c.getColumnIndexOrThrow("auto_scan_enabled")) == 1,
                    isActive = c.getInt(c.getColumnIndexOrThrow("is_active")) == 1,
                    lastRunDate = c.getString(c.getColumnIndexOrThrow("last_run_date"))
                )
            }
        }
        return DailyCampaignConfig(channel = channelKey)
    }

    fun getDailyCampaignConfig(id: Long): DailyCampaignConfig = getDailyCampaignConfig(whatsApp = (id == 2L))
    fun getDailyCampaignConfig(channel: String): DailyCampaignConfig = getDailyCampaignConfig(whatsApp = channel.contains("WHATSAPP", ignoreCase = true))

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
        return db.update(DatabaseHelper.TABLE_DAILY_CAMPAIGN, cv, "channel = ?", arrayOf(config.channel)) > 0
    }

    fun updateDailyCampaignLastRunDate(channel: String, date: String) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("last_run_date", date) }
        db.update(DatabaseHelper.TABLE_DAILY_CAMPAIGN, cv, "channel = ?", arrayOf(channel))
    }

    fun updateDailyCampaignLastRunDate(id: Long, date: String) {
        val channel = if (id == 2L) "WHATSAPP" else "SMS"
        updateDailyCampaignLastRunDate(channel, date)
    }

    fun getSentCountForCampaignToday(whatsApp: Boolean = false): Int {
        val (contacted, _) = dailyCampaignRepo.getPoolStats(whatsApp)
        return contacted
    }

    fun getSentCountForCampaignToday(id: Long): Int = getSentCountForCampaignToday(whatsApp = (id == 2L))

    fun getSentSegmentsForCampaignToday(): Int = getSentCountForCampaignToday(false)
    fun getSentSegmentsForCampaignToday(id: Long): Int = getSentCountForCampaignToday(id)

    // ==================== AUTO REPLY ====================
    fun insertRule(rule: AutoReplyRule): Long = autoReplyRepo.insertRule(rule)
    fun insertAutoReplyRule(rule: AutoReplyRule): Long = autoReplyRepo.insertRule(rule)
    fun updateAutoReplyRule(rule: AutoReplyRule): Boolean = autoReplyRepo.updateRule(rule)
    fun getAllRules(): List<AutoReplyRule> = autoReplyRepo.getAllRules()
    fun getAllAutoReplyRules(): List<AutoReplyRule> = autoReplyRepo.getAllRules()
    fun getEnabledAutoReplyRules(): List<AutoReplyRule> = autoReplyRepo.getEnabledRules()
    fun getEnabledAutoReplyRules(channelStr: String): List<AutoReplyRule> = autoReplyRepo.getEnabledRules(runCatching { ChannelType.valueOf(channelStr) }.getOrNull())
    fun getEnabledAutoReplyRules(channel: ChannelType): List<AutoReplyRule> = autoReplyRepo.getEnabledRules(channel)
    fun deleteRule(id: Long): Boolean = autoReplyRepo.deleteRule(id)
    fun deleteAutoReplyRule(id: Long): Boolean = autoReplyRepo.deleteRule(id)
    fun toggleAutoReplyRule(id: Long, enabled: Boolean): Boolean = autoReplyRepo.toggleRule(id, enabled)

    fun canReplyToNumber(ruleId: Long, phone: String): Boolean {
        val rule = autoReplyRepo.getAllRules().find { it.id == ruleId } ?: return true
        return autoReplyRepo.canReplyToNumber(rule, phone)
    }
    fun canReplyToNumber(rule: AutoReplyRule, phone: String): Boolean = autoReplyRepo.canReplyToNumber(rule, phone)
    fun recordReplySent(ruleId: Long, phone: String) = autoReplyRepo.recordReplySent(ruleId, phone)
    fun recordReplySent(rule: AutoReplyRule, phone: String) = autoReplyRepo.recordReplySent(rule.id, phone)

    // ==================== LOGS ====================
    fun logExecution(taskId: Long = 0, channel: String, recipient: String, messageSnippet: String, status: String, details: String = ""): Long =
        logRepo.logExecution(taskId, channel, recipient, messageSnippet, status, details)
    fun insertLog(taskId: Long = 0, channel: String, recipient: String, messageSnippet: String, status: String, details: String = ""): Long =
        logExecution(taskId, channel, recipient, messageSnippet, status, details)
    fun insertLog(log: ExecutionLog): Long = logRepo.insertLog(log)
    fun getAllLogs(limit: Int = 200): List<ExecutionLog> = logRepo.getAllLogs(limit)
    fun clearLogs() = logRepo.clearAllLogs()
    fun clearAllLogs() = logRepo.clearAllLogs()

    fun getLogsForToday(): List<ExecutionLog> {
        return logRepo.getLogsForToday()
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
    fun bulkInsertRecipients(items: List<RecipientItem>): Int = recipientRepo.bulkInsertRecipients(items)
    fun importContactsFromCsv(csvText: String): Int = recipientRepo.importContactsFromCsv(csvText)
    fun exportContactsToCsv(): String = recipientRepo.exportContactsToCsv()

    // ==================== AUTO FORWARD ====================
    fun getAllForwardRules(): List<AutoForwardRule> = forwardRepo.getAllForwardRules()
    fun getEnabledForwardRules(): List<AutoForwardRule> = forwardRepo.getEnabledForwardRules()
    fun getEnabledForwardRules(sourceType: String): List<AutoForwardRule> = forwardRepo.getEnabledForwardRules(sourceType)
    fun insertForwardRule(rule: AutoForwardRule): Long = forwardRepo.insertForwardRule(rule)
    fun updateForwardRule(rule: AutoForwardRule): Boolean = forwardRepo.updateForwardRule(rule)
    fun deleteForwardRule(id: Long): Boolean = forwardRepo.deleteForwardRule(id)
    fun toggleForwardRule(id: Long, enabled: Boolean): Boolean = forwardRepo.toggleForwardRule(id, enabled)

    // ==================== SEQUENCES ====================
    fun insertSequence(seq: ContactSequence): Long = sequenceRepo.insertSequence(seq)
    fun saveContactSequence(seq: ContactSequence): Long = sequenceRepo.insertSequence(seq)
    fun saveContactSequence(seq: ContactSequence, startTs: Long): Long = sequenceRepo.insertSequence(seq)
    fun saveContactSequence(seq: ContactSequence, steps: List<ContactSequenceStep>): Long = sequenceRepo.insertSequence(seq.copy(steps = steps))
    fun getAllSequences(): List<ContactSequence> = sequenceRepo.getAllSequences()
    fun getAllContactSequences(): List<ContactSequence> = sequenceRepo.getAllSequences()
    fun getContactSequenceById(id: Long): ContactSequence? = sequenceRepo.getAllSequences().find { it.id == id }
    fun getStepsForSequence(seqId: Long): List<ContactSequenceStep> = sequenceRepo.getStepsForSequence(seqId)
    fun deleteSequence(id: Long): Boolean = sequenceRepo.deleteSequence(id)
    fun deleteContactSequence(id: Long): Boolean = sequenceRepo.deleteSequence(id)

    fun toggleContactSequence(id: Long, enabled: Boolean): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("is_enabled", if (enabled) 1 else 0)
        }
        return db.update(DatabaseHelper.TABLE_CONTACT_SEQUENCES, cv, "id = ?", arrayOf(id.toString())) > 0
    }

    // ==================== WHATSAPP GROUPS & CONTACTS ====================
    fun bulkSaveWhatsAppGroups(groups: List<String>): Int = whatsAppRepo.bulkSaveWhatsAppGroups(groups)
    fun bulkSaveWhatsAppGroupItems(groups: List<com.jmd.autotext.model.WhatsAppGroupItem>): Int = whatsAppRepo.bulkSaveWhatsAppGroupItems(groups)
    fun saveWhatsAppGroup(groupName: String): Boolean = whatsAppRepo.saveWhatsAppGroup(groupName)
    fun saveWhatsAppGroup(groupName: String, groupJid: String, memberCount: String): Boolean = whatsAppRepo.saveWhatsAppGroup(groupName, groupJid, memberCount)
    fun getWhatsAppGroups(): List<String> = whatsAppRepo.getWhatsAppGroups()
    fun getWhatsAppGroupItems(): List<com.jmd.autotext.model.WhatsAppGroupItem> = whatsAppRepo.getWhatsAppGroupItems()
    fun deleteWhatsAppGroup(groupName: String): Boolean = whatsAppRepo.deleteWhatsAppGroup(groupName)
    fun isValidGroupName(name: String): Boolean = whatsAppRepo.isValidGroupName(name)
    fun getWhatsAppContacts(query: String = "", limit: Int = 100): List<WhatsAppContactItem> = whatsAppRepo.getWhatsAppContacts(query, limit)

    // ==================== BACKUP ====================
    fun exportDatabaseToJson(): String = backupRepo.exportDatabaseToJson()
    fun importDatabaseFromJson(jsonStr: String): Result<String> = backupRepo.importDatabaseFromJson(jsonStr)
}
