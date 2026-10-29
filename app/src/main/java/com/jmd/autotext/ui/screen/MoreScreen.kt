

/**
 * Package: com.jmd.autotext.ui.screen
 * Module: UI More / Navigation Screen Layer
 *
 * Hub screen navigating to secondary features: Settings, Documentation, Fake Call,
 * Templates, Auto-Reply, and System Diagnostics.
 *
 * Linked With:
 * - Host: `MainAppHost`
 * - Components: `GlassComponentsKt`
 */
package com.jmd.autotext.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jmd.autotext.data.prefs.*
import com.jmd.autotext.data.repo.*
import com.jmd.autotext.ui.screen.campaign.dailyAutoSenderScreen
import com.jmd.autotext.ui.screen.contacts.bulkContactsView
import com.jmd.autotext.ui.screen.logs.logsView
import com.jmd.autotext.ui.screen.sequence.contactSequenceStudio
import com.jmd.autotext.ui.screen.settings.*
import com.jmd.autotext.ui.screen.templates.templatesView
import com.jmd.autotext.ui.theme.*

/**
 * Hub navigation screen providing access to tools, diagnostic ledgers, settings,
 * message templates, in-app documentation, and secondary configuration views.
 *
 * @param onSubSectionStateChanged Callback signaling whether a child sub-section is active (hiding bottom navigation if true).
 * @param onThemeChanged Callback invoked when user alters theme palette preference.
 */
@Composable
fun moreScreen(
    onSubSectionStateChanged: (Boolean) -> Unit = {},
    onThemeChanged: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val prefs = remember { PreferenceManager(context) }

    var selectedSubSection by remember { mutableStateOf("MENU") }
    var previousSubSection by remember { mutableStateOf("MENU") }

    fun navigateToSubSection(target: String) {
        selectedSubSection = target
        onSubSectionStateChanged(target != "MENU")
    }

    LaunchedEffect(selectedSubSection) {
        onSubSectionStateChanged(selectedSubSection != "MENU")
    }

    BackHandler(enabled = selectedSubSection != "MENU") {
        val next = when (selectedSubSection) {
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
        navigateToSubSection(next)
    }

    when (selectedSubSection) {
        "DAILY_SMS" -> dailyAutoSenderScreen(initialCampaignId = 1L, onBack = { navigateToSubSection("MENU") })
        "DAILY_WHATSAPP" -> dailyAutoSenderScreen(initialCampaignId = 2L, onBack = { navigateToSubSection("MENU") })
        "WHATSAPP_SETUP" -> whatsAppSetupAndAutoUnlockView(context, prefs) { navigateToSubSection("MENU") }
        "SEQUENCES" -> contactSequenceStudio(onDismiss = { navigateToSubSection("MENU") }) { navigateToSubSection("MENU") }
        "TEMPLATES" -> templatesView(repository) { navigateToSubSection("MENU") }
        "BULK_CONTACTS" -> bulkContactsView(repository) { navigateToSubSection("MENU") }
        "LOGS" -> logsView(
            repository,
            onNavigateToSystemLogs = {
                previousSubSection = "LOGS"
                navigateToSubSection("SYSTEM_LOGS")
            },
        ) { navigateToSubSection("MENU") }
        "SYSTEM_LOGS" -> systemLogViewerView(context) {
            if (previousSubSection == "LOGS") {
                previousSubSection = "MENU"
                navigateToSubSection("LOGS")
            } else {
                navigateToSubSection("MENU")
            }
        }
        "PERMISSIONS" -> permissionCenterView(context) { navigateToSubSection("MENU") }
        "SETTINGS" -> settingsView(
            context = context,
            repository = repository,
            prefs = prefs,
            onThemeChanged = onThemeChanged,
            onOpenDocumentation = {
                previousSubSection = "SETTINGS"
                navigateToSubSection("DOCS")
            },
        ) { navigateToSubSection("MENU") }
        "DOCS" -> documentationView(context) {
            if (previousSubSection == "SETTINGS") {
                previousSubSection = "MENU"
                navigateToSubSection("SETTINGS")
            } else {
                navigateToSubSection("MENU")
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // SECTION 1: Campaigns & Automations
                item {
                    Text(
                        text = "CAMPAIGNS & DISPATCH",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
                item {
                    menuItemCard(
                        "Daily SMS Automation",
                        "📩",
                        MaterialTheme.colorScheme.primary,
                    ) {
                        navigateToSubSection("DAILY_SMS")
                    }
                }
                item {
                    menuItemCard(
                        "Daily WhatsApp Automation",
                        "💬",
                        Color(0xFF2E7D32),
                    ) {
                        navigateToSubSection("DAILY_WHATSAPP")
                    }
                }
                item {
                    menuItemCard(
                        "WhatsApp & Auto-Unlock",
                        "📱",
                        Color(0xFF0D9488),
                    ) {
                        navigateToSubSection("WHATSAPP_SETUP")
                    }
                }
                item {
                    menuItemCard(
                        "Task Sequences",
                        "🔗",
                        Color(0xFFF57C00),
                    ) {
                        navigateToSubSection("SEQUENCES")
                    }
                }
                item {
                    menuItemCard(
                        "Message Templates",
                        "📝",
                        Color(0xFF8B5CF6),
                    ) {
                        navigateToSubSection("TEMPLATES")
                    }
                }
                item {
                    menuItemCard(
                        "Bulk Contacts & Groups",
                        "👥",
                        Color(0xFF2E7D32),
                    ) {
                        navigateToSubSection("BULK_CONTACTS")
                    }
                }

                // SECTION 2: Rules & Execution History
                item {
                    Text(
                        text = "ROUTING & AUDIT HISTORY",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
                    )
                }
                item {
                    menuItemCard(
                        "Execution History",
                        "📜",
                        Color(0xFFF57C00),
                    ) {
                        navigateToSubSection("LOGS")
                    }
                }
                item {
                    menuItemCard(
                        "System Traces & Error Logs",
                        "💻",
                        Color(0xFFF57C00)
                    ) {
                        navigateToSubSection("SYSTEM_LOGS")
                    }
                }

                // SECTION 3: System, Security & Setup
                item {
                    Text(
                        text = "SYSTEM & PREFERENCES",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
                    )
                }
                item {
                    menuItemCard(
                        "Permission Center",
                        "🛡️",
                        MaterialTheme.colorScheme.primary
                    ) {
                        navigateToSubSection("PERMISSIONS")
                    }
                }
                item {
                    menuItemCard(
                        "Settings & Security",
                        "⚙️",
                        Color(0xFF64748B)
                    ) {
                        navigateToSubSection("SETTINGS")
                    }
                }
                item {
                    menuItemCard(
                        "Documentation Guide",
                        "📖",
                        Color(0xFF0D9488)
                    ) {
                        navigateToSubSection("DOCS")
                    }
                }
            }
        }
    }
}


@Composable
fun menuItemCard(
    title: String,
    emoji: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.14f))
                    .border(1.dp, iconColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
