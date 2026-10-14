package com.jmd.autotext.ui.screens.compose

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun WhatsAppGroupComposerCard(
    context: Context,
    repository: AppRepository,
    selectedGroupName: String,
    onSelectGroupName: (String) -> Unit,
    recipientsInput: String,
    onRecipientsInputChange: (String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onOpenGroupsDialog: () -> Unit,
    onOpenTemplates: () -> Unit
) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeLarge,
        borderColor = BentoGlassTokens.VividEmerald.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Target WhatsApp Group",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = BentoGlassTokens.TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassButton(
                    text = "Pick in WA",
                    onClick = {
                        AutoTextAccessibilityService.startManualChatPicker(context) { picked ->
                            onSelectGroupName(picked)
                        }
                    },
                    variant = GlassButtonVariant.PRIMARY,
                    icon = Icons.Default.Add,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                    shape = BentoGlassTokens.ShapePill
                )
                GlassButton(
                    text = "⚡ Scan",
                    onClick = {
                        AutoTextAccessibilityService.startWhatsAppChatScan(context)
                    },
                    variant = GlassButtonVariant.SECONDARY,
                    icon = Icons.Default.Refresh,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                    shape = BentoGlassTokens.ShapePill
                )
                GlassButton(
                    text = "List",
                    onClick = onOpenGroupsDialog,
                    variant = GlassButtonVariant.SECONDARY,
                    icon = Icons.Default.Group,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                    shape = BentoGlassTokens.ShapePill
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Group Banner
        if (selectedGroupName.isNotBlank()) {
            Surface(
                shape = BentoGlassTokens.ShapeSmall,
                color = BentoGlassTokens.VividEmerald.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, BentoGlassTokens.VividEmerald.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        BentoAvatar(
                            name = selectedGroupName,
                            size = 32.dp,
                            badgeIcon = Icons.Default.Group,
                            badgeColor = BentoGlassTokens.VividEmerald
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = selectedGroupName,
                                color = BentoGlassTokens.TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Target WhatsApp Group",
                                color = BentoGlassTokens.VividEmerald,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = { onSelectGroupName("") },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear Group",
                            tint = BentoGlassTokens.CyberRose,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Manual Group Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                GlassInputField(
                    value = recipientsInput,
                    onValueChange = onRecipientsInputChange,
                    label = "Or Type Group Name Directly",
                    placeholder = "e.g. Family Group, VIP Buyers, Sales Team",
                    leadingIcon = Icons.Default.Group,
                    accentColor = BentoGlassTokens.VividEmerald
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            GlassButton(
                text = "Set",
                onClick = {
                    if (recipientsInput.isNotBlank()) {
                        val trimmed = recipientsInput.trim()
                        onSelectGroupName(trimmed)
                        repository.saveWhatsAppGroup(trimmed)
                        onRecipientsInputChange("")
                    }
                },
                variant = GlassButtonVariant.PRIMARY,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                shape = BentoGlassTokens.ShapeSmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Message Header & Templates Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Group Message Content",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = BentoGlassTokens.TextPrimary
            )
            GlassButton(
                text = "Templates",
                onClick = onOpenTemplates,
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Default.FormatQuote,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                shape = BentoGlassTokens.ShapePill
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        GlassInputField(
            value = messageText,
            onValueChange = onMessageTextChange,
            label = "Message Text",
            placeholder = "Type message to be sent to this group...",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            accentColor = BentoGlassTokens.VividEmerald
        )

        // Variable Insertion Chips
        Spacer(modifier = Modifier.height(10.dp))
        Text("Dynamic Variables:", fontSize = 11.sp, color = BentoGlassTokens.TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        val variables = listOf("{date}", "{time}")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            variables.forEach { variable ->
                GlassChip(
                    selected = false,
                    onClick = {
                        onMessageTextChange(messageText + (if (messageText.isNotEmpty() && !messageText.endsWith(" ")) " " else "") + variable)
                    },
                    label = variable,
                    activeColor = BentoGlassTokens.VividEmerald
                )
            }
        }
    }
}
