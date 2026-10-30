

/**
 * Package: com.jmd.autotext.data.db
 * Module: Data Persistence Layer
 *
 * Provides thread-safe SQLite database initialization, schema versioning, transaction isolation,
 * and automated schema migrations across database versions 1 through 6.
 *
 * Linked With:
 * - Repositories: [com.jmd.autotext.data.repo.AppRepository], [com.jmd.autotext.data.repo.TaskRepository],
 *   [com.jmd.autotext.data.repo.DailyCampaignRepository], [com.jmd.autotext.data.repo.LogRepository],
 *   [com.jmd.autotext.data.repo.SequenceRepository], [com.jmd.autotext.data.repo.AutoReplyRepository],
 *   [com.jmd.autotext.data.repo.TemplateRepository], [com.jmd.autotext.data.repo.WhatsAppRepository]
 * - Engines: [com.jmd.autotext.engine.SequentialTaskEngine], [com.jmd.autotext.engine.ContactIngestionEngine]
 * - Schedulers: [com.jmd.autotext.scheduler.DailyCampaignWorker], [com.jmd.autotext.scheduler.AlarmScheduler]
 */
package com.jmd.autotext.data.db

import android.content.*
import android.database.Cursor
import android.util.Log
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Thread-safe SQLite OpenHelper managing all relational persistence tables for AutoText.
 *
 * Manages 15 relational tables:
 * - tasks: Scheduled automation tasks across SMS, WhatsApp, Calls, Reminders, and Sound modes.
 * - recipients: Contact book recipients and metadata.
 * - templates: Quick message templates and dynamic token strings.
 * - auto_reply_rules: Pattern matching rules for automated incoming replies.
 * - campaigns / drip_steps: Marketing campaigns and multi-phase drip sequences.
 * - execution_logs: Comprehensive on-device audit logs for every automation attempt.
 * - dynamic_contact_pool: Ingested contact pool for daily outreach campaigns.
 * - daily_campaign_config: Automated daily batch dispatch settings and throttle pacing.
 * - auto_reply_history: Duplicate-suppression history for incoming notification auto-replies.
 * - contact_sequences: Multi-step follow-up sequences for individual contacts.
 * - sequence_steps: Individual drip steps belonging to contact follow-up sequences.
 * - whatsapp_groups: WhatsApp and WhatsApp Business group metadata and participant caches.
 * - daily_campaign_sent_ledger: Rolling sent log for daily campaign contact deduplication.
 * - task_recipient_deliveries: Per-recipient status tracking for bulk dispatches.
 */
