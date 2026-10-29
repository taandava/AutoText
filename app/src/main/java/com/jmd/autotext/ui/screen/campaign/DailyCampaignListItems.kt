package com.jmd.autotext.ui.screen.campaign

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.model.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun dailyContactPoolHeaderCard(
    stats: DynamicPoolStats,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterUnsentOnly: Boolean,
    onFilterUnsentOnlyChange: (Boolean) -> Unit,
    onClearPool: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isConfirmClearVisible by remember { mutableStateOf(false) }

    appCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Dynamic Contact Pool",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${stats.totalCount} Contacts (${stats.unsentCount} Unsent)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            glassButton(
                text = if (isConfirmClearVisible) "Cancel" else "Clear Pool",
                onClick = { isConfirmClearVisible = !isConfirmClearVisible },
                variant = GlassButtonVariant.DANGER,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                shape = AppDesignTokens.ShapePill
            )
        }

        if (isConfirmClearVisible) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = AppDesignTokens.ShapeMedium,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "Clear all ${stats.totalCount} contacts from pool? You can re-scan anytime.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        glassButton(
                            text = "Cancel",
                            onClick = { isConfirmClearVisible = false },
                            variant = GlassButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                        glassButton(
                            text = "Confirm Clear",
                            onClick = {
                                onClearPool()
                                isConfirmClearVisible = false
                            },
                            variant = GlassButtonVariant.DANGER,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Field
        glassSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Search by name or number...",
            leadingIcon = Icons.Default.Search,
            onClear = { onSearchQueryChange("") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            glassChip(
                selected = !filterUnsentOnly,
                onClick = { onFilterUnsentOnlyChange(false) },
                label = "All (${stats.totalCount})",
                activeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            glassChip(
                selected = filterUnsentOnly,
                onClick = { onFilterUnsentOnlyChange(true) },
                label = "Unsent (${stats.unsentCount})",
                activeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}


/**
 * Header card for dispatched contacts audit ledger with cycle resetting and query search.
 *
 * @param isWhatsApp True if displaying WhatsApp dispatch records; false for SMS.
 * @param sentCount Number of contacts dispatched so far in the active cycle.
 * @param searchQuery Current search string filter.
 * @param onSearchQueryChange Callback when search filter text changes.
 * @param campaignThemeColor Medium accent color.
 * @param onResetCycle Callback to reset all contacted flags back to uncontacted for a new campaign cycle.
 * @param modifier Optional layout modifier.
 */
@Composable
fun dailySentLedgerHeaderCard(
    isWhatsApp: Boolean,
    sentCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    campaignThemeColor: Color,
    onResetCycle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isConfirmResetCycleVisible by remember { mutableStateOf(false) }

    appCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge,
        borderColor = campaignThemeColor.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (isWhatsApp) "WhatsApp" else "SMS"} Sent Ledger",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    appBadge(
                        text = "$sentCount Contacted",
                        color = campaignThemeColor,
                        showDot = false
                    )
                }
                Text(
                    "Strict deduplication ledger. Each unique recipient is messaged once.",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 13.5.sp
                )
            }

            if (sentCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                glassButton(
                    text = if (isConfirmResetCycleVisible) "Cancel" else "Reset Cycle",
                    onClick = { isConfirmResetCycleVisible = !isConfirmResetCycleVisible },
                    variant = GlassButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }
        }

        if (isConfirmResetCycleVisible) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = AppDesignTokens.ShapeMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "Reset campaign cycle for ${if (isWhatsApp) "WhatsApp" else "SMS"}? This will re-queue all $sentCount contacted numbers for new dispatches.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        glassButton(
                            text = "Cancel",
                            onClick = { isConfirmResetCycleVisible = false },
                            variant = GlassButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                        glassButton(
                            text = "Confirm Reset",
                            onClick = {
                                onResetCycle()
                                isConfirmResetCycleVisible = false
                            },
                            variant = GlassButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        glassSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Search sent ledger by name or number...",
            leadingIcon = Icons.Default.Search,
            onClear = { onSearchQueryChange("") }
        )
    }
}


