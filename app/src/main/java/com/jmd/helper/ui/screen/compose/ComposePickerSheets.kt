package com.jmd.helper.ui.screen.compose

import android.content.*
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.jmd.helper.service.TaskAccessibilityService
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
import com.jmd.helper.core.*
import com.jmd.helper.data.model.*
import com.jmd.helper.data.repo.*
import com.jmd.helper.engine.*
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.model.*
import com.jmd.helper.ui.theme.*
import com.jmd.helper.whatsapp.*

fun isSamePhoneNumber(p1: String, p2: String): Boolean = PhoneNumberNormalizer.isSamePhoneNumber(p1, p2)
/**
 * Contact pool picker bottom sheet modal.
 * @param onAddRecipient Callback invoked to validate and add a contact.
 * @param onCommitRecipients Optional callback committing updated selection batch.
 * @param onDismiss Callback to dismiss contact pool modal.
 */
@Composable
fun ContactPoolPickerSheet(
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
                    GlassButton(
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
                    GlassButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
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
            GlassSearchField(
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
                AppCard(
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
                        GlassButton(
                            text = if (isSyncing) "Populating Pool..." else "Sync & Populate Contact Pool",
                            icon = Icons.Filled.Sync,
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

                        AppCard(
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
                                    ContactAvatar(
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
fun WhatsAppContactsPickerSheet(
    repository: AppRepository,
    selectedRecipients: List<SelectedRecipient>,
    selectedChannel: ChannelType = ChannelType.WHATSAPP,
    onAddRecipient: (name: String, phone: String) -> Boolean,
    onCommitRecipients: ((List<SelectedRecipient>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isBusiness = selectedChannel == ChannelType.WHATSAPP_BUSINESS ||
        selectedChannel == ChannelType.WHATSAPP_BUSINESS_GROUP ||
        selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL ||
        selectedChannel == ChannelType.WHATSAPP_BUSINESS_STATUS
    val isCalling = selectedChannel == ChannelType.CALL || selectedChannel == ChannelType.WHATSAPP_CALL || selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL
    val targetPkg = if (isBusiness) "com.whatsapp.w4b" else "com.whatsapp"
    val sheetTitle = when {
        selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL -> "Select WA Business Call Contacts"
        selectedChannel == ChannelType.WHATSAPP_CALL -> "Select WhatsApp Call Contacts"
        selectedChannel == ChannelType.WHATSAPP_BUSINESS_GROUP -> "Select WA Business Groups"
        selectedChannel == ChannelType.WHATSAPP_GROUP -> "Select WhatsApp Groups"
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

    val isGroupChannel = selectedChannel == ChannelType.WHATSAPP_GROUP || selectedChannel == ChannelType.WHATSAPP_BUSINESS_GROUP

    LaunchedEffect(waSearch, refreshTrigger) {
        withContext(Dispatchers.IO) {
            val allContacts = repository.getWhatsAppContacts(query = waSearch, channel = selectedChannel, limit = 10000)
            val filteredContacts = when {
                isCalling -> allContacts.filter { !it.phone.startsWith("group:") && !it.normalizedPhone.startsWith("group:") }
                isGroupChannel -> allContacts.filter { it.phone.startsWith("group:") || it.normalizedPhone.startsWith("group:") || it.accountType.contains("group", ignoreCase = true) }
                else -> allContacts
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
        if (phone.startsWith("group:", ignoreCase = true)) {
            return selectedRecipientsMap.containsKey(phone) || selectedRecipientsMap.keys.any { it.equals(phone, ignoreCase = true) }
        }
        return selectedRecipientsMap.containsKey(phone) || selectedRecipientsMap.keys.any { isSamePhoneNumber(it, phone) }
    }

    fun toggleContact(name: String, phone: String, isCurrentlySelected: Boolean) {
        if (isCalling && (phone.startsWith("group:") || phone.filter { it.isDigit() }.length < 7)) {
            context.showAppToast("Calls require a contact with a valid phone number")
            return
        }
        if (isCurrentlySelected) {
            if (phone.startsWith("group:", ignoreCase = true)) {
                val matchingKeys = selectedRecipientsMap.keys.filter { it.equals(phone, ignoreCase = true) }
                matchingKeys.forEach { selectedRecipientsMap.remove(it) }
            } else {
                val matchingKeys = selectedRecipientsMap.keys.filter { isSamePhoneNumber(it, phone) }
                matchingKeys.forEach { selectedRecipientsMap.remove(it) }
            }
        } else {
            selectedRecipientsMap[phone] = SelectedRecipient(name = name.ifBlank { phone }, phone = phone)
        }
    }

    val commitAction = {
        val selectedList = selectedRecipientsMap.values.toList().distinctBy { 
            if (it.phone.startsWith("group:", ignoreCase = true)) {
                it.phone.lowercase()
            } else {
                val d = it.phone.filter { ch -> ch.isDigit() }
                if (d.length >= 10) d.takeLast(10) else it.phone
            }
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
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            sheetTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${waContacts.size} verified $appLabel accounts & chats",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    GlassButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
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
            // Action buttons row for Sync Contacts, Harvest Chats, and Harvest Groups
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = if (isScanning) "Syncing..." else scanLabel,
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
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
                GlassButton(
                    text = harvestLabel,
                    onClick = {
                        TaskAccessibilityService.startWhatsAppChatScan(
                            context,
                            targetPackage = targetPkg,
                            channel = selectedChannel,
                            scanTarget = WhatsAppChatScanner.ScanTarget.ALL_CHATS
                        )
                        onDismiss()
                    },
                    variant = GlassButtonVariant.SECONDARY,
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
                GlassButton(
                    text = if (isBusiness) "Harvest Groups (W4B)" else "Harvest Groups (WA)",
                    onClick = {
                        TaskAccessibilityService.startWhatsAppGroupScan(
                            context,
                            targetPackage = targetPkg,
                            channel = selectedChannel
                        )
                        onDismiss()
                    },
                    variant = GlassButtonVariant.PRIMARY,
                    icon = Icons.Default.Groups,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            GlassInputField(
                value = waSearch,
                onValueChange = { waSearch = it },
                label = "Search $appLabel Contacts & Groups",
                placeholder = "Type name, phone, or group...",
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
                        AppEmptyState(
                            icon = if (isGroupChannel) Icons.Default.Groups else Icons.AutoMirrored.Filled.Send,
                            title = if (isGroupChannel) "No $appLabel Groups Found" else "No $appLabel Contacts Found",
                            description = if (waSearch.isNotBlank()) "No results matching '$waSearch'" else if (isGroupChannel) "No WhatsApp groups harvested yet. Tap '$harvestLabel' above to scan and harvest your groups directly from WhatsApp." else "No contacts with $appLabel account detected on device. Tap '$scanLabel' to scan contacts or '$harvestLabel' to scan open chats."
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

                        AppCard(
                            onClick = {
                                toggleContact(contact.name, contact.phone, isChecked)
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
                                    ContactAvatar(
                                        name = contact.name.ifBlank { contact.phone },
                                        photoUri = contact.photoUri,
                                        size = 38.dp,
                                        badgeIcon = Icons.AutoMirrored.Filled.Send,
                                        badgeColor = if (isBusiness) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
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
                                            if (contact.accountType.contains("w4b", ignoreCase = true)) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                AppBadge(
                                                    text = "W4B",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    showDot = false
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = contact.phone,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.tertiary,
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

