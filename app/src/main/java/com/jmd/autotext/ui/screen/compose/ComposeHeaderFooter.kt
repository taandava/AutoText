package com.jmd.autotext.ui.screen.compose

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
import com.jmd.autotext.data.model.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*

/**
 * Top bar header rendering task title, mode indicator, channel badge, and close button.
 * @param selectedChannel Channel configuration active for this compose session.
 * @param taskToEdit Existing task model if editing; null if drafting a new item.
 * @param onCancel Callback invoked to dismiss draft.
 * @param modifier Optional layout modifier.
 */
@Composable
fun composeTopHeader(
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
                    Text("❌", fontSize = 16.sp)
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

            val channelColor = when (selectedChannel) {
                ChannelType.SMS -> MaterialTheme.colorScheme.primary
                ChannelType.WHATSAPP, ChannelType.WHATSAPP_STATUS, ChannelType.WHATSAPP_CALL -> MaterialTheme.colorScheme.tertiary
                ChannelType.WHATSAPP_BUSINESS, ChannelType.WHATSAPP_BUSINESS_STATUS, ChannelType.WHATSAPP_BUSINESS_CALL -> MaterialTheme.colorScheme.secondary
                ChannelType.CALL, ChannelType.REMINDER -> MaterialTheme.colorScheme.primary
                ChannelType.FAKE_CALL -> MaterialTheme.colorScheme.error
                ChannelType.SOUND_MODE -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primary
            }
            appBadge(
                text = selectedChannel.displayName,
                emoji = selectedChannel.emoji,
                color = channelColor
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
fun composeBottomBar(
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
            glassButton(
                text = "Cancel",
                onClick = onCancel,
                variant = GlassButtonVariant.SECONDARY,
                emoji = "❌",
                modifier = Modifier.weight(1f)
            )

            if (isEditing && onDelete != null) {
                glassButton(
                    text = "Delete",
                    onClick = onDelete,
                    variant = GlassButtonVariant.DANGER,
                    emoji = "🗑️",
                    modifier = Modifier.weight(1.1f)
                )
            }

            glassButton(
                text = if (isEditing) "Save Changes" else "Schedule Task",
                onClick = onSave,
                variant = GlassButtonVariant.PRIMARY,
                emoji = if (isEditing) "✅" else "⏰",
                modifier = Modifier.weight(1.8f)
            )
        }
    }
}


/**
 * Configuration card managing caller simulation parameters for Fake Calls and system ringer sound profiles.
 *
 * @param selectedChannel Active channel type (FAKE_CALL or SOUND_MODE).
 * @param fakeCallerName Simulated caller contact name.
 * @param onFakeCallerNameChange Callback when simulated name changes.
 * @param fakeCallerNumber Simulated incoming phone number.
 * @param onFakeCallerNumberChange Callback when simulated number changes.
 * @param fakeCallStyle Visual UI style for incoming call bouncer.
 * @param onFakeCallStyleChange Callback when call screen theme changes.
 * @param soundModeSetting Target sound profile (NORMAL, VIBRATE, SILENT).
 * @param onSoundModeSettingChange Callback when target sound mode is changed.
 */
