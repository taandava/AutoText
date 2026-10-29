/**
 * Package: com.jmd.autotext.ui.screen.contacts
 * Module: UI Contact Add and Edit Form
 *
 * Dedicated editor form screen for adding a new recipient or modifying an existing contact record.
 * Handles validation and persistence into [AppRepository].
 */
package com.jmd.autotext.ui.screen.contacts
import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.model.RecipientItem
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Editor form screen for adding a new recipient or modifying an existing contact record.
 *
 * @param editingRecipient Existing [RecipientItem] to edit, or null when creating a new contact.
 * @param repository Data layer repository for database mutations.
 * @param onBack Callback invoked to cancel without saving.
 * @param onSaved Callback invoked when contact is successfully persisted.
 */
@Composable
fun addEditContactView(
    editingRecipient: RecipientItem?,
    repository: AppRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var contactName by remember { mutableStateOf(editingRecipient?.name ?: "") }
    var contactPhone by remember { mutableStateOf(editingRecipient?.phoneOrEmail ?: "") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = if (editingRecipient != null) "Edit Contact" else "Add Contact",
                subtitle = "Custom contact entry",
                onBack = onBack
            )
        },
        containerColor = AppDesignTokens.VoidBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            appCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    glassInputField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = "Contact Name",
                        placeholder = "e.g. Rahul Sharma",
                        leadingIcon = Icons.Default.Person,
                        accentColor = MaterialTheme.colorScheme.tertiary
                    )
                    glassInputField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = "Phone Number",
                        placeholder = "e.g. +919876543210",
                        leadingIcon = Icons.Default.Phone,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        accentColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            glassButton(
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
                        context.showAppToast(if (wasEditing) "Contact updated!" else "Contact added!")
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
