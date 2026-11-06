

/**
 * Package: com.jmd.helper.data.repo
 * Module: Data Access Layer
 *
 * Provides thread-safe CRUD operations, JSON array serialization of multi-recipient contacts,
 * execution status transitions, and delivery deduplication for scheduled tasks.
 *
 * Linked With:
 * - Database: [com.jmd.helper.data.db.DatabaseHelper]
 * - Models: [com.jmd.helper.data.model.ScheduledTask], [com.jmd.helper.data.model.ChannelType], [com.jmd.helper.data.model.TaskStatus]
 * - Normalizer: [com.jmd.helper.core.PhoneNumberNormalizer]
 * - Schedulers: [com.jmd.helper.scheduler.AlarmScheduler]
 * - Execution Engine: [com.jmd.helper.engine.SequentialTaskEngine]
 * - UI Screens: `TasksScreen`, `ComposeTaskScreen`
 */
package com.jmd.helper.data.repo

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.jmd.helper.core.PhoneNumberNormalizer
import com.jmd.helper.data.db.DatabaseHelper
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.RecurrenceType
import com.jmd.helper.data.model.ScheduledTask
import com.jmd.helper.data.model.TaskStatus
import com.jmd.helper.scheduler.AlarmScheduler

/**
 * Repository governing persistence of [ScheduledTask] entities and recipient delivery records.
 */
class TaskRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * Inserts a new scheduled task or replaces an existing task record.
     *
     * @param task The [ScheduledTask] entity to persist.
     * @return Generated or confirmed task row ID.
     */
    fun insertTask(task: ScheduledTask): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            if (task.id > 0) {
                put("id", task.id)
            }
            put("title", task.title)
            put("channel", task.channel.name)
            put("recipients", org.json.JSONArray(task.recipients).toString())
            put("recipient_names", org.json.JSONArray(task.recipientNames).toString())
            put("message_text", task.messageText)
            put("trigger_time", task.triggerTimeMillis)
            put("recurrence", task.recurrence.name)
            put("custom_interval_minutes", task.customIntervalMinutes)
            put("days_of_week", task.selectedDaysOfWeek.joinToString(","))
            put("status", task.status.name)
            put("sim_slot", task.simSlot)
            put("ask_before_send", if (task.askBeforeSend) 1 else 0)
            put("sound_mode", task.soundModeSetting)
            put("simulated_caller_name", task.simulatedCallerName)
            put("simulated_caller_number", task.simulatedCallerNumber)
            put("simulated_call_style", task.simulatedCallStyle)
            put("created_at", task.createdAtMillis)
            put("last_executed", task.lastExecutedMillis)
            put("execution_count", task.executionCount)
            put("media_uri", task.mediaUri)
            put("call_duration_seconds", task.callDurationSeconds)
            put("include_dial_time", if (task.includeDialTimeInCallDuration) 1 else 0)
            put("call_sequence_mode", task.callSequenceMode)
            put("delay_between_calls_seconds", task.delayBetweenCallsSeconds)
            put("call_failure_policy", task.callFailurePolicy)
        }
        val rowId = db.insertWithOnConflict(DatabaseHelper.TABLE_TASKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return if (task.id > 0 && rowId >= 0) task.id else rowId
    }

    /**
     * Updates an existing task's parameters in storage.
     *
     * @param task Modified [ScheduledTask] instance.
     * @return `true` if update succeeded.
     */
    fun updateTask(task: ScheduledTask): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("title", task.title)
            put("channel", task.channel.name)
            put("recipients", org.json.JSONArray(task.recipients).toString())
            put("recipient_names", org.json.JSONArray(task.recipientNames).toString())
            put("message_text", task.messageText)
            put("trigger_time", task.triggerTimeMillis)
            put("recurrence", task.recurrence.name)
            put("custom_interval_minutes", task.customIntervalMinutes)
            put("days_of_week", task.selectedDaysOfWeek.joinToString(","))
            put("status", task.status.name)
            put("sim_slot", task.simSlot)
            put("ask_before_send", if (task.askBeforeSend) 1 else 0)
            put("sound_mode", task.soundModeSetting)
            put("simulated_caller_name", task.simulatedCallerName)
            put("simulated_caller_number", task.simulatedCallerNumber)
            put("simulated_call_style", task.simulatedCallStyle)
            put("last_executed", task.lastExecutedMillis)
            put("execution_count", task.executionCount)
            put("media_uri", task.mediaUri)
            put("call_duration_seconds", task.callDurationSeconds)
            put("include_dial_time", if (task.includeDialTimeInCallDuration) 1 else 0)
            put("call_sequence_mode", task.callSequenceMode)
            put("delay_between_calls_seconds", task.delayBetweenCallsSeconds)
            put("call_failure_policy", task.callFailurePolicy)
        }
        return db.update(DatabaseHelper.TABLE_TASKS, cv, "id = ?", arrayOf(task.id.toString())) > 0
    }

    /**
     * Transitions task status (PENDING, RUNNING, DONE, FAILED, CANCELLED) and updates lastExecuted timestamp.
     *
     * @param id The task ID.
     * @param status New [TaskStatus].
     * @param lastExecuted Timestamp in milliseconds.
     */
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

    /**
     * Recovers and reconciles any tasks left stranded in [TaskStatus.RUNNING] state due to
     * unexpected application process termination, crash, or device reboot.
     * Transitions recurring tasks back to [TaskStatus.PENDING] and single-run tasks to [TaskStatus.FAILED].
     *
     * @return Number of orphaned running tasks successfully recovered.
     */
    fun recoverInterruptedRunningTasks(): Int {
        val db = dbHelper.writableDatabase
        val tasks = mutableListOf<ScheduledTask>()
        val cursor = db.query(DatabaseHelper.TABLE_TASKS, null, "status = ?", arrayOf(TaskStatus.RUNNING.name), null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                tasks.add(cursorToTask(it))
            }
        }
        for (task in tasks) {
            val targetStatus = if (task.recurrence != RecurrenceType.ONCE) TaskStatus.PENDING else TaskStatus.FAILED
            updateTaskStatus(task.id, targetStatus)
        }
        return tasks.size
    }

    /**
     * Postpones a scheduled task by a given duration in milliseconds.
     * Updates trigger_time to now + postponeMillis and ensures status is PENDING.
     *
     * @param id The task ID.
     * @param postponeMillis Duration in milliseconds to postpone execution.
     * @return True if update succeeded.
     */
    fun postponeTask(id: Long, postponeMillis: Long): Boolean {
        if (getTaskById(id) == null) return false
        val newTrigger = System.currentTimeMillis() + postponeMillis
        val cv = ContentValues().apply {
            put("trigger_time", newTrigger)
            put("status", TaskStatus.PENDING.name)
        }
        val db = dbHelper.writableDatabase
        return db.update(DatabaseHelper.TABLE_TASKS, cv, "id = ?", arrayOf(id.toString())) > 0
    }

    /**
     * Postpones all currently pending tasks whose trigger time is due or imminent.
     *
     * @param postponeMillis Duration in milliseconds to push trigger times forward.
     * @return List of updated [ScheduledTask] objects.
     */
    fun postponeUpcomingTasks(postponeMillis: Long): List<ScheduledTask> {
        val now = System.currentTimeMillis()
        val threshold = now + postponeMillis
        val pending = getPendingTasks()
        val postponedTasks = mutableListOf<ScheduledTask>()
        val db = dbHelper.writableDatabase

        for (task in pending) {
            if (task.triggerTimeMillis <= threshold) {
                val newTrigger = now + postponeMillis
                val cv = ContentValues().apply {
                    put("trigger_time", newTrigger)
                    put("status", TaskStatus.PENDING.name)
                }
                val updated = db.update(DatabaseHelper.TABLE_TASKS, cv, "id = ?", arrayOf(task.id.toString())) > 0
                if (updated) {
                    postponedTasks.add(task.copy(triggerTimeMillis = newTrigger, status = TaskStatus.PENDING))
                }
            }
        }
        return postponedTasks
    }

    /**
     * Deletes a scheduled task by ID.
     * Directly cancels all pending AlarmManager alarms and, if the task is a
     * reminder, dismisses that specific alarm from Android's system clock app.
     *
     * @param id The task ID.
     * @return `true` if deletion affected rows.
     */
    fun deleteTask(id: Long): Boolean {
        val task = getTaskById(id)
        if (task != null) {
            AlarmScheduler(appContext).cancelTask(id, task)
        }
        val db = dbHelper.writableDatabase
        return db.delete(DatabaseHelper.TABLE_TASKS, "id = ?", arrayOf(id.toString())) > 0
    }

    /**
     * Queries a task by ID.
     *
     * @param id Task identifier.
     * @return [ScheduledTask] if found, or `null`.
     */
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

    /**
     * Retrieves all scheduled tasks ordered by trigger time ascending.
     *
     * @return List of [ScheduledTask] objects.
     */
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
        val db = dbHelper.readableDatabase
        val tasks = mutableListOf<ScheduledTask>()
        val cursor = db.query(DatabaseHelper.TABLE_TASKS, null, "status IN ('PENDING', 'SCHEDULED', 'READY')", null, null, null, "trigger_time ASC")
        cursor.use {
            while (it.moveToNext()) {
                tasks.add(cursorToTask(it))
            }
        }
        return tasks
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
        val callDurIdx = c.getColumnIndex("call_duration_seconds")
        val callDuration = if (callDurIdx >= 0) c.getInt(callDurIdx) else 0
        val dialTimeIdx = c.getColumnIndex("include_dial_time")
        val includeDialTime = dialTimeIdx >= 0 && c.getInt(dialTimeIdx) == 1
        val callSeqIdx = c.getColumnIndex("call_sequence_mode")
        val callSeqMode = if (callSeqIdx >= 0) c.getString(callSeqIdx).orEmpty().ifBlank { "GIVEN" } else "GIVEN"
        val delayCallsIdx = c.getColumnIndex("delay_between_calls_seconds")
        val delayBetweenCalls = if (delayCallsIdx >= 0) c.getInt(delayCallsIdx) else 5
        val failurePolicyIdx = c.getColumnIndex("call_failure_policy")
        val callFailurePolicy = if (failurePolicyIdx >= 0) c.getString(failurePolicyIdx).orEmpty().ifBlank { "CONTINUE" } else "CONTINUE"

        val simNameIdx = c.getColumnIndex("simulated_caller_name")
        val simNumIdx = c.getColumnIndex("simulated_caller_number")
        val simStyleIdx = c.getColumnIndex("simulated_call_style")

        val resolvedSimName = (if (simNameIdx >= 0) c.getString(simNameIdx) else null)?.ifBlank { null }
            ?: "Incoming Call"
        val resolvedSimNum = (if (simNumIdx >= 0) c.getString(simNumIdx) else null)?.ifBlank { null }
            ?: "+91 98765 43210"
        val resolvedSimStyle = (if (simStyleIdx >= 0) c.getString(simStyleIdx) else null)?.ifBlank { null }
            ?: "PIXEL"

        return ScheduledTask(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")).orEmpty(),
            channel = ChannelType.fromString(c.getString(c.getColumnIndexOrThrow("channel"))),
            recipients = recipientList,
            recipientNames = nameList,
            messageText = c.getString(c.getColumnIndexOrThrow("message_text")).orEmpty(),
            triggerTimeMillis = c.getLong(c.getColumnIndexOrThrow("trigger_time")),
            recurrence = RecurrenceType.fromString(c.getString(c.getColumnIndexOrThrow("recurrence"))),
            customIntervalMinutes = c.getInt(c.getColumnIndexOrThrow("custom_interval_minutes")),
            selectedDaysOfWeek = dayList,
            status = TaskStatus.fromString(c.getString(c.getColumnIndexOrThrow("status"))),
            simSlot = c.getInt(c.getColumnIndexOrThrow("sim_slot")),
            askBeforeSend = c.getInt(c.getColumnIndexOrThrow("ask_before_send")) == 1,
            soundModeSetting = c.getString(c.getColumnIndexOrThrow("sound_mode")) ?: "NORMAL",
            simulatedCallerName = resolvedSimName,
            simulatedCallerNumber = resolvedSimNum,
            simulatedCallStyle = resolvedSimStyle,
            createdAtMillis = c.getLong(c.getColumnIndexOrThrow("created_at")),
            lastExecutedMillis = c.getLong(c.getColumnIndexOrThrow("last_executed")),
            executionCount = c.getInt(c.getColumnIndexOrThrow("execution_count")),
            mediaUri = mediaUriStr,
            callDurationSeconds = callDuration,
            includeDialTimeInCallDuration = includeDialTime,
            callSequenceMode = callSeqMode,
            delayBetweenCallsSeconds = delayBetweenCalls,
            callFailurePolicy = callFailurePolicy
        )
    }

    fun isRecipientDeliveredForTask(taskId: Long, recipient: String, occurrenceId: String = ""): Boolean {
        if (taskId <= 0L || recipient.isBlank()) return false
        val norm = PhoneNumberNormalizer.normalize(recipient).ifBlank { recipient.trim().lowercase() }
        val db = dbHelper.readableDatabase
        return try {
            if (occurrenceId.isNotBlank()) {
                db.rawQuery(
                    "SELECT id FROM ${DatabaseHelper.TABLE_TASK_DELIVERIES} WHERE task_id = ? AND occurrence_id = ? AND (normalized_number = ? OR recipient = ?) AND status IN ('SUCCESS', 'DISPATCHED', 'PENDING_CONFIRMATION') LIMIT 1",
                    arrayOf(taskId.toString(), occurrenceId, norm, recipient.trim())
                ).use { it.moveToFirst() }
            } else {
                db.rawQuery(
                    "SELECT id FROM ${DatabaseHelper.TABLE_TASK_DELIVERIES} WHERE task_id = ? AND (normalized_number = ? OR recipient = ?) AND status IN ('SUCCESS', 'DISPATCHED', 'PENDING_CONFIRMATION') LIMIT 1",
                    arrayOf(taskId.toString(), norm, recipient.trim())
                ).use { it.moveToFirst() }
            }
        } catch (e: Exception) {
            Log.w("TaskRepo", "Failed to check recipient delivery: ${e.message}")
            false
        }
    }

    fun recordRecipientDelivery(taskId: Long, recipient: String, channel: String, status: String = "SUCCESS", occurrenceId: String = ""): Boolean {
        if (taskId <= 0L || recipient.isBlank()) return false
        val norm = PhoneNumberNormalizer.normalize(recipient).ifBlank { recipient.trim().lowercase() }
        val db = dbHelper.writableDatabase
        return try {
            val cv = ContentValues().apply {
                put("task_id", taskId)
                put("occurrence_id", occurrenceId)
                put("recipient", recipient.trim())
                put("normalized_number", norm)
                put("channel", channel)
                put("status", status)
                put("dispatched_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(DatabaseHelper.TABLE_TASK_DELIVERIES, null, cv, SQLiteDatabase.CONFLICT_REPLACE) >= 0
        } catch (e: Exception) {
            Log.w("TaskRepo", "Failed to record delivery for task #$taskId, recipient: $recipient: ${e.message}")
            false
        }
    }

    fun clearDeliveriesForTask(taskId: Long): Int {
        if (taskId <= 0L) return 0
        return try {
            val db = dbHelper.writableDatabase
            db.delete(DatabaseHelper.TABLE_TASK_DELIVERIES, "task_id = ?", arrayOf(taskId.toString()))
        } catch (e: Exception) {
            Log.w("TaskRepo", "Failed to clear deliveries for task #$taskId: ${e.message}")
            0
        }
    }
}
