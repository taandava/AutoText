package com.jmd.autotext.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.AutoReplyRule
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.screens.autoreply.BentoAutoReplyCard
import com.jmd.autotext.ui.screens.autoreply.AutoReplyRuleDialog
import com.jmd.autotext.ui.theme.BentoGlassTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AutoReplyScreen(
    onEditingStateChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val prefs = remember { com.jmd.autotext.data.PreferenceManager(context) }
    var isAutoReplyMasterEnabled by remember { mutableStateOf(prefs.isAutoReplyMasterEnabled) }
    var rules by remember { mutableStateOf(repository.getAllAutoReplyRules()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AutoReplyRule?>(null) }

    LaunchedEffect(showDialog) {
        onEditingStateChanged(showDialog)
    }

    androidx.activity.compose.BackHandler(enabled = showDialog) {
        showDialog = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoGlassTokens.GlassBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Master Auto-Reply Responder Banner
            BentoCardElevated(
                modifier = Modifier.fillMaxWidth(),
                shape = BentoGlassTokens.ShapeMedium,
                accentGlow = BentoGlassTokens.VividEmerald
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Quickreply,
                            contentDescription = null,
                            tint = BentoGlassTokens.VividEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Auto-Reply Responder",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoGlassTokens.TextPrimary
                            )
                            Text(
                                if (isAutoReplyMasterEnabled) "Active • Listening for incoming events"
                                else "Auto-Reply Engine Paused",
                                fontSize = 12.5.sp,
                                color = if (isAutoReplyMasterEnabled) BentoGlassTokens.VividEmerald else BentoGlassTokens.TextMuted
                            )
                        }
                    }

                    Switch(
                        checked = isAutoReplyMasterEnabled,
                        onCheckedChange = { active ->
                            isAutoReplyMasterEnabled = active
                            prefs.isAutoReplyMasterEnabled = active
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BentoGlassTokens.VividEmerald,
                            uncheckedThumbColor = BentoGlassTokens.TextMuted,
                            uncheckedTrackColor = BentoGlassTokens.GlassSurfaceElevated
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
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoGlassTokens.TextPrimary
                )

                GlassButton(
                    text = "Add Rule",
                    onClick = {
                        editingRule = null
                        showDialog = true
                    },
                    variant = GlassButtonVariant.PRIMARY,
                    icon = Icons.Default.Add,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    shape = BentoGlassTokens.ShapePill
                )
            }

            // Rules List
            if (rules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BentoEmptyState(
                        icon = Icons.Default.Quickreply,
                        title = "No Auto-Reply Rules",
                        description = "Create automatic responder rules for missed phone calls, incoming SMS or WhatsApp chats.",
                        actionText = "Create Missed Call Rule",
                        onActionClick = {
                            editingRule = null
                            showDialog = true
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
                        BentoAutoReplyCard(
                            rule = rule,
                            onToggle = { enable ->
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

    // Full-Screen Create / Edit Rule View
    if (showDialog) {
        AutoReplyRuleDialog(
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