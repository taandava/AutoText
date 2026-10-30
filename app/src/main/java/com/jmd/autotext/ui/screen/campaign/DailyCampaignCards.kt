package com.jmd.autotext.ui.screen.campaign

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.core.*
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.prefs.*
import com.jmd.autotext.data.repo.*
import com.jmd.autotext.engine.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun activeCampaignOverviewCard(
    campaign: DailyCampaignConfig,
    sentToday: Int,
    stats: DynamicPoolStats,
    onToggleActive: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onConfigure: () -> Unit,
    onViewContacts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWa = campaign.channel.contains("WHATSAPP", ignoreCase = true)
    val isBiz = campaign.channel.contains("BUSINESS", ignoreCase = true)
    val themeColor = when {
        isBiz -> MaterialTheme.colorScheme.primary
        isWa -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val channelEmoji = when {
        isBiz -> "💼"
        isWa -> "💬"
        else -> "📩"
    }

    appCardElevated(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeMedium,
        accentGlow = themeColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(themeColor.copy(alpha = 0.16f))
                        .border(1.dp, themeColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(channelEmoji, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            campaign.title.ifBlank { campaign.channel },
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        appBadge(
                            text = when {
                                isBiz -> "WA Business"
                                isWa -> "WhatsApp"
                                else -> "SMS"
                            },
                            emoji = channelEmoji,
                            color = themeColor,
                            showDot = false
                        )
                        if (!isWa) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val simLabel = if (campaign.simSlot == 0) "Default SIM" else "SIM ${campaign.simSlot}"
                            appBadge(
                                text = simLabel,
                                emoji = "📱",
                                color = MaterialTheme.colorScheme.primary,
                                showDot = false
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val simSuffix = if (!isWa) " • ${if (campaign.simSlot == 0) "Default SIM" else "SIM ${campaign.simSlot}"}" else ""
                    Text(
                        "Daily at %02d:%02d • %ds interval %s".format(
                            campaign.scheduledHour,
                            campaign.scheduledMinute,
                            campaign.intervalSeconds,
                            simSuffix
                        ),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = campaign.isActive,
                onToggleActive,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = themeColor,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress & Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            appStatCard(
                modifier = Modifier.weight(1f),
                title = "Sent Today",
                value = "$sentToday / ${campaign.dailyLimit}",
                emoji = "📤",
                iconTint = themeColor
            )
            appStatCard(
                modifier = Modifier.weight(1f),
                title = "Unique Pending",
                value = "${stats.unsentCount}",
                emoji = "⏳",
                iconTint = MaterialTheme.colorScheme.primary
            )
            appStatCard(
                modifier = Modifier.weight(1f),
                title = "Cycle Sent",
                value = "${stats.sentCount}",
                emoji = "✅",
                iconTint = MaterialTheme.colorScheme.tertiary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            glassButton(
                text = "Run Batch Now",
                onClick = onRunNow,
                variant = GlassButtonVariant.PRIMARY,
                emoji = "🚀",
                modifier = Modifier.weight(1.3f)
            )
            glassButton(
                text = "Configure",
                onClick = onConfigure,
                variant = GlassButtonVariant.SECONDARY,
                emoji = "⚙️",
                modifier = Modifier.weight(1f)
            )
            glassButton(
                text = "Contacts",
                onClick = onViewContacts,
                variant = GlassButtonVariant.SECONDARY,
                emoji = "👥",
                modifier = Modifier.weight(1f)
            )
        }
    }
}


/**
 * Status and resumption card rendered when a recurring campaign is currently paused.
 *
 * @param campaign Target campaign configuration data class.
 * @param onResume Callback to reactivate and schedule the paused campaign.
 * @param onConfigure Callback to navigate to campaign configuration settings.
 * @param onDelete Callback triggered when user chooses to delete this campaign.
 * @param modifier Optional layout modifier.
 */
@Composable
fun pausedCampaignOverviewCard(
    campaign: DailyCampaignConfig,
    onResume: () -> Unit,
    onConfigure: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWa = campaign.channel.contains("WHATSAPP", ignoreCase = true)
    val isBiz = campaign.channel.contains("BUSINESS", ignoreCase = true)
    val icon = when {
        isBiz -> Icons.Default.Business
        isWa -> Icons.AutoMirrored.Filled.Chat
        else -> Icons.Default.Sms
    }

    appCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeMedium
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Icon + Title + Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        campaign.title.ifBlank { campaign.channel },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isWa) {
                        val pausedSimLabel = if (campaign.simSlot == 0) "Default SIM" else "SIM ${campaign.simSlot}"
                        appBadge(
                            text = pausedSimLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            showDot = false
                        )
                    }
                    appBadge(
                        text = "PAUSED",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        showDot = false
                    )
                }
            }

            // Info row: Limits & Schedule
            val pausedSimSuffix = if (!isWa) " • ${if (campaign.simSlot == 0) "Default SIM" else "SIM ${campaign.simSlot}"}" else ""
            Text(
                "Limit: ${campaign.dailyLimit}/day • %02d:%02d%s".format(
                    campaign.scheduledHour,
                    campaign.scheduledMinute,
                    pausedSimSuffix
                ),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Actions row: Resume, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                glassButton(
                    text = "Resume",
                    onClick = onResume,
                    variant = GlassButtonVariant.PRIMARY,
                    emoji = "▶️",
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
                glassButton(
                    text = "Edit",
                    onClick = onConfigure,
                    variant = GlassButtonVariant.SECONDARY,
                    emoji = "✏️",
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
                if (campaign.id > 2L) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("🗑️", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}


/**
 * Empty state card displayed when no recurring campaign has been configured, offering channel creation presets.
 *
 * @param totalPoolCount Current number of contacts discovered across dynamic device contact pools.
 * @param onSelectChannel Callback receiving channel selection string to create a campaign.
 * @param onSyncPool Callback to trigger phonebook contact scan and synchronization.
 * @param isScanning True if background contact ingestion is currently active.
 * @param modifier Optional layout modifier.
 */
@Composable
fun dailyCampaignEmptyStateCard(
    totalPoolCount: Int,
    onSelectChannel: (String) -> Unit,
    onSyncPool: () -> Unit,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        appCardElevated(
            modifier = Modifier.fillMaxWidth(),
            shape = AppDesignTokens.ShapeLarge,
            accentGlow = MaterialTheme.colorScheme.primary
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ScheduleSend,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "No Active Daily Campaigns",
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Set up an automated outreach cycle to unique contacts without repetitive manual messaging. Choose your outreach channel below to get started:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        Text(
            "SELECT OUTREACH CHANNEL:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )

        // 1. SMS Channel Card
        channelSelectionCard(
            title = "SMS Daily Campaign",
            badge = "Carrier Direct",
            description = "Direct cellular SMS outreach. Dual SIM selection (SIM 1/2) with India carrier daily quota safety safeguard.",
            icon = Icons.Default.Sms,
            accentColor = MaterialTheme.colorScheme.primary,
            buttonText = "Set Up SMS Campaign",
            onClick = { onSelectChannel("SMS") }
        )

        // 2. WhatsApp Channel Card
        channelSelectionCard(
            title = "WhatsApp Daily Campaign",
            badge = "Personal 1-on-1",
            description = "Personal automated WhatsApp messages via accessibility service with auto-recovery and customizable delay.",
            icon = Icons.AutoMirrored.Filled.Chat,
            accentColor = MaterialTheme.colorScheme.tertiary,
            buttonText = "Set Up WhatsApp Campaign",
            onClick = { onSelectChannel("WHATSAPP") }
        )

        // 3. WhatsApp Business Channel Card
        channelSelectionCard(
            title = "WhatsApp Business Campaign",
            badge = "Client Outreach",
            description = "Automate client follow-ups via WhatsApp Business app with paced delivery to protect business account standing.",
            icon = Icons.Default.Business,
            accentColor = MaterialTheme.colorScheme.primary,
            buttonText = "Set Up WA Business Campaign",
            onClick = { onSelectChannel("WHATSAPP_BUSINESS") }
        )

        // Ingestion Pool Action Card
        appCard(
            modifier = Modifier.fillMaxWidth(),
            shape = AppDesignTokens.ShapeMedium
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Contacts Pool: $totalPoolCount Contacts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Ingested from Phone Contacts, SMS, Calls, WhatsApp & WA Business",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                glassButton(
                    text = if (isScanning) "Syncing..." else "Sync Pool",
                    onClick = onSyncPool,
                    variant = GlassButtonVariant.PRIMARY,
                    icon = Icons.Default.Sync,
                    isLoading = isScanning,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }
        }
    }
}


/**
 * Channel selection tile rendering branding icon, badge, description, and direct activation action.
 *
 * @param title Name of the channel (e.g. SMS, WhatsApp, WhatsApp Business).
 * @param badge Status or capability tag displayed on top right.
 * @param description Detailed text explaining channel constraints and behavior.
 * @param icon Vector icon representing the medium.
 * @param accentColor Primary brand color for this channel.
 * @param buttonText Label on the call-to-action button.
 * @param onClick Invoked when button is tapped.
 * @param modifier Optional layout modifier.
 */
@Composable
fun channelSelectionCard(
    title: String,
    badge: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    appCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeMedium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            appBadge(text = badge, color = accentColor, showDot = true)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            description,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        glassButton(
            text = buttonText,
            onClick = onClick,
            variant = GlassButtonVariant.PRIMARY,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Diagnostic card showing WhatsApp Business package installation and accessibility engine readiness.
 *
 * @param isInstalled True if com.whatsapp.w4b package is discovered on the device.
 * @param isA11yActive True if accessibility automation binder is responsive.
 * @param onAutoHeal Callback to trigger accessibility reactivation.
 * @param modifier Optional layout modifier.
 */
@Composable
fun whatsAppBusinessReadinessCard(
    isInstalled: Boolean,
    isA11yActive: Boolean,
    onAutoHeal: () -> Unit,
    modifier: Modifier = Modifier
) {
    appCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeMedium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Business,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "WhatsApp Business Readiness",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            appBadge(
                text = if (isInstalled) "INSTALLED" else "NOT INSTALLED",
                color = if (isInstalled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                showDot = true
            )
        }
        if (!isInstalled) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "WhatsApp Business app (com.whatsapp.w4b) is not detected on this device. Install from Google Play Store so messages can be dispatched through your business account.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        whatsAppA11yHealthBar(isA11yActive = isA11yActive, onAutoHeal = onAutoHeal)
    }
}


/**
 * Modal bottom sheet for configuring and persisting a new recurrent daily campaign.
 *
 * @param initialChannel Default channel preset (SMS, WHATSAPP, or WHATSAPP_BUSINESS).
 * @param onDismiss Callback to dismiss modal sheet.
 * @param onSave Callback receiving the newly composed campaign configuration.
 */
// =========================================================================================
// --- DAILY CAMPAIGN COMPONENTS ---
// =========================================================================================


/**
 * Campaign hero banner card presenting live execution status, contact pool counters, accessibility health, and batch trigger.
 *
 * @param isWhatsApp True if this campaign dispatches via WhatsApp or WA Business.
 * @param config Target campaign configuration data class.
 * @param stats Real-time contact pool counts.
 * @param isA11yActive True if accessibility automation engine is running.
 * @param isScanning True if phonebook contact scan is underway.
 * @param campaignThemeColor Color accent tailored to current campaign medium.
 * @param lastScanSummary Metrics from the most recent contact scan.
 * @param onToggleActive Callback triggered when campaign master switch is toggled.
 * @param onAutoHeal Callback triggered to heal dead accessibility binders.
 * @param onScan Callback to initiate contact discovery scan.
 * @param onSendBatch Callback to dispatch today's batch immediately.
 * @param modifier Optional layout modifier.
 */
@Composable
fun dailyCampaignBannerCard(
    isWhatsApp: Boolean,
    config: DailyCampaignConfig,
    stats: DynamicPoolStats,
    isA11yActive: Boolean,
    isScanning: Boolean,
    campaignThemeColor: Color,
    lastScanSummary: IngestionSummary?,
    onToggleActive: (Boolean) -> Unit,
    onAutoHeal: () -> Unit,
    onScan: () -> Unit,
    onSendBatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    appCardElevated(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeMedium,
        accentGlow = campaignThemeColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isWhatsApp) Icons.Default.PhoneAndroid else Icons.AutoMirrored.Filled.ScheduleSend,
                    contentDescription = null,
                    tint = campaignThemeColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        if (isWhatsApp) "Daily WhatsApp Campaign" else "Daily Contact SMS",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (config.isActive) "Active • Sends ${config.dailyLimit}/day at %02d:%02d".format(config.scheduledHour, config.scheduledMinute)
                        else "${if (isWhatsApp) "WhatsApp" else "SMS"} Automation Paused",
                        fontSize = 12.sp,
                        color = if (config.isActive) campaignThemeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = config.isActive,
                    onCheckedChange = onToggleActive,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = campaignThemeColor,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            appStatCard(
                modifier = Modifier.weight(1f),
                title = "Total Pool",
                value = "${stats.totalCount}",
                icon = Icons.Default.People,
                iconTint = MaterialTheme.colorScheme.primary
            )
            appStatCard(
                modifier = Modifier.weight(1f),
                title = "Pending",
                value = "${stats.unsentCount}",
                icon = Icons.Default.HourglassTop,
                iconTint = MaterialTheme.colorScheme.primary
            )
            appStatCard(
                modifier = Modifier.weight(1f),
                title = "Contacted",
                value = "${stats.sentCount}",
                icon = Icons.Default.CheckCircle,
                iconTint = MaterialTheme.colorScheme.tertiary
            )
        }

        // WhatsApp Accessibility Health & Readiness
        if (isWhatsApp) {
            Spacer(modifier = Modifier.height(6.dp))
            whatsAppA11yHealthBar(
                isA11yActive = isA11yActive,
                onAutoHeal = onAutoHeal
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons: Scan Contacts, Edit Campaign & Send Now
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            glassButton(
                text = if (isScanning) "Scanning..." else "Scan",
                onClick = onScan,
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Default.Sync,
                isLoading = isScanning,
                modifier = Modifier.weight(1f)
            )

            glassButton(
                text = "Send Batch",
                onClick = onSendBatch,
                variant = GlassButtonVariant.PRIMARY,
                icon = Icons.AutoMirrored.Filled.Send,
                modifier = Modifier.weight(1.3f)
            )
        }

        // Last scan summary indicator
        lastScanSummary?.let { summary ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Last scan: ${summary.contactsScanned} contacts, ${summary.smsScanned} SMS, ${summary.callLogsScanned} calls • +${summary.newAdded} new unique numbers added.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}



/**
 * Configuration card managing message templates, dispatch schedule timing, daily send caps, and SIM routing.
 *
 * @param config Target campaign configuration data class.
 * @param isWhatsApp True if campaign is routed via WhatsApp.
 * @param campaignThemeColor Color accent tailored to current campaign medium.
 * @param onConfigChange Callback invoked as fields are modified in real-time.
 * @param onSave Callback triggered when configuration updates are persisted.
 * @param modifier Optional layout modifier.
 */
@Composable
fun dailyCampaignConfigCard(
    config: DailyCampaignConfig,
    isWhatsApp: Boolean,
    campaignThemeColor: Color,
    onConfigChange: (DailyCampaignConfig) -> Unit,
    onSave: (DailyCampaignConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isTimePickerExpanded by remember { mutableStateOf(false) }
    var dailyLimitText by remember(config.dailyLimit) { mutableStateOf(config.dailyLimit.toString()) }
    var intervalSecondsText by remember(config.intervalSeconds) { mutableStateOf(config.intervalSeconds.toString()) }

    appCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = campaignThemeColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${if (isWhatsApp) "WhatsApp" else "SMS"} Campaign Settings",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            appBadge(text = "Settings", color = campaignThemeColor, showDot = false)
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Daily Batch Limit (PROPER ENTRY FIELD + STEPPERS + CHIPS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            glassInputField(
                value = dailyLimitText,
                onValueChange = { newStr ->
                    val digits = newStr.filter { it.isDigit() }.take(4)
                    dailyLimitText = digits
                    val num = digits.toIntOrNull()
                    if (num != null && num > 0) {
                        onConfigChange(config.copy(dailyLimit = num.coerceIn(1, 1000)))
                    }
                },
                label = "Daily Batch Limit (Messages / Day)",
                placeholder = "e.g. 50",
                leadingIcon = Icons.Default.Pin,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = campaignThemeColor,
                modifier = Modifier.weight(1f)
            )

            // Stepper controls [-] [+]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(46.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(AppDesignTokens.ShapeSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, campaignThemeColor.copy(alpha = 0.5f), AppDesignTokens.ShapeSmall)
                        .clickable {
                            val next = (config.dailyLimit - 5).coerceAtLeast(1)
                            dailyLimitText = next.toString()
                            onConfigChange(config.copy(dailyLimit = next))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "-", color = campaignThemeColor, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(AppDesignTokens.ShapeSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, campaignThemeColor.copy(alpha = 0.5f), AppDesignTokens.ShapeSmall)
                        .clickable {
                            val next = (config.dailyLimit + 5).coerceAtMost(1000)
                            dailyLimitText = next.toString()
                            onConfigChange(config.copy(dailyLimit = next))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+", color = campaignThemeColor, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Scheduled Time & Channel Specific Information
        val isSms = config.channel.equals("SMS", ignoreCase = true)
        val isWaBiz = config.channel.contains("BUSINESS", ignoreCase = true)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            glassButton(
                text = "Time: %02d:%02d".format(config.scheduledHour, config.scheduledMinute),
                onClick = { isTimePickerExpanded = !isTimePickerExpanded },
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Default.AccessTime,
                modifier = Modifier.weight(1f)
            )

            if (!isSms) {
                Surface(
                    shape = AppDesignTokens.ShapePill,
                    color = campaignThemeColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, campaignThemeColor.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (isWaBiz) Icons.Default.Business else Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = campaignThemeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isWaBiz) "WA Business Channel" else "WhatsApp Channel",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = campaignThemeColor
                        )
                    }
                }
            }
        }

        if (isSms) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Default SIM" to 0, "SIM 1" to 1, "SIM 2" to 2).forEach { (label, slot) ->
                    val isSelected = config.simSlot == slot
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onConfigChange(config.copy(simSlot = slot)) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SimCard,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Native Interactive Clock Picker Frame
        if (isTimePickerExpanded) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = AppDesignTokens.ShapeMedium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppDesignTokens.ShapeMedium)
                    .clickable {
                        showAppTimePicker(context, config.scheduledHour, config.scheduledMinute) { h, m ->
                            val updated = config.copy(scheduledHour = h, scheduledMinute = m)
                            onConfigChange(updated)
                            onSave(updated)
                        }
                    },
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = campaignThemeColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scheduled Execution Time", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }
                        appBadge(
                            text = "%02d:%02d".format(config.scheduledHour, config.scheduledMinute),
                            color = campaignThemeColor,
                            showDot = false
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap to open the clock picker and set exact delivery time.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    glassButton(
                        text = "Set Exact Time (Clock)",
                        onClick = {
                            showAppTimePicker(context, config.scheduledHour, config.scheduledMinute) { h, m ->
                                val updated = config.copy(scheduledHour = h, scheduledMinute = m)
                                onConfigChange(updated)
                                onSave(updated)
                            }
                        },
                        icon = Icons.Default.AccessTime,
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Send Delay Interval (PROPER ENTRY FIELD + STEPPERS + CHIPS)
        val minSec = if (isWhatsApp) 5 else 2
        val maxSec = 120

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            glassInputField(
                value = intervalSecondsText,
                onValueChange = { newStr ->
                    val digits = newStr.filter { it.isDigit() }.take(3)
                    intervalSecondsText = digits
                    val num = digits.toIntOrNull()
                    if (num != null && num > 0) {
                        onConfigChange(config.copy(intervalSeconds = num.coerceIn(minSec, maxSec)))
                    }
                },
                label = "Send Delay Interval (Seconds between messages)",
                placeholder = "e.g. 8",
                leadingIcon = Icons.Default.Timer,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )

            // Stepper controls [-] [+]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(46.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(AppDesignTokens.ShapeSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), AppDesignTokens.ShapeSmall)
                        .clickable {
                            val next = (config.intervalSeconds - 2).coerceAtLeast(minSec)
                            intervalSecondsText = next.toString()
                            onConfigChange(config.copy(intervalSeconds = next))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "-", color = MaterialTheme.colorScheme.tertiary, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(AppDesignTokens.ShapeSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), AppDesignTokens.ShapeSmall)
                        .clickable {
                            val next = (config.intervalSeconds + 2).coerceAtMost(maxSec)
                            intervalSecondsText = next.toString()
                            onConfigChange(config.copy(intervalSeconds = next))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+", color = MaterialTheme.colorScheme.tertiary, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Auto-scan Toggle
        glassToggleRow(
            title = "Auto-scan before batch",
            subtitle = "Discovers newly received contacts, SMS and calls automatically",
            checked = config.autoScanEnabled,
            onCheckedChange = { onConfigChange(config.copy(autoScanEnabled = it)) },
            icon = Icons.Default.Sync,
            iconTint = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Unified Glass Input Field for Message Template
        glassInputField(
            value = config.messageTemplate,
            onValueChange = { onConfigChange(config.copy(messageTemplate = it)) },
            label = "Campaign Message Template",
            placeholder = "Type your personalized batch message...",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            accentColor = campaignThemeColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Dynamic Tag Chips
        Text("Dynamic Variables:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        val campaignVariables = listOf("{name}", "{first_name}", "{phone}", "{date}", "{short_date}", "{time}", "{day}", "{short_day}", "{day_num}")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            campaignVariables.chunked(3).forEach { rowVars ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowVars.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(AppDesignTokens.ShapePill)
                                .background(campaignThemeColor.copy(alpha = 0.12f))
                                .border(1.dp, campaignThemeColor.copy(alpha = 0.35f), AppDesignTokens.ShapePill)
                                .clickable {
                                    val current = config.messageTemplate
                                    val updated = current + (if (current.isNotEmpty() && !current.endsWith(" ")) " " else "") + tag
                                    onConfigChange(config.copy(messageTemplate = updated))
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tag, fontSize = 11.sp, color = campaignThemeColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (rowVars.size < 3) {
                        repeat(3 - rowVars.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        glassButton(
            text = "Save Campaign Changes",
            onClick = { onSave(config) },
            variant = GlassButtonVariant.PRIMARY,
            icon = Icons.Default.Save,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


/**
 * TRAI Telecom Regulatory Authority quota progress card displaying India's 100 SMS/day limits.
 *
 * @param smsQuota Quota computation metrics containing total dispatched count and remaining allowance.
 * @param modifier Optional layout modifier.
 */
@Composable
fun dailySmsQuotaCard(
    smsQuota: SmsQuotaManager.QuotaStatus,
    modifier: Modifier = Modifier
) {
    appCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeMedium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "TRAI India SMS Quota",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.5.sp
                )
            }

            appBadge(
                text = if (smsQuota.isLimitReached) "100/100 LIMIT REACHED" else "${smsQuota.totalSentToday} / 100 Sent",
                color = if (smsQuota.isLimitReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (smsQuota.totalSentToday / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(AppDesignTokens.ShapePill),
            color = if (smsQuota.isLimitReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            smsQuota.details,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 13.5.sp
        )
    }
}

/**
 * Compact horizontal banner displaying accessibility automation readiness and one-click heal button.
 *
 * @param isA11yActive True if accessibility automation engine is active.
 * @param onAutoHeal Callback triggered when user taps to restart accessibility service.
 * @param modifier Optional layout modifier.
 */
@Composable
fun whatsAppA11yHealthBar(
    isA11yActive: Boolean,
    onAutoHeal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = AppDesignTokens.ShapeSmall,
        color = if (isA11yActive) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        border = BorderStroke(
            1.dp,
            if (isA11yActive) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isA11yActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isA11yActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isA11yActive) "Automation Service Ready" else "Service dropped / inactive",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!isA11yActive) {
                glassButton(
                    text = "Auto-Heal",
                    onClick = onAutoHeal,
                    variant = GlassButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }
        }
    }
}


/**
 * Header card for dynamic contact pool management with real-time search filtering, unsent toggles, and pool clearing.
 *
 * @param stats Real-time contact pool counts.
 * @param searchQuery Current search string filter.
 * @param onSearchQueryChange Callback when search string is updated.
 * @param filterUnsentOnly Filter toggle to isolate uncontacted records.
 * @param onFilterUnsentOnlyChange Callback when unsent-only switch is flipped.
 * @param onClearPool Callback to purge all contacts in pool.
 * @param modifier Optional layout modifier.
 */
