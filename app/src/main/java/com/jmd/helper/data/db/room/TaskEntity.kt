/**
 * Package: com.jmd.helper.data.db.room
 * Module: Persistence & Database Layer
 *
 * What it is:
 * Architecture definition and implementation for [TaskEntity].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for TaskEntity.
 *
 * Linked With:
 * - Repositories: [androidx.room.Dao]
 * - Models & Entities: [androidx.room.Entity], [com.jmd.helper.data.model.ChannelType], [com.jmd.helper.data.model.RecurrenceType], [com.jmd.helper.data.model.TaskStatus]
 * - Android Framework & Jetpack: [androidx.room.ColumnInfo], [androidx.room.Delete], [androidx.room.Insert], [androidx.room.OnConflictStrategy]
 */

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
 * What it is:
 * Room Entity mapping directly to the SQLite `tasks` table.
 *
 * What it does:
 * Provides compile-time schema verification and reactive Flow emission.
 *
 * Linked with:
 * - Architecture: [TaskEntity], [TaskEntity]
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
 * What it is:
 * Data Access Object for Room `tasks` table.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [TaskDao].
 *
 * Linked with:
 * - Architecture: [TaskEntity], [TaskDao]
 */
@Dao
interface TaskDao {

    /**
     * What it is:
     * Execution routine for [observeAllTasks].
     *
     * What it does:
     * Coordinates observeAllTasks operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Query("SELECT * FROM tasks ORDER BY trigger_time ASC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    /**
     * What it is:
     * Execution routine for [observeTasksByStatus].
     *
     * What it does:
     * Coordinates observeTasksByStatus operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY trigger_time ASC")
    fun observeTasksByStatus(status: TaskStatus): Flow<List<TaskEntity>>

    /**
     * What it is:
     * Execution routine for [getTaskById].
     *
     * What it does:
     * Coordinates getTaskById operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): TaskEntity?

    /**
     * What it is:
     * Execution routine for [getDuePendingTasks].
     *
     * What it does:
     * Coordinates getDuePendingTasks operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Query("SELECT * FROM tasks WHERE status = 'PENDING' AND trigger_time <= :currentTime ORDER BY trigger_time ASC")
    suspend fun getDuePendingTasks(currentTime: Long): List<TaskEntity>

    /**
     * What it is:
     * Execution routine for [observeTaskCount].
     *
     * What it does:
     * Coordinates observeTaskCount operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Query("SELECT COUNT(*) FROM tasks")
    fun observeTaskCount(): Flow<Int>

    /**
     * What it is:
     * Execution routine for [insert].
     *
     * What it does:
     * Coordinates insert operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    /**
     * What it is:
     * Execution routine for [insertAll].
     *
     * What it does:
     * Coordinates insertAll operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    /**
     * What it is:
     * Execution routine for [update].
     *
     * What it does:
     * Coordinates update operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Update
    suspend fun update(task: TaskEntity)

    /**
     * What it is:
     * Execution routine for [updateStatus].
     *
     * What it does:
     * Coordinates updateStatus operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TaskStatus)

    /**
     * What it is:
     * Execution routine for [delete].
     *
     * What it does:
     * Coordinates delete operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Delete
    suspend fun delete(task: TaskEntity)

    /**
     * What it is:
     * Execution routine for [deleteById].
     *
     * What it does:
     * Coordinates deleteById operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * What it is:
     * Execution routine for [clearAll].
     *
     * What it does:
     * Coordinates clearAll operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskEntity]
     */
    @Query("DELETE FROM tasks")
    suspend fun clearAll()
}
