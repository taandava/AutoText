/**
 * Package: com.jmd.helper.data.repo
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [LogRepository].
 *
 * What it does:
 * Persists and queries task execution logs for SMS, WhatsApp, and call actions. Maintains execution history with timestamps, delivery status, and failure diagnostics.
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.db.DatabaseHelper]
 * - Models & Entities: [com.jmd.helper.data.model.ExecutionLog]
 * - Android Framework & Jetpack: [android.database.Cursor]
 */

package com.jmd.helper.data.repo

import android.content.*
import android.database.Cursor
import com.jmd.helper.data.db.DatabaseHelper
import com.jmd.helper.data.model.ExecutionLog

/**
 * What it is:
 * Repository for logging execution outcomes and querying diagnostic event history.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [LogRepository].
 *
 * Linked with:
 * - Architecture: [LogRepository], [LogRepository]
 * @param context Application context.
 */
class LogRepository(private val context: Context) {
    /**
     * What it is:
     * Companion object constant and factory provider for [LogRepository].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [LogRepository]
     */

    companion object {
        private const val TAG = "LogRepository"
        const val MAX_LOG_RETENTION_COUNT = 5000
        const val MAX_LOG_RETENTION_DAYS = 45
        private val insertCounter = java.util.concurrent.atomic.AtomicInteger(0)
    }

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * What it is:
     * Enforces rolling log retention policy: 1.
     *
     * What it does:
     * Removes records older than [maxAgeDays] (default 45 days). 2. Caps maximum total log rows to [maxCount] (default 5,000 entries).
     *
     * Linked with:
     * - Architecture: [LogRepository], [pruneOldLogs]
     * @param maxCount Maximum number of recent log entries to retain.
     * @param maxAgeDays Maximum age of logs in days before eviction.
     * @return Number of pruned historical log records.
     */
    fun pruneOldLogs(maxCount: Int = MAX_LOG_RETENTION_COUNT, maxAgeDays: Int = MAX_LOG_RETENTION_DAYS): Int {
        val db = dbHelper.writableDatabase
        return try {
            val cutoff = System.currentTimeMillis() - (maxAgeDays * 86_400_000L)
            val deletedOld = db.delete(DatabaseHelper.TABLE_LOGS, "timestamp < ?", arrayOf(cutoff.toString()))
            val deletedExceeding = db.delete(
                DatabaseHelper.TABLE_LOGS,
                "id NOT IN (SELECT id FROM ${DatabaseHelper.TABLE_LOGS} ORDER BY timestamp DESC LIMIT ?)",
                arrayOf(maxCount.toString())
            )
            val total = deletedOld + deletedExceeding
            if (total > 0) {
                android.util.Log.d(TAG, "Pruned $total historical logs (older than $maxAgeDays days or exceeding $maxCount cap)")
            }
            total
        } catch (e: Exception) {
            android.util.Log.w(TAG, "pruneOldLogs failed: ${e.message}")
            0
        }
    }

