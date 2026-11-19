package com.jmd.supersms.core

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.jmd.supersms.data.model.ChannelType
import com.jmd.supersms.data.model.ExecutionLog
import com.jmd.supersms.data.model.ScheduledTask
import com.jmd.supersms.data.model.TaskStatus
import com.jmd.supersms.data.prefs.PreferenceManager
import com.jmd.supersms.data.repo.AppRepository
import com.jmd.supersms.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Global task safety manager and circuit breaker for SuperSMS.
 */
typealias SafetyManager = TaskSafetyManager

object TaskSafetyManager {

    private const val TAG = "AutoSafetyManager"

    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val circuitBreakerTripped = ConcurrentHashMap<String, Boolean>()
    private const val MAX_CONSECUTIVE_FAILURES = 3
    const val CIRCUIT_BREAKER_COOLDOWN_MS = 60_000L
    private val tripTimestamps = ConcurrentHashMap<String, Long>()

    @Volatile
    var lastPickerLaunchTimestamp: Long = 0L

    const val USER_IDLE_COOLDOWN_MS = 10_000L
    const val DEFAULT_POSTPONE_INTERVAL_MS = 30_000L
    val lastHumanInteractionTimestamp = AtomicLong(0L)
    val isInterruptedByHuman = AtomicBoolean(false)

    private val safetyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun recordHumanInteraction(context: Context, reason: String) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (pm?.isInteractive == false || km?.isKeyguardLocked == true) {
            return
        }

        val now = System.currentTimeMillis()
        lastHumanInteractionTimestamp.set(now)
        isInterruptedByHuman.set(true)
        AppFileLogger.action(context, TAG, "Detected human interaction: $reason")
    }

    fun isDeviceIdle(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == false) return true

        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isKeyguardLocked == true) return true

        val lastInteraction = lastHumanInteractionTimestamp.get()
        if (lastInteraction <= 0L) return true
        val elapsed = System.currentTimeMillis() - lastInteraction
        return elapsed >= USER_IDLE_COOLDOWN_MS
    }

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

    fun resetSafeguards(context: Context? = null) {
        failureCounts.clear()
        circuitBreakerTripped.clear()
        tripTimestamps.clear()
        lastHumanInteractionTimestamp.set(0L)
        isInterruptedByHuman.set(false)
        Log.i(TAG, "Circuit breakers and safety state have been reset.")
        if (context != null) {
            try {
                AlarmScheduler(context).rescheduleAllPending()
                com.jmd.supersms.scheduler.DailyCampaignScheduler.scheduleAll(context)
            } catch (t: Throwable) {
                Log.e(TAG, "Error rescheduling tasks after safeguards reset", t)
            }
            AppFileLogger.i(context, TAG, "Safety circuit breakers reset by user. All pending tasks rescheduled.")
            context.showAppToast("Task safeguards reset. All scheduled tasks re-armed.")
        }
    }

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

    fun recordSuccess(channel: ChannelType) {
        failureCounts[channel.name]?.set(0)
        circuitBreakerTripped[channel.name] = false
        tripTimestamps.remove(channel.name)
    }

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

    fun canExecute(channel: ChannelType): Boolean {
        return !isCircuitBreakerTripped(channel)
    }
}
