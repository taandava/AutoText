package com.jmd.autotext.ui.screens.more.contacts

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.RecipientItem
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Suppress("FunctionName")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditContactView(
    editingRecipient: RecipientItem?,
    repository: AppRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var contactName by remember { mutableStateOf(editingRecipient?.name ?: "") }
    var contactPhone by remember { mutableStateOf(editingRecipient?.phoneOrEmail ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editingRecipient != null) "Edit Contact" else "Add Contact",
                        fontWeight = FontWeight.Bold,
                        color = BentoGlassTokens.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BentoGlassTokens.VoidBackground)
            )
        },
        containerColor = BentoGlassTokens.VoidBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassInputField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = "Contact Name",
                        placeholder = "e.g. Rahul Sharma",
                        leadingIcon = Icons.Default.Person,
                        accentColor = BentoGlassTokens.VividEmerald
                    )
                    GlassInputField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = "Phone Number / Email",
                        placeholder = "e.g. +919876543210",
                        leadingIcon = Icons.Default.Phone,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        accentColor = BentoGlassTokens.VividEmerald
                    )
                }
            }

            GlassButton(
                text = if (editingRecipient != null) "Update Contact" else "Save Contact",
                icon = Icons.Default.Check,
                onClick = {
                    if (contactPhone.isNotBlank()) {
                        val nameToSave = if (contactName.isNotBlank()) contactName.trim() else contactPhone.trim()
                        val wasEditing = editingRecipient != null
                        if (editingRecipient != null) {
                            repository.updateRecipient(
                                editingRecipient.copy(
                                    name = nameToSave,
                                    phoneOrEmail = contactPhone.trim()
                                )
                            )
                        } else {
                            repository.insertRecipient(
                                RecipientItem(name = nameToSave, phoneOrEmail = contactPhone.trim())
                            )
                        }
                        Toast.makeText(context, if (wasEditing) "Contact updated!" else "Contact added!", Toast.LENGTH_SHORT).show()
                        onSaved()
                    }
                },
                enabled = contactPhone.isNotBlank(),
                variant = GlassButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
