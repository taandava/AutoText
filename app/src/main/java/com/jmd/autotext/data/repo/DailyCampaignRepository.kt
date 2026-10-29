

/**
 * Package: com.jmd.autotext.data.repo
 * Module: Data Access Layer
 *
 * Governs dynamic contact pool filtering, daily quota pacing, campaign ledger recording,
 * and unique cycle deduplication for high-volume automated outreach.
 *
 * Linked With:
 * - Database: [com.jmd.autotext.data.db.DatabaseHelper]
 * - Models: [com.jmd.autotext.data.model.DynamicContactItem], [com.jmd.autotext.data.model.DynamicPoolStats]
 * - Scheduler: [com.jmd.autotext.scheduler.DailyCampaignScheduler], [com.jmd.autotext.scheduler.DailyCampaignWorker]
 * - UI Screens: `DailyCampaignScreen`
 */
package com.jmd.autotext.data.repo

import android.content.*
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.jmd.autotext.data.db.DatabaseHelper
import com.jmd.autotext.data.model.DynamicContactItem
import com.jmd.autotext.data.model.DynamicPoolStats
import java.util.*

/**
 * Repository managing [DynamicContactItem] pools and daily campaign dispatches across SMS and WhatsApp channels.
 */
class DailyCampaignRepository(appContext: Context) {

    private val dbHelper = DatabaseHelper.getInstance(appContext)
    private val tag = "DailyCampaignRepo"

