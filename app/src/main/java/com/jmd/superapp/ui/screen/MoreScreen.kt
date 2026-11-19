/**
 * Package: com.jmd.superapp.ui.screen
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [MoreScreen].
 *
 * What it does:
 * Hub screen navigating to secondary features: Settings, Documentation, Call Simulation, Auto-Reply, and System Diagnostics.
 *
 * Linked With:
 * - Repositories: [com.jmd.superapp.data.repo.*]
 * - UI Screens & Components: [androidx.activity.compose.BackHandler], [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.clip]
 * - Android Framework & Jetpack: [androidx.compose.foundation.lazy.LazyColumn], [androidx.compose.foundation.shape.RoundedCornerShape], [androidx.compose.material.icons.Icons]
 */

package com.jmd.superapp.ui.screen

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

import com.jmd.superapp.data.prefs.*
import com.jmd.superapp.data.repo.*
import com.jmd.superapp.ui.screen.logs.LogsView
import com.jmd.superapp.ui.screen.settings.SettingsView
import com.jmd.superapp.ui.screen.settings.SystemLogViewerView
import com.jmd.superapp.ui.theme.*

/**
 * What it is:
 * Hub navigation screen providing access to tools, diagnostic ledgers, settings, in-app documentation, and secondary configuration views.
 *
 * What it does:
 * Renders interactive Material 3 UI for [MoreScreenView] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [MoreScreen], [MoreScreenView]
 * @param initialSubSection Child sub-section route to navigate to immediately on render.
 * @param onSubSectionStateChanged Callback signaling whether a child sub-section is active (hiding bottom navigation if true).
 * @param onThemeChanged Callback invoked when user alters theme palette preference.
 */
@Composable
fun MoreScreenView(
    initialSubSection: String = "MENU",
    onSubSectionStateChanged: (Boolean) -> Unit = {},
    onThemeChanged: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val prefs = remember { PreferenceManager(context) }

    var selectedSubSection by remember(initialSubSection) { mutableStateOf(initialSubSection) }
    var previousSubSection by remember { mutableStateOf("MENU") }
    /**
     * What it is:
     * Execution routine for [navigateToSubSection].
     *
     * What it does:
     * Coordinates navigateToSubSection operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [MoreScreen]
     */

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
            else -> "MENU"
        }
        navigateToSubSection(next)
    }

    when (selectedSubSection) {
        "LOGS" -> LogsView(
            repository,
            onNavigateToSystemLogs = {
                previousSubSection = "LOGS"
                navigateToSubSection("SYSTEM_LOGS")
            },
        ) { navigateToSubSection("MENU") }
        "SYSTEM_LOGS" -> SystemLogViewerView(context) {
            if (previousSubSection == "LOGS") {
                previousSubSection = "MENU"
                navigateToSubSection("LOGS")
            } else {
                navigateToSubSection("MENU")
            }
        }
        "PERMISSIONS" -> PermissionCenterView(context) { navigateToSubSection("MENU") }
        "CALL_SIMULATION" -> {
            LaunchedEffect(Unit) {
                com.jmd.superapp.ui.activity.CallSimulationActivity.start(
                    context = context,
                    callerName = "Boss / Office",
                    callerNumber = "+91 98765 43210",
                    callerStyle = "PIXEL"
                )
                navigateToSubSection("MENU")
            }
        }
        "SETTINGS" -> SettingsView(
            context = context,
            repository = repository,
            prefs = prefs,
            onThemeChanged = onThemeChanged
        ) { navigateToSubSection("MENU") }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // SECTION 1: System, Security & Controls
                item {
                    Text(
                        text = "System",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
                item {
                    MenuItemCard(
                        "Permission Center",
                        Icons.Filled.Security,
                        MaterialTheme.colorScheme.primary
                    ) {
                        navigateToSubSection("PERMISSIONS")
                    }
                }
                item {
                    MenuItemCard(
                        "Settings",
                        Icons.Filled.Settings,
                        Color(0xFF64748B)
                    ) {
                        navigateToSubSection("SETTINGS")
                    }
                }
                if (com.jmd.superapp.core.AppVariantHelper.isSuperSms) {
                    item {
                        MenuItemCard(
                            "Call Simulation",
                            Icons.Filled.PhoneInTalk,
                            Color(0xFFE91E63)
                        ) {
                            com.jmd.superapp.ui.activity.CallSimulationActivity.start(
                                context = context,
                                callerName = "Boss / Office",
                                callerNumber = "+91 98765 43210",
                                callerStyle = "PIXEL"
                            )
                        }
                    }
                }

                // SECTION 2: Rules & Execution History
                item {
                    Text(
                        text = "History",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
                    )
                }
                item {
                    MenuItemCard(
                        "Execution History",
                        Icons.Filled.History,
                        Color(0xFFF57C00),
                    ) {
                        navigateToSubSection("LOGS")
                    }
                }
                item {
                    MenuItemCard(
                        "System Logs",
                        Icons.Filled.Terminal,
                        Color(0xFF6366F1)
                    ) {
                        navigateToSubSection("SYSTEM_LOGS")
                    }
                }
            }
        }
    }
}


/**
 * What it is:
 * Jetpack Compose UI component for [MenuItemCard].
 *
 * What it does:
 * Renders Material 3 interface elements and handles user interaction for MenuItemCard.
 *
 * Linked with:
 * - Component: [MoreScreen]
 */
@Composable
fun MenuItemCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        tonalElevation = 0.dp
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
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
