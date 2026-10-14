package com.jmd.autotext.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.jmd.autotext.data.DatabaseHelper
import com.jmd.autotext.model.ExecutionLog

class LogRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    fun logExecution(
        taskId: Long = 0,
        channel: String,
        recipient: String,
        messageSnippet: String,
        status: String,
        details: String = ""
    ): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("task_id", taskId)
            put("channel", channel)
            put("recipient", recipient)
            put("message_snippet", messageSnippet)
            put("status", status)
            put("details", details)
            put("timestamp", System.currentTimeMillis())
        }
        return db.insert(DatabaseHelper.TABLE_LOGS, null, cv)
    }

    fun insertLog(log: ExecutionLog): Long {
        return logExecution(
            taskId = log.taskId,
            channel = log.channel,
            recipient = log.recipient,
            messageSnippet = log.messageSnippet,
            status = log.status,
            details = log.details
        )
    }

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

    fun clearAllLogs() {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_LOGS, null, null)
    }

    private fun cursorToLog(c: Cursor): ExecutionLog {
        return ExecutionLog(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            taskId = c.getLong(c.getColumnIndexOrThrow("task_id")),
            channel = c.getString(c.getColumnIndexOrThrow("channel")),
            recipient = c.getString(c.getColumnIndexOrThrow("recipient")),
            messageSnippet = c.getString(c.getColumnIndexOrThrow("message_snippet")),
            status = c.getString(c.getColumnIndexOrThrow("status")),
            details = c.getString(c.getColumnIndexOrThrow("details")),
            timestampMillis = c.getLong(c.getColumnIndexOrThrow("timestamp"))
        )
    }
}

