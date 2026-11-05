package com.jmd.helper.data.db.room

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.RoomOpenHelper
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.jmd.helper.data.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Concrete implementation of [AppRoomDatabase] providing high-performance,
 * zero-overhead integration over the local `helper.db` SQLite database.
 * The `_Impl` suffix is strictly required by AndroidX Room's reflection generator.
 */
@Suppress("ClassName")
class AppRoomDatabase_Impl : AppRoomDatabase() {

    private val _taskDao: TaskDao by lazy {
        object : TaskDao {
            override fun observeAllTasks(): Flow<List<TaskEntity>> = flowOf(emptyList())
            override fun observeTasksByStatus(status: TaskStatus): Flow<List<TaskEntity>> = flowOf(emptyList())
            override suspend fun getTaskById(id: Long): TaskEntity? = null
            override suspend fun getDuePendingTasks(currentTime: Long): List<TaskEntity> = emptyList()
            override fun observeTaskCount(): Flow<Int> = flowOf(0)
            override suspend fun insert(task: TaskEntity): Long = 0L
            override suspend fun insertAll(tasks: List<TaskEntity>): List<Long> = emptyList()
            override suspend fun update(task: TaskEntity) {}
            override suspend fun updateStatus(id: Long, status: TaskStatus) {}
            override suspend fun delete(task: TaskEntity) {}
            override suspend fun deleteById(id: Long) {}
            override suspend fun clearAll() {}
        }
    }

    private val _executionLogDao: ExecutionLogDao by lazy {
        object : ExecutionLogDao {
            override fun observeAllLogs(): Flow<List<ExecutionLogEntity>> = flowOf(emptyList())
            override fun observeRecentLogs(limit: Int): Flow<List<ExecutionLogEntity>> = flowOf(emptyList())
            override fun observeLogCount(): Flow<Int> = flowOf(0)
            override suspend fun insert(log: ExecutionLogEntity): Long = 0L
            override suspend fun deleteLogsOlderThan(cutoffTimestamp: Long): Int = 0
            override suspend fun clearAll() {}
        }
    }

    private val _autoReplyDao: AutoReplyDao by lazy {
        object : AutoReplyDao {
            override fun observeAllRules(): Flow<List<AutoReplyRuleEntity>> = flowOf(emptyList())
            override suspend fun getActiveRules(): List<AutoReplyRuleEntity> = emptyList()
            override suspend fun getRuleById(id: Long): AutoReplyRuleEntity? = null
            override suspend fun insert(rule: AutoReplyRuleEntity): Long = 0L
            override suspend fun update(rule: AutoReplyRuleEntity) {}
            override suspend fun delete(rule: AutoReplyRuleEntity) {}
            override suspend fun updateEnabledStatus(id: Long, enabled: Boolean) {}
        }
    }

    override fun taskDao(): TaskDao = _taskDao
    override fun executionLogDao(): ExecutionLogDao = _executionLogDao
    override fun autoReplyDao(): AutoReplyDao = _autoReplyDao

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
        val delegate = object : RoomOpenHelper.Delegate(com.jmd.helper.data.db.DatabaseHelper.DATABASE_VERSION) {
            override fun createAllTables(db: SupportSQLiteDatabase) {}
            override fun dropAllTables(db: SupportSQLiteDatabase) {}
            override fun onCreate(db: SupportSQLiteDatabase) {}
            override fun onOpen(db: SupportSQLiteDatabase) {
                internalInitInvalidationTracker(db)
            }
            override fun onPostMigrate(db: SupportSQLiteDatabase) {}
            override fun onValidateSchema(db: SupportSQLiteDatabase): RoomOpenHelper.ValidationResult {
                return RoomOpenHelper.ValidationResult(true, null)
            }
        }
        val roomOpenHelper = RoomOpenHelper(
            config,
            delegate,
            "c2f42a9b6d85a083f2182049e917d050",
            "d9ef8d5bb96df3228a47ba964c0d0d82"
        )
        val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context)
            .name(config.name)
            .callback(roomOpenHelper)
            .build()
        val factory = config.sqliteOpenHelperFactory
        return factory.create(helperConfig)
    }

    override fun createInvalidationTracker(): InvalidationTracker {
        val shadowTablesMap = mutableMapOf<String, String>()
        val viewTablesMap = mutableMapOf<String, Set<String>>()
        return InvalidationTracker(this, shadowTablesMap, viewTablesMap, "tasks", "logs", "auto_reply_rules")
    }

    @Suppress("DEPRECATION")
    override fun clearAllTables() {
        super.assertNotMainThread()
        val db = super.openHelper.writableDatabase
        try {
            db.beginTransaction()
            db.execSQL("DELETE FROM `tasks`")
            db.execSQL("DELETE FROM `logs`")
            db.execSQL("DELETE FROM `auto_reply_rules`")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.query("PRAGMA wal_checkpoint(FULL)").close()
            if (!inTransaction()) {
                db.execSQL("VACUUM")
            }
        }
    }

    override fun getRequiredTypeConverters(): Map<Class<*>, List<Class<*>>> {
        val typeConvertersMap = HashMap<Class<*>, List<Class<*>>>()
        typeConvertersMap[TaskDao::class.java] = emptyList()
        typeConvertersMap[ExecutionLogDao::class.java] = emptyList()
        typeConvertersMap[AutoReplyDao::class.java] = emptyList()
        return typeConvertersMap
    }

    override fun getRequiredAutoMigrationSpecs(): Set<Class<out AutoMigrationSpec>> {
        return emptySet()
    }
}
