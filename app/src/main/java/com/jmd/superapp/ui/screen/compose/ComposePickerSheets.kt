/**
 * Package: com.jmd.superapp.ui.screen.compose
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [ComposePickerSheets].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for ComposePickerSheets.
 *
 * Linked With:
 * - Repositories: [com.jmd.superapp.data.repo.*]
 * - Models & Entities: [com.jmd.superapp.data.model.*], [com.jmd.superapp.ui.model.*]
 * - Services & Engines: [com.jmd.superapp.service.TaskAccessibilityService], [com.jmd.superapp.engine.*], [com.jmd.superapp.whatsapp.*]
 * - UI Screens & Components: [androidx.activity.compose.rememberLauncherForActivityResult], [androidx.activity.result.contract.ActivityResultContracts], [androidx.compose.ui.text.style.TextAlign], [androidx.compose.ui.Alignment]
 * - Core Security & Utilities: [android.content.pm.PackageManager], [android.util.Log], [androidx.core.content.ContextCompat], [com.jmd.superapp.core.*]
 * - Android Framework & Jetpack: [androidx.compose.foundation.lazy.LazyColumn], [androidx.compose.foundation.lazy.items], [androidx.compose.foundation.lazy.itemsIndexed], [androidx.compose.foundation.shape.CircleShape]
 */

package com.jmd.superapp.ui.screen.compose

import android.content.*
import android.content.pm.PackageManager
import android.util.Log
import com.jmd.superapp.SuperApplication
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.jmd.superapp.service.TaskAccessibilityService
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
import com.jmd.superapp.core.*
import com.jmd.superapp.data.model.*
import com.jmd.superapp.data.repo.*
import com.jmd.superapp.engine.*
import com.jmd.superapp.ui.components.*
import com.jmd.superapp.ui.model.*
import com.jmd.superapp.ui.theme.*
import com.jmd.superapp.whatsapp.*
/**
 * What it is:
 * Execution routine for [isSamePhoneNumber].
 *
 * What it does:
 * Coordinates isSamePhoneNumber operations conforming to application constraints.
 *
 * Linked with:
 * - Component: [ComposePickerSheets]
 */

fun isSamePhoneNumber(p1: String, p2: String): Boolean = PhoneNumberNormalizer.isSamePhoneNumber(p1, p2)

/**
 * What it is:
 * Contact pool picker bottom sheet modal.
 *
 * What it does:
 * Renders interactive Material 3 UI for [ContactPoolPickerSheet] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [ComposePickerSheets], [ContactPoolPickerSheet]
 * @param onAddRecipient Callback invoked to validate and add a contact.
 * @param onCommitRecipients Optional callback committing updated selection batch.
 * @param onDismiss Callback to dismiss contact pool modal.
 */