/**
 * Dynamic contact pool entry item card with source attribution badge, phone number, and enable toggle.
 *
 * @param contact Data model representing the discovered phone number and metadata.
 * @param isWhatsApp True if evaluating WhatsApp cycle state.
 * @param onToggle Callback triggered when active inclusion switch is toggled.
 * @param onDelete Callback triggered when item is deleted from dynamic pool.
 */
@Composable
fun contactItemCard(
    contact: DynamicContactItem,
    isWhatsApp: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    appCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeMedium,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sourceIcon = when (contact.source) {
                "WA_BUSINESS" -> Icons.Default.Business
                "WA_CHAT" -> Icons.AutoMirrored.Filled.Chat
                "WA_GROUP" -> Icons.Default.Groups
                "CONTACT" -> Icons.Default.Person
                "SMS" -> Icons.Default.ChatBubble
                else -> Icons.Default.Call
            }
            val sourceColor = when (contact.source) {
                "WA_BUSINESS" -> MaterialTheme.colorScheme.primary
                "WA_CHAT", "WA_GROUP" -> MaterialTheme.colorScheme.tertiary
                "CONTACT" -> MaterialTheme.colorScheme.primary
                "SMS" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }

            contactAvatar(
                name = contact.name.ifBlank { contact.phone },
                photoUri = contact.photoUri,
                size = 36.dp,
                badgeIcon = sourceIcon,
                badgeColor = sourceColor
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name.ifEmpty { "Unsaved Contact" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    appBadge(
                        text = when (contact.source) {
                            "WA_BUSINESS" -> "WA Biz"
                            "WA_CHAT" -> "WA Chat"
                            "WA_GROUP" -> "WA Group"
                            "CONTACT" -> "Contact"
                            "SMS" -> "SMS"
                            else -> contact.source
                        },
                        color = sourceColor,
                        showDot = false
                    )
                }
                Text(
                    text = contact.phone,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                val isSent = if (isWhatsApp) contact.waLastSentAt > 0 else contact.lastSentAt > 0
                if (isSent) {
                    Text(
                        text = "Already contacted in cycle",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Switch(
                checked = contact.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.tertiary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Historical audit card rendering execution timestamp, status badge, and requeue action for dispatched contacts.
 *
 * @param contact Data model representing dispatched contact item.
 * @param isWhatsApp True if evaluating WhatsApp delivery history; false for SMS.
 * @param onRequeue Callback triggered to return this contact back to the pending queue.
 */
@Composable
fun ledgerItemCard(
    contact: DynamicContactItem,
    isWhatsApp: Boolean,
    onRequeue: () -> Unit
) {
    val sentAt = if (isWhatsApp) contact.waLastSentAt else contact.lastSentAt
    val sendCount = if (isWhatsApp) contact.waSendCount else contact.sendCount
    val status = if (isWhatsApp) contact.waStatus else contact.smsStatus
    val currentLocale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val dateStr = if (sentAt > 0) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", currentLocale).format(Date(sentAt))
    } else "Dispatched"

    val statusColor = when (status.uppercase()) {
        "FAILED" -> MaterialTheme.colorScheme.error
        "QUEUED" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }

    appCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeMedium,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            contactAvatar(
                name = contact.name.ifBlank { contact.phone },
                photoUri = contact.photoUri,
                size = 36.dp,
                badgeIcon = if (isWhatsApp) Icons.Default.PhoneAndroid else Icons.Default.Sms,
                badgeColor = if (isWhatsApp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = contact.name.ifEmpty { "Unsaved Contact" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    appBadge(
                        text = status.ifEmpty { "SENT" },
                        color = statusColor,
                        showDot = true
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = contact.phone,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (sendCount > 1) {
                        Text(
                            text = "• ${sendCount}x sent",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "Dispatched: $dateStr",
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            glassButton(
                text = "Re-queue",
                onClick = onRequeue,
                variant = GlassButtonVariant.SECONDARY,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                shape = AppDesignTokens.ShapePill
            )
        }
    }
}

// ============================================================================
// MAIN DASHBOARD HOME SCREEN
// ============================================================================


