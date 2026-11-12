/**
 * Package: com.jmd.helper.data.prefs
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [AppDataStore].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for AppDataStore.
 *
 * Linked With:
 * - Core Security & Utilities: [androidx.datastore.core.DataStore], [androidx.datastore.preferences.core.Preferences], [androidx.datastore.preferences.core.booleanPreferencesKey], [androidx.datastore.preferences.core.edit]
 * - Android Framework & Jetpack: [androidx.datastore.preferences.preferencesDataStore], [kotlinx.coroutines.flow.Flow], [kotlinx.coroutines.flow.catch], [kotlinx.coroutines.flow.map]
 */

package com.jmd.helper.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jmd.helper.core.AppFileLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "helper_datastore_prefs")

/**
 * What it is:
 * Modern Jetpack DataStore preferences implementation.
 *
 * What it does:
 * Asynchronous, fully transactional, and coroutine-native key-value persistence. Prevents main thread disk I/O and ANRs during high-frequency automation loops.
 *
 * Linked with:
 * - Architecture: [AppDataStore], [AppDataStore]
 */
class AppDataStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.dataStore

    // Keys
    /**
     * What it is:
     * Component definition for [PreferencesKeys].
     *
     * What it does:
     * Provides domain logic and lifecycle management for PreferencesKeys.
     *
     * Linked with:
     * - Component: [AppDataStore]
     */
    object PreferencesKeys {
        val MASTER_SCHEDULER_ENABLED = booleanPreferencesKey("master_scheduler_enabled")
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val SMS_SIM_SLOT = intPreferencesKey("sms_sim_slot")
        val CALL_SIM_SLOT = intPreferencesKey("call_sim_slot")
        val SMS_DAILY_LIMIT = intPreferencesKey("sms_daily_limit")
        val DYNAMIC_REACH_DAILY_LIMIT = intPreferencesKey("dynamic_reach_daily_limit")
        val LAST_MAINTENANCE_TIMESTAMP = longPreferencesKey("last_maintenance_timestamp")
        val DEFAULT_COUNTRY_CODE = stringPreferencesKey("default_country_code")
    }

    private val safePreferences: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                AppFileLogger.e("AppDataStore", "Error reading datastore preferences: ${exception.message}", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    // Flows
    val isMasterSchedulerEnabled: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(true)

    val isDarkThemeEnabled: Flow<Boolean> = safePreferences.map { preferences ->
        preferences[PreferencesKeys.DARK_THEME_ENABLED] ?: true
    }

    val isAppLockEnabled: Flow<Boolean> = safePreferences.map { preferences ->
        preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false
    }

    val smsSimSlot: Flow<Int> = safePreferences.map { preferences ->
        preferences[PreferencesKeys.SMS_SIM_SLOT] ?: 0
    }

    val callSimSlot: Flow<Int> = safePreferences.map { preferences ->
        preferences[PreferencesKeys.CALL_SIM_SLOT] ?: 0
    }

    val smsDailyLimit: Flow<Int> = safePreferences.map { preferences ->
        preferences[PreferencesKeys.SMS_DAILY_LIMIT] ?: 100
    }

    val dynamicReachDailyLimit: Flow<Int> = safePreferences.map { preferences ->
        preferences[PreferencesKeys.DYNAMIC_REACH_DAILY_LIMIT] ?: 50
    }

    val defaultCountryCode: Flow<String> = safePreferences.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_COUNTRY_CODE] ?: "+91"
    }

    // Suspend Update Functions
    /**
     * What it is:
     * Execution routine for [setMasterSchedulerEnabled].
     *
     * What it does:
     * Coordinates setMasterSchedulerEnabled operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppDataStore]
     */
    suspend fun setMasterSchedulerEnabled(enabled: Boolean) {
        // Master switch permanently removed - tasks always active
    }
    /**
     * What it is:
     * Execution routine for [setDarkThemeEnabled].
     *
     * What it does:
     * Coordinates setDarkThemeEnabled operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppDataStore]
     */

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_THEME_ENABLED] = enabled
        }
    }
    /**
     * What it is:
     * Execution routine for [setAppLockEnabled].
     *
     * What it does:
     * Coordinates setAppLockEnabled operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppDataStore]
     */

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
        }
    }
    /**
     * What it is:
     * Execution routine for [setSmsSimSlot].
     *
     * What it does:
     * Coordinates setSmsSimSlot operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppDataStore]
     */

    suspend fun setSmsSimSlot(slot: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SIM_SLOT] = slot
        }
    }
    /**
     * What it is:
     * Execution routine for [setCallSimSlot].
     *
     * What it does:
     * Coordinates setCallSimSlot operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppDataStore]
     */

    suspend fun setCallSimSlot(slot: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CALL_SIM_SLOT] = slot
        }
    }
    /**
     * What it is:
     * Execution routine for [setSmsDailyLimit].
     *
     * What it does:
     * Coordinates setSmsDailyLimit operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppDataStore]
     */

    suspend fun setSmsDailyLimit(limit: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_DAILY_LIMIT] = limit
        }
    }
    /**
     * What it is:
     * Execution routine for [setDynamicReachDailyLimit].
     *
     * What it does:
     * Coordinates setDynamicReachDailyLimit operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppDataStore]
     */

    suspend fun setDynamicReachDailyLimit(limit: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_REACH_DAILY_LIMIT] = limit
        }
    }
    /**
     * What it is:
     * Execution routine for [setLastMaintenanceTimestamp].
     *
     * What it does:
     * Coordinates setLastMaintenanceTimestamp operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppDataStore]
     */

    suspend fun setLastMaintenanceTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_MAINTENANCE_TIMESTAMP] = timestamp
        }
    }
    /**
     * What it is:
     * Execution routine for [setDefaultCountryCode].
     *
     * What it does:
     * Coordinates setDefaultCountryCode operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [AppDataStore]
     */

    suspend fun setDefaultCountryCode(code: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_COUNTRY_CODE] = code
        }
    }
    /**
     * What it is:
     * Companion object constant and factory provider for [AppDataStore].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [AppDataStore]
     */

    companion object {
        @Volatile
        private var instance: AppDataStore? = null

        fun getInstance(context: Context): AppDataStore {
            return instance ?: synchronized(this) {
                instance ?: AppDataStore(context).also { instance = it }
            }
        }
    }
}
