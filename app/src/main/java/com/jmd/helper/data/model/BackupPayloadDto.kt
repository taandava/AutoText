package com.jmd.helper.data.model

import kotlinx.serialization.Serializable

/**
 * Pure Kotlin, reflection-free, and high-performance serialization models for complete offline backup export/import.
 */
@Serializable
data class BackupPayloadDto(
    val version: Int = 2,
    val app: String = "Helper",
    val exportedAt: Long = System.currentTimeMillis(),
    val tasks: List<TaskBackupDto> = emptyList(),
    val templates: List<TemplateBackupDto> = emptyList(),
    val autoReplyRules: List<AutoReplyBackupDto> = emptyList(),
    val dailyCampaigns: List<CampaignBackupDto> = emptyList(),
    val contacts: List<ContactBackupDto> = emptyList()
)

@Serializable
data class TaskBackupDto(
    val id: Long = 0,
    val title: String,
    val message: String,
    val recipient: String,
    val scheduledTime: Long,
    val channel: String,
    val status: String,
    val recurrence: String = "NONE",
    val recurrenceIntervalMins: Long = 0,
    val mediaUri: String? = null,
    val callDurationSeconds: Int = 0,
    val simSlot: Int = 0,
    val soundMode: String? = null
)

@Serializable
data class TemplateBackupDto(
    val id: Long = 0,
    val title: String,
    val category: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class AutoReplyBackupDto(
    val id: Long = 0,
    val title: String,
    val channel: String,
    val eventType: String,
    val keywordTrigger: String = "*",
    val replyMessage: String,
    val isEnabled: Boolean = true,
    val simSlot: Int = 0
)

@Serializable
data class CampaignBackupDto(
    val id: Long = 0,
    val title: String,
    val channel: String,
    val messageTemplate: String,
    val dailyLimit: Int = 50,
    val simSlot: Int = 0,
    val isActive: Boolean = true
)

@Serializable
data class ContactBackupDto(
    val id: Long = 0,
    val displayName: String,
    val phoneNumber: String,
    val normalizedPhone: String,
    val isWhatsAppUser: Boolean = false,
    val lastReachedTimestamp: Long = 0,
    val reachCount: Int = 0
)

