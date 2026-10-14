package com.jmd.autotext.ui.screens

import com.jmd.autotext.ui.screens.more.*

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.data.PreferenceManager
import android.content.ClipData
import android.content.ClipboardManager
import com.jmd.autotext.model.*
import com.jmd.autotext.sender.SmsSender
import com.jmd.autotext.sender.WhatsAppAutomationSender
import com.jmd.autotext.service.AccessibilityHealthManager
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.service.AutoTextDeviceAdminReceiver
import com.jmd.autotext.util.OverlayPermissionHelper
import android.app.AlarmManager
import android.os.PowerManager
import com.jmd.autotext.ui.theme.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.jmd.autotext.util.AppFileLogger
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import java.util.*

@Composable
fun MoreScreen(
    onNavigateToDailySms: () -> Unit = {},
    onSubSectionStateChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val prefs = remember { PreferenceManager(context) }

    var selectedSubSection by remember { mutableStateOf("MENU") }
    var previousSubSection by remember { mutableStateOf("MENU") }

    LaunchedEffect(selectedSubSection) {
        onSubSectionStateChanged(selectedSubSection != "MENU")
    }

    androidx.activity.compose.BackHandler(enabled = selectedSubSection != "MENU") {
        selectedSubSection = when (selectedSubSection) {
            "SYSTEM_LOGS" -> if (previousSubSection == "LOGS") {
                previousSubSection = "MENU"
                "LOGS"
            } else "MENU"
            "DOCS" -> if (previousSubSection == "SETTINGS") {
                previousSubSection = "MENU"
                "SETTINGS"
            } else "MENU"
            else -> "MENU"
        }
    }

    when (selectedSubSection) {
        "WHATSAPP_SETUP" -> WhatsAppSetupAndAutoUnlockView(context, prefs) { selectedSubSection = "MENU" }
        "SEQUENCES" -> ContactSequenceStudio(onDismiss = { selectedSubSection = "MENU" }, onSaved = { selectedSubSection = "MENU" })
        "TEMPLATES" -> TemplatesView(repository) { selectedSubSection = "MENU" }
        "BULK_CONTACTS" -> BulkContactsView(repository) { selectedSubSection = "MENU" }
        "FORWARDING" -> ForwardingView(repository) { selectedSubSection = "MENU" }
        "LOGS" -> LogsView(repository, onNavigateToSystemLogs = { 
            previousSubSection = "LOGS"
            selectedSubSection = "SYSTEM_LOGS" 
        }) { selectedSubSection = "MENU" }
        "SYSTEM_LOGS" -> SystemLogViewerView(context) { 
            if (previousSubSection == "LOGS") {
                selectedSubSection = "LOGS"
                previousSubSection = "MENU"
            } else {
                selectedSubSection = "MENU"
            }
        }
        "PERMISSIONS" -> PermissionCenterView(context) { selectedSubSection = "MENU" }
        "SETTINGS" -> SettingsView(
            context = context,
            repository = repository,
            prefs = prefs,
            onOpenDocumentation = {
                previousSubSection = "SETTINGS"
                selectedSubSection = "DOCS"
            }
        ) { selectedSubSection = "MENU" }
        "DOCS" -> DocumentationView(context) {
            if (previousSubSection == "SETTINGS") {
                selectedSubSection = "SETTINGS"
                previousSubSection = "MENU"
            } else {
                selectedSubSection = "MENU"
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BentoGlassTokens.VoidBackground)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                item {
                    MenuItemCard(
                        "WhatsApp & Screen Auto-Unlock",
                        "Fix WhatsApp sending, PIN auto-unlock while phone is locked, and auto-lock after send",
                        Icons.Default.PhoneAndroid,
                        BentoGlassTokens.VividEmerald
                    ) {
                        selectedSubSection = "WHATSAPP_SETUP"
                    }
                }

                item {
                    MenuItemCard(
                        "Daily SMS & WhatsApp Campaigns",
                        "Auto-ingests contacts from phone, SMS & call log and sends daily automated SMS and WhatsApp batches",
                        Icons.AutoMirrored.Filled.ScheduleSend,
                        BentoGlassTokens.NeonCyan
                    ) {
                        onNavigateToDailySms()
                    }
                }

                item {
                    MenuItemCard(
                        "Contact Task Sequences",
                        "Create multi-step scheduled messages per contact per number across SMS & WhatsApp with intervals",
                        Icons.AutoMirrored.Filled.AltRoute,
                        BentoGlassTokens.NeonCyan
                    ) {
                        selectedSubSection = "SEQUENCES"
                    }
                }

                item {
                    MenuItemCard("Message Templates", "Pre-written business, meeting & greeting messages", Icons.Default.FormatQuote, BentoGlassTokens.ElectricIndigo) {
                        selectedSubSection = "TEMPLATES"
                    }
                }

                item {
                    MenuItemCard("Bulk Contacts & Groups", "Manage contact lists and paste numbers", Icons.Default.Group, BentoGlassTokens.VividEmerald) {
                        selectedSubSection = "BULK_CONTACTS"
                    }
                }

                item {
                    MenuItemCard("Auto SMS & Call Forwarding", "Forward incoming SMS & missed call alerts to another number", Icons.AutoMirrored.Filled.ForwardToInbox, BentoGlassTokens.NeonCyan) {
                        selectedSubSection = "FORWARDING"
                    }
                }

                item {
                    MenuItemCard("Execution History", "Sent, auto-replied and scheduled message logs with status icons", Icons.Default.History, BentoGlassTokens.RadiantAmber) {
                        selectedSubSection = "LOGS"
                    }
                }

                item {
                    MenuItemCard("Campaign Ledger & Complete System Logs", "Campaign audit ledger (CSV), all device actions, errors, crashes & execution traces", Icons.Default.Terminal, BentoGlassTokens.RadiantAmber) {
                        selectedSubSection = "SYSTEM_LOGS"
                    }
                }

                item {
                    MenuItemCard("Permission Center & Troubleshooting", "Accessibility, restricted settings, notification & battery settings", Icons.Default.Security, BentoGlassTokens.ElectricIndigo) {
                        selectedSubSection = "PERMISSIONS"
                    }
                }

                item {
                    MenuItemCard("Settings, Auto-Lock & Backup", "Lockscreen PIN, country code, SIM preference, and JSON backup", Icons.Default.Settings, BentoGlassTokens.TextMuted) {
                        selectedSubSection = "SETTINGS"
                    }
                }

                item {
                    MenuItemCard("Technical Manual & Logic Guide", "Complete offline architecture, 100 SMS limits, auto-unlock, and feature specs", Icons.AutoMirrored.Filled.MenuBook, AccentCyan) {
                        selectedSubSection = "DOCS"
                    }
                }
            }
        }
    }
}

@Composable
fun MenuItemCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    BentoCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeSmall,
        backgroundColor = BentoGlassTokens.GlassSurfaceElevated
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 12.5.sp, maxLines = 1)
                Spacer(modifier = Modifier.height(1.dp))
                Text(subtitle, fontSize = 10.sp, color = BentoGlassTokens.TextMuted, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BentoGlassTokens.TextMuted, modifier = Modifier.size(15.dp))
        }
    }
}

