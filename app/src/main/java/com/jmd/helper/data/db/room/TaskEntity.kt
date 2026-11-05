package com.jmd.helper.data.db.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.RecurrenceType
import com.jmd.helper.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow

/**
 * Room Entity mapping directly to the SQLite `tasks` table.
 * Provides compile-time schema verification and reactive Flow emission.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "channel")
    val channel: ChannelType = ChannelType.SMS,

    @ColumnInfo(name = "recipients")
    val recipients: String = "",

    @ColumnInfo(name = "recipient_names")
    val recipientNames: String = "",

    @ColumnInfo(name = "message_text")
    val messageText: String = "",

    @ColumnInfo(name = "trigger_time")
    val triggerTime: Long = 0,

    @ColumnInfo(name = "recurrence")
    val recurrence: RecurrenceType = RecurrenceType.ONCE,

    @ColumnInfo(name = "custom_interval_minutes")
    val customIntervalMinutes: Long = 0,

    @ColumnInfo(name = "days_of_week")
    val daysOfWeek: String = "",

    @ColumnInfo(name = "status")
    val status: TaskStatus = TaskStatus.PENDING,

    @ColumnInfo(name = "sim_slot")
    val simSlot: Int = 0,

    @ColumnInfo(name = "ask_before_send")
    val askBeforeSend: Boolean = false,

    @ColumnInfo(name = "sound_mode")
    val soundMode: String? = null,

    @ColumnInfo(name = "simulated_caller_name")
    val simulatedCallerName: String = "",

    @ColumnInfo(name = "simulated_caller_number")
    val simulatedCallerNumber: String = "",

    @ColumnInfo(name = "simulated_call_style")
    val simulatedCallStyle: String = "PIXEL",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_executed")
    val lastExecuted: Long = 0,

    @ColumnInfo(name = "execution_count")
    val executionCount: Int = 0,

    @ColumnInfo(name = "media_uri")
    val mediaUri: String? = null,

    @ColumnInfo(name = "call_duration_seconds")
    val callDurationSeconds: Int = 0,

    @ColumnInfo(name = "include_dial_time")
    val includeDialTime: Boolean = false,

    @ColumnInfo(name = "call_sequence_mode")
    val callSequenceMode: String = "GIVEN",

    @ColumnInfo(name = "delay_between_calls_seconds")
    val delayBetweenCallsSeconds: Int = 5,

    @ColumnInfo(name = "call_failure_policy")
    val callFailurePolicy: String = "CONTINUE"
)

/**
 * Data Access Object for Room `tasks` table.
 */
@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY trigger_time ASC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY trigger_time ASC")
    fun observeTasksByStatus(status: TaskStatus): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' AND trigger_time <= :currentTime ORDER BY trigger_time ASC")
    suspend fun getDuePendingTasks(currentTime: Long): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks")
    fun observeTaskCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TaskStatus)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()
}
