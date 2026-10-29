package com.jmd.autotext.ui.screen.autoreply

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
import com.jmd.autotext.core.*
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.repo.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.model.AutoReplyTypeItem
import com.jmd.autotext.ui.screen.compose.templatePickerSheet
import com.jmd.autotext.ui.theme.*

/**
 * Bottom sheet modal enabling users to select the trigger medium (Call, SMS, WhatsApp) and event type.
 *
 * @param onDismiss Callback to dismiss bottom sheet.
 * @param onSelectType Callback receiving chosen channel and event type key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun autoReplyTypeSelectionSheet(
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Select Auto-Reply Responder",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                appBadge(text = "4 TYPES", color = MaterialTheme.colorScheme.primary, showDot = false)
            }
            Text(
                "Choose the communication channel to automate. Each option opens its dedicated, tailored configuration form:",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val responderTypes = listOf(
                AutoReplyTypeItem(
                    channel = ChannelType.CALL,
                    title = "Phone Call Auto-Responder",
                    eventDescription = "2 Triggers: 📞 Missed Call or 📴 Call Ended",
                    replyMethodDescription = "Replies strictly via 📩 SMS through device SIM",
                    emoji = "📞",
                    color = MaterialTheme.colorScheme.primary,
                    defaultEvent = "MISSED_CALL"
                ),
                AutoReplyTypeItem(
                    channel = ChannelType.SMS,
                    title = "SMS Text Auto-Responder",
                    eventDescription = "Trigger: 📩 Incoming SMS Keyword Match (* for all)",
                    replyMethodDescription = "Replies strictly via 📩 SMS through device SIM",
                    emoji = "📩",
                    color = MaterialTheme.colorScheme.primary,
                    defaultEvent = "MESSAGE"
                ),
                AutoReplyTypeItem(
                    channel = ChannelType.WHATSAPP,
                    title = "WhatsApp Auto-Responder",
                    eventDescription = "3 Triggers: 💬 Message, 📞 Missed Call, or 📴 Call Ended",
                    replyMethodDescription = "Replies strictly in 💬 WhatsApp chat via quick-reply",
                    emoji = "💬",
                    color = MaterialTheme.colorScheme.tertiary,
                    defaultEvent = "MESSAGE"
                ),
                AutoReplyTypeItem(
                    channel = ChannelType.WHATSAPP_BUSINESS,
                    title = "WhatsApp Business Auto-Responder",
                    eventDescription = "3 Triggers: 💬 Message, 📞 Missed Call, or 📴 Call Ended",
                    replyMethodDescription = "Replies strictly in 💼 WhatsApp Business chat via quick-reply",
                    emoji = "💼",
                    color = MaterialTheme.colorScheme.primary,
                    defaultEvent = "MESSAGE"
                )
            )

            responderTypes.forEach { item ->
                appCard(
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
                            Text(item.emoji, fontSize = 22.sp)
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
 * Comprehensive modal dialog for creating or updating an automated reply rule with channel, triggers, recipient filters, and schedule.
 *
 * @param initialRule Existing rule to edit, or null when creating a new rule.
 * @param onDismiss Callback to dismiss editor dialog.
 * @param onSave Callback receiving populated rule data class on confirmation.
 */
