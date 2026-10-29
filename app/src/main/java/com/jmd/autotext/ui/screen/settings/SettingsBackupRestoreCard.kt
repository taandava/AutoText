package com.jmd.autotext.ui.screen.settings

import android.content.*
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.scheduler.AlarmScheduler
import com.jmd.autotext.scheduler.DailyCampaignScheduler
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data persistence and migration card providing JSON export and import capabilities.
 *
 * @param context Android context for launching system storage pickers.
 * @param repository Application data repository providing database backup serialization.
 * @param exportFileLauncher Activity result launcher for creating a destination backup JSON document.
 * @param filePickerLauncher Activity result launcher for selecting an existing backup JSON document.
 * @param restoreSummaryMsg Status or error message generated from a completed restore operation.
 * @param onDismissSummary Callback to dismiss restore result dialog.
 * @param onRestoreSuccess Callback invoked when database import completes successfully.
 * @param modifier Optional layout modifier.
 */
@Composable
fun settingsBackupRestoreCard(
    context: Context,
    repository: AppRepository,
    exportFileLauncher: ManagedActivityResultLauncher<String, Uri?>,
    filePickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    restoreSummaryMsg: String?,
    onDismissSummary: () -> Unit,
    onRestoreSuccess: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showRestoreDialog by remember { mutableStateOf(false) }
    var pasteJsonText by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        appCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Backup & Restore Center", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Back up all your scheduled tasks, SMS & WhatsApp daily campaigns, auto-reply rules, templates, and contact pool to an offline JSON file. Restore everything seamlessly after reinstalling the APK.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 15.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Export & Backup Data:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    glassButton(
                        text = "Save to File",
                        emoji = "💾",
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            exportFileLauncher.launch("AutoText_Backup_$timeStamp.json")
                        }
                    )

                    glassButton(
                        text = "Direct SD Card",
                        emoji = "📁",
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val backupDir = File(android.os.Environment.getExternalStorageDirectory(), "Documents/AutoText/backups")
                            val result = repository.exportDatabaseToLocalDirectory(backupDir)
                            result.onSuccess { file ->
                                context.showAppToast("Saved to ${file.name}")
                            }.onFailure { err ->
                                context.showAppToast("Direct export failed: ${err.message}")
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    glassButton(
                        text = "Share File",
                        emoji = "📤",
                        variant = GlassButtonVariant.SUCCESS,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            try {
                                val json = repository.exportDatabaseToJson()
                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val fileName = "AutoText_Backup_$timeStamp.json"
                                val backupFile = File(context.cacheDir, fileName)
                                backupFile.writeText(json)

                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    backupFile
                                )

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "AutoText Backup $timeStamp")
                                    putExtra(Intent.EXTRA_TEXT, "AutoText JSON Complete Data Backup ($fileName)")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Save or Share AutoText Backup"))
                                context.showAppToast("Backup file generated (${json.length} bytes)")
                            } catch (_: Exception) {
                                val json = repository.exportDatabaseToJson()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share AutoText Backup JSON"))
                            }
                        }
                    )

                    glassButton(
                        text = "Copy JSON",
                        emoji = "📋",
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val json = repository.exportDatabaseToJson()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("AutoText Backup JSON", json)
                            com.jmd.autotext.compat.ClipboardCompat.setPrimaryClip(clipboard, clip)
                            context.showAppToast("Backup JSON copied")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Restore & Migration:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                glassButton(
                    text = "Select Backup File",
                    emoji = "📂",
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.2f),
                    onClick = {
                        filePickerLauncher.launch("*/*")
                    }
                )

                glassButton(
                    text = if (showRestoreDialog) "Hide Restore" else "Paste JSON",
                    emoji = "📋",
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (text.startsWith("{") && text.contains("tasks")) pasteJsonText = text
                        showRestoreDialog = !showRestoreDialog
                    }
                )
            }
        }

        // Success Banner when backup is restored (NO POPUPS)
        restoreSummaryMsg?.let { summary ->
            appCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = MaterialTheme.colorScheme.tertiary
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✅", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup Restored Successfully", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(summary, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "All tasks, sequences, and daily campaigns rescheduled with Android AlarmManager.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                glassButton(
                    text = "Dismiss",
                    emoji = "✓",
                    onClick = onDismissSummary,
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Inline JSON Restore Panel (NO POPUPS)
        if (showRestoreDialog) {
            appCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = MaterialTheme.colorScheme.primary
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔄", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore from JSON Backup", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showRestoreDialog = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Paste your backup JSON below to restore tasks, campaigns, contact sequences, and settings:",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    glassButton(
                        text = "Select File",
                        emoji = "📂",
                        onClick = {
                            showRestoreDialog = false
                            filePickerLauncher.launch("*/*")
                        },
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                    glassButton(
                        text = "Paste Clipboard",
                        emoji = "📋",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (text.isNotBlank()) pasteJsonText = text
                        },
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pasteJsonText,
                    onValueChange = { pasteJsonText = it },
                    placeholder = { Text("{\n  \"tasks\": [...],\n  \"contact_sequences\": [...]\n}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    glassButton(
                        text = "Restore All Now",
                        emoji = "🔄",
                        onClick = {
                            if (pasteJsonText.isNotBlank()) {
                                val result = repository.importDatabaseFromJson(pasteJsonText.trim())
                                if (result.isSuccess) {
                                    AlarmScheduler(context).rescheduleAllPending()
                                    DailyCampaignScheduler.scheduleAll(context)
                                    showRestoreDialog = false
                                    onRestoreSuccess(result.getOrNull() ?: "Restore successful")
                                } else {
                                    context.showAppToast("Restore failed: ${result.exceptionOrNull()?.message}", isLong = true)
                                }
                            } else {
                                context.showAppToast("Paste valid JSON backup")
                            }
                        },
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                    glassButton(
                        text = "Cancel",
                        onClick = { showRestoreDialog = false },
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
