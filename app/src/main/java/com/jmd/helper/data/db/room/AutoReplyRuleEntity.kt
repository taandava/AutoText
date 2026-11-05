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
import kotlinx.coroutines.flow.Flow

/**
 * Room Entity mapping directly to the SQLite `auto_reply_rules` table.
 */
@Entity(tableName = "auto_reply_rules")
data class AutoReplyRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "channel")
    val channel: String = "SMS",

    @ColumnInfo(name = "reply_text")
    val replyText: String = "",

    @ColumnInfo(name = "trigger_pattern")
    val triggerPattern: String = "*",

    @ColumnInfo(name = "is_exact_match")
    val isExactMatch: Boolean = false,

    @ColumnInfo(name = "reply_contacts_only")
    val replyContactsOnly: Boolean = false,

    @ColumnInfo(name = "reply_non_contacts_only")
    val replyNonContactsOnly: Boolean = false,

    @ColumnInfo(name = "start_hour")
    val startHour: Int = 0,

    @ColumnInfo(name = "start_minute")
    val startMinute: Int = 0,

    @ColumnInfo(name = "end_hour")
    val endHour: Int = 23,

    @ColumnInfo(name = "end_minute")
    val endMinute: Int = 59,

    @ColumnInfo(name = "cooldown_minutes")
    val cooldownMinutes: Int = 0,

    @ColumnInfo(name = "call_event_type")
    val callEventType: String = "BOTH",

    @ColumnInfo(name = "max_daily_replies")
    val maxDailyReplies: Int = 1,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true
)

/**
 * Data Access Object for Room `auto_reply_rules` table.
 */
@Dao
interface AutoReplyDao {

    @Query("SELECT * FROM auto_reply_rules ORDER BY id DESC")
    fun observeAllRules(): Flow<List<AutoReplyRuleEntity>>

    @Query("SELECT * FROM auto_reply_rules WHERE is_enabled = 1")
    suspend fun getActiveRules(): List<AutoReplyRuleEntity>

    @Query("SELECT * FROM auto_reply_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Long): AutoReplyRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AutoReplyRuleEntity): Long

    @Update
    suspend fun update(rule: AutoReplyRuleEntity)

    @Delete
    suspend fun delete(rule: AutoReplyRuleEntity)

    @Query("UPDATE auto_reply_rules SET is_enabled = :enabled WHERE id = :id")
    suspend fun updateEnabledStatus(id: Long, enabled: Boolean)
}

