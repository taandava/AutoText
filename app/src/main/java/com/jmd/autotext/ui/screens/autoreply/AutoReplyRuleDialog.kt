package com.jmd.autotext.ui.screens.autoreply

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.AutoReplyRule
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Suppress("FunctionName")
@Composable
fun AutoReplyRuleDialog(
    initialRule: AutoReplyRule?,
    onDismiss: () -> Unit,
    onSave: (AutoReplyRule) -> Unit
) {
    var ruleName by remember { mutableStateOf(initialRule?.name ?: "") }
    var selectedChannel by remember { mutableStateOf(initialRule?.channel ?: ChannelType.CALL) }
    var callEventType by remember { mutableStateOf(initialRule?.callEventType ?: "BOTH") } // "BOTH", "MISSED_CALL", "CALL_ENDED"
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
            initialRule?.replyText ?: "Thanks for calling! I missed your call and will get back to you shortly."
        )
    }
    var triggerPattern by remember { mutableStateOf(initialRule?.triggerPattern ?: "*") }
    var cooldownMinutes by remember { mutableIntStateOf(initialRule?.cooldownMinutes ?: 10) }

    val isCall = selectedChannel == ChannelType.CALL

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BentoGlassTokens.GlassSurfaceElevated)
                    .border(
                        width = 1.dp,
                        brush = BentoGlassTokens.GlassBorderGradient,
                        shape = BentoGlassTokens.ShapeMedium
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = BentoGlassTokens.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (initialRule != null) "Edit Auto-Reply Rule" else "New Auto-Reply Rule",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = BentoGlassTokens.TextPrimary
                    )
                }
            }
        },
        containerColor = BentoGlassTokens.GlassBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Rule Name Input
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                shape = BentoGlassTokens.ShapeLarge
            ) {
                GlassInputField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = "Rule Identifier",
                    placeholder = "e.g. Missed Call Responder, Office Hours",
                    leadingIcon = Icons.AutoMirrored.Filled.Label,
                    accentColor = BentoGlassTokens.VividEmerald
                )
            }

            // 2. Target Platform Bento Card
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                shape = BentoGlassTokens.ShapeLarge
            ) {
                Text(
                    "Target Platform",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BentoGlassTokens.TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                val targetPlatforms = listOf(ChannelType.CALL, ChannelType.SMS, ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    targetPlatforms.chunked(2).forEach { rowChannels ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowChannels.forEach { ch ->
                                val isSelected = selectedChannel == ch
                                val chIcon = when (ch) {
                                    ChannelType.CALL -> Icons.Default.Call
                                    ChannelType.SMS -> Icons.Default.Sms
                                    else -> Icons.Default.PhoneAndroid
                                }
                                GlassChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedChannel = ch
                                        if (ch == ChannelType.CALL && replyText.isBlank()) {
                                            replyText = "Thanks for calling! I missed your call and will get back to you shortly."
                                        }
                                    },
                                    label = if (ch == ChannelType.CALL) "Calls (SMS)" else ch.displayName,
                                    icon = chIcon,
                                    activeColor = BentoGlassTokens.VividEmerald,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Call Trigger Event Selector (applicable to Phone Calls, WhatsApp & WhatsApp Business)
                val isCallOrWhatsApp = selectedChannel in listOf(ChannelType.CALL, ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS)
                if (isCallOrWhatsApp) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (isCall) "Phone Call Trigger Event:" else "Call / Message Trigger Event:",
                        fontSize = 13.sp,
                        color = BentoGlassTokens.TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "BOTH" to "All Events",
                            "MISSED_CALL" to "Missed Calls",
                            "CALL_ENDED" to "Ended Calls"
                        ).forEach { (eventKey, eventLabel) ->
                            GlassChip(
                                selected = callEventType == eventKey,
                                onClick = { callEventType = eventKey },
                                label = eventLabel,
                                activeColor = BentoGlassTokens.NeonCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (isCall) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = BentoGlassTokens.ShapeMedium,
                            color = BentoGlassTokens.VividEmerald.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, BentoGlassTokens.VividEmerald.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = BentoGlassTokens.VividEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Mobile Numbers Only: SMS replies are strictly verified and sent to valid mobile numbers only. Landlines, shortcodes, and toll-free numbers are filtered out automatically.",
                                    fontSize = 12.5.sp,
                                    color = BentoGlassTokens.TextPrimary,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = BentoGlassTokens.ShapeMedium,
                            color = BentoGlassTokens.VividEmerald.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, BentoGlassTokens.VividEmerald.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = BentoGlassTokens.VividEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Auto-Reply to WhatsApp: Automatically detects incoming messages, missed voice/video calls, and ended calls in WhatsApp and sends instant custom replies.",
                                    fontSize = 12.5.sp,
                                    color = BentoGlassTokens.TextPrimary,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3. Daily Frequency & Cooldown Bento Card
            AutoReplyLimitsCard(
                dailyLimitMode = dailyLimitMode,
                onDailyLimitModeChanged = { dailyLimitMode = it },
                customDailyLimitText = customDailyLimitText,
                onCustomDailyLimitTextChanged = { customDailyLimitText = it.filter(Char::isDigit) },
                cooldownMinutes = cooldownMinutes,
                onCooldownMinutesChanged = { cooldownMinutes = it }
            )

            // 4. Keyword Trigger (for SMS/Chat)
            if (!isCall) {
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BentoGlassTokens.ShapeLarge
                ) {
                    GlassInputField(
                        value = triggerPattern,
                        onValueChange = { triggerPattern = it },
                        label = "Trigger Keyword (* for all)",
                        placeholder = "hello, info, price, *",
                        leadingIcon = Icons.Default.Tag,
                        accentColor = BentoGlassTokens.NeonCyan
                    )
                }
            }

            // 5. Custom Reply Message Bento Card
            AutoReplyTemplateCard(
                isCall = isCall,
                replyText = replyText,
                onReplyTextChanged = { replyText = it }
            )

            // 6. Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )

                GlassButton(
                    text = if (initialRule != null) "Update Rule" else "Save Rule",
                    onClick = {
                        val computedDailyLimit = when (dailyLimitMode) {
                            "1" -> 1
                            "3" -> 3
                            "5" -> 5
                            "UNLIMITED" -> 0
                            else -> customDailyLimitText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        }

                        val finalRule = AutoReplyRule(
                            id = initialRule?.id ?: 0L,
                            name = ruleName.ifEmpty {
                                if (isCall) "Call Reply ($callEventType)" else "Auto Reply - ${selectedChannel.displayName}"
                            },
                            channel = selectedChannel,
                            replyText = replyText.ifEmpty { "Thank you for reaching out! Will get back to you shortly." },
                            triggerPattern = if (isCall) "*" else triggerPattern.ifEmpty { "*" },
                            cooldownMinutes = cooldownMinutes,
                            callEventType = callEventType,
                            maxDailyReplies = computedDailyLimit,
                            isEnabled = initialRule?.isEnabled ?: true
                        )

                        onSave(finalRule)
                    },
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.5f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
