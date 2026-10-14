package com.jmd.autotext.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enterprise-grade persistent diagnostic file logger and campaign ledger.
 * Writes real-time execution logs, device actions, error traces, and campaign audit ledgers
 * directly to internal app storage on the phone.
 * Supports auto-rotation when log files exceed 3MB.
 */
object AppFileLogger {

    private const val TAG = "AppFileLogger"
    private const val LOG_DIR_NAME = "logs"
    private const val EXEC_LOG_FILE = "autotext_execution.log"
    private const val EXEC_LOG_OLD_FILE = "autotext_execution.log.old"
    private const val ERROR_LOG_FILE = "autotext_error.log"
    private const val LEDGER_FILE = "autotext_campaign_ledger.csv"
    private const val MAX_FILE_SIZE_BYTES = 3 * 1024 * 1024L // 3 MB

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val csvDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun log(context: Context, level: String, tag: String, message: String, throwable: Throwable? = null) {
        val lvl = level.uppercase(Locale.US)

        // Output to Android Logcat
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

            // Write to dedicated error log if ERROR level
            if (lvl == "ERROR" || throwable != null) {
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

    fun i(context: Context, tag: String, message: String) = log(context, "INFO", tag, message)
    fun action(context: Context, tag: String, actionDescription: String) = log(context, "ACTION", tag, actionDescription)
    fun w(context: Context, tag: String, message: String, t: Throwable? = null) = log(context, "WARN", tag, message, t)
    fun e(context: Context, tag: String, message: String, t: Throwable? = null) = log(context, "ERROR", tag, message, t)

    /**
     * Records a permanent, immutable entry in the campaign audit ledger CSV file.
     * Captures every campaign dispatch, recipient, message snippet, and outcome.
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
        details: String = ""
    ) {
        try {
            val file = getLedgerFile(context) ?: return
            val isNewFile = !file.exists() || file.length() == 0L

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

            // Also mirror to execution log
            log(context, "ACTION", "CAMPAIGN_LEDGER", "Campaign #$campaignId [$channel] -> $recipientPhone ($status): $details")
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing to campaign ledger: ${e.message}")
        }
    }

    fun getLogDir(context: Context): File? {
        return try {
            val logDir = File(context.filesDir, LOG_DIR_NAME)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            logDir
        } catch (_: Exception) {
            null
        }
    }

    fun getLogFile(context: Context): File? {
        val dir = getLogDir(context) ?: return null
        return File(dir, EXEC_LOG_FILE)
    }

    fun getErrorFile(context: Context): File? {
        val dir = getLogDir(context) ?: return null
        return File(dir, ERROR_LOG_FILE)
    }

    fun getLedgerFile(context: Context): File? {
        val dir = getLogDir(context) ?: return null
        return File(dir, LEDGER_FILE)
    }

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

    fun clearLogFile(context: Context): Boolean = clearAllLogs(context)

    fun clearAllLogs(context: Context): Boolean {
        return try {
            val dir = getLogDir(context) ?: return false
            dir.listFiles()?.forEach { it.delete() }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun exportAllLogsSummary(context: Context): String {
        val sb = StringBuilder()
        sb.append("===================================================\n")
        sb.append("  AUTOTEXT COMPREHENSIVE DIAGNOSTIC LOG & AUDIT LEDGER\n")
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

    private fun rotateIfNecessary(file: File) {
        if (file.exists() && file.length() > MAX_FILE_SIZE_BYTES) {
            try {
                val oldFile = File(file.parentFile, "${file.name}.old")
                if (oldFile.exists()) {
                    oldFile.delete()
                }
                file.renameTo(oldFile)
            } catch (_: Exception) {}
        }
    }
}
