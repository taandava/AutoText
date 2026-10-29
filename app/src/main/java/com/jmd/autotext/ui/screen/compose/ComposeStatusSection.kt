package com.jmd.autotext.ui.screen.compose

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*

/**
 *
 * @param mediaUri Local URI of image or video for status update.
 * @param onMediaUriChange Callback when attached media is changed or removed.
 * @param messageText Text caption or body of text status.
 * @param onMessageTextChange Callback when caption is edited.
 * @param onLaunchMediaPicker Callback to launch system photo picker.
 * @param onOpenTemplates Callback to open template message drawer.
 */
@Composable
fun whatsAppStatusComposerCard(
    mediaUri: String,
    onMediaUriChange: (String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onLaunchMediaPicker: () -> Unit,
    onOpenTemplates: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.tertiary

    appCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge,
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
                    "WhatsApp Status Story",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            appBadge(
                text = "WhatsApp Story",
                color = accentColor,
                showDot = true
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = AppDesignTokens.ShapeMedium,
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
                            "Target: WhatsApp My Status",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Navigates to WhatsApp Updates tab and posts directly to your Status story",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Status Mode Selector: Text vs Photo/Video
        Text("Status Mode:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            glassChip(
                selected = mediaUri.isBlank(),
                onClick = { onMediaUriChange("") },
                label = "Text Status (Pencil)",
                icon = Icons.Default.EditNote,
                activeColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            glassChip(
                selected = mediaUri.isNotBlank(),
                onClick = {
                    if (mediaUri.isBlank()) {
                        onLaunchMediaPicker()
                    }
                },
                label = "Photo / Video",
                icon = Icons.Default.AddPhotoAlternate,
                activeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Media Attachment Box
        if (mediaUri.isNotBlank()) {
            val isVideo = mediaUri.contains("mp4", ignoreCase = true) || mediaUri.contains("video", ignoreCase = true)
            Surface(
                shape = AppDesignTokens.ShapeSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(AppDesignTokens.ShapeSmall)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isVideo) "Video Attached" else "Photo Attached",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = mediaUri.substringAfterLast("/").take(28),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onLaunchMediaPicker,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Change media",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onMediaUriChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove media",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        } else {
            glassButton(
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
                color = MaterialTheme.colorScheme.onSurface
            )
            glassButton(
                text = "Templates",
                onClick = onOpenTemplates,
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Default.FormatQuote,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                shape = AppDesignTokens.ShapePill
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        glassInputField(
            value = messageText,
            onValueChange = onMessageTextChange,
            label = if (mediaUri.isNotBlank()) "Story Caption (Optional)" else "Status Text",
            placeholder = if (mediaUri.isNotBlank()) "Add a caption to your photo / video story..." else "Type your WhatsApp status update...",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 3,
            maxLines = 4,
            accentColor = MaterialTheme.colorScheme.tertiary
        )

        // Live Status Preview Strip
        if (messageText.isNotBlank() || mediaUri.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = AppDesignTokens.ShapeSmall,
                color = Color(0xFF075E54).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (mediaUri.isNotBlank()) Icons.Default.PhotoLibrary else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Status Preview: ",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = messageText.ifBlank { "[Media Story Attached]" },
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Variable Insertion Chips
        Spacer(modifier = Modifier.height(10.dp))
        Text("Dynamic Variables:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        val statusVariables = listOf("{date}", "{short_date}", "{time}", "{day}", "{short_day}", "{day_num}")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            statusVariables.chunked(3).forEach { rowVars ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowVars.forEach { v ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(AppDesignTokens.ShapePill)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
                                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f), AppDesignTokens.ShapePill)
                                .clickable {
                                    onMessageTextChange(messageText + (if (messageText.isNotEmpty() && !messageText.endsWith(" ")) " " else "") + v)
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(v, fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Specialized composer card for scheduling automated WhatsApp Business Status stories.
 *
 * @param mediaUri Local URI of image or video for status update.
 * @param onMediaUriChange Callback when attached media is changed or removed.
 * @param messageText Text caption or body of text status.
 * @param onMessageTextChange Callback when caption is edited.
 * @param onLaunchMediaPicker Callback to launch system photo picker.
 * @param onOpenTemplates Callback to open template message drawer.
 */
@Composable
fun whatsAppBusinessStatusComposerCard(
    mediaUri: String,
    onMediaUriChange: (String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onLaunchMediaPicker: () -> Unit,
    onOpenTemplates: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary

    appCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge,
        borderColor = accentColor.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Business,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "WhatsApp Business Status Story",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            appBadge(
                text = "WA Business Story",
                color = accentColor,
                showDot = true
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = AppDesignTokens.ShapeMedium,
            color = accentColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Business,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Target: WhatsApp Business Status",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Navigates to WhatsApp Business Updates tab and posts directly to your Business Status story",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Status Mode Selector: Text vs Photo/Video
        Text("Status Mode:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            glassChip(
                selected = mediaUri.isBlank(),
                onClick = { onMediaUriChange("") },
                label = "Text Status (Pencil)",
                icon = Icons.Default.EditNote,
                activeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            glassChip(
                selected = mediaUri.isNotBlank(),
                onClick = {
                    if (mediaUri.isBlank()) {
                        onLaunchMediaPicker()
                    }
                },
                label = "Photo / Video",
                icon = Icons.Default.AddPhotoAlternate,
                activeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Media Attachment Box
        if (mediaUri.isNotBlank()) {
            val isVideo = mediaUri.contains("mp4", ignoreCase = true) || mediaUri.contains("video", ignoreCase = true)
            Surface(
                shape = AppDesignTokens.ShapeSmall,
                color = accentColor.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(AppDesignTokens.ShapeSmall)
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Image,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isVideo) "Video Attached" else "Photo Attached",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = mediaUri.substringAfterLast("/").take(28),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onLaunchMediaPicker,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Change media",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onMediaUriChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove media",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        } else {
            glassButton(
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
                color = MaterialTheme.colorScheme.onSurface
            )
            glassButton(
                text = "Templates",
                onClick = onOpenTemplates,
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Default.FormatQuote,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                shape = AppDesignTokens.ShapePill
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        glassInputField(
            value = messageText,
            onValueChange = onMessageTextChange,
            label = if (mediaUri.isNotBlank()) "Story Caption (Optional)" else "Status Text",
            placeholder = if (mediaUri.isNotBlank()) "Add a caption to your business story..." else "Type your WhatsApp Business status update...",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 3,
            maxLines = 4,
            accentColor = accentColor
        )

        // Live Status Preview Strip
        if (messageText.isNotBlank() || mediaUri.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = AppDesignTokens.ShapeSmall,
                color = Color(0xFF1B3B36).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (mediaUri.isNotBlank()) Icons.Default.PhotoLibrary else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Business Status: ",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = messageText.ifBlank { "[Media Story Attached]" },
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Variable Insertion Chips
        Spacer(modifier = Modifier.height(10.dp))
        Text("Dynamic Variables:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        val statusVariables = listOf("{date}", "{short_date}", "{time}", "{day}", "{short_day}", "{day_num}")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            statusVariables.chunked(3).forEach { rowVars ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowVars.forEach { v ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(AppDesignTokens.ShapePill)
                                .background(accentColor.copy(alpha = 0.1f))
                                .border(1.dp, accentColor.copy(alpha = 0.35f), AppDesignTokens.ShapePill)
                                .clickable {
                                    onMessageTextChange(messageText + (if (messageText.isNotEmpty() && !messageText.endsWith(" ")) " " else "") + v)
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(v, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}


/**
 * Bottom sheet modal enabling users to select target WhatsApp groups ingested from automated chat scanner.
 *
 * @param repository Data repository reading discovered group inventory.
 * @param selectedGroupName Currently selected group name string.
 * @param onSelectGroup Callback receiving selected group name.
 * @param onDismiss Callback to dismiss group picker sheet.
 */
