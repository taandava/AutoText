/**
 * Package: com.jmd.helper.ui.model
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [UiModels].
 *
 * What it does:
 * Defines lightweight presentation models, recipient chips, task type picker items, and auto-reply selection items for Compose screens. `AutoReplyScreen`
 *
 * Linked With:
 * - Models & Entities: [com.jmd.helper.data.model.*]
 * - UI Screens & Components: [androidx.compose.ui.graphics.*], [androidx.compose.ui.graphics.vector.ImageVector]
 */

package com.jmd.helper.ui.model

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.jmd.helper.data.model.*

/**
 * What it is:
 * UI model representing an Auto-Reply channel category item in selection sheets and cards.
 *
 * What it does:
 * Encapsulates the visual styling, iconography, and event description for configured reply triggers.
 *
 * Linked with:
 * - Architecture: [UiModels], [AutoReplyTypeItem]
 * @property channel Targeted communication channel ([ChannelType.SMS], [ChannelType.WHATSAPP], or [ChannelType.WHATSAPP_BUSINESS]).
 * @property title Human-readable display label (e.g., "SMS Auto Reply", "WhatsApp Auto Reply").
 * @property eventDescription Summary of triggering event condition (e.g., "Missed call or incoming SMS").
 * @property replyMethodDescription Explanation of the dispatch channel mechanism.
 * @property emoji Expressive visual emoji icon for the card header.
 * @property color Primary theme accent color associated with this channel.
 * @property defaultEvent Default event trigger identifier used during rule creation.
 * @property icon Optional Material 3 vector icon.
 */
data class AutoReplyTypeItem(
    val channel: ChannelType,
    val title: String,
    val eventDescription: String,
    val replyMethodDescription: String,
    val emoji: String = "",
    val color: Color,
    val defaultEvent: String,
    val icon: ImageVector? = null
)

/**
 * What it is:
 * Presentation model representing an active recipient chip selected during task composition.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [SelectedRecipient].
 *
 * Linked with:
 * - Architecture: [UiModels], [SelectedRecipient]
 * @property name Contact display name (e.g. "Jane Doe") or empty if only raw phone is known.
 * @property phone Cellular phone number, group identifier tag ("group:Family"), or status target ("My Status").
 */
data class SelectedRecipient(
    val name: String,
    val phone: String
)

/**
 * What it is:
 * UI presentation model representing a task type option in the creation dialog grid.
 *
 * What it does:
 * Maps user selection to specific channel dispatch configurations and specialized task modes.
 *
 * Linked with:
 * - Architecture: [UiModels], [TaskTypeItem]
 * @property title Display name of the task option (e.g. "SMS Message", "WhatsApp Voice Call").
 * @property description Subtitle outlining workflow capabilities.
 * @property emoji Visual iconography representing the task action.
 * @property color Dominant background and border highlight color.
 * @property channel Underlying [ChannelType] if mapped to a single-shot task, or null for multi-step sequences.
 * @property isSequence True if option launches the multi-step contact sequence composer.
 * @property isStatus True if option launches the WhatsApp status update publisher.
 * @property isDailyCampaign True if option routes to the unique contact daily outreach engine.
 * @property isWaCall True if option configures a live WhatsApp VoIP call task.
 * @property icon Optional Material 3 vector icon.
 */
data class TaskTypeItem(
    val title: String,
    val description: String,
    val emoji: String = "",
    val color: Color,
    val channel: ChannelType?,
    val isStatus: Boolean = false,
    val isDailyCampaign: Boolean = false,
    val isWaCall: Boolean = false,
    val isGroup: Boolean = false,
    val icon: ImageVector? = null
)
