package com.jmd.autotext.ui.screens.compose

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppGroupsPickerSheet(
    repository: AppRepository,
    selectedGroupName: String,
    onSelectGroup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var groupSearch by remember { mutableStateOf("") }
    var newGroupNameInput by remember { mutableStateOf("") }
    var refreshGroupsTrigger by remember { mutableIntStateOf(0) }
    var allGroups by remember { mutableStateOf<List<com.jmd.autotext.model.WhatsAppGroupItem>>(emptyList()) }

    LaunchedEffect(refreshGroupsTrigger) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            allGroups = repository.getWhatsAppGroupItems()
        }
    }

    val filteredGroups = remember(allGroups, groupSearch) {
        if (groupSearch.isBlank()) allGroups
        else allGroups.filter { it.name.contains(groupSearch, ignoreCase = true) || it.memberCountStr.contains(groupSearch, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BentoGlassTokens.GlassSurfaceElevated)
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
                                tint = BentoGlassTokens.TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Select WhatsApp Group",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = BentoGlassTokens.TextPrimary
                            )
                            Text(
                                "${allGroups.size} groups available",
                                fontSize = 12.sp,
                                color = BentoGlassTokens.VividEmerald
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassButton(
                            text = "👉 Pick in WA",
                            onClick = {
                                AutoTextAccessibilityService.startManualChatPicker(context) { picked ->
                                    onSelectGroup(picked)
                                }
                                onDismiss()
                            },
                            variant = GlassButtonVariant.PRIMARY,
                            icon = Icons.Default.Add,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
                            shape = BentoGlassTokens.ShapePill
                        )
                        GlassButton(
                            text = "⚡ Scan",
                            onClick = {
                                AutoTextAccessibilityService.startWhatsAppChatScan(context)
                                onDismiss()
                            },
                            variant = GlassButtonVariant.SECONDARY,
                            icon = Icons.Default.Refresh,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                            shape = BentoGlassTokens.ShapePill
                        )
                    }
                }
            }
        },
        containerColor = BentoGlassTokens.GlassBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Add New Group Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    GlassInputField(
                        value = newGroupNameInput,
                        onValueChange = { newGroupNameInput = it },
                        label = "Add New WhatsApp Group",
                        placeholder = "Exact group title as in WhatsApp...",
                        leadingIcon = Icons.Default.Add,
                        accentColor = BentoGlassTokens.VividEmerald
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                GlassButton(
                    text = "Add",
                    onClick = {
                        if (newGroupNameInput.isNotBlank()) {
                            val trimmed = newGroupNameInput.trim()
                            repository.saveWhatsAppGroup(trimmed)
                            onSelectGroup(trimmed)
                            refreshGroupsTrigger++
                            newGroupNameInput = ""
                            onDismiss()
                            Toast.makeText(context, "Selected Group: $trimmed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    variant = GlassButtonVariant.PRIMARY,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    shape = BentoGlassTokens.ShapeSmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            GlassInputField(
                value = groupSearch,
                onValueChange = { groupSearch = it },
                label = "Filter Groups",
                placeholder = "Search saved groups...",
                leadingIcon = Icons.Default.Search,
                accentColor = BentoGlassTokens.VividEmerald
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (filteredGroups.isEmpty()) {
                    item {
                        BentoEmptyState(
                            icon = Icons.Default.Group,
                            title = "No Groups Found",
                            description = "Type a group name above to add it, or tap '⚡ Scan WA' to import active chats from WhatsApp."
                        )
                    }
                } else {
                    items(filteredGroups, key = { it.name }) { groupItem ->
                        val groupName = groupItem.name
                        val isSelected = selectedGroupName.equals(groupName, ignoreCase = true)
                        BentoCard(
                            onClick = {
                                onSelectGroup(groupName)
                                onDismiss()
                                Toast.makeText(context, "Selected Group: $groupName", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (isSelected) BentoGlassTokens.VividEmerald else BentoGlassTokens.GlassBorderSubtle
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    BentoAvatar(
                                        name = groupName,
                                        size = 36.dp,
                                        badgeIcon = Icons.Default.Group,
                                        badgeColor = if (isSelected) BentoGlassTokens.VividEmerald else BentoGlassTokens.NeonCyan
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = groupName,
                                            fontWeight = FontWeight.Bold,
                                            color = BentoGlassTokens.TextPrimary,
                                            fontSize = 13.5.sp,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (groupItem.memberCountStr.isNotBlank()) groupItem.memberCountStr else "WhatsApp Group",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (groupItem.memberCountStr.isNotBlank()) BentoGlassTokens.VividEmerald else BentoGlassTokens.TextMuted
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        BentoBadge(text = "Active", color = BentoGlassTokens.VividEmerald, showDot = true)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            repository.deleteWhatsAppGroup(groupName)
                                            if (selectedGroupName == groupName) onSelectGroup("")
                                            refreshGroupsTrigger++
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Group",
                                            tint = BentoGlassTokens.TextMuted,
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
