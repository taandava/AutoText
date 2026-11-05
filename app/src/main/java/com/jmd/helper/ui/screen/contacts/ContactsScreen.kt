/**
 * Package: com.jmd.helper.ui.screen.contacts
 * Module: UI Contacts & Contact Sequences Screen Layer
 *
 * Primary coordinator screen managing:
 * 1. Saved Custom / CSV Contacts: Manually entered or imported recipients with inline sequence triggers.
 * 2. Ingested Dynamic Pool: Comprehensive auto-scanned contacts from device address book, SMS, calls, and WhatsApp.
 * 3. Saved WhatsApp Groups: Verified and discovered WhatsApp groups with 1-tap live picker integration.
 */
package com.jmd.helper.ui.screen.contacts

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.jmd.helper.core.WhatsAppPackageHelper
import com.jmd.helper.core.showAppToast
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.RecipientItem
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.engine.ContactIngestionEngine
import com.jmd.helper.service.TaskAccessibilityService
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.screen.sequence.ContactSequenceStudio
import com.jmd.helper.ui.theme.AppDesignTokens
import com.jmd.helper.whatsapp.WhatsAppChatScanner

/**
 * Top-level contacts management screen presenting segmented views for:
 * 1. Saved Custom / CSV Contacts: Manually entered or imported recipients with inline sequence triggers.
 * 2. Ingested Dynamic Pool: Comprehensive auto-scanned contacts from device address book, SMS, calls, and WhatsApp.
 * 3. Saved WhatsApp Groups: Verified and discovered WhatsApp groups with 1-tap live picker integration.
 *
 * @param repository Application data repository.
 * @param onBack Callback invoked to exit contacts management.
 */
