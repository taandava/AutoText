/**
 * Package: com.jmd.helper.data.db.room
 * Module: Persistence & Database Layer
 *
 * What it is:
 * Architecture definition and implementation for [ExecutionLogEntity].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for ExecutionLogEntity.
 *
 * Linked With:
 * - Repositories: [androidx.room.Dao]
 * - Models & Entities: [androidx.room.Entity]
 * - Android Framework & Jetpack: [androidx.room.ColumnInfo], [androidx.room.Insert], [androidx.room.OnConflictStrategy], [androidx.room.PrimaryKey]
 */

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
 * What it is:
 * Room Entity mapping directly to the SQLite `logs` table.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [ExecutionLogEntity].
 *
 * Linked with:
 * - Architecture: [ExecutionLogEntity], [ExecutionLogEntity]
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
 * What it is:
 * Data Access Object for Room `logs` table.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [ExecutionLogDao].
 *
 * Linked with:
 * - Architecture: [ExecutionLogEntity], [ExecutionLogDao]
 */
@Dao
interface ExecutionLogDao {

    /**
     * What it is:
     * Execution routine for [observeAllLogs].
     *
     * What it does:
     * Coordinates observeAllLogs operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [ExecutionLogEntity]
     */
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun observeAllLogs(): Flow<List<ExecutionLogEntity>>

    /**
     * What it is:
     * Execution routine for [observeRecentLogs].
     *
     * What it does:
     * Coordinates observeRecentLogs operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [ExecutionLogEntity]
     */
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int): Flow<List<ExecutionLogEntity>>

    /**
     * What it is:
     * Execution routine for [observeLogCount].
     *
     * What it does:
     * Coordinates observeLogCount operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [ExecutionLogEntity]
     */
    @Query("SELECT COUNT(*) FROM logs")
    fun observeLogCount(): Flow<Int>

    /**
     * What it is:
     * Execution routine for [insert].
     *
     * What it does:
     * Coordinates insert operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [ExecutionLogEntity]
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ExecutionLogEntity): Long

    /**
     * What it is:
     * Execution routine for [deleteLogsOlderThan].
     *
     * What it does:
     * Coordinates deleteLogsOlderThan operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [ExecutionLogEntity]
     */
    @Query("DELETE FROM logs WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteLogsOlderThan(cutoffTimestamp: Long): Int

    /**
     * What it is:
     * Execution routine for [clearAll].
     *
     * What it does:
     * Coordinates clearAll operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [ExecutionLogEntity]
     */
    @Query("DELETE FROM logs")
    suspend fun clearAll()
}
