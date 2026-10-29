package com.jmd.autotext.ui.screen.compose

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.core.*
import com.jmd.autotext.data.repo.*
import com.jmd.autotext.data.model.*
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.data.model.PickedChatType
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*

/**
 * Bottom sheet modal enabling users to select target WhatsApp groups ingested from automated chat scanner.
 *
 * @param repository Data repository reading discovered group inventory.
 * @param selectedGroupName Currently selected group name string.
 * @param onSelectGroup Callback receiving selected group name.
 * @param onDismiss Callback to dismiss group picker sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun whatsAppGroupPickerSheet(
    repository: AppRepository,
    selectedGroupName: String,
    onSelectGroup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var groupSearch by remember { mutableStateOf("") }
    val allGroups = remember { repository.getWhatsAppGroupItems() }
    val filteredGroups = remember(groupSearch, allGroups) {
        if (groupSearch.isBlank()) allGroups
        else allGroups.filter { it.name.contains(groupSearch, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Saved WhatsApp Groups",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                appBadge(
                    text = "${allGroups.size} Saved",
                    color = MaterialTheme.colorScheme.tertiary,
                    showDot = false
                )
            }

            glassSearchField(
                value = groupSearch,
                onValueChange = { groupSearch = it },
                placeholder = "Search ${allGroups.size} saved groups...",
                leadingIcon = Icons.Default.Search,
                onClear = { groupSearch = "" }
            )

            if (filteredGroups.isEmpty()) {
                appCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.GroupOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (allGroups.isEmpty()) "No Saved Groups Found" else "No matching groups",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (allGroups.isEmpty())
                                "Use 'Pick in WA' to choose a group live, or scan chats from the Dashboard to discover groups."
                            else "Try a different search term.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        glassButton(
                            text = "Pick Live from WhatsApp",
                            onClick = {
                                AutoTextAccessibilityService.startManualChatPickerTitle(context) { title ->
                                    if (title.isNotBlank()) {
                                        onSelectGroup(title)
                                        onDismiss()
                                    }
                                }
                            },
                            variant = GlassButtonVariant.PRIMARY,
                            emoji = "➕"
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredGroups, key = { it.id }) { g ->
                        val isSelected = g.name.equals(selectedGroupName, ignoreCase = true)
                        appCard(
                            onClick = {
                                onSelectGroup(g.name)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    contactAvatar(
                                        name = g.name,
                                        size = 36.dp,
                                        badgeIcon = Icons.Default.Group,
                                        badgeColor = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = g.name,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (g.memberCountStr.isNotBlank()) {
                                            Text(
                                                text = "${g.memberCountStr} participants",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (isSelected) {
                                    appBadge(text = "Selected", color = MaterialTheme.colorScheme.tertiary)
                                } else {
                                    Icon(
                                        Icons.Default.CheckCircleOutline,
                                        contentDescription = "Select",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



/**
 * Composer card for scheduling dispatches directly into WhatsApp or WhatsApp Business group chats.
 *
 * @param context Local context for launching WhatsApp scanner intent.
 * @param repository Data repository reading saved groups.
 * @param selectedGroupName Name of target group chat.
 * @param onSelectGroupName Callback when target group is picked or entered.
 * @param messageText Message body to dispatch into group.
 * @param onMessageTextChange Callback when message body is updated.
 * @param onOpenTemplates Callback to open template picker drawer.
 * @param mediaUri Local URI of media attachment.
 * @param onMediaUriChange Callback when media is added or cleared.
 * @param onLaunchMediaPicker Callback to open photo/media picker.
 * @param selectedChannel Channel configuration (WHATSAPP_GROUP or WHATSAPP_BUSINESS).
 */
