package com.jmd.autotext.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jmd.autotext.model.*
import org.json.JSONArray
import org.json.JSONObject

class DatabaseHelper private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "autotext.db"
        const val DATABASE_VERSION = 4

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }

        // Tables
        const val TABLE_TASKS = "tasks"
        const val TABLE_RECIPIENTS = "recipients"
        const val TABLE_TEMPLATES = "templates"
        const val TABLE_AUTO_REPLY = "auto_reply_rules"
        const val TABLE_AUTO_FORWARD = "auto_forward_rules"
        const val TABLE_CAMPAIGNS = "campaigns"
        const val TABLE_DRIP_STEPS = "drip_steps"
        const val TABLE_LOGS = "execution_logs"
        const val TABLE_DYNAMIC_POOL = "dynamic_contact_pool"
        const val TABLE_DAILY_CAMPAIGN = "daily_campaign_config"
        const val TABLE_AUTO_REPLY_HISTORY = "auto_reply_history"
        const val TABLE_CONTACT_SEQUENCES = "contact_sequences"
        const val TABLE_SEQUENCE_STEPS = "sequence_steps"
        const val TABLE_WHATSAPP_GROUPS = "whatsapp_groups"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_TASKS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                channel TEXT,
                recipients TEXT,
                recipient_names TEXT,
                message_text TEXT,
                trigger_time INTEGER,
                recurrence TEXT,
                custom_interval_minutes INTEGER,
                days_of_week TEXT,
                status TEXT,
                sim_slot INTEGER,
                ask_before_send INTEGER,
                sound_mode TEXT,
                fake_caller_name TEXT,
                fake_caller_number TEXT,
                fake_call_style TEXT,
                created_at INTEGER,
                last_executed INTEGER,
                execution_count INTEGER,
                media_uri TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_RECIPIENTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                phone_or_email TEXT,
                group_name TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_TEMPLATES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                category TEXT,
                content TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_AUTO_REPLY (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                channel TEXT,
                reply_text TEXT,
                trigger_pattern TEXT,
                is_exact_match INTEGER,
                reply_contacts_only INTEGER,
                reply_non_contacts_only INTEGER,
                start_hour INTEGER,
                start_minute INTEGER,
                end_hour INTEGER,
                end_minute INTEGER,
                cooldown_minutes INTEGER,
                call_event_type TEXT DEFAULT 'BOTH',
                max_daily_replies INTEGER DEFAULT 1,
                is_enabled INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_AUTO_FORWARD (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                source_type TEXT,
                forward_to_number TEXT,
                filter_sender TEXT,
                filter_keyword TEXT,
                is_enabled INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_CAMPAIGNS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT,
                recipients TEXT,
                is_enabled INTEGER,
                current_step INTEGER,
                total_steps INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_DRIP_STEPS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                campaign_id INTEGER,
                step_order INTEGER,
                delay_days INTEGER,
                delay_hours INTEGER,
                message_text TEXT,
                channel TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_LOGS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id INTEGER,
                channel TEXT,
                recipient TEXT,
                message_snippet TEXT,
                status TEXT,
                details TEXT,
                timestamp INTEGER
            )
        """.trimIndent())

        ensureDynamicPoolTables(db)

        // Insert Default Templates
        insertSeedTemplates(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureDynamicPoolTables(db)
        // Automatic cleanup of non-mobile shortcodes (e.g. 100, 101, 102, 199, 1930) accidentally ingested
        try {
            db.execSQL("DELETE FROM $TABLE_DYNAMIC_POOL WHERE LENGTH(REPLACE(REPLACE(phone, '+', ''), ' ', '')) < 10")
        } catch (_: Exception) {}
        // Automatic cleanup of corrupt groups (e.g., status rows, drafts, meta AI)
        try {
            db.execSQL("""
                DELETE FROM $TABLE_WHATSAPP_GROUPS 
                WHERE LOWER(name) LIKE '%status%' 
                   OR LOWER(name) LIKE '%disappears%' 
                   OR LOWER(name) LIKE '%joined using%' 
                   OR LOWER(name) LIKE '%photo%' 
                   OR LOWER(name) LIKE '%video%' 
                   OR LOWER(name) LIKE '%meta ai%' 
                   OR LOWER(name) LIKE '%http%' 
                   OR name LIKE '~%' 
                   OR LENGTH(name) < 3
            """.trimIndent())
        } catch (_: Exception) {}
    }

    /**
     * Version-gated upgrade path.
     *
     * IMPORTANT: this method must never perform unconditional data deletion.
     * The previous implementation ran two blanket `DELETE` statements on every
     * upgrade regardless of [oldVersion]/[newVersion], which would silently
     * prune a user's contact pool and WhatsApp groups again on any future
     * version bump.
     *
     * Each step below is therefore scoped to the version boundary that
     * introduced the need for it, and runs at most once per database.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema additions are idempotent (CREATE TABLE / INDEX IF NOT EXISTS)
        // and are safe to re-run on every upgrade.
        ensureDynamicPoolTables(db)

        // --- v3 -> v4 -------------------------------------------------------
        // v4 introduced stricter validation for WhatsApp groups and a
        // normalized-number requirement for the dynamic pool. Rows that were
        // ingested before those constraints existed can be non-functional, so
        // they are pruned once, during this boundary only.
        //
        // Guarded on oldVersion so that an already-v4 database upgrading to v5
        // (or later) never re-runs these deletions.
        if (oldVersion < 4) {
            pruneLegacyPoolAndGroupRows(db)
        }
    }

    /**
     * Removes rows that predate the v4 validation rules.
     *
     * Deliberately conservative: only rows that are structurally unusable are
     * removed. The previous `LENGTH(name) < 3` predicate has been dropped
     * because a short-but-valid group name is legitimate user data.
     */
    private fun pruneLegacyPoolAndGroupRows(db: SQLiteDatabase) {
        runCatching {
            // A pool entry with no usable phone number in either column can
            // never be contacted.
            db.execSQL(
                "DELETE FROM $TABLE_DYNAMIC_POOL " +
                    "WHERE (normalized_number IS NULL OR LENGTH(TRIM(normalized_number)) < 10) " +
                    "AND (phone IS NULL OR LENGTH(TRIM(phone)) < 10)"
            )
        }

        runCatching {
            // WhatsApp injects transient system rows (status updates, join
            // notices, Meta AI, link previews) into the chat list. These were
            // mistakenly persisted as "groups" before v4.
            db.execSQL(
                """
                DELETE FROM $TABLE_WHATSAPP_GROUPS
                WHERE LOWER(TRIM(name)) LIKE '%status%'
                   OR LOWER(TRIM(name)) LIKE '%disappears%'
                   OR LOWER(TRIM(name)) LIKE '%joined using%'
                   OR LOWER(TRIM(name)) LIKE '%meta ai%'
                   OR LOWER(TRIM(name)) LIKE '%http%'
                   OR TRIM(name) LIKE '~%'
                """.trimIndent()
            )
        }
    }

    private fun addColumnIfNotExists(db: SQLiteDatabase, table: String, column: String, typeDef: String) {
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("PRAGMA table_info($table)", null)
            val nameIndex = cursor.getColumnIndex("name")
            var exists = false
            if (nameIndex != -1) {
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                        exists = true
                        break
                    }
                }
            }
            if (!exists) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $column $typeDef")
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
    }

    private fun ensureDynamicPoolTables(db: SQLiteDatabase) {
        addColumnIfNotExists(db, TABLE_TASKS, "media_uri", "TEXT DEFAULT ''")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_DYNAMIC_POOL (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL DEFAULT '',
                phone TEXT NOT NULL,
                normalized_number TEXT NOT NULL UNIQUE,
                source TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                last_sent_at INTEGER DEFAULT 0,
                send_count INTEGER DEFAULT 0,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_pool_normalized ON $TABLE_DYNAMIC_POOL(normalized_number)") } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_pool_last_sent ON $TABLE_DYNAMIC_POOL(last_sent_at)") } catch (_: Exception) {}
        // Independent tracking for the WhatsApp daily campaign
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "wa_last_sent_at", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "wa_send_count", "INTEGER DEFAULT 0")
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_pool_wa_last_sent ON $TABLE_DYNAMIC_POOL(wa_last_sent_at)") } catch (_: Exception) {}
        // Fallback / alternate column names to guarantee zero crashes across schema variations
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "last_sms_sent", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "sms_sent_count", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "last_wa_sent", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "wa_sent_count", "INTEGER DEFAULT 0")
        // Retry priority and status tracking for failed/queued numbers
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "sms_status", "TEXT DEFAULT 'PENDING'")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "wa_status", "TEXT DEFAULT 'PENDING'")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "sms_retry_priority", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "wa_retry_priority", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "photo_uri", "TEXT DEFAULT ''")
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_pool_sms_priority ON $TABLE_DYNAMIC_POOL(sms_retry_priority)") } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_pool_wa_priority ON $TABLE_DYNAMIC_POOL(wa_retry_priority)") } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_pool_enabled_name ON $TABLE_DYNAMIC_POOL(enabled, name)") } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_pool_wa_status ON $TABLE_DYNAMIC_POOL(enabled, wa_send_count, wa_status)") } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_pool_sms_status ON $TABLE_DYNAMIC_POOL(enabled, send_count, sms_status)") } catch (_: Exception) {}

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_DAILY_CAMPAIGN (
                id INTEGER PRIMARY KEY,
                channel TEXT DEFAULT 'SMS',
                title TEXT,
                message_template TEXT,
                daily_limit INTEGER DEFAULT 30,
                interval_seconds INTEGER DEFAULT 8,
                scheduled_hour INTEGER DEFAULT 9,
                scheduled_minute INTEGER DEFAULT 0,
                sim_slot INTEGER DEFAULT 0,
                auto_scan_enabled INTEGER DEFAULT 1,
                is_active INTEGER DEFAULT 0,
                last_run_date TEXT DEFAULT ''
            )
        """.trimIndent())

        addColumnIfNotExists(db, TABLE_DAILY_CAMPAIGN, "channel", "TEXT DEFAULT 'SMS'")

        val defaultCampaign = ContentValues().apply {
            put("id", 1)
            put("channel", "SMS")
            put("title", "Daily Unique Contacts SMS")
            put("message_template", "Hi {name}, wishing you a wonderful and successful day ahead!")
            put("daily_limit", 30)
            put("interval_seconds", 8)
            put("scheduled_hour", 9)
            put("scheduled_minute", 0)
            put("sim_slot", 0)
            put("auto_scan_enabled", 1)
            put("is_active", 0)
            put("last_run_date", "")
        }
        db.insertWithOnConflict(TABLE_DAILY_CAMPAIGN, null, defaultCampaign, SQLiteDatabase.CONFLICT_IGNORE)

        val defaultWhatsAppCampaign = ContentValues().apply {
            put("id", 2)
            put("channel", "WHATSAPP")
            put("title", "Daily Unique Contacts WhatsApp")
            put("message_template", "Hi {name}, wishing you a wonderful and successful day ahead! 🌟")
            put("daily_limit", 20)
            put("interval_seconds", 12)
            put("scheduled_hour", 10)
            put("scheduled_minute", 0)
            put("sim_slot", 0)
            put("auto_scan_enabled", 1)
            put("is_active", 0)
            put("last_run_date", "")
        }
        db.insertWithOnConflict(TABLE_DAILY_CAMPAIGN, null, defaultWhatsAppCampaign, SQLiteDatabase.CONFLICT_IGNORE)

        // Migrations & tables for Auto-Reply with Missed & Ended Calls and daily frequency limits
        addColumnIfNotExists(db, TABLE_AUTO_REPLY, "start_hour", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_AUTO_REPLY, "start_minute", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_AUTO_REPLY, "end_hour", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_AUTO_REPLY, "end_minute", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_AUTO_REPLY, "start_time_hour", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_AUTO_REPLY, "start_time_minute", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_AUTO_REPLY, "end_time_hour", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_AUTO_REPLY, "end_time_minute", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_AUTO_REPLY, "call_event_type", "TEXT DEFAULT 'BOTH'")
        addColumnIfNotExists(db, TABLE_AUTO_REPLY, "max_daily_replies", "INTEGER DEFAULT 1")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_AUTO_REPLY_HISTORY (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_id INTEGER,
                phone_number TEXT,
                date_str TEXT,
                reply_count INTEGER DEFAULT 1,
                last_replied_at INTEGER,
                UNIQUE(rule_id, phone_number, date_str)
            )
        """.trimIndent())
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reply_history_lookup ON $TABLE_AUTO_REPLY_HISTORY(rule_id, phone_number, date_str)")
        } catch (_: Exception) {}

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_CONTACT_SEQUENCES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                contact_name TEXT DEFAULT '',
                contact_phone TEXT NOT NULL,
                title TEXT DEFAULT '',
                is_enabled INTEGER DEFAULT 1,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SEQUENCE_STEPS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sequence_id INTEGER NOT NULL,
                step_order INTEGER NOT NULL,
                channel TEXT NOT NULL,
                message_text TEXT NOT NULL,
                delay_minutes INTEGER DEFAULT 0,
                trigger_time INTEGER DEFAULT 0,
                sim_slot INTEGER DEFAULT 0
            )
        """.trimIndent())
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_seq_steps ON $TABLE_SEQUENCE_STEPS(sequence_id)") } catch (_: Exception) {}

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_WHATSAPP_GROUPS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                group_jid TEXT DEFAULT '',
                member_count TEXT DEFAULT '',
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_wa_groups_name ON $TABLE_WHATSAPP_GROUPS(name)") } catch (_: Exception) {}
        addColumnIfNotExists(db, TABLE_WHATSAPP_GROUPS, "group_jid", "TEXT DEFAULT ''")
        addColumnIfNotExists(db, TABLE_WHATSAPP_GROUPS, "member_count", "TEXT DEFAULT ''")
    }

    private fun insertSeedTemplates(db: SQLiteDatabase) {
        val seeds = listOf(
            Triple("Birthday Wish", "Personal", "Happy Birthday {name}! Wishing you a wonderful year filled with happiness and success! 🎉🎂"),
            Triple("Meeting Reminder", "Business", "Hi {name}, this is a gentle reminder regarding our scheduled meeting today at {time}. Looking forward to connecting."),
            Triple("Out of Office Auto Reply", "Work", "Hello! I am currently away from my desk with limited access to messages. I will respond to your message upon my return. Thank you!"),
            Triple("Payment Follow-up", "Business", "Dear {name}, friendly reminder regarding the pending invoice. Please let me know once processed. Thank you!"),
            Triple("Appointment Confirmation", "Service", "Hi {name}, your appointment is confirmed for {date} at {time}. Please reply YES to confirm or let us know if you need to reschedule.")
        )
        for ((title, category, content) in seeds) {
            val cv = ContentValues().apply {
                put("title", title)
                put("category", category)
                put("content", content)
            }
            db.insert(TABLE_TEMPLATES, null, cv)
        }
    }
}
