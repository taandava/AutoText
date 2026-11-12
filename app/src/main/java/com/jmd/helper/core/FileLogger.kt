/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [FileLogger].
 *
 * What it does:
 * Provides persistent on-device diagnostic logging, campaign dispatch CSV ledgers, error tracking, scheduled file rotation (3 MB threshold), and log export.
 *
 * Linked With:
 * - Core Security & Utilities: [android.util.Log], [java.util.*]
 */

package com.jmd.helper.core

import android.content.Context
import android.util.Log
import com.jmd.helper.HelperApplication
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * What it is:
 * Enterprise logging subsystem for recording task lifecycles and campaign ledger audits.
 *
 * What it does:
 * Persists log entries directly to private application internal storage (`files/logs/`).
 *
 * Linked with:
 * - Architecture: [FileLogger], [AppFileLogger]
 */
object AppFileLogger {
    private const val TAG = "AppFileLogger"
    private const val LOG_DIR_NAME = "logs"
    private const val EXEC_LOG_FILE = "helper_execution.log"
    private const val ERROR_LOG_FILE = "helper_error.log"
    private const val LEDGER_FILE = "helper_campaign_ledger.csv"
    private const val MAX_FILE_SIZE_BYTES = 3 * 1024 * 1024L // 3 MB

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val csvDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * What it is:
     * Records a diagnostic message into internal log files with direct logcat mirroring.
     *
     * What it does:
     * Executes [log] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [log]
     * @param context Active Android [Context].
     * @param level Severity tag ("DEBUG", "INFO", "WARN", "ACTION", "ERROR").
     * @param tag Calling component identifier.
     * @param message Informational text string.
     * @param throwable Optional exception with stack trace.
     */
    @Synchronized
    fun log(context: Context, level: String, tag: String, message: String, throwable: Throwable? = null) {
        val lvl = level.uppercase(Locale.US)

        when (lvl) {
            "ERROR" -> Log.e(tag, message, throwable)
            "WARN" -> Log.w(tag, message, throwable)
            "ACTION" -> Log.i(tag, "⚡ [ACTION] $message", throwable)
            else -> Log.d(tag, message, throwable)
        }

        try {
            val logFile = getLogFile(context) ?: return
            rotateIfNecessary(logFile)

            val timestamp = dateFormat.format(Date())
            val logLine = "[$timestamp] [$lvl] [$tag] $message"

            FileWriter(logFile, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println(logLine)
                    throwable?.printStackTrace(pw)
                }
            }

            if ((lvl == "ERROR") || (throwable != null)) {
                val errFile = getErrorFile(context)
                if (errFile != null) {
                    rotateIfNecessary(errFile)
                    FileWriter(errFile, true).use { fw ->
                        PrintWriter(fw).use { pw ->
                            pw.println("[$timestamp] [$tag] $message")
                            throwable?.printStackTrace(pw)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing to log file: ${e.message}")
        }
    }

    /**
     * What it is:
     * Logs an informational diagnostic message to internal storage and logcat.
     *
     * What it does:
     * Executes [i] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [i]
     * @param context Active Android [Context].
     * @param tag Calling component or service identifier.
     * @param message Informational text string to persist.
     */
    fun i(context: Context, tag: String, message: String) = log(context, "INFO", tag, message)

    /**
     * What it is:
     * Logs a verbose debug message to internal storage and logcat.
     *
     * What it does:
     * Executes [d] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [d]
     * @param context Active Android [Context].
     * @param tag Calling component or service identifier.
     * @param message Diagnostic debugging text string.
     */
    fun d(context: Context, tag: String, message: String) = log(context, "DEBUG", tag, message)

    /**
     * What it is:
     * Logs a high-priority user-visible action event to internal storage and logcat.
     *
     * What it does:
     * Executes [action] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [action]
     * @param context Active Android [Context].
     * @param tag Calling component or service identifier.
     * @param actionDescription Description of user action or dispatch step.
     */
    fun action(context: Context, tag: String, actionDescription: String) = log(context, "ACTION", tag, actionDescription)

    /**
     * What it is:
     * Logs a non-fatal warning condition to internal storage and logcat.
     *
     * What it does:
     * Executes [w] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [w]
     * @param context Active Android [Context].
     * @param tag Calling component or service identifier.
     * @param message Warning description.
     * @param t Optional associated throwable or root-cause exception.
     */
    fun w(context: Context, tag: String, message: String, t: Throwable? = null) = log(context, "WARN", tag, message, t)

    /**
     * What it is:
     * Overload for logging warnings without explicit Context (resolves application singleton).
     *
     * What it does:
     * Executes [w] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [w]
     */
    fun w(tag: String, message: String, t: Throwable? = null) {
        val ctx = runCatching { HelperApplication.instance }.getOrNull()
        if (ctx != null) w(ctx, tag, message, t) else Log.w(tag, message, t)
    }

    /**
     * What it is:
     * Logs a critical error or unhandled exception to both execution and dedicated error logs.
     *
     * What it does:
     * Executes [e] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [e]
     * @param context Active Android [Context].
     * @param tag Calling component or service identifier.
     * @param message Technical error description.
     * @param t Optional exception with stack trace.
     */
    fun e(context: Context, tag: String, message: String, t: Throwable? = null) = log(context, "ERROR", tag, message, t)

    /**
     * What it is:
     * Overload for logging errors without explicit Context (resolves application singleton).
     *
     * What it does:
     * Executes [e] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [e]
     */
    fun e(tag: String, message: String, t: Throwable? = null) {
        val ctx = runCatching { HelperApplication.instance }.getOrNull()
        if (ctx != null) e(ctx, tag, message, t) else Log.e(tag, message, t)
    }

    /**
     * What it is:
     * Overload for logging info without explicit Context.
     *
     * What it does:
     * Executes [i] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [i]
     */
    fun i(tag: String, message: String) {
        val ctx = runCatching { HelperApplication.instance }.getOrNull()
        if (ctx != null) i(ctx, tag, message) else Log.i(tag, message)
    }

    /**
     * What it is:
     * Overload for logging debug without explicit Context.
     *
     * What it does:
     * Executes [d] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [d]
     */
    fun d(tag: String, message: String) {
        val ctx = runCatching { HelperApplication.instance }.getOrNull()
        if (ctx != null) d(ctx, tag, message) else Log.d(tag, message)
    }

    /**
     * What it is:
     * Appends an immutable audit record to the CSV campaign ledger.
     *
     * What it does:
     * Executes [campaignLedger] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [campaignLedger]
     * @param context Active Android [Context].
     * @param campaignId Unique daily campaign ID.
     * @param channel Transmission channel ("SMS", "WHATSAPP", "WHATSAPP_BUSINESS").
     * @param recipientName Resolved recipient contact name.
     * @param recipientPhone Destination phone number or JID.
     * @param status Outcome status ("SUCCESS", "DISPATCHED", "FAILED", "QUEUED").
     * @param messageSnippet First 80 characters of message body.
     * @param attempt Retry attempt index (1-based).
     * @param details Additional technical status info.
     */
    @Synchronized
    fun campaignLedger(
        context: Context,
        campaignId: Long,
        channel: String,
        recipientName: String,
        recipientPhone: String,
        status: String,
        messageSnippet: String,
        attempt: Int = 1,
        details: String = "",
    ) {
        try {
            val file = getLedgerFile(context) ?: return
            val isNewFile = !file.exists() || (file.length() == 0L)

            FileWriter(file, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    if (isNewFile) {
                        pw.println("Timestamp,CampaignId,Channel,RecipientName,RecipientPhone,Status,MessageSnippet,Attempt,Details")
                    }
                    val timestamp = csvDateFormat.format(Date())
                    val cleanSnippet = messageSnippet.replace("\"", "\"\"").take(80)
                    val cleanDetails = details.replace("\"", "\"\"")
                    val cleanName = recipientName.replace("\"", "\"\"")
                    pw.println("\"$timestamp\",$campaignId,\"$channel\",\"$cleanName\",\"$recipientPhone\",\"$status\",\"$cleanSnippet\",$attempt,\"$cleanDetails\"")
                }
            }

            log(context, "ACTION", "CAMPAIGN_LEDGER", "Campaign #$campaignId [$channel] -> $recipientPhone ($status): $details")
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing to campaign ledger: ${e.message}")
        }
    }

    /**
     * What it is:
     * Resolves the application private logs directory (`files/logs/`), creating it if absent.
     *
     * What it does:
     * Executes [getLogDir] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [getLogDir]
     * @param context Active Android [Context].
     * @return Directory [File] reference, or null if filesystem is inaccessible.
     */
    fun getLogDir(context: Context): File? {
        return try {
            val logDir = File(context.filesDir, LOG_DIR_NAME)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            logDir
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving/creating log dir: ${e.message}")
            null
        }
    }

    /**
     * What it is:
     * Resolves file handle for execution log (`helper_execution.log`).
     *
     * What it does:
     * Executes [getLogFile] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [getLogFile]
     * @param context Active Android [Context].
     * @return Execution log [File] reference, or null.
     */
    fun getLogFile(context: Context): File? {
        val dir = getLogDir(context) ?: return null
        return File(dir, EXEC_LOG_FILE)
    }

    /**
     * What it is:
     * Resolves file handle for dedicated error log (`helper_error.log`).
     *
     * What it does:
     * Executes [getErrorFile] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [getErrorFile]
     * @param context Active Android [Context].
     * @return Error log [File] reference, or null.
     */
    fun getErrorFile(context: Context): File? {
        val dir = getLogDir(context) ?: return null
        return File(dir, ERROR_LOG_FILE)
    }

    /**
     * What it is:
     * Resolves file handle for CSV campaign ledger (`helper_campaign_ledger.csv`).
     *
     * What it does:
     * Executes [getLedgerFile] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [getLedgerFile]
     * @param context Active Android [Context].
     * @return Ledger [File] reference, or null.
     */
    fun getLedgerFile(context: Context): File? {
        val dir = getLogDir(context) ?: return null
        return File(dir, LEDGER_FILE)
    }

    /**
     * What it is:
     * Reads the tail lines of the execution log file.
     *
     * What it does:
     * Executes [readLogFile] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [readLogFile]
     * @param context Active Android [Context].
     * @param maxLines Maximum count of trailing lines to return (defaults to 1000).
     * @return Formatted log string.
     */
    fun readLogFile(context: Context, maxLines: Int = 1000): String {
        return try {
            val file = getLogFile(context) ?: return "Log file unavailable"
            if (!file.exists()) return "No system logs recorded yet."
            val lines = file.readLines()
            lines.takeLast(maxLines).joinToString("\n")
        } catch (e: Exception) {
            "Error reading execution log: ${e.message}"
        }
    }

    /**
     * What it is:
     * Reads the tail lines of the CSV campaign ledger.
     *
     * What it does:
     * Executes [readLedgerFile] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [readLedgerFile]
     * @param context Active Android [Context].
     * @param maxLines Maximum count of trailing records to return (defaults to 1000).
     * @return Raw CSV string lines.
     */
    fun readLedgerFile(context: Context, maxLines: Int = 1000): String {
        return try {
            val file = getLedgerFile(context) ?: return "Campaign ledger file unavailable"
            if (!file.exists()) return "No campaign ledger entries recorded yet."
            val lines = file.readLines()
            lines.takeLast(maxLines).joinToString("\n")
        } catch (e: Exception) {
            "Error reading campaign ledger: ${e.message}"
        }
    }

    /**
     * What it is:
     * Reads the tail lines of the error log file.
     *
     * What it does:
     * Executes [readErrorLog] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [readErrorLog]
     * @param context Active Android [Context].
     * @param maxLines Maximum count of trailing lines to return (defaults to 500).
     * @return Formatted error log string.
     */
    fun readErrorLog(context: Context, maxLines: Int = 500): String {
        return try {
            val file = getErrorFile(context) ?: return "Error log unavailable"
            if (!file.exists()) return "No errors or crashes recorded. System is healthy!"
            val lines = file.readLines()
            lines.takeLast(maxLines).joinToString("\n")
        } catch (e: Exception) {
            "Error reading error log: ${e.message}"
        }
    }

    /**
     * What it is:
     * Deletes all diagnostic and ledger files in the internal log directory.
     *
     * What it does:
     * Executes [clearAllLogs] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [clearAllLogs]
     * @param context Active Android [Context].
     * @return True if purged successfully, false otherwise.
     */
    fun clearAllLogs(context: Context): Boolean {
        return try {
            val dir = getLogDir(context) ?: return false
            dir.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed clearing logs: ${e.message}")
            false
        }
    }

    /**
     * What it is:
     * Generates a formatted multi-section export summary containing ledger, errors, and system logs.
     *
     * What it does:
     * Executes [exportAllLogsSummary] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [exportAllLogsSummary]
     * @param context Active Android [Context].
     * @return Comprehensive export string suitable for sharing or diagnostic reporting.
     */
    @Synchronized
    fun exportAllLogsSummary(context: Context): String {
        val sb = StringBuilder()
        sb.append("===================================================\n")
        sb.append("  HELPER COMPREHENSIVE DIAGNOSTIC LOG & AUDIT LEDGER\n")
        sb.append("  Exported at: ").append(dateFormat.format(Date())).append("\n")
        sb.append("===================================================\n\n")

        sb.append("--- 1. CAMPAIGN AUDIT LEDGER ---\n")
        sb.append(readLedgerFile(context, 500)).append("\n\n")

        sb.append("--- 2. ERROR & CRASH LOG ---\n")
        sb.append(readErrorLog(context, 200)).append("\n\n")

        sb.append("--- 3. SYSTEM EXECUTION LOG ---\n")
        sb.append(readLogFile(context, 500)).append("\n")

        return sb.toString()
    }

    /**
     * What it is:
     * Rotates file to `.old` if file size exceeds [MAX_FILE_SIZE_BYTES] (3 MB).
     *
     * What it does:
     * Executes [rotateIfNecessary] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [FileLogger], [rotateIfNecessary]
     * @param file Target file to inspect and rotate if threshold is exceeded.
     */
    private fun rotateIfNecessary(file: File) {
        if (file.exists() && file.length() > MAX_FILE_SIZE_BYTES) {
            try {
                val oldFile = File(file.parentFile, "${file.name}.old")
                if (oldFile.exists()) {
                    oldFile.delete()
                }
                file.renameTo(oldFile)
            } catch (e: Exception) { Log.d(TAG, "File rotation note: ${e.message}") }
        }
    }
}
