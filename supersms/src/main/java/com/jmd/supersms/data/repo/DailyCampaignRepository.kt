/**
 * Package: com.jmd.supersms.data.repo
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [DailyCampaignRepository].
 *
 * What it does:
 * Governs dynamic contact pool filtering, daily quota pacing, campaign ledger recording, and unique cycle deduplication for high-volume scheduled outreach.
 *
 * Linked With:
 * - Repositories: [android.database.sqlite.SQLiteDatabase], [com.jmd.supersms.data.db.DatabaseHelper]
 * - Models & Entities: [com.jmd.supersms.data.model.DynamicContactItem], [com.jmd.supersms.data.model.DynamicPoolStats]
 * - Core Security & Utilities: [android.util.Log], [java.util.*]
 * - Android Framework & Jetpack: [android.database.Cursor]
 */

package com.jmd.supersms.data.repo

import android.content.*
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.jmd.supersms.core.AppDataEvents
import com.jmd.supersms.data.db.DatabaseHelper
import com.jmd.supersms.data.model.DynamicContactItem
import com.jmd.supersms.data.model.DynamicPoolStats
import java.util.*

/**
 * What it is:
 * Repository managing [DynamicContactItem] pools and daily campaign dispatches across SMS channels.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [DailyCampaignRepository].
 *
 * Linked with:
 * - Architecture: [DailyCampaignRepository], [DailyCampaignRepository]
 */
class DailyCampaignRepository(appContext: Context) {

    private val dbHelper = DatabaseHelper.getInstance(appContext)
    private val tag = "DailyCampaignRepo"

