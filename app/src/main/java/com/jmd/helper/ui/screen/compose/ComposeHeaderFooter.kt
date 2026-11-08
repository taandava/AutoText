package com.jmd.helper.ui.screen.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.helper.data.model.*
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.theme.*

/**
 * Top bar header rendering task title, mode indicator, channel badge, and close button.
 * @param selectedChannel Channel configuration active for this compose session.
 * @param taskToEdit Existing task model if editing; null if drafting a new item.
 * @param onCancel Callback invoked to dismiss draft.
 * @param modifier Optional layout modifier.
 */
@Composable
fun ComposeTopHeader(
    selectedChannel: ChannelType,
    taskToEdit: ScheduledTask?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (taskToEdit != null) "Edit Task" else "New ${selectedChannel.displayName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (taskToEdit != null) "Modify task schedule" else "Set schedule & message",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AppBadge(
                text = selectedChannel.displayName,
                icon = MaterialIconMapper.channelIcon(selectedChannel),
                color = MaterialIconMapper.channelColor(selectedChannel),
                showDot = false
            )
        }
    }
}

/**
 * Bottom action bar with Save Task, Cancel, and optional Delete triggers.
 *
 * @param isEditing True if modifying an existing task; false for new creation.
 * @param onCancel Callback to dismiss the draft.
 * @param onSave Callback to validate and schedule task.
 * @param modifier Optional layout modifier.
 * @param onDelete Optional callback to delete task when in edit mode.
 */
@Composable
fun ComposeBottomBar(
    isEditing: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassButton(
                text = "Cancel",
                onClick = onCancel,
                variant = GlassButtonVariant.SECONDARY,
                icon = Icons.Filled.Close,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            )

            if (isEditing && onDelete != null) {
                GlassButton(
                    text = "Delete",
                    onClick = onDelete,
                    variant = GlassButtonVariant.DANGER,
                    icon = Icons.Filled.DeleteOutline,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            GlassButton(
                text = if (isEditing) "Save Changes" else "Schedule Task",
                onClick = onSave,
                variant = GlassButtonVariant.PRIMARY,
                icon = if (isEditing) Icons.Filled.Check else Icons.Filled.Schedule,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(if (isEditing && onDelete != null) 1.5f else 2f)
            )
        }
    }
}
