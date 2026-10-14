package com.jmd.autotext.model

enum class ChannelType(val displayName: String, val iconName: String) {
    SMS("SMS", "sms"),
    WHATSAPP("WhatsApp", "whatsapp"),
    WHATSAPP_BUSINESS("WA Business", "whatsapp_biz"),
    WHATSAPP_GROUP("WA Group", "whatsapp_group"),
    WHATSAPP_STATUS("WA Status", "whatsapp_status"),
    EMAIL("Email", "email"),
    CALL("Phone Call", "call"),
    FAKE_CALL("Fake Call", "fake_call"),
    REMINDER("Reminder", "alarm"),
    SOUND_MODE("Sound Mode", "volume")
}

enum class RecurrenceType(val displayName: String) {
    ONCE("Once"),
    DAILY("Daily"),
    WEEKDAYS("Mon to Fri"),
    WEEKENDS("Sat & Sun"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    CUSTOM_INTERVAL("Custom Interval")
}

enum class TaskStatus(val displayName: String) {
    PENDING("Pending"),
    DONE("Sent / Done"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

data class ScheduledTask(
    val id: Long = 0,
    val title: String = "",
    val channel: ChannelType = ChannelType.SMS,
    val recipients: List<String> = emptyList(), // phone numbers or emails
    val recipientNames: List<String> = emptyList(),
    val messageText: String = "",
    val triggerTimeMillis: Long = 0,
    val recurrence: RecurrenceType = RecurrenceType.ONCE,
    val customIntervalMinutes: Int = 0,
    val selectedDaysOfWeek: List<Int> = emptyList(), // 1=Sunday, 2=Monday...
    val status: TaskStatus = TaskStatus.PENDING,
    val simSlot: Int = 0, // 0 = default, 1 = SIM 1, 2 = SIM 2
    val askBeforeSend: Boolean = false,
    val soundModeSetting: String = "NORMAL", // SILENT, VIBRATE, NORMAL
    val fakeCallerName: String = "Incoming Call",
    val fakeCallerNumber: String = "+1 (555) 019-2834",
    val fakeCallStyle: String = "PIXEL", // PIXEL, SAMSUNG, WHATSAPP
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastExecutedMillis: Long = 0,
    val executionCount: Int = 0,
    val mediaUri: String = ""
)
