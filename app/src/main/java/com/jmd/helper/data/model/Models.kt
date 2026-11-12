/**
 * Package: com.jmd.helper.data.model
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [Models].
 *
 * What it does:
 * Defines core immutable data contracts and domain models across the entire Helper ecosystem. Encompasses scheduled tasks, dynamic contact items, campaign configurations, auto-reply rules, execution logs, channel types, and recurrence rules. `DailyCampaignScreen`
 *
 * Linked With:
 * - Architecture: Standard application component linkages
 */

package com.jmd.helper.data.model

/**
 * What it is:
 * Model representing an ingested contact inside the dynamic outreach pool.
 *
 * What it does:
 * Tracks individual contact outreach status, last sent timestamps, and dispatch counts across both SMS and WhatsApp channels.
 *
 * Linked with:
 * - Architecture: [Models], [DynamicContactItem]
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
/**
 * What it is:
 * Component definition for [DailyCampaignConfig].
 *
 * What it does:
 * Provides domain logic and lifecycle management for DailyCampaignConfig.
 *
 * Linked with:
 * - Component: [Models]
 */

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
/**
 * What it is:
 * Component definition for [IngestionSummary].
 *
 * What it does:
 * Provides domain logic and lifecycle management for IngestionSummary.
 *
 * Linked with:
 * - Component: [Models]
 */

data class IngestionSummary(
    val contactsScanned: Int = 0,
    val smsScanned: Int = 0,
    val callLogsScanned: Int = 0,
    val waChatsScanned: Int = 0,
    val waBusinessScanned: Int = 0,
    val newAdded: Int = 0,
    val totalPoolCount: Int = 0
)
/**
 * What it is:
 * Component definition for [DynamicPoolStats].
 *
 * What it does:
 * Provides domain logic and lifecycle management for DynamicPoolStats.
 *
 * Linked with:
 * - Component: [Models]
 */

data class DynamicPoolStats(
    val totalCount: Int = 0,
    val unsentCount: Int = 0,
    val sentCount: Int = 0
)

// ==================== COMMUNICATION CHANNEL TYPE ENUMERATION ====================
/**
 * What it is:
 * Component definition for [ChannelType].
 *
 * What it does:
 * Provides domain logic and lifecycle management for ChannelType.
 *
 * Linked with:
 * - Component: [Models]
 */

enum class ChannelType(val displayName: String, val emoji: String = "") {
    SMS("SMS", "📩"),
    WHATSAPP("WhatsApp", "💬"),
    WHATSAPP_BUSINESS("WA Business", "💼"),
    WHATSAPP_GROUP("WhatsApp Group", "👥"),
    WHATSAPP_BUSINESS_GROUP("WA Business Group", "👥💼"),
    WHATSAPP_STATUS("WA Status", "📸"),
    WHATSAPP_BUSINESS_STATUS("W4B Status", "📸💼"),
    CALL("Phone Call", "📞"),
    WHATSAPP_CALL("WhatsApp Call", "📞"),
    WHATSAPP_BUSINESS_CALL("WA Business Call", "📞💼"),
    CALL_SIMULATION("Call Simulation", "🎭"),
    REMINDER("Reminder", "⏰");
    /**
     * What it is:
     * Companion object constant and factory provider for [Models].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [Models]
     */

    companion object {
        /**
         * Backward-compatible alias for [CALL_SIMULATION].
         */
        @JvmField
        val SIMULATED_CALL: ChannelType = CALL_SIMULATION
        /**
         * What it is:
         * Operational routine [fromString].
         *
         * What it does:
         * Executes fromString actions according to system invariants.
         *
         * Linked with:
         * - Component: [Models]
         */

        fun fromString(value: String?): ChannelType {
            if (value.isNullOrBlank()) return SMS
            return when (val clean = value.trim().uppercase()) {
                "CALL_SIMULATION", "SIMULATION", "SIMULATED_CALL" -> CALL_SIMULATION
                "WHATSAPP_GROUP", "GROUP", "WA_GROUP" -> WHATSAPP_GROUP
                "WHATSAPP_BUSINESS_GROUP", "W4B_GROUP", "WA_BUSINESS_GROUP" -> WHATSAPP_BUSINESS_GROUP
                else -> entries.firstOrNull { it.name == clean } ?: SMS
            }
        }
    }
}