class DatabaseHelper private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "autotext.db"
        const val DATABASE_VERSION = 8

        @Volatile
        private var instance: DatabaseHelper? = null

        /**
         * Returns the thread-safe singleton instance of [DatabaseHelper].
         *
         * @param context Target Android application or activity context.
         * @return Singleton [DatabaseHelper] instance.
         */
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
        const val TABLE_CAMPAIGNS = "campaigns"
        const val TABLE_DRIP_STEPS = "drip_steps"
        const val TABLE_LOGS = "execution_logs"
        const val TABLE_DYNAMIC_POOL = "dynamic_contact_pool"
        const val TABLE_DAILY_CAMPAIGN = "daily_campaign_config"
        const val TABLE_AUTO_REPLY_HISTORY = "auto_reply_history"
        const val TABLE_CONTACT_SEQUENCES = "contact_sequences"
        const val TABLE_SEQUENCE_STEPS = "sequence_steps"
        const val TABLE_WHATSAPP_GROUPS = "whatsapp_groups"
        const val TABLE_DAILY_CAMPAIGN_SENT = "daily_campaign_sent_ledger"
        const val TABLE_TASK_DELIVERIES = "task_recipient_deliveries"
        const val TABLE_APP_PRIVACY_CONFIGS = "app_privacy_configs"
    }

    /**
     * Configures database connection before schema creation, migrations, or queries.
     * Enables SQLite Write-Ahead Logging (WAL) for high-concurrency read/write operations
     * and enforces foreign key relational constraints.
     */
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            db.enableWriteAheadLogging()
            db.setForeignKeyConstraintsEnabled(true)
        } catch (e: Exception) {
            android.util.Log.w("DatabaseHelper", "onConfigure WAL/ForeignKeys failed: ${e.message}")
        }
    }

    /**
     * Executes initial schema creation for all 15 relational tables, indices, and seed records.
     *
     * @param db Target SQLiteDatabase instance.
     */
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_TASKS (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, channel TEXT, recipients TEXT, recipient_names TEXT, message_text TEXT, trigger_time INTEGER, recurrence TEXT, custom_interval_minutes INTEGER, days_of_week TEXT, status TEXT, sim_slot INTEGER, ask_before_send INTEGER, sound_mode TEXT, fake_caller_name TEXT, fake_caller_number TEXT, fake_call_style TEXT, created_at INTEGER, last_executed INTEGER, execution_count INTEGER, media_uri TEXT, call_duration_seconds INTEGER DEFAULT 0, include_dial_time INTEGER DEFAULT 0, call_sequence_mode TEXT DEFAULT 'GIVEN', delay_between_calls_seconds INTEGER DEFAULT 5, call_failure_policy TEXT DEFAULT 'CONTINUE')")
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_status_trigger ON $TABLE_TASKS(status, trigger_time)") } catch (_: Exception) {}
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_APP_PRIVACY_CONFIGS (
                package_name TEXT PRIMARY KEY,
                app_name TEXT NOT NULL DEFAULT '',
                is_excluded INTEGER NOT NULL DEFAULT 0,
                disable_automation INTEGER NOT NULL DEFAULT 0,
                block_overlays INTEGER NOT NULL DEFAULT 0,
                suppress_notifications INTEGER NOT NULL DEFAULT 0,
                disable_clipboard INTEGER NOT NULL DEFAULT 0,
                mask_sensitive_content INTEGER NOT NULL DEFAULT 0,
                disable_rules INTEGER NOT NULL DEFAULT 0,
                custom_notes TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_privacy_pkg ON $TABLE_APP_PRIVACY_CONFIGS(package_name)") } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_privacy_excluded ON $TABLE_APP_PRIVACY_CONFIGS(is_excluded)") } catch (_: Exception) {}

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_APP_PRIVACY_CONFIGS (
                package_name TEXT PRIMARY KEY,
                app_name TEXT NOT NULL,
                is_excluded INTEGER NOT NULL DEFAULT 1,
                disable_automation INTEGER NOT NULL DEFAULT 1,
                block_overlays INTEGER NOT NULL DEFAULT 1,
                suppress_notifications INTEGER NOT NULL DEFAULT 1,
                disable_clipboard INTEGER NOT NULL DEFAULT 1,
                mask_sensitive_content INTEGER NOT NULL DEFAULT 1,
                disable_rules INTEGER NOT NULL DEFAULT 1,
                custom_notes TEXT DEFAULT '',
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_privacy_pkg ON $TABLE_APP_PRIVACY_CONFIGS(package_name)") } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_privacy_excluded ON $TABLE_APP_PRIVACY_CONFIGS(is_excluded)") } catch (_: Exception) {}

        db.execSQL("CREATE TABLE $TABLE_RECIPIENTS (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, phone_or_email TEXT, group_name TEXT)")

        db.execSQL("CREATE TABLE $TABLE_TEMPLATES (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, category TEXT, content TEXT)")

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
                segments INTEGER DEFAULT 1,
                timestamp INTEGER
            )
        """.trimIndent())

        ensureDynamicPoolTables(db)

        // Insert Default Templates
        insertSeedTemplates(db)
    }

    /**
     * Invoked whenever the database connection is opened.
     * Ensures all dynamic pool tables, supplemental columns, and legacy group row prunings are enforced.
     *
     * @param db Target SQLiteDatabase instance.
     */
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureDynamicPoolTables(db)
        pruneLegacyPoolAndGroupRows(db)
    }

    /**
     * Executes non-destructive schema migrations when upgrading across database versions.
     *
     * @param db Target SQLiteDatabase instance.
     * @param oldVersion Pre-existing database version number.
     * @param newVersion Target updated database version number.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureDynamicPoolTables(db)
        deduplicateAllPools(db)

        if (oldVersion < 4) {
            pruneLegacyPoolAndGroupRows(db)
        }
    }

    /**
     * Deduplicates dynamic contact pool records, group entries, and recipient lists by normalized number.
     * Enforces unique indices to prevent redundant rows from entering tables in future operations.
     *
     * @param db Target SQLiteDatabase instance.
     */
    private fun deduplicateAllPools(db: SQLiteDatabase) {
        runCatching {
            // 1. Deduplicate dynamic_contact_pool by normalized_number
            db.execSQL("""
                DELETE FROM $TABLE_DYNAMIC_POOL
                WHERE id NOT IN (
                    SELECT MIN(id)
                    FROM $TABLE_DYNAMIC_POOL
                    GROUP BY normalized_number
                )
            """.trimIndent())

            // 2. Deduplicate group items in dynamic_contact_pool by group name
            db.execSQL("""
                DELETE FROM $TABLE_DYNAMIC_POOL
                WHERE normalized_number LIKE 'group:%'
                  AND id NOT IN (
                    SELECT MIN(id)
                    FROM $TABLE_DYNAMIC_POOL
                    WHERE normalized_number LIKE 'group:%'
                    GROUP BY LOWER(TRIM(name))
                )
            """.trimIndent())

            // 3. Deduplicate whatsapp_groups by group name
            db.execSQL("""
                DELETE FROM $TABLE_WHATSAPP_GROUPS
                WHERE id NOT IN (
                    SELECT MIN(id)
                    FROM $TABLE_WHATSAPP_GROUPS
                    GROUP BY LOWER(TRIM(name))
                )
            """.trimIndent())

            // 4. Deduplicate recipients table by phone_or_email
            db.execSQL("""
                DELETE FROM $TABLE_RECIPIENTS
                WHERE id NOT IN (
                    SELECT MIN(id)
                    FROM $TABLE_RECIPIENTS
                    GROUP BY phone_or_email
                )
            """.trimIndent())

            // 5. Ensure UNIQUE indexes exist to permanently prevent future duplicate inserts
            try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_pool_unique_norm ON $TABLE_DYNAMIC_POOL(normalized_number)") } catch (_: Exception) {}
            try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_groups_unique_name ON $TABLE_WHATSAPP_GROUPS(name)") } catch (_: Exception) {}
        }
    }

    /**
     * Handles non-destructive schema adjustments during app downgrades to prevent app crashes.
     *
     * @param db Target SQLiteDatabase instance.
     * @param oldVersion Pre-existing higher database version.
     * @param newVersion Target downgraded database version.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Defensive non-destructive downgrade to avoid SQLiteException crashes
        ensureDynamicPoolTables(db)
    }

    /**
     * Cleanses legacy and corrupted entries from dynamic contact pools and WhatsApp group tables.
     * Removes status text, ephemeral indicators, web links, and individual contacts mistakenly classified as groups.
     *
     * @param db Target SQLiteDatabase instance.
     */
    private fun pruneLegacyPoolAndGroupRows(db: SQLiteDatabase) {
        runCatching {
            db.execSQL(
                "DELETE FROM $TABLE_DYNAMIC_POOL " +
                    "WHERE phone NOT LIKE 'group:%' AND phone NOT LIKE 'chat:%' AND phone NOT LIKE 'broadcast:%' " +
                    "AND source NOT IN ('WA_CHAT', 'WA_BUSINESS', 'WA_GROUP', 'WA_BROADCAST', 'WA_STATUS') " +
                    "AND (normalized_number IS NULL OR LENGTH(TRIM(normalized_number)) < 7) " +
                    "AND (phone IS NULL OR LENGTH(TRIM(phone)) < 7)"
            )
        }

        runCatching {
            db.execSQL(
                """
                DELETE FROM $TABLE_WHATSAPP_GROUPS
                WHERE LOWER(TRIM(name)) LIKE '%status%'
                   OR LOWER(TRIM(name)) LIKE '%disappears%'
                   OR LOWER(TRIM(name)) LIKE '%joined using%'
                   OR LOWER(TRIM(name)) LIKE '%meta ai%'
                   OR LOWER(TRIM(name)) LIKE '%http%'
                   OR TRIM(name) LIKE '~%'
                   OR TRIM(name) IN (
                       SELECT TRIM(name) FROM $TABLE_DYNAMIC_POOL
                       WHERE normalized_number NOT LIKE 'group:%'
                         AND normalized_number NOT LIKE 'broadcast:%'
                         AND normalized_number NOT LIKE 'chat:%'
                         AND LENGTH(phone) >= 7
                   )
                """.trimIndent()
            )
        }

        runCatching {
            db.execSQL(
                """
                DELETE FROM $TABLE_DYNAMIC_POOL
                WHERE normalized_number LIKE 'group:%'
                  AND TRIM(name) IN (
                      SELECT TRIM(name) FROM $TABLE_DYNAMIC_POOL
                      WHERE normalized_number NOT LIKE 'group:%'
                        AND normalized_number NOT LIKE 'broadcast:%'
                        AND normalized_number NOT LIKE 'chat:%'
                        AND LENGTH(phone) >= 7
                  )
                """.trimIndent()
            )
        }
    }

    /**
     * Inspects PRAGMA table_info to safely append a column to a table if it does not yet exist.
     *
     * @param db Target SQLiteDatabase instance.
     * @param table Name of the table to inspect.
     * @param column Name of the column to append if absent.
     * @param typeDef SQLite type and optional default value declaration (e.g. "TEXT DEFAULT ''").
     */
    private fun addColumnIfNotExists(db: SQLiteDatabase, table: String, column: String, typeDef: String) {
        try {
            var exists = false
            db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex != -1) {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                            exists = true
                            break
                        }
                    }
                }
            }
            if (!exists) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $column $typeDef")
            }
        } catch (e: Exception) {
            Log.w("DatabaseHelper", "Failed to add column $column to $table: ${e.message}")
        }
    }

    /**
     * Initializes and verifies all secondary persistence tables, supplemental feature columns,
     * seed campaigns, and performance indices across dynamic contact pools and delivery ledgers.
     *
     * @param db Target SQLiteDatabase instance.
     */
    private fun ensureDynamicPoolTables(db: SQLiteDatabase) {
        addColumnIfNotExists(db, TABLE_TASKS, "media_uri", "TEXT DEFAULT ''")
        addColumnIfNotExists(db, TABLE_TASKS, "custom_interval_minutes", "INTEGER DEFAULT 60")
        addColumnIfNotExists(db, TABLE_TASKS, "sound_mode", "TEXT DEFAULT 'NORMAL'")
        addColumnIfNotExists(db, TABLE_TASKS, "fake_caller_name", "TEXT DEFAULT ''")
        addColumnIfNotExists(db, TABLE_TASKS, "fake_caller_number", "TEXT DEFAULT ''")
        addColumnIfNotExists(db, TABLE_TASKS, "fake_call_style", "TEXT DEFAULT 'PIXEL'")
        addColumnIfNotExists(db, TABLE_TASKS, "call_duration_seconds", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_TASKS, "include_dial_time", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_TASKS, "call_sequence_mode", "TEXT DEFAULT 'GIVEN'")
        addColumnIfNotExists(db, TABLE_TASKS, "delay_between_calls_seconds", "INTEGER DEFAULT 5")
        addColumnIfNotExists(db, TABLE_TASKS, "call_failure_policy", "TEXT DEFAULT 'CONTINUE'")
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
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "wa_last_sent_at", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "wa_send_count", "INTEGER DEFAULT 0")
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_pool_wa_last_sent ON $TABLE_DYNAMIC_POOL(wa_last_sent_at)") } catch (_: Exception) {}
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "last_sms_sent", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "sms_sent_count", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "last_wa_sent", "INTEGER DEFAULT 0")
        addColumnIfNotExists(db, TABLE_DYNAMIC_POOL, "wa_sent_count", "INTEGER DEFAULT 0")
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
            CREATE TABLE IF NOT EXISTS $TABLE_DAILY_CAMPAIGN_SENT (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                campaign_id INTEGER NOT NULL,
                contact_id INTEGER NOT NULL,
                phone TEXT NOT NULL,
                normalized_number TEXT NOT NULL,
                channel TEXT NOT NULL,
                sent_at INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'SUCCESS',
                segments INTEGER DEFAULT 1,
                UNIQUE(campaign_id, normalized_number)
            )
        """.trimIndent())
        addColumnIfNotExists(db, TABLE_DAILY_CAMPAIGN_SENT, "segments", "INTEGER DEFAULT 1")
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_dc_sent_camp ON $TABLE_DAILY_CAMPAIGN_SENT(campaign_id)") } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_dc_sent_camp_contact ON $TABLE_DAILY_CAMPAIGN_SENT(campaign_id, contact_id)") } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_dc_sent_camp_norm ON $TABLE_DAILY_CAMPAIGN_SENT(campaign_id, normalized_number)") } catch (_: Exception) {}

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
            put("message_template", "")
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
            put("message_template", "")
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

        val defaultWhatsAppBusinessCampaign = ContentValues().apply {
            put("id", 3)
            put("channel", "WHATSAPP_BUSINESS")
            put("title", "Daily WhatsApp Business Campaign")
            put("message_template", "")
            put("daily_limit", 20)
            put("interval_seconds", 12)
            put("scheduled_hour", 10)
            put("scheduled_minute", 30)
            put("sim_slot", 0)
            put("auto_scan_enabled", 1)
            put("is_active", 0)
            put("last_run_date", "")
        }
        db.insertWithOnConflict(TABLE_DAILY_CAMPAIGN, null, defaultWhatsAppBusinessCampaign, SQLiteDatabase.CONFLICT_IGNORE)

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
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_TASK_DELIVERIES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id INTEGER NOT NULL,
                occurrence_id TEXT NOT NULL DEFAULT '',
                recipient TEXT NOT NULL,
                normalized_number TEXT NOT NULL,
                channel TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'SUCCESS',
                dispatched_at INTEGER NOT NULL,
                UNIQUE(task_id, occurrence_id, normalized_number)
            )
        """.trimIndent())
        addColumnIfNotExists(db, TABLE_TASK_DELIVERIES, "occurrence_id", "TEXT NOT NULL DEFAULT ''")
        addColumnIfNotExists(db, TABLE_LOGS, "segments", "INTEGER DEFAULT 1")
        // Backfill legacy logs where segments column defaulted to 1 but details recorded multiple segments/parts
        try {
            db.rawQuery("SELECT id, details, message_snippet FROM $TABLE_LOGS WHERE segments <= 1 AND (details LIKE '%parts:%' OR details LIKE '%segments:%')", null).use { cursor ->
                val idIdx = cursor.getColumnIndex("id")
                val detIdx = cursor.getColumnIndex("details")
                while (cursor.moveToNext()) {
                    val rowId = cursor.getLong(idIdx)
                    val details = cursor.getString(detIdx) ?: ""
                    val partsMatch = Regex("(?:parts|segments):\\s*(\\d+)").find(details)
                    val realSegments = partsMatch?.groupValues?.get(1)?.toIntOrNull()
                    if ((realSegments != null) && (realSegments > 1)) {
                        db.execSQL("UPDATE $TABLE_LOGS SET segments = ? WHERE id = ?", arrayOf<Any>(realSegments, rowId))
                    }
                }
            }
        } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_task_deliv_lookup ON $TABLE_TASK_DELIVERIES(task_id, occurrence_id, normalized_number)") } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_status_trigger ON $TABLE_TASKS(status, trigger_time)") } catch (_: Exception) {}
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_APP_PRIVACY_CONFIGS (
                package_name TEXT PRIMARY KEY,
                app_name TEXT NOT NULL DEFAULT '',
                is_excluded INTEGER NOT NULL DEFAULT 0,
                disable_automation INTEGER NOT NULL DEFAULT 0,
                block_overlays INTEGER NOT NULL DEFAULT 0,
                suppress_notifications INTEGER NOT NULL DEFAULT 0,
                disable_clipboard INTEGER NOT NULL DEFAULT 0,
                mask_sensitive_content INTEGER NOT NULL DEFAULT 0,
                disable_rules INTEGER NOT NULL DEFAULT 0,
                custom_notes TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_privacy_pkg ON $TABLE_APP_PRIVACY_CONFIGS(package_name)") } catch (_: Exception) {}
        try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_privacy_excluded ON $TABLE_APP_PRIVACY_CONFIGS(is_excluded)") } catch (_: Exception) {}
    }

    /**
     * Seeds the templates table with standard default categories and contextual placeholders upon initial creation.
     *
     * @param db Target SQLiteDatabase instance.
     */
    private fun insertSeedTemplates(db: SQLiteDatabase) {
        // No prebuilt seed templates; all text fields start empty by design
    }

    // 1-word / 2-word aliases


}
