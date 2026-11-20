/**
 * Package: com.jmd.supersms.ui.screen.settings
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [SettingsScreen].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for SettingsScreen.
 *
 * Linked With:
 * - Repositories: [com.jmd.supersms.data.repo.AppRepository]
 * - UI Screens & Components: [androidx.activity.compose.rememberLauncherForActivityResult], [androidx.activity.result.contract.ActivityResultContracts], [androidx.compose.ui.Modifier], [androidx.compose.ui.graphics.vector.ImageVector]
 * - Core Security & Utilities: [com.jmd.supersms.core.showAppToast], [com.jmd.supersms.data.prefs.PreferenceManager]
 * - Android Framework & Jetpack: [android.net.Uri], [androidx.compose.foundation.horizontalScroll], [androidx.compose.foundation.lazy.LazyColumn], [androidx.compose.foundation.rememberScrollState]
 */

package com.jmd.supersms.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.supersms.core.showAppToast
import com.jmd.supersms.data.prefs.PreferenceManager
import com.jmd.supersms.data.repo.AppRepository
import com.jmd.supersms.ui.components.AppSubScreenHeader
import com.jmd.supersms.ui.theme.AppDesignTokens

/**
 * What it is:
 * Functional subdivisions dividing the comprehensive application settings into focused tabs.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [SettingsSubdivision].
 *
 * Linked with:
 * - Architecture: [SettingsScreen], [SettingsSubdivision]
 */
enum class SettingsSubdivision(
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    FORMATTING("Formatting", Icons.Filled.TextFields, "Variables"),
    CHANNELS("Channels", Icons.Filled.Bolt, "Channels"),
    SECURITY("Security", Icons.Filled.Shield, "Security"),
    THEME_UI("Theme", Icons.Filled.Palette, "Theme"),
    SYSTEM("System", Icons.Filled.Build, "System")
}

/**
 * What it is:
 * Application settings and operational configuration screen providing controls for variable formatting, dispatch channels, banking guard, theme styling, system diagnostics, and database backup/restore.
 *
 * What it does:
 * Renders interactive Material 3 UI for [SettingsView] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [SettingsScreen], [SettingsView]
 * @param context Android context for resolving system services and content resolvers.
 * @param repository Application data repository for database serialization and migrations.
 * @param prefs SharedPreferences manager storing persistent user configuration.
 * @param onThemeChanged Callback triggered when user switches between Light, Dark, or System theme.
 * @param onBack Callback triggered when user navigates back.
 */
@Composable
fun SettingsView(
    context: Context,
    repository: AppRepository,
    prefs: PreferenceManager,
    onThemeChanged: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val availableSubdivisions = remember {
        if (com.jmd.supersms.core.AppVariantHelper.isSuperWa) {
            SettingsSubdivision.entries
        } else {
            SettingsSubdivision.entries.filter { it != SettingsSubdivision.SECURITY }
        }
    }
    var selectedSubdivision by remember { mutableStateOf(SettingsSubdivision.FORMATTING) }
    var restoreSummaryMsg by remember { mutableStateOf<String?>(null) }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = repository.exportDatabaseToJson()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.bufferedWriter().use { it.write(json) }
                }
                context.showAppToast("Backup saved")
            } catch (e: Exception) {
                context.showAppToast("Error saving backup file: ${e.message}", isLong = true)
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                }
                if (!content.isNullOrBlank()) {
                    val result = repository.importDatabaseFromJson(content.trim())
                    if (result.isSuccess) {
                        restoreSummaryMsg = result.getOrNull() ?: "Database restored successfully"
                        com.jmd.supersms.scheduler.AlarmScheduler(context).rescheduleAllPending()
                        com.jmd.supersms.scheduler.DailyCampaignScheduler.scheduleAll(context)
                        context.showAppToast("Backup restored")
                    } else {
                        context.showAppToast("Restore failed: ${result.exceptionOrNull()?.message}", isLong = true)
                    }
                }
            } catch (e: Exception) {
                context.showAppToast("Failed opening backup file: ${e.message}", isLong = true)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppSubScreenHeader(
                title = "Settings",
                subtitle = selectedSubdivision.description,
                onBack = onBack
            )
        },
        containerColor = AppDesignTokens.VoidBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Equal-Width Responsive 5-Card Navigation Header (Zero Sliding)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val isNarrow = maxWidth < 340.dp
                val cardGap = if (isNarrow) 4.dp else 6.dp
                val iconSize = if (isNarrow) 16.dp else 18.dp
                val fontSize = if (isNarrow) 9.5.sp else 10.5.sp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cardGap)
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
                    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
                    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainerHigh
                    val unselectedBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    val unselectedContent = MaterialTheme.colorScheme.onSurfaceVariant

                    availableSubdivisions.forEach { sub ->
                        val isSelected = selectedSubdivision == sub
                        Surface(
                            onClick = { selectedSubdivision = sub },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = AppDesignTokens.ShapeSmall,
                            color = if (isSelected) primaryContainer else surfaceContainer,
                            border = BorderStroke(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) primaryColor else unselectedBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 2.dp, vertical = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = sub.icon,
                                    contentDescription = sub.title,
                                    tint = if (isSelected) primaryColor else unselectedContent,
                                    modifier = Modifier.size(iconSize)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = sub.title,
                                    fontSize = fontSize,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) onPrimaryContainer else unselectedContent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                )
            ) {
                when (selectedSubdivision) {
                    SettingsSubdivision.FORMATTING -> {
                        item {
                            SettingsFormattingCard(
                                prefs = prefs
                            )
                        }
                    }
                    SettingsSubdivision.CHANNELS -> {
                        item {
                            SettingsChannelsCard(
                                context = context,
                                prefs = prefs
                            )
                        }
                    }
                    SettingsSubdivision.SECURITY -> {
                        // Not present in SuperSMS
                    }
                    SettingsSubdivision.THEME_UI -> {
                        item {
                            SettingsPreferencesCards(
                                prefs = prefs,
                                onThemeChanged = onThemeChanged
                            )
                        }
                    }
                    SettingsSubdivision.SYSTEM -> {
                        item {
                            SettingsStatusIndicatorsGuideCard(
                                context = context,
                                prefs = prefs
                            )
                        }
                        item {
                            SettingsBackupRestoreCard(
                                context = context,
                                repository = repository,
                                exportFileLauncher = exportFileLauncher,
                                filePickerLauncher = filePickerLauncher,
                                restoreSummaryMsg = restoreSummaryMsg,
                                onDismissSummary = { restoreSummaryMsg = null },
                                onRestoreSuccess = { summary -> restoreSummaryMsg = summary }
                            )
                        }
                    }
                }
            }
        }
    }
}
