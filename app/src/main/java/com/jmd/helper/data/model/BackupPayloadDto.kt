/**
 * Package: com.jmd.helper.data.model
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [BackupPayloadDto].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for BackupPayloadDto.
 *
 * Linked With:
 * - Android Framework & Jetpack: [kotlinx.serialization.Serializable]
 */

package com.jmd.helper.data.model

import kotlinx.serialization.Serializable

/**
 * What it is:
 * Pure Kotlin, reflection-free, and high-performance serialization models for complete offline backup export/import.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [BackupPayloadDto].
 *
 * Linked with:
 * - Architecture: [BackupPayloadDto], [BackupPayloadDto]
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

/**
 * What it is:
 * Component definition for [TaskBackupDto].
 *
 * What it does:
 * Provides domain logic and lifecycle management for TaskBackupDto.
 *
 * Linked with:
 * - Component: [BackupPayloadDto]
 */
@Serializable
data class TaskBackupDto(
    val id: Long = 0,
    val title: String,
    val message: String,
    val recipient: String,
    val recipientNames: String = "",
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

/**
 * What it is:
 * Component definition for [TemplateBackupDto].
 *
 * What it does:
 * Provides domain logic and lifecycle management for TemplateBackupDto.
 *
 * Linked with:
 * - Component: [BackupPayloadDto]
 */
@Serializable
data class TemplateBackupDto(
    val id: Long = 0,
    val title: String,
    val category: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * What it is:
 * Component definition for [AutoReplyBackupDto].
 *
 * What it does:
 * Provides domain logic and lifecycle management for AutoReplyBackupDto.
 *
 * Linked with:
 * - Component: [BackupPayloadDto]
 */
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

/**
 * What it is:
 * Component definition for [CampaignBackupDto].
 *
 * What it does:
 * Provides domain logic and lifecycle management for CampaignBackupDto.
 *
 * Linked with:
 * - Component: [BackupPayloadDto]
 */
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

/**
 * What it is:
 * Component definition for [ContactBackupDto].
 *
 * What it does:
 * Provides domain logic and lifecycle management for ContactBackupDto.
 *
 * Linked with:
 * - Component: [BackupPayloadDto]
 */
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
