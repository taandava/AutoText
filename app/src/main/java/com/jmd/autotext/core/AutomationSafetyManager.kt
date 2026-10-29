/**
 * Package: com.jmd.autotext.core
 * Module: Core Automation Utilities
 *
 * Provides fail-safe protections, global emergency kill-switching, and channel-level
 * circuit breakers preventing automation runaway loops and device lockouts.
 */
package com.jmd.autotext.core

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.ExecutionLog
import com.jmd.autotext.data.model.ScheduledTask
import com.jmd.autotext.data.model.TaskStatus
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.scheduler.AlarmScheduler
import com.jmd.autotext.service.AutoTextAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Global automation safety manager, emergency kill switch, and circuit breaker.
 * Ensures the device never gets locked into an automation loop, rapid relaunch cycle,
 * or unresponsive state.
 *
 * Implements strict Human Interaction & Idle State Protection:
 * - Automatically postpones all upcoming tasks when human user interaction is detected.
 * - Only triggers and completes automated tasks when the device is confirmed idle.
 * - Instantly disarms and auto-postpones active tasks if interrupted by human touch or navigation.
 */
object AutomationSafetyManager {

    private const val TAG = "AutoSafetyManager"

    /** Global AtomicBoolean representing whether an emergency stop is currently armed. */
    val isEmergencyStopActive = AtomicBoolean(false)

