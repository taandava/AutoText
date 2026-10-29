

/**
 * Package: com.jmd.autotext.receiver
 * Module: Broadcast Receivers Layer
 *
 * Listens for exact RTC_WAKEUP alarms triggered by Android AlarmManager for scheduled automation tasks.
 * Wakes the device, logs alarm activation, updates task execution state, and enqueues the task ID
 * into [com.jmd.autotext.engine.SequentialTaskEngine].
 *
 * Linked With:
 * - Scheduler: [com.jmd.autotext.scheduler.AlarmScheduler]
 * - Engine: [com.jmd.autotext.engine.SequentialTaskEngine]
 * - Repository: [com.jmd.autotext.data.repo.AppRepository]
 * - Diagnostics: [com.jmd.autotext.core.AppFileLogger]
 */
package com.jmd.autotext.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.core.AutomationSafetyManager
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.RecurrenceType
import com.jmd.autotext.data.model.ScheduledTask
import com.jmd.autotext.data.model.TaskStatus
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.engine.SequentialTaskEngine
import com.jmd.autotext.scheduler.AlarmScheduler

/**
 * High-priority wakeful receiver dispatched by Android AlarmManager when a scheduled task is due.
 */
class TaskAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskAlarmReceiver"
        const val EXTRA_CANCELLED = "extra_cancelled"
        const val ACTION_COUNTDOWN_TIMEOUT = "com.jmd.autotext.ACTION_COUNTDOWN_TIMEOUT"
        const val ACTION_TEST_TASK = "com.jmd.autotext.ACTION_TEST_TASK"
        const val ACTION_SETUP_ALL_TASKS = "com.jmd.autotext.ACTION_SETUP_ALL_TASKS"
        const val ACTION_DUMP_LOGS = "com.jmd.autotext.ACTION_DUMP_LOGS"
        const val ACTION_DELETE_ALL_TASKS = "com.jmd.autotext.ACTION_DELETE_ALL_TASKS"
        const val ACTION_SETUP_CAMPAIGN_CONTACTS = "com.jmd.autotext.ACTION_SETUP_CAMPAIGN_CONTACTS"
        const val ACTION_FIRE_CAMPAIGN = "com.jmd.autotext.ACTION_FIRE_CAMPAIGN" 

        /**
         * Cancels all scheduled alarms and deletes all tasks from the SQLite database.
         *
         * @param context Active Android [Context].
         * @return Number of deleted tasks.
         */
        fun deleteAllTasks(context: Context): Int {
            val repository = AppRepository(context)
            val scheduler = AlarmScheduler(context)
            val allTasks = repository.getAllTasks()
            val count = allTasks.size
            allTasks.forEach { task ->
                scheduler.cancelTask(task.id)
                repository.deleteTask(task.id)
            }
            com.jmd.autotext.core.AutomationSafetyManager.resetEmergencyStop(context)
            AppFileLogger.action(context, TAG, "Purged all $count scheduled tasks and cancelled all active alarms.")
            return count
        }

        /**
         * Sets up all 12 supported task types in the SQLite database and registers alarms.
         *
         * @param context Active Android [Context].
         * @param recipients Destination recipient numbers (defaults to 9769602020, 9930604542).
         * @param staggerSeconds Staggered offset in seconds between sequential initial trigger times.
         * @param repeatIntervalSeconds Repeat cycle duration in seconds (REPEAT_SECONDS).
         * @param executeFirstImmediately If true, queues the first generated task into SequentialTaskEngine immediately.
         * @param callDurationSeconds Auto-hangup duration for calls in seconds.
         * @param includeDialTimeInCallDuration Whether dial time is counted towards call duration.
         * @param delayBetweenCallsSeconds Pacing delay between sequential recipient calls.
         * @param clearExisting If true, cancels all pending alarms and clears prior tasks from database.
         * @return List of generated database row IDs for all 12 scheduled tasks.
         */
        fun setupAllTasks(
            context: Context,
            recipients: List<String> = listOf("9769602020", "9930604542"),
            message: String = "jai mata di",
            staggerSeconds: Int = 10,
            repeatIntervalSeconds: Int = 10,
            executeFirstImmediately: Boolean = false,
            callDurationSeconds: Int = 10,
            includeDialTimeInCallDuration: Boolean = true,
            delayBetweenCallsSeconds: Int = 5,
            clearExisting: Boolean = false
        ): List<Long> {
            val repository = AppRepository(context)
            val scheduler = AlarmScheduler(context)
            if (clearExisting) {
                repository.getAllTasks().forEach { oldTask ->
                    scheduler.cancelTask(oldTask.id)
                    repository.deleteTask(oldTask.id)
                }
                com.jmd.autotext.core.AutomationSafetyManager.resetEmergencyStop(context)
                AppFileLogger.action(context, TAG, "Cleared existing tasks and reset safety breakers before bulk setup")
            }
            val now = System.currentTimeMillis()
            val createdTaskIds = mutableListOf<Long>()

            ChannelType.entries.forEachIndexed { index, channel ->
                val targetRecipients = if (channel == ChannelType.WHATSAPP_STATUS || channel == ChannelType.WHATSAPP_BUSINESS_STATUS) {
                    listOf("STATUS")
                } else {
                    recipients
                }
                val trigger = now + ((index + 1) * staggerSeconds * 1000L)
                val task = ScheduledTask(
                    title = "Task ${index + 1} • ${channel.displayName}",
                    channel = channel,
                    recipients = targetRecipients,
                    recipientNames = targetRecipients,
                    messageText = message.ifBlank { "jai mata di" },
                    triggerTimeMillis = trigger,
                    recurrence = RecurrenceType.REPEAT_SECONDS,
                    customIntervalMinutes = repeatIntervalSeconds,
                    callDurationSeconds = callDurationSeconds,
                    includeDialTimeInCallDuration = includeDialTimeInCallDuration,
                    delayBetweenCallsSeconds = delayBetweenCallsSeconds,
                    soundModeSetting = "NORMAL",
                    fakeCallerName = "AutoText Test",
                    fakeCallerNumber = recipients.firstOrNull() ?: "9769602020",
                    status = TaskStatus.PENDING
                )
                val insertedId = repository.insertTask(task)
                val savedTask = task.copy(id = insertedId)
                scheduler.scheduleTask(savedTask)
                createdTaskIds.add(insertedId)
                AppFileLogger.action(
                    context,
                    TAG,
                    "Setup task #$insertedId channel=${channel.displayName} trigger=+${(index + 1) * staggerSeconds}s repeat=${repeatIntervalSeconds}s callDuration=${callDurationSeconds}s dialTime=$includeDialTimeInCallDuration"
                )
                if (executeFirstImmediately && index == 0) {
                    SequentialTaskEngine.getInstance(context).enqueue(insertedId)
                }
            }
            AppFileLogger.action(context, TAG, "ACTION_SETUP_ALL_TASKS: successfully created ${createdTaskIds.size} tasks (IDs: $createdTaskIds)")
            return createdTaskIds
        }

        /**
         * Dumps complete diagnostics, active tasks, execution logs, and system error traces to
         * a file in external storage (autotext_execution_dump.txt) for IDE inspection and support.
         *
         * @param context Active Android [Context].
         * @return Dump file if created successfully, null otherwise.
         */
        fun dumpExecutionLogsToFile(context: Context): java.io.File? {
            val repository = AppRepository(context)
            val logs = repository.getAllLogs()
            val tasks = repository.getAllTasks()
            val traceTail = AppFileLogger.readLogFile(context, 1000)
            val errTail = AppFileLogger.readErrorLog(context, 100)
            val isIdle = AutomationSafetyManager.isDeviceIdle(context)
            val lastInteraction = AutomationSafetyManager.lastHumanInteractionTimestamp.get()
            val now = System.currentTimeMillis()

            Log.i(TAG, "========== [START EXECUTION LOG DUMP (count=${logs.size})] ==========")
            logs.takeLast(60).forEach { log ->
                Log.i(TAG, "EXEC_LOG: id=${log.id} taskId=${log.taskId} channel=${log.channel} recipient=${log.recipient} status=${log.status} details=${log.details}")
            }
            Log.i(TAG, "========== [END EXECUTION LOG DUMP] ==========")

            return try {
                val outDir = context.getExternalFilesDir(null) ?: context.filesDir
                val outFile = java.io.File(outDir, "autotext_execution_dump.txt")
                val content = buildString {
                    appendLine("=== SYSTEM IDLE & SAFETY STATE ===")
                    appendLine("isDeviceIdle = $isIdle")
                    appendLine("lastHumanInteractionTimestamp = $lastInteraction (${(now - lastInteraction) / 1000}s ago)")
                    appendLine("currentTimestamp = $now")
                    appendLine("\n=== SCHEDULED TASKS IN DATABASE (Total: ${tasks.size}) ===")
                    tasks.forEach { t ->
                        val triggerDiffSec = (t.triggerTimeMillis - now) / 1000
                        appendLine("[ID: ${t.id}] ${t.title} | Channel: ${t.channel.displayName} | Status: ${t.status} | Trigger: in ${triggerDiffSec}s | Recurrence: ${t.recurrence.displayName} (custom: ${t.customIntervalMinutes}) | Recipients: ${t.recipients}")
                    }
                    appendLine("\n=== EXECUTION LOGS (Total: ${logs.size}) ===")
                    logs.takeLast(100).forEach { log ->
                        appendLine("[${log.timestampMillis}] [taskId=${log.taskId}] [${log.channel}] [${log.recipient}] status=${log.status} details=${log.details} snippet=${log.messageSnippet}")
                    }
                    appendLine("\n=== SYSTEM TRACE LOGS TAIL ===")
                    appendLine(traceTail)
                    appendLine("\n=== ERROR LOGS TAIL ===")
                    appendLine(errTail)
                }
                outFile.writeText(content)
                Log.i(TAG, "Dumped logs to ${outFile.absolutePath}")
                outFile
            } catch (e: Exception) {
                Log.w(TAG, "Failed dumping to external files dir: ${e.message}")
                null
            }
        }
        /**
         * Populates dynamic contact pool with specified contact numbers and resets campaign sent ledgers
         * to enable complete end-to-end verification of all daily outreach campaigns.
         */
         fun setupDailyCampaignContacts(
            context: Context,
            phoneNumbers: List<String> = listOf("9769602020", "9769402020", "9930604542", "9820098200", "9819098190"),
            triggerImmediately: Boolean = false
        ): Int {
            val repository = AppRepository(context)
            val campaignRepo = com.jmd.autotext.data.repo.DailyCampaignRepository(context)
            val contacts = phoneNumbers.mapIndexed { idx, phone ->
                val norm = phone.filter { it.isDigit() }.let { if (it.length == 10) "91$it" else it }
                val label = "Contact ${idx + 1}"
                com.jmd.autotext.data.model.DynamicContactItem(
                    name = label,
                    phone = phone,
                    normalizedNumber = norm,
                    source = "CONTACT",
                    enabled = true,
                    createdAt = System.currentTimeMillis()
                )
            }

            // Insert into TABLE_DYNAMIC_POOL
            val inserted = repository.upsertDynamicPoolBatch(contacts)

            // Reset campaign ledger and send counts so all 5 numbers are pending and ready
            listOf(1L, 2L, 3L).forEach { campId ->
                campaignRepo.resetCampaignLedger(campId)
            }

            AppFileLogger.action(
                context,
                TAG,
                "ACTION_SETUP_CAMPAIGN_CONTACTS: Configured ${phoneNumbers.size} pool contacts ($phoneNumbers) and cleared past campaign ledgers."
            )

            if (triggerImmediately) {
                listOf(1L, 2L, 3L).forEach { campId ->
                    com.jmd.autotext.scheduler.DailyCampaignScheduler.triggerNow(context, campId)
                    AppFileLogger.action(context, TAG, "Triggered immediate execution for Daily Campaign #$campId")
                }
            }
            return inserted
        }
    }

    /**
     * Intercepts wakeful alarm broadcasts from AlarmManager, validates task existence and state,
     * checks cancellation flags, and enqueues the task ID into [SequentialTaskEngine].
     *
     * @param context Active Context.
     * @param intent Alarm trigger intent carrying task ID and flags.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_FIRE_CAMPAIGN) {
            val campId = intent.getLongExtra("campaign_id", 1L)
            val prefs = PreferenceManager(context)
            val repo = AppRepository(context)
            val cfg = repo.getDailyCampaignConfig(campId)
            if (!cfg.isActive || !prefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(context)) {
                AppFileLogger.w(context, TAG, "ACTION_FIRE_CAMPAIGN suppressed: Campaign #$campId is inactive or master switch is OFF.")
                com.jmd.autotext.scheduler.DailyCampaignScheduler.cancel(context, campId)
                return
            }
            AppFileLogger.action(context, TAG, "ACTION_FIRE_CAMPAIGN received for Campaign #$campId. Triggering batch execution.")
            com.jmd.autotext.scheduler.DailyCampaignScheduler.triggerNow(context, campId)
            com.jmd.autotext.scheduler.DailyCampaignScheduler.scheduleOrUpdate(context, campId)
            return
        }

        if (intent.action == ACTION_DUMP_LOGS) {
            dumpExecutionLogsToFile(context)
            return
        }

        if (intent.action == ACTION_SETUP_CAMPAIGN_CONTACTS) {
            val numbersParam = intent.getStringArrayExtra("numbers")?.toList()
                ?: intent.getStringExtra("numbers")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("9769602020", "9769402020", "9930604542", "9820098200", "9819098190")
            val triggerNow = intent.getBooleanExtra("triggerNow", false)
            val count = setupDailyCampaignContacts(context, numbersParam, triggerNow)
            Log.i(TAG, "ACTION_SETUP_CAMPAIGN_CONTACTS: Processed $count contacts, triggerNow=$triggerNow")
            return
        }

        if (intent.action == ACTION_SETUP_ALL_TASKS) {
            val recipientsParam = intent.getStringArrayExtra("recipients")?.toList()
                ?: intent.getStringExtra("recipients")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("9769602020", "9930604542")
            val messageParam = intent.getStringExtra("message")?.ifBlank { "jai mata di" }
                ?: intent.getStringExtra("messageText")?.ifBlank { "jai mata di" }
                ?: "jai mata di"
            val staggerSec = intent.getIntExtra("staggerSeconds", 10)
            val repeatIntervalSec = intent.getIntExtra("repeatIntervalSeconds", 10)
            val executeImmediately = intent.getBooleanExtra("executeImmediately", false)
            val callDurationSec = intent.getIntExtra("callDurationSeconds", 10)
            val includeDialTime = intent.getBooleanExtra("includeDialTimeInCallDuration", true)
            val delayBetweenCalls = intent.getIntExtra("delayBetweenCallsSeconds", 5)
            val clearExisting = intent.getBooleanExtra("clearExisting", false)
            setupAllTasks(
                context = context,
                recipients = recipientsParam,
                message = messageParam,
                staggerSeconds = staggerSec,
                repeatIntervalSeconds = repeatIntervalSec,
                executeFirstImmediately = executeImmediately,
                callDurationSeconds = callDurationSec,
                includeDialTimeInCallDuration = includeDialTime,
                delayBetweenCallsSeconds = delayBetweenCalls,
                clearExisting = clearExisting
            )
            return
        }

        if (intent.action == "com.jmd.autotext.ACTION_SET_PREF") {
            val key = intent.getStringExtra("key")
            if (key == "autoLock") {
                val value = intent.getBooleanExtra("value", true)
                PreferenceManager(context).isAutoLockAfterSendEnabled = value
                AppFileLogger.action(context, TAG, "ACTION_SET_PREF: isAutoLockAfterSendEnabled=$value")
            }
            return
        }

        if (intent.action == ACTION_DELETE_ALL_TASKS) {
            val count = deleteAllTasks(context)
            AppFileLogger.action(context, TAG, "ACTION_DELETE_ALL_TASKS: Successfully deleted $count tasks.")
            Log.i(TAG, "ACTION_DELETE_ALL_TASKS: Successfully deleted $count tasks.")
            return
        }

        if (intent.action == ACTION_TEST_TASK) {
            val prefs = PreferenceManager(context)
            if (!prefs.isTasksMasterEnabled || AutomationSafetyManager.isEmergencyStopActive(context)) {
                AppFileLogger.w(context, TAG, "ACTION_TEST_TASK suppressed: Master scheduler disabled or Emergency Stop active.")
                return
            }
            val repository = AppRepository(context)
            val channelStr = intent.getStringExtra("channel") ?: "CALL"
            val channel = try { ChannelType.valueOf(channelStr.uppercase()) } catch (_: Exception) { ChannelType.CALL }
            val recipientsParam = intent.getStringArrayExtra("recipients")?.toList()
                ?: intent.getStringExtra("recipients")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("9769602020", "9769402020")
            val callDurationSec = intent.getIntExtra("callDurationSeconds", 10)
            val includeDialTime = intent.getBooleanExtra("includeDialTimeInCallDuration", true)
            val delayBetweenCalls = intent.getIntExtra("delayBetweenCallsSeconds", 5)
            val msgText = intent.getStringExtra("messageText") ?: "AutoText test message for $recipientsParam"
            if (intent.hasExtra("autoLock")) {
                PreferenceManager(context).isAutoLockAfterSendEnabled = intent.getBooleanExtra("autoLock", true)
            }
            val fakeCallerName = intent.getStringExtra("fakeCallerName") ?: msgText.ifBlank { "Incoming Call" }
            val fakeCallerNumber = intent.getStringExtra("fakeCallerNumber") ?: recipientsParam.firstOrNull() ?: "+1 (555) 019-2834"
            val soundMode = intent.getStringExtra("soundModeSetting") ?: "NORMAL"
            val mediaUri = intent.getStringExtra("mediaUri") ?: ""

            val testTask = ScheduledTask(
                title = "Live Test [${channel.displayName}]",
                channel = channel,
                recipients = recipientsParam,
                recipientNames = recipientsParam,
                messageText = msgText,
                triggerTimeMillis = System.currentTimeMillis(),
                callDurationSeconds = callDurationSec,
                includeDialTimeInCallDuration = includeDialTime,
                delayBetweenCallsSeconds = delayBetweenCalls,
                soundModeSetting = soundMode,
                fakeCallerName = fakeCallerName,
                fakeCallerNumber = fakeCallerNumber,
                mediaUri = mediaUri,
                status = TaskStatus.PENDING
            )
            val insertedId = repository.insertTask(testTask)
            AppFileLogger.action(context, TAG, "Inserted ACTION_TEST_TASK #$insertedId channel=${channel.displayName} recipients=$recipientsParam duration=${callDurationSec}s")
            SequentialTaskEngine.getInstance(context).enqueue(insertedId)
            return
        }

        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1L).let {
            if (it == -1L) intent.getIntExtra(AlarmScheduler.EXTRA_TASK_ID, -1).toLong() else it
        }.let {
            if (it == -1L) intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID)?.toLongOrNull() ?: -1L else it
        }.let {
            if (it == -1L) intent.getLongExtra("task_id", -1L) else it
        }.let {
            if (it == -1L) intent.getIntExtra("task_id", -1).toLong() else it
        }

        AppFileLogger.action(context, TAG, "TaskAlarmReceiver received action=${intent.action}, taskId=$taskId")
        if (intent.action == ACTION_COUNTDOWN_TIMEOUT) {
            AppFileLogger.action(context, TAG, "Countdown timer elapsed for Task #$taskId. Enqueuing for execution.")
        }

        if (taskId == -1L) {
            AppFileLogger.w(context, TAG, "TaskAlarmReceiver: taskId missing or invalid (-1)")
            return
        }
        val isCancelled = intent.getBooleanExtra(EXTRA_CANCELLED, false)

        val repository = AppRepository(context)
        val task = repository.getTaskById(taskId) ?: run {
            AppFileLogger.w(context, TAG, "Task #$taskId not found in database")
            Log.w(TAG, "Task $taskId not found in database")
            return
        }

        if (isCancelled) {
            Log.i(TAG, "Task #$taskId execution cancelled via intent flag. Updating DB and cancelling alarms.")
            AppFileLogger.action(context, TAG, "Task #$taskId execution cancelled via intent flag.")
            repository.updateTaskStatus(taskId, TaskStatus.CANCELLED)
            AlarmScheduler(context).cancelTask(taskId)
            return
        }

        val isManualRun = intent.getBooleanExtra(AlarmScheduler.EXTRA_MANUAL_RUN, false)
        if (isManualRun) {
            AppFileLogger.action(context, TAG, "Manual run requested for Task #$taskId (current status: ${task.status}). Resetting to PENDING.")
            if (task.status != TaskStatus.PENDING) {
                repository.updateTaskStatus(taskId, TaskStatus.PENDING)
            }
        } else {
            if (task.status == TaskStatus.CANCELLED) {
                Log.w(TAG, "Task #$taskId is marked CANCELLED in DB. Skipping execution.")
                AppFileLogger.w(context, TAG, "Alarm triggered for CANCELLED Task #$taskId. Suppressed.")
                return
            }

            if (task.status == TaskStatus.PENDING_CONFIRMATION && task.recurrence == RecurrenceType.ONCE) {
                Log.w(TAG, "Task #$taskId is Awaiting Confirmation. Skipping stale alarm trigger without manual retry.")
                AppFileLogger.w(context, TAG, "Alarm triggered for PENDING_CONFIRMATION Task #$taskId. Suppressed.")
                return
            }

            if (task.status == TaskStatus.FAILED && task.recurrence == RecurrenceType.ONCE) {
                Log.w(TAG, "Task #$taskId is marked FAILED in DB. Skipping stale alarm trigger without manual retry.")
                AppFileLogger.w(context, TAG, "Alarm triggered for FAILED Task #$taskId. Suppressed.")
                return
            }

            if (task.status == TaskStatus.DONE && task.recurrence == RecurrenceType.ONCE) {
                Log.d(TAG, "Task #$taskId is already marked DONE. Skipping alarm trigger.")
                return
            }
        }

        val prefs = PreferenceManager(context)
        if (!prefs.isTasksMasterEnabled) {
            Log.d(TAG, "Task $taskId execution skipped because Task Scheduler Engine is disabled by master switch")
            return
        }

        if (AutomationSafetyManager.isEmergencyStopActive(context)) {
            Log.d(TAG, "Task $taskId execution skipped because Emergency Stop is active")
            return
        }

        // Pre-flight Idle Check: Only trigger and complete task when idle (unless manually triggered from UI)
        if (!isManualRun && !AutomationSafetyManager.isDeviceIdle(context)) {
            val idleSince = (System.currentTimeMillis() - AutomationSafetyManager.lastHumanInteractionTimestamp.get()) / 1000
            val reason = "Alarm fired while device was in active human use (${idleSince}s since last interaction)"
            Log.i(TAG, "Task #$taskId postponed by 30s: $reason")
            AppFileLogger.action(context, TAG, "Auto-postponing Task #$taskId: $reason. Will trigger once device is idle.")
            AutomationSafetyManager.postponeTask(context, taskId, reason = reason)
            return
        }

        Log.d(TAG, "Alarm triggered for Task ID: $taskId. Routing to SequentialTaskEngine.")
        AppFileLogger.action(context, TAG, "Alarm triggered for Task #${task.id} (${task.title}) [${task.channel.displayName}] -> SequentialTaskEngine")
        val enqueued = SequentialTaskEngine.getInstance(context).enqueue(taskId)
        if (!enqueued) {
            Log.w(TAG, "Task #$taskId is already active or queued in SequentialTaskEngine. Suppressing duplicate alarm dispatch.")
            AppFileLogger.w(context, TAG, "Task #$taskId already active in SequentialTaskEngine pipeline. Duplicate alarm trigger suppressed.")
        }
    }

}
