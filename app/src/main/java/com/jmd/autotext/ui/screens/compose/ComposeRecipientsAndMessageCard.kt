package com.jmd.autotext.ui.screens.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.screens.SelectedRecipient
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun ComposeRecipientsAndMessageCard(
    selectedChannel: ChannelType,
    selectedRecipients: List<SelectedRecipient>,
    duplicateWarning: String?,
    onDismissDuplicateWarning: () -> Unit,
    onClearAllRecipients: () -> Unit,
    onRemoveRecipient: (Int) -> Unit,
    recipientsInput: String,
    onRecipientsInputChange: (String) -> Unit,
    onAddManualRecipient: () -> Unit,
    onOpenWhatsAppContacts: () -> Unit,
    onOpenPoolPicker: () -> Unit,
    onOpenContactsPicker: () -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onOpenTemplates: () -> Unit,
    smsPartCount: Int,
    isUnicode: Boolean
) {
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeLarge
    ) {
        // Recipients Header & Quick Pickers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recipients",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = BentoGlassTokens.TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selectedChannel == ChannelType.WHATSAPP || selectedChannel == ChannelType.WHATSAPP_BUSINESS) {
                    GlassButton(
                        text = "WA Contacts",
                        onClick = onOpenWhatsAppContacts,
                        variant = GlassButtonVariant.PRIMARY,
                        icon = Icons.AutoMirrored.Filled.Send,
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                        shape = BentoGlassTokens.ShapePill
                    )
                }
                GlassButton(
                    text = "From Pool",
                    onClick = onOpenPoolPicker,
                    variant = GlassButtonVariant.SECONDARY,
                    icon = Icons.Default.Group,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    shape = BentoGlassTokens.ShapePill
                )
                GlassButton(
                    text = "Contacts",
                    onClick = onOpenContactsPicker,
                    variant = GlassButtonVariant.SECONDARY,
                    icon = Icons.Default.Contacts,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    shape = BentoGlassTokens.ShapePill
                )
            }
        }

        // Duplicate Warning Banner
        if (duplicateWarning != null) {
            Surface(
                shape = BentoGlassTokens.ShapeSmall,
                color = BentoGlassTokens.CyberRose.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoGlassTokens.CyberRose.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = BentoGlassTokens.CyberRose,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = duplicateWarning,
                        color = BentoGlassTokens.CyberRose,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissDuplicateWarning,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = BentoGlassTokens.CyberRose,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Selected Recipients Chips / Cards
        if (selectedRecipients.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Selected Recipients (${selectedRecipients.size}):",
                    fontSize = 13.sp,
                    color = BentoGlassTokens.TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Clear All",
                    fontSize = 12.5.sp,
                    color = BentoGlassTokens.CyberRose,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onClearAllRecipients() }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                selectedRecipients.forEachIndexed { idx, item ->
                    Surface(
                        shape = BentoGlassTokens.ShapeSmall,
                        color = BentoGlassTokens.GlassSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BentoGlassTokens.GlassBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = BentoGlassTokens.NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    if (item.name.isNotBlank()) {
                                        Text(
                                            text = item.name,
                                            color = BentoGlassTokens.TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = item.phone,
                                        color = BentoGlassTokens.NeonCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onRemoveRecipient(idx) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = BentoGlassTokens.TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Manual Recipient Entry with Add Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                GlassInputField(
                    value = recipientsInput,
                    onValueChange = onRecipientsInputChange,
                    label = "Add Recipient Manually",
                    placeholder = "Enter phone number...",
                    leadingIcon = Icons.Default.Phone,
                    accentColor = BentoGlassTokens.NeonCyan
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            GlassButton(
                text = "Add",
                onClick = onAddManualRecipient,
                variant = GlassButtonVariant.PRIMARY,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
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
                "Message Content",
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
            placeholder = "Type your automated message...",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            accentColor = BentoGlassTokens.NeonCyan
        )

        // SMS Char & Counter
        if (selectedChannel == ChannelType.SMS) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${messageText.length} chars • $smsPartCount SMS part${if (smsPartCount > 1) "s" else ""} (${if (isUnicode) "UCS-2 Unicode" else "GSM-7"})",
                    fontSize = 11.sp,
                    color = if (smsPartCount > 1) BentoGlassTokens.RadiantAmber else BentoGlassTokens.TextMuted,
                    fontWeight = FontWeight.Medium
                )
                BentoBadge(
                    text = "App cap: 100/day",
                    color = BentoGlassTokens.NeonCyan,
                    showDot = false
                )
            }
        }

        // Variable Insertion Chips
        Spacer(modifier = Modifier.height(10.dp))
        Text("Dynamic Variables:", fontSize = 11.sp, color = BentoGlassTokens.TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        val variables = listOf("{name}", "{first_name}", "{phone}", "{date}", "{time}")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            variables.chunked(3).forEach { rowVars ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowVars.forEach { v ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(BentoGlassTokens.ShapePill)
                                .background(BentoGlassTokens.NeonCyan.copy(alpha = 0.1f))
                                .border(1.dp, BentoGlassTokens.NeonCyan.copy(alpha = 0.3f), BentoGlassTokens.ShapePill)
                                .clickable { onMessageTextChange("$messageText $v") }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(v, fontSize = 11.sp, color = BentoGlassTokens.NeonCyan, fontWeight = FontWeight.Bold)
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