@Composable
fun whatsAppGroupComposerCard(
    context: Context,
    repository: AppRepository,
    selectedGroupName: String,
    onSelectGroupName: (String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onOpenTemplates: () -> Unit,
    mediaUri: String = "",
    onMediaUriChange: (String) -> Unit = {},
    onLaunchMediaPicker: () -> Unit = {},
    selectedChannel: ChannelType = ChannelType.WHATSAPP_GROUP
) {
    val isBusiness = selectedChannel == ChannelType.WHATSAPP_BUSINESS
    val targetPkg = if (isBusiness) WhatsAppPackageHelper.PACKAGE_WHATSAPP_BUSINESS else WhatsAppPackageHelper.PACKAGE_WHATSAPP
    val groupLabel = if (isBusiness) "Target W4B Group" else "Target WhatsApp Group"
    val pickLabel = if (isBusiness) "Pick in W4B" else "Pick in WA"
    val appLabel = if (isBusiness) "WhatsApp Business" else "WhatsApp"
    var showSavedGroupsSheet by remember { mutableStateOf(false) }

    appCard(
        modifier = Modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge,
        borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                groupLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                glassButton(
                    text = "Saved Groups",
                    onClick = { showSavedGroupsSheet = true },
                    variant = GlassButtonVariant.SECONDARY,
                    emoji = "👥",
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                    shape = AppDesignTokens.ShapePill
                )
                glassButton(
                    text = pickLabel,
                    onClick = {
                        AutoTextAccessibilityService.startManualChatPicker(context, targetPackage = targetPkg, channel = selectedChannel) { chatInfo ->
                            when (chatInfo.type) {
                                PickedChatType.GROUP -> {
                                    onSelectGroupName(chatInfo.title)
                                }
                                PickedChatType.INDIVIDUAL -> {
                                    context.showAppToast("'${chatInfo.title}' is an individual chat, not a group. Selected as target.")
                                    onSelectGroupName(chatInfo.title)
                                }
                                PickedChatType.STATUS -> {
                                    context.showAppToast("Status updates cannot be used as target groups")
                                }
                                PickedChatType.CHANNEL -> {
                                    context.showAppToast("Channels cannot be used as target groups")
                                }
                                PickedChatType.BROADCAST -> {
                                    onSelectGroupName("broadcast:${chatInfo.title}")
                                }
                            }
                        }
                    },
                    variant = GlassButtonVariant.PRIMARY,
                    emoji = "➕",
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Group Banner
        if (selectedGroupName.isNotBlank()) {
            Surface(
                shape = AppDesignTokens.ShapeSmall,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        contactAvatar(
                            name = selectedGroupName,
                            size = 32.dp,
                            badgeIcon = Icons.Default.Group,
                            badgeColor = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = selectedGroupName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Target WhatsApp Group",
                                color = MaterialTheme.colorScheme.tertiary,
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
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (selectedGroupName.isBlank()) {
            Surface(
                shape = AppDesignTokens.ShapeSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Choose from 'Saved Groups' or tap '$pickLabel' to select live in $appLabel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
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

        // Media Attachment Selector for WhatsApp Group
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Group Media Attachment (Optional)",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            if (mediaUri.isNotBlank()) {
                Text(
                    "Remove",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onMediaUriChange("") }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (mediaUri.isNotBlank()) {
            val isVideo = mediaUri.contains("mp4", ignoreCase = true) || mediaUri.contains("video", ignoreCase = true)
            Surface(
                shape = AppDesignTokens.ShapeSmall,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isVideo) "Video Attached" else "Image / File Attached",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = mediaUri.substringAfterLast("/").take(32),
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    IconButton(onClick = { onMediaUriChange("") }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                glassButton(
                    text = "Attach Image",
                    onClick = onLaunchMediaPicker,
                    variant = GlassButtonVariant.SECONDARY,
                    icon = Icons.Default.AddPhotoAlternate,
                    modifier = Modifier.weight(1f),
                    shape = AppDesignTokens.ShapeSmall
                )
                glassButton(
                    text = "Attach Video",
                    onClick = onLaunchMediaPicker,
                    variant = GlassButtonVariant.SECONDARY,
                    icon = Icons.Default.Videocam,
                    modifier = Modifier.weight(1f),
                    shape = AppDesignTokens.ShapeSmall
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        glassInputField(
            value = messageText,
            onValueChange = onMessageTextChange,
            label = "Message Text",
            placeholder = "Type message to be sent to this group...",
            leadingIcon = Icons.Default.EditNote,
            singleLine = false,
            minLines = 3,
            maxLines = 4,
            accentColor = MaterialTheme.colorScheme.tertiary
        )

        // Variable Insertion Chips
        Spacer(modifier = Modifier.height(10.dp))
        Text("Dynamic Variables:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        val groupVariables = listOf("{name}", "{date}", "{short_date}", "{time}", "{day}", "{short_day}", "{day_num}")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            groupVariables.chunked(4).forEach { rowVars ->
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
                    if (rowVars.size < 4) {
                        repeat(4 - rowVars.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (showSavedGroupsSheet) {
            whatsAppGroupPickerSheet(
                repository = repository,
                selectedGroupName = selectedGroupName,
                onSelectGroup = onSelectGroupName,
                onDismiss = { showSavedGroupsSheet = false }
            )
        }
    }
}



/**
 * Scheduling card providing inline calendar/time selectors, recurrence frequency presets, custom minute intervals, and SIM slot selector.
 *
 * @param selectedChannel Channel configuration for this scheduled task.
 * @param triggerTimeMillis Exact epoch millisecond timestamp when the task should fire.
 * @param onTriggerTimeChanged Callback invoked when execution timestamp is adjusted.
 * @param selectedRecurrence Recurrence pattern (ONCE, DAILY, WEEKLY, MONTHLY, HOURLY, CUSTOM_MINUTES).
 * @param onRecurrenceChanged Callback when recurrence rule is modified.
 * @param customIntervalMinutes Number of minutes between triggers if using custom interval.
 * @param onCustomIntervalMinutesChanged Callback when custom minute interval changes.
 * @param simSlot Target SIM card index (0 for SIM 1, 1 for SIM 2).
 * @param onSimSlotChanged Callback when selected SIM slot is updated.
 * @param selectedDaysOfWeek Days of week selected for weekly repetition schedule.
 * @param onSelectedDaysOfWeekChanged Callback when selected days of week are toggled.
 */
