/**
 * Package: com.jmd.helper.data.repo
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [PrivacyRepository].
 *
 * What it does:
 * Provides transactional CRUD persistence for per-application privacy configurations in the local SQLite database (`helper.db`).
 *
 * Linked With:
 * - Repositories: [android.database.sqlite.SQLiteDatabase], [com.jmd.helper.data.db.DatabaseHelper]
 * - Models & Entities: [com.jmd.helper.data.model.AppPrivacyConfig]
 * - Core Security & Utilities: [androidx.core.database.sqlite.transaction], [android.util.Log]
 * - Android Framework & Jetpack: [android.content.ContentValues], [android.database.Cursor]
 */

package com.jmd.helper.data.repo

import android.content.ContentValues
import androidx.core.database.sqlite.transaction
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.jmd.helper.data.db.DatabaseHelper
import com.jmd.helper.data.model.AppPrivacyConfig

/**
 * What it is:
 * Repository managing relational persistence and retrieval of [AppPrivacyConfig] records.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [PrivacyRepository].
 *
 * Linked with:
 * - Architecture: [PrivacyRepository], [PrivacyRepository]
 * @param context Active Android application context.
 */
class PrivacyRepository(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)
    /**
     * What it is:
     * Companion object constant and factory provider for [PrivacyRepository].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [PrivacyRepository]
     */

    companion object {
        private const val TAG = "PrivacyRepository"
    }

    /**
     * What it is:
     * Retrieves all stored [AppPrivacyConfig] records ordered by app name.
     *
     * What it does:
     * Executes [getAllConfigs] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyRepository], [getAllConfigs]
     */
    fun getAllConfigs(): List<AppPrivacyConfig> {
        val list = mutableListOf<AppPrivacyConfig>()
        val db = dbHelper.readableDatabase
        try {
            db.query(
                DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS,
                null,
                null,
                null,
                null,
                null,
                "app_name COLLATE NOCASE ASC"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursorToAppPrivacyConfig(cursor))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all privacy configs", e)
        }
        return list
    }

    /**
     * What it is:
     * Retrieves all [AppPrivacyConfig] records where `is_excluded == 1`.
     *
     * What it does:
     * Executes [getExcludedConfigs] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyRepository], [getExcludedConfigs]
     */
    fun getExcludedConfigs(): List<AppPrivacyConfig> {
        val list = mutableListOf<AppPrivacyConfig>()
        val db = dbHelper.readableDatabase
        try {
            db.query(
                DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS,
                null,
                "is_excluded = 1",
                null,
                null,
                null,
                "app_name COLLATE NOCASE ASC"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(cursorToAppPrivacyConfig(cursor))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching excluded privacy configs", e)
        }
        return list
    }

    /**
     * What it is:
     * Retrieves the privacy configuration for a specific package name, or null if not registered.
     *
     * What it does:
     * Executes [getConfigForPackage] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyRepository], [getConfigForPackage]
     */
    fun getConfigForPackage(packageName: String): AppPrivacyConfig? {
        if (packageName.isBlank()) return null
        val db = dbHelper.readableDatabase
        try {
            db.query(
                DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS,
                null,
                "package_name = ?",
                arrayOf(packageName.trim()),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToNext()) {
                    return cursorToAppPrivacyConfig(cursor)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching privacy config for $packageName", e)
        }
        return null
    }

    /**
     * What it is:
     * Inserts or updates an individual [AppPrivacyConfig] record atomically.
     *
     * What it does:
     * Executes [saveConfig] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyRepository], [saveConfig]
     */
    fun saveConfig(config: AppPrivacyConfig): Boolean {
        if (config.packageName.isBlank()) return false
        val db = dbHelper.writableDatabase
        return try {
            val values = configToContentValues(config)
            val result = db.insertWithOnConflict(
                DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            result != -1L
        } catch (e: Exception) {
            Log.e(TAG, "Error saving privacy config for ${config.packageName}", e)
            false
        }
    }

    /**
     * What it is:
     * Inserts or updates multiple [AppPrivacyConfig] records within a single database transaction.
     *
     * What it does:
     * Executes [saveConfigs] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyRepository], [saveConfigs]
     */
    fun saveConfigs(configs: List<AppPrivacyConfig>): Boolean {
        if (configs.isEmpty()) return true
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        return try {
            for (config in configs) {
                if (config.packageName.isBlank()) continue
                val values = configToContentValues(config)
                db.insertWithOnConflict(
                    DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error batch saving privacy configs", e)
            false
        } finally {
            db.endTransaction()
        }
    }

    /**
     * What it is:
     * Deletes a privacy configuration for a given package name.
     *
     * What it does:
     * Executes [deleteConfig] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyRepository], [deleteConfig]
     */
    fun deleteConfig(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val db = dbHelper.writableDatabase
        return try {
            val rows = db.delete(
                DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS,
                "package_name = ?",
                arrayOf(packageName.trim())
            )
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting privacy config for $packageName", e)
            false
        }
    }

    /**
     * What it is:
     * Purges all application privacy configurations from the database.
     *
     * What it does:
     * Executes [deleteAllConfigs] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [PrivacyRepository], [deleteAllConfigs]
     */
    fun deleteAllConfigs(): Boolean {
        val db = dbHelper.writableDatabase
        return try {
            db.delete(DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all privacy configs", e)
            false
        }
    }

    /**
     * What it is:
     * Seeds initial default configurations for known banking, payment, and UPI packages.
     *
     * What it does:
     * Skips packages that already have persisted records.
     *
     * Linked with:
     * - Architecture: [PrivacyRepository], [seedDefaultBankingConfigs]
     */
    fun seedDefaultBankingConfigs(knownPackages: List<String>) {
        if (knownPackages.isEmpty()) return
        val db = dbHelper.writableDatabase
        try {
            db.transaction {
                val now = System.currentTimeMillis()
                for (pkg in knownPackages) {
                    if (pkg.isBlank()) continue
                    val cursor = db.query(
                        DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS,
                        arrayOf("package_name"),
                        "package_name = ?",
                        arrayOf(pkg.trim()),
                        null,
                        null,
                        null
                    )
                    val exists = cursor.use { it.moveToNext() }
                    if (!exists) {
                        val friendlyName = deriveFriendlyName(pkg)
                        val values = ContentValues().apply {
                            put("package_name", pkg.trim())
                            put("app_name", friendlyName)
                            put("is_excluded", 1)
                            put("disable_task_action", 1)
                            put("block_overlays", 1)
                            put("suppress_notifications", 1)
                            put("disable_clipboard", 1)
                            put("mask_sensitive_content", 1)
                            put("disable_rules", 1)
                            put("custom_notes", "Bank & UPI Shield")
                            put("updated_at", now)
                        }
                        db.insertWithOnConflict(
                            DatabaseHelper.TABLE_APP_PRIVACY_CONFIGS,
                            null,
                            values,
                            SQLiteDatabase.CONFLICT_IGNORE
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding default banking configs", e)
        }
    }
    /**
     * What it is:
     * Execution routine for [deriveFriendlyName].
     *
     * What it does:
     * Coordinates deriveFriendlyName operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [PrivacyRepository]
     */

    private fun deriveFriendlyName(packageName: String): String {
        val lower = packageName.lowercase()
        return when {
            lower.contains("sbi") || lower.contains("yono") -> "State Bank of India (YONO)"
            lower.contains("phonepe") -> "PhonePe UPI"
            lower.contains("paytm") -> "Paytm Payments"
            lower.contains("paisa") || lower.contains("gpay") -> "Google Pay"
            lower.contains("saraswat") -> "Saraswat Bank Mobile"
            lower.contains("hdfc") -> "HDFC Bank Mobile"
            lower.contains("icici") -> "ICICI Bank iMobile"
            lower.contains("axis") -> "Axis Bank Mobile"
            lower.contains("kotak") -> "Kotak 811 Bank"
            lower.contains("bhim") || lower.contains("npci") -> "BHIM UPI"
            lower.contains("cred") -> "CRED App"
            lower.contains("mobikwik") -> "MobiKwik Wallet"
            lower.contains("freecharge") -> "FreeCharge"
            lower.contains("bajaj") -> "Bajaj Finserv"
            lower.contains("indusind") -> "IndusInd Bank"
            lower.contains("canara") -> "Canara Bank"
            lower.contains("pnb") -> "Punjab National Bank"
            lower.contains("bob") || lower.contains("baroda") -> "Bank of Baroda"
            lower.contains("rbl") -> "RBL MoBank"
            lower.contains("boi") -> "Bank of India"
            lower.contains("centmobile") -> "Central Bank of India"
            lower.contains("indianbank") -> "Indian Bank"
            lower.contains("ucobank") -> "UCO Bank"
            lower.contains("psb") -> "Punjab & Sind Bank"
            lower.contains("mahamb") -> "Bank of Maharashtra"
            lower.contains("sib") -> "South Indian Bank"
            lower.contains("karnatakabank") -> "Karnataka Bank"
            lower.contains("kvb") -> "Karur Vysya Bank"
            lower.contains("cub") -> "City Union Bank"
            lower.contains("bandhan") -> "Bandhan Bank"
            lower.contains("csb") -> "CSB Bank"
            lower.contains("idfc") -> "IDFC FIRST Bank"
            lower.contains("federalbank") -> "Federal Bank"
            lower.contains("jio.finance") -> "JioFinance"
            lower.contains("tatadigital") -> "Tata Neu"
            lower.contains("groww") -> "Groww"
            lower.contains("zerodha") -> "Zerodha Kite"
            lower.contains("upstox") -> "Upstox"
            lower.contains("dhan") -> "Dhan"
            lower.contains("navi") -> "Navi"
            else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
    /**
     * What it is:
     * Execution routine for [configToContentValues].
     *
     * What it does:
     * Coordinates configToContentValues operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [PrivacyRepository]
     */

    private fun configToContentValues(config: AppPrivacyConfig): ContentValues {
        return ContentValues().apply {
            put("package_name", config.packageName.trim())
            put("app_name", config.appName.trim())
            put("is_excluded", if (config.isExcluded) 1 else 0)
            put("disable_task_action", if (config.disableTaskAction) 1 else 0)
            put("block_overlays", if (config.blockOverlays) 1 else 0)
            put("suppress_notifications", if (config.suppressNotifications) 1 else 0)
            put("disable_clipboard", if (config.disableClipboard) 1 else 0)
            put("mask_sensitive_content", if (config.maskSensitiveContent) 1 else 0)
            put("disable_rules", if (config.disableRules) 1 else 0)
            put("custom_notes", config.customNotes)
            put("updated_at", config.updatedAt)
        }
    }
    /**
     * What it is:
     * Execution routine for [cursorToAppPrivacyConfig].
     *
     * What it does:
     * Coordinates cursorToAppPrivacyConfig operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [PrivacyRepository]
     */

    private fun cursorToAppPrivacyConfig(cursor: Cursor): AppPrivacyConfig {
        return AppPrivacyConfig(
            packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
            appName = cursor.getString(cursor.getColumnIndexOrThrow("app_name")),
            isExcluded = cursor.getInt(cursor.getColumnIndexOrThrow("is_excluded")) == 1,
            disableTaskAction = cursor.getInt(cursor.getColumnIndexOrThrow("disable_task_action")) == 1,
            blockOverlays = cursor.getInt(cursor.getColumnIndexOrThrow("block_overlays")) == 1,
            suppressNotifications = cursor.getInt(cursor.getColumnIndexOrThrow("suppress_notifications")) == 1,
            disableClipboard = cursor.getInt(cursor.getColumnIndexOrThrow("disable_clipboard")) == 1,
            maskSensitiveContent = cursor.getInt(cursor.getColumnIndexOrThrow("mask_sensitive_content")) == 1,
            disableRules = cursor.getInt(cursor.getColumnIndexOrThrow("disable_rules")) == 1,
            customNotes = cursor.getString(cursor.getColumnIndexOrThrow("custom_notes")) ?: "",
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        )
    }
}
