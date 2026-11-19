package com.jmd.supersms.ui.screen.compose

import android.content.*
import android.content.pm.PackageManager
import android.util.Log
import com.jmd.supersms.SuperApplication
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
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
import com.jmd.supersms.core.*
import com.jmd.supersms.data.model.*
import com.jmd.supersms.data.repo.*
import com.jmd.supersms.engine.*
import com.jmd.supersms.ui.components.*
import com.jmd.supersms.ui.model.*
import com.jmd.supersms.ui.theme.*

fun isSamePhoneNumber(p1: String, p2: String): Boolean = PhoneNumberNormalizer.isSamePhoneNumber(p1, p2)

/**
 * Contact pool picker bottom sheet modal for SuperSMS.
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
    val poolTotal = remember(poolSearch, refreshTrigger) {
        repository.countDynamicPool(searchQuery = poolSearch)
    }
    val poolContacts = remember(poolSearch, refreshTrigger) {
        repository.getDynamicPool(limit = 100000, searchQuery = poolSearch)
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
                                "Contact Pool",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "$poolTotal contacts",
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
            AppSearchField(
                value = poolSearch,
                onValueChange = { poolSearch = it },
                placeholder = "Search $poolTotal pool contacts...",
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
                            "Pool Empty",
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
