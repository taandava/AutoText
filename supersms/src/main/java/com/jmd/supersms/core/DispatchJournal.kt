/**
 * Package: com.jmd.supersms.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Durable exactly-once dispatch journal for scheduled task occurrences.
 *
 * What it does:
 * Records every task occurrence (`task_id` + scheduled trigger time) in SQLite before dispatch and
 * marks the outcome afterwards, so a duplicate alarm, a queue replay after a process restart, or two
 * concurrent triggers can never send the same occurrence twice. Failed occurrences may be retried once;
 * stale in-flight claims are reconciled on app start.
 *
 * Linked With:
 * - Engine: [com.jmd.supersms.engine.SequentialTaskEngine.executeTaskSafely]
 * - Data: [com.jmd.supersms.data.db.DatabaseHelper]
 * - Boot: [com.jmd.supersms.SuperApplication]
 */

package com.jmd.supersms.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.jmd.supersms.data.db.DatabaseHelper

/**
 * What it is:
 * Crash-safe claim/complete/fail ledger for task occurrences.
 *
 * What it does:
 * Uses one row per `(task_id, occurrence_key)` inside transactions, with an in-flight timeout so a
 * process kill during dispatch cannot block the occurrence forever.
 */
object DispatchJournal {

    private const val TAG = "DispatchJournal"

    /** Table holding one row per claimed task occurrence. */
    const val TABLE = "dispatch_journal"

    const val STATE_CLAIMED = "CLAIMED"
    const val STATE_COMPLETED = "COMPLETED"
    const val STATE_FAILED = "FAILED"

    /** A claim older than this is considered abandoned (process death) and may be retried. */
    private const val IN_FLIGHT_TIMEOUT_MS = 10 * 60 * 1000L

    /** Maximum dispatch attempts per occurrence: the original try plus one retry. */
    private const val MAX_ATTEMPTS = 2

    /**
     * What it is:
     * Stable identity for a single scheduled occurrence.
     *
     * What it does:
     * Combines task id with the scheduled trigger time, which only changes when the task is
     * rescheduled for its next occurrence.
     */
    fun occurrenceKey(taskId: Long, triggerTimeMillis: Long): String = "task${taskId}_$triggerTimeMillis"

