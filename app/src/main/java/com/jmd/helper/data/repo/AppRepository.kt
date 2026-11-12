/**
 * Package: com.jmd.helper.data.repo
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [AppRepository].
 *
 * What it does:
 * Exposes a unified, transaction-safe facade aggregating access across all domain repositories: tasks, templates, dynamic contact pool, daily campaigns, execution logs, auto-reply rules, and WhatsApp contacts. [com.jmd.helper.data.repo.LogRepository], [com.jmd.helper.data.repo.RecipientRepository], [com.jmd.helper.data.repo.AutoReplyRepository], [com.jmd.helper.data.repo.WhatsAppRepository] `ComposeTaskScreen`, `DailyCampaignScreen`
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.db.DatabaseHelper]
 * - Models & Entities: [com.jmd.helper.data.model.AutoReplyRule], [com.jmd.helper.data.model.ChannelType], [com.jmd.helper.data.model.DailyCampaignConfig], [com.jmd.helper.data.model.DynamicContactItem]
 * - Schedulers & Receivers: [com.jmd.helper.scheduler.AlarmScheduler]
 * - Core Security & Utilities: [android.util.Log], [androidx.core.database.sqlite.transaction], [com.jmd.helper.core.PhoneNumberNormalizer], [java.util.Date]
 * - Android Framework & Jetpack: [android.content.ContentValues]
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
import com.jmd.helper.data.model.RecipientItem
import com.jmd.helper.data.model.RecurrenceType
import com.jmd.helper.data.model.ScheduledTask
import com.jmd.helper.data.model.TaskStatus
import com.jmd.helper.data.model.WhatsAppContactItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What it is:
 * Unified data access facade coordinating CRUD operations across all sub-repositories.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [AppRepository].
 *
 * Linked with:
 * - Architecture: [AppRepository], [AppRepository]
 */
class AppRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    val taskRepo = TaskRepository(context)
    val dailyCampaignRepo = DailyCampaignRepository(context)
    val autoReplyRepo = AutoReplyRepository(context)
    val logRepo = LogRepository(context)
    val backupRepo = BackupRepository(context)
    val recipientRepo = RecipientRepository(context)
    val whatsAppRepo = WhatsAppRepository(context)
    val privacyRepo = PrivacyRepository(context)

    // ==================== TASKS ====================
    /**
     * What it is:
     * Execution routine for [insertTask].
     *
     * What it does:
     * Coordinates insertTask operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun insertTask(task: ScheduledTask): Long = taskRepo.insertTask(task)
    /**
     * What it is:
     * Execution routine for [updateTask].
     *
     * What it does:
     * Coordinates updateTask operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun updateTask(task: ScheduledTask): Boolean = taskRepo.updateTask(task)
    /**
     * What it is:
     * Execution routine for [updateTaskStatus].
     *
     * What it does:
     * Coordinates updateTaskStatus operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun updateTaskStatus(id: Long, status: TaskStatus, lastExecuted: Long = System.currentTimeMillis()) = taskRepo.updateTaskStatus(id, status, lastExecuted)
    /**
     * What it is:
     * Execution routine for [deleteTask].
     *
     * What it does:
     * Coordinates deleteTask operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun deleteTask(id: Long): Boolean = taskRepo.deleteTask(id)
    /**
     * What it is:
     * Execution routine for [getTaskById].
     *
     * What it does:
     * Coordinates getTaskById operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun getTaskById(id: Long): ScheduledTask? = taskRepo.getTaskById(id)
    /**
     * What it is:
     * Execution routine for [getAllTasks].
     *
     * What it does:
     * Coordinates getAllTasks operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun getAllTasks(): List<ScheduledTask> = taskRepo.getAllTasks()
    /**
     * What it is:
     * Execution routine for [getPendingTasks].
     *
     * What it does:
     * Coordinates getPendingTasks operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun getPendingTasks(): List<ScheduledTask> = taskRepo.getPendingTasks()
    /**
     * What it is:
     * Execution routine for [recoverInterruptedRunningTasks].
     *
     * What it does:
     * Coordinates recoverInterruptedRunningTasks operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun recoverInterruptedRunningTasks(): Int = taskRepo.recoverInterruptedRunningTasks()
    /**
     * What it is:
     * Execution routine for [postponeTask].
     *
     * What it does:
     * Coordinates postponeTask operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun postponeTask(id: Long, postponeMillis: Long): Boolean = taskRepo.postponeTask(id, postponeMillis)
    /**
     * What it is:
     * Execution routine for [postponeUpcomingTasks].
     *
     * What it does:
     * Coordinates postponeUpcomingTasks operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun postponeUpcomingTasks(postponeMillis: Long): List<ScheduledTask> = taskRepo.postponeUpcomingTasks(postponeMillis)

    // ==================== DAILY CAMPAIGN & DYNAMIC POOL ====================
    /**
     * What it is:
     * Execution routine for [getPendingPoolContactsForCampaign].
     *
     * What it does:
     * Coordinates getPendingPoolContactsForCampaign operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun getPendingPoolContactsForCampaign(campaignId: Long, isWhatsApp: Boolean = false, limit: Int = 100): List<DynamicContactItem> =
        dailyCampaignRepo.getPendingPoolContactsForCampaign(campaignId, isWhatsApp, limit)
    /**
     * What it is:
     * Execution routine for [markCampaignContactSuccess].
     *
     * What it does:
     * Coordinates markCampaignContactSuccess operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun markCampaignContactSuccess(campaignId: Long, contact: DynamicContactItem, channel: String, segments: Int = 1) =
        dailyCampaignRepo.markCampaignContactSuccess(campaignId, contact, channel, segments)
    /**
     * What it is:
     * Execution routine for [markCampaignContactDispatched].
     *
     * What it does:
     * Coordinates markCampaignContactDispatched operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun markCampaignContactDispatched(campaignId: Long, contact: DynamicContactItem, channel: String, segments: Int = 1) =
        dailyCampaignRepo.markCampaignContactDispatched(campaignId, contact, channel, segments)
    /**
     * What it is:
     * Execution routine for [getSentContactsForCampaign].
     *
     * What it does:
     * Coordinates getSentContactsForCampaign operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun getSentContactsForCampaign(campaignId: Long, limit: Int = 100): List<DynamicContactItem> =
        dailyCampaignRepo.getSentContactsForCampaign(campaignId, limit)
    /**
     * What it is:
     * Execution routine for [resetCampaignLedger].
     *
     * What it does:
     * Coordinates resetCampaignLedger operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun resetCampaignLedger(campaignId: Long) =
        dailyCampaignRepo.resetCampaignLedger(campaignId)
    /**
     * What it is:
     * Execution routine for [requeueContactForCampaign].
     *
     * What it does:
     * Coordinates requeueContactForCampaign operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun requeueContactForCampaign(campaignId: Long, contactId: Long) =
        dailyCampaignRepo.requeueContactForCampaign(campaignId, contactId)
    /**
     * What it is:
     * Execution routine for [getCampaignStats].
     *
     * What it does:
     * Coordinates getCampaignStats operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun getCampaignStats(campaignId: Long): DynamicPoolStats =
        dailyCampaignRepo.getCampaignStats(campaignId)
    /**
     * What it is:
     * Execution routine for [markContactSent].
     *
     * What it does:
     * Coordinates markContactSent operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun markContactSent(id: Long, whatsApp: Boolean = false) = dailyCampaignRepo.markContactSuccess(id, whatsApp)
    /**
     * What it is:
     * Execution routine for [markContactPendingRetry].
     *
     * What it does:
     * Coordinates markContactPendingRetry operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun markContactPendingRetry(id: Long, whatsApp: Boolean = false, status: String = "QUEUED") = dailyCampaignRepo.markContactPendingRetry(id, whatsApp, status)
    /**
     * What it is:
     * Execution routine for [markContactInvalid].
     *
     * What it does:
     * Coordinates markContactInvalid operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun markContactInvalid(id: Long, whatsApp: Boolean = false) = dailyCampaignRepo.markContactInvalid(id, whatsApp)
    /**
     * What it is:
     * Execution routine for [getDynamicPoolStats].
     *
     * What it does:
     * Coordinates getDynamicPoolStats operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun getDynamicPoolStats(whatsApp: Boolean = false): DynamicPoolStats {
        val (contacted, total) = dailyCampaignRepo.getPoolStats(whatsApp)
        return DynamicPoolStats(
            totalCount = total,
            unsentCount = total - contacted,
            sentCount = contacted,
        )
    }
    /**
     * What it is:
     * Execution routine for [getDynamicPool].
     *
     * What it does:
     * Coordinates getDynamicPool operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

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
    /**
     * What it is:
     * Execution routine for [clearDynamicPool].
     *
     * What it does:
     * Coordinates clearDynamicPool operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun clearDynamicPool() {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_DYNAMIC_POOL, null, null)
    }
    /**
     * What it is:
     * Execution routine for [deleteDynamicContact].
     *
     * What it does:
     * Coordinates deleteDynamicContact operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun deleteDynamicContact(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_DYNAMIC_POOL, "id = ?", arrayOf(id.toString())) > 0
    }
    /**
     * What it is:
     * Execution routine for [toggleDynamicContact].
     *
     * What it does:
     * Coordinates toggleDynamicContact operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun toggleDynamicContact(id: Long, enabled: Boolean): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
        }
        return db.update(DatabaseHelper.TABLE_DYNAMIC_POOL, cv, "id = ?", arrayOf(id.toString())) > 0
    }
    /**
     * What it is:
     * Execution routine for [upsertDynamicPoolBatch].
     *
     * What it does:
     * Coordinates upsertDynamicPoolBatch operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

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
    /**
     * What it is:
     * Execution routine for [getAllDailyCampaignConfigs].
     *
     * What it does:
     * Coordinates getAllDailyCampaignConfigs operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

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
    /**
     * What it is:
     * Execution routine for [getDailyCampaignConfig].
     *
     * What it does:
     * Coordinates getDailyCampaignConfig operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

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
    /**
     * What it is:
     * Execution routine for [insertDailyCampaignConfig].
     *
     * What it does:
     * Coordinates insertDailyCampaignConfig operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

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
    /**
     * What it is:
     * Execution routine for [saveDailyCampaignConfig].
     *
     * What it does:
     * Coordinates saveDailyCampaignConfig operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

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
    /**
     * What it is:
     * Execution routine for [deleteDailyCampaignConfig].
     *
     * What it does:
     * Coordinates deleteDailyCampaignConfig operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun deleteDailyCampaignConfig(id: Long): Boolean {
        if (id <= 3L) return false // Protect primary seed campaigns
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_DAILY_CAMPAIGN, "id = ?", arrayOf(id.toString())) > 0
    }
    /**
     * What it is:
     * Execution routine for [updateDailyCampaignLastRunDate].
     *
     * What it does:
     * Coordinates updateDailyCampaignLastRunDate operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun updateDailyCampaignLastRunDate(id: Long, date: String) {
        val config = getDailyCampaignConfig(id)
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("last_run_date", date) }
        val rows = db.update(DatabaseHelper.TABLE_DAILY_CAMPAIGN, cv, "id = ?", arrayOf(id.toString()))
        if (rows <= 0) {
            db.update(DatabaseHelper.TABLE_DAILY_CAMPAIGN, cv, "channel = ?", arrayOf(config.channel))
        }
    }
    /**
     * What it is:
     * Execution routine for [getSentCountForCampaignToday].
     *
     * What it does:
     * Coordinates getSentCountForCampaignToday operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun getSentCountForCampaignToday(id: Long): Int {
        return dailyCampaignRepo.getSentCountForCampaignToday(id)
    }
    /**
     * What it is:
     * Execution routine for [getSentSegmentsForCampaignToday].
     *
     * What it does:
     * Coordinates getSentSegmentsForCampaignToday operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun getSentSegmentsForCampaignToday(id: Long): Int = dailyCampaignRepo.getSentSegmentsForCampaignToday(id)
    /**
     * What it is:
     * Execution routine for [isRecipientDeliveredForTask].
     *
     * What it does:
     * Coordinates isRecipientDeliveredForTask operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun isRecipientDeliveredForTask(taskId: Long, recipient: String, occurrenceId: String = ""): Boolean =
        taskRepo.isRecipientDeliveredForTask(taskId, recipient, occurrenceId)
    /**
     * What it is:
     * Execution routine for [recordRecipientDelivery].
     *
     * What it does:
     * Coordinates recordRecipientDelivery operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun recordRecipientDelivery(taskId: Long, recipient: String, channel: String, status: String = "SUCCESS", occurrenceId: String = ""): Boolean =
        taskRepo.recordRecipientDelivery(taskId, recipient, channel, status, occurrenceId)
    /**
     * What it is:
     * Execution routine for [clearDeliveriesForTask].
     *
     * What it does:
     * Coordinates clearDeliveriesForTask operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun clearDeliveriesForTask(taskId: Long): Int =
        taskRepo.clearDeliveriesForTask(taskId)

    // ==================== AUTO REPLY ====================
    /**
     * What it is:
     * Execution routine for [insertAutoReplyRule].
     *
     * What it does:
     * Coordinates insertAutoReplyRule operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun insertAutoReplyRule(rule: AutoReplyRule): Long = autoReplyRepo.insertRule(rule)
    /**
     * What it is:
     * Execution routine for [updateAutoReplyRule].
     *
     * What it does:
     * Coordinates updateAutoReplyRule operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun updateAutoReplyRule(rule: AutoReplyRule): Boolean = autoReplyRepo.updateRule(rule)
    /**
     * What it is:
     * Execution routine for [getAllAutoReplyRules].
     *
     * What it does:
     * Coordinates getAllAutoReplyRules operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun getAllAutoReplyRules(): List<AutoReplyRule> = autoReplyRepo.getAllRules()
    /**
     * What it is:
     * Execution routine for [getEnabledAutoReplyRules].
     *
     * What it does:
     * Coordinates getEnabledAutoReplyRules operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun getEnabledAutoReplyRules(channel: ChannelType): List<AutoReplyRule> = autoReplyRepo.getEnabledRules(channel)
    /**
     * What it is:
     * Execution routine for [deleteAutoReplyRule].
     *
     * What it does:
     * Coordinates deleteAutoReplyRule operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun deleteAutoReplyRule(id: Long): Boolean = autoReplyRepo.deleteRule(id)
    /**
     * What it is:
     * Execution routine for [canReplyToNumber].
     *
     * What it does:
     * Coordinates canReplyToNumber operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun canReplyToNumber(rule: AutoReplyRule, phone: String): Boolean = autoReplyRepo.canReplyToNumber(rule, phone)
    /**
     * What it is:
     * Execution routine for [recordReplySent].
     *
     * What it does:
     * Coordinates recordReplySent operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun recordReplySent(ruleId: Long, phone: String) = autoReplyRepo.recordReplySent(ruleId, phone)

    // ==================== LOGS ====================
    /**
     * What it is:
     * Execution routine for [logExecution].
     *
     * What it does:
     * Coordinates logExecution operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun logExecution(taskId: Long = 0, channel: String, recipient: String, messageSnippet: String, status: String, details: String = "", segments: Int = 1): Long =
        logRepo.logExecution(taskId, channel, recipient, messageSnippet, status, details, segments)
    /**
     * What it is:
     * Execution routine for [insertLog].
     *
     * What it does:
     * Coordinates insertLog operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun insertLog(taskId: Long = 0, channel: String, recipient: String, messageSnippet: String, status: String, details: String = "", segments: Int = 1): Long =
        logExecution(taskId, channel, recipient, messageSnippet, status, details, segments)
    /**
     * What it is:
     * Execution routine for [insertLog].
     *
     * What it does:
     * Coordinates insertLog operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun insertLog(log: ExecutionLog): Long = logRepo.insertLog(log)
    /**
     * What it is:
     * Execution routine for [getAllLogs].
     *
     * What it does:
     * Coordinates getAllLogs operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun getAllLogs(limit: Int = 200): List<ExecutionLog> = logRepo.getAllLogs(limit)
    /**
     * What it is:
     * Execution routine for [clearLogs].
     *
     * What it does:
     * Coordinates clearLogs operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun clearLogs() = logRepo.clearAllLogs()
    /**
     * What it is:
     * Execution routine for [pruneOldLogs].
     *
     * What it does:
     * Coordinates pruneOldLogs operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun pruneOldLogs(maxCount: Int = LogRepository.MAX_LOG_RETENTION_COUNT, maxAgeDays: Int = LogRepository.MAX_LOG_RETENTION_DAYS): Int =
        logRepo.pruneOldLogs(maxCount, maxAgeDays)
    /**
     * What it is:
     * Execution routine for [getLogsForToday].
     *
     * What it does:
     * Coordinates getLogsForToday operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

    fun getLogsForToday(): List<ExecutionLog> {
        return logRepo.getLogsForToday()
    }
    /**
     * What it is:
     * Execution routine for [exportLogsToCsv].
     *
     * What it does:
     * Coordinates exportLogsToCsv operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */

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

    // ==================== RECIPIENTS ====================
    /**
     * What it is:
     * Execution routine for [getAllRecipients].
     *
     * What it does:
     * Coordinates getAllRecipients operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun getAllRecipients(): List<RecipientItem> = recipientRepo.getAllRecipients()
    /**
     * What it is:
     * Execution routine for [insertRecipient].
     *
     * What it does:
     * Coordinates insertRecipient operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun insertRecipient(recipient: RecipientItem): Long = recipientRepo.insertRecipient(recipient)
    /**
     * What it is:
     * Execution routine for [updateRecipient].
     *
     * What it does:
     * Coordinates updateRecipient operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun updateRecipient(recipient: RecipientItem): Boolean = recipientRepo.updateRecipient(recipient)
    /**
     * What it is:
     * Execution routine for [deleteRecipient].
     *
     * What it does:
     * Coordinates deleteRecipient operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun deleteRecipient(id: Long): Boolean = recipientRepo.deleteRecipient(id)
    /**
     * What it is:
     * Execution routine for [importContactsFromCsv].
     *
     * What it does:
     * Coordinates importContactsFromCsv operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun importContactsFromCsv(csvText: String): Int = recipientRepo.importContactsFromCsv(csvText)
    /**
     * What it is:
     * Execution routine for [exportContactsToCsv].
     *
     * What it does:
     * Coordinates exportContactsToCsv operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun exportContactsToCsv(): String = recipientRepo.exportContactsToCsv()

    // ==================== WHATSAPP CONTACTS ====================
    /**
     * What it is:
     * Execution routine for [isValidPickedChatName].
     *
     * What it does:
     * Coordinates isValidPickedChatName operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun isValidPickedChatName(name: String): Boolean = whatsAppRepo.isValidPickedChatName(name)
    /**
     * What it is:
     * Execution routine for [findPhoneForContactName].
     *
     * What it does:
     * Coordinates findPhoneForContactName operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun findPhoneForContactName(name: String): String? = whatsAppRepo.findPhoneForContactName(name)
    /**
     * What it is:
     * Execution routine for [hasCellularPhoneContact].
     *
     * What it does:
     * Coordinates hasCellularPhoneContact operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun hasCellularPhoneContact(name: String): Boolean = whatsAppRepo.hasCellularPhoneContact(name)
    /**
     * What it is:
     * Execution routine for [findPhotoForContactName].
     *
     * What it does:
     * Coordinates findPhotoForContactName operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun findPhotoForContactName(name: String): String = whatsAppRepo.findPhotoForContactName(name)
    /**
     * What it is:
     * Execution routine for [findContactByPhone].
     *
     * What it does:
     * Coordinates findContactByPhone operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun findContactByPhone(phone: String): Pair<String, String>? = whatsAppRepo.findContactByPhone(phone)
    /**
     * What it is:
     * Execution routine for [getWhatsAppContacts].
     *
     * What it does:
     * Coordinates getWhatsAppContacts operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun getWhatsAppContacts(query: String = "", channel: ChannelType = ChannelType.WHATSAPP, limit: Int = 10000): List<WhatsAppContactItem> =
        whatsAppRepo.getWhatsAppContacts(query, channel, limit)

    // ==================== WHATSAPP DATA CLEANUP ====================
    /**
     * What it is:
     * Execution routine for [deleteAllWhatsAppGroups].
     *
     * What it does:
     * Coordinates deleteAllWhatsAppGroups operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun deleteAllWhatsAppGroups(source: String? = null): Int = whatsAppRepo.deleteAllWhatsAppGroups(source)
    /**
     * What it is:
     * Execution routine for [purgeCorruptedGroupsFromPool].
     *
     * What it does:
     * Coordinates purgeCorruptedGroupsFromPool operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun purgeCorruptedGroupsFromPool(): Int = whatsAppRepo.purgeCorruptedGroupsFromPool()
    /**
     * What it is:
     * Execution routine for [purgeAllGroupsFromPool].
     *
     * What it does:
     * Coordinates purgeAllGroupsFromPool operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun purgeAllGroupsFromPool(): Int = whatsAppRepo.purgeAllGroupsFromPool()

    // ==================== BACKUP ====================
    /**
     * What it is:
     * Execution routine for [exportDatabaseToJson].
     *
     * What it does:
     * Coordinates exportDatabaseToJson operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun exportDatabaseToJson(): String = backupRepo.exportDatabaseToJson()
    /**
     * What it is:
     * Execution routine for [importDatabaseFromJson].
     *
     * What it does:
     * Coordinates importDatabaseFromJson operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun importDatabaseFromJson(jsonStr: String): Result<String> = backupRepo.importDatabaseFromJson(jsonStr)
    /**
     * What it is:
     * Execution routine for [exportDatabaseToLocalDirectory].
     *
     * What it does:
     * Coordinates exportDatabaseToLocalDirectory operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun exportDatabaseToLocalDirectory(directory: java.io.File): Result<java.io.File> = backupRepo.exportDatabaseToLocalDirectory(directory)
    /**
     * What it is:
     * Execution routine for [listLocalBackups].
     *
     * What it does:
     * Coordinates listLocalBackups operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppRepository]
     */
    fun listLocalBackups(directory: java.io.File): List<java.io.File> = backupRepo.listLocalBackups(directory)
}
