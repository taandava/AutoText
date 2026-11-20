/**
 * Package: com.jmd.supersms.ui.screen.autoreply
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [AutoReplyScreen].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for AutoReplyScreen.
 *
 * Linked With:
 * - Repositories: [com.jmd.supersms.data.repo.*]
 * - Models & Entities: [com.jmd.supersms.data.model.*], [com.jmd.supersms.ui.model.*]
 * - UI Screens & Components: [androidx.activity.compose.BackHandler], [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.clip]
 * - Core Security & Utilities: [com.jmd.supersms.core.*]
 * - Android Framework & Jetpack: [androidx.compose.foundation.lazy.LazyColumn], [androidx.compose.foundation.lazy.items], [androidx.compose.foundation.shape.CircleShape], [androidx.compose.foundation.text.KeyboardOptions]
 */

package com.jmd.supersms.ui.screen.autoreply

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jmd.supersms.core.AppDataEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jmd.supersms.core.*
import com.jmd.supersms.data.model.*
import com.jmd.supersms.data.prefs.*
import com.jmd.supersms.data.repo.*
import com.jmd.supersms.ui.components.*
import com.jmd.supersms.ui.model.*
import com.jmd.supersms.ui.theme.*

/**
 * What it is:
 * Main scheduled reply rules configuration screen providing master toggles, call/message trigger rule list, dispatch pacing and frequency rate throttle, and rule editors.
 *
 * What it does:
 * Renders interactive Material 3 UI for [AutoReplyScreen] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [AutoReplyScreen], [AutoReplyScreen]
 * @param onEditingStateChanged Callback invoked when modal sheet or dialog opens/closes to hide bottom navigation.
 */
@Composable
fun AutoReplyScreen(
    onEditingStateChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val prefs = remember { PreferenceManager(context) }
    var isAutoReplyMasterEnabled by remember { mutableStateOf(prefs.isAutoReplyMasterEnabled) }
    var rules by remember { mutableStateOf(emptyList<AutoReplyRule>()) }
    var showDialog by remember { mutableStateOf(false) }
    var showTypeSelectionSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AutoReplyRule?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshRules() {
        scope.launch(Dispatchers.IO) {
            val r = repository.getAllAutoReplyRules()
            withContext(Dispatchers.Main) {
                rules = r
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshRules()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        refreshRules()
        AppDataEvents.autoReplyChanged.collect {
            refreshRules()
        }
    }

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
            AppCardElevated(
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
                    "Rules (${rules.size})",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                AppButton(
                    text = "Add Rule",
                    onClick = {
                        editingRule = null
                        showTypeSelectionSheet = true
                    },
                    variant = AppButtonVariant.PRIMARY,
                    icon = Icons.Filled.Add,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }

            val visibleRules = remember(rules) {
                rules.filter { com.jmd.supersms.core.AppVariantHelper.isChannelSupported(it.channel) }
            }

            // Rules List
            if (visibleRules.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppEmptyState(
                        icon = Icons.Filled.Quickreply,
                        title = "No Rules",
                        description = "Create quick reply rules for missed phone calls or incoming SMS.",
                        actionText = "New Rule",
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
                    items(visibleRules, key = { it.id }) { rule ->
                        AutoReplyRuleCard(
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
        AutoReplyTypeSelectionSheet(
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
        AutoReplyRuleDialog(
            initialRule = editingRule,
            onDismiss = { showDialog = false },
            onSave = { finalRule ->
                if (finalRule.id > 0L) {
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
