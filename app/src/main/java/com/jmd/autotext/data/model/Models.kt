

/**
 * Package: com.jmd.autotext.data.model
 * Module: Data Models Layer
 *
 * Defines core immutable data contracts and domain models across the entire AutoText ecosystem.
 * Encompasses scheduled tasks, dynamic contact items, campaign configurations, auto-reply rules,
 * execution logs, channel types, and recurrence rules.
 *
 * Linked With:
 * - Persistence: [com.jmd.autotext.data.db.DatabaseHelper]
 * - Repositories: [com.jmd.autotext.data.repo.AppRepository], [com.jmd.autotext.data.repo.TaskRepository]
 * - Engines: [com.jmd.autotext.engine.SequentialTaskEngine], [com.jmd.autotext.engine.ContactIngestionEngine]
 * - UI Screens: `ComposeTaskScreen`, `TasksScreen`,
 *   `TasksScreen`, `DailyCampaignScreen`
 */
package com.jmd.autotext.data.model

/**
 * Model representing an ingested contact inside the dynamic outreach pool.
 *
 * Tracks individual contact outreach status, last sent timestamps, and dispatch counts
 * across both SMS and WhatsApp channels.
 */
data class DynamicContactItem(
    val id: Long = 0,
    val name: String = "",
    val phone: String,
    val normalizedNumber: String,
    val source: String = "CONTACT",
    val enabled: Boolean = true,
    val lastSentAt: Long = 0,
    val sendCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val waLastSentAt: Long = 0,
    val waSendCount: Int = 0,
    val smsStatus: String = "PENDING",
    val waStatus: String = "PENDING",
    val photoUri: String = "",
)

data class DailyCampaignConfig(
    val id: Long = 1,
    val channel: String = "SMS",
    val title: String = "Daily Contact Campaign",
    val messageTemplate: String = "",
    val dailyLimit: Int = 30,
    val intervalSeconds: Int = 8,
    val scheduledHour: Int = 9,
    val scheduledMinute: Int = 0,
    val simSlot: Int = 0,
    val autoScanEnabled: Boolean = true,
    val isActive: Boolean = false,
    val lastRunDate: String = "",
)

data class IngestionSummary(
    val contactsScanned: Int = 0,
    val smsScanned: Int = 0,
    val callLogsScanned: Int = 0,
    val waChatsScanned: Int = 0,
    val waBusinessScanned: Int = 0,
    val newAdded: Int = 0,
    val totalPoolCount: Int = 0
)

data class DynamicPoolStats(
    val totalCount: Int = 0,
    val unsentCount: Int = 0,
    val sentCount: Int = 0
)

// ==================== COMMUNICATION CHANNEL TYPE ENUMERATION ====================

enum class ChannelType(val displayName: String, val emoji: String = "") {
    SMS("SMS", "📩"),
    WHATSAPP("WhatsApp", "💬"),
    WHATSAPP_BUSINESS("WA Business", "💼"),
    WHATSAPP_GROUP("WA Group", "👥"),
    WHATSAPP_STATUS("WA Status", "📸"),
    WHATSAPP_BUSINESS_STATUS("W4B Status", "📸💼"),
    CALL("Phone Call", "📞"),
    WHATSAPP_CALL("WhatsApp Call", "📞"),
    WHATSAPP_BUSINESS_CALL("WA Business Call", "📞💼"),
    FAKE_CALL("Fake Call", "🎭"),
    REMINDER("Reminder", "⏰"),
    SOUND_MODE("Sound Mode", "🔊")
}

// ==================== EXECUTION LOG DATA MODELS ====================

data class ExecutionLog(
    val id: Long = 0,
    val taskId: Long = 0,
    val channel: String,
    val recipient: String,
    val messageSnippet: String,
    val status: String,
    val details: String = "",
    val segments: Int = 1,
    val timestampMillis: Long = System.currentTimeMillis()
)

// ==================== RECIPIENT & CONTACT DATA MODELS ====================

