package com.jmd.autotext.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.ui.components.appSubScreenHeader
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Application settings and operational configuration screen providing controls for
 * automated lockscreen bypass PIN, accessibility stealth guard, fallback touch coordinates,
 * carrier preferences, and database backup/restore.
 *
 * @param context Android context for resolving system services and content resolvers.
 * @param repository Application data repository for database serialization and migrations.
 * @param prefs SharedPreferences manager storing persistent user configuration.
 * @param onThemeChanged Callback triggered when user switches between Light, Dark, or System theme.
 * @param onOpenDocumentation Callback triggered to display bundled technical manual.
 * @param onBack Callback triggered when user navigates back.
 */
@Composable
fun settingsView(
    context: Context,
    repository: AppRepository,
    prefs: PreferenceManager,
    onThemeChanged: (String) -> Unit = {},
    onOpenPrivacySettings: () -> Unit = {},
    onOpenDocumentation: () -> Unit = {},
    onBack: () -> Unit
) {
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
                        com.jmd.autotext.scheduler.AlarmScheduler(context).rescheduleAllPending()
                        com.jmd.autotext.scheduler.DailyCampaignScheduler.scheduleAll(context)
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
            appSubScreenHeader(
                title = "Settings & Security",
                subtitle = "Hardware identity, lockscreen bypass, banking guard & backups",
                onBack = onBack
            )
        },
        containerColor = AppDesignTokens.VoidBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            )
        ) {
            // 0. Status Indicators & System Health Guide Card
            item {
                settingsStatusIndicatorsGuideCard(
                    context = context,
                    prefs = prefs
                )
            }

            // 1. Lock Screen Automation Card
            item {
                settingsAutoUnlockCard(
                    context = context,
                    prefs = prefs
                )
            }

            // 3. Bank App Shield & Accessibility Stealth Guard
            item {
                settingsBankingShieldCard(
                    context = context,
                    prefs = prefs,
                    onOpenPrivacySettings = onOpenPrivacySettings
                )
            }

            // 4. User & Carrier Preferences Cards
            item {
                settingsPreferencesCards(
                    prefs = prefs,
                    onThemeChanged = onThemeChanged
                )
            }

            // 5. Data Backup & Restore Center
            item {
                settingsBackupRestoreCard(
                    context = context,
                    repository = repository,
                    exportFileLauncher = exportFileLauncher,
                    filePickerLauncher = filePickerLauncher,
                    restoreSummaryMsg = restoreSummaryMsg,
                    onDismissSummary = { restoreSummaryMsg = null },
                    onRestoreSuccess = { summary -> restoreSummaryMsg = summary }
                )
            }

            // 6. Technical Manual & Logic Guide
            item {
                settingsDocumentationCard(
                    context = context,
                    onOpenDocumentation = onOpenDocumentation
                )
            }
        }
    }
}