    /**
     * Retrieves eligible pending contacts from dynamic pool for campaign execution,
     * excluding contacts previously dispatched in this campaign and filtering by channel type.
     *
     * @param campaignId Identifier of the active daily campaign.
     * @param isWhatsApp True if querying for WhatsApp/W4B delivery, false for SMS.
     * @param limit Maximum contacts to fetch for this batch.
     * @return List of candidate [DynamicContactItem] recipients.
     */
    fun getPendingPoolContactsForCampaign(campaignId: Long, isWhatsApp: Boolean = false, limit: Int = 100): List<DynamicContactItem> {
        val list = mutableListOf<DynamicContactItem>()
        try {
            val db = dbHelper.readableDatabase
            val statusCol = if (isWhatsApp) "wa_status" else "sms_status"
            val priorityCol = if (isWhatsApp) "wa_retry_priority" else "sms_retry_priority"

            // Look up campaign config to check exact channel
            var channel = if (isWhatsApp) "WHATSAPP" else "SMS"
            db.rawQuery("SELECT channel FROM ${DatabaseHelper.TABLE_DAILY_CAMPAIGN} WHERE id = ? LIMIT 1", arrayOf(campaignId.toString())).use {
                if (it.moveToFirst()) channel = it.getString(0) ?: channel
            }
            val isBusiness = channel.contains("BUSINESS", ignoreCase = true)

            val channelFilter = when {
                !isWhatsApp -> "AND normalized_number NOT LIKE 'group:%' AND normalized_number NOT LIKE 'broadcast:%' AND normalized_number NOT LIKE 'chat:%'"
                isBusiness -> "AND (source = 'WA_BUSINESS' OR normalized_number LIKE 'group:%' OR source IN ('WA_CHAT', 'CONTACT'))"
                else -> "AND (source IN ('WA_CHAT', 'WA_GROUP', 'CONTACT') OR normalized_number LIKE 'group:%')"
            }

            val query = """
                SELECT id, name, phone, normalized_number, source, enabled, last_sent_at, send_count, created_at, wa_last_sent_at, wa_send_count, IFNULL(sms_status, 'PENDING'), IFNULL(wa_status, 'PENDING'), IFNULL(photo_uri, '')
                FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL}
                WHERE enabled = 1
                  $channelFilter
                  AND normalized_number NOT IN (
                      SELECT normalized_number FROM ${DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT}
                      WHERE campaign_id = ? AND status IN ('SUCCESS', 'DISPATCHED')
                  )
                  AND IFNULL($statusCol, '') != 'INVALID'
                ORDER BY IFNULL($priorityCol, 0) DESC,
                         CASE WHEN source = 'WA_BUSINESS' AND $isBusiness THEN 0
                              WHEN source = 'WA_CHAT' THEN 1
                              WHEN source = 'WA_GROUP' THEN 2
                              ELSE 3 END,
                         id ASC
                LIMIT ?
            """.trimIndent()

            db.rawQuery(query, arrayOf(campaignId.toString(), limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursorToDynamicContactItem(cursor))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error loading pending pool contacts for campaign $campaignId", e)
        }
        return list
    }

    /**
     * Records a successful campaign delivery in the sent ledger and updates the contact's send counts.
     *
     * @param campaignId Campaign identifier.
     * @param contact Target recipient.
     * @param channel Delivery channel name.
     * @param segments Count of SMS segments or message units consumed.
     */
    fun markCampaignContactSuccess(campaignId: Long, contact: DynamicContactItem, channel: String, segments: Int = 1) {
        try {
            val db = dbHelper.writableDatabase
            val now = System.currentTimeMillis()
            val cv = ContentValues().apply {
                put("campaign_id", campaignId)
                put("contact_id", contact.id)
                put("phone", contact.phone)
                put("normalized_number", contact.normalizedNumber)
                put("channel", channel)
                put("sent_at", now)
                put("status", "SUCCESS")
                put("segments", maxOf(1, segments))
            }
            db.insertWithOnConflict(DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

            val isWa = channel.contains("WHATSAPP", ignoreCase = true)
            markContactSuccess(contact.id, isWa)
        } catch (e: Exception) {
            Log.e(tag, "Error marking campaign contact success", e)
        }
    }

    /**
     * Records a dispatched but unconfirmed carrier dispatch in the campaign sent ledger.
     *
     * @param campaignId Campaign identifier.
     * @param contact Target recipient.
     * @param channel Delivery channel name.
     * @param segments Number of segments sent to modem.
     */
    fun markCampaignContactDispatched(campaignId: Long, contact: DynamicContactItem, channel: String, segments: Int = 1) {
        try {
            val db = dbHelper.writableDatabase
            val now = System.currentTimeMillis()
            val cv = ContentValues().apply {
                put("campaign_id", campaignId)
                put("contact_id", contact.id)
                put("phone", contact.phone)
                put("normalized_number", contact.normalizedNumber)
                put("channel", channel)
                put("sent_at", now)
                put("status", "DISPATCHED")
                put("segments", maxOf(1, segments))
            }
            db.insertWithOnConflict(DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Log.e(tag, "Error marking campaign contact dispatched", e)
        }
    }

    /**
     * Fetches recently sent contacts for a campaign to display in the sent ledger feed.
     *
     * @param campaignId Target campaign ID.
     * @param limit Maximum rows to return.
     * @return List of dispatched [DynamicContactItem] models.
     */
    fun getSentContactsForCampaign(campaignId: Long, limit: Int = 100): List<DynamicContactItem> {
        val list = mutableListOf<DynamicContactItem>()
        try {
            val db = dbHelper.readableDatabase
            val query = """
                SELECT p.id, p.name, p.phone, p.normalized_number, p.source, p.enabled, s.sent_at, p.send_count, p.created_at, s.sent_at, p.wa_send_count, IFNULL(p.sms_status, 'PENDING'), IFNULL(p.wa_status, 'PENDING'), IFNULL(p.photo_uri, '')
                FROM ${DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT} s
                INNER JOIN ${DatabaseHelper.TABLE_DYNAMIC_POOL} p ON s.normalized_number = p.normalized_number
                WHERE s.campaign_id = ? AND s.status = 'SUCCESS'
                ORDER BY s.sent_at DESC
                LIMIT ?
            """.trimIndent()
            db.rawQuery(query, arrayOf(campaignId.toString(), limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursorToDynamicContactItem(cursor))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error loading sent contacts for campaign $campaignId", e)
        }
        return list
    }

    fun resetCampaignLedger(campaignId: Long) {
        try {
            val db = dbHelper.writableDatabase
            db.delete(DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT, "campaign_id = ?", arrayOf(campaignId.toString()))
        } catch (e: Exception) {
            Log.e(tag, "Error resetting campaign ledger for $campaignId", e)
        }
    }

    /**
     * Removes a contact from the campaign's sent ledger so they become eligible for re-dispatch.
     *
     * @param campaignId Campaign identifier.
     * @param contactId Unique ID of the contact to requeue.
     */
    fun requeueContactForCampaign(campaignId: Long, contactId: Long) {
        try {
            val db = dbHelper.writableDatabase
            db.delete(DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT, "campaign_id = ? AND contact_id = ?", arrayOf(campaignId.toString(), contactId.toString()))
        } catch (e: Exception) {
            Log.e(tag, "Error requeuing contact $contactId for campaign $campaignId", e)
        }
    }

    /**
     * Calculates campaign pool metrics including total eligible pool count, sent count, and remaining unsent count.
     *
     * @param campaignId Target campaign ID.
     * @return [DynamicPoolStats] summary.
     */
    fun getCampaignStats(campaignId: Long): DynamicPoolStats {
        var total = 0
        var sent = 0
        try {
            val db = dbHelper.readableDatabase
            var channel = "SMS"
            db.rawQuery("SELECT channel FROM ${DatabaseHelper.TABLE_DAILY_CAMPAIGN} WHERE id = ? LIMIT 1", arrayOf(campaignId.toString())).use {
                if (it.moveToFirst()) channel = it.getString(0) ?: channel
            }
            val isWa = channel.contains("WHATSAPP", ignoreCase = true)
            val channelFilter = if (!isWa) {
                "AND normalized_number NOT LIKE 'group:%' AND normalized_number NOT LIKE 'broadcast:%' AND normalized_number NOT LIKE 'chat:%'"
            } else ""

            db.rawQuery("SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL} WHERE enabled = 1 $channelFilter", null).use {
                if (it.moveToFirst()) total = it.getInt(0)
            }
            db.rawQuery("SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT} WHERE campaign_id = ? AND status = 'SUCCESS'", arrayOf(campaignId.toString())).use {
                if (it.moveToFirst()) sent = it.getInt(0)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting stats for campaign $campaignId", e)
        }
        return DynamicPoolStats(totalCount = total, sentCount = sent, unsentCount = maxOf(0, total - sent))
    }

    /**
     * Counts the total number of contacts reached by a campaign during the current calendar day.
     *
     * @param campaignId Target campaign ID.
     * @return Count of successfully dispatched recipients today.
     */
    fun getSentCountForCampaignToday(campaignId: Long): Int {
        var count = 0
        try {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = cal.timeInMillis
            val db = dbHelper.readableDatabase
            db.rawQuery(
                "SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT} WHERE campaign_id = ? AND sent_at >= ? AND status IN ('SUCCESS', 'DISPATCHED', 'PARTIAL')",
                arrayOf(campaignId.toString(), startOfDay.toString())
            ).use {
                if (it.moveToFirst()) count = it.getInt(0)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting today sent count for campaign $campaignId", e)
        }
        return count
    }

    fun getSentSegmentsForCampaignToday(campaignId: Long): Int {
        var segments = 0
        try {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = cal.timeInMillis
            val db = dbHelper.readableDatabase
            db.rawQuery(
                "SELECT COALESCE(SUM(COALESCE(segments, 1)), 0) FROM ${DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT} WHERE campaign_id = ? AND sent_at >= ? AND status IN ('SUCCESS', 'DISPATCHED', 'PARTIAL')",
                arrayOf(campaignId.toString(), startOfDay.toString())
            ).use {
                if (it.moveToFirst()) segments = it.getInt(0)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting today sent segments for campaign $campaignId", e)
        }
        return segments
    }

    fun getPendingPoolContactsBatch(whatsApp: Boolean = false, limit: Int = 100): List<DynamicContactItem> {
        val list = mutableListOf<DynamicContactItem>()
        try {
            val db = dbHelper.readableDatabase
            val statusCol = if (whatsApp) "wa_status" else "sms_status"
            val countCol = if (whatsApp) "wa_send_count" else "send_count"
            val priorityCol = if (whatsApp) "wa_retry_priority" else "sms_retry_priority"

            val query = """
                SELECT id, name, phone, normalized_number, source, enabled, last_sent_at, send_count, created_at, wa_last_sent_at, wa_send_count, IFNULL(sms_status, 'PENDING'), IFNULL(wa_status, 'PENDING'), IFNULL(photo_uri, '')
                FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL}
                WHERE enabled = 1
                  AND IFNULL($countCol, 0) = 0
                  AND IFNULL($statusCol, '') != 'INVALID'
                ORDER BY IFNULL($priorityCol, 0) DESC, id ASC
                LIMIT ?
            """.trimIndent()

            db.rawQuery(query, arrayOf(limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursorToDynamicContactItem(cursor))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error loading pending pool contacts batch", e)
        }
        return list
    }

    fun markContactSuccess(id: Long, whatsApp: Boolean = false) {
        try {
            val db = dbHelper.writableDatabase
            val col = if (whatsApp) "wa_last_sent_at" else "last_sent_at"
            val altCol = if (whatsApp) "last_wa_sent" else "last_sms_sent"
            val cnt = if (whatsApp) "wa_send_count" else "send_count"
            val altCnt = if (whatsApp) "wa_sent_count" else "sms_sent_count"
            val priorityCol = if (whatsApp) "wa_retry_priority" else "sms_retry_priority"
            val statusCol = if (whatsApp) "wa_status" else "sms_status"

            val now = System.currentTimeMillis()
            val sql = "UPDATE ${DatabaseHelper.TABLE_DYNAMIC_POOL} SET $col = ?, $altCol = ?, $cnt = IFNULL($cnt, 0) + 1, $altCnt = IFNULL($altCnt, 0) + 1, $priorityCol = 0, $statusCol = 'SUCCESS' WHERE id = ?"
            db.execSQL(sql, arrayOf<Any>(now, now, id))
        } catch (e: Exception) {
            Log.e(tag, "Error marking contact success", e)
        }
    }

    fun markContactPendingRetry(id: Long, whatsApp: Boolean = false, status: String = "QUEUED") {
        try {
            val db = dbHelper.writableDatabase
            val priorityCol = if (whatsApp) "wa_retry_priority" else "sms_retry_priority"
            val statusCol = if (whatsApp) "wa_status" else "sms_status"

            val sql = "UPDATE ${DatabaseHelper.TABLE_DYNAMIC_POOL} SET $priorityCol = 1, $statusCol = ? WHERE id = ?"
            db.execSQL(sql, arrayOf<Any>(status, id))
        } catch (e: Exception) {
            Log.e(tag, "Error marking contact pending retry", e)
        }
    }

    fun markContactInvalid(id: Long, whatsApp: Boolean = false) {
        try {
            val db = dbHelper.writableDatabase
            val col = if (whatsApp) "wa_last_sent_at" else "last_sent_at"
            val altCol = if (whatsApp) "last_wa_sent" else "last_sms_sent"
            val priorityCol = if (whatsApp) "wa_retry_priority" else "sms_retry_priority"
            val statusCol = if (whatsApp) "wa_status" else "sms_status"

            val now = System.currentTimeMillis()
            val sql = "UPDATE ${DatabaseHelper.TABLE_DYNAMIC_POOL} SET $col = ?, $altCol = ?, $priorityCol = 0, $statusCol = 'INVALID' WHERE id = ?"
            db.execSQL(sql, arrayOf<Any>(now, now, id))
        } catch (e: Exception) {
            Log.e(tag, "Error marking contact invalid", e)
        }
    }

    fun getPoolStats(whatsApp: Boolean = false): Pair<Int, Int> {
        var total = 0
        var contacted = 0
        try {
            val db = dbHelper.readableDatabase
            val cntCol = if (whatsApp) "wa_send_count" else "send_count"
            db.rawQuery(
                "SELECT COUNT(*), SUM(CASE WHEN IFNULL($cntCol, 0) > 0 THEN 1 ELSE 0 END) FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL}",
                null
            ).use {
                if (it.moveToFirst()) {
                    total = it.getInt(0)
                    contacted = it.getInt(1)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error in getPoolStats", e)
        }
        return Pair(contacted, total)
    }

    private fun cursorToDynamicContactItem(c: Cursor): DynamicContactItem {
        return DynamicContactItem(
            id = c.getLong(0),
            name = c.getString(1).orEmpty(),
            phone = c.getString(2).orEmpty(),
            normalizedNumber = c.getString(3).orEmpty(),
            source = c.getString(4).orEmpty(),
            enabled = c.getInt(5) == 1,
            lastSentAt = c.getLong(6),
            sendCount = c.getInt(7),
            createdAt = c.getLong(8),
            waLastSentAt = c.getLong(9),
            waSendCount = c.getInt(10),
            smsStatus = c.getString(11).orEmpty(),
            waStatus = c.getString(12).orEmpty(),
            photoUri = if (c.columnCount > 13) c.getString(13).orEmpty() else ""
        )
    }
}