    /**
     * What it is:
     * Persists an execution diagnostic event with channel, recipient, status, and SMS part count.
     *
     * What it does:
     * Executes [logExecution] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [LogRepository], [logExecution]
     * @param taskId Associated task ID (0 if autonomous auto-reply or campaign).
     * @param channel Channel name string.
     * @param recipient Target destination phone or contact string.
     * @param messageSnippet Brief preview of message body.
     * @param status Outcome state string (e.g. "SUCCESS", "FAILED", "DISPATCHED").
     * @param details Additional failure explanation or delivery metrics.
     * @param segments Count of segments/parts consumed.
     * @return Generated database row ID.
     */
    fun logExecution(
        taskId: Long = 0,
        channel: String,
        recipient: String,
        messageSnippet: String,
        status: String,
        details: String = "",
        segments: Int = 1
    ): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("task_id", taskId)
            put("channel", channel)
            put("recipient", recipient)
            put("message_snippet", messageSnippet)
            put("status", status)
            put("details", details)
            put("segments", maxOf(1, segments))
            put("timestamp", System.currentTimeMillis())
        }
        val rowId = db.insert(DatabaseHelper.TABLE_LOGS, null, cv)
        if (insertCounter.incrementAndGet() % 100 == 0) {
            try {
                pruneOldLogs()
            } catch (e: Exception) {
                android.util.Log.w("LogRepository", "pruneOldLogs failed: ${e.message}")
            }
        }
        return rowId
    }

    /**
     * What it is:
     * Inserts an [ExecutionLog] entity instance into the database.
     *
     * What it does:
     * Executes [insertLog] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [LogRepository], [insertLog]
     * @param log The log model.
     * @return Generated database row ID.
     */
    fun insertLog(log: ExecutionLog): Long {
        val privacyMgr = com.jmd.helper.core.PrivacyManager.getInstance(context)
        val maskedSnippet = if (privacyMgr.shouldMaskSensitiveContent()) {
            privacyMgr.maskSensitiveText(log.messageSnippet)
        } else {
            log.messageSnippet
        }
        val maskedRecipient = if (privacyMgr.shouldMaskSensitiveContent()) {
            privacyMgr.maskPhoneNumber(log.recipient)
        } else {
            log.recipient
        }
        return logExecution(
            taskId = log.taskId,
            channel = log.channel,
            recipient = maskedRecipient,
            messageSnippet = maskedSnippet,
            status = log.status,
            details = log.details,
            segments = log.segments
        )
    }

    /**
     * What it is:
     * Queries all recorded execution logs up to [limit] ordered by timestamp descending.
     *
     * What it does:
     * Executes [getAllLogs] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [LogRepository], [getAllLogs]
     * @param limit Maximum rows to return (default 200).
     * @return List of [ExecutionLog] entries.
     */
    fun getAllLogs(limit: Int = 200): List<ExecutionLog> {
        val db = dbHelper.readableDatabase
        val logs = mutableListOf<ExecutionLog>()
        val query = "SELECT * FROM ${DatabaseHelper.TABLE_LOGS} ORDER BY timestamp DESC LIMIT ?"
        db.rawQuery(query, arrayOf(limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                logs.add(cursorToLog(cursor))
            }
        }
        return logs
    }

    /**
     * What it is:
     * Queries logs generated during the current calendar day.
     *
     * What it does:
     * Executes [getLogsForToday] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [LogRepository], [getLogsForToday]
     * @return Today's [ExecutionLog] events.
     */
    fun getLogsForToday(): List<ExecutionLog> {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val db = dbHelper.readableDatabase
        val logs = mutableListOf<ExecutionLog>()
        val query = "SELECT * FROM ${DatabaseHelper.TABLE_LOGS} WHERE timestamp >= ? ORDER BY timestamp DESC"
        db.rawQuery(query, arrayOf(startOfDay.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                logs.add(cursorToLog(cursor))
            }
        }
        return logs
    }

    /**
     * What it is:
     * Purges all recorded execution logs from the database table.
     *
     * What it does:
     * Executes [clearAllLogs] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [LogRepository], [clearAllLogs]
     */
    fun clearAllLogs() {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_LOGS, null, null)
    }
    /**
     * What it is:
     * Execution routine for [cursorToLog].
     *
     * What it does:
     * Coordinates cursorToLog operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [LogRepository]
     */

    private fun cursorToLog(c: Cursor): ExecutionLog {
        val segIdx = c.getColumnIndex("segments")
        val segments = if (segIdx >= 0) maxOf(1, c.getInt(segIdx)) else 1
        return ExecutionLog(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            taskId = c.getLong(c.getColumnIndexOrThrow("task_id")),
            channel = c.getString(c.getColumnIndexOrThrow("channel")),
            recipient = c.getString(c.getColumnIndexOrThrow("recipient")),
            messageSnippet = c.getString(c.getColumnIndexOrThrow("message_snippet")),
            status = c.getString(c.getColumnIndexOrThrow("status")),
            details = c.getString(c.getColumnIndexOrThrow("details")),
            segments = segments,
            timestampMillis = c.getLong(c.getColumnIndexOrThrow("timestamp"))
        )
    }
}