    /**
     * Checks if emergency stop is armed, reconciling in-memory state with persisted preferences.
     */
    fun isEmergencyStopActive(context: Context? = null): Boolean {
        if (isEmergencyStopActive.get()) return true
        if (context != null) {
            try {
                val persisted = PreferenceManager(context).isEmergencyStopActive
                if (persisted) {
                    isEmergencyStopActive.set(true)
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    /** Consecutive failure counters tracked per communication channel name. */
    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()

    /** Trip state flags indicating whether automated executions for a channel are temporarily suspended. */
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

    /** Atomic flag indicating that an ongoing automation was interrupted by human interaction. */
    val isInterruptedByHuman = AtomicBoolean(false)

    /** Timestamp of the last bulk postponement to debounce multiple rapid touches. */
    private var lastBulkPostponeTimestamp = 0L

    private val safetyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Records a detected human interaction event across the operating system or application.
     * Updates interaction timestamp, signals ongoing automation to abort, and auto-postpones upcoming tasks.
     *
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

        // If automation is actively running, signal interruption immediately to abort safely
        if (AutoTextAccessibilityService.isCurrentlyAutomating()) {
            isInterruptedByHuman.set(true)
            val activeTaskId = AutoTextAccessibilityService.getActiveTaskId()
            AutoTextAccessibilityService.abortDueToHumanInterruption()
            AppFileLogger.action(context, TAG, "Interrupted active automation: $reason (Task #$activeTaskId). Auto-postponing task.")
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
     * Evaluates whether the device is currently idle and safe for automated task execution.
     * Device is considered idle if:
     * 1. Screen is turned off (PowerManager.isInteractive == false), OR
     * 2. Keyguard is locked (KeyguardManager.isKeyguardLocked == true), OR
     * 3. No human interaction has occurred for at least [USER_IDLE_COOLDOWN_MS] (10s).
     *
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
     * Postpones a specific task by ID and reschedules its system alarm.
     *
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
     * Postpones all pending tasks due within the postponement window.
     *
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
     * Arms the global emergency kill switch, canceling active automation queues,
     * resetting picker listeners, tripping circuit breakers, and minimizing to launcher home.
     *
     * @param context Active Android [Context].
     * @param reason Human-readable rationale for emergency stop event.
     */
    fun triggerEmergencyStop(context: Context, reason: String = "User requested emergency stop") {
        isEmergencyStopActive.set(true)
        try {
            PreferenceManager(context).isEmergencyStopActive = true
        } catch (_: Exception) {}
        Log.e(TAG, "EMERGENCY STOP TRIGGERED: $reason")
        AppFileLogger.e(context, TAG, "EMERGENCY STOP TRIGGERED: $reason")

        try {
            // 1. Immediately abort active engine tasks and drain queue
            try {
                com.jmd.autotext.engine.SequentialTaskEngine.getInstance(context).cancelAll()
            } catch (e: Throwable) {
                Log.e(TAG, "Error cancelling SequentialTaskEngine", e)
            }

            // 2. Cancel all daily campaign WorkManager jobs and exact alarms
            try {
                com.jmd.autotext.scheduler.DailyCampaignScheduler.cancelAllCampaigns(context)
            } catch (e: Throwable) {
                Log.e(TAG, "Error cancelling DailyCampaignScheduler", e)
            }

            // 3. Cancel all pending AlarmManager alarms for individual tasks
            try {
                com.jmd.autotext.scheduler.AlarmScheduler(context).cancelAll()
            } catch (e: Throwable) {
                Log.e(TAG, "Error cancelling AlarmScheduler", e)
            }

            // 4. Immediately disarm all pending accessibility automation
            AutoTextAccessibilityService.clearPending()

            // 5. Clear any pending chat picker callbacks
            AutoTextAccessibilityService.onManualChatPicked = null
            AutoTextAccessibilityService.onManualChatInfoPicked = null

            // 6. Trip all circuit breakers defensively
            val now = System.currentTimeMillis()
            ChannelType.entries.forEach { channel ->
                circuitBreakerTripped[channel.name] = true
                tripTimestamps[channel.name] = now
            }

            // 7. Return to home launcher cleanly
            AutoTextAccessibilityService.minimizeToHome(context)

            context.showAppToast("🚨 EMERGENCY STOP: All automation halted immediately!", isLong = true)
        } catch (t: Throwable) {
            Log.e(TAG, "Error executing emergency stop cleanup", t)
        }
    }

    /**
     * Resets the emergency kill switch and clears all channel circuit breakers, restoring normal operation.
     *
     * @param context Active Android [Context] (optional, used for logging and user feedback).
     */
    fun resetEmergencyStop(context: Context? = null) {
        isEmergencyStopActive.set(false)
        if (context != null) {
            try {
                PreferenceManager(context).isEmergencyStopActive = false
            } catch (_: Exception) {}
        }
        failureCounts.clear()
        circuitBreakerTripped.clear()
        tripTimestamps.clear()
        lastHumanInteractionTimestamp.set(0L)
        isInterruptedByHuman.set(false)
        Log.i(TAG, "Emergency stop and circuit breakers have been reset.")
        if (context != null) {
            AppFileLogger.i(context, TAG, "Emergency stop and circuit breakers reset by user.")
            context.showAppToast("Automation safeguards reset. Normal operation resumed.")
        }
    }

    /**
     * Records an execution failure for a given channel, incrementing failure counter and tripping
     * circuit breaker if threshold is exceeded.
     *
     * @param channel The failing [ChannelType].
     * @param context Active Android [Context] for toast alerts and file logging.
     */
    fun recordFailure(channel: ChannelType, context: Context? = null) {
        val count = failureCounts.computeIfAbsent(channel.name) { AtomicInteger(0) }.incrementAndGet()
        Log.w(TAG, "Recorded failure for ${channel.name} (Consecutive failures: $count)")

        if (count >= MAX_CONSECUTIVE_FAILURES) {
            circuitBreakerTripped[channel.name] = true
            tripTimestamps[channel.name] = System.currentTimeMillis()
            val msg = "Circuit breaker TRIPPED for ${channel.displayName}! Pausing automated retries for ${CIRCUIT_BREAKER_COOLDOWN_MS / 1000}s to protect device stability."
            Log.e(TAG, msg)
            if (context != null) {
                AppFileLogger.e(context, TAG, msg)
                context.showAppToast("⚠️ ${channel.displayName} automation paused after $count failures. Cooldown active.", isLong = true)
            }
        }
    }

    /**
     * Records a successful execution for a given channel, resetting its failure counter and circuit breaker.
     *
     * @param channel The successful [ChannelType].
     */
    fun recordSuccess(channel: ChannelType) {
        failureCounts[channel.name]?.set(0)
        circuitBreakerTripped[channel.name] = false
        tripTimestamps.remove(channel.name)
    }

    /**
     * Determines whether the circuit breaker for a channel is currently tripped.
     * Automatically clears the trip flag if the cooldown interval has elapsed.
     *
     * @param channel Target [ChannelType].
     * @return True if tripped (preventing automated dispatch), false otherwise.
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
     * Pre-flight gatekeeper verifying whether a channel is eligible to execute.
     *
     * @param channel Target [ChannelType].
     * @return True if safe to proceed, false if kill switch is active or circuit breaker is tripped.
     */
    fun canExecute(channel: ChannelType): Boolean {
        if (isEmergencyStopActive.get()) return false
        if (isCircuitBreakerTripped(channel)) return false
        return true
    }
}
