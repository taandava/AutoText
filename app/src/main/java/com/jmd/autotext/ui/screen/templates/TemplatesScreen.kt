/**
 * Package: com.jmd.autotext.ui.screen.templates
 * Module: UI Message Templates Coordinator Screen
 *
 * Screen composable for browsing, creating, editing, and deleting message templates.
 * Provides category filtering, keyword search, modal creation/editing forms,
 * and quick-insert variable chips ({name}, {phone}, {time}, {date}).
 */
package com.jmd.autotext.ui.screen.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jmd.autotext.data.model.MessageTemplate
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.ui.components.AppEmptyState
import com.jmd.autotext.ui.components.AppSubScreenHeader
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Screen composable for browsing, creating, editing, and deleting message templates.
 *
 * @param repository Data layer repository for template persistence.
 * @param onBack Callback invoked when user exits the templates view.
 */
@Composable
fun TemplatesView(repository: AppRepository, onBack: () -> Unit) {
    var templates by remember { mutableStateOf(repository.getAllTemplates()) }
    var isFormOpen by remember { mutableStateOf(value = false) }
    var editingTemplate by remember { mutableStateOf<MessageTemplate?>(null) }

    BackHandler(enabled = isFormOpen) {
        isFormOpen = false
        editingTemplate = null
    }

    if (isFormOpen) {
        TemplateFormView(
            editingTemplate = editingTemplate,
            repository = repository,
            onBack = {
                isFormOpen = false
                editingTemplate = null
            },
            onSaved = {
                templates = repository.getAllTemplates()
                editingTemplate = null
                isFormOpen = false
            }
        )
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                AppSubScreenHeader(
                    title = "Message Templates",
                    subtitle = "${templates.size} reusable drafts",
                    onBack = onBack
                )
            },
            floatingActionButton = {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    FloatingActionButton(
                        onClick = {
                            editingTemplate = null
                            isFormOpen = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Text("➕", fontSize = 18.sp)
                    }
                }
            },
            containerColor = AppDesignTokens.VoidBackground
        ) { padding ->
            if (templates.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AppEmptyState(
                        title = "No Templates Saved",
                        description = "Create reusable templates with dynamic tags like {name}, {greeting}, and {date}.",
                        icon = Icons.Default.FormatQuote,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    com.jmd.autotext.ui.components.GlassButton(
                        text = "Load Starter India Templates 🇮🇳",
                        emoji = "✨",
                        variant = com.jmd.autotext.ui.components.GlassButtonVariant.PRIMARY,
                        onClick = {
                            val starters = listOf(
                                MessageTemplate(
                                    title = "Busy / In Meeting",
                                    category = "Quick Replies",
                                    content = "{greeting} {first_name}, I am currently in a meeting. Will call you back shortly. Thanks!"
                                ),
                                MessageTemplate(
                                    title = "Festival Greetings",
                                    category = "Festivals",
                                    content = "Wishing you and your family a very joyful, peaceful, and prosperous celebration! Warm regards, {name}"
                                ),
                                MessageTemplate(
                                    title = "Business Connect",
                                    category = "Business",
                                    content = "Namaste {first_name}, following up regarding our discussion. Please let me know when convenient to connect today."
                                ),
                                MessageTemplate(
                                    title = "Payment / Due Reminder",
                                    category = "Finance",
                                    content = "Namaste {name}, gentle reminder regarding your pending invoice of {rupee} due on {date}. Kindly arrange the settlement. Thanks!"
                                ),
                                MessageTemplate(
                                    title = "Appointment Confirmation",
                                    category = "Appointments",
                                    content = "Namaste {first_name}, your scheduled slot is confirmed for {time} on {date}. Thank you!"
                                )
                            )
                            starters.forEach { repository.insertTemplate(it) }
                            templates = repository.getAllTemplates()
                        }
                    )
                }
            } else {
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
                    items(templates, key = { it.id }) { t ->
                        TemplateItemCard(
                            template = t,
                            onEdit = {
                                editingTemplate = t
                                isFormOpen = true
                            },
                            onDelete = {
                                repository.deleteTemplate(t.id)
                                templates = repository.getAllTemplates()
                            }
                        )
                    }
                }
            }
        }
    }
}
