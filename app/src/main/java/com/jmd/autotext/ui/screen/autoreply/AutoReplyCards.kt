package com.jmd.autotext.ui.screen.autoreply

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.prefs.*
import com.jmd.autotext.data.repo.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

/**
 * Card displaying an auto reply rule with condition summary and action triggers.
 *
 * @param rule The auto reply rule definition to render.
 * @param onToggle Callback triggered when rule activation switch is flipped.
 * @param onEdit Callback triggered to open editor dialog for this rule.
 * @param onDelete Callback triggered when trash action is confirmed.
 */
@Composable
fun autoReplyRuleCard(
    rule: AutoReplyRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val themeColor = when (rule.channel) {
        ChannelType.CALL -> MaterialTheme.colorScheme.primary
        ChannelType.SMS -> MaterialTheme.colorScheme.primary
        ChannelType.WHATSAPP -> MaterialTheme.colorScheme.tertiary
        ChannelType.WHATSAPP_BUSINESS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }

    val (channelEmoji, channelText) = when (rule.channel) {
        ChannelType.CALL -> "📞" to "Calls (SMS)"
        ChannelType.SMS -> "📩" to "SMS"
        ChannelType.WHATSAPP -> "💬" to "WhatsApp"
        ChannelType.WHATSAPP_BUSINESS -> "💼" to "WA Business"
        else -> "💬" to rule.channel.displayName
    }

    val (eventEmoji, eventBadgeText) = when (rule.channel) {
        ChannelType.CALL -> when (rule.callEventType.uppercase()) {
            "MISSED_CALL" -> "📞" to "Missed Call"
            "CALL_ENDED" -> "📞" to "Call Ended"
            else -> "📞" to "Missed & Ended"
        }
        ChannelType.SMS -> "📩" to "Incoming SMS"
        ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS -> when (rule.callEventType.uppercase()) {
            "MISSED_CALL" -> "📞" to "Missed Call"
            "CALL_ENDED" -> "📞" to "Call Ended"
            "MESSAGE" -> "💬" to "Message"
            else -> "💬" to "All Events"
        }
        else -> "💬" to rule.channel.displayName
    }

    val replyMechanismText = when (rule.channel) {
        ChannelType.CALL -> "Auto-replies via SMS to mobile numbers"
        ChannelType.SMS -> "Auto-replies via SMS • Trigger: ${if (rule.triggerPattern == "*") "All Incoming" else "\"${rule.triggerPattern}\""}"
        ChannelType.WHATSAPP -> if (rule.callEventType.uppercase() in listOf("MISSED_CALL", "CALL_ENDED")) "Replies in WhatsApp chat" else "Replies in WhatsApp • Trigger: ${if (rule.triggerPattern == "*") "All Incoming" else "\"${rule.triggerPattern}\""}"
        ChannelType.WHATSAPP_BUSINESS -> if (rule.callEventType.uppercase() in listOf("MISSED_CALL", "CALL_ENDED")) "Replies in WA Business chat" else "Replies in WA Business • Trigger: ${if (rule.triggerPattern == "*") "All Incoming" else "\"${rule.triggerPattern}\""}"
        else -> "Replies via ${rule.channel.displayName}"
    }

    appCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onEdit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header: Platform Badge, Event Badge, Rule Name, Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    appBadge(
                        text = channelText,
                        emoji = channelEmoji,
                        color = themeColor,
                        showDot = false
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    appBadge(
                        text = eventBadgeText,
                        emoji = eventEmoji,
                        color = if (eventBadgeText.contains("Missed", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        showDot = false
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = rule.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = themeColor,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // Badges row: Mobile Only (for Call/SMS), Daily Limit, Cooldown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (rule.channel in listOf(ChannelType.CALL, ChannelType.SMS)) {
                    appBadge(text = "Mobile Only", emoji = "📱", color = MaterialTheme.colorScheme.primary, showDot = false)
                } else {
                    appBadge(text = "App Chat", emoji = "💬", color = themeColor, showDot = false)
                }
                appBadge(
                    text = if (rule.maxDailyReplies <= 0) "Unlimited" else "Max ${rule.maxDailyReplies}x/day",
                    emoji = "📊",
                    color = MaterialTheme.colorScheme.primary,
                    showDot = false
                )
                Text(
                    text = "⏱️ ${rule.cooldownSeconds}s cooldown",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Message Snippet
            Surface(
                shape = AppDesignTokens.ShapeSmall,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = rule.replyText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    lineHeight = 17.5.sp
                )
            }

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = replyMechanismText,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("🗑️", fontSize = 16.sp)
                }
            }
        }
    }
}



