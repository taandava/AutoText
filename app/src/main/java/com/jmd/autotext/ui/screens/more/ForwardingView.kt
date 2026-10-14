package com.jmd.autotext.ui.screens.more

import com.jmd.autotext.ui.screens.*

import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.model.*
import com.jmd.autotext.sender.SmsSender
import com.jmd.autotext.sender.WhatsAppAutomationSender
import com.jmd.autotext.service.AccessibilityHealthManager
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.service.AutoTextDeviceAdminReceiver
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*
import com.jmd.autotext.util.AppFileLogger
import com.jmd.autotext.util.OverlayPermissionHelper
import java.text.SimpleDateFormat
import java.util.*

// ----------------- SUB SCREEN: AUTO FORWARD -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardingView(repository: AppRepository, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { com.jmd.autotext.data.PreferenceManager(context) }
    var isAutoForwardMasterEnabled by remember { mutableStateOf(prefs.isAutoForwardMasterEnabled) }
    var rules by remember { mutableStateOf(repository.getAllForwardRules()) }
    var isAddingRule by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AutoForwardRule?>(null) }
    var forwardToNumber by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf("SMS") }

    androidx.activity.compose.BackHandler(enabled = isAddingRule) {
        isAddingRule = false
        editingRule = null
    }

    if (isAddingRule) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (editingRule != null) "Edit Forward Rule" else "New Forward Rule", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = {
                            isAddingRule = false
                            editingRule = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BentoGlassTokens.VoidBackground)
                )
            },
            containerColor = BentoGlassTokens.VoidBackground
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Trigger Event", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SMS" to "SMS Messages", "MISSED_CALL" to "Missed Calls").forEach { (st, label) ->
                            GlassChip(
                                selected = sourceType == st,
                                onClick = { sourceType = st },
                                label = label,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    GlassInputField(
                        value = forwardToNumber,
                        onValueChange = { forwardToNumber = it },
                        label = "Forward Target Phone Number",
                        placeholder = "e.g. +919876543210",
                        leadingIcon = Icons.AutoMirrored.Filled.ForwardToInbox,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        accentColor = BentoGlassTokens.NeonCyan
                    )
                }

                GlassButton(
                    text = if (editingRule != null) "Update Forward Rule" else "Save Forward Rule",
                    onClick = {
                        if (forwardToNumber.isNotBlank()) {
                            if (editingRule != null) {
                                repository.updateForwardRule(editingRule!!.copy(sourceType = sourceType, forwardToNumber = forwardToNumber.trim()))
                            } else {
                                repository.insertForwardRule(AutoForwardRule(name = "Forward Rule", sourceType = sourceType, forwardToNumber = forwardToNumber.trim()))
                            }
                            rules = repository.getAllForwardRules()
                            forwardToNumber = ""
                            editingRule = null
                            isAddingRule = false
                        }
                    },
                    icon = Icons.Default.Check,
                    variant = GlassButtonVariant.PRIMARY,
                    enabled = forwardToNumber.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Auto Forwarding", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BentoGlassTokens.VoidBackground)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingRule = null
                        sourceType = "SMS"
                        forwardToNumber = ""
                        isAddingRule = true
                    },
                    containerColor = BentoGlassTokens.NeonCyan,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Forward Rule")
                }
            },
            containerColor = BentoGlassTokens.VoidBackground
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Master Auto-Forwarding Engine Banner
                BentoCardElevated(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BentoGlassTokens.ShapeMedium,
                    accentGlow = BentoGlassTokens.NeonCyan
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ForwardToInbox,
                                contentDescription = null,
                                tint = BentoGlassTokens.NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Auto-Forwarding Engine", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 13.5.sp)
                                Text(
                                    if (isAutoForwardMasterEnabled) "Active • Forwarding incoming alerts"
                                    else "Auto-Forwarding Engine Paused",
                                    fontSize = 10.5.sp,
                                    color = if (isAutoForwardMasterEnabled) BentoGlassTokens.NeonCyan else BentoGlassTokens.TextMuted
                                )
                            }
                        }

                        Switch(
                            checked = isAutoForwardMasterEnabled,
                            onCheckedChange = { active ->
                                isAutoForwardMasterEnabled = active
                                prefs.isAutoForwardMasterEnabled = active
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BentoGlassTokens.NeonCyan,
                                uncheckedThumbColor = BentoGlassTokens.TextMuted,
                                uncheckedTrackColor = BentoGlassTokens.GlassSurfaceElevated
                            )
                        )
                    }
                }

                if (rules.isEmpty()) {
                    BentoEmptyState(
                        title = "No Forward Rules",
                        description = "Forward incoming SMS or missed calls automatically to another phone number.",
                        icon = Icons.AutoMirrored.Filled.ForwardToInbox,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rules, key = { it.id }) { r ->
                            BentoCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    editingRule = r
                                    sourceType = r.sourceType
                                    forwardToNumber = r.forwardToNumber
                                    isAddingRule = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${if (r.sourceType == "MISSED_CALL") "Missed Call" else "SMS"} Forward Rule", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 13.5.sp)
                                        Text("Forward to: ${r.forwardToNumber}", color = BentoGlassTokens.NeonCyan, fontSize = 12.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = r.isEnabled,
                                            onCheckedChange = {
                                                repository.toggleForwardRule(r.id, it)
                                                rules = repository.getAllForwardRules()
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = BentoGlassTokens.NeonCyan,
                                                checkedTrackColor = BentoGlassTokens.NeonCyan.copy(alpha = 0.35f)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = {
                                                editingRule = r
                                                sourceType = r.sourceType
                                                forwardToNumber = r.forwardToNumber
                                                isAddingRule = true
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Rule", tint = BentoGlassTokens.NeonCyan, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        IconButton(
                                            onClick = {
                                                repository.deleteForwardRule(r.id)
                                                rules = repository.getAllForwardRules()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = BentoGlassTokens.CyberRose, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
