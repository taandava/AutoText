package com.jmd.autotext.ui.screen.compose

import android.content.*
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.jmd.autotext.service.AutoTextAccessibilityService
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import com.jmd.autotext.core.*
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.repo.*
import com.jmd.autotext.engine.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.model.*
import com.jmd.autotext.ui.theme.*
import com.jmd.autotext.whatsapp.*

fun isSamePhoneNumber(p1: String, p2: String): Boolean = PhoneNumberNormalizer.isSamePhoneNumber(p1, p2)
/**
 * Contact pool picker bottom sheet modal.
 * @param onAddRecipient Callback invoked to validate and add a contact.
 * @param onCommitRecipients Optional callback committing updated selection batch.
 * @param onDismiss Callback to dismiss contact pool modal.
 */
@Composable
fun contactPoolPickerSheet(
    repository: AppRepository,
    selectedRecipients: List<SelectedRecipient>,
    onAddRecipient: (name: String, phone: String) -> Boolean,
    onCommitRecipients: ((List<SelectedRecipient>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var poolSearch by remember { mutableStateOf("") }
    val poolContacts = remember(poolSearch, refreshTrigger) {
        repository.getDynamicPool(limit = 3000, searchQuery = poolSearch)
    }
    val selectedRecipientsMap = remember {
        mutableStateMapOf<String, SelectedRecipient>().apply {
            selectedRecipients.forEach { put(it.phone, it) }
        }
    }

    fun isContactSelected(phone: String): Boolean {
        return selectedRecipientsMap.containsKey(phone) || selectedRecipientsMap.keys.any { isSamePhoneNumber(it, phone) }
    }

    fun toggleContact(name: String, phone: String, isCurrentlySelected: Boolean) {
        if (isCurrentlySelected) {
            val matchingKeys = selectedRecipientsMap.keys.filter { isSamePhoneNumber(it, phone) }
            matchingKeys.forEach { selectedRecipientsMap.remove(it) }
        } else {
            selectedRecipientsMap[phone] = SelectedRecipient(name = name.ifBlank { phone }, phone = phone)
        }
    }

    val commitAction = {
        val selectedList = selectedRecipientsMap.values.toList().distinctBy { 
            val d = it.phone.filter { ch -> ch.isDigit() }
            if (d.length >= 10) d.takeLast(10) else it.phone
        }
        if (onCommitRecipients != null) {
            onCommitRecipients(selectedList)
            context.showAppToast("Updated recipients (${selectedList.size})")
        } else {
            var addedCount = 0
            selectedList.forEach { c ->
                if (onAddRecipient(c.name, c.phone)) addedCount++
            }
            if (addedCount > 0) context.showAppToast("Added $addedCount contacts")
        }
        onDismiss()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Dynamic Contact Pool",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${poolContacts.size} available leads & contacts",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    glassButton(
                        text = if (isSyncing) "Syncing..." else "Sync Pool",
                        onClick = {
                            if (!isSyncing) {
                                isSyncing = true
                                scope.launch(Dispatchers.IO) {
                                    val scanner = ContactIngestionEngine(context, repository)
                                    val summary = scanner.scanAndIngestAll()
                                    withContext(Dispatchers.Main) {
                                        refreshTrigger++
                                        isSyncing = false
                                        context.showAppToast("Synced ${summary.newAdded} contacts to pool")
                                    }
                                }
                            }
                        },
                        variant = GlassButtonVariant.SECONDARY,
                        icon = Icons.Default.Sync,
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                        shape = AppDesignTokens.ShapePill
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    glassButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    glassButton(
                        text = if (selectedRecipientsMap.isNotEmpty()) "Add (${selectedRecipientsMap.size}) Contacts" else "Done",
                        onClick = commitAction,
                        variant = GlassButtonVariant.PRIMARY,
                        icon = Icons.Default.Check,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        },
        containerColor = AppDesignTokens.GlassBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            glassSearchField(
                value = poolSearch,
                onValueChange = { poolSearch = it },
                placeholder = "Search ${repository.getDynamicPoolStats().totalCount} pool contacts...",
                leadingIcon = Icons.Default.Search,
                onClear = { poolSearch = "" }
            )

            // Selection controls bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedRecipientsMap.isNotEmpty()) "${selectedRecipientsMap.size} selected" else "Select recipients",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedRecipientsMap.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (poolContacts.isNotEmpty()) {
                        Text(
                            text = "Select All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(AppDesignTokens.ShapeSmall)
                                .clickable {
                                    poolContacts.forEach {
                                        selectedRecipientsMap[it.phone] = SelectedRecipient(name = it.name.ifBlank { it.phone }, phone = it.phone)
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (selectedRecipientsMap.isNotEmpty()) {
                        Text(
                            text = "Clear All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(AppDesignTokens.ShapeSmall)
                                .clickable {
                                    selectedRecipientsMap.clear()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (poolContacts.isEmpty()) {
                appCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "Dynamic Contact Pool Empty",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Ingest and normalize contacts from your phone into the high-speed local pool.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        glassButton(
                            text = if (isSyncing) "Populating Pool..." else "⚡ Sync & Populate Contact Pool",
                            onClick = {
                                if (!isSyncing) {
                                    isSyncing = true
                                    scope.launch(Dispatchers.IO) {
                                        val scanner = ContactIngestionEngine(context, repository)
                                        val summary = scanner.scanAndIngestAll()
                                        withContext(Dispatchers.Main) {
                                            refreshTrigger++
                                            isSyncing = false
                                            context.showAppToast("Populated pool with ${summary.newAdded} contacts!")
                                        }
                                    }
                                }
                            },
                            variant = GlassButtonVariant.PRIMARY,
                            icon = Icons.Default.Sync,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            shape = AppDesignTokens.ShapePill
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(poolContacts, key = { it.id }) { c ->
                        val isChecked = isContactSelected(c.phone)

                        appCard(
                            onClick = {
                                toggleContact(c.name, c.phone, isChecked)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    contactAvatar(
                                        name = c.name.ifBlank { c.phone },
                                        photoUri = c.photoUri,
                                        size = 38.dp,
                                        badgeIcon = Icons.Default.Person,
                                        badgeColor = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = c.name.ifEmpty { "Unsaved Contact" },
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = c.phone,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        toggleContact(c.name, c.phone, isChecked)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkmarkColor = AppDesignTokens.GlassBackground
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modal bottom sheet allowing users to multi-select contacts for WhatsApp or WhatsApp Business direct messaging and calling.
 *
 * @param repository Data repository reading stored contacts and sync state.
 * @param selectedRecipients Current list of selected recipients.
 * @param selectedChannel Channel configuration (WHATSAPP, WHATSAPP_BUSINESS, etc.).
 * @param onAddRecipient Callback invoked to add a single contact.
 * @param onCommitRecipients Optional callback committing updated selection list.
 * @param onDismiss Callback to dismiss bottom sheet.
 */
@Composable
fun whatsAppContactsPickerSheet(
    repository: AppRepository,
    selectedRecipients: List<SelectedRecipient>,
    selectedChannel: ChannelType = ChannelType.WHATSAPP,
    onAddRecipient: (name: String, phone: String) -> Boolean,
    onCommitRecipients: ((List<SelectedRecipient>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isBusiness = selectedChannel == ChannelType.WHATSAPP_BUSINESS || selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL
    val isCalling = selectedChannel == ChannelType.CALL || selectedChannel == ChannelType.WHATSAPP_CALL || selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL
    val targetPkg = if (isBusiness) "com.whatsapp.w4b" else "com.whatsapp"
    val sheetTitle = when {
        selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL -> "Select WA Business Call Contacts"
        selectedChannel == ChannelType.WHATSAPP_CALL -> "Select WhatsApp Call Contacts"
        isBusiness -> "Select WA Business Contacts"
        else -> "Select WhatsApp Contacts"
    }
    val scanLabel = if (isBusiness) "Scan W4B" else "Scan WA"
    val harvestLabel = if (isBusiness) "Harvest W4B" else "Harvest WA"
    val appLabel = if (isBusiness) "Business" else "WhatsApp"

    var isScanning by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var waSearch by remember { mutableStateOf("") }
    var waContacts by remember { mutableStateOf<List<WhatsAppContactItem>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            refreshTrigger++
        }
    }

    LaunchedEffect(waSearch, refreshTrigger) {
        withContext(Dispatchers.IO) {
            val allContacts = repository.getWhatsAppContacts(query = waSearch, channel = selectedChannel, limit = 10000)
            val filteredContacts = if (isCalling) {
                allContacts.filter { !it.phone.startsWith("group:") && !it.normalizedPhone.startsWith("group:") }
            } else {
                allContacts
            }
            withContext(Dispatchers.Main) {
                waContacts = filteredContacts
            }
        }
    }

    val selectedRecipientsMap = remember {
        mutableStateMapOf<String, SelectedRecipient>().apply {
            selectedRecipients.forEach { put(it.phone, it) }
        }
    }

    fun isContactSelected(phone: String): Boolean {
        return selectedRecipientsMap.containsKey(phone) || selectedRecipientsMap.keys.any { isSamePhoneNumber(it, phone) }
    }

    fun toggleContact(name: String, phone: String, isCurrentlySelected: Boolean) {
        if (isCalling && (phone.startsWith("group:") || phone.filter { it.isDigit() }.length < 7)) {
            context.showAppToast("Calls require a contact with a valid phone number")
            return
        }
        if (isCurrentlySelected) {
            val matchingKeys = selectedRecipientsMap.keys.filter { isSamePhoneNumber(it, phone) }
            matchingKeys.forEach { selectedRecipientsMap.remove(it) }
        } else {
            selectedRecipientsMap[phone] = SelectedRecipient(name = name.ifBlank { phone }, phone = phone)
        }
    }

    val commitAction = {
        val selectedList = selectedRecipientsMap.values.toList().distinctBy { 
            val d = it.phone.filter { ch -> ch.isDigit() }
            if (d.length >= 10) d.takeLast(10) else it.phone
        }
        if (onCommitRecipients != null) {
            onCommitRecipients(selectedList)
            context.showAppToast("Updated recipients (${selectedList.size})")
        } else {
            var addedCount = 0
            selectedList.forEach { c ->
                if (onAddRecipient(c.name, c.phone)) addedCount++
            }
            if (addedCount > 0) context.showAppToast("Added $addedCount contacts")
        }
        onDismiss()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                sheetTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${waContacts.size} verified $appLabel accounts & chats",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        glassButton(
                            text = if (isScanning) "Scanning..." else scanLabel,
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                                    } catch (e: Exception) {
                                        Log.w("ComposeTaskScreen", "Permission launch failed: ${e.message}")
                                    }
                                }
                                if (!isScanning) {
                                    isScanning = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val scanner = ContactIngestionEngine(context, repository)
                                            val summary = scanner.scanAndIngestAll()
                                            val refreshed = repository.getWhatsAppContacts(query = waSearch, channel = selectedChannel, limit = 10000)
                                            withContext(Dispatchers.Main) {
                                                waContacts = refreshed
                                                isScanning = false
                                                context.showAppToast("Synced ${summary.newAdded} $appLabel contacts & chats", isLong = true)
                                            }
                                        } catch (t: Throwable) {
                                            Log.e("WhatsAppContacts", "Error during contacts scan", t)
                                            withContext(Dispatchers.Main) {
                                                isScanning = false
                                                context.showAppToast("Scan error: ${t.localizedMessage ?: "Unknown"}")
                                            }
                                        }
                                    }
                                }
                            },
                            variant = GlassButtonVariant.SECONDARY,
                            icon = Icons.Default.Sync,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                            shape = AppDesignTokens.ShapePill
                        )
                        glassButton(
                            text = harvestLabel,
                            onClick = {
                                AutoTextAccessibilityService.startWhatsAppChatScan(
                                    context,
                                    targetPackage = targetPkg,
                                    channel = selectedChannel,
                                    scanTarget = WhatsAppChatScanner.ScanTarget.ALL_CHATS
                                )
                                onDismiss()
                            },
                            variant = GlassButtonVariant.SECONDARY,
                            icon = Icons.AutoMirrored.Filled.Send,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                            shape = AppDesignTokens.ShapePill
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    glassButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    glassButton(
                        text = if (selectedRecipientsMap.isNotEmpty()) "Add (${selectedRecipientsMap.size}) Contacts" else "Done",
                        onClick = commitAction,
                        variant = GlassButtonVariant.PRIMARY,
                        icon = Icons.Default.Check,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        },
        containerColor = AppDesignTokens.GlassBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            glassInputField(
                value = waSearch,
                onValueChange = { waSearch = it },
                label = "Search $appLabel Contacts",
                placeholder = "Type name or phone number...",
                leadingIcon = Icons.Default.Search,
                accentColor = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Selection controls bar: Selected count + Select All / Clear All
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedRecipientsMap.isNotEmpty()) "${selectedRecipientsMap.size} selected" else "Select recipients",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedRecipientsMap.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (waContacts.isNotEmpty()) {
                        Text(
                            text = "Select All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .clip(AppDesignTokens.ShapeSmall)
                                .clickable {
                                    waContacts.forEach {
                                        selectedRecipientsMap[it.phone] = SelectedRecipient(name = it.name.ifBlank { it.phone }, phone = it.phone)
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (selectedRecipientsMap.isNotEmpty()) {
                        Text(
                            text = "Clear All",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(AppDesignTokens.ShapeSmall)
                                .clickable {
                                    selectedRecipientsMap.clear()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (waContacts.isEmpty()) {
                    item {
                        appEmptyState(
                            icon = Icons.AutoMirrored.Filled.Send,
                            title = "No $appLabel Contacts Found",
                            description = if (waSearch.isNotBlank()) "No contacts matching '$waSearch'" else "No contacts with $appLabel account detected on device. Tap '$scanLabel' to scan contacts or '$harvestLabel' to scan open chats."
                        )
                    }
                } else {
                    itemsIndexed(
                        items = waContacts,
                        key = { index, contact ->
                            val cleanKey = contact.normalizedPhone.ifBlank { contact.phone }.ifBlank { contact.name }
                            "${cleanKey}_${contact.accountType}_$index"
                        }
                    ) { _, contact ->
                        val isChecked = isContactSelected(contact.phone)
                        val isGroup = contact.phone.startsWith("group:") || contact.normalizedPhone.startsWith("group:")

                        appCard(
                            onClick = {
                                toggleContact(contact.name, contact.phone, isChecked)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (isChecked) (if (isGroup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary) else MaterialTheme.colorScheme.outlineVariant
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    contactAvatar(
                                        name = contact.name.ifBlank { contact.phone },
                                        photoUri = contact.photoUri,
                                        size = 38.dp,
                                        badgeIcon = if (isGroup) Icons.Default.Group else Icons.AutoMirrored.Filled.Send,
                                        badgeColor = if (isGroup) MaterialTheme.colorScheme.primary else (if (isBusiness) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = contact.name.ifBlank { "Contact" },
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isGroup) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                appBadge(
                                                    text = if (isBusiness) "W4B Group" else "Group",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    showDot = false
                                                )
                                            } else if (contact.accountType.contains("w4b", ignoreCase = true)) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                appBadge(
                                                    text = "W4B",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    showDot = false
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isGroup) "WhatsApp Group Chat" else contact.phone,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isGroup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        toggleContact(contact.name, contact.phone, isChecked)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = if (isGroup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkmarkColor = AppDesignTokens.GlassBackground
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Bottom sheet modal for selecting canned response templates or adding new reusable templates.
 *
 * @param repository Data repository for reading and persisting templates.
 * @param initialTemplates Initial fallback templates if repository is null.
 * @param onSelectTemplate Callback receiving chosen message template.
 * @param onDismiss Callback to dismiss template picker sheet.
 */
@Composable
fun templatePickerSheet(
    repository: AppRepository? = null,
    initialTemplates: List<MessageTemplate> = emptyList(),
    onSelectTemplate: (MessageTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var templateList by remember {
        mutableStateOf(repository?.getAllTemplates() ?: initialTemplates)
    }

    var isEditing by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<MessageTemplate?>(null) }
    var formTitle by remember { mutableStateOf("") }
    var formCategory by remember { mutableStateOf("") }
    var formContent by remember { mutableStateOf("") }
    var deleteCandidate by remember { mutableStateOf<MessageTemplate?>(null) }

    fun refresh() {
        if (repository != null) {
            templateList = repository.getAllTemplates()
        }
    }

    // Delete Confirmation Dialog
    deleteCandidate?.let { cand ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = {
                Text("Delete Template?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${cand.title}\"?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (repository != null) {
                            repository.deleteTemplate(cand.id)
                            refresh()
                        } else {
                            templateList = templateList.filterNot { it.id == cand.id }
                        }
                        deleteCandidate = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (isEditing) {
        // Sub-view: Add / Edit Template
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isEditing = false
                                editingTemplate = null
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            if (editingTemplate != null) "Edit Template" else "New Template",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            containerColor = AppDesignTokens.GlassBackground
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                appCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        glassInputField(
                            value = formTitle,
                            onValueChange = { formTitle = it },
                            label = "Template Title",
                            placeholder = "e.g. Order Confirmation, Meeting Notes",
                            leadingIcon = Icons.AutoMirrored.Filled.Label,
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                        glassInputField(
                            value = formCategory,
                            onValueChange = { formCategory = it },
                            label = "Category",
                            placeholder = "Business, Greetings, Personal...",
                            leadingIcon = Icons.Default.Category,
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                        glassInputField(
                            value = formContent,
                            onValueChange = { formContent = it },
                            label = "Template Content",
                            placeholder = "Write message text... Use {name} or {date} for dynamic insertion.",
                            leadingIcon = Icons.Default.EditNote,
                            singleLine = false,
                            minLines = 4,
                            maxLines = 8,
                            accentColor = MaterialTheme.colorScheme.primary
                        )

                        // Dynamic Variable Chips for Template Editor
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                "{name}", "{first_name}", "{phone}",
                                "{date}", "{short_date}", "{time}",
                                "{day}", "{short_day}", "{day_num}"
                            ).chunked(3).forEach { rowTags ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    rowTags.forEach { tag ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(AppDesignTokens.ShapePill)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), AppDesignTokens.ShapePill)
                                                .clickable {
                                                    formContent = if (formContent.isBlank()) tag else "$formContent $tag"
                                                }
                                                .padding(vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = tag,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    glassButton(
                        text = "Cancel",
                        onClick = {
                            isEditing = false
                            editingTemplate = null
                        },
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )

                    glassButton(
                        text = if (editingTemplate != null) "Update" else "Save Template",
                        onClick = {
                            if (formTitle.isNotBlank() && formContent.isNotBlank()) {
                                val t = MessageTemplate(
                                    id = editingTemplate?.id ?: 0L,
                                    title = formTitle.trim(),
                                    category = formCategory.trim().ifBlank { "General" },
                                    content = formContent.trim()
                                )
                                if (repository != null) {
                                    if (t.id > 0) repository.updateTemplate(t) else repository.insertTemplate(t)
                                    refresh()
                                } else {
                                    val newId = if (t.id > 0) t.id else (System.currentTimeMillis())
                                    val saved = t.copy(id = newId)
                                    templateList = if (t.id > 0) {
                                        templateList.map { if (it.id == t.id) saved else it }
                                    } else {
                                        listOf(saved) + templateList
                                    }
                                }
                                isEditing = false
                                editingTemplate = null
                            }
                        },
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                        enabled = formTitle.isNotBlank() && formContent.isNotBlank()
                    )
                }
            }
        }
    } else {
        // Main List View with Add, Edit, Delete
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Select Message Template",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                editingTemplate = null
                                formTitle = ""
                                formCategory = "General"
                                formContent = ""
                                isEditing = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add Template",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            },
            containerColor = AppDesignTokens.GlassBackground
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (templateList.isEmpty()) {
                    item {
                        appEmptyState(
                            icon = Icons.Default.FormatQuote,
                            title = "No Templates",
                            description = "You have not saved any templates yet. Tap '+' at the top right to create one."
                        )
                    }
                } else {
                    items(templateList, key = { it.id }) { t ->
                        appCard(
                            onClick = { onSelectTemplate(t) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        t.title,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        t.content,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    appBadge(
                                        text = t.category,
                                        color = MaterialTheme.colorScheme.primary,
                                        showDot = false
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingTemplate = t
                                            formTitle = t.title
                                            formCategory = t.category
                                            formContent = t.content
                                            isEditing = true
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            deleteCandidate = t
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
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

// ============================================================================
// CONTACT SEQUENCE STUDIO SCREEN
// ============================================================================