    /**
     * What it is:
     * Retrieves eligible pending contacts from dynamic pool for campaign execution, excluding contacts previously dispatched in this campaign.
     *
     * What it does:
     * Executes [getPendingPoolContactsForCampaign] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignRepository], [getPendingPoolContactsForCampaign]
     * @param campaignId Identifier of the active daily campaign.
     * @param limit Maximum contacts to fetch for this batch.
     * @return List of candidate [DynamicContactItem] recipients.
     */
    fun getPendingPoolContactsForCampaign(campaignId: Long, limit: Int = 100): List<DynamicContactItem> {
        val list = mutableListOf<DynamicContactItem>()
        try {
            val db = dbHelper.readableDatabase
            val statusCol = "sms_status"
            val priorityCol = "sms_retry_priority"
            val channelFilter = "AND normalized_number NOT LIKE 'group:%' AND normalized_number NOT LIKE 'broadcast:%' AND normalized_number NOT LIKE 'chat:%'"

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
     * What it is:
     * Records a successful campaign delivery in the sent ledger and updates the contact's send counts.
     *
     * What it does:
     * Executes [markCampaignContactSuccess] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignRepository], [markCampaignContactSuccess]
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

            markContactSuccess(contact.id)
            AppDataEvents.notifyCampaignsChanged()
        } catch (e: Exception) {
            Log.e(tag, "Error marking campaign contact success", e)
        }
    }

    /**
     * What it is:
     * Records a dispatched but unconfirmed carrier dispatch in the campaign sent ledger.
     *
     * What it does:
     * Executes [markCampaignContactDispatched] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignRepository], [markCampaignContactDispatched]
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
            AppDataEvents.notifyCampaignsChanged()
        } catch (e: Exception) {
            Log.e(tag, "Error marking campaign contact dispatched", e)
        }
    }

    /**
     * What it is:
     * Fetches recently sent contacts for a campaign to display in the sent ledger feed.
     *
     * What it does:
     * Executes [getSentContactsForCampaign] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignRepository], [getSentContactsForCampaign]
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
    /**
     * What it is:
     * Execution routine for [resetCampaignLedger].
     *
     * What it does:
     * Coordinates resetCampaignLedger operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DailyCampaignRepository]
     */

    fun resetCampaignLedger(campaignId: Long) {
        try {
            val db = dbHelper.writableDatabase
            db.delete(DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT, "campaign_id = ?", arrayOf(campaignId.toString()))
            AppDataEvents.notifyCampaignsChanged()
        } catch (e: Exception) {
            Log.e(tag, "Error resetting campaign ledger for $campaignId", e)
        }
    }

    /**
     * What it is:
     * Removes a contact from the campaign's sent ledger so they become eligible for re-dispatch.
     *
     * What it does:
     * Executes [requeueContactForCampaign] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignRepository], [requeueContactForCampaign]
     * @param campaignId Campaign identifier.
     * @param contactId Unique ID of the contact to requeue.
     */
    fun requeueContactForCampaign(campaignId: Long, contactId: Long) {
        try {
            val db = dbHelper.writableDatabase
            db.delete(DatabaseHelper.TABLE_DAILY_CAMPAIGN_SENT, "campaign_id = ? AND contact_id = ?", arrayOf(campaignId.toString(), contactId.toString()))
            AppDataEvents.notifyCampaignsChanged()
        } catch (e: Exception) {
            Log.e(tag, "Error requeuing contact $contactId for campaign $campaignId", e)
        }
    }

    /**
     * What it is:
     * Calculates campaign pool metrics including total eligible pool count, sent count, and remaining unsent count.
     *
     * What it does:
     * Executes [getCampaignStats] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignRepository], [getCampaignStats]
     * @param campaignId Target campaign ID.
     * @return [DynamicPoolStats] summary.
     */
    fun getCampaignStats(campaignId: Long): DynamicPoolStats {
        var total = 0
        var sent = 0
        try {
            val db = dbHelper.readableDatabase
            val channelFilter = "AND normalized_number NOT LIKE 'group:%' AND normalized_number NOT LIKE 'broadcast:%' AND normalized_number NOT LIKE 'chat:%'"

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
     * What it is:
     * Counts the total number of contacts reached by a campaign during the current calendar day.
     *
     * What it does:
     * Executes [getSentCountForCampaignToday] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [DailyCampaignRepository], [getSentCountForCampaignToday]
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
    /**
     * What it is:
     * Execution routine for [getSentSegmentsForCampaignToday].
     *
     * What it does:
     * Coordinates getSentSegmentsForCampaignToday operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DailyCampaignRepository]
     */

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
    /**
     * What it is:
     * Execution routine for [getPendingPoolContactsBatch].
     *
     * What it does:
     * Coordinates getPendingPoolContactsBatch operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DailyCampaignRepository]
     */

    fun getPendingPoolContactsBatch(limit: Int = 50000): List<DynamicContactItem> {
        val list = mutableListOf<DynamicContactItem>()
        try {
            val db = dbHelper.readableDatabase
            val statusCol = "sms_status"
            val countCol = "send_count"
            val priorityCol = "sms_retry_priority"

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

    fun markContactSuccess(id: Long) {
        try {
            val db = dbHelper.writableDatabase
            val now = System.currentTimeMillis()
            val sql = "UPDATE ${DatabaseHelper.TABLE_DYNAMIC_POOL} SET last_sent_at = ?, last_sms_sent = ?, send_count = IFNULL(send_count, 0) + 1, sms_sent_count = IFNULL(sms_sent_count, 0) + 1, sms_retry_priority = 0, sms_status = 'SUCCESS' WHERE id = ?"
            db.execSQL(sql, arrayOf<Any>(now, now, id))
        } catch (e: Exception) {
            Log.e(tag, "Error marking contact success", e)
        }
    }

    fun markContactPendingRetry(id: Long, status: String = "QUEUED") {
        try {
            val db = dbHelper.writableDatabase
            val sql = "UPDATE ${DatabaseHelper.TABLE_DYNAMIC_POOL} SET sms_retry_priority = 1, sms_status = ? WHERE id = ?"
            db.execSQL(sql, arrayOf<Any>(status, id))
        } catch (e: Exception) {
            Log.e(tag, "Error marking contact pending retry", e)
        }
    }

    fun markContactInvalid(id: Long) {
        try {
            val db = dbHelper.writableDatabase
            val now = System.currentTimeMillis()
            val sql = "UPDATE ${DatabaseHelper.TABLE_DYNAMIC_POOL} SET last_sent_at = ?, last_sms_sent = ?, sms_retry_priority = 0, sms_status = 'INVALID' WHERE id = ?"
            db.execSQL(sql, arrayOf<Any>(now, now, id))
        } catch (e: Exception) {
            Log.e(tag, "Error marking contact invalid", e)
        }
    }

    fun getPoolStats(): Pair<Int, Int> {
        var total = 0
        var contacted = 0
        try {
            val db = dbHelper.readableDatabase
            db.rawQuery(
                "SELECT COUNT(*), SUM(CASE WHEN IFNULL(send_count, 0) > 0 THEN 1 ELSE 0 END) FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL}",
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
    /**
     * What it is:
     * Execution routine for [cursorToDynamicContactItem].
     *
     * What it does:
     * Coordinates cursorToDynamicContactItem operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [DailyCampaignRepository]
     */

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
