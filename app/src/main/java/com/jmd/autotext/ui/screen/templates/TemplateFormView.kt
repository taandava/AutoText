/**
 * Package: com.jmd.autotext.ui.screen.templates
 * Module: UI Template Create and Edit Form
 *
 * Dedicated editor form screen for creating or modifying reusable message templates.
 * Features quick-insert dynamic variable chips ({name}, {first_name}, {phone}, {date}, etc.).
 */
package com.jmd.autotext.ui.screen.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jmd.autotext.data.model.MessageTemplate
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Editor form screen for adding or modifying a message template.
 *
 * @param editingTemplate The template to edit, or null when creating a new template.
 * @param repository Data layer repository for template persistence.
 * @param onBack Callback invoked to cancel without saving.
 * @param onSaved Callback invoked when template is saved.
 */
@Composable
fun templateFormView(
    editingTemplate: MessageTemplate?,
    repository: AppRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var formTitle by remember { mutableStateOf(editingTemplate?.title ?: "") }
    var formCategory by remember { mutableStateOf(editingTemplate?.category ?: "") }
    var formContent by remember { mutableStateOf(editingTemplate?.content ?: "") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = if (editingTemplate != null) "Edit Template" else "Create Template",
                subtitle = "Reusable message template",
                onBack = onBack
            )
        },
        containerColor = AppDesignTokens.VoidBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            appCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    glassInputField(
                        value = formTitle,
                        onValueChange = { formTitle = it },
                        label = "Template Title",
                        placeholder = "e.g. Order Confirmation, Meeting Link",
                        leadingIcon = Icons.AutoMirrored.Filled.Label,
                        accentColor = MaterialTheme.colorScheme.primary,
                    )
                    glassInputField(
                        value = formCategory,
                        onValueChange = { formCategory = it },
                        label = "Category",
                        placeholder = "Business, Greetings, Devotional...",
                        leadingIcon = Icons.Default.Category,
                        accentColor = MaterialTheme.colorScheme.primary,
                    )
                    glassInputField(
                        value = formContent,
                        onValueChange = { formContent = it },
                        label = "Template Content",
                        placeholder = "Write message here. Use {name} or {date} as placeholders.",
                        leadingIcon = Icons.Default.EditNote,
                        singleLine = false,
                        minLines = 4,
                        maxLines = 8,
                        accentColor = MaterialTheme.colorScheme.primary,
                    )
                    // Dynamic Variable Chips
                    Text("Dynamic Variables:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val templateVariables = listOf("{name}", "{first_name}", "{phone}", "{date}", "{short_date}", "{time}", "{day}", "{short_day}", "{day_num}")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        templateVariables.chunked(3).forEach { rowVars ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowVars.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(AppDesignTokens.ShapePill)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), AppDesignTokens.ShapePill)
                                            .clickable {
                                                formContent = formContent + (if (formContent.isNotEmpty() && !formContent.endsWith(" ")) " " else "") + tag
                                            }
                                            .padding(vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(tag, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (rowVars.size < 3) {
                                    repeat(3 - rowVars.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            glassButton(
                text = if (editingTemplate != null) "Update Template" else "Save Template",
                onClick = {
                    if (formTitle.isNotBlank() && formContent.isNotBlank()) {
                        if (editingTemplate != null) {
                            repository.updateTemplate(
                                editingTemplate.copy(
                                    title = formTitle.trim(),
                                    category = formCategory.trim(),
                                    content = formContent.trim()
                                )
                            )
                        } else {
                            repository.insertTemplate(
                                MessageTemplate(
                                    title = formTitle.trim(),
                                    category = formCategory.trim(),
                                    content = formContent.trim()
                                )
                            )
                        }
                        onSaved()
                    }
                },
                emoji = "💾",
                variant = GlassButtonVariant.PRIMARY,
                enabled = formTitle.isNotBlank() && formContent.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
