package com.jmd.autotext.data.repository

import android.content.Context
import android.database.Cursor
import android.util.Log
import com.jmd.autotext.data.DatabaseHelper
import com.jmd.autotext.model.DynamicContactItem

class DailyCampaignRepository(appContext: Context) {

    private val dbHelper = DatabaseHelper.getInstance(appContext)
    private val tag = "DailyCampaignRepo"

    fun getPendingPoolContact(whatsApp: Boolean = false): DynamicContactItem? {
        val list = getPendingPoolContactsBatch(whatsApp = whatsApp, limit = 1)
        return list.firstOrNull()
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

    fun getSentContactsLedger(whatsApp: Boolean = false, limit: Int = 100): List<DynamicContactItem> {
        val list = mutableListOf<DynamicContactItem>()
        try {
            val db = dbHelper.readableDatabase
            val countCol = if (whatsApp) "wa_send_count" else "send_count"
            val timeCol = if (whatsApp) "wa_last_sent_at" else "last_sent_at"
            val query = """
                SELECT id, name, phone, normalized_number, source, enabled, last_sent_at, send_count, created_at, wa_last_sent_at, wa_send_count, IFNULL(sms_status, 'PENDING'), IFNULL(wa_status, 'PENDING'), IFNULL(photo_uri, '')
                FROM ${DatabaseHelper.TABLE_DYNAMIC_POOL}
                WHERE IFNULL($countCol, 0) > 0
                ORDER BY $timeCol DESC
                LIMIT ?
            """.trimIndent()
            db.rawQuery(query, arrayOf(limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursorToDynamicContactItem(cursor))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error loading sent contacts ledger", e)
        }
        return list
    }

    fun resetDailyCampaignLedger(whatsApp: Boolean = false) {
        try {
            val db = dbHelper.writableDatabase
            if (whatsApp) {
                db.execSQL("UPDATE ${DatabaseHelper.TABLE_DYNAMIC_POOL} SET wa_send_count = 0, wa_last_sent_at = 0, last_wa_sent = 0, wa_sent_count = 0, wa_status = 'PENDING', wa_retry_priority = 0")
            } else {
                db.execSQL("UPDATE ${DatabaseHelper.TABLE_DYNAMIC_POOL} SET send_count = 0, last_sent_at = 0, last_sms_sent = 0, sms_sent_count = 0, sms_status = 'PENDING', sms_retry_priority = 0")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error resetting daily campaign ledger", e)
        }
    }

    fun resetContactLedger(id: Long, whatsApp: Boolean = false) {
        try {
            val db = dbHelper.writableDatabase
            if (whatsApp) {
                db.execSQL("UPDATE ${DatabaseHelper.TABLE_DYNAMIC_POOL} SET wa_send_count = 0, wa_last_sent_at = 0, last_wa_sent = 0, wa_sent_count = 0, wa_status = 'PENDING', wa_retry_priority = 0 WHERE id = ?", arrayOf<Any>(id))
            } else {
                db.execSQL("UPDATE ${DatabaseHelper.TABLE_DYNAMIC_POOL} SET send_count = 0, last_sent_at = 0, last_sms_sent = 0, sms_sent_count = 0, sms_status = 'PENDING', sms_retry_priority = 0 WHERE id = ?", arrayOf<Any>(id))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error resetting contact ledger", e)
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

