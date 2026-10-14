package com.jmd.autotext.ui.screens.more

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.ui.screens.more.settings.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    context: Context,
    repository: AppRepository,
    prefs: PreferenceManager,
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
                Toast.makeText(context, "Backup file saved successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving backup file: ${e.message}", Toast.LENGTH_LONG).show()
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
                    val result = repository.importDatabaseFromJson(content)
                    if (result.isSuccess) {
                        restoreSummaryMsg = result.getOrNull()
                        Toast.makeText(context, "Restore successful!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Restore failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "Selected backup file was empty", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading backup file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings, Auto-Lock & Backup", color = BentoGlassTokens.TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BentoGlassTokens.VoidBackground)
            )
        },
        containerColor = BentoGlassTokens.VoidBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Device Identity & Owner Profile
            item {
                SettingsDeviceIdentityCard(
                    context = context,
                    prefs = prefs
                )
            }

            // 2. Lock Screen Automation Card
            item {
                SettingsAutoUnlockCard(
                    context = context,
                    prefs = prefs
                )
            }

            // 3. User & Carrier Preferences Cards
            item {
                SettingsPreferencesCards(
                    prefs = prefs
                )
            }

            // 4. Data Backup & Restore Center
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

            // 5. Technical Manual & Logic Guide
            item {
                SettingsDocumentationCard(
                    context = context,
                    onOpenDocumentation = onOpenDocumentation
                )
            }
        }
    }
}