@Composable
fun ContactPoolPickerSheet(
    repository: AppRepository,
    selectedRecipients: List<SelectedRecipient>,
    selectedChannel: ChannelType = ChannelType.WHATSAPP,
    onAddRecipient: (name: String, phone: String) -> Boolean,
    onCommitRecipients: ((List<SelectedRecipient>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isBusinessChannel = selectedChannel == ChannelType.WHATSAPP_BUSINESS ||
        selectedChannel == ChannelType.WHATSAPP_BUSINESS_GROUP ||
        selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL ||
        selectedChannel == ChannelType.WHATSAPP_BUSINESS_STATUS
    val isGroupChannel = selectedChannel == ChannelType.WHATSAPP_GROUP || selectedChannel == ChannelType.WHATSAPP_BUSINESS_GROUP
    val isSmsOrCall = selectedChannel == ChannelType.SMS || selectedChannel == ChannelType.CALL || selectedChannel == ChannelType.CALL_SIMULATION

    var poolTab by remember { mutableIntStateOf(if (isGroupChannel) 1 else 0) }
    var isSyncing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var poolSearch by remember { mutableStateOf("") }

    val activeGroupFilter = if (poolTab == 1) {
        if (isBusinessChannel) "WA_BUSINESS_GROUP" else "WA_GROUP"
    } else null

    val activeChatFilter = if (isBusinessChannel) "w4b" else "wa"
    val poolTotal = remember(poolSearch, refreshTrigger, poolTab, isBusinessChannel) {
        if (poolTab == 1) {
            repository.countDynamicGroupPool(searchQuery = poolSearch, channelFilter = activeGroupFilter)
        } else {
            repository.countDynamicPool(searchQuery = poolSearch, channelFilter = activeChatFilter)
        }
    }
    val poolContacts = remember(poolSearch, refreshTrigger, poolTab, isBusinessChannel) {
        if (poolTab == 1) {
            repository.getDynamicGroupPool(limit = 100000, searchQuery = poolSearch, channelFilter = activeGroupFilter)
        } else {
            repository.getDynamicPool(limit = 100000, searchQuery = poolSearch, channelFilter = activeChatFilter)
        }
    }
    val selectedRecipientsMap = remember {
        mutableStateMapOf<String, SelectedRecipient>().apply {
            selectedRecipients.forEach { put(it.phone, it) }
        }
    }
    /**
     * What it is:
     * Execution routine for [isContactSelected].
     *
     * What it does:
     * Coordinates isContactSelected operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [ComposePickerSheets]
     */

    fun isContactSelected(phone: String): Boolean {
        if (phone.startsWith("group:", ignoreCase = true)) {
            return selectedRecipientsMap.containsKey(phone) || selectedRecipientsMap.keys.any { it.equals(phone, ignoreCase = true) }
        }
        return selectedRecipientsMap.containsKey(phone) || selectedRecipientsMap.keys.any { isSamePhoneNumber(it, phone) }
    }
    /**
     * What it is:
     * Execution routine for [toggleContact].
     *
     * What it does:
     * Coordinates toggleContact operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [ComposePickerSheets]
     */

    fun toggleContact(name: String, phone: String, isCurrentlySelected: Boolean, source: String = "") {
        if (isCurrentlySelected) {
            if (phone.startsWith("group:", ignoreCase = true)) {
                val matchingKeys = selectedRecipientsMap.keys.filter { it.equals(phone, ignoreCase = true) }
                matchingKeys.forEach { selectedRecipientsMap.remove(it) }
            } else {
                val matchingKeys = selectedRecipientsMap.keys.filter { isSamePhoneNumber(it, phone) }
                matchingKeys.forEach { selectedRecipientsMap.remove(it) }
            }
        } else {
            if (phone.startsWith("group:", ignoreCase = true)) {
                if (isSmsOrCall) {
                    context.showAppToast("Groups cannot receive calls or SMS", isLong = true)
                    return
                }
                val isBizGroup = source == "WA_BUSINESS_GROUP" || phone.startsWith("group:w4b:", ignoreCase = true) || source.contains("business", ignoreCase = true)
                val isWaGroup = source == "WA_GROUP" || phone.startsWith("group:wa:", ignoreCase = true)
                if (isBusinessChannel && !isBizGroup) {
                    context.showAppToast("Cannot add WhatsApp group to Business task", isLong = true)
                    return
                }
                if (!isBusinessChannel && isBizGroup) {
                    context.showAppToast("Cannot add Business group to WhatsApp task", isLong = true)
                    return
                }
            } else {
                val isBizContact = source == "WA_BUSINESS"
                val isWaContact = source == "WA_CHAT"
                if (isBusinessChannel && isWaContact) {
                    context.showAppToast("Cannot add WhatsApp contact to Business task", isLong = true)
                    return
                }
                if (!isBusinessChannel && !isSmsOrCall && isBizContact) {
                    context.showAppToast("Cannot add Business contact to WhatsApp task", isLong = true)
                    return
                }
            }
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
                                "Contact Pool",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "$poolTotal ${if (poolTab == 1) "groups" else "contacts"}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    AppButton(
                        text = if (isSyncing) "Syncing..." else "Sync Pool",
                        onClick = {
                            if (!isSyncing) {
                                isSyncing = true
                                scope.launch {
                                    val summary = repository.syncWhatsAppPool()
                                    withContext(Dispatchers.Main) {
                                        refreshTrigger++
                                        isSyncing = false
                                        val toastMsg = if (summary.newAdded > 0) {
                                            "Added ${summary.newAdded} new contacts (${summary.totalPoolCount} total)"
                                        } else {
                                            "Synced ${summary.totalPoolCount} contacts (up to date)"
                                        }
                                        context.showAppToast(toastMsg)
                                    }
                                }
                            }
                        },
                        variant = AppButtonVariant.SECONDARY,
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
                    AppButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = AppButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AppButton(
                        text = if (selectedRecipientsMap.isNotEmpty()) "Add (${selectedRecipientsMap.size})" else "Done",
                        onClick = commitAction,
                        variant = AppButtonVariant.PRIMARY,
                        icon = Icons.Default.Check,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        },
        containerColor = AppDesignTokens.SurfaceBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isSmsOrCall && !isGroupChannel) {
                AppSegmentedControl(
                    items = listOf("Contacts", "Groups"),
                    selectedIndex = poolTab,
                    onItemSelected = { poolTab = it }
                )
            }


            AppSearchField(
                value = poolSearch,
                onValueChange = { poolSearch = it },
                placeholder = if (poolTab == 1) "Search groups..." else "Search contacts...",
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
                            if (poolTab == 1) Icons.Default.Groups else Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            if (poolTab == 1) "No Groups" else "Pool Empty",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (poolTab == 1) "No WhatsApp groups in pool. Sync chats from WA Contacts to ingest groups." else "Ingest and normalize contacts from your phone into the high-speed local pool.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        AppButton(
                            text = if (isSyncing) "Syncing..." else "Sync Contacts",
                            icon = Icons.Filled.Sync,
                            onClick = {
                                if (!isSyncing) {
                                    isSyncing = true
                                    scope.launch {
                                        val summary = repository.syncWhatsAppPool()
                                        withContext(Dispatchers.Main) {
                                            refreshTrigger++
                                            isSyncing = false
                                            val toastMsg = if (summary.newAdded > 0) {
                                                "Added ${summary.newAdded} contacts (${summary.totalPoolCount} total)"
                                            } else {
                                                "Synced ${summary.totalPoolCount} contacts (up to date)"
                                            }
                                            context.showAppToast(toastMsg)
                                        }
                                    }
                                }
                            },
                            variant = AppButtonVariant.PRIMARY,
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
                                toggleContact(c.name, c.phone, isChecked, c.source)
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
                                    val isBizGroup = c.source == "WA_BUSINESS_GROUP" || c.source.contains("business", ignoreCase = true) || c.source.contains("w4b", ignoreCase = true)
                                    ContactAvatar(
                                        name = c.name.ifBlank { c.phone },
                                        photoUri = c.photoUri,
                                        size = 38.dp,
                                        badgeIcon = if (c.isGroup) (if (isBizGroup) Icons.Default.GroupWork else Icons.Default.Groups) else Icons.Default.Person,
                                        badgeColor = if (c.isGroup) (if (isBizGroup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary) else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = c.name.ifEmpty { if (c.isGroup) "Group" else "Unsaved Contact" },
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (c.isGroup) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                AppBadge(
                                                    text = if (isBizGroup) "Business" else "WhatsApp",
                                                    color = if (isBizGroup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                                    showDot = false
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (c.isGroup) c.phone.removePrefix("group:").removePrefix("GROUP:") else c.phone,
                                            fontSize = 11.5.sp,
                                            color = if (c.isGroup) (if (isBizGroup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary) else MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        toggleContact(c.name, c.phone, isChecked, c.source)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkmarkColor = AppDesignTokens.SurfaceBackground
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
 * What it is:
 * Modal bottom sheet allowing users to multi-select contacts for WhatsApp or WhatsApp Business direct messaging and calling.
 *
 * What it does:
 * Renders interactive Material 3 UI for [WhatsAppContactsPickerSheet] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [ComposePickerSheets], [WhatsAppContactsPickerSheet]
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
    val isCalling = selectedChannel == ChannelType.WHATSAPP_CALL || selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL
    val targetPkg = if (isBusiness) "com.whatsapp.w4b" else "com.whatsapp"
    val sheetTitle = when {
        selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL -> "Business Calls"
        selectedChannel == ChannelType.WHATSAPP_CALL -> "WA Calls"
        selectedChannel == ChannelType.WHATSAPP_BUSINESS_GROUP -> "Business Groups"
        selectedChannel == ChannelType.WHATSAPP_GROUP -> "WA Groups"
        isBusiness -> "Business Contacts"
        else -> "WA Contacts"
    }
    val appLabel = if (isBusiness) "Business" else "WhatsApp"

    val isGroupChannel = selectedChannel == ChannelType.WHATSAPP_GROUP || selectedChannel == ChannelType.WHATSAPP_BUSINESS_GROUP
    var filterTabIndex by remember {
        mutableIntStateOf(
            if (isGroupChannel) 2 else if (isCalling) 1 else 0
        )
    } // 0 = All, 1 = Chats, 2 = Groups

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

    val scanTick by TaskAccessibilityService.scanCompletedEvent.collectAsState()

    // Dynamically purge any legacy corrupted group rows on sheet opening
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            repository.purgeCorruptedGroupsFromPool()
        }
        refreshTrigger++
    }

    // The accessibility sync runs in the background; refresh this sheet when it reports completion
    // instead of dismissing the sheet and leaving the user with a stale list.
    LaunchedEffect(scanTick, filterTabIndex) {
        if (scanTick > 0L) {
            withContext(Dispatchers.IO) {
                val mode = when (filterTabIndex) {
                    1 -> WhatsAppFilterMode.CHATS_ONLY
                    2 -> WhatsAppFilterMode.GROUPS_ONLY
                    else -> WhatsAppFilterMode.ALL
                }
                val refreshed = repository.getWhatsAppContacts(
                    query = waSearch,
                    channel = selectedChannel,
                    limit = 10000,
                    filterMode = mode
                )
                val filtered = when {
                    isCalling -> refreshed.filter { !it.isGroup }
                    filterTabIndex == 2 -> refreshed.filter { it.isGroup }
                    filterTabIndex == 1 -> refreshed.filter { !it.isGroup }
                    else -> refreshed
                }
                withContext(Dispatchers.Main) {
                    waContacts = filtered
                    isScanning = false
                }
            }
        }
    }

    LaunchedEffect(waSearch, refreshTrigger, filterTabIndex) {
        withContext(Dispatchers.IO) {
            val mode = when (filterTabIndex) {
                1 -> WhatsAppFilterMode.CHATS_ONLY
                2 -> WhatsAppFilterMode.GROUPS_ONLY
                else -> WhatsAppFilterMode.ALL
            }
            val allContacts = repository.getWhatsAppContacts(
                query = waSearch,
                channel = selectedChannel,
                limit = 10000,
                filterMode = mode
            )
            val filteredContacts = when {
                isCalling -> allContacts.filter { !it.isGroup }
                filterTabIndex == 2 -> allContacts.filter { it.isGroup }
                filterTabIndex == 1 -> allContacts.filter { !it.isGroup }
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
    /**
     * What it is:
     * Execution routine for [isContactSelected].
     *
     * What it does:
     * Coordinates isContactSelected operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [ComposePickerSheets]
     */

    fun isContactSelected(phone: String): Boolean {
        if (phone.startsWith("group:", ignoreCase = true)) {
            return selectedRecipientsMap.containsKey(phone) || selectedRecipientsMap.keys.any { it.equals(phone, ignoreCase = true) }
        }
        return selectedRecipientsMap.containsKey(phone) || selectedRecipientsMap.keys.any { isSamePhoneNumber(it, phone) }
    }
    /**
     * What it is:
     * Execution routine for [toggleContact].
     *
     * What it does:
     * Coordinates toggleContact operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [ComposePickerSheets]
     */

    fun toggleContact(name: String, phone: String, isCurrentlySelected: Boolean, accountType: String = "") {
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
            if (phone.startsWith("group:", ignoreCase = true)) {
                val isBizGroup = accountType.contains("w4b", ignoreCase = true) || accountType.contains("business", ignoreCase = true) || phone.startsWith("group:w4b:", ignoreCase = true)
                if (isBusiness && !isBizGroup) {
                    context.showAppToast("Cannot add WhatsApp group to Business task", isLong = true)
                    return
                }
                if (!isBusiness && isBizGroup) {
                    context.showAppToast("Cannot add Business group to WhatsApp task", isLong = true)
                    return
                }
            }
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
                        val countLabel = when (filterTabIndex) {
                            2 -> "groups"
                            1 -> "chats"
                            else -> "recipients"
                        }
                        Text(
                            "${waContacts.size} $countLabel",
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
                    AppButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = AppButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                    AppButton(
                        text = if (selectedRecipientsMap.isNotEmpty()) "Add (${selectedRecipientsMap.size})" else "Done",
                        onClick = commitAction,
                        variant = AppButtonVariant.PRIMARY,
                        icon = Icons.Default.Check,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        },
        containerColor = AppDesignTokens.SurfaceBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (!isCalling && !isGroupChannel) {
                AppSegmentedControl(
                    items = listOf("All", "Chats", "Groups"),
                    selectedIndex = filterTabIndex,
                    onItemSelected = { filterTabIndex = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val syncBtnText = when {
                    isScanning -> "Syncing..."
                    filterTabIndex == 2 -> "Sync Groups"
                    filterTabIndex == 1 -> "Sync Chats"
                    else -> "Sync All"
                }
                val scanTarget = when (filterTabIndex) {
                    2 -> WhatsAppChatScanner.ScanTarget.GROUPS_ONLY
                    1 -> WhatsAppChatScanner.ScanTarget.DIRECT_CHATS_ONLY
                    else -> WhatsAppChatScanner.ScanTarget.ALL_CHATS
                }
                AppButton(
                    text = syncBtnText,
                    onClick = {
                        if (!isScanning) {
                            isScanning = true
                            TaskAccessibilityService.startWhatsAppChatScan(
                                context,
                                targetPackage = targetPkg,
                                channel = selectedChannel,
                                scanTarget = scanTarget
                            )
                        }
                    },
                    variant = AppButtonVariant.PRIMARY,
                    icon = Icons.Default.Sync,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = AppDesignTokens.ShapePill
                )
                AppButton(
                    text = "Refresh",
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            repository.purgeCorruptedGroupsFromPool()
                            withContext(Dispatchers.Main) {
                                refreshTrigger++
                            }
                        }
                    },
                    variant = AppButtonVariant.SECONDARY,
                    icon = Icons.Default.Sync,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = AppDesignTokens.ShapePill
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            AppInputField(
                value = waSearch,
                onValueChange = { waSearch = it },
                label = if (filterTabIndex == 2) "Search Groups" else if (filterTabIndex == 1) "Search Chats" else "Search Contacts",
                placeholder = if (filterTabIndex == 2) "Group name..." else "Name, phone...",
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
                        val emptyTitle = when (filterTabIndex) {
                            2 -> "No Groups"
                            1 -> "No Chats"
                            else -> "No Contacts"
                        }
                        val emptyDesc = when {
                            waSearch.isNotBlank() -> "No results matching '$waSearch'"
                            filterTabIndex == 2 -> "No WhatsApp groups synced yet. Tap 'Sync Groups' to scan groups from WhatsApp."
                            filterTabIndex == 1 -> "No WhatsApp chats stored yet. Tap 'Sync Chats' to import chats."
                            else -> "No contacts stored yet. Tap 'Sync All' to import in one pass."
                        }
                        AppEmptyState(
                            icon = if (filterTabIndex == 2) Icons.Default.Groups else Icons.AutoMirrored.Filled.Send,
                            title = emptyTitle,
                            description = emptyDesc
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
                                toggleContact(contact.name, contact.phone, isChecked, contact.accountType)
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
                                    val isBizGroup = contact.accountType.contains("w4b", ignoreCase = true) || contact.accountType.contains("business", ignoreCase = true)
                                    ContactAvatar(
                                        name = contact.name.ifBlank { contact.phone },
                                        photoUri = contact.photoUri,
                                        size = 38.dp,
                                        badgeIcon = if (contact.isGroup) (if (isBizGroup) Icons.Default.GroupWork else Icons.Default.Groups) else Icons.AutoMirrored.Filled.Send,
                                        badgeColor = if (contact.isGroup) (if (isBizGroup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary) else if (isBusiness) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = contact.name.ifBlank { if (contact.isGroup) "Group" else "Contact" },
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (contact.isGroup) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                AppBadge(
                                                    text = if (isBizGroup) "Business" else "WhatsApp",
                                                    color = if (isBizGroup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                                    showDot = false
                                                )
                                            } else if (contact.accountType.contains("w4b", ignoreCase = true)) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                AppBadge(
                                                    text = "Business",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    showDot = false
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (contact.isGroup) contact.phone.removePrefix("group:").removePrefix("GROUP:") else contact.phone,
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
                                        toggleContact(contact.name, contact.phone, isChecked, contact.accountType)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkmarkColor = AppDesignTokens.SurfaceBackground
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
