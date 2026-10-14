package com.jmd.autotext.model

data class DripCampaign(
    val id: Long = 0,
    val title: String,
    val recipients: List<String> = emptyList(),
    val isEnabled: Boolean = true,
    val currentStep: Int = 0,
    val totalSteps: Int = 0
)

data class DripStep(
    val id: Long = 0,
    val campaignId: Long,
    val stepOrder: Int,
    val delayDays: Int,
    val delayHours: Int,
    val messageText: String,
    val channel: ChannelType = ChannelType.WHATSAPP
)

data class DynamicContactItem(
    val id: Long = 0,
    val name: String = "",
    val phone: String,
    val normalizedNumber: String,
    val source: String = "CONTACT", // "CONTACT", "SMS", "CALLLOG", "MANUAL"
    val enabled: Boolean = true,
    val lastSentAt: Long = 0,
    val sendCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val waLastSentAt: Long = 0,
    val waSendCount: Int = 0,
    val smsStatus: String = "PENDING",
    val waStatus: String = "PENDING",
    val photoUri: String = ""
)

data class DailyCampaignConfig(
    val id: Long = 1,
    val channel: String = "SMS", // "SMS", "WHATSAPP", "WHATSAPP_BUSINESS"
    val title: String = "Daily Contact Campaign",
    val messageTemplate: String = "Hi {name}, hope you have a great day! Wishing you success and happiness.",
    val dailyLimit: Int = 30,
    val intervalSeconds: Int = 8,
    val scheduledHour: Int = 9,
    val scheduledMinute: Int = 0,
    val simSlot: Int = 0,
    val autoScanEnabled: Boolean = true,
    val isActive: Boolean = false,
    val lastRunDate: String = ""
)

data class IngestionSummary(
    val contactsScanned: Int = 0,
    val smsScanned: Int = 0,
    val callLogsScanned: Int = 0,
    val newAdded: Int = 0,
    val totalPoolCount: Int = 0
)

data class DynamicPoolStats(
    val totalCount: Int = 0,
    val unsentCount: Int = 0,
    val sentCount: Int = 0
)