/**
 * Anti-spam and frequency throttling card managing daily message caps and cooldown intervals per sender.
 *
 * @param dailyLimitMode Selected daily quota preset or custom string.
 * @param onDailyLimitModeChanged Callback invoked when daily quota preset changes.
 * @param customDailyLimitText Custom integer limit string when in custom mode.
 * @param onCustomDailyLimitTextChanged Callback invoked when custom limit text changes.
 * @param cooldownSeconds Number of seconds to suppress replies to the same contact.
 * @param onCooldownSecondsChanged Callback invoked when cooldown seconds value is adjusted.
 * @param modifier Optional layout modifier.
 */
@Composable
fun autoReplyLimitsCard(
    dailyLimitMode: String,
    onDailyLimitModeChanged: (String) -> Unit,
    customDailyLimitText: String,
    onCustomDailyLimitTextChanged: (String) -> Unit,
    cooldownSeconds: Int,
    onCooldownSecondsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var cooldownText by remember(cooldownSeconds) { mutableStateOf(cooldownSeconds.toString()) }

    appCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge
    ) {
        Text("Anti-Spam & Contact Daily Quota", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Safeguards against messaging the same caller repeatedly within a 24-hour cycle.",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("1" to "1x / day", "3" to "3x / day", "5" to "5x / day").forEach { (mode, label) ->
                glassChip(
                    selected = dailyLimitMode == mode,
                    onClick = { onDailyLimitModeChanged(mode) },
                    label = label,
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("CUSTOM" to "Custom", "UNLIMITED" to "Unlimited").forEach { (mode, label) ->
                glassChip(
                    selected = dailyLimitMode == mode,
                    onClick = { onDailyLimitModeChanged(mode) },
                    label = label,
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (dailyLimitMode == "CUSTOM") {
            Spacer(modifier = Modifier.height(10.dp))
            glassInputField(
                value = customDailyLimitText,
                onValueChange = onCustomDailyLimitTextChanged,
                label = "Custom Times Per Day",
                placeholder = "e.g. 2, 4, 10",
                leadingIcon = Icons.Default.Pin,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Repeat Delay Interval Entry
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            glassInputField(
                value = cooldownText,
                onValueChange = { newStr ->
                    val digits = newStr.filter { it.isDigit() }.take(4)
                    cooldownText = digits
                    val num = digits.toIntOrNull()
                    if (num != null && num > 0) {
                        onCooldownSecondsChanged(num.coerceIn(10, 60))
                    }
                },
                label = "Repeat Delay Interval (Seconds)",
                placeholder = "e.g. 10",
                leadingIcon = Icons.Default.Timer,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )

            // Stepper controls: [-] [+]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), CircleShape)
                        .clickable {
                            val next = (cooldownSeconds - 5).coerceAtLeast(10)
                            cooldownText = next.toString()
                            onCooldownSecondsChanged(next)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "-", color = MaterialTheme.colorScheme.tertiary, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), CircleShape)
                        .clickable {
                            val next = (cooldownSeconds + 5).coerceAtMost(60)
                            cooldownText = next.toString()
                            onCooldownSecondsChanged(next)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+", color = MaterialTheme.colorScheme.tertiary, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Preset Chips for Cooldown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(10 to "10s", 15 to "15s", 20 to "20s", 30 to "30s", 45 to "45s", 60 to "60s").forEach { (presetSec, presetLabel) ->
                val isSel = cooldownSeconds == presetSec
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(AppDesignTokens.ShapePill)
                        .background(if (isSel) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, if (isSel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant, AppDesignTokens.ShapePill)
                        .clickable {
                            cooldownText = presetSec.toString()
                            onCooldownSecondsChanged(presetSec)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = presetLabel,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}


/**
 * Template message editor card for crafting response texts with dynamic placeholder chips (e.g. {name}, {time}).
 *
 * @param channel Target channel type (Calls, SMS, WhatsApp).
 * @param callEventType Associated trigger event type.
 * @param replyText Current draft message body.
 * @param onReplyTextChanged Callback when message body is edited.
 * @param modifier Optional layout modifier.
 */
@Composable
fun autoReplyTemplateCard(
    channel: ChannelType,
    callEventType: String,
    replyText: String,
    onReplyTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    appCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge
    ) {
        glassInputField(
            value = replyText,
            onValueChange = onReplyTextChanged,
            label = "Auto-Reply Message Text",
            placeholder = "Thanks for reaching out! Will reply shortly.",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            accentColor = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text("Dynamic Variables:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        val tagList = listOf("{name}", "{first_name}", "{phone}", "{time}", "{date}", "{day}", "{short_date}", "{day_num}", "{event}")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tagList.chunked(3).forEach { rowVars ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowVars.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(AppDesignTokens.ShapePill)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), AppDesignTokens.ShapePill)
                                .clickable {
                                    val formatted = if (replyText.endsWith(" ") || replyText.isEmpty()) "$replyText$tag" else "$replyText $tag"
                                    onReplyTextChanged(formatted)
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tag, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
    }
}


