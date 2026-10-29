

/**
 * Package: com.jmd.autotext.data.repo
 * Module: Data Repositories Layer
 *
 * Persists and queries automation execution logs for SMS, WhatsApp, and call actions.
 * Maintains execution history with timestamps, delivery status, and failure diagnostics.
 *
 * Linked With:
 * - Database: [com.jmd.autotext.data.db.DatabaseHelper]
 * - Models: [com.jmd.autotext.data.model.ExecutionLog]
 * - UI Screens: `LogsScreen`
 */
package com.jmd.autotext.data.repo

import android.content.*
import android.database.Cursor
import com.jmd.autotext.data.db.DatabaseHelper
import com.jmd.autotext.data.model.ExecutionLog

/**
 * Repository for logging execution outcomes and querying diagnostic event history.
 *
 * @param context Application context.
 */
class LogRepository(context: Context) {

    companion object {
        private const val TAG = "LogRepository"
        const val MAX_LOG_RETENTION_COUNT = 5000
        const val MAX_LOG_RETENTION_DAYS = 45
        private val insertCounter = java.util.concurrent.atomic.AtomicInteger(0)
    }

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * Enforces rolling log retention policy:
     * 1. Removes records older than [maxAgeDays] (default 45 days).
     * 2. Caps maximum total log rows to [maxCount] (default 5,000 entries).
     *
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
     * Persists an execution diagnostic event with channel, recipient, status, and SMS part count.
     *
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
            } catch (_: Exception) {}
        }
        return rowId
    }

    /**
     * Inserts an [ExecutionLog] entity instance into the database.
     *
     * @param log The log model.
     * @return Generated database row ID.
     */
    fun insertLog(log: ExecutionLog): Long {
        return logExecution(
            taskId = log.taskId,
            channel = log.channel,
            recipient = log.recipient,
            messageSnippet = log.messageSnippet,
            status = log.status,
            details = log.details,
            segments = log.segments
        )
    }

    /**
     * Queries all recorded execution logs up to [limit] ordered by timestamp descending.
     *
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
     * Queries logs generated during the current calendar day.
     *
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
     * Purges all recorded execution logs from the database table.
     */
    fun clearAllLogs() {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_LOGS, null, null)
    }

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
