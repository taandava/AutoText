

/**
 * Package: com.jmd.autotext.ui.model
 * Module: UI View State Models Layer
 *
 * Defines lightweight presentation models, recipient chips, task type picker items,
 * and auto-reply selection items for Compose screens.
 *
 * Linked With:
 * - Screens: `TaskTypeSelectionDialog`, `ComposeTaskScreen`,
 *   `AutoReplyScreen`
 */
package com.jmd.autotext.ui.model

import androidx.compose.ui.graphics.*
import com.jmd.autotext.data.model.*

/**
 * UI model representing an Auto-Reply channel category item in selection sheets and cards.
 *
 * Encapsulates the visual styling, iconography, and event description for configured reply triggers.
 *
 * @property channel Targeted communication channel ([ChannelType.SMS], [ChannelType.WHATSAPP], or [ChannelType.WHATSAPP_BUSINESS]).
 * @property title Human-readable display label (e.g., "SMS Auto Reply", "WhatsApp Auto Reply").
 * @property eventDescription Summary of triggering event condition (e.g., "Missed call or incoming SMS").
 * @property replyMethodDescription Explanation of the dispatch channel mechanism.
 * @property emoji Expressive visual emoji icon for the card header.
 * @property color Primary theme accent color associated with this channel.
 * @property defaultEvent Default event trigger identifier used during rule creation.
 */
data class AutoReplyTypeItem(
    val channel: ChannelType,
    val title: String,
    val eventDescription: String,
    val replyMethodDescription: String,
    val emoji: String,
    val color: Color,
    val defaultEvent: String,
)

/**
 * Presentation model representing an active recipient chip selected during task composition.
 *
 * @property name Contact display name (e.g. "Jane Doe") or empty if only raw phone is known.
 * @property phone Cellular phone number, group identifier tag ("group:Family"), or status target ("My Status").
 */
data class SelectedRecipient(
    val name: String,
    val phone: String
)

/**
 * UI presentation model representing a task type option in the creation dialog grid.
 *
 * Maps user selection to specific channel dispatch configurations and specialized automation modes.
 *
 * @property title Display name of the automation option (e.g. "SMS Message", "WhatsApp Voice Call").
 * @property description Subtitle outlining workflow capabilities.
 * @property emoji Visual iconography representing the automation action.
 * @property color Dominant background and border highlight color.
 * @property channel Underlying [ChannelType] if mapped to a single-shot task, or null for multi-step sequences.
 * @property isSequence True if option launches the multi-step contact sequence composer.
 * @property isStatus True if option launches the WhatsApp status update publisher.
 * @property isDailyCampaign True if option routes to the automated unique contact daily outreach engine.
 * @property isWaCall True if option configures a live WhatsApp VoIP call task.
 */
data class TaskTypeItem(
    val title: String,
    val description: String,
    val emoji: String,
    val color: Color,
    val channel: ChannelType?,
    val isSequence: Boolean = false,
    val isStatus: Boolean = false,
    val isDailyCampaign: Boolean = false,
    val isWaCall: Boolean = false
)