@Composable
fun autoReplyRuleDialog(
    initialRule: AutoReplyRule?,
    onDismiss: () -> Unit,
    onSave: (AutoReplyRule) -> Unit
) {
    var ruleName by remember { mutableStateOf(initialRule?.name ?: "") }
    var selectedChannel by remember { mutableStateOf(initialRule?.channel ?: ChannelType.CALL) }
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
        ChannelType.WHATSAPP -> MaterialTheme.colorScheme.tertiary
        ChannelType.WHATSAPP_BUSINESS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = if (initialRule != null) "Edit Auto-Reply Rule" else "New Auto-Reply Rule",
                subtitle = "Automated reply configuration",
                onBack = onDismiss
            )
        },
        containerColor = AppDesignTokens.GlassBackground
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
            appCard(
                modifier = Modifier.fillMaxWidth(),
                shape = AppDesignTokens.ShapeLarge
            ) {
                glassInputField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = "Rule Identifier",
                    placeholder = "e.g. Missed Call Responder, Office Hours",
                    leadingIcon = Icons.AutoMirrored.Filled.Label,
                    accentColor = themeColor
                )
            }

            // 2. Target Platform Selector
            appCard(
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
                    ChannelType.CALL to ("Phone Calls" to "📞"),
                    ChannelType.SMS to ("SMS" to "📩"),
                    ChannelType.WHATSAPP to ("WhatsApp" to "💬"),
                    ChannelType.WHATSAPP_BUSINESS to ("WA Business" to "💼")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    targetPlatforms.chunked(2).forEach { rowChannels ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowChannels.forEach { (ch, pair) ->
                                val (title, emoji) = pair
                                val isSelected = selectedChannel == ch
                                val chipColor = when (ch) {
                                    ChannelType.CALL, ChannelType.SMS -> MaterialTheme.colorScheme.primary
                                    ChannelType.WHATSAPP -> MaterialTheme.colorScheme.tertiary
                                    ChannelType.WHATSAPP_BUSINESS -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                                glassChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedChannel = ch
                                        if (ch == ChannelType.CALL) {
                                            if (callEventType == "MESSAGE") callEventType = "MISSED_CALL"
                                        } else if (ch == ChannelType.SMS) {
                                            callEventType = "MESSAGE"
                                        } else {
                                            if (callEventType !in listOf("MESSAGE", "MISSED_CALL", "CALL_ENDED")) callEventType = "MESSAGE"
                                        }
                                    },
                                    label = title,
                                    activeColor = chipColor,
                                    emoji = emoji,
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
                    appCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppDesignTokens.ShapeLarge
                    ) {
                        Text(
                            "Phone Call Trigger Event",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Select which call event triggers an automatic response:",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("MISSED_CALL", "Missed Call", "📞"),
                                Triple("CALL_ENDED", "Call Ended", "📴")
                            ).forEach { (eventKey, eventLabel, eventEmoji) ->
                                glassChip(
                                    selected = callEventType == eventKey,
                                    onClick = { callEventType = eventKey },
                                    label = eventLabel,
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    emoji = eventEmoji,
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
                                Text("📩", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Reply Method: SMS Message", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "When a phone call is missed or ended, AutoText immediately sends an automated SMS reply to the caller's mobile number via your device SIM. Landlines and toll-free numbers are filtered out automatically.",
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
                    appCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppDesignTokens.ShapeLarge
                    ) {
                        Text(
                            "SMS Trigger Configuration",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Triggers automatically on incoming SMS messages received on your device:",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        glassInputField(
                            value = triggerPattern,
                            onValueChange = { triggerPattern = it },
                            label = "Trigger Keyword (* for all incoming SMS)",
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
                                Text("Exact Keyword Match", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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
                                Text("📩", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Reply Method: SMS Message", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "Automated response is sent as an SMS message via your SIM card.",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                ChannelType.WHATSAPP -> {
                    appCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppDesignTokens.ShapeLarge
                    ) {
                        Text(
                            "WhatsApp Trigger Event",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Select which WhatsApp event triggers this auto-reply:",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("MESSAGE", "Message", "💬"),
                                Triple("MISSED_CALL", "Missed Call", "📞"),
                                Triple("CALL_ENDED", "Call Ended", "📴")
                            ).forEach { (eventKey, eventLabel, eventEmoji) ->
                                glassChip(
                                    selected = callEventType == eventKey,
                                    onClick = { callEventType = eventKey },
                                    label = eventLabel,
                                    activeColor = MaterialTheme.colorScheme.tertiary,
                                    emoji = eventEmoji,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (callEventType == "MESSAGE") {
                            glassInputField(
                                value = triggerPattern,
                                onValueChange = { triggerPattern = it },
                                label = "Trigger Keyword (* for all incoming messages)",
                                placeholder = "e.g. *, price, help, support",
                                leadingIcon = Icons.Default.Tag,
                                accentColor = MaterialTheme.colorScheme.tertiary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Exact Keyword Match", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Match exact word instead of containing substring", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = isExactMatch,
                                    onCheckedChange = { isExactMatch = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.tertiary,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            }
                        } else {
                            Surface(
                                shape = AppDesignTokens.ShapeMedium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📞", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Triggers automatically whenever a voice or video call is ${if (callEventType == "CALL_ENDED") "ended" else "missed"} in WhatsApp.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = AppDesignTokens.ShapeMedium,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💬", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Reply Method: WhatsApp Chat Only", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.tertiary)
                                    Text(
                                        "Sends an automated reply directly inside the same WhatsApp chat via Android notification quick-reply automation.",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                ChannelType.WHATSAPP_BUSINESS -> {
                    appCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppDesignTokens.ShapeLarge
                    ) {
                        Text(
                            "WhatsApp Business Trigger Event",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Select which WhatsApp Business event triggers this auto-reply:",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("MESSAGE", "Message", "💬"),
                                Triple("MISSED_CALL", "Missed Call", "📞"),
                                Triple("CALL_ENDED", "Call Ended", "📴")
                            ).forEach { (eventKey, eventLabel, eventEmoji) ->
                                glassChip(
                                    selected = callEventType == eventKey,
                                    onClick = { callEventType = eventKey },
                                    label = eventLabel,
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    emoji = eventEmoji,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (callEventType == "MESSAGE") {
                            glassInputField(
                                value = triggerPattern,
                                onValueChange = { triggerPattern = it },
                                label = "Trigger Keyword (* for all incoming messages)",
                                placeholder = "e.g. *, quote, order, hours",
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
                                    Text("Exact Keyword Match", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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
                        } else {
                            Surface(
                                shape = AppDesignTokens.ShapeMedium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📞", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Triggers automatically whenever a business voice or video call is ${if (callEventType == "CALL_ENDED") "ended" else "missed"} in WhatsApp Business.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
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
                                Text("💼", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Reply Method: WA Business Only", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "Sends an automated reply directly inside WhatsApp Business only via Android notification quick-reply automation.",
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
            autoReplyLimitsCard(
                dailyLimitMode = dailyLimitMode,
                onDailyLimitModeChanged = { dailyLimitMode = it },
                customDailyLimitText = customDailyLimitText,
                onCustomDailyLimitTextChanged = { customDailyLimitText = it.filter(Char::isDigit) },
                cooldownSeconds = cooldownSeconds,
                onCooldownSecondsChanged = { cooldownSeconds = it }
            )

            // 5. Custom Reply Message Enterprise Card (Tailored for active channel & event)
            autoReplyTemplateCard(
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
                glassButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = GlassButtonVariant.SECONDARY,
                    emoji = "❌",
                    modifier = Modifier.weight(1f)
                )

                glassButton(
                    text = if (initialRule != null) "Update Rule" else "Save Rule",
                    variant = GlassButtonVariant.PRIMARY,
                    emoji = "✅",
                    onClick = {
                        val computedDailyLimit = when (dailyLimitMode) {
                            "1" -> 1
                            "3" -> 3
                            "5" -> 5
                            "UNLIMITED" -> 0
                            else -> customDailyLimitText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        }

                        val defaultName = when (selectedChannel) {
                            ChannelType.CALL -> if (callEventType == "CALL_ENDED") "Call Ended Responder" else "Missed Call Responder"
                            ChannelType.SMS -> "SMS Auto-Reply"
                            ChannelType.WHATSAPP -> when (callEventType) {
                                "MISSED_CALL" -> "WhatsApp Missed Call Reply"
                                "CALL_ENDED" -> "WhatsApp Call Ended Reply"
                                else -> "WhatsApp Chat Reply"
                            }
                            ChannelType.WHATSAPP_BUSINESS -> when (callEventType) {
                                "MISSED_CALL" -> "WA Business Missed Call Reply"
                                "CALL_ENDED" -> "WA Business Call Ended Reply"
                                else -> "WA Business Chat Reply"
                            }
                            else -> "Auto Reply - ${selectedChannel.displayName}"
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