// ==================== EXECUTION LOG DATA MODELS ====================
/**
 * What it is:
 * Component definition for [ExecutionLog].
 *
 * What it does:
 * Provides domain logic and lifecycle management for ExecutionLog.
 *
 * Linked with:
 * - Component: [Models]
 */

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
/**
 * What it is:
 * Component definition for [RecipientItem].
 *
 * What it does:
 * Provides domain logic and lifecycle management for RecipientItem.
 *
 * Linked with:
 * - Component: [Models]
 */

data class RecipientItem(
    val id: Long = 0,
    val name: String,
    val phoneOrEmail: String,
    val groupName: String = "General",
    val photoUri: String = ""
)
/**
 * What it is:
 * Component definition for [WhatsAppContactItem].
 *
 * What it does:
 * Provides domain logic and lifecycle management for WhatsAppContactItem.
 *
 * Linked with:
 * - Component: [Models]
 */

data class WhatsAppContactItem(
    val name: String,
    val phone: String,
    val normalizedPhone: String = "",
    val accountType: String = "com.whatsapp",
    val photoUri: String = ""
)
/**
 * What it is:
 * Component definition for [WhatsAppGroupItem].
 *
 * What it does:
 * Provides domain logic and lifecycle management for WhatsAppGroupItem.
 *
 * Linked with:
 * - Component: [Models]
 */

data class WhatsAppGroupItem(
    val name: String,
    val participantCount: String = "",
    val lastDiscoveredAt: Long = System.currentTimeMillis(),
    val packageName: String = "com.whatsapp",
    val photoUri: String = "",
    val description: String = ""
)

// ==================== QUICK REPLY RULE DATA MODELS ====================
/**
 * What it is:
 * Component definition for [AutoReplyRule].
 *
 * What it does:
 * Provides domain logic and lifecycle management for AutoReplyRule.
 *
 * Linked with:
 * - Component: [Models]
 */

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

// ==================== SCHEDULED TASK & RECURRENCE DATA MODELS ====================
/**
 * What it is:
 * Component definition for [RepeatUnit].
 *
 * What it does:
 * Provides domain logic and lifecycle management for RepeatUnit.
 *
 * Linked with:
 * - Component: [Models]
 */

enum class RepeatUnit(val displayName: String, val singularName: String) {
    SECONDS("Seconds", "Second"),
    MINUTES("Minutes", "Minute"),
    HOURS("Hours", "Hour"),
    DAYS("Days", "Day"),
    WEEKS("Weeks", "Week"),
    MONTHS("Months", "Month");
    /**
     * What it is:
     * Execution routine for [toRecurrenceType].
     *
     * What it does:
     * Coordinates toRecurrenceType operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [Models]
     */

    fun toRecurrenceType(): RecurrenceType = when (this) {
        SECONDS -> RecurrenceType.REPEAT_SECONDS
        MINUTES -> RecurrenceType.REPEAT_MINUTES
        HOURS -> RecurrenceType.REPEAT_HOURS
        DAYS -> RecurrenceType.REPEAT_DAYS
        WEEKS -> RecurrenceType.REPEAT_WEEKS
        MONTHS -> RecurrenceType.REPEAT_MONTHS
    }
    /**
     * What it is:
     * Companion object constant and factory provider for [Models].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [Models]
     */

