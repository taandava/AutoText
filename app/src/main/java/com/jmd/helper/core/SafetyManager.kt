/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [SafetyManager].
 *
 * What it does:
 * Provides fail-safe protections and channel-level circuit breakers preventing task execution loops and unintended dispatches.
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.helper.data.model.ChannelType], [com.jmd.helper.data.model.ExecutionLog], [com.jmd.helper.data.model.ScheduledTask], [com.jmd.helper.data.model.TaskStatus]
 * - Schedulers & Receivers: [com.jmd.helper.scheduler.AlarmScheduler]
 * - Services & Engines: [com.jmd.helper.service.TaskAccessibilityService]
 * - Core Security & Utilities: [android.app.KeyguardManager], [android.os.PowerManager], [android.util.Log], [com.jmd.helper.data.prefs.PreferenceManager]
 * - Android Framework & Jetpack: [kotlinx.coroutines.CoroutineScope], [kotlinx.coroutines.Dispatchers], [kotlinx.coroutines.SupervisorJob], [kotlinx.coroutines.launch]
 */

package com.jmd.helper.core

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.ExecutionLog
import com.jmd.helper.data.model.ScheduledTask
import com.jmd.helper.data.model.TaskStatus
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.scheduler.AlarmScheduler
import com.jmd.helper.service.TaskAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Global task safety manager and circuit breaker.
 */
typealias SafetyManager = TaskSafetyManager
/**
 * What it is:
 * Component definition for [TaskSafetyManager].
 *
 * What it does:
 * Provides domain logic and lifecycle management for TaskSafetyManager.
 *
 * Linked with:
 * - Component: [SafetyManager]
 */

object TaskSafetyManager {

    private const val TAG = "AutoSafetyManager"

    /** Consecutive failure counters tracked per communication channel name. */
    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()

    /** Trip state flags indicating whether scheduled executions for a channel are temporarily suspended. */
    private val circuitBreakerTripped = ConcurrentHashMap<String, Boolean>()

    /** Maximum consecutive failures tolerated before tripping the safety circuit breaker. */
    private const val MAX_CONSECUTIVE_FAILURES = 3

    /** Safety cooldown duration in milliseconds before an auto-reset of a tripped channel is permitted. */
    const val CIRCUIT_BREAKER_COOLDOWN_MS = 60_000L

    /** Timestamp per channel when the circuit breaker was tripped. */
    private val tripTimestamps = ConcurrentHashMap<String, Long>()

    /** Timestamp of last manual picker launch used to debounce rapid button triggers. */
    @Volatile
    var lastPickerLaunchTimestamp: Long = 0L

    // ==================== HUMAN INTERACTION & IDLE POLICY ====================

    /** Required duration of user inactivity in milliseconds before the device is considered idle. */
    const val USER_IDLE_COOLDOWN_MS = 10_000L

    /** Default duration in milliseconds to postpone tasks when human interaction occurs. */
    const val DEFAULT_POSTPONE_INTERVAL_MS = 30_000L

    /** Timestamp in milliseconds of the last detected human interaction (touches, clicks, scrolls, typing, or navigation). */
    val lastHumanInteractionTimestamp = AtomicLong(0L)

    /** Atomic flag indicating that an ongoing task was interrupted by human interaction. */
    val isInterruptedByHuman = AtomicBoolean(false)

    /** Timestamp of the last bulk postponement to debounce multiple rapid touches. */
    private var lastBulkPostponeTimestamp = 0L

    private val safetyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * What it is:
     * Records a detected human interaction event across the operating system or application.
     *
     * What it does:
     * Updates interaction timestamp, signals ongoing task to abort, and auto-postpones upcoming tasks.
     *
     * Linked with:
     * - Architecture: [SafetyManager], [recordHumanInteraction]
     * @param context Active Android [Context].
     * @param reason Human-readable description of detected interaction.
     */
    fun recordHumanInteraction(context: Context, reason: String) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        // If screen is off or keyguard is locked, background system events are not human interactions
        if (pm?.isInteractive == false || km?.isKeyguardLocked == true) {
            return
        }

        val now = System.currentTimeMillis()
        lastHumanInteractionTimestamp.set(now)