@Composable
fun BulkContactsView(repository: AppRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Custom/CSV, 1 = Dynamic Pool, 2 = WhatsApp Groups
    var recipients by remember { mutableStateOf(repository.getAllRecipients()) }
    var isImporting by remember { mutableStateOf(value = false) }
    var isAddingContact by remember { mutableStateOf(value = false) }
    var editingRecipient by remember { mutableStateOf<RecipientItem?>(null) }
    var sequenceTargetContactName by remember { mutableStateOf("") }
    var sequenceTargetContactPhone by remember { mutableStateOf("") }

    // Dynamic Pool & WhatsApp Groups State
    var poolSearch by remember { mutableStateOf("") }
    var groupSearch by remember { mutableStateOf("") }
    var poolRefreshTrigger by remember { mutableIntStateOf(0) }
    var groupRefreshTrigger by remember { mutableIntStateOf(0) }
    var isSyncingPool by remember { mutableStateOf(value = false) }

    val poolStats = remember(poolRefreshTrigger) { repository.getDynamicPoolStats() }
    val poolContacts = remember(poolSearch, poolRefreshTrigger) {
        repository.getDynamicPool(limit = 1000, searchQuery = poolSearch)
    }
    val groupsList = remember(groupSearch, groupRefreshTrigger) {
        val all = repository.getWhatsAppGroupItems()
        if (groupSearch.isBlank()) all
        else all.filter { it.name.contains(groupSearch, ignoreCase = true) }
    }

    if (sequenceTargetContactPhone.isNotBlank()) {
        ContactSequenceStudio(
            initialContactName = sequenceTargetContactName,
            initialContactPhone = sequenceTargetContactPhone,
            onDismiss = {
                sequenceTargetContactName = ""
                sequenceTargetContactPhone = ""
            },
            onSaved = {
                sequenceTargetContactName = ""
                sequenceTargetContactPhone = ""
            },
        )
        return
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                }
                if (!content.isNullOrBlank()) {
                    val count = repository.importContactsFromCsv(content)
                    recipients = repository.getAllRecipients()
                    context.showAppToast("Imported $count contacts")
                } else {
                    context.showAppToast("CSV file was empty")
                }
            } catch (e: Exception) {
                context.showAppToast("Error reading CSV: ${e.message}", isLong = true)
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val csv = repository.exportContactsToCsv()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.bufferedWriter().use { it.write(csv) }
                }
                context.showAppToast("Exported ${recipients.size} contacts")
            } catch (e: Exception) {
                context.showAppToast("Error saving CSV: ${e.message}", isLong = true)
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
            },
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
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                AppSubScreenHeader(
                    title = "Contacts & Groups Hub",
                    subtitle = when (selectedTab) {
                        1 -> "${poolStats.totalCount} dynamic pool leads"
                        2 -> "${groupsList.size} discovered WhatsApp groups"
                        else -> "${recipients.size} saved custom & CSV contacts"
                    },
                    onBack = onBack,
                    actions = {
                        when (selectedTab) {
                            0 -> {
                                IconButton(onClick = { csvExportLauncher.launch("helper_contacts.csv") }) {
                                    Icon(Icons.Filled.FileDownload, contentDescription = "Export CSV", modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { csvPickerLauncher.launch("*/*") }) {
                                    Icon(Icons.Filled.FileUpload, contentDescription = "Import CSV", modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = {
                                        editingRecipient = null
                                        isAddingContact = true
                                    },
                                ) {
                                    Icon(Icons.Filled.PersonAdd, contentDescription = "Add Contact", modifier = Modifier.size(20.dp))
                                }
                            }
                            1 -> {
                                IconButton(
                                    onClick = {
                                        if (!isSyncingPool) {
                                            isSyncingPool = true
                                            scope.launch(Dispatchers.IO) {
                                                val scanner = ContactIngestionEngine(context, repository)
                                                val summary = scanner.scanAndIngestAll()
                                                withContext(Dispatchers.Main) {
                                                    poolRefreshTrigger++
                                                    isSyncingPool = false
                                                    context.showAppToast("Ingested ${summary.newAdded} leads to pool")
                                                }
                                            }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Filled.Sync, contentDescription = "Sync", modifier = Modifier.size(20.dp))
                                }
                            }
                            else -> {
                                IconButton(onClick = { groupRefreshTrigger++ }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    when (selectedTab) {
                        0 -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FloatingActionButton(
                                    onClick = {
                                        editingRecipient = null
                                        isAddingContact = true
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.Black
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(22.dp))
                                }

                                ExtendedFloatingActionButton(
                                    onClick = { isImporting = true },
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = Color.Black,
                                    icon = { Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    text = { Text("Import CSV", fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                        1 -> {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        context.showAppToast("Contacts permission required to sync lead pool")
                                    }
                                    if (!isSyncingPool) {
                                        isSyncingPool = true
                                        scope.launch(Dispatchers.IO) {
                                            val scanner = ContactIngestionEngine(context, repository)
                                            val summary = scanner.scanAndIngestAll()
                                            withContext(Dispatchers.Main) {
                                                poolRefreshTrigger++
                                                isSyncingPool = false
                                                context.showAppToast("Ingested ${summary.newAdded} leads to pool")
                                            }
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black,
                                icon = { Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text(if (isSyncingPool) "Syncing..." else "Sync Lead Pool", fontWeight = FontWeight.Bold) }
                            )
                        }
                        else -> {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    val installedPkg = WhatsAppPackageHelper.getInstalledWhatsAppPackage(context)
                                    val isBiz = installedPkg == WhatsAppPackageHelper.PACKAGE_WHATSAPP_BUSINESS
                                    val ch = if (isBiz) ChannelType.WHATSAPP_BUSINESS else ChannelType.WHATSAPP
                                    TaskAccessibilityService.startWhatsAppChatScan(
                                        context,
                                        targetPackage = installedPkg,
                                        channel = ch,
                                        scanTarget = WhatsAppChatScanner.ScanTarget.ALL_CHATS
                                    )
                                    context.showAppToast("Scanning chats for groups...")
                                },
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = Color.Black,
                                icon = { Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("Harvest Groups", fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            },
            containerColor = AppDesignTokens.VoidBackground
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Unified 3-Tab Segmented Selector
                item {
                    GlassSegmentedControl(
                        items = listOf(
                            "Custom & CSV",
                            "Dynamic Pool (${poolStats.totalCount})",
                            "WA Groups (${groupsList.size})"
                        ),
                        selectedIndex = selectedTab,
                        onItemSelected = { selectedTab = it },
                        activeColor = when (selectedTab) {
                            1 -> MaterialTheme.colorScheme.primary
                            2 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }

                // TAB 0: Custom & CSV Contacts
                if (selectedTab == 0) {
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ContactPhone, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Saved: ${recipients.size}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    GlassButton(
                                        text = "Export CSV",
                                        icon = Icons.Default.FileDownload,
                                        onClick = { csvExportLauncher.launch("helper_contacts.csv") },
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
                            AppEmptyState(
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
                                    sequenceTargetContactName = r.name
                                    sequenceTargetContactPhone = r.phoneOrEmail
                                },
                                onDelete = {
                                    repository.deleteRecipient(r.id)
                                    recipients = repository.getAllRecipients()
                                }
                            )
                        }
                    }
                }

                // TAB 1: Dynamic Lead Pool
                else if (selectedTab == 1) {
                    item {
                        GlassSearchField(
                            value = poolSearch,
                            onValueChange = { poolSearch = it },
                            placeholder = "Search ${poolStats.totalCount} lead pool contacts...",
                            leadingIcon = Icons.Default.Search,
                            onClear = { poolSearch = "" }
                        )
                    }

                    if (poolContacts.isEmpty()) {
                        item {
                            AppEmptyState(
                                title = "No Leads in Pool",
                                description = "Tap 'Sync Lead Pool' below to harvest contacts from device calls, SMS, and WhatsApp chats.",
                                icon = Icons.Default.PeopleOutline,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                            )
                        }
                    } else {
                        items(poolContacts, key = { it.id }) { c ->
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        ContactAvatar(
                                            name = c.name.ifBlank { c.phone },
                                            photoUri = c.photoUri,
                                            size = 36.dp,
                                            badgeIcon = Icons.Default.Person,
                                            badgeColor = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = c.name.ifBlank { "Unsaved Contact" },
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 13.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                AppBadge(
                                                    text = c.source,
                                                    color = when (c.source) {
                                                        "WA_CHAT", "WA_BUSINESS" -> MaterialTheme.colorScheme.tertiary
                                                        "CALL" -> MaterialTheme.colorScheme.primary
                                                        "SMS" -> MaterialTheme.colorScheme.primary
                                                        else -> MaterialTheme.colorScheme.primary
                                                    },
                                                    showDot = false
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = c.phone,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                sequenceTargetContactName = c.name
                                                sequenceTargetContactPhone = c.phone
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.AltRoute,
                                                contentDescription = "Create Sequence",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                repository.deleteDynamicContact(c.id)
                                                poolRefreshTrigger++
                                                context.showAppToast("Contact removed from pool")
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: WhatsApp Groups
                else {
                    item {
                        GlassSearchField(
                            value = groupSearch,
                            onValueChange = { groupSearch = it },
                            placeholder = "Search ${groupsList.size} discovered groups...",
                            leadingIcon = Icons.Default.Search,
                            onClear = { groupSearch = "" }
                        )
                    }

                    if (groupsList.isEmpty()) {
                        item {
                            AppEmptyState(
                                title = "No WhatsApp Groups Found",
                                description = "Tap 'Harvest Groups' below or use 'Pick in WA' when composing group messages to discover groups.",
                                icon = Icons.Default.GroupOff,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                            )
                        }
                    } else {
                        items(groupsList, key = { it.id }) { g ->
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        ContactAvatar(
                                            name = g.name,
                                            size = 36.dp,
                                            badgeIcon = Icons.Default.Group,
                                            badgeColor = MaterialTheme.colorScheme.tertiary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = g.name,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (g.memberCountStr.isNotBlank()) "${g.memberCountStr} participants • WhatsApp Group" else "WhatsApp Group",
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            repository.deleteWhatsAppGroup(g.name)
                                            groupRefreshTrigger++
                                            context.showAppToast("Group removed")
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Group",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
