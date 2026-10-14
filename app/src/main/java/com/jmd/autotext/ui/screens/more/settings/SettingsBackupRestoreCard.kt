package com.jmd.autotext.ui.screens.more.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsBackupRestoreCard(
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
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = BentoGlassTokens.VividEmerald, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Backup & Restore Center", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Back up all your scheduled tasks, SMS & WhatsApp daily campaigns, auto-reply rules, auto-forwarding rules, templates, and contact pool to an offline JSON file. Restore everything seamlessly after reinstalling the APK.",
                color = BentoGlassTokens.TextMuted,
                fontSize = 11.5.sp,
                lineHeight = 15.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Export & Backup Data:", fontWeight = FontWeight.SemiBold, color = BentoGlassTokens.TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlassButton(
                    text = "Save to File",
                    icon = Icons.Default.Save,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        exportFileLauncher.launch("AutoText_Backup_$timeStamp.json")
                    }
                )

                GlassButton(
                    text = "Share File",
                    icon = Icons.Default.Share,
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
                            Toast.makeText(context, "Backup file generated (${json.length} bytes)", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            val json = repository.exportDatabaseToJson()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share AutoText Backup JSON"))
                        }
                    }
                )

                GlassButton(
                    text = "Copy JSON",
                    icon = Icons.Default.ContentCopy,
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val json = repository.exportDatabaseToJson()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("AutoText Backup JSON", json)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Full Backup JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Restore & Migration:", fontWeight = FontWeight.SemiBold, color = BentoGlassTokens.TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = "Select Backup File",
                    icon = Icons.Default.FolderOpen,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.2f),
                    onClick = {
                        filePickerLauncher.launch("*/*")
                    }
                )

                GlassButton(
                    text = if (showRestoreDialog) "Hide Restore" else "Paste JSON",
                    icon = Icons.Default.ContentPaste,
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
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = BentoGlassTokens.VividEmerald
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BentoGlassTokens.VividEmerald, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup Restored Successfully", fontWeight = FontWeight.Bold, color = BentoGlassTokens.VividEmerald, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(summary, color = BentoGlassTokens.TextPrimary, fontSize = 12.5.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "All tasks, sequences, and daily campaigns rescheduled with Android AlarmManager.",
                    fontSize = 11.sp,
                    color = BentoGlassTokens.TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                GlassButton(
                    text = "Dismiss",
                    onClick = onDismissSummary,
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Inline JSON Restore Panel (NO POPUPS)
        if (showRestoreDialog) {
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = BentoGlassTokens.NeonCyan
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = BentoGlassTokens.NeonCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore from JSON Backup", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showRestoreDialog = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = BentoGlassTokens.TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Paste your backup JSON below to restore tasks, campaigns, contact sequences, and settings:",
                    fontSize = 11.5.sp,
                    color = BentoGlassTokens.TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassButton(
                        text = "Select File",
                        icon = Icons.Default.FolderOpen,
                        onClick = {
                            showRestoreDialog = false
                            filePickerLauncher.launch("*/*")
                        },
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Paste Clipboard",
                        icon = Icons.Default.ContentPaste,
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
                        focusedBorderColor = BentoGlassTokens.NeonCyan,
                        unfocusedBorderColor = BentoGlassTokens.GlassBorderSubtle,
                        focusedTextColor = BentoGlassTokens.TextPrimary,
                        unfocusedTextColor = BentoGlassTokens.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassButton(
                        text = "Restore All Now",
                        icon = Icons.Default.Restore,
                        onClick = {
                            if (pasteJsonText.isNotBlank()) {
                                val result = repository.importDatabaseFromJson(pasteJsonText.trim())
                                if (result.isSuccess) {
                                    showRestoreDialog = false
                                    onRestoreSuccess(result.getOrNull() ?: "Restore successful")
                                } else {
                                    Toast.makeText(context, "Restore failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Please paste valid JSON backup content", Toast.LENGTH_SHORT).show()
                            }
                        },
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
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

