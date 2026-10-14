package com.jmd.autotext.scheduler

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jmd.autotext.AutoTextApplication
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.model.*
import com.jmd.autotext.sender.SmsSender
import com.jmd.autotext.sender.WhatsAppAutomationSender
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.ui.fakecall.FakeCallActivity
import com.jmd.autotext.ui.reminder.ReminderAlarmActivity
import com.jmd.autotext.util.AppFileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TaskAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskAlarmReceiver"
        const val EXTRA_CONFIRMED = "extra_confirmed"
        const val EXTRA_CANCELLED = "extra_cancelled"
    }

    override fun onReceive(context: Context, intent: Intent) {
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

        if (taskId == -1L) {
            AppFileLogger.w(context, TAG, "TaskAlarmReceiver: taskId missing or invalid (-1)")
            return
        }
        val isConfirmed = intent.getBooleanExtra(EXTRA_CONFIRMED, false)
        val isCancelled = intent.getBooleanExtra(EXTRA_CANCELLED, false)

        val repository = AppRepository(context)
        val task = repository.getTaskById(taskId) ?: run {
            AppFileLogger.w(context, TAG, "Task #$taskId not found in database")
            Log.w(TAG, "Task $taskId not found in database")
            return
        }

        val prefs = PreferenceManager(context)
        if (!prefs.isTasksMasterEnabled) {
            Log.d(TAG, "Task $taskId execution skipped because Task Scheduler Engine is disabled by master switch")
            return
        }

        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (isCancelled) {
            notifManager.cancel(taskId.toInt())
            repository.updateTaskStatus(taskId, TaskStatus.DONE)
            Log.d(TAG, "Task $taskId cancelled by user")
            AppFileLogger.action(context, TAG, "Task #$taskId was skipped/cancelled by user")
            return
        }

        if (task.askBeforeSend && !isConfirmed) {
            AppFileLogger.action(context, TAG, "Task #${task.id} (${task.title}): Displayed pre-send confirmation notification")
            promptAskBeforeSend(context, notifManager, task)
            return
        }

        if (isConfirmed) {
            notifManager.cancel(taskId.toInt())
        }

        Log.d(TAG, "Alarm triggered for Task ID: $taskId. Routing to SequentialTaskEngine.")
        AppFileLogger.action(context, TAG, "Alarm triggered for Task #${task.id} (${task.title}) [${task.channel.displayName}] -> SequentialTaskEngine")
        val enqueued = com.jmd.autotext.engine.SequentialTaskEngine.getInstance(context).enqueue(taskId)
        if (!enqueued) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    executeTask(context, repository, task)
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing task $taskId", e)
                    AppFileLogger.e(context, TAG, "Error executing task #$taskId: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun promptAskBeforeSend(context: Context, notifManager: NotificationManager, task: ScheduledTask) {
        val confirmIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_FIRE_TASK
            putExtra(AlarmScheduler.EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_CONFIRMED, true)
        }
        val confirmPending = PendingIntent.getBroadcast(
            context,
            (task.id * 10 + 1).toInt(),
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_FIRE_TASK
            putExtra(AlarmScheduler.EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_CANCELLED, true)
        }
        val cancelPending = PendingIntent.getBroadcast(
            context,
            (task.id * 10 + 2).toInt(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val recipientLabel = if (task.recipients.isNotEmpty()) task.recipients.joinToString(", ") else "Recipient"
        val notif = NotificationCompat.Builder(context, AutoTextApplication.CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ready to send: ${task.channel.displayName}")
            .setContentText("To: $recipientLabel - \"${task.messageText.take(45)}\"")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Send Now", confirmPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Skip", cancelPending)
            .build()

        notifManager.notify(task.id.toInt(), notif)
    }

    private suspend fun executeTask(context: Context, repository: AppRepository, task: ScheduledTask) {
        var allSuccess = true

        // Acquire wake lock to wake screen for WhatsApp automation
        var wakeLock: PowerManager.WakeLock? = null
        if (task.channel == ChannelType.WHATSAPP || task.channel == ChannelType.WHATSAPP_BUSINESS ||
            task.channel == ChannelType.WHATSAPP_GROUP || task.channel == ChannelType.WHATSAPP_STATUS) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                @Suppress("DEPRECATION")
                wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "AutoText:TaskExecutionWakeLock"
                )
                wakeLock.acquire(15000L)
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock: ${e.message}")
            }
        }

        try {
            when (task.channel) {
                ChannelType.SMS -> {
                    val smsSender = SmsSender(context)
                    for (i in task.recipients.indices) {
                        val phone = task.recipients[i]
                        val name = if (i in task.recipientNames.indices) task.recipientNames[i] else "Friend"
                        val personalizedMsg = personalize(task.messageText, name, phone)

                        val sendResult = smsSender.sendSmsWithResult(phone, personalizedMsg, task.simSlot, enforceQuota = true)
                        val isQuota = sendResult.reason.contains("limit", ignoreCase = true) || sendResult.reason.contains("quota", ignoreCase = true)
                        val logStatus = if (sendResult.success) "SUCCESS" else if (isQuota) "QUEUED" else "FAILED"
                        val logDetails = if (sendResult.success) "Sent via SIM slot ${task.simSlot}, parts: ${sendResult.segments}" else sendResult.reason

                        repository.insertLog(
                            ExecutionLog(
                                taskId = task.id,
                                channel = "SMS",
                                recipient = phone,
                                messageSnippet = personalizedMsg.take(160),
                                status = logStatus,
                                details = logDetails
                            )
                        )
                        AppFileLogger.campaignLedger(
                            context = context,
                            campaignId = task.id,
                            channel = "SMS",
                            recipientName = name,
                            recipientPhone = phone,
                            status = logStatus,
                            messageSnippet = personalizedMsg.take(80),
                            attempt = 1,
                            details = logDetails
                        )
                        if (!sendResult.success) allSuccess = false
                        if (isQuota) {
                            Log.w(TAG, "Task ${task.id} paused due to India SMS Quota: ${sendResult.reason}")
                            AppFileLogger.w(context, TAG, "Task #${task.id} paused: ${sendResult.reason}")
                            break
                        }
                    }
                }
                ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_GROUP, ChannelType.WHATSAPP_STATUS -> {
                    com.jmd.autotext.service.AccessibilityHealthManager.ensureServiceAlive(context)
                    val waSender = WhatsAppAutomationSender(context)
                    if (task.channel == ChannelType.WHATSAPP_STATUS) {
                        AppFileLogger.action(context, TAG, "Task #${task.id}: Dispatching WhatsApp Status story update")
                        AutoTextAccessibilityService.clearPending()
                        AutoTextAccessibilityService.markDispatchPending()
                        val statusTarget = task.recipients.firstOrNull() ?: "STATUS"
                        val dispatched = waSender.send(
                            taskId = task.id,
                            channel = task.channel,
                            recipientPhone = statusTarget,
                            messageText = task.messageText,
                            shouldRelock = PreferenceManager(context).isAutoLockAfterSendEnabled,
                            mediaUri = task.mediaUri
                        )
                        if (!dispatched) {
                            allSuccess = false
                            AppFileLogger.w(context, TAG, "Task #${task.id}: Failed launching WhatsApp Status intent")
                            AppFileLogger.campaignLedger(
                                context = context,
                                campaignId = task.id,
                                channel = "WhatsApp Status",
                                recipientName = "My Status",
                                recipientPhone = "STATUS",
                                status = "FAILED",
                                messageSnippet = task.messageText.take(80),
                                attempt = 1,
                                details = "Failed to launch WhatsApp Status intent"
                            )
                        } else {
                            val maxWaitMs = 25000L
                            var waitedMs = 0L
                            while (AutoTextAccessibilityService.lastSendResult == AutoTextAccessibilityService.RESULT_PENDING && waitedMs < maxWaitMs) {
                                delay(500.milliseconds)
                                waitedMs += 500L
                            }
                            if (AutoTextAccessibilityService.lastSendResult != AutoTextAccessibilityService.RESULT_SUCCESS) {
                                allSuccess = false
                            }
                        }
                    } else {
                        val totalRecipients = task.recipients.size
                        for (i in task.recipients.indices) {
                            val phone = task.recipients[i]
                            val name = if (i in task.recipientNames.indices) task.recipientNames[i] else "Friend"
                            val personalizedMsg = personalize(task.messageText, name, phone)
                            val isLast = i == totalRecipients - 1

                            AppFileLogger.action(context, TAG, "Task #${task.id}: Dispatching WhatsApp to $phone ($name) [${i + 1}/$totalRecipients]")
                            AutoTextAccessibilityService.clearPending()
                            AutoTextAccessibilityService.markDispatchPending()
                            val dispatched = waSender.send(
                                taskId = task.id,
                                channel = task.channel,
                                recipientPhone = phone,
                                messageText = personalizedMsg,
                                shouldRelock = isLast && PreferenceManager(context).isAutoLockAfterSendEnabled,
                                mediaUri = task.mediaUri
                            )
                            if (!dispatched) {
                                allSuccess = false
                                AppFileLogger.w(context, TAG, "Task #${task.id}: Failed launching WhatsApp intent for $phone")
                                AppFileLogger.campaignLedger(
                                    context = context,
                                    campaignId = task.id,
                                    channel = task.channel.displayName,
                                    recipientName = name,
                                    recipientPhone = phone,
                                    status = "FAILED",
                                    messageSnippet = personalizedMsg.take(80),
                                    attempt = 1,
                                    details = "Failed to launch WhatsApp intent"
                                )
                            } else {
                                val maxWaitMs = 20000L
                                var waitedMs = 0L
                                while (AutoTextAccessibilityService.lastSendResult == AutoTextAccessibilityService.RESULT_PENDING && waitedMs < maxWaitMs) {
                                    delay(500.milliseconds)
                                    waitedMs += 500L
                                }

                                val result = AutoTextAccessibilityService.lastSendResult
                                if (result != AutoTextAccessibilityService.RESULT_SUCCESS) {
                                    allSuccess = false
                                    AppFileLogger.w(context, TAG, "Task #${task.id}: Recipient $phone failed with result code $result")
                                }

                                if (totalRecipients > 1 && !isLast) {
                                    delay(2.seconds)
                                }
                            }
                        }
                    }
                }
                ChannelType.CALL -> {
                    val phone = task.recipients.firstOrNull() ?: ""
                    if (phone.isNotEmpty()) {
                        try {
                            val hasCallPerm = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.CALL_PHONE
                            ) == PackageManager.PERMISSION_GRANTED

                            val callIntent = if (hasCallPerm) {
                                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            } else {
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            }
                            context.startActivity(callIntent)
                            repository.insertLog(
                                ExecutionLog(
                                    taskId = task.id,
                                    channel = "Phone Call",
                                    recipient = phone,
                                    messageSnippet = "Placed call to $phone",
                                    status = "SUCCESS"
                                )
                            )
                            AppFileLogger.action(context, TAG, "Task #${task.id}: Placed call to $phone")
                            AppFileLogger.campaignLedger(
                                context = context,
                                campaignId = task.id,
                                channel = "Phone Call",
                                recipientName = "Contact",
                                recipientPhone = phone,
                                status = "SUCCESS",
                                messageSnippet = "Placed call to $phone"
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to place call: ${e.message}", e)
                            AppFileLogger.e(context, TAG, "Failed to place call to $phone: ${e.message}", e)
                            allSuccess = false
                        }
                    }
                }
                ChannelType.FAKE_CALL -> {
                    val fakeIntent = Intent(context, FakeCallActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("caller_name", task.fakeCallerName)
                        putExtra("caller_number", task.fakeCallerNumber)
                        putExtra("caller_style", task.fakeCallStyle)
                    }
                    context.startActivity(fakeIntent)
                    repository.insertLog(
                        ExecutionLog(
                            taskId = task.id,
                            channel = "Fake Call",
                            recipient = task.fakeCallerName,
                            messageSnippet = "Fake call triggered (${task.fakeCallStyle})",
                            status = "SUCCESS"
                        )
                    )
                }
                ChannelType.REMINDER -> {
                    val remIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("reminder_title", task.title.ifEmpty { "Reminder Alert" })
                        putExtra("reminder_text", task.messageText)
                    }
                    context.startActivity(remIntent)
                    repository.insertLog(
                        ExecutionLog(
                            taskId = task.id,
                            channel = "Reminder",
                            recipient = "Self",
                            messageSnippet = task.messageText.take(60),
                            status = "SUCCESS"
                        )
                    )
                }
                ChannelType.SOUND_MODE -> {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    val canAccessPolicy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        notifManager.isNotificationPolicyAccessGranted
                    } else true

                    when (task.soundModeSetting.uppercase()) {
                        "SILENT" -> {
                            if (canAccessPolicy) {
                                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                            } else {
                                audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                            }
                        }
                        "VIBRATE" -> {
                            if (canAccessPolicy) {
                                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                            } else {
                                audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                            }
                        }
                        else -> {
                            if (canAccessPolicy) {
                                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                            }
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                            audioManager.setStreamVolume(AudioManager.STREAM_RING, (maxVol * 0.7).toInt(), 0)
                        }
                    }
                    repository.insertLog(
                        ExecutionLog(
                            taskId = task.id,
                            channel = "Sound Mode",
                            recipient = "System",
                            messageSnippet = "Sound profile set to ${task.soundModeSetting}",
                            status = "SUCCESS"
                        )
                    )
                }
                ChannelType.EMAIL -> {
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, task.recipients.toTypedArray())
                        putExtra(Intent.EXTRA_SUBJECT, task.title)
                        putExtra(Intent.EXTRA_TEXT, task.messageText)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(emailIntent)
                    repository.insertLog(
                        ExecutionLog(
                            taskId = task.id,
                            channel = "Email",
                            recipient = task.recipients.joinToString(","),
                            messageSnippet = task.messageText.take(60),
                            status = "SUCCESS"
                        )
                    )
                }
            }

            // Update task status and handle recurrence
            val scheduler = AlarmScheduler(context)
            if (task.recurrence != RecurrenceType.ONCE) {
                val nextTrigger = scheduler.calculateNextTrigger(task, System.currentTimeMillis())
                val updatedTask = task.copy(
                    triggerTimeMillis = nextTrigger,
                    lastExecutedMillis = System.currentTimeMillis(),
                    executionCount = task.executionCount + 1,
                    status = TaskStatus.PENDING
                )
                repository.updateTask(updatedTask)
                scheduler.scheduleTask(updatedTask)
                Log.d(TAG, "Recurrence rescheduled task ${task.id} for $nextTrigger")
                AppFileLogger.action(context, TAG, "Task #${task.id} recurring execution completed. Next run scheduled for trigger timestamp: $nextTrigger")
            } else {
                val isAsyncChannel = task.channel in listOf(
                    ChannelType.WHATSAPP,
                    ChannelType.WHATSAPP_BUSINESS,
                    ChannelType.WHATSAPP_GROUP,
                    ChannelType.WHATSAPP_STATUS
                )

                if (isAsyncChannel && allSuccess) {
                    repository.updateTaskStatus(
                        id = task.id,
                        status = TaskStatus.DONE,
                        lastExecuted = System.currentTimeMillis()
                    )
                    AppFileLogger.action(context, TAG, "Task #${task.id} (${task.title}): WhatsApp automation completed with status: DONE")
                } else {
                    val finalStatus = if (allSuccess) TaskStatus.DONE else TaskStatus.FAILED
                    repository.updateTaskStatus(
                        id = task.id,
                        status = finalStatus,
                        lastExecuted = System.currentTimeMillis()
                    )
                    AppFileLogger.action(context, TAG, "Task #${task.id} (${task.title}) finished with status: ${finalStatus.name}")
                }
            }
        } finally {
            if (wakeLock?.isHeld == true) {
                runCatching { wakeLock.release() }
            }
        }
    }

    private fun personalize(template: String, name: String, phone: String): String {
        val dateStr = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val timeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        return template
            .replace("{name}", name, ignoreCase = true)
            .replace("{first_name}", (name.split(" ").firstOrNull() ?: name), ignoreCase = true)
            .replace("{phone}", phone, ignoreCase = true)
            .replace("{date}", dateStr, ignoreCase = true)
            .replace("{time}", timeStr, ignoreCase = true)
    }
}