    /**
     * What it is:
     * Creates the journal table when it does not exist.
     *
     * What it does:
     * Runs `CREATE TABLE IF NOT EXISTS` plus an index, so the schema is added to existing installs
     * without a database version bump.
     */
    fun ensureTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                task_id INTEGER NOT NULL,
                occurrence_key TEXT NOT NULL,
                state TEXT NOT NULL DEFAULT '$STATE_CLAIMED',
                attempts INTEGER NOT NULL DEFAULT 1,
                claimed_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (task_id, occurrence_key)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dispatch_journal_state ON $TABLE(state, claimed_at)")
    }

    /**
     * What it is:
     * Attempts to take ownership of one occurrence.
     *
     * What it does:
     * Grants the claim for a new, failed (retryable) or abandoned occurrence; denies it when the
     * occurrence already completed, is currently in flight, or exhausted its retry budget. Returns
     * `true` (fail-open) if the journal itself errors, so dispatch is never blocked by bookkeeping.
     *
     * @return true when the caller may dispatch.
     */
    @Synchronized
    fun claim(context: Context, taskId: Long, occurrenceKey: String): Boolean {
        val database = try {
            DatabaseHelper.getInstance(context).writableDatabase
        } catch (t: Throwable) {
            AppFileLogger.w(context, TAG, "Journal unavailable, allowing dispatch: ${t.message}")
            return true
        }
        return try {
            ensureTable(database)
            val now = System.currentTimeMillis()
            var state: String? = null
            var attempts = 0
            var claimedAt = 0L
            database.rawQuery(
                "SELECT state, attempts, claimed_at FROM $TABLE WHERE task_id = ? AND occurrence_key = ? LIMIT 1",
                arrayOf(taskId.toString(), occurrenceKey),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    state = cursor.getString(0)
                    attempts = cursor.getInt(1)
                    claimedAt = cursor.getLong(2)
                }
            }

            val granted = database.transaction {
                val allowed = when {
                    state == null -> {
                        insertOrThrow(
                            TABLE,
                            null,
                            ContentValues().apply {
                                put("task_id", taskId)
                                put("occurrence_key", occurrenceKey)
                                put("state", STATE_CLAIMED)
                                put("attempts", 1)
                                put("claimed_at", now)
                                put("updated_at", now)
                            },
                        )
                        true
                    }
                    state == STATE_COMPLETED -> false
                    attempts >= MAX_ATTEMPTS -> false
                    state == STATE_CLAIMED && (now - claimedAt) < IN_FLIGHT_TIMEOUT_MS -> false
                    else -> {
                        update(
                            TABLE,
                            ContentValues().apply {
                                put("state", STATE_CLAIMED)
                                put("attempts", attempts + 1)
                                put("claimed_at", now)
                                put("updated_at", now)
                            },
                            "task_id = ? AND occurrence_key = ?",
                            arrayOf(taskId.toString(), occurrenceKey),
                        )
                        true
                    }
                }
                if (!allowed) {
                    AppFileLogger.action(
                        context,
                        TAG,
                        "Duplicate suppression: task #$taskId occurrence $occurrenceKey already dispatched or in flight (state=$state, attempts=$attempts).",
                    )
                }
                allowed
            }
            granted
        } catch (t: Throwable) {
            AppFileLogger.w(context, TAG, "Journal claim failed, allowing dispatch: ${t.message}")
            true
        }
    }

    /**
     * What it is:
     * Marks an occurrence as successfully dispatched.
     *
     * What it does:
     * Sets state to [STATE_COMPLETED] so the occurrence can never run again.
     */
    @Synchronized
    fun complete(context: Context, taskId: Long, occurrenceKey: String) {
        updateState(context, taskId, occurrenceKey, STATE_COMPLETED)
    }

    /**
     * What it is:
     * Marks an occurrence as failed.
     *
     * What it does:
     * Sets state to [STATE_FAILED], which allows a single retry if attempts remain.
     */
    @Synchronized
    fun fail(context: Context, taskId: Long, occurrenceKey: String) {
        updateState(context, taskId, occurrenceKey, STATE_FAILED)
    }

    /**
     * What it is:
     * Reads the recorded state of an occurrence.
     *
     * What it does:
     * Returns the stored state string, or null when the occurrence was never claimed.
     */
    fun stateOf(context: Context, taskId: Long, occurrenceKey: String): String? {
        return try {
            val database = DatabaseHelper.getInstance(context).readableDatabase
            ensureTable(database)
            database.rawQuery(
                "SELECT state FROM $TABLE WHERE task_id = ? AND occurrence_key = ? LIMIT 1",
                arrayOf(taskId.toString(), occurrenceKey),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * What it is:
     * Startup reconciliation for abandoned claims.
     *
     * What it does:
     * Converts in-flight claims older than the timeout into [STATE_FAILED], so a process kill during
     * dispatch does not permanently suppress that occurrence.
     *
     * @return number of reconciled rows.
     */
    fun reconcileStale(context: Context): Int {
        return try {
            val database = DatabaseHelper.getInstance(context).writableDatabase
            ensureTable(database)
            val cutoff = System.currentTimeMillis() - IN_FLIGHT_TIMEOUT_MS
            val values = ContentValues().apply {
                put("state", STATE_FAILED)
                put("updated_at", System.currentTimeMillis())
            }
            val rows = database.update(
                TABLE,
                values,
                "state = ? AND claimed_at < ?",
                arrayOf(STATE_CLAIMED, cutoff.toString()),
            )
            if (rows > 0) {
                AppFileLogger.action(context, TAG, "Reconciled $rows stale in-flight dispatch claim(s).")
            }
            rows
        } catch (t: Throwable) {
            AppFileLogger.w(context, TAG, "Reconcile failed: ${t.message}")
            0
        }
    }

    /**
     * What it is:
     * Shared state writer for [complete] and [fail].
     *
     * What it does:
     * Updates the matching row and appends a diagnostic trace.
     */
    private fun updateState(context: Context, taskId: Long, occurrenceKey: String, state: String) {
        try {
            val database = DatabaseHelper.getInstance(context).writableDatabase
            ensureTable(database)
            database.update(
                TABLE,
                ContentValues().apply {
                    put("state", state)
                    put("updated_at", System.currentTimeMillis())
                },
                "task_id = ? AND occurrence_key = ?",
                arrayOf(taskId.toString(), occurrenceKey),
            )
        } catch (t: Throwable) {
            AppFileLogger.w(context, TAG, "Journal update to $state failed: ${t.message}")
        }
    }
}
