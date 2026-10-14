package com.jmd.autotext.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.jmd.autotext.data.DatabaseHelper
import com.jmd.autotext.model.*

class TaskRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    fun insertTask(task: ScheduledTask): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            if (task.id > 0) {
                put("id", task.id)
            }
            put("title", task.title)
            put("channel", task.channel.name)
            put("recipients", task.recipients.joinToString(","))
            put("recipient_names", task.recipientNames.joinToString(","))
            put("message_text", task.messageText)
            put("trigger_time", task.triggerTimeMillis)
            put("recurrence", task.recurrence.name)
            put("custom_interval_minutes", task.customIntervalMinutes)
            put("days_of_week", task.selectedDaysOfWeek.joinToString(","))
            put("status", task.status.name)
            put("sim_slot", task.simSlot)
            put("ask_before_send", if (task.askBeforeSend) 1 else 0)
            put("sound_mode", task.soundModeSetting)
            put("fake_caller_name", task.fakeCallerName)
            put("fake_caller_number", task.fakeCallerNumber)
            put("fake_call_style", task.fakeCallStyle)
            put("created_at", task.createdAtMillis)
            put("last_executed", task.lastExecutedMillis)
            put("execution_count", task.executionCount)
            put("media_uri", task.mediaUri)
        }
        val rowId = db.insertWithOnConflict(DatabaseHelper.TABLE_TASKS, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        return if (task.id > 0 && rowId >= 0) task.id else rowId
    }

    fun updateTask(task: ScheduledTask): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("title", task.title)
            put("channel", task.channel.name)
            put("recipients", task.recipients.joinToString(","))
            put("recipient_names", task.recipientNames.joinToString(","))
            put("message_text", task.messageText)
            put("trigger_time", task.triggerTimeMillis)
            put("recurrence", task.recurrence.name)
            put("custom_interval_minutes", task.customIntervalMinutes)
            put("days_of_week", task.selectedDaysOfWeek.joinToString(","))
            put("status", task.status.name)
            put("sim_slot", task.simSlot)
            put("ask_before_send", if (task.askBeforeSend) 1 else 0)
            put("sound_mode", task.soundModeSetting)
            put("fake_caller_name", task.fakeCallerName)
            put("fake_caller_number", task.fakeCallerNumber)
            put("fake_call_style", task.fakeCallStyle)
            put("last_executed", task.lastExecutedMillis)
            put("execution_count", task.executionCount)
            put("media_uri", task.mediaUri)
        }
        return db.update(DatabaseHelper.TABLE_TASKS, cv, "id = ?", arrayOf(task.id.toString())) > 0
    }

    fun updateTaskStatus(id: Long, status: TaskStatus, lastExecuted: Long = System.currentTimeMillis()) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("status", status.name)
            if (lastExecuted > 0) {
                put("last_executed", lastExecuted)
            }
        }
        db.update(DatabaseHelper.TABLE_TASKS, cv, "id = ?", arrayOf(id.toString()))
    }

    fun deleteTask(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_TASKS, "id = ?", arrayOf(id.toString())) > 0
    }

    fun getTaskById(id: Long): ScheduledTask? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(DatabaseHelper.TABLE_TASKS, null, "id = ?", arrayOf(id.toString()), null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return cursorToTask(it)
            }
        }
        return null
    }

    fun getAllTasks(): List<ScheduledTask> {
        val db = dbHelper.readableDatabase
        val tasks = mutableListOf<ScheduledTask>()
        val cursor = db.query(DatabaseHelper.TABLE_TASKS, null, null, null, null, null, "trigger_time ASC")
        cursor.use {
            while (it.moveToNext()) {
                tasks.add(cursorToTask(it))
            }
        }
        return tasks
    }

    fun getPendingTasks(): List<ScheduledTask> {
        return getTasksByStatus(TaskStatus.PENDING)
    }

    fun getTasksByStatus(status: TaskStatus): List<ScheduledTask> {
        val db = dbHelper.readableDatabase
        val tasks = mutableListOf<ScheduledTask>()
        val cursor = db.query(DatabaseHelper.TABLE_TASKS, null, "status = ?", arrayOf(status.name), null, null, "trigger_time ASC")
        cursor.use {
            while (it.moveToNext()) {
                tasks.add(cursorToTask(it))
            }
        }
        return tasks
    }

    private fun cursorToTask(c: Cursor): ScheduledTask {
        val recs = c.getString(c.getColumnIndexOrThrow("recipients"))
        val recipientList = when {
            recs.isNullOrBlank() -> emptyList()
            recs.trim().startsWith("[") -> runCatching {
                val arr = org.json.JSONArray(recs.trim())
                (0 until arr.length()).map { arr.getString(it).trim().replace("\"", "") }
            }.getOrDefault(recs.split(",").map { it.trim().replace("\"", "").replace("[", "").replace("]", "") })
            else -> recs.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        val names = c.getString(c.getColumnIndexOrThrow("recipient_names"))
        val nameList = when {
            names.isNullOrBlank() -> emptyList()
            names.trim().startsWith("[") -> runCatching {
                val arr = org.json.JSONArray(names.trim())
                (0 until arr.length()).map { arr.getString(it).trim().replace("\"", "") }
            }.getOrDefault(names.split(",").map { it.trim().replace("\"", "").replace("[", "").replace("]", "") })
            else -> names.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        val days = c.getString(c.getColumnIndexOrThrow("days_of_week"))
        val dayList = if (days.isNullOrEmpty()) emptyList() else days.split(",").mapNotNull { it.toIntOrNull() }

        val mediaIdx = c.getColumnIndex("media_uri")
        val mediaUriStr = if (mediaIdx >= 0) c.getString(mediaIdx).orEmpty() else ""

        return ScheduledTask(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")).orEmpty(),
            channel = runCatching { ChannelType.valueOf(c.getString(c.getColumnIndexOrThrow("channel"))) }.getOrDefault(ChannelType.SMS),
            recipients = recipientList,
            recipientNames = nameList,
            messageText = c.getString(c.getColumnIndexOrThrow("message_text")).orEmpty(),
            triggerTimeMillis = c.getLong(c.getColumnIndexOrThrow("trigger_time")),
            recurrence = runCatching { RecurrenceType.valueOf(c.getString(c.getColumnIndexOrThrow("recurrence"))) }.getOrDefault(RecurrenceType.ONCE),
            customIntervalMinutes = c.getInt(c.getColumnIndexOrThrow("custom_interval_minutes")),
            selectedDaysOfWeek = dayList,
            status = runCatching { TaskStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status"))) }.getOrDefault(TaskStatus.PENDING),
            simSlot = c.getInt(c.getColumnIndexOrThrow("sim_slot")),
            askBeforeSend = c.getInt(c.getColumnIndexOrThrow("ask_before_send")) == 1,
            soundModeSetting = c.getString(c.getColumnIndexOrThrow("sound_mode")) ?: "NORMAL",
            fakeCallerName = c.getString(c.getColumnIndexOrThrow("fake_caller_name")) ?: "Incoming Call",
            fakeCallerNumber = c.getString(c.getColumnIndexOrThrow("fake_caller_number")) ?: "+1 (555) 019-2834",
            fakeCallStyle = c.getString(c.getColumnIndexOrThrow("fake_call_style")) ?: "PIXEL",
            createdAtMillis = c.getLong(c.getColumnIndexOrThrow("created_at")),
            lastExecutedMillis = c.getLong(c.getColumnIndexOrThrow("last_executed")),
            executionCount = c.getInt(c.getColumnIndexOrThrow("execution_count")),
            mediaUri = mediaUriStr
        )
    }
}

