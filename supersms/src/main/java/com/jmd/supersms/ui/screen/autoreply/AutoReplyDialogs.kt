/**
 * Package: com.jmd.supersms.ui.screen.autoreply
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [AutoReplyDialogs].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for AutoReplyDialogs.
 *
 * Linked With:
 * - Repositories: [com.jmd.supersms.data.repo.*]
 * - Models & Entities: [com.jmd.supersms.data.model.*], [com.jmd.supersms.ui.model.AutoReplyTypeItem]
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.clip], [androidx.compose.ui.graphics.Color]
 * - Core Security & Utilities: [com.jmd.supersms.core.*]
 * - Android Framework & Jetpack: [androidx.compose.foundation.lazy.LazyColumn], [androidx.compose.foundation.lazy.items], [androidx.compose.foundation.shape.CircleShape], [androidx.compose.foundation.shape.RoundedCornerShape]
 */

package com.jmd.supersms.ui.screen.autoreply

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jmd.supersms.core.*
import com.jmd.supersms.data.model.*
import com.jmd.supersms.data.repo.*
import com.jmd.supersms.ui.components.*
import com.jmd.supersms.ui.model.AutoReplyTypeItem
import com.jmd.supersms.ui.theme.*

/**
 * What it is:
 * Bottom sheet modal enabling users to select the trigger medium (Call, SMS) and event type.
 *
 * What it does:
 * Renders interactive Material 3 UI for [AutoReplyTypeSelectionSheet] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [AutoReplyDialogs], [AutoReplyTypeSelectionSheet]
 * @param onDismiss Callback to dismiss bottom sheet.
 * @param onSelectType Callback receiving chosen channel and event type key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoReplyTypeSelectionSheet(
    onDismiss: () -> Unit,
    onSelectType: (ChannelType, String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(AppDesignTokens.ShapePill)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val responderTypes = remember(primaryColor) {
            listOf(
                AutoReplyTypeItem(
                    channel = ChannelType.CALL,
                    title = "Call Responder",
                    eventDescription = "2 Triggers: Missed Call or Call Ended",
                    replyMethodDescription = "Replies strictly via SMS through device SIM",
                    icon = Icons.Filled.Phone,
                    color = primaryColor,
                    defaultEvent = "MISSED_CALL"
                ),
                    AutoReplyTypeItem(
                        channel = ChannelType.SMS,
                        title = "SMS Reply",
                        eventDescription = "Trigger: Incoming SMS (* for all)",
                        replyMethodDescription = "Replies via SMS using device SIM",
                        icon = Icons.AutoMirrored.Filled.Message,
                        color = Color(0xFF2563EB),
                        defaultEvent = "MESSAGE"
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Select Responder",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                AppBadge(text = "${responderTypes.size} TYPES", color = MaterialTheme.colorScheme.primary, showDot = false)
            }
            Text(
                "Choose the communication channel to schedule. Each option opens its dedicated, tailored configuration form:",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            responderTypes.forEach { item ->
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppDesignTokens.ShapeMedium,
                    onClick = { onSelectType(item.channel, item.defaultEvent) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(item.color.copy(alpha = 0.16f))
                                .border(1.dp, item.color.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon ?: Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = item.color,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(item.eventDescription, fontSize = 11.5.sp, color = item.color, fontWeight = FontWeight.Medium)
                            Text(item.replyMethodDescription, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


/**
 * What it is:
 * Comprehensive modal dialog for creating or updating an scheduled reply rule with channel, triggers, recipient filters, and schedule.
 *
 * What it does:
 * Renders interactive Material 3 UI for [AutoReplyRuleDialog] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [AutoReplyDialogs], [AutoReplyRuleDialog]
 * @param initialRule Existing rule to edit, or null when creating a new rule.
 * @param onDismiss Callback to dismiss editor dialog.
 * @param onSave Callback receiving populated rule data class on confirmation.
 */
