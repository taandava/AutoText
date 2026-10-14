package com.jmd.autotext.model

data class AutoReplyRule(
    val id: Long = 0,
    val name: String,
    val channel: ChannelType = ChannelType.WHATSAPP, // SMS, WHATSAPP, CALL (missed call & call ended)
    val replyText: String,
    val triggerPattern: String = "*", // "*" for all, or keywords comma-separated
    val isExactMatch: Boolean = false,
    val replyToContactsOnly: Boolean = false,
    val replyToNonContactsOnly: Boolean = false,
    val startTimeHour: Int = 0, // 0..23
    val startTimeMinute: Int = 0,
    val endTimeHour: Int = 23,
    val endTimeMinute: Int = 59,
    val cooldownMinutes: Int = 10, // Avoid replying more than once per contact within X min
    val callEventType: String = "BOTH", // "BOTH", "MISSED_CALL", "CALL_ENDED"
    val maxDailyReplies: Int = 1, // 1 = Once/day, 3 = 3x/day, 5 = 5x/day, N = custom, 0 = unlimited
    val isEnabled: Boolean = true
)

data class AutoForwardRule(
    val id: Long = 0,
    val name: String,
    val sourceType: String = "SMS", // "SMS" or "MISSED_CALL"
    val forwardToNumber: String,
    val filterSender: String = "", // empty = forward all
    val filterKeyword: String = "", // empty = forward all
    val isEnabled: Boolean = true
)
