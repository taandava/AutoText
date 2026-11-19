/**
 * Package: com.jmd.superwa.data.model
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [BackupDataDto].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for BackupDataDto.
 *
 * Linked With:
 * - Android Framework & Jetpack: [kotlinx.serialization.Serializable]
 */

package com.jmd.superwa.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What it is:
 * Pure Kotlin, reflection-free, and high-performance serialization models for complete offline backup export/import.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [BackupDataDto].
 *
 * Linked with:
 * - Architecture: [BackupDataDto], [BackupDataDto]
 */
@Serializable
data class BackupDataDto(
    val version: Int = 2,
    val app: String = "Super",
    val exportedAt: Long = System.currentTimeMillis(),
    val tasks: List<TaskBackupDto> = emptyList(),
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
 * - Component: [BackupDataDto]
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
 * Component definition for [AutoReplyBackupDto].
 *
 * What it does:
 * Provides domain logic and lifecycle management for AutoReplyBackupDto.
 *
 * Linked with:
 * - Component: [BackupDataDto]
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
 * - Component: [BackupDataDto]
 */
@Serializable
data class CampaignBackupDto(
    val id: Long = 0,
    val title: String,
    val channel: String,
    @SerialName("messageTemplate") val message: String,
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
 * - Component: [BackupDataDto]
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
