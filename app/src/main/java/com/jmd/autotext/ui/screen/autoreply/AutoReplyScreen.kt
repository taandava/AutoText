package com.jmd.autotext.ui.screen.autoreply

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jmd.autotext.core.*
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.prefs.*
import com.jmd.autotext.data.repo.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.model.*
import com.jmd.autotext.ui.theme.*

/**
 * Main automated reply rules configuration screen providing master toggles,
 * call/message trigger rule list, anti-spam rate limiting, and rule editors.
 *
 * @param onEditingStateChanged Callback invoked when modal sheet or dialog opens/closes to hide bottom navigation.
 */
@Composable
fun autoReplyScreen(
    onEditingStateChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val prefs = remember { PreferenceManager(context) }
    var isAutoReplyMasterEnabled by remember { mutableStateOf(prefs.isAutoReplyMasterEnabled) }
    var rules by remember { mutableStateOf(repository.getAllAutoReplyRules()) }
    var showDialog by remember { mutableStateOf(false) }
    var showTypeSelectionSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AutoReplyRule?>(null) }

    LaunchedEffect(showDialog || showTypeSelectionSheet) {
        onEditingStateChanged(showDialog || showTypeSelectionSheet)
    }

    BackHandler(enabled = showDialog || showTypeSelectionSheet) {
        if (showTypeSelectionSheet) {
            showTypeSelectionSheet = false
        } else {
            showDialog = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Master Auto-Reply Responder Banner
            appCardElevated(
                modifier = Modifier.fillMaxWidth(),
                shape = AppDesignTokens.ShapeMedium,
                accentGlow = MaterialTheme.colorScheme.tertiary
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                        Icon(
                            imageVector = Icons.Default.Quickreply,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                "Auto-Reply Responder",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (isAutoReplyMasterEnabled) "Active • Listening for incoming events"
                                else "Auto-Reply Engine Paused",
                                fontSize = 12.sp,
                                color = if (isAutoReplyMasterEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = isAutoReplyMasterEnabled,
                        onCheckedChange = { active ->
                            isAutoReplyMasterEnabled = active
                            prefs.isAutoReplyMasterEnabled = active
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.tertiary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                }
            }

            // Sub-header with Add Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Configured Rules (${rules.size})",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                glassButton(
                    text = "Add Rule",
                    onClick = {
                        editingRule = null
                        showTypeSelectionSheet = true
                    },
                    variant = GlassButtonVariant.PRIMARY,
                    emoji = "➕",
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }

            // Rules List
            if (rules.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    appEmptyState(
                        emoji = "🤖",
                        title = "No Auto-Reply Rules",
                        description = "Create automatic responder rules for missed phone calls, incoming SMS or WhatsApp chats.",
                        actionText = "Create Auto-Reply Rule",
                        onActionClick = {
                            editingRule = null
                            showTypeSelectionSheet = true
                        }
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 60.dp)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        autoReplyRuleCard(
                            rule = rule,
                            onToggle = { enable: Boolean ->
                                val updated = rule.copy(isEnabled = enable)
                                repository.updateAutoReplyRule(updated)
                                rules = repository.getAllAutoReplyRules()
                            },
                            onEdit = {
                                editingRule = rule
                                showDialog = true
                            },
                            onDelete = {
                                repository.deleteAutoReplyRule(rule.id)
                                rules = repository.getAllAutoReplyRules()
                            }
                        )
                    }
                }
            }
        }
    }

    // Bottom Sheet: Select Auto-Reply Responder Type
    if (showTypeSelectionSheet) {
        autoReplyTypeSelectionSheet(
            onDismiss = { showTypeSelectionSheet = false },
            onSelectType = { channel, defaultEvent ->
                showTypeSelectionSheet = false
                editingRule = AutoReplyRule(
                    name = "",
                    channel = channel,
                    callEventType = defaultEvent,
                    replyText = "",
                    isEnabled = true
                )
                showDialog = true
            }
        )
    }

    // Full-Screen Create / Edit Rule View
    if (showDialog) {
        autoReplyRuleDialog(
            initialRule = editingRule,
            onDismiss = { showDialog = false },
            onSave = { finalRule ->
                if (editingRule != null) {
                    repository.updateAutoReplyRule(finalRule)
                } else {
                    repository.insertAutoReplyRule(finalRule)
                }
                rules = repository.getAllAutoReplyRules()
                showDialog = false
            }
        )
    }
}

