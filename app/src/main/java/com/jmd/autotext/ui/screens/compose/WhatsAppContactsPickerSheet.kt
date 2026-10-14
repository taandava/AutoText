package com.jmd.autotext.ui.screens.compose

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.jmd.autotext.ui.screens.SelectedRecipient
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import kotlin.time.Duration.Companion.milliseconds

@Suppress("FunctionName")
@Composable
fun WhatsAppContactsPickerSheet(
    repository: AppRepository,
    selectedRecipients: List<SelectedRecipient>,
    onAddRecipient: (name: String, phone: String) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var waSearch by remember { mutableStateOf("") }
    var waSelectedKeys by remember { mutableStateOf(setOf<String>()) }
    var waContacts by remember { mutableStateOf<List<com.jmd.autotext.model.WhatsAppContactItem>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }

    LaunchedEffect(waSearch) {
        if (waSearch.isNotBlank()) {
            kotlinx.coroutines.delay(200.milliseconds) // Debounce keystrokes so query doesn't fire on every letter
        }
        isScanning = true
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val list = repository.getWhatsAppContacts(query = waSearch, limit = 200)
            waContacts = list
        }
        isScanning = false
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
                                "WhatsApp Contacts",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = BentoGlassTokens.TextPrimary
                            )
                            Text(
                                "${waContacts.size} verified WhatsApp accounts",
                                fontSize = 12.sp,
                                color = BentoGlassTokens.VividEmerald
                            )
                        }
                    }
                    if (waSelectedKeys.isNotEmpty()) {
                        GlassButton(
                            text = "Add (${waSelectedKeys.size})",
                            onClick = {
                                val toAdd = waContacts.filter { waSelectedKeys.contains(it.phone) }
                                var addedCount = 0
                                toAdd.forEach { c ->
                                    if (onAddRecipient(c.name, c.phone)) {
                                        addedCount++
                                    }
                                }
                                Toast.makeText(context, "Added $addedCount WhatsApp contacts", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            variant = GlassButtonVariant.PRIMARY,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
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
            GlassInputField(
                value = waSearch,
                onValueChange = { waSearch = it },
                label = "Search WhatsApp Contacts",
                placeholder = "Type name or phone number...",
                leadingIcon = Icons.Default.Search,
                accentColor = BentoGlassTokens.VividEmerald
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (waContacts.isEmpty()) {
                    item {
                        BentoEmptyState(
                            icon = Icons.AutoMirrored.Filled.Send,
                            title = "No WhatsApp Contacts Found",
                            description = if (waSearch.isNotBlank()) "No contacts matching '$waSearch'" else "No contacts with WhatsApp account detected on device."
                        )
                    }
                } else {
                    items(waContacts, key = { it.phone }) { contact ->
                        val isChecked = waSelectedKeys.contains(contact.phone)
                        val isAlreadyInRecipients = selectedRecipients.any {
                            it.phone.filter { ch -> ch.isDigit() } == contact.phone.filter { ch -> ch.isDigit() }
                        }

                        BentoCard(
                            onClick = {
                                waSelectedKeys = if (waSelectedKeys.contains(contact.phone)) {
                                    waSelectedKeys - contact.phone
                                } else {
                                    waSelectedKeys + contact.phone
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = if (isChecked) BentoGlassTokens.VividEmerald else BentoGlassTokens.GlassBorderSubtle
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    BentoAvatar(
                                        name = contact.name.ifBlank { contact.phone },
                                        photoUri = contact.photoUri,
                                        size = 36.dp,
                                        badgeIcon = Icons.AutoMirrored.Filled.Send,
                                        badgeColor = BentoGlassTokens.VividEmerald
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = contact.name.ifBlank { "Contact" },
                                            fontWeight = FontWeight.Bold,
                                            color = BentoGlassTokens.TextPrimary,
                                            fontSize = 13.5.sp,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = contact.phone,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = BentoGlassTokens.VividEmerald
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isAlreadyInRecipients) {
                                        BentoBadge(text = "Added", color = BentoGlassTokens.TextMuted, showDot = false)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            waSelectedKeys = if (checked) {
                                                waSelectedKeys + contact.phone
                                            } else {
                                                waSelectedKeys - contact.phone
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = BentoGlassTokens.VividEmerald,
                                            uncheckedColor = BentoGlassTokens.TextMuted,
                                            checkmarkColor = BentoGlassTokens.GlassBackground
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
}