data class RecipientItem(
    val id: Long = 0,
    val name: String,
    val phoneOrEmail: String,
    val groupName: String = "General",
    val photoUri: String = ""
)

data class WhatsAppContactItem(
    val name: String,
    val phone: String,
    val normalizedPhone: String = "",
    val accountType: String = "com.whatsapp",
    val photoUri: String = ""
)

data class WhatsAppGroupItem(
    val id: Long = 0,
    val name: String,
    val groupJid: String = "",
    val memberCountStr: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ==================== AUTOMATIC REPLY RULE DATA MODELS ====================

data class AutoReplyRule(
    val id: Long = 0,
    val name: String,
    val channel: ChannelType = ChannelType.WHATSAPP,
    val replyText: String,
    val triggerPattern: String = "*",
    val isExactMatch: Boolean = false,
    val replyToContactsOnly: Boolean = false,
    val replyToNonContactsOnly: Boolean = false,
    val startTimeHour: Int = 0,
    val startTimeMinute: Int = 0,
    val endTimeHour: Int = 23,
    val endTimeMinute: Int = 59,
    val cooldownSeconds: Int = 30,
    val callEventType: String = "BOTH",
    val maxDailyReplies: Int = 1,
    val isEnabled: Boolean = true
)

// ==================== CONTACT SEQUENCE STEP DATA MODELS ====================

data class ContactSequence(
    val id: Long = 0,
    val contactName: String = "",
    val contactPhone: String = "",
    val title: String = "",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val steps: List<ContactSequenceStep> = emptyList()
)

data class ContactSequenceStep(
    val id: Long = 0,
    val sequenceId: Long = 0,
    val stepOrder: Int = 1,
    val channel: ChannelType = ChannelType.SMS,
    val messageText: String = "",
    val delayMinutesFromPrevious: Int = 0,
    val specificTriggerTime: Long = 0,
    val simSlot: Int = 0
)

// ==================== SCHEDULED TASK & RECURRENCE DATA MODELS ====================

enum class RepeatUnit(val displayName: String, val singularName: String) {
    SECONDS("Seconds", "Second"),
    MINUTES("Minutes", "Minute"),
    HOURS("Hours", "Hour"),
    DAYS("Days", "Day"),
    WEEKS("Weeks", "Week"),
    MONTHS("Months", "Month");

    fun toRecurrenceType(): RecurrenceType = when (this) {
        SECONDS -> RecurrenceType.REPEAT_SECONDS
        MINUTES -> RecurrenceType.REPEAT_MINUTES
        HOURS -> RecurrenceType.REPEAT_HOURS
        DAYS -> RecurrenceType.REPEAT_DAYS
        WEEKS -> RecurrenceType.REPEAT_WEEKS
        MONTHS -> RecurrenceType.REPEAT_MONTHS
    }

    companion object {
        fun fromString(str: String?): RepeatUnit {
            val clean = str?.trim()?.uppercase() ?: "MINUTES"
            return when {
                clean.startsWith("SEC") -> SECONDS
                clean.startsWith("MIN") -> MINUTES
                clean.startsWith("HOUR") -> HOURS
                clean.startsWith("DAY") -> DAYS
                clean.startsWith("WEEK") -> WEEKS
                clean.startsWith("MONTH") -> MONTHS
                else -> MINUTES
            }
        }
    }
}

enum class RecurrenceType(val displayName: String) {
    ONCE("Once"),
    REPEAT_SECONDS("Repeat (Seconds)"),
    REPEAT_MINUTES("Repeat (Minutes)"),
    REPEAT_HOURS("Repeat (Hours)"),
    REPEAT_DAYS("Repeat (Days)"),
    REPEAT_WEEKS("Repeat (Weeks)"),
    REPEAT_MONTHS("Repeat (Months)"),
    // Legacy options preserved for backward compatibility with existing tasks
    DAILY("Daily"),
    WEEKDAYS("Mon to Fri"),
    WEEKENDS("Sat & Sun"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    CUSTOM_INTERVAL("Custom Interval");

    companion object {
        fun fromString(value: String?): RecurrenceType {
            if (value.isNullOrBlank()) return ONCE
            val clean = value.trim().uppercase().replace(" ", "_").replace("-", "_")
            return entries.firstOrNull {
                (it.name == clean) || it.displayName.equals(value.trim(), ignoreCase = true)
            } ?: when {
                clean.startsWith("REPEAT_SEC") || clean.contains("SECOND") -> REPEAT_SECONDS
                clean.startsWith("REPEAT_HOUR") || clean.contains("HOUR") -> REPEAT_HOURS
                clean.startsWith("REPEAT_DAY") || clean == "DAYS" -> REPEAT_DAYS
                clean.startsWith("REPEAT_WEEK") || clean == "WEEKS" -> REPEAT_WEEKS
                clean.startsWith("REPEAT_MONTH") || clean == "MONTHS" -> REPEAT_MONTHS
                clean.startsWith("REPEAT_MIN") || clean == "MINUTES" -> REPEAT_MINUTES
                clean.contains("INTERVAL") || clean.contains("MINUTE") || clean.contains("CUSTOM") -> CUSTOM_INTERVAL
                clean.contains("DAILY") || clean.contains("DAY") -> DAILY
                clean.contains("WEEKDAY") || clean.contains("MON") -> WEEKDAYS
                clean.contains("WEEKEND") || clean.contains("SAT") -> WEEKENDS
                clean.contains("WEEK") -> WEEKLY
                clean.contains("MONTH") -> MONTHLY
                clean.contains("YEAR") -> YEARLY
                else -> ONCE
            }
        }
    }
}

enum class TaskStatus(val displayName: String) {
    PENDING("Pending"),
    PENDING_CONFIRMATION("Awaiting Confirmation"),
    DONE("Sent / Done"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

data class ScheduledTask(
    val id: Long = 0,
    val title: String = "",
    val channel: ChannelType = ChannelType.SMS,
    val recipients: List<String> = emptyList(),
    val recipientNames: List<String> = emptyList(),
    val messageText: String = "",
    val triggerTimeMillis: Long = 0,
    val recurrence: RecurrenceType = RecurrenceType.ONCE,
    val customIntervalMinutes: Int = 0,
    val selectedDaysOfWeek: List<Int> = emptyList(),
    val status: TaskStatus = TaskStatus.PENDING,
    val simSlot: Int = 0,
    val askBeforeSend: Boolean = false,
    val soundModeSetting: String = "NORMAL",
    val fakeCallerName: String = "Incoming Call",
    val fakeCallerNumber: String = "+1 (555) 019-2834",
    val fakeCallStyle: String = "PIXEL",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastExecutedMillis: Long = 0,
    val executionCount: Int = 0,
    val mediaUri: String = "",
    val callDurationSeconds: Int = 0,
    val includeDialTimeInCallDuration: Boolean = false,
    val callSequenceMode: String = "GIVEN", // "GIVEN" or "RANDOM"
    val delayBetweenCallsSeconds: Int = 5,
    val callFailurePolicy: String = "CONTINUE" // "CONTINUE" or "STOP_ON_FAILURE"
)

// ==================== MESSAGE TEMPLATE DATA MODELS ====================

data class MessageTemplate(
    val id: Long = 0,
    val title: String,
    val category: String = "General",
    val content: String
)

// ==================== CHAT PICKER METADATA MODELS ====================

enum class PickedChatType(val displayName: String) {
    INDIVIDUAL("Individual"),
    GROUP("Group"),
    BROADCAST("Broadcast List"),
    STATUS("Status"),
    CHANNEL("Channel")
}

data class PickedChatInfo(
    val title: String,
    val type: PickedChatType,
    val phoneOrId: String? = null,
    val subtitle: String = ""
)
