/**
 * Package: com.jmd.helper.data.db.room
 * Module: Persistence & Database Layer
 *
 * What it is:
 * Architecture definition and implementation for [AppRoomDatabase].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for AppRoomDatabase.
 *
 * Linked With:
 * - Repositories: [androidx.room.Database], [androidx.room.RoomDatabase]
 * - Core Security & Utilities: [com.jmd.helper.core.AppFileLogger]
 * - Android Framework & Jetpack: [androidx.room.Room], [androidx.room.TypeConverters]
 */

package com.jmd.helper.data.db.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jmd.helper.core.AppFileLogger

/**
 * Jetpack Room Database for Helper.
 * Operates over the local `helper.db` database with compile-time SQL verification and reactive Flows.
 */
@Database(
    entities = [
        TaskEntity::class,
        ExecutionLogEntity::class,
        AutoReplyRuleEntity::class
    ],
    version = com.jmd.helper.data.db.DatabaseHelper.DATABASE_VERSION,
    exportSchema = false
)
/**
 * What it is:
 * Component definition for [AppRoomDatabase].
 *
 * What it does:
 * Provides domain logic and lifecycle management for AppRoomDatabase.
 *
 * Linked with:
 * - Component: [AppRoomDatabase]
 */
@TypeConverters(RoomTypeConverters::class)
abstract class AppRoomDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun executionLogDao(): ExecutionLogDao
    abstract fun autoReplyDao(): AutoReplyDao
    /**
     * What it is:
     * Companion object constant and factory provider for [AppRoomDatabase].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [AppRoomDatabase]
     */

    companion object {
        private const val DATABASE_NAME = "helper.db"

        @Volatile
        private var instance: AppRoomDatabase? = null
        /**
         * What it is:
         * Operational routine [getInstance].
         *
         * What it does:
         * Executes getInstance actions according to system invariants.
         *
         * Linked with:
         * - Component: [AppRoomDatabase]
         */

        fun getInstance(context: Context): AppRoomDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }
        }
        /**
         * What it is:
         * Operational routine [buildDatabase].
         *
         * What it does:
         * Executes buildDatabase actions according to system invariants.
         *
         * Linked with:
         * - Component: [AppRoomDatabase]
         */

        private fun buildDatabase(context: Context): AppRoomDatabase {
            return Room.databaseBuilder(context, AppRoomDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
                .also {
                    AppFileLogger.i("AppRoomDatabase", "Room Database instance initialized over $DATABASE_NAME")
                }
        }
    }
}

