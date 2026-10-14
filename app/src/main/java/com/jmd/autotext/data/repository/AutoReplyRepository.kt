package com.jmd.autotext.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.jmd.autotext.data.DatabaseHelper
import com.jmd.autotext.model.AutoReplyRule
import com.jmd.autotext.model.ChannelType

class AutoReplyRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)

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
            put("cooldown_minutes", rule.cooldownMinutes)
            put("call_event_type", rule.callEventType)
            put("max_daily_replies", rule.maxDailyReplies)
            put("is_enabled", if (rule.isEnabled) 1 else 0)
        }
        return db.insertWithOnConflict(DatabaseHelper.TABLE_AUTO_REPLY, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateRule(rule: AutoReplyRule): Boolean {
        return insertRule(rule) > 0
    }

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

    fun getEnabledRules(channel: ChannelType? = null): List<AutoReplyRule> {
        val all = getAllRules().filter { it.isEnabled }
        return if (channel != null) all.filter { it.channel == channel } else all
    }

    fun toggleRule(id: Long, enabled: Boolean): Boolean {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("is_enabled", if (enabled) 1 else 0)
        }
        return db.update(DatabaseHelper.TABLE_AUTO_REPLY, cv, "id = ?", arrayOf(id.toString())) > 0
    }

    fun deleteRule(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        try {
            db.delete(DatabaseHelper.TABLE_AUTO_REPLY_HISTORY, "rule_id = ?", arrayOf(id.toString()))
        } catch (_: Exception) {}
        return db.delete(DatabaseHelper.TABLE_AUTO_REPLY, "id = ?", arrayOf(id.toString())) > 0
    }

    fun canReplyToNumber(rule: AutoReplyRule, phone: String): Boolean {
        val normalized = com.jmd.autotext.util.PhoneNumberNormalizer.normalize(phone)
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val now = System.currentTimeMillis()

        val db = dbHelper.readableDatabase

        // 1. Check cooldown period across all dates (prevents cooldown bypass across midnight)
        if (rule.cooldownMinutes > 0) {
            val cooldownMs = rule.cooldownMinutes * 60 * 1000L
            db.query(
                DatabaseHelper.TABLE_AUTO_REPLY_HISTORY,
                arrayOf("MAX(last_replied_at)"),
                "rule_id = ? AND phone_number = ?",
                arrayOf(rule.id.toString(), normalized),
                null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    val lastRepliedAt = cursor.getLong(0)
                    if (now - lastRepliedAt < cooldownMs) {
                        return false
                    }
                }
            }
        }

        // 2. Check maximum daily replies limit for today
        if (rule.maxDailyReplies > 0) {
            db.query(
                DatabaseHelper.TABLE_AUTO_REPLY_HISTORY,
                arrayOf("reply_count"),
                "rule_id = ? AND phone_number = ? AND date_str = ?",
                arrayOf(rule.id.toString(), normalized, todayStr),
                null, null, null
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

    fun recordReplySent(ruleId: Long, phone: String) {
        val normalized = com.jmd.autotext.util.PhoneNumberNormalizer.normalize(phone)
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val now = System.currentTimeMillis()

        val db = dbHelper.writableDatabase
        val sql = """
            INSERT INTO ${DatabaseHelper.TABLE_AUTO_REPLY_HISTORY} (rule_id, phone_number, date_str, reply_count, last_replied_at)
            VALUES (?, ?, ?, 1, ?)
            ON CONFLICT(rule_id, phone_number, date_str)
            DO UPDATE SET reply_count = reply_count + 1, last_replied_at = ?
        """.trimIndent()
        try {
            db.execSQL(sql, arrayOf<Any>(ruleId, normalized, todayStr, now, now))
        } catch (_: Exception) {}
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
            cooldownMinutes = c.getInt(c.getColumnIndexOrThrow("cooldown_minutes")),
            callEventType = c.getString(c.getColumnIndexOrThrow("call_event_type")),
            maxDailyReplies = c.getInt(c.getColumnIndexOrThrow("max_daily_replies")),
            isEnabled = c.getInt(c.getColumnIndexOrThrow("is_enabled")) == 1
        )
    }
}

