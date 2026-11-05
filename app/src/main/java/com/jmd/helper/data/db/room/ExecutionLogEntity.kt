package com.jmd.helper.data.db.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room Entity mapping directly to the SQLite `logs` table.
 */
@Entity(tableName = "logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "task_id")
    val taskId: Long = 0,

    @ColumnInfo(name = "channel")
    val channel: String = "",

    @ColumnInfo(name = "recipient")
    val recipient: String = "",

    @ColumnInfo(name = "message_snippet")
    val messageSnippet: String = "",

    @ColumnInfo(name = "status")
    val status: String = "",

    @ColumnInfo(name = "details")
    val details: String? = null,

    @ColumnInfo(name = "segments")
    val segments: Int = 1,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Data Access Object for Room `logs` table.
 */
@Dao
interface ExecutionLogDao {

    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun observeAllLogs(): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int): Flow<List<ExecutionLogEntity>>

    @Query("SELECT COUNT(*) FROM logs")
    fun observeLogCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ExecutionLogEntity): Long

    @Query("DELETE FROM logs WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteLogsOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM logs")
    suspend fun clearAll()
}

