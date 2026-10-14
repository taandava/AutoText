package com.jmd.autotext.ui.screens.more

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.RecipientItem
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.screens.ContactSequenceStudio
import com.jmd.autotext.ui.screens.more.contacts.AddEditContactView
import com.jmd.autotext.ui.screens.more.contacts.BulkContactImportView
import com.jmd.autotext.ui.screens.more.contacts.BulkContactItemCard
import com.jmd.autotext.ui.theme.BentoGlassTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkContactsView(repository: AppRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    var recipients by remember { mutableStateOf(repository.getAllRecipients()) }
    var isImporting by remember { mutableStateOf(false) }
    var isAddingContact by remember { mutableStateOf(false) }
    var editingRecipient by remember { mutableStateOf<RecipientItem?>(null) }
    var sequenceTargetContact by remember { mutableStateOf<RecipientItem?>(null) }

    if (sequenceTargetContact != null) {
        ContactSequenceStudio(
            initialContactName = sequenceTargetContact!!.name,
            initialContactPhone = sequenceTargetContact!!.phoneOrEmail,
            onDismiss = { sequenceTargetContact = null },
            onSaved = { sequenceTargetContact = null }
        )
        return
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                }
                if (!content.isNullOrBlank()) {
                    val count = repository.importContactsFromCsv(content)
                    recipients = repository.getAllRecipients()
                    Toast.makeText(context, "Imported $count contacts from CSV!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Selected CSV file was empty", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val csv = repository.exportContactsToCsv()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.bufferedWriter().use { it.write(csv) }
                }
                Toast.makeText(context, "Exported ${recipients.size} contacts to CSV file!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = isImporting || isAddingContact) {
        if (isAddingContact) {
            isAddingContact = false
            editingRecipient = null
        } else {
            isImporting = false
        }
    }

    if (isAddingContact) {
        AddEditContactView(
            editingRecipient = editingRecipient,
            repository = repository,
            onBack = {
                isAddingContact = false
                editingRecipient = null
            },
            onSaved = {
                recipients = repository.getAllRecipients()
                isAddingContact = false
                editingRecipient = null
            }
        )
    } else if (isImporting) {
        BulkContactImportView(
            repository = repository,
            onPickCsv = {
                isImporting = false
                csvPickerLauncher.launch("*/*")
            },
            onBack = { isImporting = false },
            onImportSuccess = {
                recipients = repository.getAllRecipients()
                isImporting = false
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Contacts & Groups", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { csvExportLauncher.launch("autotext_contacts.csv") }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = BentoGlassTokens.NeonCyan)
                        }
                        IconButton(onClick = { csvPickerLauncher.launch("*/*") }) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Import CSV", tint = BentoGlassTokens.VividEmerald)
                        }
                        IconButton(onClick = {
                            editingRecipient = null
                            isAddingContact = true
                        }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", tint = BentoGlassTokens.ElectricIndigo)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BentoGlassTokens.VoidBackground)
                )
            },
            floatingActionButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FloatingActionButton(
                        onClick = {
                            editingRecipient = null
                            isAddingContact = true
                        },
                        containerColor = BentoGlassTokens.NeonCyan,
                        contentColor = Color.Black
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact")
                    }

                    ExtendedFloatingActionButton(
                        onClick = { isImporting = true },
                        containerColor = BentoGlassTokens.VividEmerald,
                        contentColor = Color.Black,
                        icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                        text = { Text("Import CSV", fontWeight = FontWeight.Bold) }
                    )
                }
            },
            containerColor = BentoGlassTokens.VoidBackground
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    BentoCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContactPhone, contentDescription = null, tint = BentoGlassTokens.VividEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saved: ${recipients.size}", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 13.5.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                GlassButton(
                                    text = "Export CSV",
                                    icon = Icons.Default.FileDownload,
                                    onClick = { csvExportLauncher.launch("autotext_contacts.csv") },
                                    variant = GlassButtonVariant.SECONDARY
                                )
                                GlassButton(
                                    text = "Import CSV",
                                    icon = Icons.Default.FileUpload,
                                    onClick = { csvPickerLauncher.launch("*/*") },
                                    variant = GlassButtonVariant.PRIMARY
                                )
                            }
                        }
                    }
                }

                if (recipients.isEmpty()) {
                    item {
                        BentoEmptyState(
                            title = "No Contacts Imported Yet",
                            description = "Tap below to add contacts individually or paste CSV phone numbers.",
                            icon = Icons.Default.Group,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                        )
                    }
                } else {
                    items(recipients, key = { it.id }) { r ->
                        BulkContactItemCard(
                            recipient = r,
                            onEdit = {
                                editingRecipient = r
                                isAddingContact = true
                            },
                            onCreateSequence = {
                                sequenceTargetContact = r
                            },
                            onDelete = {
                                repository.deleteRecipient(r.id)
                                recipients = repository.getAllRecipients()
                            }
                        )
                    }
                }
            }
        }
    }
}