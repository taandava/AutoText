package com.jmd.autotext.ui.screens.compose

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Person
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
import com.jmd.autotext.ui.components.BentoAvatar
import com.jmd.autotext.ui.components.BentoBadge
import com.jmd.autotext.ui.components.BentoCard
import com.jmd.autotext.ui.components.GlassSearchField
import com.jmd.autotext.ui.theme.BentoGlassTokens
import com.jmd.autotext.util.PhoneNumberNormalizer

@Composable
fun ContactPoolPickerSheet(
    repository: AppRepository,
    selectedRecipients: List<SelectedRecipient>,
    onAddRecipient: (name: String, phone: String) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var poolSearch by remember { mutableStateOf("") }
    val poolContacts = remember(poolSearch) {
        repository.getDynamicPool(limit = 3000, searchQuery = poolSearch)
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Select From Dynamic Pool",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = BentoGlassTokens.TextPrimary
                    )
                }
            }
        },
        containerColor = BentoGlassTokens.GlassBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassSearchField(
                value = poolSearch,
                onValueChange = { poolSearch = it },
                placeholder = "Search ${repository.getDynamicPoolStats().totalCount} pool contacts...",
                leadingIcon = Icons.Default.Search,
                onClear = { poolSearch = "" }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(poolContacts, key = { it.id }) { c ->
                    val isAlreadyAdded = selectedRecipients.any {
                        val norm = PhoneNumberNormalizer.normalize(it.phone)
                        val cNorm = PhoneNumberNormalizer.normalize(c.phone)
                        (norm.isNotBlank() && cNorm.isNotBlank() && norm == cNorm) ||
                        (it.phone.filter { ch -> ch.isDigit() } == c.phone.filter { ch -> ch.isDigit() })
                    }

                    BentoCard(
                        onClick = {
                            val added = onAddRecipient(c.name, c.phone)
                            if (added) {
                                Toast.makeText(context, "Added: ${c.name.ifBlank { c.phone }}", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                BentoAvatar(
                                    name = c.name.ifBlank { c.phone },
                                    photoUri = c.photoUri,
                                    size = 36.dp,
                                    badgeIcon = Icons.Default.Person,
                                    badgeColor = BentoGlassTokens.ElectricIndigo
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(c.name.ifEmpty { "Unsaved Contact" }, fontWeight = FontWeight.SemiBold, color = BentoGlassTokens.TextPrimary, fontSize = 13.5.sp)
                                    Text(c.phone, fontSize = 11.5.sp, color = BentoGlassTokens.NeonCyan)
                                }
                            }
                            if (isAlreadyAdded) {
                                BentoBadge(text = "Added", color = BentoGlassTokens.TextMuted, showDot = false)
                            } else {
                                Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = BentoGlassTokens.VividEmerald, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
