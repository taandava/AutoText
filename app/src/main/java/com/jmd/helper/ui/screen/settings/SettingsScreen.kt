package com.jmd.helper.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jmd.helper.core.showAppToast
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.ui.components.AppSubScreenHeader
import com.jmd.helper.ui.components.GlassChip
import com.jmd.helper.ui.theme.AppDesignTokens

/**
 * Functional subdivisions dividing the comprehensive application settings into focused tabs.
 */
enum class SettingsSubdivision(
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    TEMPLATES("Templates & Tokens", Icons.Filled.TextFields, "Date formats, tokens & dynamic variables"),
    AUTOMATION("Automation & Channels", Icons.Filled.Bolt, "WhatsApp, SMS quota, SIM & call simulation"),
    SECURITY("Security & Shield", Icons.Filled.Shield, "PIN unlock, banking shield & gesture fallback"),
    THEME_UI("Theme & Display", Icons.Filled.Palette, "App theme, styling & layout preferences"),
    SYSTEM("System & Diagnostics", Icons.Filled.Build, "Diagnostics, self-repair, backup & docs")
}

/**
 * Application settings and operational configuration screen providing controls for
 * dynamic message templates, automation channels, lockscreen PIN unlock, accessibility
 * banking guard, theme styling, system diagnostics, and database backup/restore.
 *
 * @param context Android context for resolving system services and content resolvers.
 * @param repository Application data repository for database serialization and migrations.
 * @param prefs SharedPreferences manager storing persistent user configuration.
 * @param onThemeChanged Callback triggered when user switches between Light, Dark, or System theme.
 * @param onOpenDocumentation Callback triggered to display bundled technical manual.
 * @param onBack Callback triggered when user navigates back.
 */
@Composable
fun SettingsView(
    context: Context,
    repository: AppRepository,
    prefs: PreferenceManager,
    onThemeChanged: (String) -> Unit = {},
    onOpenPrivacySettings: () -> Unit = {},
    onOpenDocumentation: () -> Unit = {},
    onBack: () -> Unit
) {
    var selectedSubdivision by remember { mutableStateOf(SettingsSubdivision.TEMPLATES) }
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
                        com.jmd.helper.scheduler.AlarmScheduler(context).rescheduleAllPending()
                        com.jmd.helper.scheduler.DailyCampaignScheduler.scheduleAll(context)
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
                title = "Settings & Configuration",
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
            // Horizontal Subdivisions Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                SettingsSubdivision.entries.forEach { sub ->
                    GlassChip(
                        selected = selectedSubdivision == sub,
                        onClick = { selectedSubdivision = sub },
                        label = sub.title,
                        icon = sub.icon,
                        activeColor = primaryColor
                    )
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
                    SettingsSubdivision.TEMPLATES -> {
                        item {
                            SettingsTemplateTokensCard(
                                prefs = prefs
                            )
                        }
                    }
                    SettingsSubdivision.AUTOMATION -> {
                        item {
                            SettingsAutomationChannelsCard(
                                context = context,
                                prefs = prefs
                            )
                        }
                    }
                    SettingsSubdivision.SECURITY -> {
                        item {
                            SettingsBankingShieldCard(
                                context = context,
                                prefs = prefs,
                                onOpenPrivacySettings = onOpenPrivacySettings
                            )
                        }
                        item {
                            SettingsScreenAssistCard(
                                context = context,
                                prefs = prefs
                            )
                        }
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
                            SettingsSystemSelfTestCard(
                                context = context
                            )
                        }
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
                        item {
                            SettingsDocumentationCard(
                                context = context,
                                onOpenDocumentation = onOpenDocumentation
                            )
                        }
                    }
                }
            }
        }
    }
}
