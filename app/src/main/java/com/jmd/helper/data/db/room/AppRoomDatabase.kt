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
@TypeConverters(RoomTypeConverters::class)
abstract class AppRoomDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun executionLogDao(): ExecutionLogDao
    abstract fun autoReplyDao(): AutoReplyDao

    companion object {
        private const val DATABASE_NAME = "helper.db"

        @Volatile
        private var instance: AppRoomDatabase? = null

        fun getInstance(context: Context): AppRoomDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }
        }

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