    companion object {
        /**
         * What it is:
         * Operational routine [fromString].
         *
         * What it does:
         * Executes fromString actions according to system invariants.
         *
         * Linked with:
         * - Component: [Models]
         */
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
/**
 * What it is:
 * Component definition for [RecurrenceType].
 *
 * What it does:
 * Provides domain logic and lifecycle management for RecurrenceType.
 *
 * Linked with:
 * - Component: [Models]
 */

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
    /**
     * What it is:
     * Companion object constant and factory provider for [Models].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [Models]
     */

    companion object {
        /**
         * What it is:
         * Operational routine [fromString].
         *
         * What it does:
         * Executes fromString actions according to system invariants.
         *
         * Linked with:
         * - Component: [Models]
         */
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
/**
 * What it is:
 * Component definition for [TaskStatus].
 *
 * What it does:
 * Provides domain logic and lifecycle management for TaskStatus.
 *
 * Linked with:
 * - Component: [Models]
 */

enum class TaskStatus(val displayName: String) {
    DRAFT("Draft"),
    VALIDATING("Validating"),
    READY("Ready"),
    SCHEDULED("Scheduled"),
    PENDING("Pending"),
    RUNNING("Running"),
    SUCCESS("Success"),
    DONE("Sent / Done"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    PAUSED("Paused"),
    PENDING_CONFIRMATION("Awaiting Confirmation");

    val isPendingOrScheduled: Boolean
        get() = this == PENDING || this == SCHEDULED || this == READY

    val isSuccessOrDone: Boolean
        get() = this == SUCCESS || this == DONE

    val isTerminal: Boolean
        get() = this == SUCCESS || this == DONE || this == FAILED || this == CANCELLED

    val isRunning: Boolean
        get() = this == RUNNING

    val isPaused: Boolean
        get() = this == PAUSED
    /**
     * What it is:
     * Companion object constant and factory provider for [Models].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [Models]
     */

    companion object {
        /**
         * What it is:
         * Operational routine [fromString].
         *
         * What it does:
         * Executes fromString actions according to system invariants.
         *
         * Linked with:
         * - Component: [Models]
         */
        fun fromString(value: String?): TaskStatus {
            if (value.isNullOrBlank()) return PENDING
            val clean = value.trim().uppercase()
            return entries.firstOrNull { it.name == clean } ?: when {
                clean == "DONE" || clean == "SUCCESS" || clean == "SENT" -> SUCCESS
                clean == "PENDING" || clean == "SCHEDULED" || clean == "ACTIVE" -> SCHEDULED
                clean == "FAILED" -> FAILED
                clean == "CANCELLED" || clean == "CANCELED" -> CANCELLED
                clean == "PAUSED" -> PAUSED
                clean == "RUNNING" -> RUNNING
                clean == "READY" -> READY
                clean == "VALIDATING" -> VALIDATING
                clean == "DRAFT" -> DRAFT
                clean == "PENDING_CONFIRMATION" || clean.contains("CONFIRM") -> PENDING_CONFIRMATION
                else -> PENDING
            }
        }
        /**
         * What it is:
         * Operational routine [canTransition].
         *
         * What it does:
         * Executes canTransition actions according to system invariants.
         *
         * Linked with:
         * - Component: [Models]
         */

        fun canTransition(from: TaskStatus, to: TaskStatus): Boolean {
            return from == to || when (from) {
                DRAFT -> to == VALIDATING || to == CANCELLED
                VALIDATING -> to == READY || to == FAILED || to == CANCELLED
                READY -> to == SCHEDULED || to == RUNNING || to == CANCELLED || to == PAUSED
                SCHEDULED, PENDING -> to == RUNNING || to == PAUSED || to == CANCELLED
                RUNNING -> to == SUCCESS || to == DONE || to == FAILED || to == CANCELLED || to == PAUSED || to == SCHEDULED || to == PENDING || to == PENDING_CONFIRMATION
                PAUSED -> to == SCHEDULED || to == PENDING || to == CANCELLED
                SUCCESS, DONE -> to == SCHEDULED || to == PENDING || to == RUNNING
                FAILED -> to == SCHEDULED || to == PENDING || to == RUNNING
                CANCELLED -> to == SCHEDULED || to == PENDING
                PENDING_CONFIRMATION -> to == SUCCESS || to == DONE || to == FAILED || to == CANCELLED
            }
        }
    }
}
/**
 * What it is:
 * Component definition for [ScheduledTask].
 *
 * What it does:
 * Provides domain logic and lifecycle management for ScheduledTask.
 *
 * Linked with:
 * - Component: [Models]
 */

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
    val simulatedCallerName: String = "Incoming Call",
    val simulatedCallerNumber: String = "+91 98765 43210",
    val simulatedCallStyle: String = "PIXEL",
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

// ==================== CHAT PICKER METADATA MODELS ====================
/**
 * What it is:
 * Component definition for [PickedChatType].
 *
 * What it does:
 * Provides domain logic and lifecycle management for PickedChatType.
 *
 * Linked with:
 * - Component: [Models]
 */

enum class PickedChatType(val displayName: String) {
    INDIVIDUAL("Individual"),
    GROUP("Group"),
    BROADCAST("Broadcast List"),
    STATUS("Status"),
    CHANNEL("Channel")
}
/**
 * What it is:
 * Component definition for [PickedChatInfo].
 *
 * What it does:
 * Provides domain logic and lifecycle management for PickedChatInfo.
 *
 * Linked with:
 * - Component: [Models]
 */

data class PickedChatInfo(
    val title: String,
    val type: PickedChatType,
    val phoneOrId: String? = null,
    val subtitle: String = ""
)