        // If task is actively running, signal interruption immediately to abort safely
        if (TaskAccessibilityService.isCurrentlyDispatching()) {
            if (TaskAccessibilityService.isManualPickMode()) {
                // User is actively touching the screen to pick a contact or chat in manual picker mode.
                // Do not abort or postpone tasks.
                return
            }
            isInterruptedByHuman.set(true)
            val activeTaskId = TaskAccessibilityService.getActiveTaskId()
            TaskAccessibilityService.abortDueToHumanInterruption()
            AppFileLogger.action(context, TAG, "Interrupted active task: $reason (Task #$activeTaskId). Postponing task.")
            if (activeTaskId > 0) {
                safetyScope.launch {
                    try {
                        postponeTask(context, activeTaskId, DEFAULT_POSTPONE_INTERVAL_MS, reason)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in postponeTask for interrupted task #$activeTaskId", t)
                    }
                }
            }
        }
    }

    /**
     * What it is:
     * Evaluates whether the device is currently idle and safe for scheduled task execution.
     *
     * What it does:
     * Device is considered idle if: 1. Screen is turned off (PowerManager.isInteractive == false), OR 2. Keyguard is locked (KeyguardManager.isKeyguardLocked == true), OR 3. No human interaction has occurred for at least [USER_IDLE_COOLDOWN_MS] (10s).
     *
     * Linked with:
     * - Architecture: [SafetyManager], [isDeviceIdle]
     * @param context Active Android [Context].
     * @return True if device is idle; false if human is actively using the device.
     */
    fun isDeviceIdle(context: Context): Boolean {
        // 1. Screen off means user is not looking or touching
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == false) {
            return true
        }

        // 2. Keyguard locked means user is not actively operating apps
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isKeyguardLocked == true) {
            return true
        }

        // 3. Screen is on and unlocked: check time since last human interaction
        val lastInteraction = lastHumanInteractionTimestamp.get()
        if (lastInteraction <= 0L) {
            return true // No human interaction recorded yet
        }
        val elapsed = System.currentTimeMillis() - lastInteraction
        return elapsed >= USER_IDLE_COOLDOWN_MS
    }

    /**
     * What it is:
     * Postpones a specific task by ID and reschedules its system alarm.
     *
     * What it does:
     * Executes [postponeTask] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [SafetyManager], [postponeTask]
     * @param context Active Android [Context].
     * @param taskId Unique identifier of the task.
     * @param postponeMillis Duration in milliseconds to postpone (defaults to 30s).
     * @param reason Human-readable justification.
     * @return True if task was successfully postponed and rescheduled.
     */
    fun postponeTask(
        context: Context,
        taskId: Long,
        postponeMillis: Long = DEFAULT_POSTPONE_INTERVAL_MS,
        reason: String = "Postponed due to active human interaction"
    ): Boolean {
        val repo = AppRepository(context)
        val task = repo.getTaskById(taskId) ?: return false
        val newTrigger = System.currentTimeMillis() + postponeMillis
        val updated = task.copy(triggerTimeMillis = newTrigger, status = TaskStatus.PENDING)

        val success = repo.updateTask(updated)
        if (success) {
            val scheduler = AlarmScheduler(context)
            scheduler.cancelTask(taskId)
            scheduler.scheduleTask(updated)

            repo.insertLog(
                ExecutionLog(
                    taskId = taskId,
                    channel = task.channel.displayName,
                    recipient = task.recipients.firstOrNull() ?: "SYSTEM",
                    messageSnippet = task.messageText.take(160),
                    status = "POSTPONED",
                    details = "$reason. Rescheduled for +${postponeMillis / 1000}s"
                )
            )
            AppFileLogger.action(context, TAG, "Postponed Task #$taskId by ${postponeMillis / 1000}s: $reason")
        }
        return success
    }

    /**
     * What it is:
     * Postpones all pending tasks due within the postponement window.
     *
     * What it does:
     * Executes [postponeUpcomingTasks] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [SafetyManager], [postponeUpcomingTasks]
     * @param context Active Android [Context].
     * @param postponeMillis Duration in milliseconds to postpone.
     * @param reason Human-readable justification.
     * @return Count of tasks postponed.
     */
    fun postponeUpcomingTasks(
        context: Context,
        postponeMillis: Long = DEFAULT_POSTPONE_INTERVAL_MS,
        reason: String = "Human interaction detected"
    ): Int {
        val repo = AppRepository(context)
        val scheduler = AlarmScheduler(context)
        val postponedTasks = repo.postponeUpcomingTasks(postponeMillis)

        for (task in postponedTasks) {
            scheduler.cancelTask(task.id)
            scheduler.scheduleTask(task)
            repo.insertLog(
                ExecutionLog(
                    taskId = task.id,
                    channel = task.channel.displayName,
                    recipient = task.recipients.firstOrNull() ?: "SYSTEM",
                    messageSnippet = task.messageText.take(160),
                    status = "POSTPONED",
                    details = "Auto-postponed by ${postponeMillis / 1000}s upon human interaction ($reason). Will trigger when idle."
                )
            )
        }
        if (postponedTasks.isNotEmpty()) {
            AppFileLogger.action(context, TAG, "Postponed ${postponedTasks.size} upcoming tasks by ${postponeMillis / 1000}s ($reason)")
        }
        return postponedTasks.size
    }

    /**
     * What it is:
     * Resets all channel circuit breakers and interaction state, restoring normal operation.
     *
     * What it does:
     * Executes [resetSafeguards] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [SafetyManager], [resetSafeguards]
     * @param context Active Android [Context] (optional, used for logging and user feedback).
     */
    fun resetSafeguards(context: Context? = null) {
        failureCounts.clear()
        circuitBreakerTripped.clear()
        tripTimestamps.clear()
        lastHumanInteractionTimestamp.set(0L)
        isInterruptedByHuman.set(false)
        Log.i(TAG, "Circuit breakers and safety state have been reset.")
        if (context != null) {
            try {
                com.jmd.helper.scheduler.AlarmScheduler(context).rescheduleAllPending()
                com.jmd.helper.scheduler.DailyCampaignScheduler.scheduleAll(context)
            } catch (t: Throwable) {
                Log.e(TAG, "Error rescheduling tasks after safeguards reset", t)
            }
            AppFileLogger.i(context, TAG, "Safety circuit breakers reset by user. All pending tasks rescheduled.")
            context.showAppToast("Task safeguards reset. All scheduled tasks re-armed.")
        }
    }

    /**
     * What it is:
     * Records an execution failure for a given channel, incrementing failure counter and tripping circuit breaker if threshold is exceeded.
     *
     * What it does:
     * Executes [recordFailure] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [SafetyManager], [recordFailure]
     * @param channel The failing [ChannelType].
     * @param context Active Android [Context] for toast alerts and file logging.
     */
    fun recordFailure(channel: ChannelType, context: Context? = null) {
        val count = failureCounts.computeIfAbsent(channel.name) { AtomicInteger(0) }.incrementAndGet()
        Log.w(TAG, "Recorded failure for ${channel.name} (Consecutive failures: $count)")

        if (count >= MAX_CONSECUTIVE_FAILURES) {
            circuitBreakerTripped[channel.name] = true
            tripTimestamps[channel.name] = System.currentTimeMillis()
            val msg = "Circuit breaker TRIPPED for ${channel.displayName}! Pausing retries for ${CIRCUIT_BREAKER_COOLDOWN_MS / 1000}s to protect device stability."
            Log.e(TAG, msg)
            if (context != null) {
                AppFileLogger.e(context, TAG, msg)
                context.showAppToast("⚠️ ${channel.displayName} paused after $count failures. Cooldown active.", isLong = true)
            }
        }
    }

    /**
     * What it is:
     * Records a successful execution for a given channel, resetting its failure counter and circuit breaker.
     *
     * What it does:
     * Executes [recordSuccess] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [SafetyManager], [recordSuccess]
     * @param channel The successful [ChannelType].
     */
    fun recordSuccess(channel: ChannelType) {
        failureCounts[channel.name]?.set(0)
        circuitBreakerTripped[channel.name] = false
        tripTimestamps.remove(channel.name)
    }

    /**
     * What it is:
     * Determines whether the circuit breaker for a channel is currently tripped.
     *
     * What it does:
     * Directly clears the trip flag if the cooldown interval has elapsed.
     *
     * Linked with:
     * - Architecture: [SafetyManager], [isCircuitBreakerTripped]
     * @param channel Target [ChannelType].
     * @return True if tripped (preventing scheduled dispatch), false otherwise.
     */
    fun isCircuitBreakerTripped(channel: ChannelType): Boolean {
        if (circuitBreakerTripped[channel.name] != true) return false
        val trippedAt = tripTimestamps[channel.name] ?: return true
        val now = System.currentTimeMillis()
        if (now - trippedAt >= CIRCUIT_BREAKER_COOLDOWN_MS) {
            circuitBreakerTripped[channel.name] = false
            failureCounts[channel.name]?.set(0)
            tripTimestamps.remove(channel.name)
            Log.i(TAG, "Circuit breaker cooldown elapsed for ${channel.displayName}. Auto-resumed.")
            return false
        }
        return true
    }

    /**
     * What it is:
     * Pre-flight gatekeeper verifying whether a channel is eligible to execute.
     *
     * What it does:
     * Executes [canExecute] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [SafetyManager], [canExecute]
     * @param channel Target [ChannelType].
     * @return True if safe to proceed, false if circuit breaker is tripped.
     */
    fun canExecute(channel: ChannelType): Boolean {
        return !isCircuitBreakerTripped(channel)
    }
}
