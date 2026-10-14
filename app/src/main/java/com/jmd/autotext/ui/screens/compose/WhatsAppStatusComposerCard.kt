package com.jmd.autotext.ui.screens.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun WhatsAppStatusComposerCard(
    mediaUri: String,
    onMediaUriChange: (String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    targetPackage: String = "com.whatsapp",
    onTargetPackageChange: (String) -> Unit = {},
    onLaunchMediaPicker: () -> Unit,
    onOpenTemplates: () -> Unit
) {
    val isBusiness = targetPackage == "com.whatsapp.w4b"
    val accentColor = if (isBusiness) BentoGlassTokens.ElectricIndigo else BentoGlassTokens.VividEmerald

    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeLarge,
        borderColor = accentColor.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "WhatsApp Status Destination",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = BentoGlassTokens.TextPrimary
                )
            }
            BentoBadge(
                text = if (isBusiness) "WA Business Story" else "WhatsApp Story",
                color = accentColor,
                showDot = true
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Target App Toggle: WhatsApp vs WhatsApp Business
        Text("Target App:", fontSize = 11.5.sp, color = BentoGlassTokens.TextMuted, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassChip(
                selected = !isBusiness,
                onClick = { onTargetPackageChange("com.whatsapp") },
                label = "WhatsApp",
                icon = Icons.AutoMirrored.Filled.Send,
                activeColor = BentoGlassTokens.VividEmerald,
                modifier = Modifier.weight(1f)
            )
            GlassChip(
                selected = isBusiness,
                onClick = { onTargetPackageChange("com.whatsapp.w4b") },
                label = "WA Business",
                icon = Icons.Default.Business,
                activeColor = BentoGlassTokens.ElectricIndigo,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = BentoGlassTokens.ShapeMedium,
            color = accentColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            if (isBusiness) "Target: WhatsApp Business Status" else "Target: WhatsApp My Status",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = BentoGlassTokens.TextPrimary
                        )
                        Text(
                            if (isBusiness) "Navigates to WhatsApp Business Updates tab and posts to your story"
                            else "Navigates to WhatsApp Updates tab and posts directly to your Status story",
                            fontSize = 11.sp,
                            color = BentoGlassTokens.TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Status Mode Selector: Text vs Photo/Video
        Text("Status Mode:", fontSize = 11.5.sp, color = BentoGlassTokens.TextMuted, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassChip(
                selected = mediaUri.isBlank(),
                onClick = { onMediaUriChange("") },
                label = "Text Status (Pencil)",
                icon = Icons.Default.EditNote,
                activeColor = BentoGlassTokens.VividEmerald,
                modifier = Modifier.weight(1f)
            )
            GlassChip(
                selected = mediaUri.isNotBlank(),
                onClick = {
                    if (mediaUri.isBlank()) {
                        onLaunchMediaPicker()
                    }
                },
                label = "Photo / Video",
                icon = Icons.Default.AddPhotoAlternate,
                activeColor = BentoGlassTokens.NeonCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Media Attachment Box
        if (mediaUri.isNotBlank()) {
            val isVideo = mediaUri.contains("mp4", ignoreCase = true) || mediaUri.contains("video", ignoreCase = true)
            Surface(
                shape = BentoGlassTokens.ShapeMedium,
                color = BentoGlassTokens.NeonCyan.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, BentoGlassTokens.NeonCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (isVideo) Icons.Default.Videocam else Icons.Default.Image,
                            contentDescription = null,
                            tint = BentoGlassTokens.NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                if (isVideo) "Video Attached" else "Photo Attached",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = BentoGlassTokens.TextPrimary
                            )
                            Text(
                                mediaUri.substringAfterLast("/").take(28),
                                fontSize = 11.sp,
                                color = BentoGlassTokens.TextMuted,
                                maxLines = 1
                            )
                        }
                    }
                    IconButton(onClick = { onMediaUriChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove media",
                            tint = BentoGlassTokens.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        } else {
            GlassButton(
                text = "Attach Photo or Video",
                onClick = onLaunchMediaPicker,
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Default.AddPhotoAlternate,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Message Header & Templates Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (mediaUri.isNotBlank()) "Story Caption (Optional)" else "Status Text",
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
            label = if (mediaUri.isNotBlank()) "Caption" else "Status Text",
            placeholder = if (mediaUri.isNotBlank()) "Add a caption to your photo / video story..." else "Type your WhatsApp status update...",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = if (mediaUri.isNotBlank()) 2 else 3,
            maxLines = 6,
            accentColor = BentoGlassTokens.VividEmerald
        )

        // Status Preview Card (WhatsApp Status Simulation)
        if (messageText.isNotBlank() || mediaUri.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Status Preview:", fontSize = 11.sp, color = BentoGlassTokens.TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = BentoGlassTokens.ShapeMedium,
                color = Color(0xFF075E54),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (mediaUri.isNotBlank()) {
                        val isVid = mediaUri.contains("mp4", ignoreCase = true) || mediaUri.contains("video", ignoreCase = true)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (isVid) Icons.Default.Videocam else Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isVid) "[Video Status Story]" else "[Photo Status Story]",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (messageText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    if (messageText.isNotBlank()) {
                        Text(
                            text = messageText,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

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