@Composable
fun AutoReplyRuleDialog(
    initialRule: AutoReplyRule?,
    onDismiss: () -> Unit,
    onSave: (AutoReplyRule) -> Unit
) {
    var ruleName by remember { mutableStateOf(initialRule?.name ?: "") }
    var selectedChannel by remember {
        mutableStateOf(
            initialRule?.channel ?: ChannelType.CALL
        )
    }
    var callEventType by remember {
        mutableStateOf(
            initialRule?.callEventType ?: if (initialRule?.channel == ChannelType.CALL) "MISSED_CALL" else "MESSAGE"
        )
    }
    var isExactMatch by remember { mutableStateOf(initialRule?.isExactMatch ?: false) }
    var dailyLimitMode by remember {
        mutableStateOf(
            when (initialRule?.maxDailyReplies) {
                1 -> "1"
                3 -> "3"
                5 -> "5"
                0 -> "UNLIMITED"
                null -> "1"
                else -> "CUSTOM"
            }
        )
    }
    var customDailyLimitText by remember {
        mutableStateOf(if (dailyLimitMode == "CUSTOM") (initialRule?.maxDailyReplies ?: 2).toString() else "2")
    }
    var replyText by remember {
        mutableStateOf(
            initialRule?.replyText ?: ""
        )
    }
    var triggerPattern by remember { mutableStateOf(initialRule?.triggerPattern ?: "") }
    var cooldownSeconds by remember { mutableIntStateOf(initialRule?.cooldownSeconds ?: 30) }

    val themeColor = when (selectedChannel) {
        ChannelType.CALL -> MaterialTheme.colorScheme.primary
        ChannelType.SMS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppSubScreenHeader(
                title = if (initialRule != null && initialRule.id > 0L) "Edit Rule" else "New Rule",
                subtitle = "Reply Config",
                onBack = onDismiss
            )
        },
        containerColor = AppDesignTokens.SurfaceBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Rule Name Input
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = AppDesignTokens.ShapeLarge
            ) {
                AppInputField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = "Rule Identifier",
                    placeholder = "e.g. Missed Call Responder, Office Hours",
                    leadingIcon = Icons.AutoMirrored.Filled.Label,
                    accentColor = themeColor
                )
            }

            // 2. Target Platform Selector
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = AppDesignTokens.ShapeLarge
            ) {
                Text(
                    "Target Platform",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Select which communication channel this auto-responder monitors:",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                val targetPlatforms = listOf(
                    ChannelType.CALL to ("Phone Calls" to Icons.Filled.Phone),
                    ChannelType.SMS to ("SMS" to Icons.AutoMirrored.Filled.Message)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    targetPlatforms.chunked(2).forEach { rowChannels ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowChannels.forEach { (ch, pair) ->
                                val (title, iconVector) = pair
                                val isSelected = selectedChannel == ch
                                val chipColor = MaterialTheme.colorScheme.primary
                                AppChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedChannel = ch
                                        if (ch == ChannelType.CALL) {
                                            if (callEventType == "MESSAGE") callEventType = "MISSED_CALL"
                                        } else {
                                            callEventType = "MESSAGE"
                                        }
                                    },
                                    label = title,
                                    activeColor = chipColor,
                                    icon = iconVector,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Dynamic Specialized Form Based on Selected Channel
            when (selectedChannel) {
                ChannelType.CALL -> {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppDesignTokens.ShapeLarge
                    ) {
                        Text(
                            "Call Trigger",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Select which call event triggers an quick response:",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("MISSED_CALL", "Missed Call", Icons.AutoMirrored.Filled.PhoneMissed),
                                Triple("CALL_ENDED", "Call Ended", Icons.Filled.CallEnd)
                            ).forEach { (eventKey, eventLabel, eventIcon) ->
                                AppChip(
                                    selected = callEventType == eventKey,
                                    onClick = { callEventType = eventKey },
                                    label = eventLabel,
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    icon = eventIcon,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = AppDesignTokens.ShapeMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sms,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Method: SMS", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "When a phone call is missed or ended, Super immediately sends a quick SMS reply to the caller's mobile number via your device SIM. Landlines and toll-free numbers are filtered out directly.",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                ChannelType.SMS -> {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppDesignTokens.ShapeLarge
                    ) {
                        Text(
                            "SMS Trigger",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Triggers directly on incoming SMS messages received on your device:",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        AppInputField(
                            value = triggerPattern,
                            onValueChange = { triggerPattern = it },
                            label = "Trigger Keyword",
                            placeholder = "e.g. *, hello, price, info",
                            leadingIcon = Icons.Default.Tag,
                            accentColor = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Exact Match", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text("Match exact word instead of containing substring", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isExactMatch,
                                onCheckedChange = { isExactMatch = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = AppDesignTokens.ShapeMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sms,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Method: SMS", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "Quick response is sent as an SMS message via your SIM card.",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {}
            }

            // 4. Daily Frequency & Cooldown Enterprise Card
            AutoReplyLimitsCard(
                dailyLimitMode = dailyLimitMode,
                onDailyLimitModeChanged = { dailyLimitMode = it },
                customDailyLimitText = customDailyLimitText,
                onCustomDailyLimitTextChanged = { customDailyLimitText = it.filter(Char::isDigit) },
                cooldownSeconds = cooldownSeconds,
                onCooldownSecondsChanged = { cooldownSeconds = it }
            )

            // 5. Custom Reply Message Enterprise Card (Tailored for active channel & event)
            AutoReplyMessageCard(
                channel = selectedChannel,
                callEventType = callEventType,
                replyText = replyText,
                onReplyTextChanged = { replyText = it }
            )

            // 6. Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = AppButtonVariant.SECONDARY,
                    icon = Icons.Filled.Close,
                    modifier = Modifier.weight(1f)
                )

                AppButton(
                    text = if (initialRule != null && initialRule.id > 0L) "Update Rule" else "Save Rule",
                    variant = AppButtonVariant.PRIMARY,
                    icon = Icons.Filled.Check,
                    onClick = {
                        val computedDailyLimit = when (dailyLimitMode) {
                            "1" -> 1
                            "3" -> 3
                            "5" -> 5
                            "UNLIMITED" -> 0
                            else -> customDailyLimitText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        }

                        val defaultName = when (selectedChannel) {
                            ChannelType.CALL -> if (callEventType == "CALL_ENDED") "Call Ended" else "Missed Call"
                            ChannelType.SMS -> "SMS Reply"
                            else -> "Auto Reply"
                        }

                        val finalRule = AutoReplyRule(
                            id = initialRule?.id ?: 0L,
                            name = ruleName.ifBlank { defaultName },
                            channel = selectedChannel,
                            replyText = replyText.trim(),
                            triggerPattern = if (selectedChannel == ChannelType.CALL || callEventType in listOf("MISSED_CALL", "CALL_ENDED")) "*" else triggerPattern.ifEmpty { "*" },
                            isExactMatch = isExactMatch,
                            cooldownSeconds = cooldownSeconds,
                            callEventType = callEventType,
                            maxDailyReplies = computedDailyLimit,
                            isEnabled = initialRule?.isEnabled ?: true
                        )

                        onSave(finalRule)
                    },
                    modifier = Modifier.weight(1.5f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

// ============================================================================
// TASK CREATION & COMPOSITION SCREEN
// ============================================================================
