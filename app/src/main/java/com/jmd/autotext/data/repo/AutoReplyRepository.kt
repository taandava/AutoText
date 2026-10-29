

/**
 * Package: com.jmd.autotext.data.repo
 * Module: Data Access Layer
 *
 * Governs auto-reply rule configurations, time-window evaluation, keyword pattern matching,
 * per-sender cooldown intervals, and daily reply rate limiting.
 *
 * Linked With:
 * - Database: [com.jmd.autotext.data.db.DatabaseHelper]
 * - Models: [com.jmd.autotext.data.model.AutoReplyRule], [com.jmd.autotext.data.model.ChannelType]
 * - Normalizer: [com.jmd.autotext.core.PhoneNumberNormalizer]
 * - UI Screens: `AutoReplyScreen`
 */
package com.jmd.autotext.data.repo

import android.content.*
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.sqlite.transaction
import com.jmd.autotext.core.PhoneNumberNormalizer
import com.jmd.autotext.data.db.DatabaseHelper
import com.jmd.autotext.data.model.AutoReplyRule
import com.jmd.autotext.data.model.ChannelType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository managing [AutoReplyRule] persistence and sender deduplication history.
 */
class AutoReplyRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * Inserts or replaces an [AutoReplyRule] in the SQLite database.
     *
     * @param rule The rule instance to persist.
     * @return Row ID of the inserted rule, or -1 on error.
     */
    fun insertRule(rule: AutoReplyRule): Long {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            if (rule.id > 0) put("id", rule.id)
            put("name", rule.name)
            put("channel", rule.channel.name)
            put("reply_text", rule.replyText)
            put("trigger_pattern", rule.triggerPattern)
            put("is_exact_match", if (rule.isExactMatch) 1 else 0)
            put("reply_contacts_only", if (rule.replyToContactsOnly) 1 else 0)
            put("reply_non_contacts_only", if (rule.replyToNonContactsOnly) 1 else 0)
            put("start_hour", rule.startTimeHour)
            put("start_minute", rule.startTimeMinute)
            put("end_hour", rule.endTimeHour)
            put("end_minute", rule.endTimeMinute)
            put("start_time_hour", rule.startTimeHour)
            put("start_time_minute", rule.startTimeMinute)
            put("end_time_hour", rule.endTimeHour)
            put("end_time_minute", rule.endTimeMinute)
            put("cooldown_minutes", rule.cooldownSeconds)
            put("call_event_type", rule.callEventType)
            put("max_daily_replies", rule.maxDailyReplies)
            put("is_enabled", if (rule.isEnabled) 1 else 0)
        }
        return db.insertWithOnConflict(DatabaseHelper.TABLE_AUTO_REPLY, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Updates an existing [AutoReplyRule] in storage.
     *
     * @param rule Modified rule object.
     * @return `true` if update succeeded.
     */
    fun updateRule(rule: AutoReplyRule): Boolean {
        return insertRule(rule) > 0
    }

    /**
     * Retrieves all configured [AutoReplyRule] records ordered by ID descending.
     *
     * @return List of auto-reply rules.
     */
    fun getAllRules(): List<AutoReplyRule> {
        val db = dbHelper.readableDatabase
        val rules = mutableListOf<AutoReplyRule>()
        db.query(DatabaseHelper.TABLE_AUTO_REPLY, null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) {
                rules.add(cursorToAutoReplyRule(c))
            }
        }
        return rules
    }

    /**
     * Retrieves enabled auto-reply rules, optionally filtered by communication channel.
     *
     * @param channel Optional target channel filter.
     * @return List of enabled rules.
     */
    fun getEnabledRules(channel: ChannelType? = null): List<AutoReplyRule> {
        val all = getAllRules().filter { it.isEnabled }
        return if (channel != null) all.filter { it.channel == channel } else all
    }

    /**
     * Deletes an auto-reply rule and purges its execution history.
     *
     * @param id The rule identifier.
     * @return `true` if rule was successfully removed.
     */
    fun deleteRule(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        try {
            db.delete(DatabaseHelper.TABLE_AUTO_REPLY_HISTORY, "rule_id = ?", arrayOf(id.toString()))
        } catch (e: Exception) {
            Log.w("AutoReplyRepo", "Failed to clear history for rule #$id: ${e.message}")
        }
        return db.delete(DatabaseHelper.TABLE_AUTO_REPLY, "id = ?", arrayOf(id.toString())) > 0
    }

    /**
     * Evaluates cooldown intervals and daily reply rate limits to determine whether an auto-reply
     * is permitted for a given sender number or chat title.
     *
     * @param rule The candidate [AutoReplyRule].
     * @param phone The sender phone number or chat title.
     * @return `true` if reply is allowed; `false` if rate limit or cooldown is active.
     */
    fun canReplyToNumber(rule: AutoReplyRule, phone: String): Boolean {
        val rawTrimmed = phone.trim()
        val normalized = PhoneNumberNormalizer.normalize(rawTrimmed).ifBlank {
            "title:" + rawTrimmed.lowercase()
        }
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val now = System.currentTimeMillis()

        val db = dbHelper.readableDatabase

        if (rule.cooldownSeconds > 0) {
            val cooldownMs = rule.cooldownSeconds * 1000L
            db.query(
                DatabaseHelper.TABLE_AUTO_REPLY_HISTORY,
                arrayOf("MAX(last_replied_at)"),
                "rule_id = ? AND phone_number = ?",
                arrayOf(rule.id.toString(), normalized),
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    val lastRepliedAt = cursor.getLong(0)
                    if ((now - lastRepliedAt) < cooldownMs) {
                        return false
                    }
                }
            }
        }

        if (rule.maxDailyReplies > 0) {
            db.query(
                DatabaseHelper.TABLE_AUTO_REPLY_HISTORY,
                arrayOf("reply_count"),
                "rule_id = ? AND phone_number = ? AND date_str = ?",
                arrayOf(rule.id.toString(), normalized, todayStr),
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val replyCount = cursor.getInt(0)
                    if (replyCount >= rule.maxDailyReplies) {
                        return false
                    }
                }
            }
        }
        return true
    }

    /**
     * Records a successful auto-reply dispatch in the history ledger, updating counters and timestamps.
     *
     * @param ruleId The triggered rule ID.
     * @param phone The recipient phone number or chat title.
     */
    fun recordReplySent(ruleId: Long, phone: String) {
        val rawTrimmed = phone.trim()
        val normalized = PhoneNumberNormalizer.normalize(rawTrimmed).ifBlank {
            "title:" + rawTrimmed.lowercase()
        }
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val now = System.currentTimeMillis()

        val db = dbHelper.writableDatabase
        db.transaction {
            val cv = ContentValues().apply {
                put("rule_id", ruleId)
                put("phone_number", normalized)
                put("date_str", todayStr)
                put("reply_count", 1)
                put("last_replied_at", now)
            }
            val rowId = db.insertWithOnConflict(
                DatabaseHelper.TABLE_AUTO_REPLY_HISTORY,
                null,
                cv,
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            if (rowId < 0) {
                db.execSQL(
                    "UPDATE ${DatabaseHelper.TABLE_AUTO_REPLY_HISTORY} SET reply_count = reply_count + 1, last_replied_at = ? WHERE rule_id = ? AND phone_number = ? AND date_str = ?",
                    arrayOf<Any>(now, ruleId, normalized, todayStr),
                )
            }
        }
    }

    private fun cursorToAutoReplyRule(c: Cursor): AutoReplyRule {
        val chanStr = c.getString(c.getColumnIndexOrThrow("channel"))
        val channelEnum = runCatching { ChannelType.valueOf(chanStr) }.getOrDefault(ChannelType.WHATSAPP)

        fun getColInt(primary: String, fallback: String): Int {
            val pIdx = c.getColumnIndex(primary)
            if (pIdx != -1) return c.getInt(pIdx)
            val fIdx = c.getColumnIndex(fallback)
            if (fIdx != -1) return c.getInt(fIdx)
            return 0
        }

        return AutoReplyRule(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            channel = channelEnum,
            replyText = c.getString(c.getColumnIndexOrThrow("reply_text")),
            triggerPattern = c.getString(c.getColumnIndexOrThrow("trigger_pattern")),
            isExactMatch = c.getInt(c.getColumnIndexOrThrow("is_exact_match")) == 1,
            replyToContactsOnly = c.getInt(c.getColumnIndexOrThrow("reply_contacts_only")) == 1,
            replyToNonContactsOnly = c.getInt(c.getColumnIndexOrThrow("reply_non_contacts_only")) == 1,
            startTimeHour = getColInt("start_hour", "start_time_hour"),
            startTimeMinute = getColInt("start_minute", "start_time_minute"),
            endTimeHour = getColInt("end_hour", "end_time_hour"),
            endTimeMinute = getColInt("end_minute", "end_time_minute"),
            cooldownSeconds = c.getInt(c.getColumnIndexOrThrow("cooldown_minutes")),
            callEventType = c.getString(c.getColumnIndexOrThrow("call_event_type")),
            maxDailyReplies = c.getInt(c.getColumnIndexOrThrow("max_daily_replies")),
            isEnabled = c.getInt(c.getColumnIndexOrThrow("is_enabled")) == 1
        )
    }
}
